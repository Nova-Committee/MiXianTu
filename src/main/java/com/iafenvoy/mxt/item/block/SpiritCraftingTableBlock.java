package com.iafenvoy.mxt.item.block;

import com.iafenvoy.mxt.item.block.entity.SpiritCraftingTableBlockEntity;
import com.iafenvoy.mxt.registry.MxtBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;

/**
 * A persistent crafting table that only accepts MiXianTu spirit recipes.
 */
public final class SpiritCraftingTableBlock extends EconomyWorkstationBlock implements EntityBlock {
    public SpiritCraftingTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @NonNull SpiritCraftingTableBlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new SpiritCraftingTableBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NonNull BlockState state, @NonNull BlockEntityType<T> type) {
        if (level.isClientSide() || type != MxtBlockEntities.SPIRIT_CRAFTING_TABLE.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<SpiritCraftingTableBlockEntity>) SpiritCraftingTableBlockEntity::serverTick;
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SpiritCraftingTableBlockEntity table)
            player.openMenu(table);
        return InteractionResult.SUCCESS;
    }

}
