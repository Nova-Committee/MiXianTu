package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.item.block.entity.SpiritHerbPlotBlockEntity;
import com.iafenvoy.mxt.item.block.entity.SpiritHerbPlotBlockEntity.Pause;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbGrowthService;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbService;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbService.HerbPotency;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbService.HerbRole;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.AuraQueryCache;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.runtime.world.AuraWorldAttachment.Area;
import com.iafenvoy.mxt.runtime.world.AuraWorldAttachment.Shape;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Real plot, inventory and aura-pool checks for the herb loop. Each leg prints actual and expected.
 */
public final class HerbProbes {
    private static final Identifier A = id("herb/a");
    private static final Identifier GROWER = id("herb/grower");
    private static final Identifier NURSERY = id("herb/nursery");

    private HerbProbes() {
    }

    public static int run(CommandContext<CommandSourceStack> context) {
        return run(context.getSource());
    }

    public static int run(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("herb probe: needs a player MISMATCH"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos pos = player.blockPosition().above(2);
        BlockPos side = pos.east();
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(side).isAir()) {
            source.sendFailure(Component.literal("herb probe: positions occupied actual="
                    + level.getBlockState(pos).getBlock() + "," + level.getBlockState(side).getBlock()
                    + " expected=air MISMATCH"));
            return 0;
        }
        ItemStack hand = player.getMainHandItem().copy();
        ItemStack[] inventory = snapshot(player);
        GameType mode = player.gameMode();
        boolean instabuild = player.getAbilities().instabuild;
        boolean shift = player.isShiftKeyDown();
        AuraChunkAttachment chunk = level.getChunkAt(pos).getData(MxtAttachments.AURA_CHUNK);
        Map<Holder<Aura>, AuraPool> auraBefore = new LinkedHashMap<>(chunk.auras());
        String area = null;
        boolean ok = true;
        try {
            player.getAbilities().instabuild = false;
            area = level.getData(MxtAttachments.AURA_WORLD).add(new Area(NURSERY, shape(pos, side), 10_000));
            AuraQueryCache.setEnabled(false);
            Holder<Aura> spirit = aura(level, "spirit_power");
            Holder<Aura> water = aura(level, "water_power");
            if (spirit == null || water == null) {
                ok &= check(source, "herb probe: aura fixtures", "missing", "spirit_power and water_power");
            } else {
                chunk.initializeAuras(Map.of(spirit, AuraPool.natural(10.0D, 10.0D, 0.0D), water, AuraPool.natural(0.0D, 10.0D, 0.0D)));
                ok &= potency(source, level);
                ok &= place(level, pos);
                double bonus = AuraService.getPositionAura(level, pos).rules().spiritPlantBonus();
                ok &= check(source, "herb probe: nursery bonus", bonus, 0.5D);
                ok &= refuse(source, level, pos, player);
                ok &= growth(source, level, pos, player, spirit, water, chunk);
                ok &= breaks(source, level, side, player);
            }
        } catch (RuntimeException exception) {
            ok = false;
            source.sendFailure(Component.literal("herb probe: exception " + exception + " MISMATCH"));
        } finally {
            AuraQueryCache.setEnabled(true);
            if (area != null) level.getData(MxtAttachments.AURA_WORLD).remove(area);
            chunk.initializeAuras(auraBefore);
            level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(side, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            clearDrops(level, pos);
            clearDrops(level, side);
            restore(player, inventory);
            player.setItemInHand(InteractionHand.MAIN_HAND, hand);
            player.getAbilities().instabuild = instabuild;
            player.setShiftKeyDown(shift);
            if (player.gameMode() != mode) player.setGameMode(mode);
        }
        if (ok) source.sendSuccess(() -> Component.literal("herb probe: OK"), false);
        else source.sendFailure(Component.literal("herb probe: MISMATCH"));
        return ok ? 1 : 0;
    }

    private static boolean potency(CommandSourceStack source, ServerLevel level) {
        boolean ok = check(source, "herb probe: ginseng default age",
                SpiritHerbService.age(level.registryAccess(), new ItemStack(Items.RED_MUSHROOM)), 100);
        ItemStack young = new ItemStack(Items.ALLIUM);
        ItemStack century = new ItemStack(Items.ALLIUM);
        century.set(MxtDataComponents.HERB_AGE.get(), 100);
        Optional<HerbPotency> main = SpiritHerbService.potency(level.registryAccess(), young, HerbRole.MAIN, FormulaContext.of(level));
        Optional<HerbPotency> aged = SpiritHerbService.potency(level.registryAccess(), century, HerbRole.MAIN, FormulaContext.of(level));
        Optional<HerbPotency> auxiliary = SpiritHerbService.potency(level.registryAccess(), young, HerbRole.AUXILIARY, FormulaContext.of(level));
        ok &= check(source, "herb probe: A main age 0", main.map(HerbPotency::totalPower).orElse(-1.0D), 3.0D);
        ok &= check(source, "herb probe: A main age 100", aged.map(HerbPotency::totalPower).orElse(-1.0D), 6.0D);
        ok &= check(source, "herb probe: A auxiliary", auxiliary.map(HerbPotency::totalPower).orElse(-1.0D), 0.0D);
        ok &= check(source, "herb probe: B main", power(level, Items.AZURE_BLUET, HerbRole.MAIN), 6.0D);
        ok &= check(source, "herb probe: C auxiliary", power(level, Items.CORNFLOWER, HerbRole.AUXILIARY), 3.0D);
        ok &= check(source, "herb probe: C bias", bias(level, Items.CORNFLOWER, HerbRole.AUXILIARY), -1.0D);
        ok &= check(source, "herb probe: D catalyst", power(level, Items.OXEYE_DAISY, HerbRole.CATALYST), 1.0D);
        ItemStack split = century.copyWithCount(2);
        ItemStack half = split.split(1);
        boolean splitKeeps = half.get(MxtDataComponents.HERB_AGE.get()) == 100 && split.get(MxtDataComponents.HERB_AGE.get()) == 100;
        boolean unmerged = !ItemStack.isSameItemSameComponents(young, century);
        ok &= check(source, "herb probe: age split and no merge", splitKeeps && unmerged, true);
        Identifier found = main.flatMap(potency -> potency.herb().unwrapKey()).map(ResourceKey::identifier).orElse(null);
        return ok && check(source, "herb probe: A id", String.valueOf(found), A.toString());
    }

    private static boolean refuse(CommandSourceStack source, ServerLevel level, BlockPos pos, ServerPlayer player) {
        ItemStack stick = new ItemStack(Items.STICK, 2);
        InteractionResult passed = click(player, level, pos, stick, false);
        boolean stickKept = passed == InteractionResult.PASS && stick.getCount() == 2 && !plot(level, pos).occupied();
        boolean stickOk = check(source, "herb probe: non-seed refused", stickKept, true);
        GameType mode = player.gameMode();
        player.setGameMode(GameType.ADVENTURE);
        ItemStack protectedSeeds = new ItemStack(Items.TORCHFLOWER_SEEDS, 2);
        InteractionResult blocked = click(player, level, pos, protectedSeeds, false);
        boolean protectedOk = blocked != InteractionResult.SUCCESS && protectedSeeds.getCount() == 2 && !plot(level, pos).occupied();
        player.setGameMode(mode);
        player.getAbilities().instabuild = false;
        return stickOk && check(source, "herb probe: protected position", protectedOk, true);
    }

    private static boolean growth(CommandSourceStack source, ServerLevel level, BlockPos pos, ServerPlayer player,
                                  Holder<Aura> spirit, Holder<Aura> water, AuraChunkAttachment chunk) {
        boolean ok = true;
        ItemStack seeds = new ItemStack(Items.TORCHFLOWER_SEEDS, 2);
        InteractionResult planted = click(player, level, pos, seeds, false);
        SpiritHerbPlotBlockEntity plot = plot(level, pos);
        ok &= check(source, "herb probe: sowed", planted == InteractionResult.SUCCESS && plot.occupied() && plot.progress() == 0.0F && seeds.getCount() == 1, true);
        InteractionResult again = click(player, level, pos, seeds, false);
        ok &= check(source, "herb probe: no second plant", again != InteractionResult.SUCCESS && seeds.getCount() == 1, true);
        tick(level, pos, 19);
        ok &= check(source, "herb probe: 19 ticks", plot.progress(), 0.0F);
        ok &= emptyClick(source, "herb probe: immature status", player, level, pos, false);
        ok &= check(source, "herb probe: status left the plant", plot.occupied() && plot.progress() == 0.0F, true);
        tick(level, pos, 1);
        ok &= check(source, "herb probe: one period", plot.progress(), 3.0F);
        tick(level, pos, 60);
        ok &= check(source, "herb probe: four periods", plot.progress(), 12.0F);
        ItemStack[] beforeFull = snapshot(player);
        fill(player);
        ok &= emptyClick(source, "herb probe: harvest click", player, level, pos, false);
        ItemStack flower = stackOf(player, level, pos, Items.TORCHFLOWER);
        int flowers = count(player, Items.TORCHFLOWER) + dropped(level, pos, Items.TORCHFLOWER);
        int returned = count(player, Items.TORCHFLOWER_SEEDS) + dropped(level, pos, Items.TORCHFLOWER_SEEDS);
        boolean same = flower != null && GROWER.equals(SpiritHerbService.findHolder(level.registryAccess(), flower)
                .flatMap(holder -> holder.unwrapKey().map(ResourceKey::identifier)).orElse(null));
        int age = flower == null ? -1 : flower.getOrDefault(MxtDataComponents.HERB_AGE.get(), -1);
        ok &= check(source, "herb probe: harvest once", flowers == 1 && returned == 1 && age == 12 && same && !plot.occupied(), true);
        clearDrops(level, pos);
        restore(player, beforeFull);
        ItemStack resow = new ItemStack(Items.TORCHFLOWER_SEEDS);
        click(player, level, pos, resow, false);
        ok &= check(source, "herb probe: resown at 0", plot.occupied() && plot.progress() == 0.0F, true);
        int seedsBefore = count(player, Items.TORCHFLOWER_SEEDS);
        clearDrops(level, pos);
        ok &= emptyClick(source, "herb probe: immature pull click", player, level, pos, true);
        int seedBack = count(player, Items.TORCHFLOWER_SEEDS) - seedsBefore + dropped(level, pos, Items.TORCHFLOWER_SEEDS);
        ok &= check(source, "herb probe: immature pull", seedBack == 1 && dropped(level, pos, Items.TORCHFLOWER) == 0 && !plot.occupied(), true);
        clearDrops(level, pos);
        plot.clear();
        plot.sync();

        ok &= sowAndTick(source, level, pos, player, Items.POISONOUS_POTATO, 20);
        ok &= check(source, "herb probe: condition pause", plot.pause() == Pause.CONDITION && plot.progress() == 0.0F && amount(chunk, spirit) == 10.0D, true);
        plot.clear();
        plot.sync();
        ok &= sowAndTick(source, level, pos, player, Items.GLOW_BERRIES, 20);
        ok &= check(source, "herb probe: no partial charge", plot.pause() == Pause.AURA && plot.progress() == 0.0F
                && amount(chunk, spirit) == 10.0D && amount(chunk, water) == 0.0D, true);
        plot.clear();
        plot.sync();
        ok &= sowAndTick(source, level, pos, player, Items.PITCHER_POD, 20);
        ok &= check(source, "herb probe: paid growth", plot.progress() == 3.0F && amount(chunk, spirit) == 8.0D, true);
        tick(level, pos, 20);
        ok &= check(source, "herb probe: cap stops payment", plot.progress() == 3.0F && plot.pause() == Pause.CAPPED && amount(chunk, spirit) == 8.0D, true);
        plot.clear();
        plot.sync();
        ok &= randomRoll(source, level, pos, player, spirit, chunk);

        Holder<SpiritHerb> retired = level.registryAccess().lookupOrThrow(MxtResourceKeys.SPIRIT_HERB)
                .get(ResourceKey.create(MxtResourceKeys.SPIRIT_HERB, id("herb/retired"))).orElse(null);
        if (retired == null) return check(source, "herb probe: retired holder", "missing", "mxt_test:herb/retired");
        plot.restore(retired, new ItemStack(Items.LILY_OF_THE_VALLEY), 4.0F, 0, Pause.NONE);
        tick(level, pos, 20);
        boolean stopped = plot.progress() == 4.0F && plot.pause() == Pause.DISABLED;
        int before = count(player, Items.LILY_OF_THE_VALLEY);
        clearDrops(level, pos);
        ok &= emptyClick(source, "herb probe: disabled pull click", player, level, pos, true);
        int gained = count(player, Items.LILY_OF_THE_VALLEY) - before + dropped(level, pos, Items.LILY_OF_THE_VALLEY);
        ok &= check(source, "herb probe: disabled returns seed", stopped && gained == 1 && !plot.occupied(), true);
        return ok;
    }

    private static boolean breaks(CommandSourceStack source, ServerLevel level, BlockPos pos, ServerPlayer player) {
        place(level, pos);
        ItemStack seed = new ItemStack(Items.TORCHFLOWER_SEEDS);
        click(player, level, pos, seed, false);
        level.destroyBlock(pos, true, player);
        int immatureFlower = dropped(level, pos, Items.TORCHFLOWER);
        int immatureSeed = dropped(level, pos, Items.TORCHFLOWER_SEEDS);
        clearDrops(level, pos);
        boolean immature = check(source, "herb probe: immature break", immatureFlower == 0 && immatureSeed == 1, true);
        place(level, pos);
        click(player, level, pos, new ItemStack(Items.TORCHFLOWER_SEEDS), false);
        tick(level, pos, 80);
        level.destroyBlock(pos, true, player);
        int matureFlower = dropped(level, pos, Items.TORCHFLOWER);
        int matureSeed = dropped(level, pos, Items.TORCHFLOWER_SEEDS);
        return immature && check(source, "herb probe: mature break once", matureFlower == 1 && matureSeed == 1, true);
    }

    private static boolean sowAndTick(CommandSourceStack source, ServerLevel level, BlockPos pos, ServerPlayer player,
                                      Item seed, int ticks) {
        ItemStack stack = new ItemStack(seed);
        InteractionResult result = click(player, level, pos, stack, false);
        tick(level, pos, ticks);
        return check(source, "herb probe: sowed " + seed, result == InteractionResult.SUCCESS && stack.getCount() == 0, true);
    }

    private static boolean emptyClick(CommandSourceStack source, String label, ServerPlayer player, ServerLevel level,
                                      BlockPos pos, boolean sneak) {
        return check(source, label, click(player, level, pos, ItemStack.EMPTY, sneak), InteractionResult.SUCCESS);
    }

    private static boolean randomRoll(CommandSourceStack source, ServerLevel level, BlockPos pos, ServerPlayer player,
                                      Holder<Aura> spirit, AuraChunkAttachment chunk) {
        long seed = distinguishingSeed();
        net.minecraft.util.RandomSource single = net.minecraft.util.RandomSource.create(seed);
        double growth = uniform(single, 1.0D, 3.0D);
        double cost = uniform(single, 1.0D, 2.0D);
        chunk.initializeAuras(Map.of(spirit, AuraPool.natural(10.0D, 10.0D, 0.0D)));
        ItemStack seedStack = new ItemStack(Items.WHITE_TULIP);
        InteractionResult planted = click(player, level, pos, seedStack, false);
        if (planted != InteractionResult.SUCCESS) return check(source, "herb probe: rolled sow", planted, InteractionResult.SUCCESS);
        level.getRandom().setSeed(seed);
        tick(level, pos, 20);
        SpiritHerbPlotBlockEntity plot = plot(level, pos);
        float expectedProgress = (float) (growth * 1.5D);
        boolean progress = plot.progress() == expectedProgress;
        boolean charged = Math.abs(amount(chunk, spirit) - (10.0D - cost)) < 1.0E-9D;
        boolean ok = check(source, "herb probe: one growth roll", plot.progress(), expectedProgress);
        ok &= check(source, "herb probe: one cost roll", amount(chunk, spirit), 10.0D - cost);
        plot.clear();
        plot.sync();
        return ok && progress && charged && planted == InteractionResult.SUCCESS;
    }

    private static long distinguishingSeed() {
        for (long seed = 1L; seed < 64L; seed++) {
            net.minecraft.util.RandomSource single = net.minecraft.util.RandomSource.create(seed);
            double growth = uniform(single, 1.0D, 3.0D);
            double cost = uniform(single, 1.0D, 2.0D);
            net.minecraft.util.RandomSource doubled = net.minecraft.util.RandomSource.create(seed);
            uniform(doubled, 1.0D, 3.0D);
            double secondGrowth = uniform(doubled, 1.0D, 3.0D);
            double secondCost = uniform(doubled, 1.0D, 2.0D);
            if (growth != secondGrowth && cost != secondCost) return seed;
        }
        return 1L;
    }

    private static double uniform(net.minecraft.util.RandomSource random, double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private static ItemStack stackOf(ServerPlayer player, ServerLevel level, BlockPos pos, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) return stack;
        }
        return firstDrop(level, pos, item);
    }

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static InteractionResult click(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack, boolean sneak) {
        player.setShiftKeyDown(sneak);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
        return player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
    }

