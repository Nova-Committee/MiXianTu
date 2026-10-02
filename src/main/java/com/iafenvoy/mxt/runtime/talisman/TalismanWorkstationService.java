package com.iafenvoy.mxt.runtime.talisman;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.api.ItemAuraAccess;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment.Audit;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.builtin.ItemCost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.data.item.TalismanComponent;
import com.iafenvoy.mxt.data.item.TalismanComponent.TriggerMode;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.recipe.TalismanDrawingRecipe;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer.Point;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer.Score;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaDiagnostics;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.util.matcher.builtin.ItemEntry;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.*;

/**
 * Everything a drawing does on the server: which formulas may be picked, opening a session (which takes the paper
 * and the extras out of the station slot), charging one stroke to the brush, the final reconciliation and
 * settlement.
 *
 * <p>Nothing here trusts the client: the amounts charged are measured from the point lists the server received,
 * the completion is recomputed from the reconciled points, and only the server clock decides whether two strokes
 * came too close together.
 */
public final class TalismanWorkstationService {
    /**
     * One row of the formula list; the name is the referenced talisman definition's, computed server side.
     */
    public record Entry(Identifier id, Component name, boolean affordable) {
    }

    /**
     * Why one stroke was not taken. A refusal is a normal answer, never an error.
     */
    public enum StrokeRefusal {
        TOO_FAST, NO_PIGMENT, BRUSH_GONE, TOO_MANY_POINTS, SESSION_OVER
    }

    public record StrokeResult(boolean accepted, StrokeRefusal refusal, int charged) {
        static final StrokeResult ACCEPTED = new StrokeResult(true, null, 0);
    }

    public record Outcome(boolean success, double completion, ItemStack product, Component text, int pigmentSpent) {
    }

    private TalismanWorkstationService() {
    }

    /**
     * The formulas this player could start right now: unlocked, and affordable out of the station slot and the
     * payer's own accounts. The brush is deliberately not part of the answer - it is charged per stroke, so it is
     * checked when a stroke arrives rather than when a formula is picked.
     */
    public static List<Entry> entries(ServerPlayer player, Container paperSlot) {
        return entries(player, paperSlot, ItemStack.EMPTY);
    }

    /**
     * @param heldPaper what an open session that has drawn nothing is holding, and would hand back if the player
     *                  switched formula: it still counts, or every row would be unaffordable the moment one is
     *                  picked and the formula could never be changed.
     */
    public static List<Entry> entries(ServerPlayer player, Container paperSlot, ItemStack heldPaper) {
        List<Entry> entries = new ArrayList<>();
        Container station = paperSlot.getItem(0).isEmpty() && !heldPaper.isEmpty() ? slotOf(heldPaper) : paperSlot;
        for (RecipeHolder<TalismanDrawingRecipe> holder : all(player.level().getServer().getRecipeManager())) {
            TalismanDrawingRecipe recipe = holder.value();
            Component name = DefinitionText.name(recipe.talisman(), "talisman");
            boolean unlocked = recipe.unlockCondition().test(player, FormulaContext.of(player));
            boolean affordable = CostTransaction.plan(costs(recipe), context(player, station)).ok();
            entries.add(new Entry(holder.id().identifier(), name, unlocked && affordable));
        }
        entries.sort(Comparator.comparing(entry -> entry.id().toString()));
        return entries;
    }

    /**
     * A one-slot stand-in for the station slot; only ever read, and only by {@code CostTransaction.plan}.
     */
    private static Container slotOf(ItemStack stack) {
        SimpleContainer container = new SimpleContainer(1);
        container.setItem(0, stack);
        return container;
    }

    /**
     * Opens a session and takes what it costs out of the station slot and the payer. What the item channel takes
     * is measured, not assumed: an empty session hands back exactly that much, whatever else the store holds.
     */
    public static Optional<TalismanDrawingSession> start(ServerPlayer player, Container paperSlot, Identifier id) {
        TalismanDrawingRecipe recipe = find(player.level().getServer().getRecipeManager(), id).orElse(null);
        if (recipe == null) return Optional.empty();
        if (!recipe.unlockCondition().test(player, FormulaContext.of(player))) return Optional.empty();
        List<Cost> costs = costs(recipe);
        CostContext context = context(player, paperSlot);
        CostTransaction.Planning planning = CostTransaction.plan(costs, context);
        if (!planning.ok()) return Optional.empty();
        List<Runnable> refunds = snapshotResources(context, planning);
        List<ItemStack> before = contents(paperSlot);
        if (!CostTransaction.commit(planning, context).paid()) return Optional.empty();
        return Optional.of(new TalismanDrawingSession(id, recipe, costs, context, paperSlot,
                charged(before, paperSlot), refunds, player.level().getGameTime()));
    }

