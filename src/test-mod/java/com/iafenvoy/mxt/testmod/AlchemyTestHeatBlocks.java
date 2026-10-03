package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.api.AlchemyHeatSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Test heat sources, none of them consumed and none of them craftable. Their numbers come from the test pack's
 * {@code mxt_test:heat_source} entries - a tag entry plus a higher-priority override - except the advanced one,
 * which answers for itself and is what proves the API wins over the table.
 */
public final class AlchemyTestHeatBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MxtTestMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MxtTestMod.MOD_ID);
    /** 150 maximum, 40 degrees per tick, from the block tag entry. Matches the old furnace heating rate. */
    public static final DeferredBlock<Block> FIRE = register("alchemy_test_fire");
    /** 80 maximum, 10 degrees per tick: the entry for this block outranks the tag's. Cannot reach a 100±5 recipe. */
    public static final DeferredBlock<Block> WEAK_FIRE = register("alchemy_weak_fire");
    /** 250 / 25 while lit and nothing while unlit, whatever the table says. */
    public static final DeferredBlock<Block> ADVANCED_FIRE = BLOCKS.register("alchemy_advanced_fire",
            () -> new AdvancedFire(properties("alchemy_advanced_fire")));
    /** The advanced block's own switch; the probes flip it to see the API answer change. */
    public static final BooleanProperty ADVANCED_LIT = BooleanProperty.create("lit");

    private AlchemyTestHeatBlocks() {
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
        return BlockBehaviour.Properties.ofFullCopy(Blocks.MAGMA_BLOCK)
                .setId(ResourceKey.create(Registries.BLOCK, id(path)));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, path);
    }

    /**
     * The API branch of the heat lookup: the answer depends on the block's own state, which no table entry can say.
     */
    private static final class AdvancedFire extends Block implements AlchemyHeatSource {
        private AdvancedFire(BlockBehaviour.Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(ADVANCED_LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(ADVANCED_LIT);
        }

        @Override
        public double maxTemperature(BlockState state, ServerLevel level, BlockPos pos) {
            return state.getValue(ADVANCED_LIT) ? 250.0D : 0.0D;
        }

        @Override
        public double heatingPerTick(BlockState state, ServerLevel level, BlockPos pos) {
            return state.getValue(ADVANCED_LIT) ? 25.0D : 0.0D;
        }
    }
}