    private static void tick(ServerLevel level, BlockPos pos, int times) {
        SpiritHerbPlotBlockEntity plot = plot(level, pos);
        BlockState state = plot.getBlockState();
        for (int i = 0; i < times; i++) SpiritHerbPlotBlockEntity.serverTick(level, pos, state, plot);
    }

    private static boolean place(ServerLevel level, BlockPos pos) {
        return level.setBlockAndUpdate(pos, MxtBlocks.SPIRIT_HERB_PLOT.get().defaultBlockState());
    }

    private static SpiritHerbPlotBlockEntity plot(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SpiritHerbPlotBlockEntity plot))
            throw new IllegalStateException("spirit herb plot missing at " + pos);
        return plot;
    }

    private static double power(ServerLevel level, Item item, HerbRole role) {
        return SpiritHerbService.potency(level.registryAccess(), new ItemStack(item), role, FormulaContext.of(level))
                .map(HerbPotency::totalPower).orElse(-1.0D);
    }

    private static double bias(ServerLevel level, Item item, HerbRole role) {
        return SpiritHerbService.potency(level.registryAccess(), new ItemStack(item), role, FormulaContext.of(level))
                .map(HerbPotency::thermalBias).orElse(99.0D);
    }

    private static Holder<Aura> aura(ServerLevel level, String path) {
        return MxtDatapackRegistries.holder(level.registryAccess(), MxtResourceKeys.AURA, id(path)).orElse(null);
    }

    private static double amount(AuraChunkAttachment chunk, Holder<Aura> aura) {
        AuraPool pool = chunk.auras().get(aura);
        return pool == null ? -1.0D : pool.amount();
    }

    private static int dropped(ServerLevel level, BlockPos pos, Item item) {
        int total = 0;
        for (ItemEntity entity : drops(level, pos, item)) total += entity.getItem().getCount();
        return total;
    }

    private static ItemStack firstDrop(ServerLevel level, BlockPos pos, Item item) {
        List<ItemEntity> found = drops(level, pos, item);
        return found.isEmpty() ? null : found.getFirst().getItem();
    }

    private static List<ItemEntity> drops(ServerLevel level, BlockPos pos, Item item) {
        return level.getEntities(EntityType.ITEM, new AABB(pos).inflate(2.0D), entity -> entity.getItem().is(item));
    }

    private static void clearDrops(ServerLevel level, BlockPos pos) {
        for (ItemEntity entity : level.getEntities(EntityType.ITEM, new AABB(pos).inflate(2.0D), entity -> true))
            entity.discard();
    }

    private static void fill(ServerPlayer player) {
        ItemStack cobble = new ItemStack(Items.COBBLESTONE, 64);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            player.getInventory().setItem(slot, cobble.copy());
    }

    private static ItemStack[] snapshot(ServerPlayer player) {
        ItemStack[] slots = new ItemStack[player.getInventory().getContainerSize()];
        for (int slot = 0; slot < slots.length; slot++) slots[slot] = player.getInventory().getItem(slot).copy();
        return slots;
    }

    private static void restore(ServerPlayer player, ItemStack[] slots) {
        for (int slot = 0; slot < slots.length; slot++) player.getInventory().setItem(slot, slots[slot]);
    }

    private static Shape shape(BlockPos first, BlockPos second) {
        return new Shape(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()), Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("mxt_test", path);
    }

    private static boolean check(CommandSourceStack source, String label, Object actual, Object expected) {
        boolean same = actual instanceof Double left && expected instanceof Double right
                ? Double.compare(left, right) == 0 : java.util.Objects.equals(actual, expected);
        String line = label + " actual=" + actual + " expected=" + expected + (same ? " OK" : " MISMATCH");
        if (same) source.sendSuccess(() -> Component.literal(line), false);
        else source.sendFailure(Component.literal(line));
        return same;
    }
}
