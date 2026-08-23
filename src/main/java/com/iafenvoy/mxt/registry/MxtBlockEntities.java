package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.item.block.entity.SystemStationBlockEntity;
import com.iafenvoy.mxt.item.block.entity.TradeStationBlockEntity;
import com.iafenvoy.mxt.item.block.entity.DisplayStandBlockEntity;
import com.iafenvoy.mxt.item.block.entity.SpiritCraftingTableBlockEntity;
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
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DisplayStandBlockEntity>> DISPLAY_STAND = REGISTRY.register("display_stand", () ->
            new BlockEntityType<>(DisplayStandBlockEntity::new,
                    MxtBlocks.OAK_DISPLAY_STAND.get(), MxtBlocks.BIRCH_DISPLAY_STAND.get(), MxtBlocks.SPRUCE_DISPLAY_STAND.get(),
                    MxtBlocks.JUNGLE_DISPLAY_STAND.get(), MxtBlocks.ACACIA_DISPLAY_STAND.get(), MxtBlocks.DARK_OAK_DISPLAY_STAND.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpiritCraftingTableBlockEntity>> SPIRIT_CRAFTING_TABLE = REGISTRY.register("spirit_crafting_table", () ->
            new BlockEntityType<>(SpiritCraftingTableBlockEntity::new, MxtBlocks.SPIRIT_CRAFTING_TABLE.get()));

    private MxtBlockEntities() {
    }
}
