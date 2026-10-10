package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.attachment.HerbChunkAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.data.alchemy.SpiritHerb.Growth;
import com.iafenvoy.mxt.data.cost.CostPayment;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.item.QualityService;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The 20-tick growth settlement and the harvest stamp, both read from the chunk attachment rather than a block
 * entity. A plant's clock lives in {@code mxt:herb_chunk} keyed by position, and the block standing there is
 * whichever block a pack's herb definition claims - this framework registers none of its own and asks no block to
 * implement anything.
 * <p>
 * Placing a claimed block writes the current game time, which is the whole of "planting": there is no sowing
 * interaction, no seedling and no vanilla growth. A block that was never placed - one the world generated - has no
 * row, and the first read rolls {@code wild_age}, walks it forward from game time 0 once, and parks the result in
 * the age field with a negative clock. The negative clock is what tells an unsettled row from a real one. The roll
 * is a provider and the walk can span a thousand periods, so repeating it per look would cost that much every time
 * and let two looks disagree about one plant.
 * <p>
 * Breaking a claimed block stamps the age onto whatever its {@code drops} matcher claims, so the fruit carries the
 * 药龄 it grew for. Nothing else about the break is this service's business.
 * <p>
 * Time is split by whether the chunk was ticking. A period that ticks pays everything: the environment is
 * re-tested and the aura cost is drawn. A period that elapsed while the chunk was unloaded has no level to test
 * against and no aura to draw, so it pays growth rate alone - the same split aura regeneration already makes for
 * itself. The split is read off the row's own clock: continuous ticking leaves {@code gameTime - clock} at
 * exactly one period, so anything past that is time this chunk was not there for.
 */
public final class SpiritHerbGrowthService {
    public static final int PERIOD = 20;

    private SpiritHerbGrowthService() {
    }

    // Placement. A claimed block starts its life here, at age zero, dated to the current game time rather than
    // anything the placement carried: a plant is young whatever it was built from.
    public static void placed(ServerLevel level, BlockPos pos, BlockState state) {
        SpiritHerbService.findBlock(level.registryAccess(), state).ifPresent(herb -> {
            if (herb.value().growth().isEmpty()) return;
            attachment(level, pos).set(pos, 0.0D, level.getGameTime());
            write(level, pos);
        });
    }

    /**
     * The age at this position right now, settling any time it is owed first. Empty when no herb claims the block,
     * which is the one and only answer to "is this a spirit herb" - the attachment is not consulted, because a row
     * outlives the definition that opened it.
     */
    public static Optional<Double> ageAt(ServerLevel level, BlockPos pos, BlockState state) {
        Optional<Holder<SpiritHerb>> herb = SpiritHerbService.findBlock(level.registryAccess(), state);
        if (herb.isEmpty()) return Optional.empty();
        Growth growth = herb.get().value().growth().orElse(null);
        if (growth == null) return Optional.empty();
        return Optional.of(resolve(level, pos, growth));
    }

    // The harvest. Every stack the block dropped that the herb claims as its fruit is stamped with the age the
    // plant reached, and the row is dropped with it: the plant is gone, so its clock has nothing left to describe.
    public static void harvested(ServerLevel level, BlockPos pos, BlockState state, List<ItemEntity> drops) {
        Optional<Holder<SpiritHerb>> herb = SpiritHerbService.findBlock(level.registryAccess(), state);
        if (herb.isEmpty()) return;
        Growth growth = herb.get().value().growth().orElse(null);
        if (growth == null) return;
        double age = resolve(level, pos, growth);
        int years = (int) Math.floor(Math.max(0.0D, age));
        for (ItemEntity entity : drops) {
            ItemStack stack = entity.getItem();
            if (!RegistryCodecs.matchesValue(growth.drops(), BuiltInRegistries.ITEM, stack.getItem())) continue;
            stack.set(MxtDataComponents.HERB_AGE.get(), years);
            herb.get().value().qualityFor(years).ifPresent(quality -> QualityService.set(stack, quality));
        }
        forget(level, pos);
    }

    // The settlement over the loaded chunks, once per period. A row whose block is gone is dropped; a row whose
    // herb no longer supplies a growth stage is paused in place rather than deleted, so a datapack edit that drops
    // the block does not silently throw away every plant on the map.
    public static void settle(ServerLevel level, LevelChunk chunk, HerbChunkAttachment attachment) {
        boolean moved = false;
        for (Map.Entry<BlockPos, HerbChunkAttachment.Plant> entry : attachment.plants().entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = chunk.getBlockState(pos);
            Optional<Holder<SpiritHerb>> herb = SpiritHerbService.findBlock(level.registryAccess(), state);
            Growth growth = herb.flatMap(value -> value.value().growth()).orElse(null);
            if (growth == null) {
                // No herb claims this block any more, so the row describes nothing and goes.
                if (herb.isEmpty()) moved |= attachment.remove(pos);
                continue;
            }
            HerbChunkAttachment.Plant plant = entry.getValue();
            if (plant.clock() < 0L) {
                // A parked wild roll nobody has read yet. It becomes a real row dated to now, keeping the age it
                // rolled: the clock moves forward and the parked age becomes the age the plant starts at.
                attachment.set(pos, Math.min(plant.age(), growth.maxAge()), level.getGameTime());
                moved = true;
                continue;
            }
            long gap = level.getGameTime() - plant.clock();
            if (gap < PERIOD) continue;
            long periods = Math.min(gap / PERIOD, catchUpPeriods());
            attachment.set(pos, advance(level, pos, growth, plant.age(), periods), plant.clock() + periods * PERIOD);
            moved = true;
        }
        if (moved) chunk.markUnsaved();
    }