    /**
     * Charges one stroke to the brush the session was opened with.
     */
    public static StrokeResult stroke(ServerPlayer player, TalismanDrawingSession session, List<Point> points) {
        if (session.settled() || session.failed()) return new StrokeResult(false, StrokeRefusal.SESSION_OVER, 0);
        if (points.size() > MxtServerConfig.INSTANCE.talisman.maxStrokePoints.getValue())
            return new StrokeResult(false, StrokeRefusal.TOO_MANY_POINTS, 0);
        long now = player.level().getGameTime();
        long interval = MxtServerConfig.INSTANCE.talisman.minStrokeInterval.getValue() / 50L;
        // The first stroke is measured against the tick the drawing opened, so "start drawing then dump strokes"
        // is caught by the same check as two strokes in a row.
        if (interval > 0L && now - session.lastStrokeAt() < interval) {
            session.reject();
            return new StrokeResult(false, StrokeRefusal.TOO_FAST, 0);
        }
        ItemStack brush = carried(player);
        if (!BrushPigmentService.isBrush(brush)) return new StrokeResult(false, StrokeRefusal.BRUSH_GONE, 0);
        int charge = BrushPigmentService.chargeForStroke(points);
        if (!BrushPigmentService.hasPigment(brush, charge)) {
            session.reject();
            return new StrokeResult(false, StrokeRefusal.NO_PIGMENT, 0);
        }
        brush.set(MxtDataComponents.BRUSH_PIGMENT, BrushPigmentService.pigment(brush) - charge);
        session.spendPigment(charge);
        session.record(points, now);
        if (session.rejected() > MxtServerConfig.INSTANCE.talisman.maxRejectedStrokes.getValue()) session.fail();
        return new StrokeResult(true, null, charge);
    }

    /**
     * Reconciles the whole drawing against the strokes that arrived one by one and settles it. A mismatch, a
     * failed session or a count over the limits consumes the materials and produces nothing.
     */
    public static Outcome submit(ServerPlayer player, TalismanDrawingSession session, List<List<Point>> strokes) {
        if (!session.markSettled())
            return new Outcome(false, 0.0D, ItemStack.EMPTY, Component.empty(), session.pigmentSpent());
        TalismanDrawingRecipe recipe = session.recipe();
        boolean overLimits = strokes.size() > MxtServerConfig.INSTANCE.talisman.maxSessionStrokes.getValue()
                || count(strokes) > MxtServerConfig.INSTANCE.talisman.maxSessionPoints.getValue();
        if (session.failed() || overLimits || !reconciles(session.strokes(), strokes)) {
            MiXianTu.LOGGER.warn("Discarding a talisman drawing by {} on {}: the submitted pattern does not match the strokes received",
                    player.getGameProfile().name(), session.recipeId());
            return finish(player, session, recipe, 0.0D, false);
        }
        Score score = TalismanDrawingScorer.score(recipe.pattern().strokes(),
                strokes.stream().map(TalismanDrawingScorer.Stroke::new).toList(),
                recipe.pattern().tolerance(), recipe.judgement());
        return finish(player, session, recipe, score.completion(),
                recipe.result().gradeFor(score.completion()).isPresent());
    }

    /**
     * Ends a session that was not submitted. It consumes what it took if anything was drawn; a session with no
     * strokes at all is a no-op that hands everything back.
     */
    public static void settle(ServerPlayer player, TalismanDrawingSession session) {
        if (!session.markSettled()) return;
        if (!session.drewAnything()) refund(player, session);
        // Drawn but not submitted: the materials are spent and nothing is produced, not even a failure output -
        // the drawing never reached a settlement.
    }

    /**
     * A session that failed its own checks (too many refusals) settles as a failure: the materials are gone.
     */
    public static void fail(ServerPlayer player, TalismanDrawingSession session) {
        if (!session.markSettled()) return;
        session.recipe().result().failureAction()
                .execute(player, FormulaContext.of(player, Map.of(TalismanDrawingRecipe.PERCENTAGE, 0.0D)));
    }

