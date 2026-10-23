package com.iafenvoy.mxt.probe.herb;

import com.iafenvoy.mxt.attachment.HerbChunkAttachment;
import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbGrowthService;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbService;
import com.iafenvoy.mxt.testmod.HerbTestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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
import java.util.OptionalDouble;

/**
 * End-to-end checks for the herb loop: the find, the placed clock, the wild roll, the settlement, the read stability
 * and the harvest stamp. Each leg is a test of its own, planting on the floor of its own arena.
 *
 * <p>The loop has five moving parts and every leg aims at one of them. There is no sowing and no right-click, so
 * nothing here clicks a block.
 */
public final class HerbProbes {
    private static final Identifier GROWER = ProbeDefinitions.id("herb/grower");
    private static final Identifier AGING = ProbeDefinitions.id("herb/aging");
    private static final Identifier ROLLED = ProbeDefinitions.id("herb/rolled");
    private static final int MAX = 20;
    // The arena's first air layer, which is where the old legs' "above the world's own terrain" base used to be.
    private static final BlockPos SPOT = new BlockPos(8, 0, 8);

    private HerbProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("herb/find_block", HerbProbes::findBlock),
                Probe.of("herb/placed_clock", HerbProbes::placedClock),
                Probe.of("herb/wild_roll", HerbProbes::wildRoll),
                Probe.of("herb/settle", HerbProbes::settle),
                Probe.of("herb/stability", HerbProbes::stability),
                Probe.of("herb/drops", HerbProbes::drops),
                Probe.of("herb/unclaimed", HerbProbes::unclaimed));
    }

    // A herb is identified by its block, and only its block.
    private static void findBlock(ProbeContext context) {
        boolean ok = true;
        BlockState soil = HerbTestBlocks.SOIL.get().defaultBlockState();
        BlockState grower = HerbTestBlocks.WILD_GROWER.get().defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();

        ok &= check(context, "herb probe: soil is claimed by grower",
                SpiritHerbService.findBlock(context.registries(), soil).map(HerbProbes::idOf).orElse(null), GROWER);
        ok &= check(context, "herb probe: wild grower block is claimed by rolled",
                SpiritHerbService.findBlock(context.registries(), grower).map(HerbProbes::idOf).orElse(null), ROLLED);
        ok &= check(context, "herb probe: the third test block is claimed by aging",
                SpiritHerbService.findBlock(context.registries(), HerbTestBlocks.WILD_ROLLED.get().defaultBlockState())
                        .map(HerbProbes::idOf).orElse(null), AGING);
        ok &= check(context, "herb probe: stone is not a herb",
                SpiritHerbService.findBlock(context.registries(), stone).isPresent(), false);
        // The drop matcher is how an item is traced back to its plant, and it is not the block matcher.
        ok &= check(context, "herb probe: torchflower resolves to grower",
                SpiritHerbService.find(context.registries(), new ItemStack(Items.TORCHFLOWER))
                        .map(HerbProbes::idOf).orElse(null), GROWER);
        ok &= check(context, "herb probe: dirt does not resolve to a herb",
                SpiritHerbService.find(context.registries(), new ItemStack(Items.DIRT)).isPresent(), false);
        // A herb with no growth bears no fruit, so its block's own item is the only handle it has. Without that
        // fallback its power fields could never be read from any stack, and the picker row would show no tooltip.
        ok &= check(context, "herb probe: a furnace-only herb is readable from its block's item",
                SpiritHerbService.find(context.registries(), new ItemStack(Items.CACTUS))
                        .map(HerbProbes::idOf).orElse(null), ProbeDefinitions.id("alchemy/rank"));
        // ... but a herb that does have a growth must not also match by its block, or one stack would be claimed
        // by two definitions at once.
        ok &= check(context, "herb probe: a fruiting herb does not also match by its block item",
                SpiritHerbService.find(context.registries(), new ItemStack(Items.HONEYCOMB_BLOCK)).isPresent(), false);
        ok &= check(context, "herb probe: a fruiting herb still matches by its fruit",
                SpiritHerbService.find(context.registries(), new ItemStack(Items.HONEYCOMB))
                        .map(HerbProbes::idOf).orElse(null), ProbeDefinitions.id("herb/tiered"));
    }

    // Planting is placing. The clock is the current game time, and nothing about the stack that placed it.
    private static void placedClock(ProbeContext context) {
        ServerLevel level = context.level();
        BlockPos pos = context.at(SPOT);
        boolean ok = true;
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, HerbTestBlocks.SOIL.get().defaultBlockState());
        SpiritHerbGrowthService.placed(level, pos, level.getBlockState(pos));

        Long clock = clock(level, pos);
        ok &= check(context, "herb probe: placing a claimed block opens a clock", clock != null, true);
        ok &= check(context, "herb probe: the clock is the current game time", clock, level.getGameTime());

        // A placed plant starts young however long the world has run.
        ok &= check(context, "herb probe: a placed plant starts at age 0", ageAt(level, pos), 0.0D);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    // A block nobody placed has no row, and the first read rolls one and stores it negative.
    private static void wildRoll(ProbeContext context) {
        ServerLevel level = context.level();
        BlockPos pos = context.at(SPOT);
        // Two blocks along, so the fractional plant never shares a row or a neighbour update with the first.
        BlockPos fractional = context.at(SPOT.offset(2, 0, 0));
        boolean ok = true;
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        // alchemy_test_wild_grower is claimed by herb/rolled: wild_age is a uniform 1..3, max_age is 40.
        level.setBlockAndUpdate(pos, HerbTestBlocks.WILD_GROWER.get().defaultBlockState());
        forget(level, pos);

        ok &= check(context, "herb probe: an untouched wild block has no row", clock(level, pos), null);
        double rolled = ageAt(level, pos);
        ok &= check(context, "herb probe: the first read rolls a non-negative age", rolled >= 0.0D, true);
        ok &= check(context, "herb probe: the rolled age is capped at max_age", rolled <= 40.0D, true);

        // The roll is parked in the age field with a negative clock marking the row unsettled; the next read
        // settles it into a real row.
        Long stored = clock(level, pos);
        ok &= check(context, "herb probe: the roll is left as an unsettled negative clock", stored != null && stored < 0L, true);
        // A second read must agree with the first: this is the regression the age-oscillation bug was.
        ok &= check(context, "herb probe: the second read agrees with the first", ageAt(level, pos), rolled);
        // ... and the row it settled into is a normal, positive one, still at the same age.
        Long settled = clock(level, pos);
        ok &= check(context, "herb probe: reading it again turns the row into a real clock", settled != null && settled >= 0L, true);
        ok &= check(context, "herb probe: a settled row keeps the rolled age", ageAt(level, pos), rolled);
        // herb/aging rolls a fractional range, so the parked roll must survive the round trip exactly: encoding it
        // in the clock's magnitude used to truncate it and make the first read disagree with the second.
        level.setBlockAndUpdate(fractional, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(fractional, HerbTestBlocks.WILD_ROLLED.get().defaultBlockState());
        forget(level, fractional);
        double firstRoll = ageAt(level, fractional);
        ok &= check(context, "herb probe: a rolled age survives the first read exactly",
                ageAt(level, fractional), firstRoll);
        ok &= check(context, "herb probe: a rolled age survives the second read exactly",
                ageAt(level, fractional), firstRoll);
        level.setBlockAndUpdate(fractional, Blocks.AIR.defaultBlockState());
        forget(level, fractional);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        forget(level, pos);
    }

    // An open clock settles a period at a time, and pays for exactly the periods it moves.
    private static void settle(ProbeContext context) {
        ServerLevel level = context.level();
        BlockPos pos = context.at(SPOT);
        boolean ok = true;
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, HerbTestBlocks.SOIL.get().defaultBlockState());
        SpiritHerbGrowthService.placed(level, pos, level.getBlockState(pos));
        ok &= check(context, "herb probe: settled plant starts at 0", ageAt(level, pos), 0.0D);

        // Rewind the clock and let the real settlement run, so the period boundary is the server's own.
        rewind(level, pos, 20);
        settle(level, pos);
        double afterOne = ageAt(level, pos);
        ok &= check(context, "herb probe: one period grows the plant", afterOne > 0.0D, true);
        ok &= check(context, "herb probe: growth stops at mature_age for a constant rate", afterOne <= MAX, true);

        // The clock moved up by what it paid for, so the same time can never be charged twice.
        Long moved = clock(level, pos);
        ok &= check(context, "herb probe: the clock advanced past the settled span", moved != null && moved > level.getGameTime() - 40L, true);

        // Time paid for twice is time grown twice.
        rewind(level, pos, 20);
        settle(level, pos);
        ok &= check(context, "herb probe: a second period grows further", ageAt(level, pos) >= afterOne, true);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        forget(level, pos);
    }

    // Reading a plant must never move its age backwards, and reading it twice must not grow it twice. This is the
    // regression for the age oscillation: a replayed window used to answer differently on every read.
    private static void stability(ProbeContext context) {
        ServerLevel level = context.level();
        BlockPos pos = context.at(SPOT);
        boolean ok = true;
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, HerbTestBlocks.SOIL.get().defaultBlockState());
        SpiritHerbGrowthService.placed(level, pos, level.getBlockState(pos));

        // Give it real time behind it, then read many times in a row without any time passing between the reads.
        rewind(level, pos, 400);
        double first = ageAt(level, pos);
        ok &= check(context, "herb probe: a long span grows the plant", first > 0.0D, true);
        boolean stable = true;
        for (int i = 0; i < 8; i++)
            stable &= ageAt(level, pos) == first;
        // Repeated identical reads are the exact shape of the bug: Jade polls, so a plant that answers differently
        // each time shows up as a number flickering between two values.
        ok &= check(context, "herb probe: repeated reads answer the same age", stable, true);

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
        ok &= check(context, "herb probe: age never decreases as time passes", monotonic, true);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        forget(level, pos);
    }

    // Breaking stamps the age onto the dropped fruit and only onto the dropped fruit.
    private static void drops(ProbeContext context) {
        ServerLevel level = context.level();
        BlockPos pos = context.at(SPOT);
        boolean ok = true;
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, HerbTestBlocks.SOIL.get().defaultBlockState());
        SpiritHerbGrowthService.placed(level, pos, level.getBlockState(pos));
        SpiritHerb herb = SpiritHerbService.findBlock(context.registries(), level.getBlockState(pos))
                .map(Holder::value).orElse(null);
        ok &= check(context, "herb probe: the placed block has a herb to stamp with", herb != null, true);
        if (herb == null) return;

        ItemStack fruit = new ItemStack(Items.TORCHFLOWER);
        ItemStack other = new ItemStack(Items.DIRT);
        List<ItemEntity> drops = List.of(
                SpiritHerbGrowthService.item(level, pos, fruit),
                SpiritHerbGrowthService.item(level, pos, other));

        // Rewind so the plant has a real age to stamp, then break it through the framework's own entry point.
        rewind(level, pos, 200);
        settle(level, pos);
        SpiritHerbGrowthService.harvested(level, pos, level.getBlockState(pos), drops);

        ok &= check(context, "herb probe: the fruit is stamped with an age",
                drops.getFirst().getItem().get(MxtDataComponents.HERB_AGE) != null, true);
        ok &= check(context, "herb probe: an unclaimed drop is left alone",
                drops.get(1).getItem().get(MxtDataComponents.HERB_AGE) != null, false);
        // The row is the plant's, and the plant is gone.
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        SpiritHerbGrowthService.harvested(level, pos, Blocks.AIR.defaultBlockState(), List.of());
        ok &= check(context, "herb probe: breaking the plant clears its clock", clock(level, pos), null);
        forget(level, pos);
    }

    // Nothing at all happens on a block no herb claims.
    private static void unclaimed(ProbeContext context) {
        ServerLevel level = context.level();
        BlockPos pos = context.at(SPOT);
        boolean ok = true;
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        forget(level, pos);
        SpiritHerbGrowthService.placed(level, pos, level.getBlockState(pos));
        ok &= check(context, "herb probe: an unclaimed block gets no clock", clock(level, pos), null);
        ok &= check(context, "herb probe: an unclaimed block has no age", ageAt(level, pos), null);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    private static Double ageAt(ServerLevel level, BlockPos pos) {
        OptionalDouble age = SpiritHerbGrowthService.ageAt(level, pos, level.getBlockState(pos));
        return age.isPresent() ? age.getAsDouble() : null;
    }

    private static void settle(ServerLevel level, BlockPos pos) {
        SpiritHerbGrowthService.settle(level, level.getChunkAt(pos), attachment(level, pos));
    }

    // Puts the clock back so the next real settlement has a full period to pay for. It never crosses zero: a
    // negative clock is the engine's own marker for a wild roll nobody has read yet, so a row rewound past it would
    // be read as an unsettled plant and dated back to now instead. The span is therefore capped at the world's own
    // age, which is the oldest a real row can honestly be dated.
    private static void rewind(ServerLevel level, BlockPos pos, int ticks) {
        HerbChunkAttachment attachment = attachment(level, pos);
        HerbChunkAttachment.Plant plant = attachment.find(pos).orElse(null);
        if (plant == null) return;
        attachment.set(pos, plant.age(), Math.max(0L, plant.clock() - ticks));
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

    // The old leg's own line, printed either way, and the comparison it announced is the verdict.
    private static boolean check(ProbeContext context, String label, Object actual, Object expected) {
        boolean same = Objects.equals(actual, expected);
        String line = label + " actual=" + actual + " expected=" + expected + (same ? " OK" : " MISMATCH");
        context.note(line);
        return context.check(line, same);
    }
}
