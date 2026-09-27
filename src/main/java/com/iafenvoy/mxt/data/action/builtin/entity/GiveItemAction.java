package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Datapack action, so the stack is an {@link ItemStackTemplate}: registries parse before item components are
 * bound and {@code ItemStack.CODEC} fails there. Each execution builds a fresh stack from it.
 */
public record GiveItemAction(ItemStackTemplate stack, ItemAction itemAction,
                             Optional<EquipmentSlot> preferredSlot) implements EntityAction {
    public static final MapCodec<GiveItemAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemStackTemplate.CODEC.fieldOf("stack").forGetter(GiveItemAction::stack),
            ItemAction.optionalCodec("item_action").forGetter(GiveItemAction::itemAction),
            EquipmentSlot.CODEC.optionalFieldOf("preferred_slot").forGetter(GiveItemAction::preferredSlot)
    ).apply(i, GiveItemAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        if (!(ctx.entity() instanceof Player player)) return;
        ItemStack result = this.stack.create();
        this.itemAction.execute(player, result, ctx);
        if (result.isEmpty()) return;
        if (this.preferredSlot.isPresent() && player.getItemBySlot(this.preferredSlot.get()).isEmpty())
            player.setItemSlot(this.preferredSlot.get(), result);
        else player.getInventory().placeItemBackInInventory(result);
    }

    @Override
    public @NonNull MapCodec<GiveItemAction> codec() {
        return CODEC;
    }
}
