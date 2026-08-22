package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.registry.MxtDataComponents;

import com.iafenvoy.mxt.attachment.ResourceHolderComponent;
import com.iafenvoy.mxt.data.item.ResourceContainerComponent;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resource.ResourceService.Result;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

/**
 * A reusable container that transfers arbitrary registered resources to and from its holder.
 */
public final class SpiritVesselItem extends Item {
    private static final double CAPACITY_PER_RESOURCE = 1000.0D;

    public SpiritVesselItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer)) return InteractionResult.SUCCESS;
        ResourceContainerComponent container = stack.getOrDefault(MxtDataComponents.RESOURCE_CONTAINER, ResourceContainerComponent.EMPTY);
        Object2DoubleMap<Holder<Resource>> values = new Object2DoubleOpenHashMap<>(container.values());
        ResourceHolderComponent holder = player.getData(MxtAttachments.RESOURCE_HOLDER);
        boolean changed = player.isShiftKeyDown() ? store(holder, values, player) : release(holder, values, player);
        if (!changed) {
            ItemFeedback.send(player, Component.translatable(player.isShiftKeyDown()
                    ? "item.mxt.spirit_vessel.store_failed" : "item.mxt.spirit_vessel.release_failed"));
            return InteractionResult.FAIL;
        }
        stack.set(MxtDataComponents.RESOURCE_CONTAINER, new ResourceContainerComponent(values));
        ItemFeedback.send(player, Component.translatable(player.isShiftKeyDown()
                ? "item.mxt.spirit_vessel.stored" : "item.mxt.spirit_vessel.released"));
        return InteractionResult.SUCCESS_SERVER;
    }

    private static boolean store(ResourceHolderComponent holder, Object2DoubleMap<Holder<Resource>> values, Player player) {
        boolean changed = false;
        for (Entry<Holder<Resource>, Double> entry : new LinkedHashMap<>(holder.values()).entrySet()) {
            double stored = values.getOrDefault(entry.getKey(), 0.0D);
            double amount = Math.min(entry.getValue(), CAPACITY_PER_RESOURCE - stored);
            if (amount <= 0.0D) continue;
            Result result = ResourceService.change(holder, entry.getKey(), -amount, ResourceService.formulaContext(player, entry.getKey(), FormulaContext.EMPTY));
            if (!result.valid()) continue;
            double moved = entry.getValue() - result.value();
            if (moved <= 0.0D) continue;
            values.put(entry.getKey(), stored + moved);
            changed = true;
        }
        return changed;
    }

    private static boolean release(ResourceHolderComponent holder, Object2DoubleMap<Holder<Resource>> values, Player player) {
        boolean changed = false;
        for (Object2DoubleMap.Entry<Holder<Resource>> entry : values.object2DoubleEntrySet()) {
            double before = holder.get(entry.getKey());
            Result result = ResourceService.change(holder, entry.getKey(), entry.getDoubleValue(),
                    ResourceService.formulaContext(player, entry.getKey(), FormulaContext.EMPTY));
            if (!result.valid()) continue;
            double accepted = result.value() - before;
            if (accepted <= 0.0D) continue;
            double remaining = Math.max(0.0D, entry.getDoubleValue() - accepted);
            if (remaining == 0.0D) values.removeDouble(entry.getKey());
            else values.put(entry.getKey(), remaining);
            changed = true;
        }
        return changed;
    }
}
