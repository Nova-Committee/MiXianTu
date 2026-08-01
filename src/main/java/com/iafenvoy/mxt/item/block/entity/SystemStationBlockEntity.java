package com.iafenvoy.mxt.item.block.entity;

import com.iafenvoy.mxt.registry.MxtBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class SystemStationBlockEntity extends StationBlockEntity {
    public SystemStationBlockEntity(BlockPos pos, BlockState state) {
        super(MxtBlockEntities.SYSTEM_STATION.get(), pos, state, false);
    }

    @Override
    public boolean isSystemStation() {
        return true;
    }
}
