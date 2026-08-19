package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public record GiveItemAction(ItemStack stack, Optional<ItemAction> itemAction,
                             Optional<EquipmentSlot> preferredSlot) implements EntityAction {
    public static final MapCodec<GiveItemAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemStack.CODEC.fieldOf("stack").forGetter(GiveItemAction::stack),
            ItemAction.CODEC.optionalFieldOf("item_action").forGetter(GiveItemAction::itemAction),
            EquipmentSlot.CODEC.optionalFieldOf("preferred_slot").forGetter(GiveItemAction::preferredSlot)
    ).apply(i, GiveItemAction::new));

    @Override
    public void execute(Entity entity, FormulaContext context) {
        if (!(entity instanceof Player player)) return;
        ItemStack result = this.stack.copy();
        this.itemAction.ifPresent(action -> action.execute(player, result, context));
        if (result.isEmpty()) return;
        if (this.preferredSlot.isPresent() && player.getItemBySlot(this.preferredSlot.get()).isEmpty())
            player.setItemSlot(this.preferredSlot.get(), result);
        else player.getInventory().placeItemBackInInventory(result);
    }

    @Override
    public MapCodec<GiveItemAction> codec() {
        return CODEC;
    }
}
