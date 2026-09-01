package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.item.block.ChequeTableBlock;
import com.iafenvoy.mxt.item.block.DisplayStandBlock;
import com.iafenvoy.mxt.item.block.ExchangeStationBlock;
import com.iafenvoy.mxt.item.block.SystemStationBlock;
import com.iafenvoy.mxt.item.block.TradeStationBlock;
import com.iafenvoy.mxt.item.block.SpiritCraftingTableBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;

@SuppressWarnings("unused")
public final class MxtBlocks {
    public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(MiXianTu.MOD_ID);

    public static final DeferredBlock<ExchangeStationBlock> EXCHANGE_STATION = register("exchange_station", ExchangeStationBlock::new);
    public static final DeferredBlock<ChequeTableBlock> CHEQUE_TABLE = register("cheque_table", ChequeTableBlock::new);
    public static final DeferredBlock<TradeStationBlock> TRADE_STATION = register("trade_station", TradeStationBlock::new);
    public static final DeferredBlock<SystemStationBlock> SYSTEM_STATION = register("system_station", properties -> new SystemStationBlock(properties.strength(-1.0F, 3_600_000.0F).noLootTable()));
    public static final DeferredBlock<DropExperienceBlock> SPIRIT_STONE_ORE = register("spirit_stone_ore", properties -> new DropExperienceBlock(ConstantInt.of(1), properties.strength(3.0F, 3.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> SPIRIT_STONE_BLOCK = registerSolid("spirit_stone_block", properties -> new Block(properties.strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredBlock<SpiritCraftingTableBlock> SPIRIT_CRAFTING_TABLE = register("spirit_crafting_table", SpiritCraftingTableBlock::new);
    public static final DeferredBlock<DisplayStandBlock> OAK_DISPLAY_STAND = register("oak_display_stand", DisplayStandBlock::new);
    public static final DeferredBlock<DisplayStandBlock> BIRCH_DISPLAY_STAND = register("birch_display_stand", DisplayStandBlock::new);
    public static final DeferredBlock<DisplayStandBlock> SPRUCE_DISPLAY_STAND = register("spruce_display_stand", DisplayStandBlock::new);
    public static final DeferredBlock<DisplayStandBlock> JUNGLE_DISPLAY_STAND = register("jungle_display_stand", DisplayStandBlock::new);
    public static final DeferredBlock<DisplayStandBlock> ACACIA_DISPLAY_STAND = register("acacia_display_stand", DisplayStandBlock::new);
    public static final DeferredBlock<DisplayStandBlock> DARK_OAK_DISPLAY_STAND = register("dark_oak_display_stand", DisplayStandBlock::new);

    public static <T extends Block> DeferredBlock<T> register(String path, Function<Properties, T> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path));
        DeferredBlock<T> block = REGISTRY.register(path, () -> factory.apply(Properties.ofFullCopy(Blocks.CRAFTING_TABLE).noOcclusion().setId(key)));
        MxtItems.registerBlockItem(path, block);
        return block;
    }

    /**
     * Registers a full opaque cube without disabling neighbour face culling.
     */
    private static <T extends Block> DeferredBlock<T> registerSolid(String path, Function<Properties, T> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path));
        DeferredBlock<T> block = REGISTRY.register(path, () -> factory.apply(Properties.ofFullCopy(Blocks.IRON_BLOCK).setId(key)));
        MxtItems.registerBlockItem(path, block);
        return block;
    }
}
