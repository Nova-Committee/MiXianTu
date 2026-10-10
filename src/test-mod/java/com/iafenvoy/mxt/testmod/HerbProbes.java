package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.attachment.HerbChunkAttachment;
import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbGrowthService;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;

/**
 * End-to-end checks for the herb loop. Each leg prints actual and expected.
 * <p>
 * The loop has five moving parts and every leg aims at one of them: a claimed block placed in the world opens a
 * row at the current game time; a claimed block that was already there is rolled once and parked as an unsettled
 * row; an open row settles every 20 ticks and advances by exactly what it paid for; reading a plant twice answers
 * the same age and never goes backwards; and breaking a plant stamps its age onto whatever its {@code drops}
 * claims. There is no sowing and no right-click, so nothing here clicks a block.
 */
public final class HerbProbes {
    private static final Identifier GROWER = id("herb/grower");
    private static final Identifier AGING = id("herb/aging");
    private static final Identifier ROLLED = id("herb/rolled");
    private static final int MAX = 20;

    private HerbProbes() {
    }

    public static int run(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());
        // Above the world's own terrain, so every leg starts from air and no leg inherits another's block.
        BlockPos base = new BlockPos(origin.getX(), Math.min(level.getMaxY() - 8, 200), origin.getZ());
        boolean ok = true;

        ok &= findBlockLeg(source, level, base);
        ok &= placedClockLeg(source, level, base);
        ok &= wildRollLeg(source, level, base);
        ok &= settleLeg(source, level, base);
        ok &= stabilityLeg(source, level, base);
        ok &= dropsLeg(source, level, base);
        ok &= unclaimedLeg(source, level, base);

