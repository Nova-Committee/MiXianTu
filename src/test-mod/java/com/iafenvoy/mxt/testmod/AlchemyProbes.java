package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.data.alchemy.AlchemyFurnaceDefinition;
import com.google.gson.JsonElement;
import com.iafenvoy.mxt.data.item.PillComponent;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.serialization.JsonOps;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.event.AlchemyCraftEvent;
import com.iafenvoy.mxt.item.block.AlchemyFurnaceBlock;
import com.iafenvoy.mxt.item.block.AlchemyFurnaceCasingBlock;
import com.iafenvoy.mxt.item.block.AlchemyFurnaceInventoryBlock;
import com.iafenvoy.mxt.item.block.entity.AlchemyFurnaceBlockEntity;
import com.iafenvoy.mxt.item.block.entity.AlchemyFurnaceCasingBlockEntity;
import com.iafenvoy.mxt.item.block.entity.AlchemyFurnaceInventoryBlockEntity;
import com.iafenvoy.mxt.network.payload.AlchemyActionC2SPayload;
import com.iafenvoy.mxt.network.payload.AlchemyActionC2SPayload.Action;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyFailure;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyFurnaceStructure;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyPhase;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyResolver;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyResolver.Candidate;
import com.iafenvoy.mxt.runtime.alchemy.AlchemySession;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService.AlchemyPreview;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService.StartResult;
import com.iafenvoy.mxt.runtime.spirit.SpiritBurstEntity;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceMenu;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
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
import net.minecraft.advancements.AdvancementHolder;
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
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.*;
import java.util.function.Predicate;

/**
 * Real furnace probes. Each line is OK or MISMATCH; placed blocks and listeners are restored in finally.
 */
public final class AlchemyProbes {
    private static final String NS = "mxt_test";
    private static final Identifier WIDE = id("alchemy/wide");
    private static final Identifier SMALL = id("alchemy/small");
    private static final Identifier DUAL = id("alchemy/dual");
    private static final Identifier SEALED = id("alchemy/sealed");
    private static final Identifier PRACTICE = id("alchemy/practice");
    private static final Identifier HOT = id("alchemy/hot");
    private static final Identifier HOT_LOW = id("alchemy/hot_low");
    private static final Identifier LONG_BAD = id("alchemy/long_bad");
    private static final Identifier RANK_LOW = id("alchemy/rank_low");
    private static final Identifier RANK_HIGH = id("alchemy/rank_high");
    private static final Identifier KILN_WALL = id("alchemy/kiln");
    private static final Identifier BRITTLE_WALL = id("alchemy/brittle");
    private static final Identifier QI = id("qi");
    private static final Identifier MAIN = id("herb/main");
    private static final Set<ItemEntity> PROBE_DROPS = new HashSet<>();
    private static boolean revokeBrew;
    private static AdvancementHolder brewHolder;
    private static int mismatches;
    private static int checks;

    private AlchemyProbes() {
    }

