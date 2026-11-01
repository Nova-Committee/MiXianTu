package com.iafenvoy.mxt.runtime.rift;

import com.iafenvoy.mxt.data.rift.RiftPlacement;
import com.iafenvoy.mxt.runtime.resulting.FailureSources;
import com.iafenvoy.saturn.profiling.LoggerDispatcher;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.FailureSource;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public final class RiftLanding {
    private static final LoggerDispatcher PLATFORMS = LoggerDispatcher.of(LogUtils.getLogger());

    private RiftLanding() {
    }

    public static @Nullable Vec3 find(ServerLevel level, Entity entity, Vec3 target, boolean nearby) {
        return search(level, entity, target, nearby).getData().map(Inspection::position).orElse(null);
    }

    public static Result<Inspection> search(ServerLevel level, Entity entity, Vec3 target, boolean nearby) {
        return search(level, entity, target, nearby, _ -> true);
    }

    public static Result<Inspection> search(ServerLevel level, Entity entity, Vec3 target, boolean nearby, Predicate<Vec3> allowed) {
        return candidates(target, nearby, pos -> {
            Result<Inspection> check = inspect(level, entity, pos);
            return check instanceof Result.SuccessResult<Inspection> && !allowed.test(pos)
                    ? Result.failure(Reason.LANDING_PORTAL_CONTACT) : check;
        });
    }

    public static Result<Inspection> resolve(ServerLevel level, Entity entity, Vec3 target, boolean nearby) {
        return resolve(level, entity, target, nearby, _ -> true);
    }

    public static Result<Inspection> resolve(ServerLevel level, Entity entity, Vec3 target, boolean nearby, Predicate<Vec3> allowed) {
        Result<Inspection> found = search(level, entity, target, nearby, allowed);
        Failure foundFailure = found.getFailure().orElse(null);
        if (found instanceof Result.SuccessResult<Inspection> || foundFailure == Reason.LANDING_NON_FINITE
                || foundFailure == Reason.LANDING_ENTITY_SIZE || foundFailure == Reason.LANDING_OUT_OF_BOUNDS)
            return found;
        Result<Inspection> built = candidates(target, nearby, pos -> platform(level, entity, pos, allowed));
        return built instanceof Result.SuccessResult<Inspection> ? built : found;
    }

    private static Result<Inspection> candidates(Vec3 target, boolean nearby, Function<Vec3, Result<Inspection>> attempt) {
        Result<Inspection> first = attempt.apply(target);
        if (first instanceof Result.SuccessResult<Inspection>) return first;
        Failure firstFailure = first.getFailure().orElse(null);
        if (!nearby || firstFailure == Reason.LANDING_NON_FINITE || firstFailure == Reason.LANDING_ENTITY_SIZE)
            return first;
        for (int radius = 1; radius <= 4; radius++)
            for (int y = -radius; y <= radius; y++)
                for (int x = -radius; x <= radius; x++)
                    for (int z = -radius; z <= radius; z++) {
                        if (Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) != radius) continue;
                        Vec3 candidate = target.add(x, y, z);
                        Result<Inspection> check = attempt.apply(candidate);
                        if (check instanceof Result.SuccessResult<Inspection>) return check;
                    }
        return first;
    }

    private static Result<Inspection> platform(ServerLevel level, Entity entity, Vec3 requested, Predicate<Vec3> allowed) {
        // Full dirt blocks need an integral foot height and one layer above the world floor.
        Vec3 pos = new Vec3(requested.x, Math.max(Math.ceil(requested.y), level.getMinY() + 1), requested.z);
        Result<Inspection> check = inspect(level, entity, pos);
        if (!allowed.test(pos)) return Result.failure(Reason.LANDING_PORTAL_CONTACT);
        if (check.getFailure().orElse(null) != Reason.LANDING_NO_SUPPORT) return check;
        AABB body = entity.getBoundingBox().move(pos.subtract(entity.position())).deflate(1e-6);
        int minX = (int) Math.floor(body.minX) - 1, maxX = (int) Math.floor(body.maxX) + 1;
        int minZ = (int) Math.floor(body.minZ) - 1, maxZ = (int) Math.floor(body.maxZ) + 1;
        int floorY = (int) pos.y - 1;
        AABB floor = new AABB(minX, floorY, minZ, maxX + 1, pos.y, maxZ + 1);
        if (!level.getWorldBorder().isWithinBounds(floor) || !level.getEntities(entity, floor).isEmpty()) return check;
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(minX, floorY, minZ, maxX, floorY, maxZ)) {
            if (!level.hasChunkAt(p)) return check;
            BlockState state = level.getBlockState(p);
            if (!state.isAir() || !level.getFluidState(p).isEmpty()) return check;
            previous.put(p.immutable(), state);
        }
        boolean ready = false;
        List<BlockPos> placed = new ArrayList<>();
        try {
            for (Map.Entry<BlockPos, BlockState> entry : previous.entrySet()) {
                if (!level.getBlockState(entry.getKey()).equals(entry.getValue())) return check;
                placed.add(entry.getKey());
                if (!level.setBlock(entry.getKey(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL)) return check;
            }
            Result<Inspection> result = inspect(level, entity, pos);
            ready = result instanceof Result.SuccessResult<Inspection>;
            if (ready)
                PLATFORMS.log("Created dirt landing platform: entity={} dimension={} requested={} landing={} floor={} blocks={}",
                        entity.getUUID(), level.dimension().identifier(), requested, pos, floor, previous.size());
            return result;
        } finally {
            if (!ready) for (BlockPos p : placed)
                if (level.getBlockState(p).is(Blocks.DIRT)) level.setBlock(p, previous.get(p), Block.UPDATE_ALL);
        }
    }

    public static AABB area(Entity entity, Vec3 target, boolean nearby) {
        return entity.getBoundingBox().move(target.subtract(entity.position())).inflate(nearby ? 5 : 1);
    }

    // Realm preparation is already synchronous; callers explicitly load this bounded arrival area before checking it.
    public static void loadArea(ServerLevel level, Entity entity, Vec3 target, boolean nearby) {
        Result<Inspection> check = inspect(level, entity, target);
        Failure reason = check.getFailure().orElse(null);
        if (reason == Reason.LANDING_NON_FINITE || reason == Reason.LANDING_ENTITY_SIZE
                || reason == Reason.LANDING_OUT_OF_BOUNDS) return;
        AABB box = area(entity, target, nearby);
        for (int x = (int) Math.floor(box.minX / 16); x <= (int) Math.floor(box.maxX / 16); x++)
            for (int z = (int) Math.floor(box.minZ / 16); z <= (int) Math.floor(box.maxZ / 16); z++)
                level.getChunk(x, z);
    }

    public static boolean safe(ServerLevel level, Entity entity, Vec3 pos) {
        return inspect(level, entity, pos) instanceof Result.SuccessResult<Inspection>;
    }

    public static Result<Inspection> inspect(ServerLevel level, Entity entity, Vec3 pos) {
        if (!RiftPlacement.finite(pos)) return Result.failure(Reason.LANDING_NON_FINITE);
        AABB box = entity.getBoundingBox().move(pos.subtract(entity.position())).deflate(1e-6);
        if (box.minY < level.getMinY() || box.maxY > level.getMaxY() || !level.getWorldBorder().isWithinBounds(box))
            return Result.failure(Reason.LANDING_OUT_OF_BOUNDS);
        if (box.getXsize() > 16 || box.getYsize() > 32 || box.getZsize() > 16)
            return Result.failure(Reason.LANDING_ENTITY_SIZE);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY - 0.063, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (!level.hasChunkAt(p)) return Result.failure(Reason.LANDING_CHUNK_UNLOADED);
            BlockState state = level.getBlockState(p);
            if (!level.getFluidState(p).isEmpty()) return Result.failure(Reason.LANDING_FLUID);
            if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                    || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE)
                    || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW))
                return Result.failure(Reason.LANDING_HAZARD);
        }
        if (!level.noCollision(entity, box)) return Result.failure(Reason.LANDING_BLOCKED);
        boolean support = entity instanceof LivingEntity living && !living.isFallFlying() && !(entity instanceof Mob mob && mob.getMoveControl() instanceof FlyingMoveControl)
                && !entity.isNoGravity() && !(entity instanceof ServerPlayer player && player.getAbilities().flying);
        if (support && !level.getBlockCollisions(entity, new AABB(box.minX, box.minY - 0.0625, box.minZ,
                box.maxX, box.minY, box.maxZ)).iterator().hasNext())
            return Result.failure(Reason.LANDING_NO_SUPPORT);
        return Result.success(new Inspection(pos));
    }

    public enum Reason implements Failure {
        LANDING_NON_FINITE, LANDING_OUT_OF_BOUNDS, LANDING_ENTITY_SIZE, LANDING_CHUNK_UNLOADED,
        LANDING_FLUID, LANDING_HAZARD, LANDING_BLOCKED, LANDING_NO_SUPPORT, LANDING_PORTAL_CONTACT;

        @Override
        public FailureSource source() {
            return FailureSources.SECRET_REALM;
        }
    }

    // A safe landing: where the body would stand. The block a failed inspection looked at is not success data, so it
    // is read from the inspected position instead.
    public record Inspection(Vec3 position) {
    }
}