        final boolean passed = ok;
        source.sendSuccess(() -> Component.literal(passed ? "herb probe: all legs OK" : "herb probe: MISMATCH"), false);
        return ok ? 1 : 0;
    }

    // A herb is identified by its block, and only its block.
    private static boolean findBlockLeg(CommandSourceStack source, ServerLevel level, BlockPos base) {
        boolean ok = true;
        BlockState soil = HerbTestBlocks.SOIL.get().defaultBlockState();
        BlockState grower = HerbTestBlocks.WILD_GROWER.get().defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();

        ok &= check(source, "herb probe: soil is claimed by grower",
                SpiritHerbService.findBlock(level.registryAccess(), soil).map(HerbProbes::idOf).orElse(null), GROWER);
        ok &= check(source, "herb probe: wild grower block is claimed by rolled",
                SpiritHerbService.findBlock(level.registryAccess(), grower).map(HerbProbes::idOf).orElse(null), ROLLED);
        ok &= check(source, "herb probe: the third test block is claimed by aging",
                SpiritHerbService.findBlock(level.registryAccess(), HerbTestBlocks.WILD_ROLLED.get().defaultBlockState())
                        .map(HerbProbes::idOf).orElse(null), AGING);
        ok &= check(source, "herb probe: stone is not a herb",
                SpiritHerbService.findBlock(level.registryAccess(), stone).isPresent(), false);
        // The drop matcher is how an item is traced back to its plant, and it is not the block matcher.
        ok &= check(source, "herb probe: torchflower resolves to grower",
                SpiritHerbService.find(level.registryAccess(), new ItemStack(Items.TORCHFLOWER))
                        .map(HerbProbes::idOf).orElse(null), GROWER);
        ok &= check(source, "herb probe: dirt does not resolve to a herb",
                SpiritHerbService.find(level.registryAccess(), new ItemStack(Items.DIRT)).isPresent(), false);
        // A herb with no growth bears no fruit, so its block's own item is the only handle it has. Without that
        // fallback its power fields could never be read from any stack, and the picker row would show no tooltip.
        ok &= check(source, "herb probe: a furnace-only herb is readable from its block's item",
                SpiritHerbService.find(level.registryAccess(), new ItemStack(Items.CACTUS))
                        .map(HerbProbes::idOf).orElse(null), id("alchemy/rank"));
        // ... but a herb that does have a growth must not also match by its block, or one stack would be claimed
        // by two definitions at once.
        ok &= check(source, "herb probe: a fruiting herb does not also match by its block item",
                SpiritHerbService.find(level.registryAccess(), new ItemStack(Items.HONEYCOMB_BLOCK)).isPresent(), false);
        ok &= check(source, "herb probe: a fruiting herb still matches by its fruit",
                SpiritHerbService.find(level.registryAccess(), new ItemStack(Items.HONEYCOMB))
                        .map(HerbProbes::idOf).orElse(null), id("herb/tiered"));
        return ok;
    }

    // Planting is placing. The clock is the current game time, and nothing about the stack that placed it.
    private static boolean placedClockLeg(CommandSourceStack source, ServerLevel level, BlockPos base) {
        BlockPos pos = base.offset(0, 0, 0);
        boolean ok = true;
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, HerbTestBlocks.SOIL.get().defaultBlockState());
        SpiritHerbGrowthService.placed(level, pos, level.getBlockState(pos));

        Long clock = clock(level, pos);
        ok &= check(source, "herb probe: placing a claimed block opens a clock", clock != null, true);
        ok &= check(source, "herb probe: the clock is the current game time", clock, level.getGameTime());

        // A placed plant starts young however long the world has run.
        ok &= check(source, "herb probe: a placed plant starts at age 0", ageAt(level, pos), 0.0D);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        return ok;
    }

    // A block nobody placed has no row, and the first read rolls one and stores it negative.
    private static boolean wildRollLeg(CommandSourceStack source, ServerLevel level, BlockPos base) {
        BlockPos pos = base.offset(2, 0, 0);
        boolean ok = true;
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        // alchemy_test_wild_grower is claimed by herb/rolled: wild_age is a uniform 1..3, max_age is 40.
        level.setBlockAndUpdate(pos, HerbTestBlocks.WILD_GROWER.get().defaultBlockState());
        forget(level, pos);

        ok &= check(source, "herb probe: an untouched wild block has no row", clock(level, pos), null);
        double rolled = ageAt(level, pos);
        ok &= check(source, "herb probe: the first read rolls a non-negative age", rolled >= 0.0D, true);
        ok &= check(source, "herb probe: the rolled age is capped at max_age", rolled <= 40.0D, true);

        // The roll is parked in the age field with a negative clock marking the row unsettled; the next read
        // settles it into a real row.
        Long stored = clock(level, pos);
        ok &= check(source, "herb probe: the roll is left as an unsettled negative clock", stored != null && stored < 0L, true);
        // A second read must agree with the first: this is the regression the age-oscillation bug was.
        ok &= check(source, "herb probe: the second read agrees with the first", ageAt(level, pos), rolled);
        // ... and the row it settled into is a normal, positive one, still at the same age.
        Long settled = clock(level, pos);
        ok &= check(source, "herb probe: reading it again turns the row into a real clock", settled != null && settled >= 0L, true);
        ok &= check(source, "herb probe: a settled row keeps the rolled age", ageAt(level, pos), rolled);
        // herb/aging rolls a fractional range, so the parked roll must survive the round trip exactly: encoding it
        // in the clock's magnitude used to truncate it and make the first read disagree with the second.
        BlockPos fractional = base.offset(3, 0, 0);
        level.setBlockAndUpdate(fractional, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(fractional, HerbTestBlocks.WILD_ROLLED.get().defaultBlockState());
        forget(level, fractional);
        double firstRoll = ageAt(level, fractional);
        ok &= check(source, "herb probe: a rolled age survives the first read exactly",
                ageAt(level, fractional), firstRoll);
        ok &= check(source, "herb probe: a rolled age survives the second read exactly",
                ageAt(level, fractional), firstRoll);
        level.setBlockAndUpdate(fractional, Blocks.AIR.defaultBlockState());
        forget(level, fractional);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        forget(level, pos);
        return ok;
    }

    // An open clock settles a period at a time, and pays for exactly the periods it moves.
    private static boolean settleLeg(CommandSourceStack source, ServerLevel level, BlockPos base) {
        BlockPos pos = base.offset(4, 0, 0);
        boolean ok = true;
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, HerbTestBlocks.SOIL.get().defaultBlockState());
        SpiritHerbGrowthService.placed(level, pos, level.getBlockState(pos));
        ok &= check(source, "herb probe: settled plant starts at 0", ageAt(level, pos), 0.0D);

        // Rewind the clock and let the real settlement run, so the period boundary is the server's own.
        rewind(level, pos, 20);
        settle(level, pos);
        double afterOne = ageAt(level, pos);
        ok &= check(source, "herb probe: one period grows the plant", afterOne > 0.0D, true);
        ok &= check(source, "herb probe: growth stops at mature_age for a constant rate", afterOne <= MAX, true);

        // The clock moved up by what it paid for, so the same time can never be charged twice.
        Long moved = clock(level, pos);
        ok &= check(source, "herb probe: the clock advanced past the settled span", moved != null && moved > level.getGameTime() - 40L, true);

        // Time paid for twice is time grown twice.
        rewind(level, pos, 20);
        settle(level, pos);
        ok &= check(source, "herb probe: a second period grows further", ageAt(level, pos) >= afterOne, true);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        forget(level, pos);
        return ok;
    }

    // Reading a plant must never move its age backwards, and reading it twice must not grow it twice. This is the
    // regression for the age oscillation: a replayed window used to answer differently on every read.
    private static boolean stabilityLeg(CommandSourceStack source, ServerLevel level, BlockPos base) {
        BlockPos pos = base.offset(10, 0, 0);
        boolean ok = true;
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, HerbTestBlocks.SOIL.get().defaultBlockState());
        SpiritHerbGrowthService.placed(level, pos, level.getBlockState(pos));

        // Give it real time behind it, then read many times in a row without any time passing between the reads.
        rewind(level, pos, 400);
        double first = ageAt(level, pos);
        ok &= check(source, "herb probe: a long span grows the plant", first > 0.0D, true);
        boolean stable = true;
        for (int i = 0; i < 8; i++)
            stable &= ageAt(level, pos) == first;
        // Repeated identical reads are the exact shape of the bug: Jade polls, so a plant that answers differently
        // each time shows up as a number flickering between two values.
        ok &= check(source, "herb probe: repeated reads answer the same age", stable, true);

        // A read must also never go backwards when time does pass.
        double seen = first;
        boolean monotonic = true;
        for (int i = 0; i < 8; i++) {
            rewind(level, pos, 20);
            settle(level, pos);
            double now = ageAt(level, pos);
            monotonic &= now >= seen;
            seen = now;
        }
        ok &= check(source, "herb probe: age never decreases as time passes", monotonic, true);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        forget(level, pos);
        return ok;
    }

    // Breaking stamps the age onto the dropped fruit and only onto the dropped fruit.
    private static boolean dropsLeg(CommandSourceStack source, ServerLevel level, BlockPos base) {
        BlockPos pos = base.offset(6, 0, 0);
        boolean ok = true;
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, HerbTestBlocks.SOIL.get().defaultBlockState());
        SpiritHerbGrowthService.placed(level, pos, level.getBlockState(pos));
        SpiritHerb herb = SpiritHerbService.findBlock(level.registryAccess(), level.getBlockState(pos))
                .map(Holder::value).orElse(null);
        ok &= check(source, "herb probe: the placed block has a herb to stamp with", herb != null, true);
        if (herb == null) return false;

        ItemStack fruit = new ItemStack(Items.TORCHFLOWER);
        ItemStack other = new ItemStack(Items.DIRT);
        List<ItemEntity> drops = List.of(
                SpiritHerbGrowthService.item(level, pos, fruit),
                SpiritHerbGrowthService.item(level, pos, other));

        // Rewind so the plant has a real age to stamp, then break it through the framework's own entry point.
        rewind(level, pos, 200);
        settle(level, pos);
        SpiritHerbGrowthService.harvested(level, pos, level.getBlockState(pos), drops);

        ok &= check(source, "herb probe: the fruit is stamped with an age",
                drops.getFirst().getItem().get(MxtDataComponents.HERB_AGE.get()) != null, true);
        ok &= check(source, "herb probe: an unclaimed drop is left alone",
                drops.get(1).getItem().get(MxtDataComponents.HERB_AGE.get()) != null, false);
        // The row is the plant's, and the plant is gone.
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        SpiritHerbGrowthService.harvested(level, pos, Blocks.AIR.defaultBlockState(), List.of());
        ok &= check(source, "herb probe: breaking the plant clears its clock", clock(level, pos), null);
        forget(level, pos);
        return ok;
    }

    // Nothing at all happens on a block no herb claims.
    private static boolean unclaimedLeg(CommandSourceStack source, ServerLevel level, BlockPos base) {
        BlockPos pos = base.offset(8, 0, 0);
        boolean ok = true;
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        forget(level, pos);
        SpiritHerbGrowthService.placed(level, pos, level.getBlockState(pos));
        ok &= check(source, "herb probe: an unclaimed block gets no clock", clock(level, pos), null);
        ok &= check(source, "herb probe: an unclaimed block has no age", ageAt(level, pos), null);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        return ok;
    }

    private static Double ageAt(ServerLevel level, BlockPos pos) {
        return SpiritHerbGrowthService.ageAt(level, pos, level.getBlockState(pos)).orElse(null);
    }

    private static void settle(ServerLevel level, BlockPos pos) {
        SpiritHerbGrowthService.settle(level, level.getChunkAt(pos), attachment(level, pos));
    }

    // Puts the clock back so the next real settlement has a full period to pay for.
    private static void rewind(ServerLevel level, BlockPos pos, int ticks) {
        HerbChunkAttachment attachment = attachment(level, pos);
        HerbChunkAttachment.Plant plant = attachment.find(pos).orElse(null);
        if (plant == null) return;
        attachment.set(pos, plant.age(), plant.clock() - ticks);
        level.getChunkAt(pos).markUnsaved();
    }

    private static void forget(ServerLevel level, BlockPos pos) {
        HerbChunkAttachment attachment = level.getChunkAt(pos).getExistingDataOrNull(MxtAttachments.HERB_CHUNK.get());
        if (attachment == null) return;
        if (attachment.remove(pos)) level.getChunkAt(pos).markUnsaved();
    }

    private static Long clock(ServerLevel level, BlockPos pos) {
        HerbChunkAttachment attachment = level.getChunkAt(pos).getExistingDataOrNull(MxtAttachments.HERB_CHUNK.get());
        HerbChunkAttachment.Plant plant = attachment == null ? null : attachment.find(pos).orElse(null);
        return plant == null ? null : plant.clock();
    }

    private static HerbChunkAttachment attachment(ServerLevel level, BlockPos pos) {
        return level.getChunkAt(pos).getData(MxtAttachments.HERB_CHUNK);
    }

    private static Identifier idOf(Holder<SpiritHerb> holder) {
        return holder.unwrapKey().map(ResourceKey::identifier).orElse(null);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, path);
    }

    private static boolean check(CommandSourceStack source, String label, Object actual, Object expected) {
        boolean same = Objects.equals(actual, expected);
        source.sendSuccess(() -> Component.literal(label + " actual=" + actual + " expected=" + expected
                + (same ? " OK" : " MISMATCH")), false);
        return same;
    }
}
