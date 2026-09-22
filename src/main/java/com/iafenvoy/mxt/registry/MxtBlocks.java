package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.item.block.*;
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
    public static final DeferredBlock<Block> SPIRIT_STONE_BLOCK = registerSolid("spirit_stone_block", properties -> new Block(properties.strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredBlock<SpiritCraftingTableBlock> SPIRIT_CRAFTING_TABLE = register("spirit_crafting_table", SpiritCraftingTableBlock::new);
    public static final DeferredBlock<ForgingTableBlock> FORGING_TABLE = registerForging("forging_table", ForgingTableBlock::new);
    public static final DeferredBlock<DisplayStandBlock> OAK_DISPLAY_STAND = register("oak_display_stand", DisplayStandBlock::new);
    public static final DeferredBlock<DisplayStandBlock> BIRCH_DISPLAY_STAND = register("birch_display_stand", DisplayStandBlock::new);
    public static final DeferredBlock<DisplayStandBlock> SPRUCE_DISPLAY_STAND = register("spruce_display_stand", DisplayStandBlock::new);
    public static final DeferredBlock<DisplayStandBlock> JUNGLE_DISPLAY_STAND = register("jungle_display_stand", DisplayStandBlock::new);
    public static final DeferredBlock<DisplayStandBlock> ACACIA_DISPLAY_STAND = register("acacia_display_stand", DisplayStandBlock::new);
    public static final DeferredBlock<DisplayStandBlock> DARK_OAK_DISPLAY_STAND = register("dark_oak_display_stand", DisplayStandBlock::new);
    public static final DeferredBlock<RiftBlock> RIFT = registerRift("rift", RiftBlock::new);

    public static <T extends Block> DeferredBlock<T> register(String path, Function<Properties, T> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path));
        DeferredBlock<T> block = REGISTRY.register(path, () -> factory.apply(Properties.ofFullCopy(Blocks.CRAFTING_TABLE).noOcclusion().setId(key)));
        MxtItems.registerBlockItem(path, block);
        return block;
    }

    // A full opaque cube: no noOcclusion(), so neighbour face culling stays on.
    private static <T extends Block> DeferredBlock<T> registerSolid(String path, Function<Properties, T> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path));
        DeferredBlock<T> block = REGISTRY.register(path, () -> factory.apply(Properties.ofFullCopy(Blocks.IRON_BLOCK).setId(key)));
        MxtItems.registerBlockItem(path, block);
        return block;
    }

    // Deliberately not a copy of the vanilla smithing table, which is wooden despite its stone-looking texture:
    // a forging station is stone-sounding, stone-coloured and needs a pickaxe to drop.
    private static <T extends Block> DeferredBlock<T> registerForging(String path, Function<Properties, T> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path));
        DeferredBlock<T> block = REGISTRY.register(path, () -> factory.apply(Properties.of()
                .setId(key)
                .mapColor(MapColor.STONE)
                .strength(3.5F)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops()
                .noOcclusion()));
        MxtItems.registerBlockItem(path, block);
        return block;
    }

    // The block itself is invisible - only its block entity is drawn. Unbreakable by ordinary means, lit, no
    // collision, and neighbours are asked not to cull faces against it. It still gets an ordinary block item,
    // which is what places one; the anchor item only ever adjusts rifts that already exist.
    private static <T extends Block> DeferredBlock<T> registerRift(String path, Function<Properties, T> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path));
        DeferredBlock<T> block = REGISTRY.register(path, () -> factory.apply(Properties.of()
                .setId(key)
                .mapColor(MapColor.COLOR_BLACK)
                .strength(-1.0F, 3_600_000.0F)
                .sound(SoundType.GLASS)
                .lightLevel(state -> 15)
                .noCollision()
                .noOcclusion()
                .noTerrainParticles()));
        MxtItems.registerBlockItem(path, block);
        return block;
    }
}
