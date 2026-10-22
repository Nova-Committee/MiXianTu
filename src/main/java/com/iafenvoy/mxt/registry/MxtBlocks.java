package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.item.block.*;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyInventoryKind;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
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
    public static final DeferredBlock<Block> SPIRIT_STONE_BLOCK = register("spirit_stone_block", properties -> new Block(properties.strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()), Properties.ofFullCopy(Blocks.IRON_BLOCK));
    public static final DeferredBlock<SpiritCraftingTableBlock> SPIRIT_CRAFTING_TABLE = register("spirit_crafting_table", SpiritCraftingTableBlock::new);
    public static final DeferredBlock<ForgingTableBlock> FORGING_TABLE = register("forging_table", ForgingTableBlock::new, Properties.of().mapColor(MapColor.STONE).strength(3.5F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredBlock<AlchemyFurnaceBlock> ALCHEMY_FURNACE = registerAlchemy("alchemy_furnace", properties -> new AlchemyFurnaceBlock(properties.lightLevel(state -> state.getValue(AlchemyFurnaceBlock.LIT) ? 10 : 0)));
    public static final DeferredBlock<AlchemyFurnaceCasingBlock> ALCHEMY_FURNACE_CASING = registerAlchemy("alchemy_furnace_casing", AlchemyFurnaceCasingBlock::new);
    public static final DeferredBlock<AlchemyFurnaceInventoryBlock> ALCHEMY_MAIN_INPUT = registerAlchemy("alchemy_main_input", properties -> new AlchemyFurnaceInventoryBlock(properties, AlchemyInventoryKind.MAIN));
    public static final DeferredBlock<AlchemyFurnaceInventoryBlock> ALCHEMY_AUXILIARY_INPUT = registerAlchemy("alchemy_auxiliary_input", properties -> new AlchemyFurnaceInventoryBlock(properties, AlchemyInventoryKind.AUXILIARY));
    public static final DeferredBlock<AlchemyFurnaceInventoryBlock> ALCHEMY_OUTPUT = registerAlchemy("alchemy_output", properties -> new AlchemyFurnaceInventoryBlock(properties, AlchemyInventoryKind.OUTPUT));
    public static final DeferredBlock<TalismanWorkstationBlock> TALISMAN_WORKSTATION = register("talisman_workstation", TalismanWorkstationBlock::new, Properties.ofFullCopy(Blocks.CRAFTING_TABLE));
    public static final DeferredBlock<DisplayStandBlock> OAK_DISPLAY_STAND = registerNoOcclusion("oak_display_stand", DisplayStandBlock::new);
    public static final DeferredBlock<DisplayStandBlock> BIRCH_DISPLAY_STAND = registerNoOcclusion("birch_display_stand", DisplayStandBlock::new);
    public static final DeferredBlock<DisplayStandBlock> SPRUCE_DISPLAY_STAND = registerNoOcclusion("spruce_display_stand", DisplayStandBlock::new);
    public static final DeferredBlock<DisplayStandBlock> JUNGLE_DISPLAY_STAND = registerNoOcclusion("jungle_display_stand", DisplayStandBlock::new);
    public static final DeferredBlock<DisplayStandBlock> ACACIA_DISPLAY_STAND = registerNoOcclusion("acacia_display_stand", DisplayStandBlock::new);
    public static final DeferredBlock<DisplayStandBlock> DARK_OAK_DISPLAY_STAND = registerNoOcclusion("dark_oak_display_stand", DisplayStandBlock::new);

    public static <T extends Block> DeferredBlock<T> registerNoOcclusion(String path, Function<Properties, T> factory) {
        return register(path, factory, Properties.ofFullCopy(Blocks.CRAFTING_TABLE).noOcclusion());
    }

    private static <T extends Block> DeferredBlock<T> registerAlchemy(String path, Function<Properties, T> factory) {
        return register(path, factory, Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.5F).noOcclusion());
    }

    public static <T extends Block> DeferredBlock<T> register(String path, Function<Properties, T> factory) {
        return register(path, factory, Properties.of());
    }

    public static <T extends Block> DeferredBlock<T> register(String path, Function<Properties, T> factory, Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path));
        DeferredBlock<T> block = REGISTRY.register(path, () -> factory.apply(properties.setId(key)));
        MxtItems.registerBlockItem(path, block);
        return block;
    }
}
