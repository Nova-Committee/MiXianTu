package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.ItemAura;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.data.item.HoldBinding;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.cultivation.ItemAuraService;
import com.iafenvoy.mxt.runtime.hold.HoldLookup;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resource.ResourceService.Result;
import com.iafenvoy.mxt.runtime.spirit.SpiritPour.Entry;
import com.iafenvoy.mxt.util.TooltipText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Charges items that implement {@link UseItemAuraAccess} by pouring the holder's own aura into them: hold the
 * item down and every server tick moves aura from the holder's resource pool into the item's store, through
 * the item's own {@link ItemAuraAccess#insert}.
 * <p>
 * What a pour moves and what it costs is the item's own answer ({@link UseItemAuraAccess#pour}) or, for an item
 * that gives none, the {@code item_aura} definition that matches it - the exchange the fuel direction already
 * describes, read the other way round. Burning an item spends {@code consume_speed} of it per tick per item and
 * hands the holder {@code release_speed}; pouring spends {@code release_speed} of the holder's aura to put
 * {@code consume_speed} into the item. Neither direction is a rate the other does not know, and because the two
 * are the same pair of numbers, a round trip - pour in, burn out - returns exactly what it cost. That is the
 * property that makes this safe to hand to anything implementing the interface: pouring cannot manufacture
 * aura, it can only store it.
 * <p>
 * Nothing here is item-specific, and an item needs no code beyond the interface: the whole gesture is declared
 * by {@link SpiritChargeHold} and driven by {@link com.iafenvoy.mxt.runtime.hold.HoldService}, and the reading
 * side of an item's store - what it holds, what it can hold, whether it is full - is the same reading the
 * tooltip and the Jade display make, so no two of them can disagree.
 */
@EventBusSubscriber
public final class SpiritChargeService {
    /**
     * The last action-bar line written to each holder, so a number that has not moved is not rewritten and a
     * refusal repeated every tick of a hold is said once. Keyed by the code rather than the message, because
     * the message is rebuilt from the charge each time.
     */
    private static final Map<UUID, String> LAST_LINE = new ConcurrentHashMap<>();

    /**
     * How fast a store that declares itself is poured: one whole unit a tick, one for one. Nothing about the
     * store's shape is assumed by it - an item that only says what it holds is poured at the gesture's own rate,
     * while an item the shared {@code item_aura} definition describes states its own pair and keeps it.
     */
    public static final int POUR_INTAKE_PER_TICK = 1;
    public static final double POUR_COST_PER_UNIT = 1.0D;

    private SpiritChargeService() {
    }

    /**
     * Hands the hold module this module's one declaration, once, at construction. The hold module drives the
     * gesture and never learns what an item aura is; this module never touches the use cycle.
     */
    public static void initialize() {
        HoldLookup.register(registries -> List.of(SpiritChargeHold.INSTANCE));
    }

    /**
     * Says why a click on a chargeable item did nothing. A stack this module does not claim is never armed, so
     * the click falls through to vanilla and there is no gesture and no other place that could explain the
     * silence.
     * <p>
     * This never cancels: an item that is also a technique manual, that carries a talisman's invocation, or that
     * declares its own use, belongs to whatever claims it first, and swallowing the click here would take that
     * away.
     */
    @SubscribeEvent
    public static void onItemUse(RightClickItem event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        ItemStack stack = entity.getItemInHand(event.getHand());
        if (!(stack.getItem() instanceof UseItemAuraAccess)) return;
        if (!(HoldLookup.hold(stack) instanceof SpiritChargeHold)) return;
        // A click speaks for itself rather than being deduplicated against the last hold's last line.
        LAST_LINE.remove(entity.getUUID());
        Charge charge = resolve(entity.level().registryAccess(), stack);
        if (charge == null) show(entity, Component.translatable("actionbar.mxt.charge.unchargeable"), "unchargeable");
        else if (charge.full()) show(entity, Component.translatable("actionbar.mxt.charge.full"), "full");
    }

