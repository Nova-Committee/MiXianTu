package com.iafenvoy.mxt.testmod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Test herb blocks. Nothing about them is herb-specific: they are ordinary blocks that the {@code mxt_test} herb
 * definitions claim by block, which is the whole point of the current design - a pack supplies the block, the
 * framework supplies the clock. {@code alchemy_test_soil} is the planted one; the {@code alchemy_test_wild_*} pair
 * stand for naturally generated plants that were never placed, so a probe can exercise the roll-on-first-read path.
 */
public final class HerbTestBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MxtTestMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MxtTestMod.MOD_ID);
    public static final DeferredBlock<Block> SOIL = register("alchemy_test_soil");
    public static final DeferredBlock<Block> WILD_GROWER = register("alchemy_test_wild_grower");
    public static final DeferredBlock<Block> WILD_ROLLED = register("alchemy_test_wild_rolled");

    private HerbTestBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    private static DeferredBlock<Block> register(String path) {
        DeferredBlock<Block> block = BLOCKS.register(path, () -> new Block(properties(path)));
        ITEMS.register(path, () -> new BlockItem(block.get(),
                new Item.Properties().useBlockDescriptionPrefix().setId(ResourceKey.create(Registries.ITEM, id(path)))));
        return block;
    }

    private static BlockBehaviour.Properties properties(String path) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.FERN)
                .setId(ResourceKey.create(Registries.BLOCK, id(path)));
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, path);
    }
}
