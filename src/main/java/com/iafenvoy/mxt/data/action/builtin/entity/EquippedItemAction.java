package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * Runs an item action against one equipped stack.
 */
public record EquippedItemAction(EquipmentSlot slot, ItemAction action) implements EntityAction {
    public static final MapCodec<EquippedItemAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EquipmentSlot.CODEC.fieldOf("slot").forGetter(EquippedItemAction::slot),
            ItemAction.CODEC.fieldOf("action").forGetter(EquippedItemAction::action)
    ).apply(i, EquippedItemAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        if (ctx.entity() instanceof LivingEntity living)
            this.action.execute(living, living.getItemBySlot(this.slot), ctx);
    }

    @Override
    public @NonNull MapCodec<EquippedItemAction> codec() {
        return CODEC;
    }
}