    /**
     * One tick of the pour. Server only: the charge is the item's stored state, and only the server may write
     * it - the client's copy arrives through the component sync and is what draws the tooltip.
     */
    @SubscribeEvent
    public static void onUseTick(Tick event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        ItemStack stack = event.getItem();
        if (!(HoldLookup.hold(stack) instanceof SpiritChargeHold)) return;
        if (!(stack.getItem() instanceof UseItemAuraAccess access)) return;
        // A tick is paid for before the item answers it, so an item that would refuse this tick says so first.
        if (!access.canPourInto(entity, stack)) return;
        Provider registries = entity.level().registryAccess();
        // Which aura is poured is the item's answer and does not depend on the holder, so it is read first: the
        // formula context the rates are read against is that aura's own.
        Charge probe = resolve(registries, stack);
        if (probe == null) return;
        Holder<Aura> aura = probe.aura();
        // The rates are read in the aura's own context, and the pool that pays for them is the value the aura
        // is counted in - the one conversion that never needs a lookup.
        Holder<Resource> resource = aura.value().resource();
        FormulaContext context = ResourceService.formulaContext(entity, resource, FormulaContext.of(entity));
        Charge charge = resolve(registries, stack, context);
        if (charge == null) return;

        int want = charge.intake();
        if (want <= 0) return;
        // What the item will really take: a full one, or one that stores another aura, answers zero, and
        // the answer is asked before anything is paid for.
        int units = want - access.insert(entity, stack, aura, want, true);
        if (units <= 0) {
            show(entity, Component.translatable("actionbar.mxt.charge.full"), "full");
            return;
        }

        double unitCost = charge.costPerUnit();
        if (unitCost > 0.0D) {
            ResourceHolderAttachment resources = entity.getData(MxtAttachments.RESOURCE_HOLDER);
            // What the holder can pay for, decided before anything moves: a partial payment that bought no
            // whole unit would be aura taken for nothing.
            units = Math.min(units, (int) Math.floor(resources.get(resource) / unitCost));
            if (units <= 0) {
                show(entity, Component.translatable("actionbar.mxt.charge.insufficient"), "insufficient");
                return;
            }
            double before = resources.get(resource);
            // The holder's pool, spent the way the fuel direction fills it: through the resource service, so
            // the definition's bounds and its audit trail both apply, and what is charged for is what was
            // actually taken rather than what was asked for - a bound may trim the payment. The measured
            // payment can only lower the units, never raise them: a pool clamped down from above its own
            // maximum must not buy more than the tick asked for.
            Result paid = ResourceService.change(resources, resource, -(units * unitCost), context);
            if (!paid.valid()) {
                show(entity, Component.translatable("actionbar.mxt.charge.insufficient"), "insufficient");
                return;
            }
            units = Math.min(units, (int) Math.floor(Math.max(0.0D, before - paid.value()) / unitCost));
            if (units <= 0) {
                show(entity, Component.translatable("actionbar.mxt.charge.insufficient"), "insufficient");
                return;
            }
        }
        access.insert(entity, stack, aura, units, false);
        showProgress(entity, resolve(registries, stack, context));
        // The writer reports the move, and the item decides whether that was the moment it filled: a talisman
        // carrier spends itself and fires what is written on it, a spirit stone simply has nothing to say.
        access.onCharged(SpiritSource.of(entity), stack);
    }