    private static Outcome finish(ServerPlayer player, TalismanDrawingSession session, TalismanDrawingRecipe recipe,
                                  double completion, boolean success) {
        FormulaContext formula = FormulaContext.of(player, Map.of(TalismanDrawingRecipe.PERCENTAGE, completion));
        if (!success) {
            for (ItemStack extra : failureItems(recipe)) player.getInventory().placeItemBackInInventory(extra);
            recipe.result().failureAction().execute(player, formula);
            return new Outcome(false, completion, ItemStack.EMPTY, Component.empty(), session.pigmentSpent());
        }
        TalismanDrawingRecipe.Grade grade = recipe.result().gradeFor(completion).orElseThrow();
        ItemStack product = product(player, recipe, grade, formula);
        give(session.paperSlot(), player, product);
        for (ItemStackTemplate template : grade.outputs())
            give(session.paperSlot(), player, template.create());
        recipe.result().successAction().execute(player, formula);
        Component name = ItemQualityService.find(player.registryAccess(), product)
                .map(quality -> ItemQualityService.coloredName(quality, DefinitionText.name(quality)))
                .orElse(Component.empty());
        return new Outcome(true, completion, product, name, session.pigmentSpent());
    }

    /**
     * The carrier the grade describes: the formula's own talisman, its quality, its wear and its pour.
     */
    private static ItemStack product(ServerPlayer player, TalismanDrawingRecipe recipe, TalismanDrawingRecipe.Grade grade,
                                     FormulaContext formula) {
        ItemStack stack = MxtItems.TALISMAN.toStack();
        stack.set(MxtDataComponents.TALISMAN, new TalismanComponent(List.of(recipe.talisman()), TriggerMode.FIRE));
        grade.quality().ifPresent(quality -> setQuality(stack, quality));
        // Written before the wear is applied, because applyDurability keeps a cap the stack already carries.
        grade.maxDamage().ifPresent(provider -> stack.set(DataComponents.MAX_DAMAGE,
                (int) Math.max(0.0D, provider.evaluate(formula))));
        TalismanService.applyDurability(stack);
        double ratio = evaluated(grade.chargeRatio(), formula);
        if (ratio > 0.0D) pour(player, stack, ratio);
        return stack;
    }

    private static void setQuality(ItemStack stack, Holder<ItemQuality> quality) {
        ItemQualityService.set(stack, quality);
    }

    // One pour, the same shape the command uses: every aura the inscriptions make room for, scaled by the ratio.
    private static void pour(ServerPlayer player, ItemStack stack, double ratio) {
        if (!(stack.getItem() instanceof ItemAuraAccess access)) return;
        for (Map.Entry<Holder<Aura>, Integer> entry : TalismanService.capacity(stack).entrySet()) {
            int units = (int) Math.ceil(entry.getValue() * Math.min(1.0D, ratio));
            if (units > 0) access.insert(player, stack, entry.getKey(), units, false);
        }
    }

    private static double evaluated(NumberProvider provider, FormulaContext formula) {
        double value = provider.evaluate(formula);
        if (Double.isFinite(value)) return value;
        // A settlement formula that cannot produce a number gives nothing rather than everything.
        FormulaDiagnostics.report("A talisman settlement formula produced the non-finite value " + value + "; using 0");
        return 0.0D;
    }

