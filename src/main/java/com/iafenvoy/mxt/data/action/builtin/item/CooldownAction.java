package com.iafenvoy.mxt.data.action.builtin.item;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public record CooldownAction(int ticks) implements ItemAction {
    public static final MapCodec<CooldownAction> CODEC = Codec.INT.fieldOf("ticks").xmap(CooldownAction::new, CooldownAction::ticks);

    @Override
    public void execute(Entity holder, ItemStack stack, FormulaContext context) {
        if (holder instanceof Player player && !stack.isEmpty() && this.ticks > 0)
            player.getCooldowns().addCooldown(stack, this.ticks);
    }

    @Override
    public MapCodec<CooldownAction> codec() {
        return CODEC;
    }
}
