package com.iafenvoy.mxt.data.action.builtin.block;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;

/**
 * Applies bone meal to the selected block.
 */
public record BonemealAction(boolean effect) implements BlockAction {
    public static final MapCodec<BonemealAction> CODEC = Codec.BOOL.optionalFieldOf("effect", true).xmap(BonemealAction::new, BonemealAction::effect);

    @Override
    public void execute(Level level, BlockPos pos, FormulaContext context) {
        if (BoneMealItem.growCrop(ItemStack.EMPTY, level, pos) && this.effect && !level.isClientSide())
            level.globalLevelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 0);
    }

    @Override
    public MapCodec<BonemealAction> codec() {
        return CODEC;
    }
}
