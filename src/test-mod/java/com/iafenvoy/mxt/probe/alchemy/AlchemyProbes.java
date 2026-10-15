package com.iafenvoy.mxt.probe.alchemy;

import com.google.gson.JsonElement;
import com.iafenvoy.mxt.data.alchemy.AlchemyFurnaceDefinition;
import com.iafenvoy.mxt.data.alchemy.MedicinalProperty;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.item.PillComponent;
import com.iafenvoy.mxt.event.AlchemyCraftEvent;
import com.iafenvoy.mxt.item.block.AlchemyFurnaceBlock;
import com.iafenvoy.mxt.item.block.AlchemyFurnaceCasingBlock;
import com.iafenvoy.mxt.item.block.AlchemyFurnaceInventoryBlock;
import com.iafenvoy.mxt.item.block.entity.AlchemyFurnaceBlockEntity;
import com.iafenvoy.mxt.item.block.entity.AlchemyFurnaceCasingBlockEntity;
import com.iafenvoy.mxt.item.block.entity.AlchemyFurnaceInventoryBlockEntity;
import com.iafenvoy.mxt.network.payload.AlchemyActionC2SPayload;
import com.iafenvoy.mxt.network.payload.AlchemyActionC2SPayload.Action;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.*;
import com.iafenvoy.mxt.runtime.alchemy.*;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyResolver.Candidate;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService.AlchemyPreview;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService.StartResult;
import com.iafenvoy.mxt.runtime.spirit.SpiritBurstEntity;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceMenu;
import com.iafenvoy.mxt.testmod.AlchemyTestHeatBlocks;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Real furnace probes: one game test per scenario the old command ran along a single cursor.
 *
 * <p>Placed blocks, listeners, drops and the player's own state are restored when a body returns.
 */
public final class AlchemyProbes {
    private static final Identifier WIDE = ProbeDefinitions.id("alchemy/wide");
    private static final Identifier CAPPED = ProbeDefinitions.id("alchemy/capped");
    private static final Identifier SMALL = ProbeDefinitions.id("alchemy/small");
    private static final Identifier DUAL = ProbeDefinitions.id("alchemy/dual");
    private static final Identifier SEALED = ProbeDefinitions.id("alchemy/sealed");
    private static final Identifier PRACTICE = ProbeDefinitions.id("alchemy/practice");
    private static final Identifier HOT = ProbeDefinitions.id("alchemy/hot");
    private static final Identifier HOT_LOW = ProbeDefinitions.id("alchemy/hot_low");
    private static final Identifier LONG_BAD = ProbeDefinitions.id("alchemy/long_bad");
    private static final Identifier RANK_LOW = ProbeDefinitions.id("alchemy/rank_low");
    private static final Identifier RANK_HIGH = ProbeDefinitions.id("alchemy/rank_high");
    private static final Identifier KILN_WALL = ProbeDefinitions.id("alchemy/kiln");
    private static final Identifier BRITTLE_WALL = ProbeDefinitions.id("alchemy/brittle");
    private static final Identifier QI = ProbeDefinitions.id("qi");
    private static final Identifier MAIN = ProbeDefinitions.id("herb/main");
    /**
     * Arena relative offset of the apparatus from the probe's own player, the old command's first cursor.
     */
    private static final int SPOT_X = 6;
    private static final int SPOT_Y = 1;
    private static final int SPOT_Z = 6;

