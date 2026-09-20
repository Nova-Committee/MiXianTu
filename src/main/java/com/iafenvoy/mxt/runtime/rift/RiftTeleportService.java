package com.iafenvoy.mxt.runtime.rift;

import com.iafenvoy.mxt.item.block.entity.RiftBlockEntity;
import com.iafenvoy.mxt.registry.MxtBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.portal.TeleportTransition.PostTeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Moves an entity through a rift.
 *
 * <p>Arriving looks for a rift that already leads back where the traveller came from, and only carves a new one
 * when there is none. That keeps the two ends of a journey paired, so walking back through the rift a player
 * arrived at returns them to where they started instead of to a fresh, unrelated spot.
 */
public final class RiftTeleportService {
    /**
     * How far around the scaled arrival position an existing return rift is looked for.
     */
    public static final int ARRIVAL_HORIZONTAL_RADIUS = 16;
    public static final int ARRIVAL_VERTICAL_RADIUS = 8;
    /**
     * Falling grace after arriving, since the far side is often mid-air or solid rock.
     */
    private static final int SLOW_FALLING_TICKS = 600;
    private static final PostTeleportTransition AFTER_ARRIVAL = RiftTeleportService::afterArrival;

    private RiftTeleportService() {
    }

    /**
     * The transition a rift leads to, or {@code null} when the target dimension is not loaded.
     */
    @Nullable
    public static TeleportTransition destination(ServerLevel level, RiftBlockEntity rift, Entity entity, BlockPos entry) {
        ServerLevel target = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, rift.target()));
        if (target == null) return null;
        Vec3 scaled = scale(Vec3.atCenterOf(entry), level.dimensionType(), target.dimensionType());
        BlockPos clamped = target.getWorldBorder().clampToBounds(scaled);
        return new TeleportTransition(target, arrival(target, level.dimension(), clamped), Vec3.ZERO,
                entity.getYRot(), entity.getXRot(), AFTER_ARRIVAL);
    }

    /**
     * Where an entity arriving in {@code target} from {@code back} should be put down.
     */
    public static Vec3 arrival(ServerLevel target, ResourceKey<Level> back, BlockPos around) {
        BlockPos rift = findReturnRift(target, back, around);
        if (rift == null) rift = carveReturnRift(target, back, around);
        return standingSpot(target, rift);
    }

    /**
     * Cross-dimension position mapping: the vertical axis is mapped by relative height, the horizontal axes by
     * the vanilla teleportation scale, which is what makes an eight-to-one pair of dimensions line up.
     */
    static Vec3 scale(Vec3 position, DimensionType from, DimensionType to) {
        double factor = Mth.clamp((position.y - from.minY()) / from.height(), 0.0, 1.0);
        double horizontal = DimensionType.getTeleportationScale(from, to);
        return new Vec3(position.x * horizontal, to.minY() + to.height() * factor, position.z * horizontal);
    }

    /**
     * The closest rift in range that already leads back to {@code back}, or {@code null}.
     *
     * <p>Unloaded chunks are skipped rather than queried: reading a block entity from one would generate it, and
     * a search box of this size would then pull in hundreds of chunks just to place one traveller.
     */
    @Nullable
    private static BlockPos findReturnRift(ServerLevel target, ResourceKey<Level> back, BlockPos around) {
        BlockPos min = around.offset(-ARRIVAL_HORIZONTAL_RADIUS, -ARRIVAL_VERTICAL_RADIUS, -ARRIVAL_HORIZONTAL_RADIUS);
        BlockPos max = around.offset(ARRIVAL_HORIZONTAL_RADIUS, ARRIVAL_VERTICAL_RADIUS, ARRIVAL_HORIZONTAL_RADIUS);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!target.isLoaded(pos)) continue;
            if (!(target.getBlockEntity(pos) instanceof RiftBlockEntity rift)) continue;
            if (!rift.target().equals(back.identifier())) continue;
            double distance = pos.distSqr(around);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    /**
     * Opens a rift that leads back to where the traveller came from, so the next trip returns to this spot. The
     * block is carved at the arrival position when that position can hold it, and just short of it otherwise.
     */
    private static BlockPos carveReturnRift(ServerLevel target, ResourceKey<Level> back, BlockPos around) {
        BlockPos pos = around;
        for (int attempt = 0; attempt < 4 && !canHoldRift(target, pos); attempt++) pos = pos.above();
        if (!canHoldRift(target, pos)) return around;
        target.setBlock(pos, MxtBlocks.RIFT.get().defaultBlockState(), Block.UPDATE_ALL);
        if (target.getBlockEntity(pos) instanceof RiftBlockEntity rift)
            rift.configure(back.identifier(), RiftColors.AUTO);
        return pos;
    }

    private static boolean canHoldRift(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return false;
        return level.getBlockState(pos).canBeReplaced();
    }

    /**
     * A spot to stand in near the rift: the four blocks beside it first, then above it, and only then the rift
     * itself. Landing beside the rift rather than in it keeps the traveller out of the return portal they just
     * arrived at, and out of the block the rift was carved into. Falls back to the rift position, where the slow
     * falling this service hands out takes over. Unloaded chunks are skipped for the same reason the rift search
     * skips them.
     */
    private static Vec3 standingSpot(ServerLevel level, BlockPos rift) {
        for (BlockPos candidate : new BlockPos[]{rift.north(), rift.south(), rift.east(), rift.west(),
                rift.above(), rift.above(2), rift})
            if (level.isLoaded(candidate) && isStandable(level, candidate)) return Vec3.atBottomCenterOf(candidate);
        return Vec3.atBottomCenterOf(rift);
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return false;
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
        if (!level.getFluidState(pos).isEmpty()) return false;
        return !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    /**
     * Cooldown first: arriving inside a return rift must not bounce the traveller straight back.
     */
    private static void afterArrival(Entity entity) {
        entity.setPortalCooldown();
        if (entity instanceof LivingEntity living)
            living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALLING_TICKS, 1, false, true));
        TeleportTransition.PLAY_PORTAL_SOUND.onTransition(entity);
    }
}