    // The one reader of a row: settles whatever time it owes and answers the age. A row that does not exist yet is
    // a naturally generated plant, and this is where its age gets rolled and written down.
    private static double resolve(ServerLevel level, BlockPos pos, Growth growth) {
        HerbChunkAttachment attachment = attachment(level, pos);
        HerbChunkAttachment.Plant plant = attachment.find(pos).orElse(null);
        if (plant == null) {
            double rolled = wildAge(level, pos, growth);
            // The roll is parked in the age field and the clock's sign alone marks the row unsettled. Encoding the
            // roll in the clock's magnitude would truncate a fractional provider and make the first read disagree
            // with the second, which is the same non-idempotence the age field exists to prevent.
            attachment.set(pos, rolled, -1L);
            write(level, pos);
            return rolled;
        }
        if (plant.clock() < 0L) {
            // The parked roll becomes the starting age, dated to now, so the plant grows on from where it was
            // rolled instead of restarting at zero.
            double rolled = Math.min(plant.age(), growth.maxAge());
            attachment.set(pos, rolled, level.getGameTime());
            write(level, pos);
            return rolled;
        }
        long gap = level.getGameTime() - plant.clock();
        long periods = Math.min(Math.max(0L, gap) / PERIOD, catchUpPeriods());
        if (periods <= 0L) return plant.age();
        double age = advance(level, pos, growth, plant.age(), periods);
        attachment.set(pos, age, plant.clock() + periods * PERIOD);
        write(level, pos);
        return age;
    }

    // Grows one plant by a number of periods, from the age it already has, and returns where it ends up. The age
    // carried in is the state, so the same span always lands on the same answer however often it is walked.
    private static double advance(ServerLevel level, BlockPos pos, Growth growth, double age, long periods) {
        double value = age;
        if (value >= growth.maxAge()) return value;
        double bonus = AuraService.getPositionAura(level, pos).rules().spiritPlantBonus();
        // Every period but the last is walked without the level: they are time already served, and only the final
        // one is a live settlement that tests the environment and draws the aura cost.
        for (long settled = 0L; settled < periods - 1L && value < growth.maxAge(); settled++)
            value = grow(level, pos, growth, bonus, value);
        if (value < growth.maxAge()) value = period(level, pos, growth, bonus, value);
        return value;
    }

    // The wild roll: what a plant that was never placed starts at. Evaluated against the position's own aura, then
    // walked forward from game time 0 so a plant in a rich spot has had the whole world's time to grow.
    private static double wildAge(ServerLevel level, BlockPos pos, Growth growth) {
        double rolled = growth.wildAge().evaluate(FormulaContext.of(level));
        if (!Double.isFinite(rolled) || rolled <= 0.0D) return 0.0D;
        double start = Math.min(rolled, growth.maxAge());
        return advance(level, pos, growth, start, Math.min(level.getGameTime() / PERIOD, catchUpPeriods()));
    }

    // Advances one period on a live level: the environment is tested and the aura cost is drawn.
    private static double period(ServerLevel level, BlockPos pos, Growth growth, double bonus, double age) {
        FormulaContext formula = FormulaContext.of(level).with(SpiritHerbService.HERB_AGE, age);
        if (!growth.condition().test(level, pos, formula)) return age;
        double amount = SpiritHerbService.appliedGrowth(growth.growthRate(), formula, bonus);
        CostPayment.pay(growth.costs(), CostContext.pool(null, level, pos, formula, CostOrigin.HERB_GROWTH));
        return Math.min(growth.maxAge(), age + amount);
    }

    // One period with no level to test against and no aura to draw: growth rate alone. This is what a chunk that
    // was not ticking can still be charged for.
    private static double grow(ServerLevel level, BlockPos pos, Growth growth, double bonus, double age) {
        FormulaContext formula = FormulaContext.of(level).with(SpiritHerbService.HERB_AGE, age);
        return Math.min(growth.maxAge(), age + SpiritHerbService.appliedGrowth(growth.growthRate(), formula, bonus));
    }

    private static void forget(ServerLevel level, BlockPos pos) {
        HerbChunkAttachment attachment = existing(level, pos);
        if (attachment == null) return;
        if (attachment.remove(pos)) write(level, pos);
    }

    private static void write(ServerLevel level, BlockPos pos) {
        chunk(level, pos).markUnsaved();
    }

    private static HerbChunkAttachment existing(ServerLevel level, BlockPos pos) {
        return chunk(level, pos).getExistingDataOrNull(MxtAttachments.HERB_CHUNK.get());
    }

    private static HerbChunkAttachment attachment(ServerLevel level, BlockPos pos) {
        return chunk(level, pos).getData(MxtAttachments.HERB_CHUNK);
    }

    private static LevelChunk chunk(ServerLevel level, BlockPos pos) {
        return level.getChunkAt(pos);
    }

    private static long catchUpPeriods() {
        return Math.max(PERIOD, MxtServerConfig.INSTANCE.lifespan.ticksPerYear.getValue()) / PERIOD;
    }

    // The roll of one item entity, placed the way the block drop path places its own.
    public static ItemEntity item(ServerLevel level, BlockPos pos, ItemStack stack) {
        RandomSource random = level.getRandom();
        double halfHeight = EntityType.ITEM.getHeight() / 2.0D;
        return new ItemEntity(level, pos.getX() + 0.5D + Mth.nextDouble(random, -0.25D, 0.25D),
                pos.getY() + 0.5D + Mth.nextDouble(random, -0.25D, 0.25D) - halfHeight,
                pos.getZ() + 0.5D + Mth.nextDouble(random, -0.25D, 0.25D), stack);
    }
}
