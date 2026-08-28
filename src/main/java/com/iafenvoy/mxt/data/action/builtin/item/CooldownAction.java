package com.iafenvoy.mxt.data.action.builtin.item;

import com.iafenvoy.mxt.data.context.action.ItemActionContext;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

public record CooldownAction(int ticks) implements ItemAction {
    public static final MapCodec<CooldownAction> CODEC = Codec.INT.fieldOf("ticks").xmap(CooldownAction::new, CooldownAction::ticks);

    @Override
    public void execute(@NonNull ItemActionContext ctx) {
        ItemStack stack = ctx.stack();
        if (ctx.holder() instanceof Player player && !stack.isEmpty() && this.ticks > 0)
            player.getCooldowns().addCooldown(stack, this.ticks);
    }

    @Override
    public @NonNull MapCodec<CooldownAction> codec() {
        return CODEC;
    }
}
