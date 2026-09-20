package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.realm.RealmInstance;
import com.iafenvoy.mxt.data.realm.RealmInstance.Border;
import com.iafenvoy.mxt.data.realm.RealmInstance.EntryPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Decides where travellers arrive.
 *
 * <p>A definition lists weighted landing options: one with a {@code pos} is a fixed gate, one without is a
 * random spot inside {@code random_center}/{@code random_radius}. Whichever is picked becomes the instance
 * anchor, so later visitors reach the same place instead of scattering across the realm, and arrivals in the
 * same visit are spread around that anchor.
 */
public final class RealmEntryLocator {
    private static final double DEFAULT_RADIUS = 64.0D;

    private RealmEntryLocator() {
    }

    /**
     * The planned landing of a fresh instance. The height is a first guess from the terrain as it is before
     * the realm is furnished; {@link #finish} corrects it once structures are in place.
     */
    public static Landing plan(ServerLevel level, RealmRecord record) {
        RealmInstance definition = record.instance();
        RandomSource random = RandomSource.create(record.seed());
        EntryPoint point = definition.pickEntry(random);
        Vec3 position = point != null && point.pos().isPresent()
                ? point.pos().get()
                : randomPoint(level, definition, point, random);
        return new Landing(point, position);
    }

    /**
     * The final anchor. An explicit y is honoured, a random landing is dropped onto whatever now occupies the
     * chosen column - the structures that were just placed included.
     */
    public static Vec3 finish(ServerLevel level, Landing landing) {
        if (landing.point() != null && landing.point().pos().isPresent()) return landing.position();
        return new Vec3(landing.position().x, surface(level, landing.position().x, landing.position().z), landing.position().z);
    }

    /**
     * Where one member of a visit lands, and which way they face.
     */
    public static Arrival arrival(ServerLevel level, RealmRecord record, int slot, float fallbackYaw, float fallbackPitch) {
        Vec3 anchor = record.anchor().orElseGet(() -> new Vec3(0.5D, 0.0D, 0.5D));
        RandomSource random = RandomSource.create(record.seed());
        EntryPoint point = record.instance().pickEntry(random);
        double spread = record.instance().entry().stream().mapToDouble(EntryPoint::spread).max().orElse(0.0D);
        Vec3 position = anchor;
        if (slot > 0 && spread > 0.0D) {
            double angle = Math.PI * 2.0D / 6.0D * slot;
            double x = anchor.x + Math.cos(angle) * spread;
            double z = anchor.z + Math.sin(angle) * spread;
            position = new Vec3(x, surface(level, x, z), z);
        }
        float yaw = point == null ? fallbackYaw : point.yaw().orElse(fallbackYaw);
        float pitch = point == null ? fallbackPitch : point.pitch().orElse(fallbackPitch);
        return new Arrival(position, yaw, pitch);
    }

    private static Vec3 randomPoint(ServerLevel level, RealmInstance definition, @Nullable EntryPoint point, RandomSource random) {
        Border border = definition.effectiveBorder();
        Vec2 center = point == null ? null : point.randomCenter().orElse(null);
        double centerX = center == null ? border.center().x : center.x;
        double centerZ = center == null ? border.center().y : center.y;
        double radius = point == null ? 0.0D : point.randomRadius().orElse(0.0D);
        if (radius <= 0.0D) radius = definition.border().isPresent() ? border.size() * 0.4D : DEFAULT_RADIUS;
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = Math.sqrt(random.nextDouble()) * radius;
        double x = centerX + Math.cos(angle) * distance;
        double z = centerZ + Math.sin(angle) * distance;
        return new Vec3(x, surface(level, x, z), z);
    }

    /**
     * The height of the first blocking block, or the bottom of the world for a realm with no floor at all.
     */
    public static double surface(ServerLevel level, double x, double z) {
        BlockPos column = new BlockPos(Mth.floor(x), level.getMinY(), Mth.floor(z));
        return level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
    }

    public record Landing(@Nullable EntryPoint point, Vec3 position) {
    }

    public record Arrival(Vec3 position, float yaw, float pitch) {
    }
}
