package com.iafenvoy.mxt.probe.alchemy;

import com.iafenvoy.mxt.api.AlchemyWorkstation;
import com.iafenvoy.mxt.data.alchemy.AlchemyFurnaceDefinition;
import com.iafenvoy.mxt.data.alchemy.MedicinalProperty;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.alchemy.*;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService.AlchemyPreview;
import com.iafenvoy.mxt.runtime.resulting.failures.AlchemyFailure;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The furnace's two tier gates and the core's rank, read from a stub controller and a fake player.
 * A recipe field decides them before temperature, environment or output capacity are looked at.
 */
public final class AlchemyGateProbes {
    private static final double STATION_TEMPERATURE = 50.0D;
    private static final double TEMPERATURE_LIMIT = 200.0D;

    private AlchemyGateProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("alchemy/gate/core_entry", AlchemyGateProbes::coreEntry),
                Probe.of("alchemy/gate/core_cross_ladder", AlchemyGateProbes::coreCrossLadder),
                Probe.of("alchemy/gate/core_upper_tier", AlchemyGateProbes::coreUpperTier),
                Probe.of("alchemy/gate/material_tier", AlchemyGateProbes::materialTier),
                Probe.of("alchemy/gate/input_tier", AlchemyGateProbes::inputTier),
                Probe.of("alchemy/gate/input_tier_marked", AlchemyGateProbes::inputTierMarked),
                Probe.of("alchemy/gate/temperature_limit", AlchemyGateProbes::temperatureLimit),
                Probe.of("alchemy/gate/walls", AlchemyGateProbes::walls));
    }

    // A plain core reads the ladder's entry out of the default_quality table, so the entry in "hot" is satisfied
    // by it and the refusal that follows is the temperature one - the same answer the placed probe expects.
    private static void coreEntry(ProbeContext context) {
        ServerLevel level = context.level();
        FakePlayer player = viewer(level);
        Generic core = new Generic(standard(), at(context), spec(level), core(level));
        AlchemyPreview entry = AlchemyWorkstationService.preview(player, core);
        context.expect("gate-core-entry-recipe", resolved(entry), ProbeDefinitions.id("alchemy/hot"));
        context.expect("gate-core-entry-blocker", entry.blocker().orElse(null), AlchemyFailure.TEMPERATURE);
        context.expect("gate-core-entry-duration", duration(entry), 4L);
    }

    // A tier from another ladder is neither above nor below the one the formula names, so the gate refuses and
    // the batch is left untouched.
    private static void coreCrossLadder(ProbeContext context) {
        ServerLevel level = context.level();
        FakePlayer player = viewer(level);
        Generic crossed = new Generic(standard(), at(context), spec(level), core(level, ProbeDefinitions.id("poor")));
        AlchemyPreview crossPreview = AlchemyWorkstationService.preview(player, crossed);
        context.expect("gate-core-cross-ladder", crossPreview.blocker().orElse(null), AlchemyFailure.FURNACE_TIER);
        context.expect("gate-core-cross-started", AlchemyWorkstationService.start(player, crossed).isSuccess(), false);
        context.expect("gate-core-cross-kept", crossed.container().getItem(0).getCount(), 2);
    }

    // One tier up the same ladder satisfies the same floor, and that tier's own alchemy_modifier is what makes the
    // batch shorter: hot's own numbers read the rank (4 + 1), the tier halves the result. The mixture is untouched
    // by the core, which is the whole point - a better furnace never changes what the herbs add up to.
    private static void coreUpperTier(ProbeContext context) {
        ServerLevel level = context.level();
        FakePlayer player = viewer(level);
        Generic upper = new Generic(standard(), at(context), spec(level), core(level, ProbeDefinitions.id("alchemy/kiln_b")));
        AlchemyPreview higher = AlchemyWorkstationService.preview(player, upper);
        context.expect("gate-core-upper-recipe", resolved(higher), ProbeDefinitions.id("alchemy/hot"));
        context.expect("gate-core-upper-blocker", higher.blocker().orElse(null), AlchemyFailure.TEMPERATURE);
        context.expect("core-tier-speed", duration(higher), 3L);
        context.expect("core-tier-mixture", property(level, higher, "alchemy/heat"), 2.0D);
    }

    // The material side of the same rule, on the entry core: one input tier whose own alchemy_modifier is 2 halves
    // this batch as well, which is the factor the core's is multiplied by.
    private static void materialTier(ProbeContext context) {
        ServerLevel level = context.level();
        FakePlayer player = viewer(level);
        Generic quick = new Generic(standard(), at(context), spec(level), core(level));
        grade(level, quick.container().getItem(0), ProbeDefinitions.id("alchemy/quick"));
        AlchemyPreview quickened = AlchemyWorkstationService.preview(player, quick);
        context.expect("material-tier-speed", duration(quickened), 2L);
    }

    // The input gate, on a formula that only this probe can reach: doubling every herb picks the gated recipe
    // because it is the only candidate that strictly covers the two ungated ones. Every input has to answer, so
    // the batch is refused while the herbs carry no tier of their own.
    private static void inputTier(ProbeContext context) {
        ServerLevel level = context.level();
        FakePlayer player = viewer(level);
        Generic refused = new Generic(doubled(), at(context), spec(level), core(level));
        AlchemyPreview gated = AlchemyWorkstationService.preview(player, refused);
        context.expect("gate-input-recipe", resolved(gated), ProbeDefinitions.id("alchemy/zz_input_gate"));
        context.expect("gate-input-refused", gated.blocker().orElse(null), AlchemyFailure.INPUT_TIER);
        context.expect("gate-input-started", AlchemyWorkstationService.start(player, refused).isSuccess(), false);
        context.expect("gate-input-kept", refused.container().getItem(0).getCount(), 4);
    }

    // Marked on the ladder the formula names, the same batch passes the gate and starts. Every non-empty slot
    // answers the requirement, so every one of them has to be marked.
    private static void inputTierMarked(ProbeContext context) {
        ServerLevel level = context.level();
        FakePlayer player = viewer(level);
        Generic marked = new Generic(doubled(), at(context), spec(level), core(level));
        for (int index = 0; index < AlchemySlots.OUTPUT_START; index++)
            if (!marked.container().getItem(index).isEmpty())
                grade(level, marked.container().getItem(index), ProbeDefinitions.id("alchemy/kiln_a"));
        AlchemyPreview markedPreview = AlchemyWorkstationService.preview(player, marked);
        context.expect("gate-input-marked-blocker", markedPreview.blocker().orElse(null), null);
        context.expect("gate-input-marked-started", AlchemyWorkstationService.start(player, marked).isSuccess(), true);
    }

    // The temperature ceiling is a minimum of three, and the specification is only one of them: the capped
    // fixture answers with its own number even where the walls and the heat block allow far more, a specification
    // that declares nothing lets the walls and the heat block decide, and either of those being unavailable reads
    // as a furnace that cannot run at all.
    private static void temperatureLimit(ProbeContext context) {
        ServerLevel level = context.level();
        AlchemyFurnaceDefinition capped = spec(level, "alchemy/capped");
        AlchemyFurnaceDefinition wide = spec(level, "alchemy/wide");
        context.expect("temperature-limit-wall-binds",
                AlchemyFurnaceDefinition.temperatureLimit(60.0D, 150.0D, wide), 60.0D);
        context.expect("temperature-limit-heat-binds",
                AlchemyFurnaceDefinition.temperatureLimit(200.0D, 80.0D, wide), 80.0D);
        context.expect("temperature-limit-core-binds",
                AlchemyFurnaceDefinition.temperatureLimit(200.0D, 150.0D, capped), 60.0D);
        context.expect("temperature-limit-no-core",
                AlchemyFurnaceDefinition.temperatureLimit(200.0D, 150.0D, null), 150.0D);
        context.expect("temperature-limit-unavailable",
                AlchemyFurnaceDefinition.temperatureLimit(0.0D, 150.0D, capped), 0.0D);
        context.expect("temperature-limit-loaded", capped.maxTemperature().orElse(-1.0D), 60.0D);
    }

    // The wall limit counts the structure's own wall list, so the list itself is worth one leg: 22 cells are
    // validated, and the core, the two ports, the output and the bottom centre are not walls among them.
    private static void walls(ProbeContext context) {
        int[] walls = AlchemyFurnaceStructure.walls();
        context.expect("wall-count", walls.length == 18
                        && !contains(walls, AlchemyFurnaceStructure.CONTROLLER_INDEX)
                        && !contains(walls, AlchemyFurnaceStructure.MAIN_INDEX)
                        && !contains(walls, AlchemyFurnaceStructure.AUXILIARY_INDEX)
                        && !contains(walls, AlchemyFurnaceStructure.OUTPUT_INDEX)
                        && !contains(walls, AlchemyFurnaceStructure.HEAT_INDEX),
                true);
    }

    private static SimpleContainer standard() {
        SimpleContainer container = new SimpleContainer(AlchemySlots.TOTAL);
        container.setItem(0, new ItemStack(Items.ALLIUM, 2));
        container.setItem(1, new ItemStack(Items.POPPY));
        container.setItem(2, new ItemStack(Items.CORNFLOWER, 2));
        container.setItem(4, new ItemStack(Items.OXEYE_DAISY));
        return container;
    }

    // The same mixture doubled, which is what makes the gated recipe dominate instead of tying with the ungated two.
    private static SimpleContainer doubled() {
        SimpleContainer container = new SimpleContainer(AlchemySlots.TOTAL);
        container.setItem(0, new ItemStack(Items.ALLIUM, 4));
        container.setItem(1, new ItemStack(Items.POPPY));
        container.setItem(2, new ItemStack(Items.CORNFLOWER, 4));
        container.setItem(4, new ItemStack(Items.OXEYE_DAISY));
        return container;
    }

    private static ItemStack core(ServerLevel level) {
        ItemStack stack = MxtBlocks.ALCHEMY_FURNACE.toStack();
        stack.set(MxtDataComponents.ALCHEMY_FURNACE.get(), spec(level));
        return stack;
    }

    private static ItemStack core(ServerLevel level, Identifier quality) {
        ItemStack stack = core(level);
        grade(level, stack, quality);
        return stack;
    }

    private static void grade(ServerLevel level, ItemStack stack, Identifier quality) {
        Holder<ItemQuality> holder = MxtDatapackRegistries
                .holder(level.registryAccess(), MxtResourceKeys.ITEM_QUALITY, quality).orElseThrow();
        stack.set(MxtDataComponents.QUALITY.get(), holder);
    }

    private static Holder<AlchemyFurnaceDefinition> spec(ServerLevel level) {
        return MxtDatapackRegistries.holder(level.registryAccess(), MxtResourceKeys.ALCHEMY_FURNACE, ProbeDefinitions.id("alchemy/wide")).orElseThrow();
    }

    private static AlchemyFurnaceDefinition spec(ServerLevel level, String path) {
        return MxtDatapackRegistries.holder(level.registryAccess(), MxtResourceKeys.ALCHEMY_FURNACE, ProbeDefinitions.id(path))
                .map(Holder::value).orElseThrow();
    }

    /**
     * The command source stood at the player, so the stub's own position is where the probe's player stands.
     */
    private static BlockPos at(ProbeContext context) {
        return BlockPos.containing(context.player().position());
    }

    // The stub controller answers everything a placed one would, so the player it works for is a fake one.
    private static FakePlayer viewer(ServerLevel level) {
        return new FakePlayer(level, new GameProfile(UUID.randomUUID(), "MxtGateProbe"));
    }

    private static boolean contains(int[] values, int wanted) {
        for (int value : values) if (value == wanted) return true;
        return false;
    }

    private static Identifier resolved(AlchemyPreview preview) {
        return preview.resolvedRecipe().map(ResourceKey::identifier).orElse(null);
    }

    private static long duration(AlchemyPreview preview) {
        return preview.parameters().map(AlchemyWorkstationService.Parameters::durationTicks).orElse(-1L);
    }

    private static double property(ServerLevel level, AlchemyPreview preview, String property) {
        Holder<MedicinalProperty> holder = MxtDatapackRegistries
                .holder(level.registryAccess(), MxtResourceKeys.MEDICINAL_PROPERTY, ProbeDefinitions.id(property)).orElseThrow();
        return preview.mixture().main().getOrDefault(holder, -1.0D);
    }

    /**
     * A controller that owns its own slots: the gates under test read the recipe, the core item and the mixture, so
     * the structure and the heat cell are answers this stub simply states.
     */
    private static final class Generic implements AlchemyWorkstation {
        private final Container container;
        private final BlockPos pos;
        private final Holder<AlchemyFurnaceDefinition> spec;
        private final AlchemyWorkstationState state = new AlchemyWorkstationState();
        private final ItemStack core;
        private double temperature;

        private Generic(Container container, BlockPos pos, Holder<AlchemyFurnaceDefinition> spec, ItemStack core) {
            this.container = container;
            this.pos = pos;
            this.spec = spec;
            this.core = core;
            this.state.setTargetTemperature(STATION_TEMPERATURE);
        }

        @Override
        public Container container() {
            return this.container;
        }

        @Override
        public AlchemyWorkstationState state() {
            return this.state;
        }

        @Override
        public BlockPos getBlockPos() {
            return this.pos;
        }

        @Override
        public ItemStack furnaceItem() {
            return this.core;
        }

        @Override
        public Optional<Holder<AlchemyFurnaceDefinition>> furnaceDefinition() {
            return Optional.of(this.spec);
        }

        @Override
        public double temperature() {
            return this.temperature;
        }

        @Override
        public double targetTemperature() {
            return this.state.targetTemperature();
        }

        @Override
        public boolean setTargetTemperature(double temperature) {
            this.state.setTargetTemperature(temperature);
            return true;
        }

        @Override
        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        @Override
        public BlockPos heatSourcePos() {
            return this.pos;
        }

        @Override
        public double wallTemperatureLimit() {
            return TEMPERATURE_LIMIT;
        }

        @Override
        public double heatTemperatureLimit() {
            return TEMPERATURE_LIMIT;
        }

        @Override
        public double maximumTemperature() {
            return TEMPERATURE_LIMIT;
        }

        @Override
        public AlchemyFurnaceStructure.Status structureStatus() {
            return new AlchemyFurnaceStructure.Status(true, true, List.of(), List.of(), List.of(), this.pos, Direction.NORTH);
        }

        @Override
        public AlchemyPhase phase() {
            return this.state.phase();
        }

        @Override
        public void setChanged() {
        }
    }
}