    public static int run(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("alchemy probe: MISMATCH no player"));
            return 0;
        }
        ServerLevel level = player.level();
        mismatches = 0;
        checks = 0;
        List<BlockPos> touched = new ArrayList<>();
        Gate gate = new Gate(level, touched);
        NeoForge.EVENT_BUS.register(gate);
        AbstractContainerMenu previousMenu = player.containerMenu;
        ItemStack carried = previousMenu.getCarried().copy();
        ItemStack[] savedItems = snapshot(player);
        int savedXp = player.totalExperience;
        Collection<ItemEntity> previousDrops = player.captureDrops();
        PROBE_DROPS.clear();
        revokeBrew = false;
        brewHolder = null;
        int failed = 0;
        try {
            player.captureDrops(new ArrayList<>());
            player.getInventory().clearContent();
            BlockPos cursor = player.blockPosition().offset(6, 1, 6);
            mixture(source, level, cursor, touched);
            cursor = cursor.offset(8, 0, 0);
            resolution(source, level, player, cursor, touched);
            cursor = cursor.offset(8, 0, 0);
            refusal(source, level, player, cursor, touched);
            cursor = cursor.offset(8, 0, 0);
            heatAndFailure(source, level, player, cursor, touched);
            cursor = cursor.offset(8, 0, 0);
            persistence(source, level, player, cursor, touched);
            cursor = cursor.offset(8, 0, 0);
            dropsAndStructure(source, level, player, cursor, touched);
            cursor = cursor.offset(10, 0, 0);
            actionsAndMenu(source, level, player, cursor, gate, touched);
            cursor = cursor.offset(0, 0, 12);
            orientations(source, level, cursor, touched);
            unloaded(source, level, player);
            conflicts(source, level, player, player.blockPosition().offset(6, 1, 20), touched);
            regressions(source, level, player, player.blockPosition().offset(6, 1, 28), gate, touched);
            oneRoll(source, level, player, player.blockPosition().offset(6, 1, 36), touched);
            outputFit(source, level, player, player.blockPosition().offset(6, 1, 44), touched);
            criterion(source, level, player, player.blockPosition().offset(6, 1, 52), touched);
            limitsAndParts(source, level, player, player.blockPosition().offset(6, 1, 60), touched);
            callbackFailures(source, level, player, player.blockPosition().offset(6, 1, 68), gate, touched);
            menuOwnership(source, level, player, player.blockPosition().offset(6, 1, 76), touched);
        } catch (PlaceRefused refused) {
            failed++;
            checks++;
            source.sendFailure(Component.literal("alchemy probe: MISMATCH place-refused " + refused.getMessage()));
        } catch (RuntimeException exception) {
            failed++;
            checks++;
            source.sendFailure(Component.literal("alchemy probe: MISMATCH exception " + exception.getClass().getSimpleName() + " " + exception.getMessage()));
        } finally {
            try {
                for (BlockPos pos : touched) erase(level, pos);
                discardProbeDrops();
            } finally {
                NeoForge.EVENT_BUS.unregister(gate);
                player.captureDrops(previousDrops);
                player.containerMenu = previousMenu;
                previousMenu.setCarried(carried);
                restore(player, savedItems);
                if (player.totalExperience != savedXp) player.giveExperiencePoints(savedXp - player.totalExperience);
                if (revokeBrew && brewHolder != null) player.getAdvancements().revoke(brewHolder, "brew");
            }
        }
        Component summary = Component.literal("alchemy probe: " + (mismatches == 0 && failed == 0 ? "OK" : "MISMATCH") + " cases=" + checks);
        if (mismatches == 0 && failed == 0) source.sendSuccess(() -> summary, false);
        else source.sendFailure(summary);
        return mismatches == 0 && failed == 0 ? 1 : 0;
    }

    private static void mixture(CommandSourceStack source, ServerLevel level, BlockPos at, List<BlockPos> touched) {
        AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        furnace.refreshStructure();
        check(source, "structure", furnace.structureStatus().complete(), true);
        furnace.setTargetTemperature(50);
        furnace.container().setItem(0, new ItemStack(Items.ALLIUM, 2));
        furnace.container().setItem(2, new ItemStack(Items.CORNFLOWER, 2));
        furnace.container().setItem(4, new ItemStack(Items.OXEYE_DAISY));
        AlchemyPreview together = AlchemyWorkstationService.preview(playerOf(source), furnace);
        furnace.container().setItem(0, new ItemStack(Items.ALLIUM));
        furnace.container().setItem(1, new ItemStack(Items.ALLIUM));
        AlchemyPreview split = AlchemyWorkstationService.preview(playerOf(source), furnace);
        check(source, "split-main", split.mixture().main().getOrDefault(MAIN, -1.0D), together.mixture().main().getOrDefault(MAIN, -2.0D));
        check(source, "split-same-recipe", resolvedId(split), resolvedId(together));
        furnace.container().setItem(0, ItemStack.EMPTY);
        furnace.container().setItem(1, ItemStack.EMPTY);
        furnace.container().setItem(2, new ItemStack(Items.ALLIUM, 2));
        AlchemyPreview asAux = AlchemyWorkstationService.preview(playerOf(source), furnace);
        check(source, "aux-role", asAux.mixture().main().getOrDefault(MAIN, 0.0D), 0.0D);
        furnace.container().setItem(0, new ItemStack(Items.AZURE_BLUET));
        furnace.container().setItem(1, ItemStack.EMPTY);
        furnace.container().setItem(2, new ItemStack(Items.CORNFLOWER, 2));
        AlchemyPreview single = AlchemyWorkstationService.preview(playerOf(source), furnace);
        check(source, "substitute-main", single.mixture().main().getOrDefault(MAIN, -1.0D), 6.0D);
        check(source, "substitute-balance", single.mixture().balance(), 0.0D);
        check(source, "substitute-resolved", resolvedId(single), PRACTICE);
        ItemStack aged = new ItemStack(Items.ALLIUM);
        aged.set(MxtDataComponents.HERB_AGE.get(), 100);
        furnace.container().setItem(0, aged);
        AlchemyPreview century = AlchemyWorkstationService.preview(playerOf(source), furnace);
        check(source, "century-main", century.mixture().main().getOrDefault(MAIN, -1.0D), 6.0D);
        check(source, "century-resolved", resolvedId(century), PRACTICE);
        check(source, "century-only", matchedCount(century), 1);
        check(source, "century-edible", ediblePractice(level.registryAccess(), century.successOutputs()), true);
        furnace.container().setItem(1, new ItemStack(Items.DANDELION));
        AlchemyPreview conflict = AlchemyWorkstationService.preview(playerOf(source), furnace);
        check(source, "conflict", conflict.blocker().orElse(null), AlchemyFailure.CONFLICT);
        int before = furnace.container().getItem(1).getCount();
        StartResult refused = AlchemyWorkstationService.start(playerOf(source), furnace);
        check(source, "conflict-no-spend", refused.started(), false);
        check(source, "conflict-kept", furnace.container().getItem(1).getCount(), before);
        furnace.container().setItem(1, ItemStack.EMPTY);
        check(source, "practice-start", AlchemyWorkstationService.start(playerOf(source), furnace).started(), true);
        tickUntil(level, furnace, 220, phase -> phase == AlchemyPhase.IDLE);
        check(source, "practice-pills", ediblePractice(level.registryAccess(), List.of(stored(furnace, MxtItems.PILL.get()))), true);
    }

    private static void resolution(CommandSourceStack source, ServerLevel level, ServerPlayer player, BlockPos at, List<BlockPos> touched) {
        AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        furnace.refreshStructure();
        furnace.setTargetTemperature(50);
        furnace.container().setItem(0, new ItemStack(Items.CACTUS));
        furnace.container().setItem(4, new ItemStack(Items.OXEYE_DAISY));
        AlchemyPreview low = AlchemyWorkstationService.preview(player, furnace);
        check(source, "rank-low-resolved", resolvedId(low), RANK_LOW);
        check(source, "rank-low-only", matchedCount(low), 1);
        furnace.container().setItem(0, new ItemStack(Items.CACTUS, 2));
        AlchemyPreview high = AlchemyWorkstationService.preview(player, furnace);
        check(source, "rank-high-resolved", resolvedId(high), RANK_HIGH);
        check(source, "rank-high-also-low", matched(high, RANK_LOW), true);
        Candidate reversed = AlchemyResolver.dominating(high.candidates().reversed());
        check(source, "rank-candidate-order", reversed == null ? null : reversed.id().identifier(), RANK_HIGH);
        furnace.container().setItem(0, new ItemStack(Items.CACTUS));
        furnace.container().setItem(1, new ItemStack(Items.CACTUS));
        AlchemyPreview splitRank = AlchemyWorkstationService.preview(player, furnace);
        check(source, "rank-split", resolvedId(splitRank), resolvedId(high));
        check(source, "rank-start", AlchemyWorkstationService.start(player, furnace).started(), true);
        tickUntil(level, furnace, 12, phase -> phase == AlchemyPhase.IDLE);
        check(source, "rank-output", stored(furnace, Items.EMERALD).getCount(), 1);
        check(source, "rank-not-low-output", stored(furnace, Items.STICK).isEmpty(), true);
        clearInputs(furnace);
        fill(furnace);
        furnace.container().setItem(1, new ItemStack(Items.WITHER_ROSE));
        AlchemyPreview tie = AlchemyWorkstationService.preview(player, furnace);
        check(source, "tie-ambiguous", tie.blocker().orElse(null), AlchemyFailure.AMBIGUOUS);
        check(source, "tie-unresolved", tie.resolvedRecipe().isEmpty(), true);
        int rose = furnace.container().getItem(1).getCount();
        StartResult tied = AlchemyWorkstationService.start(player, furnace);
        check(source, "tie-no-spend", tied.started(), false);
        check(source, "tie-kept", furnace.container().getItem(1).getCount(), rose);
        clearInputs(furnace);
        fill(furnace);
        furnace.container().setItem(1, new ItemStack(Items.BAMBOO));
        AlchemyPreview crossed = AlchemyWorkstationService.preview(player, furnace);
        check(source, "cross-ambiguous", crossed.blocker().orElse(null), AlchemyFailure.AMBIGUOUS);
        check(source, "cross-unresolved", crossed.resolvedRecipe().isEmpty(), true);
        int bamboo = furnace.container().getItem(1).getCount();
        StartResult cross = AlchemyWorkstationService.start(player, furnace);
        check(source, "cross-no-spend", cross.started(), false);
        check(source, "cross-kept", furnace.container().getItem(1).getCount(), bamboo);
        clearInputs(furnace);
        furnace.container().setItem(0, new ItemStack(Items.ALLIUM));
        furnace.container().setItem(2, new ItemStack(Items.CORNFLOWER));
        furnace.container().setItem(4, new ItemStack(Items.OXEYE_DAISY));
        AlchemyPreview shortfall = AlchemyWorkstationService.preview(player, furnace);
        check(source, "shortfall", shortfall.blocker().orElse(null), AlchemyFailure.INSUFFICIENT);
    }

    private static void refusal(CommandSourceStack source, ServerLevel level, ServerPlayer player, BlockPos at, List<BlockPos> touched) {
        AlchemyFurnaceBlockEntity small = place(level, at, Direction.NORTH, item(level, SMALL), touched);
        small.refreshStructure();
        small.setTargetTemperature(50);
        small.container().setItem(1, new ItemStack(Items.ALLIUM));
        StartResult slots = AlchemyWorkstationService.start(player, small);
        check(source, "disabled-slot", slots.failure(), AlchemyFailure.SLOTS);
        check(source, "disabled-slot-kept", small.container().getItem(1).getCount(), 1);
        small.container().setItem(1, ItemStack.EMPTY);
        small.container().setItem(0, new ItemStack(Items.ALLIUM, 5));
        StartResult capacity = AlchemyWorkstationService.start(player, small);
        check(source, "capacity", capacity.failure(), AlchemyFailure.CAPACITY);
        check(source, "capacity-kept", small.container().getItem(0).getCount(), 5);
        clearFurnace(level, at, touched);
        AlchemyFurnaceBlockEntity sealed = place(level, at, Direction.NORTH, item(level, SEALED), touched);
        sealed.refreshStructure();
        sealed.setTargetTemperature(50);
        fill(sealed);
        sealed.fireContainer().setItem(0, new ItemStack(AlchemyTestFireItems.FIRE.get()));
        StartResult quality = AlchemyWorkstationService.start(player, sealed);
        check(source, "sealed-quality", quality.failure(), AlchemyFailure.QUALITY_CONDITIONS);
        check(source, "sealed-kept", sealed.container().getItem(0).getCount(), 2);
        check(source, "sealed-fire", fireCount(sealed), 1);
        clearFurnace(level, at, touched);
        AlchemyFurnaceBlockEntity wide = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        wide.refreshStructure();
        wide.setTargetTemperature(50);
        fillMarked(wide, Items.POPPY);
        AlchemyPreview hotPreview = AlchemyWorkstationService.preview(player, wide);
        check(source, "hot-resolved", resolvedId(hotPreview), HOT);
        check(source, "hot-dominates-low", matched(hotPreview, HOT_LOW), true);
        check(source, "hot-not-practice", matched(hotPreview, PRACTICE), false);
        StartResult hot = AlchemyWorkstationService.start(player, wide);
        check(source, "hot-temperature", hot.failure(), AlchemyFailure.TEMPERATURE);
        check(source, "hot-kept", wide.container().getItem(0).getCount(), 2);
        check(source, "hot-marker-kept", wide.container().getItem(1).getCount(), 1);
        BlockPos missing = AlchemyFurnaceStructure.world(at, Direction.NORTH, 16);
        level.setBlockAndUpdate(missing, Blocks.AIR.defaultBlockState());
        wide.refreshStructure();
        StartResult broken = AlchemyWorkstationService.start(player, wide);
        check(source, "missing-shell", broken.failure(), AlchemyFailure.STRUCTURE);
        check(source, "missing-kept", wide.container().getItem(0).getCount(), 2);
    }

    private static void heatAndFailure(CommandSourceStack source, ServerLevel level, ServerPlayer player, BlockPos at, List<BlockPos> touched) {
        AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        furnace.refreshStructure();
        furnace.setTargetTemperature(50);
        fillMarked(furnace, Items.ORANGE_TULIP);
        furnace.fireContainer().setItem(0, new ItemStack(AlchemyTestFireItems.FIRE.get()));
        StartResult started = AlchemyWorkstationService.start(player, furnace);
        check(source, "full-start", started.started(), true);
        for (int slot = 5; slot < 9; slot++) furnace.container().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        tickUntil(level, furnace, 12, phase -> phase == AlchemyPhase.READY || phase == AlchemyPhase.IDLE);
        check(source, "full-ready", furnace.phase(), AlchemyPhase.READY);
        int pending = furnace.state().session().map(session -> session.pendingOutputs().stream().mapToInt(ItemStack::getCount).sum()).orElse(0);
        check(source, "full-waiting", pending, 2);
        check(source, "full-not-inserted", furnace.container().getItem(5).is(Items.COBBLESTONE), true);
        double hot = furnace.temperature();
        int fire = fireCount(furnace);
        tick(level, furnace);
        tick(level, furnace);
        check(source, "ready-fire-kept-and-cools", fireCount(furnace) == fire && furnace.temperature() < hot, true);
        check(source, "full-no-dup", furnace.state().session().map(session -> session.pendingOutputs().stream().mapToInt(ItemStack::getCount).sum()).orElse(-1), pending);
        clearFurnace(level, at, touched);
        AlchemyFurnaceBlockEntity dual = place(level, at, Direction.NORTH, item(level, DUAL), touched);
        dual.refreshStructure();
        dual.setTargetTemperature(50);
        fill(dual);
        dual.fireContainer().setItem(0, new ItemStack(AlchemyTestFireItems.FIRE.get()));
        StartResult dualStart = AlchemyWorkstationService.start(player, dual);
        check(source, "alternate-quality-start", dualStart.started(), true);
        tick(level, dual);
        check(source, "running-fire-not-consumed", fireCount(dual), 1);
        clearFurnace(level, at, touched);
        AlchemyFurnaceBlockEntity burstFurnace = place(level, at, Direction.NORTH, item(level, SMALL), touched);
        burstFurnace.refreshStructure();
        burstFurnace.fireContainer().setItem(0, new ItemStack(AlchemyTestFireItems.FIRE.get()));
        int before = fireCount(burstFurnace);
        double cold = burstFurnace.temperature();
        shoot(level, player, at.above(), aura(level, QI), 3);
        check(source, "burst-no-fire", fireCount(burstFurnace), before);
        check(source, "burst-no-heat", burstFurnace.temperature(), cold);
        clearFurnace(level, at, touched);
        AlchemyFurnaceBlockEntity bad = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        bad.refreshStructure();
        bad.setTargetTemperature(50);
        fill(bad);
        bad.fireContainer().setItem(0, new ItemStack(AlchemyTestFireItems.FIRE.get()));
        check(source, "bad-start", AlchemyWorkstationService.start(player, bad).started(), true);
        tickUntil(level, bad, 6, phase -> phase == AlchemyPhase.RUNNING);
        check(source, "bad-running", bad.phase(), AlchemyPhase.RUNNING);
        bad.setTargetTemperature(0);
        tick(level, bad);
        tick(level, bad);
        check(source, "bad-tolerated", bad.state().session().map(AlchemySession::badTicks).orElse(-1), 2);
        check(source, "bad-still-running", bad.phase(), AlchemyPhase.RUNNING);
        PostWatch watch = new PostWatch(bad.getBlockPos());
        NeoForge.EVENT_BUS.register(watch);
        tick(level, bad);
        check(source, "bad-third-fails", watch.posts == 1 && watch.failure, true);
        bad.setTemperature(50);
        bad.setTargetTemperature(50);
        tick(level, bad);
        NeoForge.EVENT_BUS.unregister(watch);
        check(source, "bad-no-recovery", watch.posts == 1 && charcoalCount(bad) == 1 && !hasOutput(bad, Items.PAPER), true);
        check(source, "bad-charcoal-once", charcoalCount(bad), 1);
    }

    private static void persistence(CommandSourceStack source, ServerLevel level, ServerPlayer player, BlockPos at, List<BlockPos> touched) {
        AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, overrideItem(level), touched);
        furnace.refreshStructure();
        furnace.setTargetTemperature(50);
        fillMarked(furnace, Items.RED_TULIP);
        furnace.fireContainer().setItem(0, new ItemStack(AlchemyTestFireItems.FIRE.get()));
        check(source, "save-start", AlchemyWorkstationService.start(player, furnace).started(), true);
        AlchemyFurnaceBlockEntity warming = reload(level, furnace);
        check(source, "warming-phase", warming.phase(), AlchemyPhase.WARMING);
        check(source, "warming-recipe", warming.state().session().map(session -> session.recipe().successOutputs().isEmpty()).orElse(true), false);
        tickUntil(level, furnace, 6, phase -> phase == AlchemyPhase.RUNNING);
        int fire = fireCount(furnace);
        AlchemyFurnaceBlockEntity running = reload(level, furnace);
        check(source, "running-phase", running.phase(), AlchemyPhase.RUNNING);
        check(source, "running-fire", fireCount(running), fire);
        check(source, "running-frozen", running.state().session().map(session -> session.recipeId().equals(LONG_BAD)).orElse(false), true);
        for (int slot = 5; slot < 9; slot++) furnace.container().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        tickUntil(level, furnace, 90, phase -> phase == AlchemyPhase.READY);
        AlchemyFurnaceBlockEntity ready = reload(level, furnace);
        check(source, "ready-phase", ready.phase(), AlchemyPhase.READY);
        check(source, "ready-pending", ready.state().session().map(session -> !session.pendingOutputs().isEmpty()).orElse(false), true);
        check(source, "frozen-output", ready.state().session().map(session -> session.recipe().failureOutputs().getFirst().item().value()).orElse(Items.AIR), Items.CHARCOAL);
    }

    private static void dropsAndStructure(CommandSourceStack source, ServerLevel level, ServerPlayer player, BlockPos at, List<BlockPos> touched) {
        ItemStack marked = overrideItem(level);
        AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, marked, touched);
        furnace.refreshStructure();
        furnace.setTargetTemperature(50);
        fill(furnace);
        AlchemyWorkstationService.start(player, furnace);
        AlchemyFurnaceBlockEntity saved = reload(level, furnace);
        check(source, "override-saved-name", saved.furnaceItem().get(DataComponents.CUSTOM_NAME) != null, true);
        check(source, "override-saved-quality", saved.furnaceItem().get(MxtDataComponents.QUALITY.get()) != null, true);
        BlockPos casing = AlchemyFurnaceStructure.world(at, Direction.NORTH, 0);
        level.setBlockAndUpdate(casing, Blocks.AIR.defaultBlockState());
        check(source, "casing-abort", furnace.state().session().map(AlchemySession::failed).orElse(false), true);
        check(source, "casing-no-refund", furnace.container().getItem(0).isEmpty(), true);
        int charcoal = charcoalCount(furnace);
        level.setBlockAndUpdate(AlchemyFurnaceStructure.world(at, Direction.NORTH, 2), Blocks.AIR.defaultBlockState());
        check(source, "casing-abort-once", charcoalCount(furnace), charcoal);
        clearDrops(level, at);
        level.destroyBlock(at, true, player);
        List<ItemEntity> drops = drops(level, at);
        check(source, "controller-one-furnace", count(drops, MxtBlocks.ALCHEMY_FURNACE.get().asItem()), 1);
        check(source, "controller-no-ingredient", count(drops, Items.ALLIUM), 0);
        ItemStack dropped = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(MxtBlocks.ALCHEMY_FURNACE.get().asItem())).findFirst().orElse(ItemStack.EMPTY);
        check(source, "override-quality", dropped.get(MxtDataComponents.QUALITY.get()) != null && HolderHelperEquals(dropped, id("alchemy/kiln_b")), true);
        check(source, "override-name", dropped.get(DataComponents.CUSTOM_NAME) != null, true);
        check(source, "override-spec", dropped.get(MxtDataComponents.ALCHEMY_FURNACE.get()) != null, true);
    }

    private static void actionsAndMenu(CommandSourceStack source, ServerLevel level, ServerPlayer player, BlockPos at, Gate gate, List<BlockPos> touched) {
        gate.cancel = true;
        AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        furnace.refreshStructure();
        furnace.setTargetTemperature(50);
        fill(furnace);
        furnace.fireContainer().setItem(0, new ItemStack(AlchemyTestFireItems.FIRE.get()));
        StartResult cancelled = AlchemyWorkstationService.start(player, furnace);
        check(source, "pre-cancel", cancelled.failure(), AlchemyFailure.CANCELLED);
        check(source, "pre-cancel-items", furnace.container().getItem(0).getCount(), 2);
        check(source, "pre-cancel-fire", fireCount(furnace), 1);
        gate.cancel = false;
        gate.mutate = true;
        fillMarked(furnace, Items.SUNFLOWER);
        StartResult mutated = AlchemyWorkstationService.start(player, furnace);
        check(source, "pre-copy-starts", mutated.started(), true);
        check(source, "pre-copy-consumed", furnace.container().getItem(0).isEmpty(), true);
        gate.mutate = false;
        gate.posts = 0;
        FakePlayer viewer = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "alchemy_viewer"));
        int viewerShells = count(viewer, Items.NAUTILUS_SHELL);
        tickUntil(level, furnace, 8, phase -> phase == AlchemyPhase.IDLE || level.getBlockState(at).is(Blocks.GOLD_BLOCK));
        check(source, "post-once", gate.posts, 1);
        check(source, "post-operator", gate.postOperator, player.getUUID());
        check(source, "operator-shell", count(player, Items.NAUTILUS_SHELL) >= 1, true);
        check(source, "viewer-no-shell", count(viewer, Items.NAUTILUS_SHELL), viewerShells);
        check(source, "block-action-once", level.getBlockState(at).is(Blocks.GOLD_BLOCK), true);
        tick(level, furnace);
        check(source, "post-not-repeated", gate.posts, 1);
        clearFurnace(level, at, touched);
        AlchemyFurnaceBlockEntity menuFurnace = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        menuFurnace.refreshStructure();
        menuFurnace.setTargetTemperature(50);
        fill(menuFurnace);
        AlchemyFurnaceMenu menu = new AlchemyFurnaceMenu(7, player.getInventory(), at, AlchemyFurnaceMenu.View.MONITOR);
        player.containerMenu = menu;
        menu.handleAction(player, new AlchemyActionC2SPayload(99, Action.START, 50));
        check(source, "wrong-container", menuFurnace.phase(), AlchemyPhase.IDLE);
        check(source, "wrong-container-items", menuFurnace.container().getItem(0).getCount(), 2);
        player.containerMenu = player.inventoryMenu;
        menu.handleAction(player, new AlchemyActionC2SPayload(7, Action.START, 50));
        check(source, "stale-menu", menuFurnace.phase(), AlchemyPhase.IDLE);
    }

    private static void orientations(CommandSourceStack source, ServerLevel level, BlockPos at, List<BlockPos> touched) {
        int index = 0;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos controller = at.offset(index++ * 6, 0, 0);
            AlchemyFurnaceBlockEntity furnace = place(level, controller, facing, item(level, WIDE), touched);
            furnace.refreshStructure();
            BlockPos shell = AlchemyFurnaceStructure.world(controller, facing, 0);
            BlockState state = level.getBlockState(shell);
            check(source, "facing-" + facing.getName(), furnace.structureStatus().complete()
                    && state.getValue(AlchemyFurnaceCasingBlock.PART) == 1
                    && state.getValue(AlchemyFurnaceCasingBlock.FACING) == facing, true);
        }
    }

    private static void unloaded(CommandSourceStack source, ServerLevel level, ServerPlayer player) {
        BlockPos controller = new BlockPos((player.getBlockX() >> 4) * 16 + 15 + 8000, Math.max(8, player.getBlockY()), (player.getBlockZ() >> 4) * 16 + 8);
        Set<BlockPos> before = new HashSet<>();
        for (int cell = 0; cell < 27; cell++) {
            BlockPos pos = AlchemyFurnaceStructure.world(controller, Direction.NORTH, cell);
            if (!level.isLoaded(pos)) before.add(pos);
        }
        if (before.isEmpty()) {
            check(source, "unloaded-setup", false, true);
            return;
        }
        AlchemyFurnaceStructure.Status status = AlchemyFurnaceStructure.inspect(level, controller, Direction.NORTH);
        check(source, "unloaded-listed", new HashSet<>(status.unloaded()).equals(before), true);
        check(source, "unloaded-incomplete", status.complete(), false);
        check(source, "unloaded-not-forced", before.stream().anyMatch(level::isLoaded), false);
    }

    private static void outputFit(CommandSourceStack source, ServerLevel level, ServerPlayer player, BlockPos at, List<BlockPos> touched) {
        AlchemyFurnaceBlockEntity split = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        split.refreshStructure();
        split.setTargetTemperature(50);
        fillMarked(split, Items.PEONY);
        split.fireContainer().setItem(0, new ItemStack(AlchemyTestFireItems.FIRE.get()));
        split.container().setItem(5, ItemStack.EMPTY);
        split.container().setItem(6, new ItemStack(Items.APPLE, 63));
        split.container().setItem(7, new ItemStack(Items.STONE, 64));
        split.container().setItem(8, new ItemStack(Items.STONE, 64));
        check(source, "split-start", AlchemyWorkstationService.start(player, split).started(), true);
        tickUntil(level, split, 16, phase -> phase == AlchemyPhase.IDLE);
        check(source, "split-apple", split.container().getItem(6).getCount(), 64);
        check(source, "split-diamond", split.container().getItem(5).is(Items.DIAMOND) && split.container().getItem(5).getCount() == 1, true);
        clearFurnace(level, at, touched);
        AlchemyFurnaceBlockEntity pack = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        pack.refreshStructure();
        pack.setTargetTemperature(50);
        fillMarked(pack, Items.ROSE_BUSH);
        pack.fireContainer().setItem(0, new ItemStack(AlchemyTestFireItems.FIRE.get()));
        pack.container().setItem(5, new ItemStack(Items.APPLE, 60));
        pack.container().setItem(6, new ItemStack(Items.APPLE, 63));
        pack.container().setItem(7, new ItemStack(Items.STONE, 64));
        pack.container().setItem(8, new ItemStack(Items.STONE, 64));
        check(source, "pack-start", AlchemyWorkstationService.start(player, pack).started(), true);
        tickUntil(level, pack, 16, phase -> phase == AlchemyPhase.IDLE);
        check(source, "pack-slot5", pack.container().getItem(5).getCount(), 64);
        check(source, "pack-slot6", pack.container().getItem(6).getCount(), 64);
        check(source, "pack-still-apple", pack.container().getItem(5).is(Items.APPLE) && pack.container().getItem(6).is(Items.APPLE), true);
        clearFurnace(level, at, touched);
        AlchemyFurnaceBlockEntity merged = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        merged.refreshStructure();
        merged.setTargetTemperature(50);
        fillMarked(merged, Items.ROSE_BUSH);
        merged.container().setItem(5, new ItemStack(Items.APPLE, 59));
        merged.container().setItem(6, new ItemStack(Items.STONE, 64));
        merged.container().setItem(7, new ItemStack(Items.STONE, 64));
        merged.container().setItem(8, new ItemStack(Items.STONE, 64));
        check(source, "merge-start", AlchemyWorkstationService.start(player, merged).started(), true);
        tickUntil(level, merged, 16, phase -> phase == AlchemyPhase.IDLE);
        check(source, "merge-room", merged.container().getItem(5).getCount(), 64);
        check(source, "merge-other", merged.container().getItem(6).is(Items.STONE) && merged.container().getItem(6).getCount() == 64, true);
    }

    private static void oneRoll(CommandSourceStack source, ServerLevel level, ServerPlayer player, BlockPos at, List<BlockPos> touched) {
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
        check(source, "roll-started", started.started(), true);
        check(source, "roll-one-evaluation", frozen, firstTicks);
        check(source, "roll-not-second", frozen == secondTicks && firstTicks != secondTicks, false);
        check(source, "roll-target", furnace.state().session().map(AlchemySession::frozenTarget).orElse(-1.0D), 50.0D);
    }

    private static void conflicts(CommandSourceStack source, ServerLevel level, ServerPlayer player, BlockPos at, List<BlockPos> touched) {
        AlchemyFurnaceBlockEntity first = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        first.refreshStructure();
        AlchemyFurnaceBlockEntity second = placeOverlap(level, at.offset(2, 0, 0), Direction.NORTH, item(level, WIDE), touched);
        second.refreshStructure();
        second.setTargetTemperature(50);
        check(source, "claim-conflict", second.structureStatus().conflicts().isEmpty(), false);
        fill(second);
        StartResult refused = AlchemyWorkstationService.start(player, second);
        check(source, "conflict-refuse", refused.failure(), AlchemyFailure.STRUCTURE);
        check(source, "conflict-no-spend", second.container().getItem(0).getCount(), 2);
        check(source, "conflict-owner-formed", level.getBlockState(at).getValue(AlchemyFurnaceBlock.FORMED), true);
    }

    private static void regressions(CommandSourceStack source, ServerLevel level, ServerPlayer player, BlockPos at, Gate gate, List<BlockPos> touched) {
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
        check(source, "reenter-rejected", gate.reentered != null && !gate.reentered.started() && gate.reentered.failure() == AlchemyFailure.ACTIVE, true);
        check(source, "reenter-one-session", primary.state().session().isPresent(), true);
        gate.reenter = false;
        clearFurnace(level, at, touched);
        clearFurnace(level, at.offset(0, 0, 8), touched);
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
        check(source, "move-rejected", move.started(), false);
        check(source, "move-no-dup", count(player, Items.ALLIUM) + moved.container().getItem(0).getCount(), held + 2);
        clearFurnace(level, at, touched);
        AlchemyFurnaceBlockEntity restart = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        restart.refreshStructure();
        restart.setTargetTemperature(50);
        fill(restart);
        gate.restart = true;
        gate.primary = restart;
        gate.player = player;
        check(source, "restart-start", AlchemyWorkstationService.start(player, restart).started(), true);
        tickUntil(level, restart, 8, phase -> phase == AlchemyPhase.IDLE || restart.state().session().isEmpty() || restart.phase() == AlchemyPhase.WARMING && gate.restarted);
        gate.restart = false;
        check(source, "post-restart-kept", restart.state().session().isPresent() && restart.phase() != AlchemyPhase.IDLE, true);
        clearFurnace(level, at, touched);
        AlchemyFurnaceBlockEntity aborted = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        aborted.refreshStructure();
        aborted.setTargetTemperature(50);
        fill(aborted);
        AlchemyWorkstationService.start(player, aborted);
        AlchemyWorkstationService.abort(level, aborted.getBlockPos(), aborted);
        check(source, "abort-cause", aborted.state().session().flatMap(AlchemySession::failure).orElse(null), AlchemyFailure.CANCELLED);
        check(source, "abort-failure-output", aborted.state().session().map(session -> session.pendingOutputs().stream().anyMatch(stack -> stack.is(Items.CHARCOAL))).orElse(false), true);
        check(source, "abort-not-success", aborted.state().session().map(session -> session.pendingOutputs().stream().anyMatch(stack -> stack.is(Items.PAPER))).orElse(false), false);
        BlockPos shell = AlchemyFurnaceStructure.world(at, Direction.NORTH, 0);
        clearFurnace(level, at, touched);
        AlchemyFurnaceBlockEntity formed = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        formed.refreshStructure();
        formed.setTargetTemperature(50);
        fill(formed);
        AlchemyWorkstationService.start(player, formed);
        level.setBlockAndUpdate(shell, Blocks.AIR.defaultBlockState());
        check(source, "structure-cause", formed.state().session().flatMap(AlchemySession::failure).orElse(null), AlchemyFailure.STRUCTURE);
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
        check(source, "release-keeps-owner", level.getBlockState(owned).getValue(AlchemyFurnaceCasingBlock.PART), ownedPart);
        check(source, "release-owner-formed", level.getBlockState(ownerPos).getValue(AlchemyFurnaceBlock.FORMED), true);
        clearFurnace(level, ownerPos, touched);
        clearFurnace(level, at, touched);
        AlchemyFurnaceBlockEntity idle = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        idle.refreshStructure();
        LevelChunk chunk = level.getChunkAt(at);
        chunk.tryMarkSaved();
        tick(level, idle);
        check(source, "idle-not-dirtied", chunk.isUnsaved(), false);
    }

    private static void criterion(CommandSourceStack source, ServerLevel level, ServerPlayer player, BlockPos at, List<BlockPos> touched) {
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(id("alchemy/brew"));
        if (holder == null || player.getAdvancements().getOrStartProgress(holder).isDone()) {
            check(source, "advancement-setup", false, true);
            return;
        }
        revokeBrew = true;
        brewHolder = holder;
        int xp = player.totalExperience;
        int crystals = count(player, Items.PRISMARINE_CRYSTALS);
        FakePlayer viewer = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "AlchemyViewer"));
        int viewerXp = viewer.totalExperience;
        int viewerCrystals = count(viewer, Items.PRISMARINE_CRYSTALS);
        AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        furnace.refreshStructure();
        furnace.setTargetTemperature(50);
        fillMarked(furnace, Items.SPORE_BLOSSOM);
        furnace.fireContainer().setItem(0, new ItemStack(AlchemyTestFireItems.FIRE.get()));
        check(source, "criterion-start", AlchemyWorkstationService.start(player, furnace).started(), true);
        tickUntil(level, furnace, 16, phase -> phase == AlchemyPhase.IDLE);
        boolean earned = player.getAdvancements().getOrStartProgress(holder).isDone();
        check(source, "criterion-earned", earned, true);
        check(source, "criterion-xp", player.totalExperience, xp + 1);
        check(source, "criterion-action", count(player, Items.PRISMARINE_CRYSTALS), crystals + 1);
        check(source, "criterion-viewer-xp", viewer.totalExperience, viewerXp);
        check(source, "criterion-viewer-action", count(viewer, Items.PRISMARINE_CRYSTALS), viewerCrystals);
        int afterXp = player.totalExperience;
        int afterCrystals = count(player, Items.PRISMARINE_CRYSTALS);
        tick(level, furnace);
        check(source, "criterion-xp-once", player.totalExperience, afterXp);
        check(source, "criterion-action-once", count(player, Items.PRISMARINE_CRYSTALS), afterCrystals);
    }

    private static AlchemyFurnaceBlockEntity place(ServerLevel level, BlockPos controller, Direction facing, ItemStack furnaceItem, List<BlockPos> touched) {
        return place(level, controller, facing, furnaceItem, touched, KILN_WALL);
    }

    private static AlchemyFurnaceBlockEntity place(ServerLevel level, BlockPos controller, Direction facing, ItemStack furnaceItem, List<BlockPos> touched, Identifier wallId) {
        BlockPos[] cells = new BlockPos[27];
        for (int cell = 0; cell < 27; cell++) {
            BlockPos pos = AlchemyFurnaceStructure.world(controller, facing, cell).immutable();
            cells[cell] = pos;
            if (touched.contains(pos)) continue;
            if (!level.isLoaded(pos)) throw new PlaceRefused("unloaded " + pos);
            if (!level.getBlockState(pos).isAir()) throw new PlaceRefused("occupied " + pos);
        }
        ItemStack wall = wallItem(level, wallId);
        for (int cell = 0; cell < 27; cell++) {
            BlockPos pos = cells[cell];
            if (!touched.contains(pos)) touched.add(pos);
            level.setBlockAndUpdate(pos, shell(cell, facing));
            if (AlchemyFurnaceStructure.wall(cell) && level.getBlockEntity(pos) instanceof AlchemyFurnaceCasingBlockEntity casing)
                casing.acceptWallItem(wall.copy());
        }
        if (!(level.getBlockEntity(controller) instanceof AlchemyFurnaceBlockEntity furnace))
            throw new PlaceRefused("controller " + controller);
        furnace.acceptFurnaceItem(furnaceItem);
        furnace.fireContainer().setItem(0, new ItemStack(AlchemyTestFireItems.FIRE.get()));
        return furnace;
    }

    private static BlockState shell(int cell, Direction facing) {
        if (cell == AlchemyFurnaceStructure.HOLLOW_INDEX) return Blocks.AIR.defaultBlockState();
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
        stack.set(MxtDataComponents.QUALITY.get(), MxtDatapackRegistries.holder(level.registryAccess(), MxtResourceKeys.ITEM_QUALITY, id("alchemy/kiln_b")).orElseThrow());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Probe Kiln"));
        return stack;
    }

    private static Holder<AlchemyFurnaceDefinition> holder(ServerLevel level, Identifier id) {
        return MxtDatapackRegistries.holder(level.registryAccess(), MxtResourceKeys.ALCHEMY_FURNACE, id).orElseThrow();
    }

    private static Holder<Aura> aura(ServerLevel level, Identifier id) {
        return MxtDatapackRegistries.holder(level.registryAccess(), MxtResourceKeys.AURA, id).orElseThrow();
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
            if (!stack.is(MxtItems.PILL.get()) || stack.getCount() != 4 || !stack.has(DataComponents.CONSUMABLE)) continue;
            PillComponent pill = stack.get(MxtDataComponents.PILL.get());
            if (pill == null) return false;
            JsonElement encoded = PillComponent.CODEC.encodeStart(access.createSerializationContext(JsonOps.INSTANCE), pill).getOrThrow();
            return encoded.isJsonObject() && encoded.getAsJsonObject().has("pill")
                    && "mxt_test:toxicity".equals(encoded.getAsJsonObject().get("pill").getAsString());
        }
        return false;
    }

    private static ItemStack stored(AlchemyFurnaceBlockEntity furnace, Item item) {
        for (int slot = 5; slot < 9; slot++) if (furnace.container().getItem(slot).is(item)) return furnace.container().getItem(slot);
        return ItemStack.EMPTY;
    }

    private static void tick(ServerLevel level, AlchemyFurnaceBlockEntity furnace) {
        if (furnace.isRemoved() || !(level.getBlockEntity(furnace.getBlockPos()) instanceof AlchemyFurnaceBlockEntity live)) return;
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

    private static void clearFurnace(ServerLevel level, BlockPos controller, List<BlockPos> touched) {
        for (int cell = 0; cell < 27; cell++) {
            BlockPos pos = AlchemyFurnaceStructure.world(controller, Direction.NORTH, cell);
            if (touched.contains(pos)) {
                erase(level, pos);
                touched.remove(pos);
            }
        }
        clearDrops(level, controller);
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

    private static void discardProbeDrops() {
        for (ItemEntity entity : PROBE_DROPS) if (!entity.isRemoved()) entity.discard();
        PROBE_DROPS.clear();
    }

    private static ItemStack wallItem(ServerLevel level, Identifier id) {
        ItemStack stack = MxtBlocks.ALCHEMY_FURNACE_CASING.toStack();
        stack.set(MxtDataComponents.ALCHEMY_WALL_MATERIAL.get(),
                MxtDatapackRegistries.holder(level.registryAccess(), MxtResourceKeys.ALCHEMY_WALL_MATERIAL, id).orElseThrow());
        return stack;
    }

    private static int fireCount(AlchemyFurnaceBlockEntity furnace) {
        return furnace.fireContainer().getItem(0).getCount();
    }

    private static void limitsAndParts(CommandSourceStack source, ServerLevel level, ServerPlayer player, BlockPos at, List<BlockPos> touched) {
        AlchemyFurnaceBlockEntity brittle = place(level, at, Direction.NORTH, item(level, WIDE), touched, BRITTLE_WALL);
        brittle.refreshStructure();
        check(source, "brittle-limit", brittle.maximumTemperature(), 80.0D);
        check(source, "brittle-rejects-100", brittle.setTargetTemperature(100.0D), false);
        check(source, "brittle-accepts-80", brittle.setTargetTemperature(80.0D), true);
        fillMarked(brittle, Items.POPPY);
        check(source, "brittle-hot", AlchemyWorkstationService.start(player, brittle).failure(), AlchemyFailure.TEMPERATURE);
        check(source, "brittle-kept", brittle.container().getItem(0).getCount(), 2);
        clearFurnace(level, at, touched);
        AlchemyFurnaceBlockEntity weak = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        weak.refreshStructure();
        weak.fireContainer().setItem(0, new ItemStack(AlchemyTestFireItems.WEAK_FIRE.get()));
        check(source, "weak-fire-limit", weak.maximumTemperature(), 80.0D);
        check(source, "weak-fire-rejects-100", weak.setTargetTemperature(100.0D), false);
        weak.setTargetTemperature(80.0D);
        fillMarked(weak, Items.POPPY);
        check(source, "weak-fire-hot", AlchemyWorkstationService.start(player, weak).failure(), AlchemyFailure.TEMPERATURE);
        check(source, "weak-fire-kept", weak.container().getItem(0).getCount(), 2);
        check(source, "weak-fire-remains", fireCount(weak), 1);
        clearFurnace(level, at, touched);
        AlchemyFurnaceBlockEntity mixed = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        BlockPos low = AlchemyFurnaceStructure.world(at, Direction.NORTH, 0);
        if (level.getBlockEntity(low) instanceof AlchemyFurnaceCasingBlockEntity casing)
            casing.acceptWallItem(wallItem(level, BRITTLE_WALL));
        mixed.refreshStructure();
        check(source, "mixed-min-wall", mixed.maximumTemperature(), 80.0D);
        BlockPos bare = AlchemyFurnaceStructure.world(at, Direction.NORTH, 1);
        if (level.getBlockEntity(bare) instanceof AlchemyFurnaceCasingBlockEntity empty) empty.acceptWallItem(ItemStack.EMPTY);
        mixed.refreshStructure();
        fill(mixed);
        check(source, "missing-material", AlchemyWorkstationService.start(player, mixed).failure(), AlchemyFailure.STRUCTURE);
        check(source, "missing-material-kept", mixed.container().getItem(0).getCount(), 2);
        clearFurnace(level, at, touched);
        AlchemyFurnaceBlockEntity owned = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        owned.refreshStructure();
        fill(owned);
        owned.container().setItem(5, new ItemStack(Items.DIAMOND, 3));
        check(source, "core-hopper-has-no-aggregate", HopperBlockEntity.getContainerAt(level, at) == null, true);
        BlockPos main = AlchemyFurnaceStructure.world(at, Direction.NORTH, AlchemyFurnaceStructure.MAIN_INDEX);
        clearDrops(level, main);
        level.destroyBlock(main, true, player);
        List<ItemEntity> mainDrops = drops(level, main);
        check(source, "main-drops-herb", count(mainDrops, Items.ALLIUM), 2);
        check(source, "main-drops-not-aux", count(mainDrops, Items.CORNFLOWER), 0);
        check(source, "aux-kept", owned.container().getItem(2).getCount(), 2);
        clearDrops(level, at);
        level.destroyBlock(at, true, player);
        List<ItemEntity> coreDrops = drops(level, at);
        check(source, "core-one-furnace", count(coreDrops, MxtBlocks.ALCHEMY_FURNACE.get().asItem()), 1);
        check(source, "core-one-fire", count(coreDrops, AlchemyTestFireItems.FIRE.get()), 1);
        check(source, "core-not-aux", count(coreDrops, Items.CORNFLOWER), 0);
        check(source, "core-not-output", count(coreDrops, Items.DIAMOND), 0);
        BlockPos aux = AlchemyFurnaceStructure.world(at, Direction.NORTH, AlchemyFurnaceStructure.AUXILIARY_INDEX);
        BlockPos output = AlchemyFurnaceStructure.world(at, Direction.NORTH, AlchemyFurnaceStructure.OUTPUT_INDEX);
        check(source, "core-removal-keeps-aux", ((AlchemyFurnaceInventoryBlockEntity) level.getBlockEntity(aux)).inventory().getItem(0).getCount(), 2);
        check(source, "core-removal-keeps-output", ((AlchemyFurnaceInventoryBlockEntity) level.getBlockEntity(output)).inventory().getItem(0).getCount(), 3);
    }

    private static void callbackFailures(CommandSourceStack source, ServerLevel level, ServerPlayer player, BlockPos at, Gate gate, List<BlockPos> touched) {
        AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        furnace.refreshStructure();
        furnace.setTargetTemperature(50);
        fill(furnace);
        gate.primary = furnace;
        gate.removeFire = true;
        StartResult result = AlchemyWorkstationService.start(player, furnace);
        gate.removeFire = false;
        check(source, "pre-fire-removed", fireCount(furnace), 0);
        check(source, "pre-fire-rejected", result.failure(), AlchemyFailure.TEMPERATURE);
        check(source, "pre-fire-main-kept", furnace.container().getItem(0).getCount(), 2);
        check(source, "pre-fire-aux-kept", furnace.container().getItem(2).getCount(), 2);
        check(source, "pre-fire-catalyst-kept", furnace.container().getItem(4).getCount(), 1);
        clearFurnace(level, at, touched);
        AlchemyFurnaceBlockEntity replaced = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        replaced.refreshStructure();
        replaced.setTargetTemperature(50);
        fillMarked(replaced, Items.SUNFLOWER);
        gate.primary = replaced;
        gate.player = player;
        gate.restart = true;
        gate.restartResult = null;
        check(source, "action-replacement-start", AlchemyWorkstationService.start(player, replaced).started(), true);
        tickUntil(level, replaced, 8, phase -> phase == AlchemyPhase.IDLE || level.getBlockState(at).is(Blocks.GOLD_BLOCK));
        gate.restart = false;
        check(source, "action-before-post", level.getBlockState(at).is(Blocks.GOLD_BLOCK), true);
        check(source, "post-cannot-restart-removed-core", gate.restartResult != null && !gate.restartResult.started(), true);
        BlockPos main = AlchemyFurnaceStructure.world(at, Direction.NORTH, AlchemyFurnaceStructure.MAIN_INDEX);
        check(source, "stale-core-cannot-write-main", ((AlchemyFurnaceInventoryBlockEntity) level.getBlockEntity(main)).inventory().getItem(0).isEmpty(), true);
    }

    private static void menuOwnership(CommandSourceStack source, ServerLevel level, ServerPlayer player, BlockPos at, List<BlockPos> touched) {
        AlchemyFurnaceBlockEntity furnace = place(level, at, Direction.NORTH, item(level, WIDE), touched);
        furnace.refreshStructure();
        FakePlayer viewer = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "AlchemyMenus"));
        viewer.setPos(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
        AlchemyFurnaceMenu monitor = new AlchemyFurnaceMenu(20, viewer.getInventory(), at, AlchemyFurnaceMenu.View.MONITOR);
        viewer.containerMenu = monitor;
        monitor.handleAction(viewer, new AlchemyActionC2SPayload(20, Action.TEMPERATURE, 50.125D));
        check(source, "temperature-exact-round-trip", monitor.temperatureAck().accepted() && monitor.temperatureAck().target() == 50.125D && furnace.state().targetTemperature() == 50.125D, true);
        int epoch = monitor.temperatureAck().epoch();
        monitor.handleAction(viewer, new AlchemyActionC2SPayload(20, Action.TEMPERATURE, 50.125D));
        check(source, "temperature-same-value-ack", monitor.temperatureAck().accepted() && monitor.temperatureAck().epoch() != epoch, true);
        epoch = monitor.temperatureAck().epoch();
        monitor.handleAction(viewer, new AlchemyActionC2SPayload(20, Action.TEMPERATURE, Double.NaN));
        check(source, "temperature-reject-keeps-value", !monitor.temperatureAck().accepted() && monitor.temperatureAck().epoch() != epoch && monitor.temperatureAck().target() == 50.125D, true);
        BlockPos outputPos = AlchemyFurnaceStructure.world(at, Direction.NORTH, AlchemyFurnaceStructure.OUTPUT_INDEX);
        AlchemyFurnaceInventoryBlockEntity output = (AlchemyFurnaceInventoryBlockEntity) level.getBlockEntity(outputPos);
        output.inventory().setItem(0, new ItemStack(Items.DIAMOND));
        viewer.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 3));
        AlchemyFurnaceMenu outputs = new AlchemyFurnaceMenu(21, viewer.getInventory(), outputPos, AlchemyFurnaceMenu.View.OUTPUT);
        outputs.quickMoveStack(viewer, outputs.menuIndex("inventory_9"));
        check(source, "output-occupied-rejects-shift", output.inventory().getItem(0).getCount(), 1);
        check(source, "output-rejection-keeps-player-items", count(viewer, Items.DIAMOND), 3);
        viewer.getInventory().clearContent();
        furnace.setTargetTemperature(50);
        fill(furnace);
        check(source, "busy-menu-start", AlchemyWorkstationService.start(player, furnace).started(), true);
        BlockPos mainPos = AlchemyFurnaceStructure.world(at, Direction.NORTH, AlchemyFurnaceStructure.MAIN_INDEX);
        AlchemyFurnaceInventoryBlockEntity main = (AlchemyFurnaceInventoryBlockEntity) level.getBlockEntity(mainPos);
        main.inventory().setItem(0, new ItemStack(Items.ALLIUM));
        viewer.getInventory().setItem(9, new ItemStack(Items.ALLIUM, 3));
        AlchemyFurnaceMenu inputs = new AlchemyFurnaceMenu(22, viewer.getInventory(), mainPos, AlchemyFurnaceMenu.View.MAIN);
        inputs.quickMoveStack(viewer, inputs.menuIndex("inventory_9"));
        check(source, "busy-occupied-input-rejects-shift", main.inventory().getItem(0).getCount(), 1);
        check(source, "busy-input-keeps-player-items", count(viewer, Items.ALLIUM), 3);
        check(source, "busy-fire-cannot-be-taken", furnace.fireContainer().removeItem(0, 1).isEmpty(), true);
        furnace.fireContainer().setItem(0, ItemStack.EMPTY);
        check(source, "busy-fire-cannot-be-cleared", fireCount(furnace), 1);
        AlchemyWorkstationService.abort(level, at, furnace);
        tickUntil(level, furnace, 8, phase -> phase == AlchemyPhase.IDLE);
        viewer.getInventory().clearContent();
        viewer.getInventory().setItem(9, new ItemStack(Items.ALLIUM, 3));
        inputs.quickMoveStack(viewer, inputs.menuIndex("inventory_9"));
        check(source, "idle-occupied-input-merges", main.inventory().getItem(0).getCount(), 4);
        check(source, "idle-input-moves-player-items", count(viewer, Items.ALLIUM), 0);
        BlockState mainState = level.getBlockState(mainPos);
        level.setBlockAndUpdate(mainPos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(mainPos, mainState);
        check(source, "replaced-input-invalidates-menu", inputs.stillValid(viewer), false);
        viewer.getInventory().setItem(9, new ItemStack(Items.ALLIUM, 2));
        inputs.quickMoveStack(viewer, inputs.menuIndex("inventory_9"));
        check(source, "stale-input-keeps-player-items", count(viewer, Items.ALLIUM), 2);
        check(source, "stale-input-cannot-write-replacement", ((AlchemyFurnaceInventoryBlockEntity) level.getBlockEntity(mainPos)).inventory().isEmpty(), true);
        level.setBlockAndUpdate(mainPos, MxtBlocks.ALCHEMY_AUXILIARY_INPUT.get().defaultBlockState());
        boolean rejected = false;
        try {
            new AlchemyFurnaceMenu(23, viewer.getInventory(), mainPos, AlchemyFurnaceMenu.View.MAIN);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        check(source, "wrong-kind-menu-rejected", rejected, true);
        AlchemyFurnaceMenu auxiliary = new AlchemyFurnaceMenu(24, viewer.getInventory(), mainPos, AlchemyFurnaceMenu.View.AUXILIARY);
        check(source, "replacement-role-opens", auxiliary.stillValid(viewer), true);
        level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(at, MxtBlocks.ALCHEMY_FURNACE.get().defaultBlockState());
        AlchemyFurnaceBlockEntity replacement = (AlchemyFurnaceBlockEntity) level.getBlockEntity(at);
        replacement.acceptFurnaceItem(item(level, WIDE));
        double target = replacement.state().targetTemperature();
        viewer.containerMenu = monitor;
        monitor.handleAction(viewer, new AlchemyActionC2SPayload(20, Action.TEMPERATURE, 40.0D));
        check(source, "replaced-core-invalidates-menu", monitor.stillValid(viewer), false);
        check(source, "stale-core-action-keeps-replacement", replacement.state().targetTemperature(), target);
    }

    private static void clearDrops(ServerLevel level, BlockPos pos) {
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4)))
            if (PROBE_DROPS.contains(entity)) entity.discard();
    }

    private static List<ItemEntity> drops(ServerLevel level, BlockPos pos) {
        List<ItemEntity> found = new ArrayList<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)))
            if (PROBE_DROPS.contains(entity)) found.add(entity);
        return found;
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
        for (int slot = 5; slot < 9; slot++) if (furnace.container().getItem(slot).is(Items.CHARCOAL)) stored += furnace.container().getItem(slot).getCount();
        return pending + stored;
    }

    private static boolean HolderHelperEquals(ItemStack stack, Identifier quality) {
        Holder<?> holder = stack.get(MxtDataComponents.QUALITY.get());
        return holder != null && HolderHelper.id(holder).equals(quality);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NS, path);
    }

    private static ServerPlayer playerOf(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void check(CommandSourceStack source, String name, Object actual, Object expected) {
        checks++;
        boolean ok = Objects.equals(expected, actual);
        if (!ok) mismatches++;
        Component line = Component.literal("alchemy probe: " + (ok ? "OK" : "MISMATCH") + " " + name + " actual=" + actual + " expected=" + expected);
        if (ok) source.sendSuccess(() -> line, false);
        else source.sendFailure(line);
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

    private static final class PlaceRefused extends RuntimeException {
        private PlaceRefused(String message) {
            super(message);
        }
    }

    private static final class Gate {
        private final ServerLevel level;
        private final List<BlockPos> touched;
        private final Thread thread = Thread.currentThread();
        private boolean cancel;
        private boolean mutate;
        private boolean reenter;
        private boolean move;
        private boolean removeFire;
        private boolean restart;
        private boolean restarted;
        private int posts;
        private UUID postOperator;
        private AlchemyFurnaceBlockEntity primary;
        private AlchemyFurnaceBlockEntity other;
        private ServerPlayer player;
        private StartResult reentered;
        private StartResult restartResult;

        private Gate(ServerLevel level, List<BlockPos> touched) {
            this.level = level;
            this.touched = touched;
        }

        @SubscribeEvent
        public void itemSpawned(EntityJoinLevelEvent event) {
            if (Thread.currentThread() == this.thread && event.getLevel() == this.level && !event.loadedFromDisk()
                    && event.getEntity() instanceof ItemEntity item && this.touched.contains(item.blockPosition()))
                PROBE_DROPS.add(item);
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
            if (this.removeFire && this.primary != null && event.pos().equals(this.primary.getBlockPos()))
                this.primary.fireContainer().removeItem(0, 1);
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
        furnace.fireContainer().setItem(0, new ItemStack(AlchemyTestFireItems.FIRE.get()));
        return furnace;
    }

    private static boolean hasOutput(AlchemyFurnaceBlockEntity furnace, Item item) {
        for (int slot = 5; slot < 9; slot++) if (furnace.container().getItem(slot).is(item)) return true;
        return furnace.state().session().map(session -> session.pendingOutputs().stream().anyMatch(stack -> stack.is(item))).orElse(false);
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
