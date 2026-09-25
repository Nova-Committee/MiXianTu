package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.api.ItemAuraAccess;
import com.iafenvoy.mxt.api.UseItemAuraAccess;
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
 * Charges items implementing {@link UseItemAuraAccess} by pouring the holder's own aura into them through the
 * item's {@link ItemAuraAccess#insert}. The rates are the {@code item_aura} pair read the other way round, so a
 * pour-then-burn round trip returns exactly what it cost: pouring can only store aura, never manufacture it.
 */
@EventBusSubscriber
public final class SpiritChargeService {
    // Keyed by the code rather than the message, because the message is rebuilt from the charge each time.
    private static final Map<UUID, String> LAST_LINE = new ConcurrentHashMap<>();

    // The gesture's own rate, used for a store that declares only what it holds; an item described by the
    // shared item_aura definition states its own pair instead.
    public static final int POUR_INTAKE_PER_TICK = 1;
    public static final double POUR_COST_PER_UNIT = 1.0D;

    private SpiritChargeService() {
    }

    // Registered once, at construction: the hold module drives the gesture and never learns what an item aura is.
    public static void initialize() {
        HoldLookup.register(registries -> List.of(SpiritChargeHold.INSTANCE));
    }

    // This never cancels: an item that is also a technique manual, carries a talisman's invocation or declares
    // its own use belongs to whatever claims it first.
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

    // Server only: the charge is item state, and the client's copy arrives through the component sync.
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
        // Which aura is poured does not depend on the holder, so it is read first: the rates are read against
        // that aura's own context.
        Charge probe = resolve(registries, stack);
        if (probe == null) return;
        Holder<Aura> aura = probe.aura();
        // The pool that pays is the resource the aura is counted in - the one conversion needing no lookup.
        Holder<Resource> resource = aura.value().resource();
        FormulaContext context = ResourceService.formulaContext(entity, resource, FormulaContext.of(entity));
        Charge charge = resolve(registries, stack, context);
        if (charge == null) return;

        int want = charge.intake();
        if (want <= 0) return;
        // What the item will really take, asked before anything is paid for: a full item, or one storing
        // another aura, answers zero.
        int units = want - access.insert(entity, stack, aura, want, true);
        if (units <= 0) {
            show(entity, Component.translatable("actionbar.mxt.charge.full"), "full");
            return;
        }

        double unitCost = charge.costPerUnit();
        if (unitCost > 0.0D) {
            ResourceHolderAttachment resources = entity.getData(MxtAttachments.RESOURCE_HOLDER);
            // Decided before anything moves: a partial payment that bought no whole unit would be aura taken
            // for nothing.
            units = Math.min(units, (int) Math.floor(resources.get(resource) / unitCost));
            if (units <= 0) {
                show(entity, Component.translatable("actionbar.mxt.charge.insufficient"), "insufficient");
                return;
            }
            double before = resources.get(resource);
            // Spent through the resource service so the definition's bounds and audit trail apply: what is
            // charged for is what was actually taken, and a bound may only trim the payment, never raise it.
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
        // The item decides whether that was the moment it filled, so a talisman carrier can spend itself here.
        access.onCharged(SpiritSource.of(entity), stack);
    }

    // Answers null for anything this module does not charge: an empty stack, a non-ItemAuraAccess item, one that
    // matches no store, and one whose numbers cannot move a tick. The context is the caller's.
    public static @Nullable Charge resolve(Provider registries, ItemStack stack, FormulaContext context) {
        // An item_aura definition describes items, so a plain item one happens to match - a crystal that only
        // burns as fuel - must not read as pourable.
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemAuraAccess)) return null;
        SpiritPour pour = stack.getItem() instanceof UseItemAuraAccess manual
                ? manual.pour(registries, stack).orElse(null) : null;
        if (pour != null) {
            Entry entry = pour.active().orElse(null);
            if (entry == null) return null;
            return new Charge(entry.aura(), entry.stored(), entry.capacity(),
                    POUR_INTAKE_PER_TICK, POUR_COST_PER_UNIT);
        }
        // The shared reading: one definition describes one item, so a stack moves as many times as it has
        // items, and a missing component is a pristine, fully charged item.
        Holder<ItemAura> definition = ItemAuraService.find(registries, stack).orElse(null);
        if (definition == null) return null;
        int capacity = ItemAuraService.capacity(registries, stack, context);
        if (capacity <= 0) return null;
        int count = Math.max(1, stack.getCount());
        double intakeSpeed = definition.value().consumeSpeed().evaluate(context) * count;
        double costSpeed = definition.value().releaseSpeed().evaluate(context) * count;
        if (!Double.isFinite(intakeSpeed) || intakeSpeed <= 0.0D) return null;
        // A written store files its amounts under the auras they count, so a re-typed definition simply stops
        // matching them; a store holding nothing leaves the declaration to say which aura this is.
        SpiritStorageComponent component = stack.get(MxtDataComponents.SPIRIT_STORAGE);
        Holder<Aura> aura = (component == null ? Optional.<Holder<Aura>>empty() : component.soleAura())
                .orElse(definition.value().type());
        int stored = component == null ? capacity : (int) Math.clamp(Math.floor(component.get(aura)), 0.0D, capacity);
        // What one whole unit costs: the ratio of the two speeds, the same for one item and for a stack.
        double unitCost = !Double.isFinite(costSpeed) || costSpeed <= 0.0D ? 0.0D : costSpeed / intakeSpeed;
        return new Charge(aura, stored, capacity,
                Math.max(1, (int) Math.floor(intakeSpeed)), unitCost);
    }

    // The same reading against the empty formula context - what a hold is sized by.
    public static @Nullable Charge resolve(Provider registries, ItemStack stack) {
        return resolve(registries, stack, FormulaContext.EMPTY);
    }

    // A store the item describes itself may take several auras and only the item can say whether all of them
    // are full, so that question is asked of the store rather than of the one aura resolve() answers for.
    public static boolean full(Provider registries, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemAuraAccess)) return false;
        SpiritPour pour = stack.getItem() instanceof UseItemAuraAccess manual
                ? manual.pour(registries, stack).orElse(null) : null;
        if (pour != null) return pour.full();
        Charge charge = resolve(registries, stack);
        return charge != null && charge.full();
    }

    // Shared so the tooltip, the Jade display and the action bar cannot drift apart.
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

    // Writes one action-bar line per holder, and only when it differs from the last one, so a refusal repeated
    // every tick of a hold is said once.
    private static void show(LivingEntity entity, Component line, String code) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (code.equals(LAST_LINE.put(player.getUUID(), code))) return;
        player.sendSystemMessage(line, true);
    }

    // One item's store as one tick of pouring sees it.
    public record Charge(Holder<Aura> aura, int stored, int capacity, int intake, double costPerUnit) {
        // Ceiling on how long a pour may last: an item whose capacity dwarfs its intake would otherwise ask for
        // a hold nobody would ever finish, so past this the gesture stops and is repeated instead.
        public static final int MAX_HOLD_TICKS = 200;

        public int percentage() {
            return this.capacity <= 0 ? 0 : (int) Math.round(this.stored * 100.0D / this.capacity);
        }

        public boolean full() {
            return this.stored >= this.capacity;
        }

        // Sized by the capacity rather than the deficit, because the length must be a function of the stack and not
        // of the tick: the sound's cadence is read from it every tick, and a shrinking length would drop it.
        public int holdTicks() {
            if (this.full() || this.intake <= 0) return HoldBinding.NO_HOLD;
            return Mth.clamp((int) Math.ceil((double) this.capacity / this.intake), 1, MAX_HOLD_TICKS);
        }
    }
}
