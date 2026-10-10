package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.api.AuraAccess;
import com.iafenvoy.mxt.api.ItemAuraAccess;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.item.ResourceCapacityComponent;
import com.iafenvoy.mxt.data.item.ResourceContainerComponent;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resource.ResourceService.Result;
import com.iafenvoy.mxt.runtime.spirit.SpiritChargeService;
import com.iafenvoy.mxt.util.TooltipText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A reusable container that pours arbitrary registered resources into its holder while it is held down, one unit of
 * each resource a tick. Holding it down is the whole of its own use: there is no mode and no click that moves
 * anything. How much of each resource fits is the {@code mxt:resource_capacity} it was handed, resource by resource,
 * and a resource nobody wrote a cap for has no room at all.
 *
 * <p>It is also an {@link ItemAuraAccess}: that is the protocol a display stand pours through, and the one a spirit
 * burst pours through when its holder is carrying a vessel instead of shooting at something. What those move is an
 * aura, an aura is counted in a resource ({@code Aura#resource}), so a pour lands in the container as that resource.
 */
public final class SpiritVesselItem extends Item implements ItemAuraAccess {
    /**
     * How much of each resource inside leaves per tick of the hold. Pouring out is the charge gesture's mirror, so it
     * moves at the same pace.
     */
    public static final int POUR_PER_TICK = 1;

    public SpiritVesselItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // The pour is a hold, and the hold module armed the use cycle on the way in: that click belongs to it.
        if (stack.has(DataComponents.CONSUMABLE)) return super.use(level, player, hand);
        if (!(player instanceof ServerPlayer)) return InteractionResult.SUCCESS;
        // A sneaking click is never a hold, so it is left to whatever else the player is doing.
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        // Nothing was armed because there is nothing to pour, which is the one thing this click can say.
        ItemFeedback.send(player, Component.translatable("item.mxt.spirit_vessel.release_failed"));
        return InteractionResult.FAIL;
    }

    /**
     * One tick of the hold: each resource inside moves into the holder's own account, at most {@link #POUR_PER_TICK}
     * of it, and the account's own bounds decide how much of that it takes. Returns whether anything moved, so a
     * vessel that ran dry falls silent rather than reporting a pour that never happened.
     */
    public static boolean pourTick(LivingEntity holder, ItemStack stack) {
        ResourceContainerComponent container = stack.getOrDefault(MxtDataComponents.RESOURCE_CONTAINER, ResourceContainerComponent.EMPTY);
        if (container.values().isEmpty()) return false;
        Object2DoubleMap<Holder<Resource>> values = new Object2DoubleOpenHashMap<>(container.values());
        ResourceHolderAttachment account = holder.getData(MxtAttachments.RESOURCE_HOLDER);
        boolean changed = false;
        for (Object2DoubleMap.Entry<Holder<Resource>> entry : values.object2DoubleEntrySet()) {
            double wanted = Math.min(entry.getDoubleValue(), POUR_PER_TICK);
            double before = account.get(entry.getKey());
            Result result = ResourceService.change(account, entry.getKey(), wanted,
                    ResourceService.formulaContext(holder, entry.getKey(), FormulaContext.EMPTY));
            if (!result.valid()) continue;
            double accepted = result.value() - before;
            if (accepted <= 0.0D) continue;
            double remaining = entry.getDoubleValue() - accepted;
            if (remaining <= 0.0D) values.removeDouble(entry.getKey());
            else values.put(entry.getKey(), remaining);
            changed = true;
        }
        if (changed) stack.set(MxtDataComponents.RESOURCE_CONTAINER, new ResourceContainerComponent(values));
        return changed;
    }

    /**
     * Everything the vessel holds right now, which is what "is there anything to pour" reads.
     */
    public static double totalStored(ItemStack stack) {
        double total = 0.0D;
        for (double amount : stack.getOrDefault(MxtDataComponents.RESOURCE_CONTAINER, ResourceContainerComponent.EMPTY).values().values())
            total += amount;
        return total;
    }

    /**
     * The largest cap the vessel was written with, which is what sizes its hold: a cap does not move while pouring,
     * where the amount inside does.
     */
    public static double maxCapacity(ItemStack stack) {
        double max = 0.0D;
        for (double cap : stack.getOrDefault(MxtDataComponents.RESOURCE_CAPACITY, ResourceCapacityComponent.EMPTY).values().values())
            max = Math.max(max, cap);
        return max;
    }

    // The line a burst writes when what it poured went into the vessel instead of a target: the same reading the
    // charge gesture shows, with this vessel's numbers for the resource that aura is counted in.
    public static void showCharge(LivingEntity holder, ItemStack stack, Holder<Resource> resource) {
        if (!(holder instanceof ServerPlayer player)) return;
        double capacity = stack.getOrDefault(MxtDataComponents.RESOURCE_CAPACITY, ResourceCapacityComponent.EMPTY).capacityOf(resource);
        double stored = stack.getOrDefault(MxtDataComponents.RESOURCE_CONTAINER, ResourceContainerComponent.EMPTY).stored(resource);
        int percentage = capacity <= 0.0D ? 0 : (int) Math.round(stored * 100.0D / capacity);
        player.sendSystemMessage(Component.translatable("actionbar.mxt.charge.progress", TooltipText.number(stored), TooltipText.number(capacity),
                Component.literal(percentage + "%").withColor(SpiritChargeService.color(percentage))).withStyle(ChatFormatting.AQUA), true);
    }

    @Override
    public Object2IntMap<Holder<Aura>> getCapacity(@Nullable LivingEntity entity, ItemStack stack) {
        // Naming auras takes a registry, so a caller without one gets nothing here; the aura-scoped question below
        // answers from the resource alone, and that is the one a stand asks.
        if (entity == null) return Object2IntMaps.emptyMap();
        Object2IntMap<Holder<Aura>> room = new Object2IntOpenHashMap<>();
        MxtDatapackRegistries.holdersOrEmpty(entity.level().registryAccess(), MxtResourceKeys.AURA).forEach(aura -> {
            int fits = room(stack, aura.value().resource());
            if (fits > 0) room.put(aura, fits);
        });
        return room;
    }

    @Override
    public int getCapacity(@Nullable LivingEntity entity, ItemStack stack, Holder<Aura> aura) {
        return room(stack, aura.value().resource());
    }

    @Override
    public int insert(@Nullable LivingEntity entity, ItemStack stack, Holder<Aura> aura, int amount, boolean simulate) {
        AuraAccess.requireNonNegative(amount);
        int moved = Math.min(amount, room(stack, aura.value().resource()));
        if (!simulate && moved > 0) {
            Holder<Resource> resource = aura.value().resource();
            ResourceContainerComponent container = stack.getOrDefault(MxtDataComponents.RESOURCE_CONTAINER, ResourceContainerComponent.EMPTY);
            stack.set(MxtDataComponents.RESOURCE_CONTAINER, container.with(resource, container.stored(resource) + moved));
        }
        return amount - moved;
    }

    @Override
    public int extract(@Nullable LivingEntity entity, ItemStack stack, Holder<Aura> aura, int amount, boolean simulate) {
        // A vessel is poured into, never drained through the store protocol: what leaves it leaves through the hold,
        // where the holder's own account is on the other side.
        AuraAccess.requireNonNegative(amount);
        return amount;
    }

    // Whole aura units only, and only as far as the cap written for the resource that aura is counted in.
    private static int room(ItemStack stack, Holder<Resource> resource) {
        double room = stack.getOrDefault(MxtDataComponents.RESOURCE_CAPACITY, ResourceCapacityComponent.EMPTY).capacityOf(resource)
                - stack.getOrDefault(MxtDataComponents.RESOURCE_CONTAINER, ResourceContainerComponent.EMPTY).stored(resource);
        return room <= 0.0D ? 0 : (int) Math.floor(room);
    }
}
