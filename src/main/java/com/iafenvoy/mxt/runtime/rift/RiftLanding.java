package com.iafenvoy.mxt.runtime.rift;

import com.iafenvoy.mxt.data.rift.RiftPlacement;
import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.FailureSource;
import com.iafenvoy.mxt.runtime.resulting.FailureSources;
import com.iafenvoy.mxt.runtime.resulting.Result;
import com.iafenvoy.mxt.util.profiling.LoggerDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public final class RiftLanding {
    private static final LoggerDispatcher PLATFORMS = LoggerDispatcher.of(LogUtils.getLogger());
    private RiftLanding() { }

    public static @Nullable Vec3 find(ServerLevel level, Entity entity, Vec3 target, boolean nearby) {
        Inspection result = search(level, entity, target, nearby);
        return result.isSuccess() ? result.position() : null;
    }

    public static Inspection search(ServerLevel level, Entity entity, Vec3 target, boolean nearby) {
        return search(level, entity, target, nearby, _ -> true);
    }

    public static Inspection search(ServerLevel level, Entity entity, Vec3 target, boolean nearby, Predicate<Vec3> allowed) {
        return candidates(target, nearby, pos -> {
            Inspection check = inspect(level, entity, pos);
            return check.isSuccess() && !allowed.test(pos) ? new Inspection(Reason.LANDING_PORTAL_CONTACT, pos, null) : check;
        });
    }

    public static Inspection resolve(ServerLevel level, Entity entity, Vec3 target, boolean nearby) {
        return resolve(level, entity, target, nearby, _ -> true);
    }

    public static Inspection resolve(ServerLevel level, Entity entity, Vec3 target, boolean nearby, Predicate<Vec3> allowed) {
        Inspection found = search(level, entity, target, nearby, allowed);
        if (found.isSuccess() || found.failure() == Reason.LANDING_NON_FINITE || found.failure() == Reason.LANDING_ENTITY_SIZE
                || found.failure() == Reason.LANDING_OUT_OF_BOUNDS) return found;
        Inspection built = candidates(target, nearby, pos -> platform(level, entity, pos, allowed));
        return built.isSuccess() ? built : found;
    }

    private static Inspection candidates(Vec3 target, boolean nearby, Function<Vec3, Inspection> attempt) {
        Inspection first = attempt.apply(target);
        if (first.isSuccess()) return first;
        if (!nearby || first.failure() == Reason.LANDING_NON_FINITE || first.failure() == Reason.LANDING_ENTITY_SIZE) return first;
        for (int radius = 1; radius <= 4; radius++)
            for (int y = -radius; y <= radius; y++)
                for (int x = -radius; x <= radius; x++)
                    for (int z = -radius; z <= radius; z++) {
                        if (Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) != radius) continue;
                        Vec3 candidate = target.add(x, y, z);
                        Inspection check = attempt.apply(candidate);
                        if (check.isSuccess()) return check;
                    }
        return first;
    }

    private static Inspection platform(ServerLevel level, Entity entity, Vec3 requested, Predicate<Vec3> allowed) {
        // Full dirt blocks need an integral foot height and one layer above the world floor.
        Vec3 pos = new Vec3(requested.x, Math.max(Math.ceil(requested.y), level.getMinY() + 1), requested.z);
        Inspection check = inspect(level, entity, pos);
        if (!allowed.test(pos)) return new Inspection(Reason.LANDING_PORTAL_CONTACT, pos, null);
        if (check.failure() != Reason.LANDING_NO_SUPPORT) return check;
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
            Inspection result = inspect(level, entity, pos);
            ready = result.isSuccess();
            if (ready) PLATFORMS.log("Created dirt landing platform: entity={} dimension={} requested={} landing={} floor={} blocks={}",
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
        Inspection check = inspect(level, entity, target);
        if (check.failure() == Reason.LANDING_NON_FINITE || check.failure() == Reason.LANDING_ENTITY_SIZE
                || check.failure() == Reason.LANDING_OUT_OF_BOUNDS) return;
        AABB box = area(entity, target, nearby);
        for (int x = (int) Math.floor(box.minX / 16); x <= (int) Math.floor(box.maxX / 16); x++)
            for (int z = (int) Math.floor(box.minZ / 16); z <= (int) Math.floor(box.maxZ / 16); z++)
                level.getChunk(x, z);
    }

    public static boolean safe(ServerLevel level, Entity entity, Vec3 pos) {
        return inspect(level, entity, pos).isSuccess();
    }

    public static Inspection inspect(ServerLevel level, Entity entity, Vec3 pos) {
        if (!RiftPlacement.finite(pos)) return new Inspection(Reason.LANDING_NON_FINITE, pos, null);
        AABB box = entity.getBoundingBox().move(pos.subtract(entity.position())).deflate(1e-6);
        if (box.minY < level.getMinY() || box.maxY > level.getMaxY() || !level.getWorldBorder().isWithinBounds(box)) return new Inspection(Reason.LANDING_OUT_OF_BOUNDS, pos, null);
        if (box.getXsize() > 16 || box.getYsize() > 32 || box.getZsize() > 16) return new Inspection(Reason.LANDING_ENTITY_SIZE, pos, null);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY - 0.063, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (!level.hasChunkAt(p)) return new Inspection(Reason.LANDING_CHUNK_UNLOADED, pos, p.immutable());
            BlockState state = level.getBlockState(p);
            if (!level.getFluidState(p).isEmpty()) return new Inspection(Reason.LANDING_FLUID, pos, p.immutable());
            if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                    || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE)
                    || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)) return new Inspection(Reason.LANDING_HAZARD, pos, p.immutable());
        }
        if (!level.noCollision(entity, box)) return new Inspection(Reason.LANDING_BLOCKED, pos, null);
        boolean support = entity instanceof LivingEntity living && !living.isFallFlying() && !(entity instanceof Mob mob && mob.getMoveControl() instanceof FlyingMoveControl)
                && !entity.isNoGravity() && !(entity instanceof ServerPlayer player && player.getAbilities().flying);
        if (support && !level.getBlockCollisions(entity, new AABB(box.minX, box.minY - 0.0625, box.minZ,
                box.maxX, box.minY, box.maxZ)).iterator().hasNext()) return new Inspection(Reason.LANDING_NO_SUPPORT, pos, null);
        return new Inspection(null, pos, null);
    }
    public enum Reason implements Failure {
        LANDING_NON_FINITE, LANDING_OUT_OF_BOUNDS, LANDING_ENTITY_SIZE, LANDING_CHUNK_UNLOADED,
        LANDING_FLUID, LANDING_HAZARD, LANDING_BLOCKED, LANDING_NO_SUPPORT, LANDING_PORTAL_CONTACT;

        @Override
        public FailureSource source() { return FailureSources.SECRET_REALM; }
    }

    public static final class Inspection extends Result {
        private final Vec3 position;
        private final @Nullable BlockPos block;

        private Inspection(@Nullable Reason failure, Vec3 position, @Nullable BlockPos block) {
            super(failure, null);
            this.position = position;
            this.block = block;
        }

        public Vec3 position() { return this.position; }
        public @Nullable BlockPos block() { return this.block; }
        @Override
        public String toString() {
            return "Landing[reason=" + (this.failure() == null ? "ok" : this.failure().getSerializedName())
                    + ", position=" + this.position + ", block=" + this.block + "]";
        }
    }
}
