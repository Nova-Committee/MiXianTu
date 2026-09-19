package com.iafenvoy.mxt.runtime.curse;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment;
import com.iafenvoy.mxt.compat.CuriosIntegration;
import com.iafenvoy.mxt.data.action.builtin.entity.ApplyCurseAction;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.data.curse.CurseContainerComponent;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registers loaded cursed entities and services only their due lifecycle work.
 * <p>
 * It also owns the item side of a curse: a stack carrying {@code mxt:curse_container} transfers its curses to
 * whoever equips it, and takes them back when it is removed. The transfer runs through the ordinary transaction,
 * so the carried curses obey their own conditions, stacking and duration like any other.
 */
@EventBusSubscriber
public final class CurseEventBridge {
    /**
     * The slots a carried curse is transferred from. The held slots count, so a cursed blade curses its wielder.
     */
    private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final Identifier CURIOS_SOURCE = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "curios_equipment");
    private static final String EQUIPMENT_PATH = "equipment/";
    private static final CurseContainerComponent EMPTY_CONTAINER = new CurseContainerComponent();
    private static final long RECONCILE_INTERVAL = 20L;

    private CurseEventBridge() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        CurseScheduler.reschedule(event.getEntity());
        if (event.getEntity() instanceof LivingEntity living) syncCarried(living);
    }

    /**
     * Answers an equipment change at once, rather than waiting for the slow cadence.
     */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        syncCarried(event.getEntity());
    }

    /**
     * The slow cadence. It is what makes Curios carry curses, and what lets a carried curse come back by itself
     * after it expired, was cleansed, or was released by the source that held it before.
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()) return;
        if (entity.level().getGameTime() % RECONCILE_INTERVAL != 0L) return;
        syncCarried(entity);
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (event.getLevel() instanceof ServerLevel level) CurseScheduler.onLevelTick(level);
    }

    /**
     * Reconciles the curses an entity's gear carries with the curses it holds, per source and the same way the
     * ability model reconciles its equipment: a stack applies what nobody holds yet, joins the ledger for the
     * curses that are already there, and releases whatever it no longer declares. A curse only leaves when that
     * release was its last source, so gear and an ability may hold the same curse at once.
     * <p>
     * Everything here is idempotent, so the equipment event and the slow cadence can both call it.
     */
    public static void syncCarried(LivingEntity entity) {
        Map<Identifier, List<ApplyCurseAction>> declared = new LinkedHashMap<>();
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            List<ApplyCurseAction> carried = carried(stack);
            if (!carried.isEmpty()) declared.put(source(slot, stack), carried);
        }
        List<ApplyCurseAction> curios = new ArrayList<>();
        for (ItemStack stack : CuriosIntegration.equipped(entity)) curios.addAll(carried(stack));
        if (!curios.isEmpty()) declared.put(CURIOS_SOURCE, curios);
        // Nothing is carried and there is nothing of ours to release: reading the attachment here would create
        // one on every living entity in the world for nothing.
        if (declared.isEmpty() && !entity.hasData(MxtAttachments.CURSE_HOLDER.get())) return;

        CurseHolderAttachment holder = entity.getData(MxtAttachments.CURSE_HOLDER);
        long gameTime = entity.level().getGameTime();
        FormulaContext context = FormulaContext.of(entity);
        for (Map.Entry<Identifier, List<ApplyCurseAction>> entry : declared.entrySet()) {
            Identifier source = entry.getKey();
            Set<Holder<Curse>> wanted = new LinkedHashSet<>();
            for (ApplyCurseAction action : entry.getValue()) {
                Holder<Curse> curse = action.curse();
                wanted.add(curse);
                // Only a curse nobody holds yet needs applying. Joining one that is already there is the ledger's
                // business, which is what stops carried gear from fighting another source over the same curse.
                if (holder.instances().containsKey(curse)) continue;
                double stacks = action.stacks().evaluate(context);
                if (!Double.isFinite(stacks) || stacks < 1.0D || stacks > 256.0D) continue;
                Optional<Long> duration = action.durationTicks().flatMap(provider -> {
                    double value = provider.evaluate(context);
                    return Double.isFinite(value) && value >= 0.0D && value <= Long.MAX_VALUE
                            ? Optional.of(Math.round(value)) : Optional.empty();
                });
                CurseService.applyWithDuration(entity, curse, (int) Math.round(stacks), gameTime, context, source, duration);
            }
            CurseService.reconcileSource(entity, source, wanted, gameTime, context);
        }
        // A source whose gear is gone entirely is not in that map at all, so it is released on its own.
        for (Identifier known : holder.sources().allSources()) {
            if (!isEquipmentSource(known) || declared.containsKey(known)) continue;
            CurseService.reconcileSource(entity, known, List.of(), gameTime, context);
        }
    }

    private static List<ApplyCurseAction> carried(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        return stack.getOrDefault(MxtDataComponents.CURSE_CONTAINER.get(), EMPTY_CONTAINER).curses();
    }

    private static boolean isEquipmentSource(Identifier source) {
        return source.getNamespace().equals(MiXianTu.MOD_ID)
                && (source.getPath().startsWith(EQUIPMENT_PATH) || source.equals(CURIOS_SOURCE));
    }

    /**
     * One equipped stack's own source, in the same shape the ability model uses for its grants.
     */
    private static Identifier source(EquipmentSlot slot, ItemStack stack) {
        Identifier item = stack.isEmpty() ? Identifier.fromNamespaceAndPath("minecraft", "air")
                : BuiltInRegistries.ITEM.getKey(stack.getItem());
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID,
                EQUIPMENT_PATH + slot.getName() + "/" + item.getNamespace() + "/" + item.getPath());
    }
}
