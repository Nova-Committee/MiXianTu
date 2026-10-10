package com.iafenvoy.mxt.runtime.talisman;

import com.iafenvoy.mxt.api.UseItemAuraAccess;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.Talisman;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostPayment;
import com.iafenvoy.mxt.data.cost.builtin.AuraCost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.data.item.TalismanComponent;
import com.iafenvoy.mxt.data.item.TalismanComponent.TriggerMode;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.talisman.TalismanType;
import com.iafenvoy.mxt.data.talisman.TalismanUse;
import com.iafenvoy.mxt.item.TalismanItem;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.spirit.SpiritChargeService;
import com.iafenvoy.mxt.runtime.spirit.SpiritPour.Entry;
import com.iafenvoy.mxt.runtime.spirit.SpiritSource;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The talisman carrier's own half of the pour: what a carrier takes to fill, and what happens once it holds
 * enough to fire. A carrier's capacity is the multiplier written on its definitions times what one invocation
 * wants, capped by the wear the carrier has left - so "full" is what the carrier was written to hold, and one with
 * room for several invocations fires again without being poured. What one invocation takes is its {@code costs}:
 * an aura entry comes out of that store, every other entry is paid by the holder. Whether the holder may use the
 * carrier at all is the definitions' own {@code condition}, asked before the price is planned. The pour itself
 * belongs to the spirit module ({@link UseItemAuraAccess#pour}), and a carrier takes one unit a tick at one for one.
 *
 * <p>What one invocation <em>does</em> belongs to each inscription's {@link TalismanType}: the type decides the
 * targets it lands on and runs its own action fields. A carrier holds a list of inscriptions, so one invocation asks
 * every one of them to plan first - a type that cannot happen (a crosshair with nobody under it) refuses the whole
 * carrier before the condition is asked and before anything is paid - then pays once and applies them all. Firing is
 * the carrier's answer both to being charged and to a right-click once it holds a whole invocation, so a carrier
 * billed nothing at all is still usable. What it costs the carrier is one of the uses its definitions declare, or
 * the carrier itself one item at a time when none of them declare any.
 */
public final class TalismanService {
    private TalismanService() {
    }

    // The words this module refuses with; the lang keys are `actionbar.mxt.talisman.failure.<lowercased name>`.
    private enum Failure {
        CONDITION_FAILED, INSUFFICIENT_RESOURCE, INSUFFICIENT_COST, INVALID_FORMULA, NO_TARGET
    }

    // One inscription's invocation: who is using it, where from, and the plan its own type answered with.
    private record PlannedUse(TalismanUse use, TalismanType.Plan plan) {
    }

    // One entry per aura, each the aura one invocation wants times the multiplier, rounded up to the whole units a
    // pour moves in. Auras, amounts and multipliers are all summed over the definitions written on the carrier.
    // Priced against the empty context because it has to be: the capacity is also how long a pour lasts, and the
    // client sizes the same gesture from the same stack.
    public static Map<Holder<Aura>, Integer> capacity(ItemStack stack) {
        Map<Holder<Aura>, Double> totals = new LinkedHashMap<>();
        int uses = remainingUses(stack);
        for (Holder<Talisman> talisman : inscribed(stack)) {
            // What the carrier can still spend caps what it can hold: a carrier with two invocations of wear left is
            // never poured for five, and one that is spent whole holds exactly one.
            double multiplier = Math.min(talisman.value().capacity(), uses);
            for (Cost cost : talisman.value().costs())
                if (cost instanceof AuraCost(Holder<Aura> aura1, NumberProvider amount1)) {
                    double amount = amount1.evaluate(FormulaContext.EMPTY);
                    if (Double.isFinite(amount) && amount > 0.0D)
                        totals.merge(aura1, amount * multiplier, Double::sum);
                }
        }
        Map<Holder<Aura>, Integer> capacity = new LinkedHashMap<>();
        totals.forEach((aura, amount) -> capacity.put(aura, units(amount)));
        return capacity;
    }

    // What one invocation takes out of the carrier's own store: the aura entries of the definitions' costs, summed
    // over the definitions. Priced against the empty context for the same reason the capacity is - the two numbers
    // have to agree about what one invocation costs, and neither may depend on who happens to be holding it. The
    // amounts are kept as they are written rather than rounded up, so a multiplier really does buy that many
    // invocations of them.
    private static Map<Holder<Aura>, Double> draw(List<Holder<Talisman>> written) {
        Map<Holder<Aura>, Double> draw = new LinkedHashMap<>();
        for (Holder<Talisman> talisman : written)
            for (Cost cost : talisman.value().costs())
                if (cost instanceof AuraCost(Holder<Aura> aura1, NumberProvider amount1)) {
                    double amount = amount1.evaluate(FormulaContext.EMPTY);
                    if (Double.isFinite(amount) && amount > 0.0D) draw.merge(aura1, amount, Double::sum);
                }
        return draw;
    }

    // How many invocations a carrier still has in it. Wear is the only use count a carrier has, so a carrier whose
    // definitions declare none is spent whole and has exactly one; one that declares uses has at least one, because
    // a carrier the wear has not destroyed yet can always fire once more - that shot is the one that destroys it. A
    // partial point of wear buys nothing, which is the same rounding `spend` does when it destroys the carrier.
    private static int remainingUses(ItemStack stack) {
        int cap = durability(stack);
        int cost = durabilityCost(stack);
        if (cap <= 0 || cost <= 0) return 1;
        return Math.max(1, (cap - stack.getDamageValue()) / cost);
    }

    // Whole units, the same rounding the shared stores use: a capacity of half an aura is still one whole one.
    private static int units(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return 0;
        return amount >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.ceil(amount);
    }

    // Whether the store covers one invocation, which is what a click needs to be a firing rather than a pour. A
    // carrier billed no aura at all answers yes and is fired by the same click as a filled one.
    public static boolean ready(ItemStack stack) {
        Map<Holder<Aura>, Double> draw = draw(inscribed(stack));
        if (draw.isEmpty()) return true;
        SpiritStorageComponent charge = store(stack);
        return draw.entrySet().stream().allMatch(entry -> charge.get(entry.getKey()) >= entry.getValue());
    }

    // What one invocation takes off the store, leaving the rest for the invocations after it.
    private static SpiritStorageComponent drain(SpiritStorageComponent charge, Map<Holder<Aura>, Double> draw) {
        SpiritStorageComponent left = charge;
        for (Map.Entry<Holder<Aura>, Double> entry : draw.entrySet())
            left = left.with(entry.getKey(), Math.max(0.0D, left.get(entry.getKey()) - entry.getValue()));
        return left;
    }

    private static SpiritStorageComponent store(ItemStack stack) {
        return stack.getOrDefault(MxtDataComponents.SPIRIT_STORAGE, SpiritStorageComponent.EMPTY);
    }

    // How many uses a carrier has in it, read off the stack first and off what is written on it second: a cap the
    // pack patched onto the stack (a drawing grade, or the pack's own write) overrides the definitions, and a
    // carrier whose definitions declare none has no wear at all - it is still spent whole, one item per invocation.
    // Wear belongs to a single carrier, so a stack of several never has any: vanilla refuses a stack that is both
    // damageable and stackable, and a stacked carrier stays exactly what it was - one item per invocation.
    public static int durability(ItemStack stack) {
        if (stack.getCount() > 1) return 0;
        return stack.has(DataComponents.MAX_DAMAGE) ? stack.getMaxDamage() : declaredDurability(stack);
    }

    // One invocation spends exactly one declared use: `max_use` is a count of uses rather than a wear rate, so
    // there is nothing to sum. A carrier with no cap at all has no bar and costs nothing here - the item itself is
    // what is spent (see spend).
    public static int durabilityCost(ItemStack stack) {
        if (stack.getCount() > 1) return 0;
        return durability(stack) > 0 ? 1 : 0;
    }

    // Puts the declared wear onto the stack in vanilla's own shape: the cap, a stack size of one and a damage
    // value that exists. All three are needed - a cap with a stack size above one is refused on the way out
    // ("Item cannot be both damageable and stackable"), and a stack without a damage component is not damageable
    // at all - so the framework never writes a partial one. A cap the stack already carries is kept as it is.
    public static void applyDurability(ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() > 1) return;
        int cap = stack.has(DataComponents.MAX_DAMAGE) ? stack.getMaxDamage() : declaredDurability(stack);
        if (cap <= 0) return;
        stack.set(DataComponents.MAX_DAMAGE, cap);
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        stack.set(DataComponents.DAMAGE, stack.getOrDefault(DataComponents.DAMAGE, 0));
    }

    // What every inscription written on the carrier declares together; a type that declares none adds nothing.
    private static int declaredDurability(ItemStack stack) {
        int declared = 0;
        for (Holder<Talisman> talisman : inscribed(stack))
            declared = add(declared, Math.max(0, talisman.value().type().maxUse()));
        return declared;
    }

    // What one invocation costs the holder, in the order the definitions were written. Every aura entry is left
    // out: an aura is drawn from the carrier's own store rather than from an account. The list goes into the shared
    // transaction untouched, so a price that cannot be paid refuses the invocation instead of half-paying it.
    private static List<Cost> holderCosts(List<Holder<Talisman>> written) {
        return written.stream().flatMap(talisman -> talisman.value().costs().stream())
                .filter(cost -> !(cost instanceof AuraCost)).toList();
    }

    // The one entry every trigger shares - a hand (TalismanItem#use) and a carrier that filled itself - so
    // neither way in can drift from the other; which rule applies is read off the source, never decided here.
    // A hand charges the window for the attempt rather than the firing. The window is filed against a copy
    // taken before the invocation, because firing burns the stack and vanilla reads the cooldown group off it.
    public static boolean invokeOnUse(SpiritSource source, ItemStack stack) {
        ItemStack beforeUse = stack.copy();
        Attempt attempt = attempt(source, stack);
        if (source.consumedByHand() && attempt.attempted()) applyCooldown(source.actor(), beforeUse);
        return attempt.fired();
    }

    // Whether the carrier's own mode lets it fire the moment it is full. STORE is a carrier that only
    // accumulates: filled to its bill and sitting there, waiting for a hand or for a switch back.
    public static boolean autoFires(ItemStack stack) {
        return component(stack).mode() == TriggerMode.FIRE;
    }

    // The switch a hand makes with a sneaking use: firing on its own stops, storing starts. A full carrier in
    // STORE is not switched at all - it fires instead, because a stored charge exists to be spent. The return
    // is whether anything was asked of the carrier, which tells a click that fired from one that only changed
    // a mode.
    public static boolean toggleMode(SpiritSource source, ItemStack stack) {
        if (inscribed(stack).isEmpty()) return false;
        TalismanComponent component = component(stack);
        boolean full = SpiritChargeService.full(source.level().registryAccess(), stack);
        if (component.mode() == TriggerMode.STORE && full) {
            // Set first: firing spends the stack, and this is the mode the rest of it is left in.
            stack.set(MxtDataComponents.TALISMAN, component.withMode(TriggerMode.FIRE));
            return invokeOnUse(source, stack);
        }
        TriggerMode next = component.mode().other();
        stack.set(MxtDataComponents.TALISMAN, component.withMode(next));
        LivingEntity holder = source.actor();
        if (holder != null) say(holder, Component.translatable("actionbar.mxt.talisman.mode." + next.key()));
        return false;
    }

    // False inside the use window, because the aura a tick would move would buy a charge the click is not going
    // to spend. A full carrier is left out by the caller rather than here, and a display stand never asks.
    public static boolean canFireFrom(@Nullable LivingEntity holder, ItemStack stack) {
        return !coolingDown(holder, stack);
    }

    // One invocation, and whether it got as far as being one: a type refusing to plan, or the carrier's own
    // condition refusing the holder, still means the carrier was used, while a carrier that is blank, uncharged or
    // cooling down was never attempted.
    private static Attempt attempt(SpiritSource source, ItemStack stack) {
        LivingEntity holder = source.actor();
        // Nothing living is answerable for it: somebody has to pay, to be credited and to answer for it. The carrier
        // stays charged, which is what a click by a person can use.
        if (holder == null || source.level().isClientSide() || !(stack.getItem() instanceof TalismanItem))
            return Attempt.NOT_AN_ATTEMPT;
        // Two rules, one per way in. The use window is about the hand alone: a placed carrier on a display stand
        // is outside every window - it reads none and leaves none. The burn is about where the carrier is: a
        // hand keeps what a creative hand would not spend, a placed carrier is always spent. The source's own
        // flag says which this is, so nothing infers it from whether an actor happened to be passed.
        boolean inHand = source.consumedByHand();
        if (inHand && coolingDown(holder, stack)) {
            say(holder, Component.translatable("actionbar.mxt.talisman.cooldown"));
            return Attempt.NOT_AN_ATTEMPT;
        }
        List<Holder<Talisman>> written = inscribed(stack);
        if (written.isEmpty()) {
            say(holder, Component.translatable("actionbar.mxt.talisman.blank"));
            return Attempt.NOT_AN_ATTEMPT;
        }
        if (!ready(stack)) {
            say(holder, Component.translatable("actionbar.mxt.talisman.not_charged"));
            return Attempt.NOT_AN_ATTEMPT;
        }
        // Where it happened, in the same shape every other position travels in: the explicit formula values
        // block_x, block_y and block_z. The acting entity is still the actor, because that is who the invocation
        // belongs to - the position says where, not who.
        FormulaContext context = FormulaContext.of(holder, Map.of("block_x", source.position().x(),
                "block_y", source.position().y(), "block_z", source.position().z()));
        // Ask every inscription where its use would land before anything else happens: a type that cannot happen at
        // all - a crosshair with nobody under it - refuses the carrier here, ahead of the condition and of the price.
        List<PlannedUse> planned = new ArrayList<>(written.size());
        for (Holder<Talisman> talisman : written) {
            TalismanUse use = new TalismanUse(holder, context, source.position(), talisman);
            TalismanType.Plan plan = talisman.value().type().plan(use);
            if (!plan.runnable()) {
                say(holder, failed(Failure.NO_TARGET));
                return Attempt.REFUSED;
            }
            planned.add(new PlannedUse(use, plan));
        }
        // The definitions' own gate, asked before the price is planned: what a carrier will not let the holder do
        // right now is refused while nothing has been paid, so a condition can never cost the holder anything.
        if (!allowed(written, holder, context)) {
            say(holder, failed(Failure.CONDITION_FAILED));
            return Attempt.REFUSED;
        }
        // One invocation is charged as a whole before anything happens: the carrier's own costs plus every
        // inscription's own costs, merged into one payment that is never committed, so an inscription that has
        // already fired can never be left unpaid for. The aura entries are not in it - they come out of the
        // carrier's own store.
        CostContext costContext = CostContext.of(holder, context, CostOrigin.TALISMAN);
        CostPayment payment = CostPayment.of(costContext);
        Optional<CostFailure> priced = payment.loadAndTest(holderCosts(written));
        if (priced.isPresent()) {
            say(holder, failed(costFailure(priced.get())));
            return Attempt.REFUSED;
        }
        // What each type does once the price can be paid. A type answers for its own targets; the ones that land
        // immediately run their actions here, and a thrown one leaves its effect to the moment it hits.
        for (PlannedUse entry : planned) entry.use().definition().value().type().apply(entry.use(), entry.plan());
        CostPayment.Result paid = CostPayment.pay(holderCosts(written), costContext);
        if (!paid.paid()) {
            say(holder, failed(costFailure(paid.failure())));
            return Attempt.REFUSED;
        }
        // This invocation's share of the store is spent here; whatever the definitions wrote on top of one
        // invocation is left for the invocations after it. What is left goes back to whoever set the invocation
        // off when the carrier itself is spent, which is the one case where it never gets to keep it.
        SpiritStorageComponent left = drain(store(stack), draw(written));
        if (left.isEmpty()) stack.remove(MxtDataComponents.SPIRIT_STORAGE);
        else stack.set(MxtDataComponents.SPIRIT_STORAGE, left);
        if (spend(stack, holder, inHand)) refund(left, holder);
        say(holder, Component.translatable("actionbar.mxt.talisman.invoked", planned.size()));
        return Attempt.FIRED;
    }

    // Every inscription, because one invocation is one act across everything written on the carrier: a definition
    // that says the holder may not use it now refuses the whole carrier rather than being left out of the volley.
    private static boolean allowed(List<Holder<Talisman>> written, LivingEntity holder, FormulaContext context) {
        return written.stream().allMatch(talisman -> talisman.value().condition().test(holder, context));
    }

    // A price the holder cannot make is the same two answers every other cost gives: not enough of one resource,
    // or not enough of everything else.
    private static Failure costFailure(CostFailure failure) {
        return failure == CostFailure.INSUFFICIENT_RESOURCE ? Failure.INSUFFICIENT_RESOURCE : Failure.INSUFFICIENT_COST;
    }

    private static Component failed(Failure failure) {
        return Component.translatable("actionbar.mxt.talisman.failed",
                Component.translatable("actionbar.mxt.talisman.failure." + failure.name().toLowerCase(Locale.ROOT)));
    }

    // What a carrier was still holding when it was spent, handed back to whoever set that invocation off. The pour
    // charged one unit of the aura's own resource for each unit it put in, so that is what comes back; a definition
    // that no longer resolves or an amount the resource will not take loses the aura with the paper rather than
    // failing the invocation that has already happened.
    private static void refund(SpiritStorageComponent charge, LivingEntity holder) {
        if (charge.isEmpty()) return;
        ResourceHolderAttachment resources = holder.getData(MxtAttachments.RESOURCE_HOLDER);
        charge.amounts().forEach((aura, units) -> {
            double amount = units * SpiritChargeService.POUR_COST_PER_UNIT;
            if (!Double.isFinite(amount) || amount <= 0.0D) return;
            Holder<Resource> resource = aura.value().resource();
            FormulaContext context = ResourceService.formulaContext(holder, resource, FormulaContext.of(holder));
            ResourceService.change(resources, resource, amount, context);
        });
    }

    // One invocation's share of the carrier, in one of two currencies: the uses its definitions declare, or the
    // carrier itself - one item off the stack, which is what a carrier with no uses to spend has always cost.
    // Wear that passes the cap destroys the carrier and leaves nothing behind, so the damage a stack shows is
    // always the wear of the item on top. A creative hand spends neither, which is vanilla's own answer that its
    // stack is not the thing being spent. True means the carrier is gone, which is when what it was still holding
    // has nobody left to keep it.
    private static boolean spend(ItemStack stack, LivingEntity holder, boolean inHand) {
        applyDurability(stack);
        boolean creative = inHand && holder instanceof Player player && player.hasInfiniteMaterials();
        int cap = durability(stack);
        int cost = durabilityCost(stack);
        if (cap > 0 && cost > 0) {
            if (creative) return false;
            int damage = stack.getDamageValue() + cost;
            if (damage < cap) {
                stack.setDamageValue(damage);
                return false;
            }
            stack.shrink(1);
            if (!stack.isEmpty()) stack.setDamageValue(0);
            return stack.isEmpty();
        }
        if (!inHand) {
            // A placed carrier is always spent: nobody's creative mode is holding it.
            stack.shrink(1);
        } else if (!creative) {
            stack.consume(1, holder);
        }
        return stack.isEmpty();
    }

    // An attempt is what the use cooldown is charged for, so the two answers are kept apart rather than
    // flattened into a boolean the caller would have to guess at.
    private record Attempt(boolean attempted, boolean fired) {
        private static final Attempt NOT_AN_ATTEMPT = new Attempt(false, false);
        private static final Attempt REFUSED = new Attempt(true, false);
        private static final Attempt FIRED = new Attempt(true, true);
    }

    // Vanilla's tracker is per player and keyed by the item, and every carrier is the same item, so one window
    // covers every carrier a player holds. A group of its own is what `cooldown_group` is for.
    private static boolean coolingDown(@Nullable LivingEntity holder, ItemStack stack) {
        return useCooldownTicks() > 0 && holder instanceof Player player && player.getCooldowns().isOnCooldown(stack);
    }

    // Vanilla's own tracker through vanilla's own call, with the window from the server option instead of a
    // component. The group is read off the stack handed in, so it has to be one that still holds what was used.
    private static void applyCooldown(@Nullable LivingEntity holder, ItemStack stackBeforeUse) {
        int ticks = useCooldownTicks();
        if (ticks <= 0 || !(holder instanceof Player player)) return;
        player.getCooldowns().addCooldown(stackBeforeUse, ticks);
    }

    private static int useCooldownTicks() {
        return MxtServerConfig.INSTANCE.talisman.useCooldown.getValue();
    }

    public static List<Holder<Talisman>> inscribed(ItemStack stack) {
        return component(stack).talismans();
    }

    // Read by the tooltip, which cannot reach the component itself: the default a missing one decodes as is this
    // class's own business.
    public static TriggerMode mode(ItemStack stack) {
        return component(stack).mode();
    }

    // Defaults a missing component means: nothing inscribed, and the firing mode - which is also what every
    // carrier written before the mode existed decodes as.
    private static TalismanComponent component(ItemStack stack) {
        return stack.getOrDefault(MxtDataComponents.TALISMAN, TalismanComponent.EMPTY);
    }

    // The whole store of a stack as the pour reads it: one entry per aura, in the order capacity was written,
    // with whatever has been poured in so far.
    public static List<Entry> entries(ItemStack stack) {
        Map<Holder<Aura>, Integer> capacity = capacity(stack);
        if (capacity.isEmpty()) return List.of();
        SpiritStorageComponent charge = store(stack);
        List<Entry> entries = new ArrayList<>(capacity.size());
        capacity.forEach((aura, size) -> entries.add(new Entry(aura,
                (int) Math.clamp(Math.floor(charge.get(aura)), 0.0D, size), size)));
        return List.copyOf(entries);
    }

    private static int add(int first, int second) {
        long total = (long) first + second;
        return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private static void say(LivingEntity holder, Component line) {
        if (holder instanceof ServerPlayer player) player.sendSystemMessage(line, true);
    }
}
