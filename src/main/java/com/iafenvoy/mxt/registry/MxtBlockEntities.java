package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.item.block.entity.SystemStationBlockEntity;
import com.iafenvoy.mxt.item.block.entity.TradeStationBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Persistent inventories for the two configurable trade stations.
 */
public final class MxtBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTRY = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TradeStationBlockEntity>> TRADE_STATION = REGISTRY.register("trade_station", () ->
            new BlockEntityType<>(TradeStationBlockEntity::new, MxtBlocks.TRADE_STATION.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SystemStationBlockEntity>> SYSTEM_STATION = REGISTRY.register("system_station", () ->
            new BlockEntityType<>(SystemStationBlockEntity::new, MxtBlocks.SYSTEM_STATION.get()));

    private MxtBlockEntities() {
    }
}