    /**
     * What one item's store is doing, resolved from the registries and the item itself. Answers {@code null}
     * for anything this module does not charge: a stack that is empty or is not an {@link ItemAuraAccess} item,
     * one that neither declares a store nor matches an {@code item_aura} definition, and one whose numbers do
     * not evaluate to something a tick could move.
     * <p>
     * The context is the caller's, because a definition may scale its numbers by the holder; the hold itself
     * resolves with the empty context, so the two sides of a connection agree on how long a gesture lasts.
     */
    public static @Nullable Charge resolve(Provider registries, ItemStack stack, FormulaContext context) {
        // The guard is the precondition too: an item_aura definition describes items, so a plain item one
        // happens to match - a crystal that only burns as fuel - must not read as pourable.
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemAuraAccess)) return null;
        SpiritPour pour = stack.getItem() instanceof UseItemAuraAccess manual
                ? manual.pour(registries, stack).orElse(null) : null;
        if (pour != null) {
            Entry entry = pour.active().orElse(null);
            if (entry == null) return null;
            // A store that only says what it holds is poured at the gesture's own rate.
            return new Charge(entry.aura(), entry.stored(), entry.capacity(),
                    POUR_INTAKE_PER_TICK, POUR_COST_PER_UNIT);
        }
        // The shared reading: one definition describes one item, so a stack of them moves as many times as it
        // has items, and a missing component is a pristine, fully charged item.
        Holder<ItemAura> definition = ItemAuraService.find(registries, stack).orElse(null);
        if (definition == null) return null;
        int capacity = ItemAuraService.capacity(registries, stack, context);
        if (capacity <= 0) return null;
        int count = Math.max(1, stack.getCount());
        double intakeSpeed = definition.value().consumeSpeed().evaluate(context) * count;
        double costSpeed = definition.value().releaseSpeed().evaluate(context) * count;
        if (!Double.isFinite(intakeSpeed) || intakeSpeed <= 0.0D) return null;
        // What is in the stack is its own answer when it has one: a written store files its amounts under the
        // auras they count, so a definition that has since been re-typed cannot reinterpret them - it simply
        // stops matching them. A store holding nothing, or one that was drained, leaves the declaration to say
        // which aura this is, and the definition supplies the size either way.
        SpiritStorageComponent component = stack.get(MxtDataComponents.SPIRIT_STORAGE);
        Holder<Aura> aura = (component == null ? Optional.<Holder<Aura>>empty() : component.soleAura())
                .orElse(definition.value().type());
        int stored = component == null ? capacity : Mth.clamp(component.get(aura), 0, capacity);
        // The whole units a tick moves, and what one of them costs: the ratio of the two speeds, which is the
        // same for one item and for a stack of them.
        double unitCost = !Double.isFinite(costSpeed) || costSpeed <= 0.0D ? 0.0D : costSpeed / intakeSpeed;
        return new Charge(aura, stored, capacity,
                Math.max(1, (int) Math.floor(intakeSpeed)), unitCost);
    }

    /**
     * The same reading against the empty formula context - what a hold is sized by.
     */
    public static @Nullable Charge resolve(Provider registries, ItemStack stack) {
        return resolve(registries, stack, FormulaContext.EMPTY);
    }

    /**
     * Whether there is nothing left to pour into this stack - the question a caller means by "is it full". A
     * store the item describes itself may take several auras and only the item can say whether all of them are
     * full, so it is asked of the store rather than of the one aura {@link #resolve} answers for.
     */
    public static boolean full(Provider registries, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemAuraAccess)) return false;
        SpiritPour pour = stack.getItem() instanceof UseItemAuraAccess manual
                ? manual.pour(registries, stack).orElse(null) : null;
        if (pour != null) return pour.full();
        Charge charge = resolve(registries, stack);
        return charge != null && charge.full();
    }

    /**
     * The colour a charge percentage is shown in, from full to empty. Shared so the tooltip, the Jade display
     * and the action bar cannot drift apart.
     */
    public static int color(int percentage) {
        if (percentage >= 75) return 0xFF55FF55;
        if (percentage >= 50) return 0xFFFFFF55;
        if (percentage >= 25) return 0xFFFFAA00;
        return 0xFFFF5555;
    }

    private static void showProgress(LivingEntity entity, @Nullable Charge charge) {
        if (charge == null) return;
        int percentage = charge.percentage();
        show(entity, Component.translatable("actionbar.mxt.charge.progress", TooltipText.number(charge.stored()),
                        TooltipText.number(charge.capacity()),
                        Component.literal(percentage + "%").withColor(color(percentage)))
                .withStyle(ChatFormatting.AQUA), "progress:" + percentage);
    }

    /**
     * Writes one action-bar line per holder, and only when it says something the last one did not: a hold that
     * cannot go on says so once instead of every tick, and a number that has not moved is not rewritten.
     */
    private static void show(LivingEntity entity, Component line, String code) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (code.equals(LAST_LINE.put(player.getUUID(), code))) return;
        player.sendSystemMessage(line, true);
    }

    /**
     * One item's store as one tick of pouring sees it: the aura being poured, what it holds, what it can hold,
     * how many whole units a tick moves, and what one of them costs the holder.
     */
    public record Charge(Holder<Aura> aura, int stored, int capacity, int intake, double costPerUnit) {
        /**
         * How long a pour may last at most. A charge is sized by the time it takes to fill the item, and this
         * is the ceiling on that: an item whose capacity dwarfs its intake would otherwise ask for a hold
         * nobody would ever finish, so past this the gesture stops and is repeated instead.
         */
        public static final int MAX_HOLD_TICKS = 200;

        public int percentage() {
            return this.capacity <= 0 ? 0 : (int) Math.round(this.stored * 100.0D / this.capacity);
        }

        public boolean full() {
            return this.stored >= this.capacity;
        }

        /**
         * The ticks a pour lasts: the time this item takes to fill from empty, capped. A stack with nothing to
         * pour is not a hold at all, which is what {@link HoldBinding#NO_HOLD} already means - so a filled item
         * is never armed, and a hold that fills one stops making its sound while the pose runs out.
         * <p>
         * Sized by the capacity rather than by what is missing, even though the deficit is what actually gets
         * poured, because the length has to be a function of the stack and not of the tick: the sound's cadence
         * is read from it again on every tick of the hold, and a length that shrank as the item filled would
         * take the sound away with it. A partly charged item therefore fills before its pose ends, which is
         * visible as a hold that stops moving - and is why the pose can be released early.
         */
        public int holdTicks() {
            if (this.full() || this.intake <= 0) return HoldBinding.NO_HOLD;
            return Mth.clamp((int) Math.ceil((double) this.capacity / this.intake), 1, MAX_HOLD_TICKS);
        }
    }
}
