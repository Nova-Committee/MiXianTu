package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.api.AlchemyWorkstation;
import com.iafenvoy.mxt.data.alchemy.AlchemyFurnaceDefinition;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyFailure;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyFurnaceStructure;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyPhase;
import com.iafenvoy.mxt.runtime.alchemy.AlchemySlots;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService.AlchemyPreview;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationState;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Both of the furnace's tier gates, plus the core's rank, read from a stub controller and a fake player: a recipe
 * field decides these answers before temperature, environment or output capacity are looked at, so nothing here
 * needs a placed multiblock or a real client. The mixtures and formulas are the fixtures the placed probes use.
 */
public final class AlchemyGateProbes {
    private static final String NS = "mxt_test";
    private static final double STATION_TEMPERATURE = 50.0D;
    private static final double TEMPERATURE_LIMIT = 200.0D;
    private static int checks;
    private static int mismatches;

    private AlchemyGateProbes() {
    }

    public static boolean run(CommandSourceStack source) {
        checks = 0;
        mismatches = 0;
        ServerLevel level = source.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "MxtGateProbe"));
        // A plain core reads the ladder's entry out of the default_quality table, so the entry in "hot" is satisfied
        // by it and the refusal that follows is the temperature one - the same answer the placed probe expects.
        Generic core = new Generic(standard(), at(source), spec(level), core(level));
        AlchemyPreview entry = AlchemyWorkstationService.preview(player, core);
        check(source, "gate-core-entry-recipe", resolved(entry), id("alchemy/hot"));
        check(source, "gate-core-entry-blocker", entry.blocker().orElse(null), AlchemyFailure.TEMPERATURE);
        check(source, "gate-core-entry-duration", duration(entry), 4L);

        // A tier from another ladder is neither above nor below the one the formula names, so the gate refuses and
        // the batch is left untouched.
        Generic crossed = new Generic(standard(), at(source), spec(level), core(level, id("poor")));
        AlchemyPreview crossPreview = AlchemyWorkstationService.preview(player, crossed);
        check(source, "gate-core-cross-ladder", crossPreview.blocker().orElse(null), AlchemyFailure.FURNACE_TIER);
        check(source, "gate-core-cross-started", AlchemyWorkstationService.start(player, crossed).started(), false);
        check(source, "gate-core-cross-kept", crossed.container().getItem(0).getCount(), 2);

        // One tier up the same ladder satisfies the same floor, and that tier's own alchemy_modifier is what makes the
        // batch shorter: hot's own numbers read the rank (4 + 1), the tier halves the result. The mixture is untouched
        // by the core, which is the whole point - a better furnace never changes what the herbs add up to.
        Generic upper = new Generic(standard(), at(source), spec(level), core(level, id("alchemy/kiln_b")));
        AlchemyPreview higher = AlchemyWorkstationService.preview(player, upper);
        check(source, "gate-core-upper-recipe", resolved(higher), id("alchemy/hot"));
        check(source, "gate-core-upper-blocker", higher.blocker().orElse(null), AlchemyFailure.TEMPERATURE);
        check(source, "core-tier-speed", duration(higher), 3L);
        check(source, "core-tier-mixture", property(higher, "alchemy/heat"), 2.0D);

        // The material side of the same rule, on the entry core: one input tier whose own alchemy_modifier is 2 halves
        // this batch as well, which is the factor the core's is multiplied by.
        Generic quick = new Generic(standard(), at(source), spec(level), core(level));
        grade(level, quick.container().getItem(0), id("alchemy/quick"));
        AlchemyPreview quickened = AlchemyWorkstationService.preview(player, quick);
        check(source, "material-tier-speed", duration(quickened), 2L);

        // The input gate, on a formula that only this probe can reach: doubling every herb picks the gated recipe
        // because it is the only candidate that strictly covers the two ungated ones. Every input has to answer, so
        // the batch is refused while the herbs carry no tier of their own.
        Generic refused = new Generic(doubled(), at(source), spec(level), core(level));
        AlchemyPreview gated = AlchemyWorkstationService.preview(player, refused);
        check(source, "gate-input-recipe", resolved(gated), id("alchemy/zz_input_gate"));
        check(source, "gate-input-refused", gated.blocker().orElse(null), AlchemyFailure.INPUT_TIER);
        check(source, "gate-input-started", AlchemyWorkstationService.start(player, refused).started(), false);
        check(source, "gate-input-kept", refused.container().getItem(0).getCount(), 4);

        // Marked on the ladder the formula names, the same batch passes the gate and starts. Every non-empty slot
        // answers the requirement, so every one of them has to be marked.
        Generic marked = new Generic(doubled(), at(source), spec(level), core(level));
        for (int index = 0; index < AlchemySlots.OUTPUT_START; index++)
            if (!marked.container().getItem(index).isEmpty()) grade(level, marked.container().getItem(index), id("alchemy/kiln_a"));
        AlchemyPreview markedPreview = AlchemyWorkstationService.preview(player, marked);
        check(source, "gate-input-marked-blocker", markedPreview.blocker().orElse(null), null);
        check(source, "gate-input-marked-started", AlchemyWorkstationService.start(player, marked).started(), true);

        // The temperature ceiling is a minimum of three, and the specification is only one of them: the capped
        // fixture answers with its own number even where the walls and the heat block allow far more, a specification
        // that declares nothing lets the walls and the heat block decide, and either of those being unavailable reads
        // as a furnace that cannot run at all.
        AlchemyFurnaceDefinition capped = spec(level, "alchemy/capped");
        AlchemyFurnaceDefinition wide = spec(level, "alchemy/wide");
        check(source, "temperature-limit-wall-binds",
                AlchemyFurnaceDefinition.temperatureLimit(60.0D, 150.0D, wide), 60.0D);
        check(source, "temperature-limit-heat-binds",
                AlchemyFurnaceDefinition.temperatureLimit(200.0D, 80.0D, wide), 80.0D);
        check(source, "temperature-limit-core-binds",
                AlchemyFurnaceDefinition.temperatureLimit(200.0D, 150.0D, capped), 60.0D);
        check(source, "temperature-limit-no-core",
                AlchemyFurnaceDefinition.temperatureLimit(200.0D, 150.0D, null), 150.0D);
        check(source, "temperature-limit-unavailable",
                AlchemyFurnaceDefinition.temperatureLimit(0.0D, 150.0D, capped), 0.0D);
        check(source, "temperature-limit-loaded", capped.maxTemperature().orElse(-1.0D), 60.0D);

        // The wall limit counts the structure's own wall list, so the list itself is worth one leg: 22 cells are
        // validated, and the core, the two ports, the output and the bottom centre are not walls among them.
        int[] walls = AlchemyFurnaceStructure.walls();
        check(source, "wall-count", walls.length == 18
                        && !contains(walls, AlchemyFurnaceStructure.CONTROLLER_INDEX)
                        && !contains(walls, AlchemyFurnaceStructure.MAIN_INDEX)
                        && !contains(walls, AlchemyFurnaceStructure.AUXILIARY_INDEX)
                        && !contains(walls, AlchemyFurnaceStructure.OUTPUT_INDEX)
                        && !contains(walls, AlchemyFurnaceStructure.HEAT_INDEX),
                true);

        if (mismatches == 0) source.sendSuccess(() -> Component.literal("alchemy gate probe: OK"), false);
        else source.sendFailure(Component.literal("alchemy gate probe: MISMATCH " + mismatches + "/" + checks));
        return mismatches == 0;
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
        return MxtDatapackRegistries.holder(level.registryAccess(), MxtResourceKeys.ALCHEMY_FURNACE, id("alchemy/wide")).orElseThrow();
    }

    private static AlchemyFurnaceDefinition spec(ServerLevel level, String path) {
        return MxtDatapackRegistries.holder(level.registryAccess(), MxtResourceKeys.ALCHEMY_FURNACE, id(path))
                .map(Holder::value).orElseThrow();
    }

    private static BlockPos at(CommandSourceStack source) {
        return BlockPos.containing(source.getPosition());
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

    private static double property(AlchemyPreview preview, String property) {
        return preview.mixture().main().getOrDefault(id(property), -1.0D);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NS, path);
    }

    private static void check(CommandSourceStack source, String name, Object actual, Object expected) {
        checks++;
        boolean ok = Objects.equals(expected, actual);
        if (!ok) mismatches++;
        Component line = Component.literal("alchemy gate probe: " + (ok ? "OK" : "MISMATCH") + " " + name
                + " actual=" + actual + " expected=" + expected);
        if (ok) source.sendSuccess(() -> line, false);
        else source.sendFailure(line);
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