    private static List<ItemStack> failureItems(TalismanDrawingRecipe recipe) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStackTemplate template : recipe.result().failureOutputs()) stacks.add(template.create());
        return stacks;
    }

    // Into the station slot the paper vacated, and into the player's inventory when that one is taken.
    private static void give(Container paperSlot, ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        for (int slot = 0; slot < paperSlot.getContainerSize(); slot++) {
            if (!paperSlot.getItem(slot).isEmpty()) continue;
            paperSlot.setItem(slot, stack);
            paperSlot.setChanged();
            return;
        }
        player.getInventory().placeItemBackInInventory(stack);
    }

    /**
     * Comparing the whole drawing with the strokes that arrived one at a time: an extra stroke, a missing one, a
     * moved point or a changed order all fail here. Without this, a client could draw anything it liked offline
     * and send it as the settlement.
     */
    private static boolean reconciles(List<List<Point>> recorded, List<List<Point>> submitted) {
        if (recorded.size() != submitted.size()) return false;
        for (int index = 0; index < recorded.size(); index++) {
            List<Point> first = recorded.get(index), second = submitted.get(index);
            if (first.size() != second.size()) return false;
            for (int point = 0; point < first.size(); point++)
                if (Double.compare(first.get(point).x(), second.get(point).x()) != 0
                        || Double.compare(first.get(point).y(), second.get(point).y()) != 0) return false;
        }
        return true;
    }

    private static int count(List<List<Point>> strokes) {
        int total = 0;
        for (List<Point> stroke : strokes) total += stroke.size();
        return total;
    }

    /**
     * The formula's own costs plus the one paper this recipe type always takes out of the station slot.
     */
    private static List<Cost> costs(TalismanDrawingRecipe recipe) {
        List<Cost> costs = new ArrayList<>(recipe.costs().size() + 1);
        costs.add(new ItemCost(List.of(new ItemEntry(TalismanDrawingRecipe.paper())),
                new Constant(TalismanDrawingRecipe.DEFAULT_PAPER_COUNT)));
        costs.addAll(recipe.costs());
        return costs;
    }

    private static CostContext context(ServerPlayer player, Container paperSlot) {
        return CostContext.of(player, CostOrigin.TALISMAN_DRAWING).withItemTarget(paperSlot);
    }

    private static ItemStack carried(ServerPlayer player) {
        return player.containerMenu.getCarried();
    }

    // What the payer's own accounts held before the payment, so an empty session can hand it back. Only the
    // resources the plan actually names are touched.
    private static List<Runnable> snapshotResources(CostContext context, CostTransaction.Planning planning) {
        ResourceHolderAttachment target = context.resourceTarget();
        if (target == null || planning.resources().isEmpty()) return List.of();
        List<Runnable> refunds = new ArrayList<>();
        for (Identifier id : planning.resources().keySet())
            MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, id).ifPresent(resource -> {
                double value = target.get(resource);
                Audit audit = target.audit(resource);
                refunds.add(() -> target.set(resource, value, audit.minSnapshot(), audit.maxSnapshot(), audit.lastChangedTick(), audit.source()));
            });
        return refunds;
    }

    private static List<ItemStack> contents(Container container) {
        NonNullList<ItemStack> contents = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < container.getContainerSize(); slot++)
            contents.set(slot, container.getItem(slot).copy());
        return contents;
    }

    /**
     * What paying took out of the store, slot by slot. Comparing the state before and after the commit is the only
     * way to know: a plan says "one item matching this matcher", not which slot it came from or how many of them
     * were already there.
     */
    private static List<ItemStack> charged(List<ItemStack> before, Container container) {
        List<ItemStack> charged = new ArrayList<>(before.size());
        for (int index = 0; index < before.size(); index++) {
            ItemStack was = before.get(index);
            ItemStack now = index < container.getContainerSize() ? container.getItem(index) : ItemStack.EMPTY;
            int taken = was.getCount() - (ItemStack.isSameItemSameComponents(was, now) ? now.getCount() : 0);
            charged.add(taken > 0 ? was.copyWithCount(taken) : ItemStack.EMPTY);
        }
        return charged;
    }

    // Handing back what the session took, and only that: the payer's own accounts first, then the store. Whatever
    // the slot cannot take - something else was put there meanwhile, or the stack no longer fits - goes to the
    // player, so a refund can never conjure a second copy of a stack the slot still holds.
    private static void refund(ServerPlayer player, TalismanDrawingSession session) {
        for (Runnable refund : session.refunds()) refund.run();
        Container slot = session.paperSlot();
        List<ItemStack> charged = session.chargedItems();
        for (int index = 0; index < charged.size() && index < slot.getContainerSize(); index++) {
            ItemStack back = charged.get(index);
            if (back.isEmpty()) continue;
            ItemStack present = slot.getItem(index);
            if (!present.isEmpty() && !ItemStack.isSameItemSameComponents(present, back)) {
                player.getInventory().placeItemBackInInventory(back);
                continue;
            }
            int placed = Math.min(back.getCount(), Math.max(0, back.getMaxStackSize() - present.getCount()));
            if (placed > 0) {
                if (present.isEmpty()) slot.setItem(index, back.copyWithCount(placed));
                else present.grow(placed);
                slot.setChanged();
            }
            if (placed < back.getCount())
                player.getInventory().placeItemBackInInventory(back.copyWithCount(back.getCount() - placed));
        }
    }

    private static Optional<TalismanDrawingRecipe> find(RecipeManager manager, Identifier id) {
        return manager.byKey(ResourceKey.create(Registries.RECIPE, id))
                .filter(holder -> holder.value() instanceof TalismanDrawingRecipe)
                .map(holder -> (TalismanDrawingRecipe) holder.value());
    }

    private static List<RecipeHolder<TalismanDrawingRecipe>> all(RecipeManager manager) {
        return manager.getRecipes().stream()
                .filter(holder -> holder.value() instanceof TalismanDrawingRecipe)
                .map(holder -> {
                    @SuppressWarnings("unchecked")
                    RecipeHolder<TalismanDrawingRecipe> cast = (RecipeHolder<TalismanDrawingRecipe>) holder;
                    return cast;
                })
                .sorted(Comparator.comparing(holder -> holder.id().identifier().toString()))
                .toList();
    }
}
