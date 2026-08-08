package com.iafenvoy.mxt.item.block;

import com.iafenvoy.mxt.item.block.entity.TradeStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class TradeStationBlock extends EconomyWorkstationBlock implements EntityBlock {
    public TradeStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @NonNull BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new TradeStationBlockEntity(pos, state);
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TradeStationBlockEntity station)
            player.openMenu(station);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state, @Nullable LivingEntity placer, @NonNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player && level.getBlockEntity(pos) instanceof TradeStationBlockEntity station)
            station.setOwner(player.getUUID());
    }
}