    private AlchemyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("alchemy/mixture", AlchemyProbes::mixture),
                Probe.of("alchemy/resolution", AlchemyProbes::resolution),
                Probe.of("alchemy/refusal", AlchemyProbes::refusal),
                Probe.of("alchemy/heat_and_failure", AlchemyProbes::heatAndFailure),
                Probe.of("alchemy/persistence", AlchemyProbes::persistence),
                Probe.of("alchemy/drops_and_structure", AlchemyProbes::dropsAndStructure),
                Probe.of("alchemy/actions_and_menu", AlchemyProbes::actionsAndMenu),
                Probe.of("alchemy/orientations", AlchemyProbes::orientations),
                Probe.of("alchemy/unloaded", AlchemyProbes::unloaded),
                Probe.of("alchemy/conflicts", AlchemyProbes::conflicts),
                Probe.of("alchemy/regressions", AlchemyProbes::regressions),
                Probe.of("alchemy/one_roll", AlchemyProbes::oneRoll),
                Probe.of("alchemy/output_fit", AlchemyProbes::outputFit),
                Probe.of("alchemy/criterion", AlchemyProbes::criterion),
                Probe.of("alchemy/limits_and_parts", AlchemyProbes::limitsAndParts),
                Probe.of("alchemy/callback_failures", AlchemyProbes::callbackFailures),
                Probe.of("alchemy/menu_ownership", AlchemyProbes::menuOwnership),
                Probe.of("alchemy/heat_sources", AlchemyProbes::heatSources),
                Probe.of("alchemy/core_quality", AlchemyProbes::coreQuality));
    }

    private static void mixture(ProbeContext context) {
        scenario(context, fixture -> {
            ServerLevel level = fixture.level();
            ServerPlayer player = fixture.player();
            List<BlockPos> touched = fixture.touched();
            BlockPos at = fixture.at();
            AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            Holder<MedicinalProperty> mainProperty = property(level, MAIN);
            furnace.refreshStructure();
            context.expect("structure", furnace.structureStatus().complete(), true);
            furnace.setTargetTemperature(50);
            furnace.container().setItem(0, new ItemStack(Items.ALLIUM, 2));
            furnace.container().setItem(2, new ItemStack(Items.CORNFLOWER, 2));
            furnace.container().setItem(4, new ItemStack(Items.OXEYE_DAISY));
            AlchemyPreview together = AlchemyWorkstationService.preview(player, furnace);
            furnace.container().setItem(0, new ItemStack(Items.ALLIUM));
            furnace.container().setItem(1, new ItemStack(Items.ALLIUM));
            AlchemyPreview split = AlchemyWorkstationService.preview(player, furnace);
            context.expect("split-main", getMain(split, mainProperty, -1.0D), getMain(together, mainProperty, -2.0D));
            context.expect("split-same-recipe", resolvedId(split), resolvedId(together));
            furnace.container().setItem(0, ItemStack.EMPTY);
            furnace.container().setItem(1, ItemStack.EMPTY);
            furnace.container().setItem(2, new ItemStack(Items.ALLIUM, 2));
            AlchemyPreview asAux = AlchemyWorkstationService.preview(player, furnace);
            context.expect("aux-role", getMain(asAux, mainProperty, 0.0D), 0.0D);
            furnace.container().setItem(0, new ItemStack(Items.AZURE_BLUET));
            furnace.container().setItem(1, ItemStack.EMPTY);
            furnace.container().setItem(2, new ItemStack(Items.CORNFLOWER, 2));
            AlchemyPreview single = AlchemyWorkstationService.preview(player, furnace);
            context.expect("substitute-main", getMain(single, mainProperty, -1.0D), 6.0D);
            context.expect("substitute-balance", single.mixture().balance(), 0.0D);
            context.expect("substitute-resolved", resolvedId(single), PRACTICE);
            ItemStack aged = new ItemStack(Items.ALLIUM);
            aged.set(MxtDataComponents.HERB_AGE.get(), 100);
            furnace.container().setItem(0, aged);
            AlchemyPreview century = AlchemyWorkstationService.preview(player, furnace);
            context.expect("century-main", getMain(century, mainProperty, -1.0D), 6.0D);
            context.expect("century-resolved", resolvedId(century), PRACTICE);
            context.expect("century-only", matchedCount(century), 1);
            context.expect("century-edible", ediblePractice(level.registryAccess(), century.successOutputs()), true);
            furnace.container().setItem(1, new ItemStack(Items.DANDELION));
            AlchemyPreview conflict = AlchemyWorkstationService.preview(player, furnace);
            context.expect("conflict", conflict.blocker().orElse(null), AlchemyFailure.CONFLICT);
            int before = furnace.container().getItem(1).getCount();
            StartResult refused = AlchemyWorkstationService.start(player, furnace);
            context.expect("conflict-no-spend", refused.started(), false);
            context.expect("conflict-kept", furnace.container().getItem(1).getCount(), before);
            furnace.container().setItem(1, ItemStack.EMPTY);
            context.expect("practice-start", AlchemyWorkstationService.start(player, furnace).started(), true);
            tickUntil(level, furnace, 220, phase -> phase == AlchemyPhase.IDLE);
            context.expect("practice-pills", ediblePractice(level.registryAccess(), List.of(stored(furnace, MxtItems.PILL.get()))), true);
        });
    }

    private static void resolution(ProbeContext context) {
        scenario(context, fixture -> {
            ServerLevel level = fixture.level();
            ServerPlayer player = fixture.player();
            List<BlockPos> touched = fixture.touched();
            BlockPos at = fixture.at();
            AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            furnace.refreshStructure();
            furnace.setTargetTemperature(50);
            furnace.container().setItem(0, new ItemStack(Items.CACTUS));
            furnace.container().setItem(4, new ItemStack(Items.OXEYE_DAISY));
            AlchemyPreview low = AlchemyWorkstationService.preview(player, furnace);
            context.expect("rank-low-resolved", resolvedId(low), RANK_LOW);
            context.expect("rank-low-only", matchedCount(low), 1);
            furnace.container().setItem(0, new ItemStack(Items.CACTUS, 2));
            AlchemyPreview high = AlchemyWorkstationService.preview(player, furnace);
            context.expect("rank-high-resolved", resolvedId(high), RANK_HIGH);
            context.expect("rank-high-also-low", matched(high, RANK_LOW), true);
            Candidate reversed = AlchemyResolver.dominating(high.candidates().reversed());
            context.expect("rank-candidate-order", reversed == null ? null : reversed.id().identifier(), RANK_HIGH);
            furnace.container().setItem(0, new ItemStack(Items.CACTUS));
            furnace.container().setItem(1, new ItemStack(Items.CACTUS));
            AlchemyPreview splitRank = AlchemyWorkstationService.preview(player, furnace);
            context.expect("rank-split", resolvedId(splitRank), resolvedId(high));
            context.expect("rank-start", AlchemyWorkstationService.start(player, furnace).started(), true);
            tickUntil(level, furnace, 12, phase -> phase == AlchemyPhase.IDLE);
            context.expect("rank-output", stored(furnace, Items.EMERALD).getCount(), 1);
            context.expect("rank-not-low-output", stored(furnace, Items.STICK).isEmpty(), true);
            clearInputs(furnace);
            fill(furnace);
            furnace.container().setItem(1, new ItemStack(Items.WITHER_ROSE));
            AlchemyPreview tie = AlchemyWorkstationService.preview(player, furnace);
            context.expect("tie-ambiguous", tie.blocker().orElse(null), AlchemyFailure.AMBIGUOUS);
            context.expect("tie-unresolved", tie.resolvedRecipe().isEmpty(), true);
            int rose = furnace.container().getItem(1).getCount();
            StartResult tied = AlchemyWorkstationService.start(player, furnace);
            context.expect("tie-no-spend", tied.started(), false);
            context.expect("tie-kept", furnace.container().getItem(1).getCount(), rose);
            clearInputs(furnace);
            fill(furnace);
            furnace.container().setItem(1, new ItemStack(Items.BAMBOO));
            AlchemyPreview crossed = AlchemyWorkstationService.preview(player, furnace);
            context.expect("cross-ambiguous", crossed.blocker().orElse(null), AlchemyFailure.AMBIGUOUS);
            context.expect("cross-unresolved", crossed.resolvedRecipe().isEmpty(), true);
            int bamboo = furnace.container().getItem(1).getCount();
            StartResult cross = AlchemyWorkstationService.start(player, furnace);
            context.expect("cross-no-spend", cross.started(), false);
            context.expect("cross-kept", furnace.container().getItem(1).getCount(), bamboo);
            clearInputs(furnace);
            furnace.container().setItem(0, new ItemStack(Items.ALLIUM));
            furnace.container().setItem(2, new ItemStack(Items.CORNFLOWER));
            furnace.container().setItem(4, new ItemStack(Items.OXEYE_DAISY));
            AlchemyPreview shortfall = AlchemyWorkstationService.preview(player, furnace);
            context.expect("shortfall", shortfall.blocker().orElse(null), AlchemyFailure.INSUFFICIENT);
        });
    }

    private static void refusal(ProbeContext context) {
        scenario(context, fixture -> {
            ServerLevel level = fixture.level();
            ServerPlayer player = fixture.player();
            List<BlockPos> touched = fixture.touched();
            BlockPos at = fixture.at();
            AlchemyFurnaceBlockEntity small = place(level, at, Direction.NORTH, item(level, SMALL), touched);
            small.refreshStructure();
            small.setTargetTemperature(50);
            small.container().setItem(1, new ItemStack(Items.ALLIUM));
            StartResult slots = AlchemyWorkstationService.start(player, small);
            context.expect("disabled-slot", slots.failure(), AlchemyFailure.SLOTS);
            context.expect("disabled-slot-kept", small.container().getItem(1).getCount(), 1);
            small.container().setItem(1, ItemStack.EMPTY);
            small.container().setItem(0, new ItemStack(Items.ALLIUM, 5));
            StartResult capacity = AlchemyWorkstationService.start(player, small);
            context.expect("capacity", capacity.failure(), AlchemyFailure.CAPACITY);
            context.expect("capacity-kept", small.container().getItem(0).getCount(), 5);
            fixture.clearFurnace(at);
            // No quality component on the stack: the tier is the one this specification declares, which is the only
            // way a furnace can state it, since every specification shares one block item.
            AlchemyFurnaceBlockEntity sealed = place(level, at, Direction.NORTH, item(level, SEALED), touched);
            sealed.refreshStructure();
            sealed.setTargetTemperature(50);
            fill(sealed);
            StartResult quality = AlchemyWorkstationService.start(player, sealed);
            context.expect("sealed-quality", quality.failure(), AlchemyFailure.QUALITY_CONDITIONS);
            context.expect("sealed-kept", sealed.container().getItem(0).getCount(), 2);
            context.expect("sealed-heat-kept", heatState(level, sealed).is(AlchemyTestHeatBlocks.FIRE.get()), true);
            fixture.clearFurnace(at);
            AlchemyFurnaceBlockEntity wide = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            wide.refreshStructure();
            wide.setTargetTemperature(50);
            fillMarked(wide, Items.POPPY);
            AlchemyPreview hotPreview = AlchemyWorkstationService.preview(player, wide);
            context.expect("hot-resolved", resolvedId(hotPreview), HOT);
            context.expect("hot-dominates-low", matched(hotPreview, HOT_LOW), true);
            context.expect("hot-not-practice", matched(hotPreview, PRACTICE), false);
            StartResult hot = AlchemyWorkstationService.start(player, wide);
            context.expect("hot-temperature", hot.failure(), AlchemyFailure.TEMPERATURE);
            context.expect("hot-kept", wide.container().getItem(0).getCount(), 2);
            context.expect("hot-marker-kept", wide.container().getItem(1).getCount(), 1);
            BlockPos missing = AlchemyFurnaceStructure.world(at, Direction.NORTH, 16);
            level.setBlockAndUpdate(missing, Blocks.AIR.defaultBlockState());
            wide.refreshStructure();
            StartResult broken = AlchemyWorkstationService.start(player, wide);
            context.expect("missing-shell", broken.failure(), AlchemyFailure.STRUCTURE);
            context.expect("missing-kept", wide.container().getItem(0).getCount(), 2);
        });
    }

    // The core is the one machine part a formula asks about: the tier it requires and the rank its own formulas
    // read both come from the controller's item, never from the walls or the shape.
    private static void coreQuality(ProbeContext context) {
        scenario(context, fixture -> {
            ServerLevel level = fixture.level();
            ServerPlayer player = fixture.player();
            List<BlockPos> touched = fixture.touched();
            BlockPos at = fixture.at();
            AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            furnace.refreshStructure();
            furnace.setTargetTemperature(50);
            fillMarked(furnace, Items.POPPY);
            // The plain core carries no tier of its own, so the default_quality table names the ladder's entry for it.
            AlchemyPreview entry = AlchemyWorkstationService.preview(player, furnace);
            context.expect("core-quality-resolved", resolvedId(entry), HOT);
            context.expect("core-quality-entry", AlchemyWorkstationService.start(player, furnace).failure(), AlchemyFailure.TEMPERATURE);
            context.expect("core-quality-entry-kept", furnace.container().getItem(0).getCount(), 2);
            context.expect("furnace-rank-entry", entry.parameters().map(AlchemyWorkstationService.Parameters::durationTicks).orElse(-1L), 4L);
            // One tier up the same ladder satisfies a floor set at the entry, and that tier's own alchemy_modifier is
            // what shortens the batch: hot's numbers read rank 1 (4 + 1), the tier halves the result.
            furnace.acceptFurnaceItem(tieredCore(level, ProbeDefinitions.id("alchemy/kiln_b")));
            AlchemyPreview higher = AlchemyWorkstationService.preview(player, furnace);
            context.expect("core-quality-higher", AlchemyWorkstationService.start(player, furnace).failure(), AlchemyFailure.TEMPERATURE);
            context.expect("core-tier-speed", higher.parameters().map(AlchemyWorkstationService.Parameters::durationTicks).orElse(-1L), 3L);
            // A tier from another ladder is neither above nor below it, so the requirement refuses instead of guessing.
            furnace.acceptFurnaceItem(tieredCore(level, ProbeDefinitions.id("poor")));
            context.expect("core-quality-cross-ladder", AlchemyWorkstationService.preview(player, furnace).blocker().orElse(null), AlchemyFailure.FURNACE_TIER);
            context.expect("core-quality-refused", AlchemyWorkstationService.start(player, furnace).started(), false);
            context.expect("core-quality-kept", furnace.container().getItem(0).getCount(), 2);
            context.expect("core-quality-heat-kept", heatState(level, furnace).is(AlchemyTestHeatBlocks.FIRE.get()), true);
            fixture.clearFurnace(at);
        });
    }

    private static void heatAndFailure(ProbeContext context) {
        scenario(context, fixture -> {
            ServerLevel level = fixture.level();
            ServerPlayer player = fixture.player();
            List<BlockPos> touched = fixture.touched();
            BlockPos at = fixture.at();
            AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            furnace.refreshStructure();
            furnace.setTargetTemperature(50);
            fillMarked(furnace, Items.ORANGE_TULIP);
            StartResult started = AlchemyWorkstationService.start(player, furnace);
            context.expect("full-start", started.started(), true);
            for (int slot = 5; slot < 9; slot++)
                furnace.container().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            tickUntil(level, furnace, 12, phase -> phase == AlchemyPhase.READY || phase == AlchemyPhase.IDLE);
            context.expect("full-ready", furnace.phase(), AlchemyPhase.READY);
            int pending = furnace.state().session().map(session -> session.pendingOutputs().stream().mapToInt(ItemStack::getCount).sum()).orElse(0);
            context.expect("full-waiting", pending, 2);
            context.expect("full-not-inserted", furnace.container().getItem(5).is(Items.COBBLESTONE), true);
            double hot = furnace.temperature();
            tick(level, furnace);
            tick(level, furnace);
            context.expect("ready-heat-kept-and-cools", heatState(level, furnace).is(AlchemyTestHeatBlocks.FIRE.get())
                    && furnace.temperature() < hot, true);
            context.expect("full-no-dup", furnace.state().session().map(session -> session.pendingOutputs().stream().mapToInt(ItemStack::getCount).sum()).orElse(-1), pending);
            fixture.clearFurnace(at);
            AlchemyFurnaceBlockEntity dual = place(level, at, Direction.NORTH, item(level, DUAL), touched);
            dual.refreshStructure();
            dual.setTargetTemperature(50);
            fill(dual);
            StartResult dualStart = AlchemyWorkstationService.start(player, dual);
            context.expect("alternate-quality-start", dualStart.started(), true);
            tick(level, dual);
            context.expect("running-heat-not-consumed", heatState(level, dual).is(AlchemyTestHeatBlocks.FIRE.get()), true);
            fixture.clearFurnace(at);
            AlchemyFurnaceBlockEntity burstFurnace = place(level, at, Direction.NORTH, item(level, SMALL), touched);
            burstFurnace.refreshStructure();
            double cold = burstFurnace.temperature();
            shoot(level, player, at.above(), aura(level, QI), 3);
            context.expect("burst-heat-kept", heatState(level, burstFurnace).is(AlchemyTestHeatBlocks.FIRE.get()), true);
            context.expect("burst-no-heat", burstFurnace.temperature(), cold);
            fixture.clearFurnace(at);
            AlchemyFurnaceBlockEntity bad = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            bad.refreshStructure();
            bad.setTargetTemperature(50);
            fill(bad);
            context.expect("bad-start", AlchemyWorkstationService.start(player, bad).started(), true);
            tickUntil(level, bad, 6, phase -> phase == AlchemyPhase.RUNNING);
            context.expect("bad-running", bad.phase(), AlchemyPhase.RUNNING);
            bad.setTargetTemperature(0);
            tick(level, bad);
            tick(level, bad);
            context.expect("bad-tolerated", bad.state().session().map(AlchemySession::badTicks).orElse(-1), 2);
            context.expect("bad-still-running", bad.phase(), AlchemyPhase.RUNNING);
            PostWatch watch = new PostWatch(bad.getBlockPos());
            NeoForge.EVENT_BUS.register(watch);
            tick(level, bad);
            context.expect("bad-third-fails", watch.posts == 1 && watch.failure, true);
            bad.setTemperature(50);
            bad.setTargetTemperature(50);
            tick(level, bad);
            NeoForge.EVENT_BUS.unregister(watch);
            context.expect("bad-no-recovery", watch.posts == 1 && charcoalCount(bad) == 1 && !hasOutput(bad, Items.PAPER), true);
            context.expect("bad-charcoal-once", charcoalCount(bad), 1);
        });
    }

    private static void persistence(ProbeContext context) {
        scenario(context, fixture -> {
            ServerLevel level = fixture.level();
            ServerPlayer player = fixture.player();
            List<BlockPos> touched = fixture.touched();
            BlockPos at = fixture.at();
            AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, overrideItem(level), touched);
            furnace.refreshStructure();
            furnace.setTargetTemperature(50);
            fillMarked(furnace, Items.RED_TULIP);
            context.expect("save-start", AlchemyWorkstationService.start(player, furnace).started(), true);
            AlchemyFurnaceBlockEntity warming = reload(level, furnace);
            context.expect("warming-phase", warming.phase(), AlchemyPhase.WARMING);
            context.expect("warming-recipe", warming.state().session().map(session -> session.recipe().successOutputs().isEmpty()).orElse(true), false);
            tickUntil(level, furnace, 6, phase -> phase == AlchemyPhase.RUNNING);
            AlchemyFurnaceBlockEntity running = reload(level, furnace);
            context.expect("running-phase", running.phase(), AlchemyPhase.RUNNING);
            context.expect("running-heat", heatState(level, running).is(AlchemyTestHeatBlocks.FIRE.get()), true);
            context.expect("running-frozen", running.state().session().map(session -> session.recipeId().equals(LONG_BAD)).orElse(false), true);
            for (int slot = 5; slot < 9; slot++)
                furnace.container().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            tickUntil(level, furnace, 90, phase -> phase == AlchemyPhase.READY);
            AlchemyFurnaceBlockEntity ready = reload(level, furnace);
            context.expect("ready-phase", ready.phase(), AlchemyPhase.READY);
            context.expect("ready-pending", ready.state().session().map(session -> !session.pendingOutputs().isEmpty()).orElse(false), true);
            context.expect("frozen-output", ready.state().session().map(session -> session.recipe().failureOutputs().getFirst().item().value()).orElse(Items.AIR), Items.CHARCOAL);
        });
    }

    private static void dropsAndStructure(ProbeContext context) {
        scenario(context, fixture -> {
            ServerLevel level = fixture.level();
            ServerPlayer player = fixture.player();
            List<BlockPos> touched = fixture.touched();
            BlockPos at = fixture.at();
            ItemStack marked = overrideItem(level);
            AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, marked, touched);
            furnace.refreshStructure();
            furnace.setTargetTemperature(50);
            fill(furnace);
            AlchemyWorkstationService.start(player, furnace);
            AlchemyFurnaceBlockEntity saved = reload(level, furnace);
            context.expect("override-saved-name", saved.furnaceItem().get(DataComponents.CUSTOM_NAME) != null, true);
            context.expect("override-saved-quality", saved.furnaceItem().get(MxtDataComponents.QUALITY.get()) != null, true);
            BlockPos casing = AlchemyFurnaceStructure.world(at, Direction.NORTH, 0);
            level.setBlockAndUpdate(casing, Blocks.AIR.defaultBlockState());
            context.expect("casing-abort", furnace.state().session().map(AlchemySession::failed).orElse(false), true);
            context.expect("casing-no-refund", furnace.container().getItem(0).isEmpty(), true);
            int charcoal = charcoalCount(furnace);
            level.setBlockAndUpdate(AlchemyFurnaceStructure.world(at, Direction.NORTH, 2), Blocks.AIR.defaultBlockState());
            context.expect("casing-abort-once", charcoalCount(furnace), charcoal);
            fixture.clearDrops(at);
            level.destroyBlock(at, true, player);
            List<ItemEntity> drops = fixture.drops(at);
            context.expect("controller-one-furnace", count(drops, MxtBlocks.ALCHEMY_FURNACE.get().asItem()), 1);
            context.expect("controller-no-ingredient", count(drops, Items.ALLIUM), 0);
            ItemStack dropped = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(MxtBlocks.ALCHEMY_FURNACE.get().asItem())).findFirst().orElse(ItemStack.EMPTY);
            context.expect("override-quality", dropped.get(MxtDataComponents.QUALITY.get()) != null && HolderHelperEquals(dropped, ProbeDefinitions.id("alchemy/kiln_b")), true);
            context.expect("override-name", dropped.get(DataComponents.CUSTOM_NAME) != null, true);
            context.expect("override-spec", dropped.get(MxtDataComponents.ALCHEMY_FURNACE.get()) != null, true);
        });
    }

    private static void actionsAndMenu(ProbeContext context) {
        scenario(context, fixture -> {
            ServerLevel level = fixture.level();
            ServerPlayer player = fixture.player();
            List<BlockPos> touched = fixture.touched();
            BlockPos at = fixture.at();
            Gate gate = fixture.gate();
            gate.cancel = true;
            AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            furnace.refreshStructure();
            furnace.setTargetTemperature(50);
            fill(furnace);
            StartResult cancelled = AlchemyWorkstationService.start(player, furnace);
            context.expect("pre-cancel", cancelled.failure(), AlchemyFailure.CANCELLED);
            context.expect("pre-cancel-items", furnace.container().getItem(0).getCount(), 2);
            context.expect("pre-cancel-heat", heatState(level, furnace).is(AlchemyTestHeatBlocks.FIRE.get()), true);
            gate.cancel = false;
            gate.mutate = true;
            fillMarked(furnace, Items.SUNFLOWER);
            StartResult mutated = AlchemyWorkstationService.start(player, furnace);
            context.expect("pre-copy-starts", mutated.started(), true);
            context.expect("pre-copy-consumed", furnace.container().getItem(0).isEmpty(), true);
            gate.mutate = false;
            gate.posts = 0;
            FakePlayer viewer = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "alchemy_viewer"));
            int viewerShells = count(viewer, Items.NAUTILUS_SHELL);
            tickUntil(level, furnace, 8, phase -> phase == AlchemyPhase.IDLE || level.getBlockState(at).is(Blocks.GOLD_BLOCK));
            context.expect("post-once", gate.posts, 1);
            context.expect("post-operator", gate.postOperator, player.getUUID());
            context.expect("operator-shell", count(player, Items.NAUTILUS_SHELL) >= 1, true);
            context.expect("viewer-no-shell", count(viewer, Items.NAUTILUS_SHELL), viewerShells);
            context.expect("block-action-once", level.getBlockState(at).is(Blocks.GOLD_BLOCK), true);
            tick(level, furnace);
            context.expect("post-not-repeated", gate.posts, 1);
            fixture.clearFurnace(at);
            AlchemyFurnaceBlockEntity menuFurnace = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            menuFurnace.refreshStructure();
            menuFurnace.setTargetTemperature(50);
            fill(menuFurnace);
            AlchemyFurnaceMenu menu = new AlchemyFurnaceMenu(7, player.getInventory(), at, AlchemyFurnaceMenu.View.MONITOR);
            player.containerMenu = menu;
            menu.handleAction(player, new AlchemyActionC2SPayload(99, Action.START, 50));
            context.expect("wrong-container", menuFurnace.phase(), AlchemyPhase.IDLE);
            context.expect("wrong-container-items", menuFurnace.container().getItem(0).getCount(), 2);
            player.containerMenu = player.inventoryMenu;
            menu.handleAction(player, new AlchemyActionC2SPayload(7, Action.START, 50));
            context.expect("stale-menu", menuFurnace.phase(), AlchemyPhase.IDLE);
        });
    }

    private static void orientations(ProbeContext context) {
        scenario(context, fixture -> {
            ServerLevel level = fixture.level();
            List<BlockPos> touched = fixture.touched();
            BlockPos at = fixture.at();
            int index = 0;
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockPos controller = at.offset(index++ * 6, 0, 0);
                AlchemyFurnaceBlockEntity furnace = place(level, controller, facing, item(level, WIDE), touched);
                furnace.refreshStructure();
                BlockPos shell = AlchemyFurnaceStructure.world(controller, facing, 0);
                BlockState state = level.getBlockState(shell);
                context.expect("facing-" + facing.getName(), furnace.structureStatus().complete()
                        && state.getValue(AlchemyFurnaceCasingBlock.PART) == 1
                        && state.getValue(AlchemyFurnaceCasingBlock.FACING) == facing, true);
                // The cell the shape used to leave hollow is a wall now, so taking it out breaks the structure...
                BlockPos centre = AlchemyFurnaceStructure.world(controller, facing, 13);
                level.setBlockAndUpdate(centre, Blocks.AIR.defaultBlockState());
                furnace.refreshStructure();
                context.expect("facing-" + facing.getName() + "-centre", furnace.structureStatus().complete(), false);
                level.setBlockAndUpdate(centre, shell(13, facing));
                if (level.getBlockEntity(centre) instanceof AlchemyFurnaceCasingBlockEntity casing)
                    casing.acceptWallItem(wallItem(level, KILN_WALL));
                // ...while the five bottom cells between the corners are none of its business.
                BlockPos free = AlchemyFurnaceStructure.world(controller, facing, 4);
                level.setBlockAndUpdate(free, Blocks.STONE.defaultBlockState());
                touched.add(free);
                furnace.refreshStructure();
                context.expect("facing-" + facing.getName() + "-free", furnace.structureStatus().complete()
                        && level.getBlockState(free).is(Blocks.STONE), true);
                // The heat cell is one of those five, and it rotates with the shell; the offsets are spelled out here
                // so a wrong quarter turn in the structure cannot hide behind its own helper.
                BlockPos heat = switch (facing) {
                    case EAST -> controller.offset(-1, -1, 0);
                    case SOUTH -> controller.offset(0, -1, -1);
                    case WEST -> controller.offset(1, -1, 0);
                    default -> controller.offset(0, -1, 1);
                };
                context.expect("facing-" + facing.getName() + "-heat-cell", furnace.heatSourcePos(), heat);
                context.expect("facing-" + facing.getName() + "-heat-absent", furnace.heatTemperatureLimit(), 0.0D);
                level.setBlockAndUpdate(heat, AlchemyTestHeatBlocks.FIRE.get().defaultBlockState());
                touched.add(heat);
                context.expect("facing-" + facing.getName() + "-heat", furnace.heatTemperatureLimit(), 150.0D);
            }
        });
    }

    private static void unloaded(ProbeContext context) {
        scenario(context, fixture -> {
            ServerLevel level = fixture.level();
            ServerPlayer player = fixture.player();
            BlockPos controller = new BlockPos((player.getBlockX() >> 4) * 16 + 15 + 8000, Math.max(8, player.getBlockY()), (player.getBlockZ() >> 4) * 16 + 8);
            Set<BlockPos> before = new HashSet<>();
            for (int cell = 0; cell < 27; cell++) {
                if (!AlchemyFurnaceStructure.required(cell)) continue;
                BlockPos pos = AlchemyFurnaceStructure.world(controller, Direction.NORTH, cell);
                if (!level.isLoaded(pos)) before.add(pos);
            }
            if (before.isEmpty()) {
                context.expect("unloaded-setup", false, true);
                return;
            }
            AlchemyFurnaceStructure.Status status = AlchemyFurnaceStructure.inspect(level, controller, Direction.NORTH);
            context.expect("unloaded-listed", new HashSet<>(status.unloaded()), before);
            context.expect("unloaded-incomplete", status.complete(), false);
            context.expect("unloaded-not-forced", before.stream().anyMatch(level::isLoaded), false);
        });
    }

    private static void outputFit(ProbeContext context) {
        scenario(context, fixture -> {
            ServerLevel level = fixture.level();
            ServerPlayer player = fixture.player();
            List<BlockPos> touched = fixture.touched();
            BlockPos at = fixture.at();
            AlchemyFurnaceBlockEntity split = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            split.refreshStructure();
            split.setTargetTemperature(50);
            fillMarked(split, Items.PEONY);
            split.container().setItem(5, ItemStack.EMPTY);
            split.container().setItem(6, new ItemStack(Items.APPLE, 63));
            split.container().setItem(7, new ItemStack(Items.STONE, 64));
            split.container().setItem(8, new ItemStack(Items.STONE, 64));
            context.expect("split-start", AlchemyWorkstationService.start(player, split).started(), true);
            tickUntil(level, split, 16, phase -> phase == AlchemyPhase.IDLE);
            context.expect("split-apple", split.container().getItem(6).getCount(), 64);
            context.expect("split-diamond", split.container().getItem(5).is(Items.DIAMOND) && split.container().getItem(5).getCount() == 1, true);
            fixture.clearFurnace(at);
            AlchemyFurnaceBlockEntity pack = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            pack.refreshStructure();
            pack.setTargetTemperature(50);
            fillMarked(pack, Items.ROSE_BUSH);
            pack.container().setItem(5, new ItemStack(Items.APPLE, 60));
            pack.container().setItem(6, new ItemStack(Items.APPLE, 63));
            pack.container().setItem(7, new ItemStack(Items.STONE, 64));
            pack.container().setItem(8, new ItemStack(Items.STONE, 64));
            context.expect("pack-start", AlchemyWorkstationService.start(player, pack).started(), true);
            tickUntil(level, pack, 16, phase -> phase == AlchemyPhase.IDLE);
            context.expect("pack-slot5", pack.container().getItem(5).getCount(), 64);
            context.expect("pack-slot6", pack.container().getItem(6).getCount(), 64);
            context.expect("pack-still-apple", pack.container().getItem(5).is(Items.APPLE) && pack.container().getItem(6).is(Items.APPLE), true);
            fixture.clearFurnace(at);
            AlchemyFurnaceBlockEntity merged = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            merged.refreshStructure();
            merged.setTargetTemperature(50);
            fillMarked(merged, Items.ROSE_BUSH);
            merged.container().setItem(5, new ItemStack(Items.APPLE, 59));
            merged.container().setItem(6, new ItemStack(Items.STONE, 64));
            merged.container().setItem(7, new ItemStack(Items.STONE, 64));
            merged.container().setItem(8, new ItemStack(Items.STONE, 64));
            context.expect("merge-start", AlchemyWorkstationService.start(player, merged).started(), true);
            tickUntil(level, merged, 16, phase -> phase == AlchemyPhase.IDLE);
            context.expect("merge-room", merged.container().getItem(5).getCount(), 64);
            context.expect("merge-other", merged.container().getItem(6).is(Items.STONE) && merged.container().getItem(6).getCount() == 64, true);
        });
    }

    private static void oneRoll(ProbeContext context) {
        scenario(context, fixture -> {
            ServerLevel level = fixture.level();
            ServerPlayer player = fixture.player();
            List<BlockPos> touched = fixture.touched();
            BlockPos at = fixture.at();
            AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            furnace.refreshStructure();
            furnace.setTargetTemperature(50);
            fillMarked(furnace, Items.LILAC);
            player.getRandom().setSeed(0xA17E5L);
            RandomSource mirror = RandomSource.create(0xA17E5L);
            double first = 10.0D + mirror.nextDouble() * 10.0D;
            double second = 10.0D + mirror.nextDouble() * 10.0D;
            StartResult started = AlchemyWorkstationService.start(player, furnace);
            long frozen = furnace.state().session().map(AlchemySession::totalTicks).orElse(-1L);
            long firstTicks = Math.max(1L, Math.round(first));
            long secondTicks = Math.max(1L, Math.round(second));
            context.expect("roll-started", started.started(), true);
            context.expect("roll-one-evaluation", frozen, firstTicks);
            context.expect("roll-not-second", frozen == secondTicks && firstTicks != secondTicks, false);
            context.expect("roll-target", furnace.state().session().map(AlchemySession::frozenTarget).orElse(-1.0D), 50.0D);
        });
    }

    private static void conflicts(ProbeContext context) {
        scenario(context, fixture -> {
            ServerLevel level = fixture.level();
            ServerPlayer player = fixture.player();
            List<BlockPos> touched = fixture.touched();
            BlockPos at = fixture.at();
            AlchemyFurnaceBlockEntity first = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            first.refreshStructure();
            AlchemyFurnaceBlockEntity second = placeOverlap(level, at.offset(2, 0, 0), Direction.NORTH, item(level, WIDE), touched);
            second.refreshStructure();
            second.setTargetTemperature(50);
            context.expect("claim-conflict", second.structureStatus().conflicts().isEmpty(), false);
            fill(second);
            StartResult refused = AlchemyWorkstationService.start(player, second);
            context.expect("conflict-refuse", refused.failure(), AlchemyFailure.STRUCTURE);
            context.expect("conflict-no-spend", second.container().getItem(0).getCount(), 2);
            context.expect("conflict-owner-formed", level.getBlockState(at).getValue(AlchemyFurnaceBlock.FORMED), true);
        });
    }

    private static void regressions(ProbeContext context) {
        scenario(context, fixture -> {
            ServerLevel level = fixture.level();
            ServerPlayer player = fixture.player();
            List<BlockPos> touched = fixture.touched();
            BlockPos at = fixture.at();
            Gate gate = fixture.gate();
            AlchemyFurnaceBlockEntity primary = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            AlchemyFurnaceBlockEntity other = place(level, at.offset(0, 0, 8), Direction.NORTH, item(level, WIDE), touched);
            primary.refreshStructure();
            other.refreshStructure();
            primary.setTargetTemperature(50);
            other.setTargetTemperature(50);
            fill(primary);
            fill(other);
            gate.reenter = true;
            gate.primary = primary;
            gate.other = other;
            gate.player = player;
            gate.reentered = null;
            AlchemyWorkstationService.start(player, primary);
            context.expect("reenter-rejected", gate.reentered != null && !gate.reentered.started() && gate.reentered.failure() == AlchemyFailure.ACTIVE, true);
            context.expect("reenter-one-session", primary.state().session().isPresent(), true);
            gate.reenter = false;
            fixture.clearFurnace(at);
            fixture.clearFurnace(at.offset(0, 0, 8));
            AlchemyFurnaceBlockEntity moved = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            moved.refreshStructure();
            moved.setTargetTemperature(50);
            fill(moved);
            int held = count(player, Items.ALLIUM);
            gate.move = true;
            gate.primary = moved;
            gate.player = player;
            StartResult move = AlchemyWorkstationService.start(player, moved);
            gate.move = false;
            context.expect("move-rejected", move.started(), false);
            context.expect("move-no-dup", count(player, Items.ALLIUM) + moved.container().getItem(0).getCount(), held + 2);
            fixture.clearFurnace(at);
            AlchemyFurnaceBlockEntity restart = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            restart.refreshStructure();
            restart.setTargetTemperature(50);
            fill(restart);
            gate.restart = true;
            gate.primary = restart;
            gate.player = player;
            context.expect("restart-start", AlchemyWorkstationService.start(player, restart).started(), true);
            tickUntil(level, restart, 8, phase -> phase == AlchemyPhase.IDLE || restart.state().session().isEmpty() || restart.phase() == AlchemyPhase.WARMING && gate.restarted);
            gate.restart = false;
            context.expect("post-restart-kept", restart.state().session().isPresent() && restart.phase() != AlchemyPhase.IDLE, true);
            fixture.clearFurnace(at);
            AlchemyFurnaceBlockEntity aborted = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            aborted.refreshStructure();
            aborted.setTargetTemperature(50);
            fill(aborted);
            AlchemyWorkstationService.start(player, aborted);
            AlchemyWorkstationService.abort(level, aborted.getBlockPos(), aborted);
            context.expect("abort-cause", aborted.state().session().flatMap(AlchemySession::failure).orElse(null), AlchemyFailure.CANCELLED);
            context.expect("abort-failure-output", aborted.state().session().map(session -> session.pendingOutputs().stream().anyMatch(stack -> stack.is(Items.CHARCOAL))).orElse(false), true);
            context.expect("abort-not-success", aborted.state().session().map(session -> session.pendingOutputs().stream().anyMatch(stack -> stack.is(Items.PAPER))).orElse(false), false);
            BlockPos shell = AlchemyFurnaceStructure.world(at, Direction.NORTH, 0);
            fixture.clearFurnace(at);
            AlchemyFurnaceBlockEntity formed = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            formed.refreshStructure();
            formed.setTargetTemperature(50);
            fill(formed);
            AlchemyWorkstationService.start(player, formed);
            level.setBlockAndUpdate(shell, Blocks.AIR.defaultBlockState());
            context.expect("structure-cause", formed.state().session().flatMap(AlchemySession::failure).orElse(null), AlchemyFailure.STRUCTURE);
            BlockPos ownerPos = at.offset(0, 0, 10);
            place(level, ownerPos, Direction.NORTH, item(level, WIDE), touched).refreshStructure();
            BlockPos owned = AlchemyFurnaceStructure.world(ownerPos, Direction.NORTH, 0);
            int ownedPart = level.getBlockState(owned).getValue(AlchemyFurnaceCasingBlock.PART);
            BlockPos intruderPos = ownerPos.offset(-2, 0, 0);
            if (!level.isLoaded(intruderPos) || !level.getBlockState(intruderPos).isAir())
                throw new PlaceRefused("intruder " + intruderPos);
            touched.add(intruderPos.immutable());
            level.setBlockAndUpdate(intruderPos, MxtBlocks.ALCHEMY_FURNACE.get().defaultBlockState().setValue(AlchemyFurnaceBlock.FACING, Direction.NORTH));
            level.setBlockAndUpdate(intruderPos, Blocks.AIR.defaultBlockState());
            context.expect("release-keeps-owner", level.getBlockState(owned).getValue(AlchemyFurnaceCasingBlock.PART), ownedPart);
            context.expect("release-owner-formed", level.getBlockState(ownerPos).getValue(AlchemyFurnaceBlock.FORMED), true);
            fixture.clearFurnace(ownerPos);
            fixture.clearFurnace(at);
            AlchemyFurnaceBlockEntity idle = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            idle.refreshStructure();
            LevelChunk chunk = level.getChunkAt(at);
            chunk.tryMarkSaved();
            tick(level, idle);
            context.expect("idle-not-dirtied", chunk.isUnsaved(), false);
        });
    }

    private static void criterion(ProbeContext context) {
        scenario(context, fixture -> {
            ServerLevel level = fixture.level();
            ServerPlayer player = fixture.player();
            List<BlockPos> touched = fixture.touched();
            BlockPos at = fixture.at();
            AdvancementHolder holder = level.getServer().getAdvancements().get(ProbeDefinitions.id("alchemy/brew"));
            if (holder == null || player.getAdvancements().getOrStartProgress(holder).isDone()) {
                context.expect("advancement-setup", false, true);
                return;
            }
            fixture.revokeBrew(holder);
            int xp = player.totalExperience;
            int crystals = count(player, Items.PRISMARINE_CRYSTALS);
            FakePlayer viewer = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "AlchemyViewer"));
            int viewerXp = viewer.totalExperience;
            int viewerCrystals = count(viewer, Items.PRISMARINE_CRYSTALS);
            AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            furnace.refreshStructure();
            furnace.setTargetTemperature(50);
            fillMarked(furnace, Items.SPORE_BLOSSOM);
            context.expect("criterion-start", AlchemyWorkstationService.start(player, furnace).started(), true);
            tickUntil(level, furnace, 16, phase -> phase == AlchemyPhase.IDLE);
            boolean earned = player.getAdvancements().getOrStartProgress(holder).isDone();
            context.expect("criterion-earned", earned, true);
            context.expect("criterion-xp", player.totalExperience, xp + 1);
            context.expect("criterion-action", count(player, Items.PRISMARINE_CRYSTALS), crystals + 1);
            context.expect("criterion-viewer-xp", viewer.totalExperience, viewerXp);
            context.expect("criterion-viewer-action", count(viewer, Items.PRISMARINE_CRYSTALS), viewerCrystals);
            int afterXp = player.totalExperience;
            int afterCrystals = count(player, Items.PRISMARINE_CRYSTALS);
            tick(level, furnace);
            context.expect("criterion-xp-once", player.totalExperience, afterXp);
            context.expect("criterion-action-once", count(player, Items.PRISMARINE_CRYSTALS), afterCrystals);
        });
    }

    private static void limitsAndParts(ProbeContext context) {
        scenario(context, fixture -> {
            ServerLevel level = fixture.level();
            ServerPlayer player = fixture.player();
            List<BlockPos> touched = fixture.touched();
            BlockPos at = fixture.at();
            AlchemyFurnaceBlockEntity brittle = place(level, at, Direction.NORTH, item(level, WIDE), touched, BRITTLE_WALL);
            brittle.refreshStructure();
            context.expect("brittle-limit", brittle.maximumTemperature(), 80.0D);
            context.expect("brittle-rejects-100", brittle.setTargetTemperature(100.0D), false);
            context.expect("brittle-accepts-80", brittle.setTargetTemperature(80.0D), true);
            fillMarked(brittle, Items.POPPY);
            context.expect("brittle-hot", AlchemyWorkstationService.start(player, brittle).failure(), AlchemyFailure.TEMPERATURE);
            context.expect("brittle-kept", brittle.container().getItem(0).getCount(), 2);
            fixture.clearFurnace(at);
            AlchemyFurnaceBlockEntity weak = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            weak.refreshStructure();
            placeHeat(level, at, Direction.NORTH, AlchemyTestHeatBlocks.WEAK_FIRE.get());
            context.expect("weak-heat-limit", weak.heatTemperatureLimit(), 80.0D);
            context.expect("weak-heat-beats-tag", weak.maximumTemperature(), 80.0D);
            context.expect("weak-heat-rejects-100", weak.setTargetTemperature(100.0D), false);
            weak.setTargetTemperature(80.0D);
            fillMarked(weak, Items.POPPY);
            context.expect("weak-heat-hot", AlchemyWorkstationService.start(player, weak).failure(), AlchemyFailure.TEMPERATURE);
            context.expect("weak-heat-kept", weak.container().getItem(0).getCount(), 2);
            context.expect("weak-heat-remains", heatState(level, weak).is(AlchemyTestHeatBlocks.WEAK_FIRE.get()), true);
            fixture.clearFurnace(at);
            AlchemyFurnaceBlockEntity mixed = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            BlockPos low = AlchemyFurnaceStructure.world(at, Direction.NORTH, 0);
            if (level.getBlockEntity(low) instanceof AlchemyFurnaceCasingBlockEntity casing)
                casing.acceptWallItem(wallItem(level, BRITTLE_WALL));
            mixed.refreshStructure();
            context.expect("mixed-min-wall", mixed.maximumTemperature(), 80.0D);
            BlockPos bare = AlchemyFurnaceStructure.world(at, Direction.NORTH, 1);
            if (level.getBlockEntity(bare) instanceof AlchemyFurnaceCasingBlockEntity empty)
                empty.acceptWallItem(ItemStack.EMPTY);
            mixed.refreshStructure();
            fill(mixed);
            // A wall casing that keeps its block but loses its material is not a hole in the structure, so the
            // structural check still passes and the beam collapses the temperature ceiling instead.
            context.expect("missing-material", AlchemyWorkstationService.start(player, mixed).failure(), AlchemyFailure.TEMPERATURE);
            context.expect("missing-material-kept", mixed.container().getItem(0).getCount(), 2);
            fixture.clearFurnace(at);
            // A specification that declares its own ceiling is the third and lowest one: kiln walls take 200 and the
            // test heat block gives 80, yet this core caps the furnace at 60.
            AlchemyFurnaceBlockEntity capped = place(level, at, Direction.NORTH, item(level, CAPPED), touched);
            capped.refreshStructure();
            context.expect("capped-limit", capped.maximumTemperature(), 60.0D);
            context.expect("capped-rejects-80", capped.setTargetTemperature(80.0D), false);
            context.expect("capped-accepts-60", capped.setTargetTemperature(60.0D), true);
            fixture.clearFurnace(at);
            AlchemyFurnaceBlockEntity owned = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            owned.refreshStructure();
            fill(owned);
            owned.container().setItem(5, new ItemStack(Items.DIAMOND, 3));
            context.expect("core-hopper-has-no-aggregate", HopperBlockEntity.getContainerAt(level, at) == null, true);
            BlockPos main = AlchemyFurnaceStructure.world(at, Direction.NORTH, AlchemyFurnaceStructure.MAIN_INDEX);
            fixture.clearDrops(main);
            level.destroyBlock(main, true, player);
            List<ItemEntity> mainDrops = fixture.drops(main);
            context.expect("main-drops-herb", count(mainDrops, Items.ALLIUM), 2);
            context.expect("main-drops-not-aux", count(mainDrops, Items.CORNFLOWER), 0);
            context.expect("aux-kept", owned.container().getItem(2).getCount(), 2);
            fixture.clearDrops(at);
            level.destroyBlock(at, true, player);
            List<ItemEntity> coreDrops = fixture.drops(at);
            context.expect("core-one-furnace", count(coreDrops, MxtBlocks.ALCHEMY_FURNACE.get().asItem()), 1);
            context.expect("core-no-heat-drop", count(coreDrops, AlchemyTestHeatBlocks.FIRE.get().asItem()), 0);
            context.expect("core-keeps-heat-block", heatState(level, owned).is(AlchemyTestHeatBlocks.FIRE.get()), true);
            context.expect("core-not-aux", count(coreDrops, Items.CORNFLOWER), 0);
            context.expect("core-not-output", count(coreDrops, Items.DIAMOND), 0);
            BlockPos aux = AlchemyFurnaceStructure.world(at, Direction.NORTH, AlchemyFurnaceStructure.AUXILIARY_INDEX);
            BlockPos output = AlchemyFurnaceStructure.world(at, Direction.NORTH, AlchemyFurnaceStructure.OUTPUT_INDEX);
            context.expect("core-removal-keeps-aux", ((AlchemyFurnaceInventoryBlockEntity) level.getBlockEntity(aux)).inventory().getItem(0).getCount(), 2);
            context.expect("core-removal-keeps-output", ((AlchemyFurnaceInventoryBlockEntity) level.getBlockEntity(output)).inventory().getItem(0).getCount(), 3);
        });
    }

    private static void callbackFailures(ProbeContext context) {
        scenario(context, fixture -> {
            ServerLevel level = fixture.level();
            ServerPlayer player = fixture.player();
            List<BlockPos> touched = fixture.touched();
            BlockPos at = fixture.at();
            Gate gate = fixture.gate();
            AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            furnace.refreshStructure();
            furnace.setTargetTemperature(50);
            fill(furnace);
            gate.primary = furnace;
            gate.removeHeat = true;
            StartResult result = AlchemyWorkstationService.start(player, furnace);
            gate.removeHeat = false;
            context.expect("pre-heat-removed", heatState(level, furnace).isAir(), true);
            context.expect("pre-heat-rejected", result.failure(), AlchemyFailure.TEMPERATURE);
            context.expect("pre-heat-main-kept", furnace.container().getItem(0).getCount(), 2);
            context.expect("pre-heat-aux-kept", furnace.container().getItem(2).getCount(), 2);
            context.expect("pre-heat-catalyst-kept", furnace.container().getItem(4).getCount(), 1);
            fixture.clearFurnace(at);
            AlchemyFurnaceBlockEntity replaced = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            replaced.refreshStructure();
            replaced.setTargetTemperature(50);
            fillMarked(replaced, Items.SUNFLOWER);
            gate.primary = replaced;
            gate.player = player;
            gate.restart = true;
            gate.restartResult = null;
            context.expect("action-replacement-start", AlchemyWorkstationService.start(player, replaced).started(), true);
            tickUntil(level, replaced, 8, phase -> phase == AlchemyPhase.IDLE || level.getBlockState(at).is(Blocks.GOLD_BLOCK));
            gate.restart = false;
            context.expect("action-before-post", level.getBlockState(at).is(Blocks.GOLD_BLOCK), true);
            context.expect("post-cannot-restart-removed-core", gate.restartResult != null && !gate.restartResult.started(), true);
            BlockPos main = AlchemyFurnaceStructure.world(at, Direction.NORTH, AlchemyFurnaceStructure.MAIN_INDEX);
            context.expect("stale-core-cannot-write-main", ((AlchemyFurnaceInventoryBlockEntity) level.getBlockEntity(main)).inventory().getItem(0).isEmpty(), true);
        });
    }

    private static void menuOwnership(ProbeContext context) {
        scenario(context, fixture -> {
            ServerLevel level = fixture.level();
            ServerPlayer player = fixture.player();
            List<BlockPos> touched = fixture.touched();
            BlockPos at = fixture.at();
            AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            furnace.refreshStructure();
            FakePlayer viewer = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "AlchemyMenus"));
            viewer.setPos(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
            AlchemyFurnaceMenu monitor = new AlchemyFurnaceMenu(20, viewer.getInventory(), at, AlchemyFurnaceMenu.View.MONITOR);
            viewer.containerMenu = monitor;
            monitor.handleAction(viewer, new AlchemyActionC2SPayload(20, Action.TEMPERATURE, 50.125D));
            context.expect("temperature-exact-round-trip", monitor.temperatureAck().accepted() && monitor.temperatureAck().target() == 50.125D && furnace.state().targetTemperature() == 50.125D, true);
            int epoch = monitor.temperatureAck().epoch();
            monitor.handleAction(viewer, new AlchemyActionC2SPayload(20, Action.TEMPERATURE, 50.125D));
            context.expect("temperature-same-value-ack", monitor.temperatureAck().accepted() && monitor.temperatureAck().epoch() != epoch, true);
            epoch = monitor.temperatureAck().epoch();
            monitor.handleAction(viewer, new AlchemyActionC2SPayload(20, Action.TEMPERATURE, Double.NaN));
            context.expect("temperature-reject-keeps-value", !monitor.temperatureAck().accepted() && monitor.temperatureAck().epoch() != epoch && monitor.temperatureAck().target() == 50.125D, true);
            BlockPos outputPos = AlchemyFurnaceStructure.world(at, Direction.NORTH, AlchemyFurnaceStructure.OUTPUT_INDEX);
            AlchemyFurnaceInventoryBlockEntity output = (AlchemyFurnaceInventoryBlockEntity) level.getBlockEntity(outputPos);
            output.inventory().setItem(0, new ItemStack(Items.DIAMOND));
            viewer.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 3));
            AlchemyFurnaceMenu outputs = new AlchemyFurnaceMenu(21, viewer.getInventory(), outputPos, AlchemyFurnaceMenu.View.OUTPUT);
            outputs.quickMoveStack(viewer, outputs.menuIndex("inventory_9"));
            context.expect("output-occupied-rejects-shift", output.inventory().getItem(0).getCount(), 1);
            context.expect("output-rejection-keeps-player-items", count(viewer, Items.DIAMOND), 3);
            viewer.getInventory().clearContent();
            furnace.setTargetTemperature(50);
            fill(furnace);
            context.expect("busy-menu-start", AlchemyWorkstationService.start(player, furnace).started(), true);
            BlockPos mainPos = AlchemyFurnaceStructure.world(at, Direction.NORTH, AlchemyFurnaceStructure.MAIN_INDEX);
            AlchemyFurnaceInventoryBlockEntity main = (AlchemyFurnaceInventoryBlockEntity) level.getBlockEntity(mainPos);
            main.inventory().setItem(0, new ItemStack(Items.ALLIUM));
            viewer.getInventory().setItem(9, new ItemStack(Items.ALLIUM, 3));
            AlchemyFurnaceMenu inputs = new AlchemyFurnaceMenu(22, viewer.getInventory(), mainPos, AlchemyFurnaceMenu.View.MAIN);
            inputs.quickMoveStack(viewer, inputs.menuIndex("inventory_9"));
            context.expect("busy-occupied-input-rejects-shift", main.inventory().getItem(0).getCount(), 1);
            context.expect("busy-input-keeps-player-items", count(viewer, Items.ALLIUM), 3);
            // The heat block is a world block: nothing locks it during a batch, and taking it away only stops the heat.
            BlockPos heat = furnace.heatSourcePos();
            tick(level, furnace);
            double warm = furnace.temperature();
            level.setBlockAndUpdate(heat, Blocks.AIR.defaultBlockState());
            tick(level, furnace);
            context.expect("busy-heat-can-be-removed", level.getBlockState(heat).isAir(), true);
            context.expect("busy-heat-removed-cools", furnace.temperature() < warm, true);
            level.setBlockAndUpdate(heat, AlchemyTestHeatBlocks.FIRE.get().defaultBlockState());
            AlchemyWorkstationService.abort(level, at, furnace);
            tickUntil(level, furnace, 8, phase -> phase == AlchemyPhase.IDLE);
            viewer.getInventory().clearContent();
            viewer.getInventory().setItem(9, new ItemStack(Items.ALLIUM, 3));
            inputs.quickMoveStack(viewer, inputs.menuIndex("inventory_9"));
            context.expect("idle-occupied-input-merges", main.inventory().getItem(0).getCount(), 4);
            context.expect("idle-input-moves-player-items", count(viewer, Items.ALLIUM), 0);
            BlockState mainState = level.getBlockState(mainPos);
            level.setBlockAndUpdate(mainPos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(mainPos, mainState);
            context.expect("replaced-input-invalidates-menu", inputs.stillValid(viewer), false);
            viewer.getInventory().setItem(9, new ItemStack(Items.ALLIUM, 2));
            inputs.quickMoveStack(viewer, inputs.menuIndex("inventory_9"));
            context.expect("stale-input-keeps-player-items", count(viewer, Items.ALLIUM), 2);
            context.expect("stale-input-cannot-write-replacement", ((AlchemyFurnaceInventoryBlockEntity) level.getBlockEntity(mainPos)).inventory().isEmpty(), true);
            level.setBlockAndUpdate(mainPos, MxtBlocks.ALCHEMY_AUXILIARY_INPUT.get().defaultBlockState());
            boolean rejected = false;
            try {
                new AlchemyFurnaceMenu(23, viewer.getInventory(), mainPos, AlchemyFurnaceMenu.View.MAIN);
            } catch (IllegalStateException expected) {
                rejected = true;
            }
            context.expect("wrong-kind-menu-rejected", rejected, true);
            AlchemyFurnaceMenu auxiliary = new AlchemyFurnaceMenu(24, viewer.getInventory(), mainPos, AlchemyFurnaceMenu.View.AUXILIARY);
            context.expect("replacement-role-opens", auxiliary.stillValid(viewer), true);
            level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(at, MxtBlocks.ALCHEMY_FURNACE.get().defaultBlockState());
            AlchemyFurnaceBlockEntity replacement = (AlchemyFurnaceBlockEntity) level.getBlockEntity(at);
            replacement.acceptFurnaceItem(item(level, WIDE));
            double target = replacement.state().targetTemperature();
            viewer.containerMenu = monitor;
            monitor.handleAction(viewer, new AlchemyActionC2SPayload(20, Action.TEMPERATURE, 40.0D));
            context.expect("replaced-core-invalidates-menu", monitor.stillValid(viewer), false);
            context.expect("stale-core-action-keeps-replacement", replacement.state().targetTemperature(), target);
        });
    }

    /**
     * The heat lookup: the bottom centre cell, the table entry behind a block tag, the priority override, and a
     * block whose own {@code AlchemyHeatSource} answer outranks the table.
     */
    private static void heatSources(ProbeContext context) {
        scenario(context, fixture -> {
            ServerLevel level = fixture.level();
            ServerPlayer player = fixture.player();
            List<BlockPos> touched = fixture.touched();
            BlockPos at = fixture.at();
            AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, item(level, WIDE), touched);
            furnace.refreshStructure();
            context.expect("heat-cell-is-bottom-centre", furnace.heatSourcePos(), at.offset(0, -1, 1));
            context.expect("heat-cell-never-required", AlchemyFurnaceStructure.required(AlchemyFurnaceStructure.HEAT_INDEX), false);
            // One tag entry covers both test blocks at 150 / 40, and the kiln wall is rated 200, so the heat is the ceiling.
            context.expect("heat-tag-limit", furnace.heatTemperatureLimit(), 150.0D);
            context.expect("heat-tag-max", furnace.maximumTemperature(), 150.0D);
            context.expect("heat-tag-start", startBatch(level, player, furnace, 50.0D), true);
            tick(level, furnace);
            context.expect("heat-tag-rate", furnace.temperature(), 40.0D);
            // A block that is not a heat source is just a block: with it there the furnace has no heat at all.
            level.setBlockAndUpdate(furnace.heatSourcePos(), Blocks.STONE.defaultBlockState());
            AlchemyWorkstationService.abort(level, at, furnace);
            tickUntil(level, furnace, 8, phase -> phase == AlchemyPhase.IDLE);
            context.expect("heat-non-source", furnace.maximumTemperature(), 0.0D);
            context.expect("heat-absent-rejects-50", furnace.setTargetTemperature(50.0D), false);
            fill(furnace);
            context.expect("heat-absent-start", AlchemyWorkstationService.start(player, furnace).failure(), AlchemyFailure.TEMPERATURE);
            context.expect("heat-absent-kept", furnace.container().getItem(0).getCount(), 2);
            // The API branch answers for itself: the table says 999 for this block, the block says 250 / 25 while lit.
            level.setBlockAndUpdate(furnace.heatSourcePos(), advanced(true));
            context.expect("heat-api-limit", furnace.heatTemperatureLimit(), 250.0D);
            context.expect("heat-api-wall-binds", furnace.maximumTemperature(), 200.0D);
            context.expect("heat-api-start", startBatch(level, player, furnace, 50.0D), true);
            tick(level, furnace);
            context.expect("heat-api-rate", furnace.temperature(), 25.0D);
            AlchemyWorkstationService.abort(level, at, furnace);
            tickUntil(level, furnace, 8, phase -> phase == AlchemyPhase.IDLE);
            level.setBlockAndUpdate(furnace.heatSourcePos(), advanced(false));
            context.expect("heat-api-unlit", furnace.heatTemperatureLimit(), 0.0D);
        });
    }

    // The setup the command probe did once around all of its scenarios, done per test instead.
    private static void scenario(ProbeContext context, Consumer<Fixture> body) {
        Fixture fixture = new Fixture(context);
        try {
            fixture.begin();
            body.accept(fixture);
        } catch (PlaceRefused refused) {
            context.fail("place-refused " + refused.getMessage());
        } catch (RuntimeException exception) {
            context.fail("exception " + exception.getClass().getSimpleName() + " " + exception.getMessage());
        } finally {
            fixture.end();
        }
    }

    private static AlchemyFurnaceBlockEntity place(ServerLevel level, BlockPos controller, Direction facing, ItemStack furnaceItem, List<BlockPos> touched) {
        return place(level, controller, facing, furnaceItem, touched, KILN_WALL);
    }

    private static AlchemyFurnaceBlockEntity place(ServerLevel level, BlockPos controller, Direction facing, ItemStack furnaceItem, List<BlockPos> touched, Identifier wallId) {
        BlockPos[] cells = new BlockPos[27];
        for (int cell = 0; cell < 27; cell++) {
            if (!AlchemyFurnaceStructure.required(cell)) continue;
            BlockPos pos = AlchemyFurnaceStructure.world(controller, facing, cell).immutable();
            cells[cell] = pos;
            if (touched.contains(pos)) continue;
            if (!level.isLoaded(pos)) throw new PlaceRefused("unloaded " + pos);
            if (!level.getBlockState(pos).isAir()) throw new PlaceRefused("occupied " + pos);
        }
        ItemStack wall = wallItem(level, wallId);
        for (int cell = 0; cell < 27; cell++) {
            if (!AlchemyFurnaceStructure.required(cell)) continue;
            BlockPos pos = cells[cell];
            if (!touched.contains(pos)) touched.add(pos);
            level.setBlockAndUpdate(pos, shell(cell, facing));
            if (AlchemyFurnaceStructure.wall(cell) && level.getBlockEntity(pos) instanceof AlchemyFurnaceCasingBlockEntity casing)
                casing.acceptWallItem(wall.copy());
        }
        if (!(level.getBlockEntity(controller) instanceof AlchemyFurnaceBlockEntity furnace))
            throw new PlaceRefused("controller " + controller);
        furnace.acceptFurnaceItem(furnaceItem);
        BlockPos heat = AlchemyFurnaceStructure.heatSource(controller, facing).immutable();
        touched.add(heat);
        placeHeat(level, controller, facing, AlchemyTestHeatBlocks.FIRE.get());
        return furnace;
    }

    private static BlockState shell(int cell, Direction facing) {
        if (!AlchemyFurnaceStructure.required(cell)) return Blocks.AIR.defaultBlockState();
        if (cell == AlchemyFurnaceStructure.CONTROLLER_INDEX)
            return MxtBlocks.ALCHEMY_FURNACE.get().defaultBlockState().setValue(AlchemyFurnaceBlock.FACING, facing);
        if (cell == AlchemyFurnaceStructure.MAIN_INDEX)
            return MxtBlocks.ALCHEMY_MAIN_INPUT.get().defaultBlockState().setValue(AlchemyFurnaceInventoryBlock.FACING, facing);
        if (cell == AlchemyFurnaceStructure.AUXILIARY_INDEX)
            return MxtBlocks.ALCHEMY_AUXILIARY_INPUT.get().defaultBlockState().setValue(AlchemyFurnaceInventoryBlock.FACING, facing);
        if (cell == AlchemyFurnaceStructure.OUTPUT_INDEX)
            return MxtBlocks.ALCHEMY_OUTPUT.get().defaultBlockState().setValue(AlchemyFurnaceInventoryBlock.FACING, facing);
        return MxtBlocks.ALCHEMY_FURNACE_CASING.get().defaultBlockState().setValue(AlchemyFurnaceCasingBlock.FACING, facing);
    }

    private static ItemStack item(ServerLevel level, Identifier id) {
        ItemStack stack = MxtBlocks.ALCHEMY_FURNACE.toStack();
        stack.set(MxtDataComponents.ALCHEMY_FURNACE.get(), holder(level, id));
        return stack;
    }

    private static ItemStack overrideItem(ServerLevel level) {
        ItemStack stack = item(level, WIDE);
        stack.set(MxtDataComponents.QUALITY.get(), MxtDatapackRegistries.holder(level.registryAccess(), MxtResourceKeys.ITEM_QUALITY, ProbeDefinitions.id("alchemy/kiln_b")).orElseThrow());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Probe Kiln"));
        return stack;
    }

    // The same specification with a tier written on the core itself, which is how a furnace states one by hand.
    private static ItemStack tieredCore(ServerLevel level, Identifier quality) {
        ItemStack stack = item(level, WIDE);
        stack.set(MxtDataComponents.QUALITY.get(), MxtDatapackRegistries
                .holder(level.registryAccess(), MxtResourceKeys.ITEM_QUALITY, quality).orElseThrow());
        return stack;
    }

    private static Holder<AlchemyFurnaceDefinition> holder(ServerLevel level, Identifier id) {
        return MxtDatapackRegistries.holder(level.registryAccess(), MxtResourceKeys.ALCHEMY_FURNACE, id).orElseThrow();
    }

    private static Holder<Aura> aura(ServerLevel level, Identifier id) {
        return MxtDatapackRegistries.holder(level.registryAccess(), MxtResourceKeys.AURA, id).orElseThrow();
    }

    private static Holder<MedicinalProperty> property(ServerLevel level, Identifier id) {
        return MxtDatapackRegistries.holder(level.registryAccess(), MxtResourceKeys.MEDICINAL_PROPERTY, id).orElseThrow();
    }

    private static double getMain(AlchemyPreview preview, Holder<MedicinalProperty> property, double fallback) {
        return preview.mixture().main().getOrDefault(property, fallback);
    }

    private static void fill(AlchemyFurnaceBlockEntity furnace) {
        clearInputs(furnace);
        furnace.container().setItem(0, new ItemStack(Items.ALLIUM, 2));
        furnace.container().setItem(2, new ItemStack(Items.CORNFLOWER, 2));
        furnace.container().setItem(4, new ItemStack(Items.OXEYE_DAISY));
    }

    private static void fillMarked(AlchemyFurnaceBlockEntity furnace, Item marker) {
        fill(furnace);
        furnace.container().setItem(1, new ItemStack(marker));
    }

    private static void clearInputs(AlchemyFurnaceBlockEntity furnace) {
        for (int slot = 0; slot < 5; slot++) furnace.container().setItem(slot, ItemStack.EMPTY);
    }

    private static Identifier resolvedId(AlchemyPreview preview) {
        return preview.resolvedRecipe().map(ResourceKey::identifier).orElse(null);
    }

    private static boolean matched(AlchemyPreview preview, Identifier id) {
        for (Candidate candidate : preview.candidates())
            if (candidate.matched() && candidate.id().identifier().equals(id)) return true;
        return false;
    }

    private static int matchedCount(AlchemyPreview preview) {
        int count = 0;
        for (Candidate candidate : preview.candidates()) if (candidate.matched()) count++;
        return count;
    }

    private static boolean ediblePractice(HolderLookup.Provider access, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!stack.is(MxtItems.PILL.get()) || stack.getCount() != 4 || !stack.has(DataComponents.CONSUMABLE))
                continue;
            PillComponent pill = stack.get(MxtDataComponents.PILL.get());
            if (pill == null) return false;
            JsonElement encoded = PillComponent.CODEC.encodeStart(access.createSerializationContext(JsonOps.INSTANCE), pill).getOrThrow();
            return encoded.isJsonObject() && encoded.getAsJsonObject().has("pill")
                    && "mxt_test:toxicity".equals(encoded.getAsJsonObject().get("pill").getAsString());
        }
        return false;
    }

    private static ItemStack stored(AlchemyFurnaceBlockEntity furnace, Item item) {
        for (int slot = 5; slot < 9; slot++)
            if (furnace.container().getItem(slot).is(item)) return furnace.container().getItem(slot);
        return ItemStack.EMPTY;
    }

    private static void tick(ServerLevel level, AlchemyFurnaceBlockEntity furnace) {
        if (furnace.isRemoved() || !(level.getBlockEntity(furnace.getBlockPos()) instanceof AlchemyFurnaceBlockEntity live))
            return;
        AlchemyFurnaceBlockEntity.serverTick(level, live.getBlockPos(), live.getBlockState(), live);
    }

    private static void tickUntil(ServerLevel level, AlchemyFurnaceBlockEntity furnace, int limit, Predicate<AlchemyPhase> done) {
        for (int i = 0; i < limit && !done.test(furnace.phase()); i++) tick(level, furnace);
    }

    private static AlchemyFurnaceBlockEntity reload(ServerLevel level, AlchemyFurnaceBlockEntity furnace) {
        CompoundTag tag = furnace.saveWithFullMetadata(level.registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(furnace.getBlockPos(), furnace.getBlockState(), tag, level.registryAccess());
        if (!(loaded instanceof AlchemyFurnaceBlockEntity copy))
            throw new IllegalStateException("Furnace snapshot did not decode");
        return copy;
    }

    private static void erase(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof AlchemyFurnaceBlockEntity furnace) furnace.voidContents();
        else if (entity instanceof AlchemyFurnaceInventoryBlockEntity inventory) inventory.voidContents();
        else if (entity instanceof AlchemyFurnaceCasingBlockEntity casing) casing.voidContents();
        if (!level.getBlockState(pos).isAir())
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static ItemStack wallItem(ServerLevel level, Identifier id) {
        ItemStack stack = MxtBlocks.ALCHEMY_FURNACE_CASING.toStack();
        stack.set(MxtDataComponents.ALCHEMY_WALL_MATERIAL.get(),
                MxtDatapackRegistries.holder(level.registryAccess(), MxtResourceKeys.ALCHEMY_WALL_MATERIAL, id).orElseThrow());
        return stack;
    }

    private static void placeHeat(ServerLevel level, BlockPos controller, Direction facing, Block block) {
        level.setBlockAndUpdate(AlchemyFurnaceStructure.heatSource(controller, facing), block.defaultBlockState());
    }

    private static BlockState heatState(ServerLevel level, AlchemyFurnaceBlockEntity furnace) {
        return level.getBlockState(furnace.heatSourcePos());
    }

    // One batch with room for it: aborts what runs, empties the output slots, then sets the target and starts.
    private static boolean startBatch(ServerLevel level, ServerPlayer player, AlchemyFurnaceBlockEntity furnace, double target) {
        AlchemyWorkstationService.abort(level, furnace.getBlockPos(), furnace);
        tickUntil(level, furnace, 8, phase -> phase == AlchemyPhase.IDLE);
        for (int slot = 5; slot < 9; slot++) furnace.container().setItem(slot, ItemStack.EMPTY);
        if (!furnace.setTargetTemperature(target)) return false;
        fill(furnace);
        return AlchemyWorkstationService.start(player, furnace).started();
    }

    private static BlockState advanced(boolean lit) {
        return AlchemyTestHeatBlocks.ADVANCED_FIRE.get().defaultBlockState()
                .setValue(AlchemyTestHeatBlocks.ADVANCED_LIT, lit);
    }

    private static int count(List<ItemEntity> entities, Item item) {
        int total = 0;
        for (ItemEntity entity : entities) if (entity.getItem().is(item)) total += entity.getItem().getCount();
        return total;
    }

    private static int count(Player player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            if (player.getInventory().getItem(slot).is(item)) total += player.getInventory().getItem(slot).getCount();
        return total;
    }

    private static int charcoalCount(AlchemyFurnaceBlockEntity furnace) {
        int pending = furnace.state().session().map(session -> session.pendingOutputs().stream().filter(stack -> stack.is(Items.CHARCOAL)).mapToInt(ItemStack::getCount).sum()).orElse(0);
        int stored = 0;
        for (int slot = 5; slot < 9; slot++)
            if (furnace.container().getItem(slot).is(Items.CHARCOAL))
                stored += furnace.container().getItem(slot).getCount();
        return pending + stored;
    }

    private static boolean HolderHelperEquals(ItemStack stack, Identifier quality) {
        Holder<?> holder = stack.get(MxtDataComponents.QUALITY.get());
        return holder != null && HolderHelper.id(holder).equals(quality);
    }

    private static ItemStack[] snapshot(ServerPlayer player) {
        ItemStack[] saved = new ItemStack[player.getInventory().getContainerSize()];
        for (int slot = 0; slot < saved.length; slot++) saved[slot] = player.getInventory().getItem(slot).copy();
        return saved;
    }

    private static void restore(ServerPlayer player, ItemStack[] saved) {
        for (int slot = 0; slot < saved.length && slot < player.getInventory().getContainerSize(); slot++)
            player.getInventory().setItem(slot, saved[slot]);
    }

    private static void shoot(ServerLevel level, ServerPlayer player, BlockPos shell, Holder<Aura> type, int amount) {
        SpiritBurstEntity burst = new SpiritBurstEntity(level, player, type, amount, 0xFFFFFF);
        burst.setPos(shell.getX() + 0.5D, shell.getY() + 0.5D, shell.getZ() - 0.35D);
        burst.setDeltaMovement(0.0D, 0.0D, 1.25D);
        level.addFreshEntity(burst);
        burst.tick();
        if (!burst.isRemoved()) burst.discard();
    }

    private static AlchemyFurnaceBlockEntity placeOverlap(ServerLevel level, BlockPos controller, Direction facing, ItemStack furnaceItem, List<BlockPos> touched) {
        if (!level.isLoaded(controller) || !level.getBlockState(controller).isAir())
            throw new PlaceRefused("overlap controller " + controller);
        ItemStack wall = wallItem(level, KILN_WALL);
        for (int cell = 0; cell < 27; cell++) {
            if (!AlchemyFurnaceStructure.required(cell)) continue;
            BlockPos pos = AlchemyFurnaceStructure.world(controller, facing, cell).immutable();
            if (!level.isLoaded(pos)) throw new PlaceRefused("unloaded " + pos);
            if (!level.getBlockState(pos).isAir()) continue;
            touched.add(pos);
            level.setBlockAndUpdate(pos, shell(cell, facing));
            if (AlchemyFurnaceStructure.wall(cell) && level.getBlockEntity(pos) instanceof AlchemyFurnaceCasingBlockEntity casing)
                casing.acceptWallItem(wall.copy());
        }
        if (!(level.getBlockEntity(controller) instanceof AlchemyFurnaceBlockEntity furnace))
            throw new PlaceRefused("overlap entity " + controller);
        furnace.acceptFurnaceItem(furnaceItem);
        BlockPos heat = AlchemyFurnaceStructure.heatSource(controller, facing).immutable();
        touched.add(heat);
        placeHeat(level, controller, facing, AlchemyTestHeatBlocks.FIRE.get());
        return furnace;
    }

    private static boolean hasOutput(AlchemyFurnaceBlockEntity furnace, Item item) {
        for (int slot = 5; slot < 9; slot++) if (furnace.container().getItem(slot).is(item)) return true;
        return furnace.state().session().map(session -> session.pendingOutputs().stream().anyMatch(stack -> stack.is(item))).orElse(false);
    }

    /**
     * What the command probe captured once for the whole run, captured per test and handed back when the body ends.
     */
    private static final class Fixture {
        private final ServerLevel level;
        private final ServerPlayer player;
        private final List<BlockPos> touched = new ArrayList<>();
        private final Set<ItemEntity> drops = new HashSet<>();
        private final AbstractContainerMenu previousMenu;
        private final ItemStack carried;
        private final ItemStack[] savedItems;
        private final int savedXp;
        private final Collection<ItemEntity> previousDrops;
        private final Gate gate;
        private boolean revokeBrew;
        private AdvancementHolder brewHolder;

        private Fixture(ProbeContext context) {
            this.level = context.level();
            this.player = context.player();
            this.gate = new Gate(this.level, this.touched, this.drops);
            this.previousMenu = this.player.containerMenu;
            this.carried = this.previousMenu.getCarried().copy();
            this.savedItems = snapshot(this.player);
            this.savedXp = this.player.totalExperience;
            this.previousDrops = this.player.captureDrops();
        }

        private void begin() {
            NeoForge.EVENT_BUS.register(this.gate);
            this.player.captureDrops(new ArrayList<>());
            this.player.getInventory().clearContent();
        }

        private void end() {
            try {
                for (BlockPos pos : this.touched) erase(this.level, pos);
                for (ItemEntity entity : this.drops) if (!entity.isRemoved()) entity.discard();
                this.drops.clear();
            } finally {
                NeoForge.EVENT_BUS.unregister(this.gate);
                this.player.captureDrops(this.previousDrops);
                this.player.containerMenu = this.previousMenu;
                this.previousMenu.setCarried(this.carried);
                restore(this.player, this.savedItems);
                if (this.player.totalExperience != this.savedXp)
                    this.player.giveExperiencePoints(this.savedXp - this.player.totalExperience);
                if (this.revokeBrew && this.brewHolder != null)
                    this.player.getAdvancements().revoke(this.brewHolder, "brew");
            }
        }

        /**
         * The old command's first cursor: a small offset from the player, room for one apparatus in the arena.
         */
        private BlockPos at() {
            return this.player.blockPosition().offset(SPOT_X, SPOT_Y, SPOT_Z);
        }

        private ServerLevel level() {
            return this.level;
        }

        private ServerPlayer player() {
            return this.player;
        }

        private List<BlockPos> touched() {
            return this.touched;
        }

        private Gate gate() {
            return this.gate;
        }

        private void revokeBrew(AdvancementHolder holder) {
            this.revokeBrew = true;
            this.brewHolder = holder;
        }

        private void clearDrops(BlockPos pos) {
            for (ItemEntity entity : this.level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4)))
                if (this.drops.contains(entity)) entity.discard();
        }

        private void clearFurnace(BlockPos controller) {
            for (int cell = 0; cell < 27; cell++) {
                BlockPos pos = AlchemyFurnaceStructure.world(controller, Direction.NORTH, cell);
                if (this.touched.contains(pos)) {
                    erase(this.level, pos);
                    this.touched.remove(pos);
                }
            }
            this.clearDrops(controller);
        }

        private List<ItemEntity> drops(BlockPos pos) {
            List<ItemEntity> found = new ArrayList<>();
            for (ItemEntity entity : this.level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)))
                if (this.drops.contains(entity)) found.add(entity);
            return found;
        }
    }

    private static final class PlaceRefused extends RuntimeException {
        private PlaceRefused(String message) {
            super(message);
        }
    }

    private static final class Gate {
        private final ServerLevel level;
        private final List<BlockPos> touched;
        private final Set<ItemEntity> drops;
        private final Thread thread = Thread.currentThread();
        private boolean cancel;
        private boolean mutate;
        private boolean reenter;
        private boolean move;
        private boolean removeHeat;
        private boolean restart;
        private boolean restarted;
        private int posts;
        private UUID postOperator;
        private AlchemyFurnaceBlockEntity primary;
        private AlchemyFurnaceBlockEntity other;
        private ServerPlayer player;
        private StartResult reentered;
        private StartResult restartResult;

        private Gate(ServerLevel level, List<BlockPos> touched, Set<ItemEntity> drops) {
            this.level = level;
            this.touched = touched;
            this.drops = drops;
        }

        @SubscribeEvent
        public void itemSpawned(EntityJoinLevelEvent event) {
            if (Thread.currentThread() == this.thread && event.getLevel() == this.level && !event.loadedFromDisk()
                    && event.getEntity() instanceof ItemEntity item && this.touched.contains(item.blockPosition()))
                this.drops.add(item);
        }

        @SubscribeEvent
        public void pre(AlchemyCraftEvent.Pre event) {
            if (this.cancel) event.setCanceled(true);
            if (this.mutate) for (AlchemyCraftEvent.InputCopy copy : event.inputs()) copy.stack().setCount(99);
            if (this.reenter && this.primary != null && event.pos().equals(this.primary.getBlockPos())) {
                this.reenter = false;
                AlchemyWorkstationService.start(this.player, this.other);
                this.reentered = AlchemyWorkstationService.start(this.player, this.primary);
            }
            if (this.move && this.primary != null && event.pos().equals(this.primary.getBlockPos())) {
                ItemStack taken = this.primary.container().removeItem(0, this.primary.container().getItem(0).getCount());
                if (!taken.isEmpty()) this.player.getInventory().add(taken);
            }
            if (this.removeHeat && this.primary != null && event.pos().equals(this.primary.getBlockPos()))
                this.level.setBlockAndUpdate(this.primary.heatSourcePos(), Blocks.AIR.defaultBlockState());
        }

        @SubscribeEvent
        public void post(AlchemyCraftEvent.Post event) {
            this.posts++;
            this.postOperator = event.operator();
            if (this.restart && this.primary != null && event.pos().equals(this.primary.getBlockPos())) {
                this.restart = false;
                this.restarted = true;
                this.primary.container().setItem(0, new ItemStack(Items.ALLIUM, 2));
                this.primary.container().setItem(2, new ItemStack(Items.CORNFLOWER, 2));
                this.primary.container().setItem(4, new ItemStack(Items.OXEYE_DAISY));
                this.restartResult = AlchemyWorkstationService.start(this.player, this.primary);
            }
        }
    }

    private static final class PostWatch {
        private final BlockPos pos;
        private int posts;
        private boolean failure;

        private PostWatch(BlockPos pos) {
            this.pos = pos;
        }

        @SubscribeEvent
        public void post(AlchemyCraftEvent.Post event) {
            if (!this.pos.equals(event.pos())) return;
            this.posts++;
            this.failure = !event.success() && event.reason().orElse(null) == AlchemyFailure.TEMPERATURE;
        }
    }
}
