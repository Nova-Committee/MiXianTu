package com.iafenvoy.mxt.item.block.entity;

import com.iafenvoy.mxt.registry.MxtBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class TradeStationBlockEntity extends StationBlockEntity {
    public TradeStationBlockEntity(BlockPos pos, BlockState state) {
        super(MxtBlockEntities.TRADE_STATION.get(), pos, state, true);
    }

    @Override
    public boolean isSystemStation() {
        return false;
    }
}
