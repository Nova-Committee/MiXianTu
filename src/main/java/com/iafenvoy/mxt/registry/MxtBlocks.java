package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.item.block.ChequeTableBlock;
import com.iafenvoy.mxt.item.block.ExchangeStationBlock;
import com.iafenvoy.mxt.item.block.SystemStationBlock;
import com.iafenvoy.mxt.item.block.TradeStationBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;

/**
 * Code-owned economy blocks and their block items.
 */
public final class MxtBlocks {
    public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(MiXianTu.MOD_ID);
    public static final DeferredBlock<ExchangeStationBlock> EXCHANGE_STATION = register("exchange_station", ExchangeStationBlock::new);
    public static final DeferredBlock<ChequeTableBlock> CHEQUE_TABLE = register("cheque_table", ChequeTableBlock::new);
    public static final DeferredBlock<TradeStationBlock> TRADE_STATION = register("trade_station", TradeStationBlock::new);
    public static final DeferredBlock<SystemStationBlock> SYSTEM_STATION = register("system_station",
            properties -> new SystemStationBlock(properties.strength(-1.0F, 3_600_000.0F).noLootTable()));
    /**
     * Grade is derived from the connected vein at runtime; no per-block grade state is persisted.
     */
    public static final DeferredBlock<DropExperienceBlock> SPIRIT_STONE_ORE = register("spirit_stone_ore",
            properties -> new DropExperienceBlock(ConstantInt.of(1), properties.strength(3.0F, 3.0F).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> SPIRIT_STONE_BLOCK = registerSolid("spirit_stone_block",
            properties -> new Block(properties.strength(5.0F, 6.0F).requiresCorrectToolForDrops()));

    public static <T extends Block> DeferredBlock<T> register(String path, Function<Properties, T> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path));
        DeferredBlock<T> block = REGISTRY.register(path, () -> factory.apply(Properties.ofFullCopy(Blocks.CRAFTING_TABLE).noOcclusion().setId(key)));
        MxtItems.registerBlockItem(path, block);
        return block;
    }

    /** Registers a full opaque cube without disabling neighbour face culling. */
    private static <T extends Block> DeferredBlock<T> registerSolid(String path, Function<Properties, T> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path));
        DeferredBlock<T> block = REGISTRY.register(path, () -> factory.apply(Properties.ofFullCopy(Blocks.IRON_BLOCK).setId(key)));
        MxtItems.registerBlockItem(path, block);
        return block;
    }

    private MxtBlocks() {
    }
}
