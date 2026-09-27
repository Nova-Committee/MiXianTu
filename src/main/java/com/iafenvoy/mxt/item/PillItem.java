package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.data.item.PillBinding;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Generic pill carrier. Consumption, stack shrinkage and any remainder are the vanilla consumable component.
 * Effects are dispatched once from the use-finish event, not from this item.
 */
public class PillItem extends Item {
    // 32 ticks, the same length vanilla food uses, so a pack that does not override the component still completes.
    public static final Consumable CONSUMABLE = new Consumable(1.625F, ItemUseAnimation.EAT, SoundEvents.GENERIC_EAT, true, List.of());

    public PillItem(Properties properties) {
        super(properties.component(DataComponents.CONSUMABLE, CONSUMABLE));
    }
    @Override
    public @NonNull Component getName(@NonNull ItemStack stack) {
        Holder<PillBinding> pill = stack.get(MxtDataComponents.PILL.get());
        if (pill != null && pill.isBound()) return pill.value().name();
        return Component.translatable(this.getDescriptionId());
    }
}
