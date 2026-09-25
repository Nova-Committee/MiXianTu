package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.NoOpAction;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * The vehicle an artifact declares: how fast it flies, how many it carries, whether they sit, how it is drawn, how
 * big it is, what it does while a flight lasts and what it leaves behind. Pure data - the press that starts a flight
 * belongs to the rider's own {@code mxt:flight_control} skill, which is what keeps "may I fly" and "what am I flying"
 * two separate declarations.
 *
 * <p>Never activated: only these fields are read, so the ability fields that describe an activation
 * ({@code entity_action}, {@code modifiers}, a cast time) do nothing here.
 */
public record MountAbilityType(NumberProvider speed, int seats, boolean sit, FlightDisplay display,
                               double width, double height, double stepHeight, List<Vec3> seatOffsets,
                               MountActions actions, Optional<MountTrail> trail)
        implements AbilityType {
    public static final int MAX_SEATS = 4;
    // What the vehicle type is registered with: the box and the ride height this had before either was data.
    public static final double DEFAULT_WIDTH = 0.35D;
    public static final double DEFAULT_HEIGHT = 0.12D;
    public static final double DEFAULT_SEAT_HEIGHT = 0.65D;
    public static final double DEFAULT_SEAT_SPACING = 0.8D;
    // NaN and infinity fall out of a range check, which is why the bounds are not written as a validator.
    private static final Codec<Double> POSITIVE = Codec.doubleRange(Double.MIN_VALUE, Double.MAX_VALUE);
    private static final MapCodec<MountAbilityType> RAW_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("speed").forGetter(MountAbilityType::speed),
            Codec.intRange(1, MAX_SEATS).optionalFieldOf("seats", 1).forGetter(MountAbilityType::seats),
            Codec.BOOL.optionalFieldOf("sit", false).forGetter(MountAbilityType::sit),
            FlightDisplay.CODEC.optionalFieldOf("display", FlightDisplay.DEFAULT).forGetter(MountAbilityType::display),
            POSITIVE.optionalFieldOf("width", DEFAULT_WIDTH).forGetter(MountAbilityType::width),
            POSITIVE.optionalFieldOf("height", DEFAULT_HEIGHT).forGetter(MountAbilityType::height),
            MiscCodecs.NON_NEGATIVE.optionalFieldOf("step_height", 0.0D).forGetter(MountAbilityType::stepHeight),
            Vec3.CODEC.listOf().optionalFieldOf("seat_offsets", List.of()).forGetter(MountAbilityType::seatOffsets),
            MountActions.CODEC.optionalFieldOf("mount_action", MountActions.NONE).forGetter(MountAbilityType::actions),
            MountTrail.CODEC.optionalFieldOf("trail").forGetter(MountAbilityType::trail)
    ).apply(i, MountAbilityType::new));
    public static final MapCodec<MountAbilityType> CODEC = RAW_CODEC.validate(MountAbilityType::validate);

    private static DataResult<MountAbilityType> validate(MountAbilityType mount) {
        if (mount.seatOffsets().size() > MAX_SEATS)
            return DataResult.error(() -> "seat_offsets names more than " + MAX_SEATS + " seats");
        return mount.seatOffsets().stream().allMatch(MountAbilityType::isFinite)
                ? DataResult.success(mount)
                : DataResult.error(() -> "Seat offsets must be finite");
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    @Override
    public MapCodec<MountAbilityType> codec() {
        return CODEC;
    }

    // Where one seat's feet land, in blocks from the mount's origin. Written offsets win and the last one is reused
    // for any seat past the list; without any, seat 0 keeps the registered ride height and the rest sit behind it.
    public Vec3 seatOffset(int index) {
        if (!this.seatOffsets.isEmpty())
            return this.seatOffsets.get(Math.min(index, this.seatOffsets.size() - 1));
        return new Vec3(0.0D, DEFAULT_SEAT_HEIGHT, -DEFAULT_SEAT_SPACING * index);
    }

    // A mount that cannot move is a pack mistake, not a vehicle that happens to be still; the rider's own multiplier
    // is applied by the caller.
    public boolean canMove(double speed) {
        return Double.isFinite(speed) && speed > 0.0D;
    }

    /**
     * What a mount does by itself: the moment a flight starts, the moment it ends, and every tick in between. All three
     * run on the driver - the vehicle is a plain entity that an effect or a resource action has nothing to say to.
     */
    public record MountActions(EntityAction onMount, EntityAction onDismount, EntityAction tick) {
        public static final MountActions NONE = new MountActions(NoOpAction.INSTANCE, NoOpAction.INSTANCE, NoOpAction.INSTANCE);
        private static final MapCodec<MountActions> RAW_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                EntityAction.optionalCodec("on_mount").forGetter(MountActions::onMount),
                EntityAction.optionalCodec("on_dismount").forGetter(MountActions::onDismount),
                EntityAction.optionalCodec("tick").forGetter(MountActions::tick)
        ).apply(i, MountActions::new));
        // A field of this shape is an object under one key, not a type-dispatched entry.
        public static final Codec<MountActions> CODEC = RAW_CODEC.codec();
    }

    /**
     * The particles a mount leaves behind, emitted from the mount itself every {@code interval} ticks. Spread and offset
     * are in blocks, unlike {@code mxt:spawn_particles}, which scales the spread by the actor's own size.
     */
    public record MountTrail(ParticleOptions particle, int interval, int count, float speed, Vec3 spread,
                             float offsetX, float offsetY, float offsetZ, boolean movingOnly) {
        public static final Vec3 DEFAULT_SPREAD = new Vec3(0.2D, 0.1D, 0.2D);
        public static final float DEFAULT_OFFSET_Y = 0.1F;
        private static final MapCodec<MountTrail> RAW_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ParticleTypes.CODEC.fieldOf("particle").forGetter(MountTrail::particle),
                Codec.intRange(1, 200).optionalFieldOf("interval", 1).forGetter(MountTrail::interval),
                Codec.intRange(0, 256).optionalFieldOf("count", 1).forGetter(MountTrail::count),
                Codec.FLOAT.optionalFieldOf("speed", 0.0F).forGetter(MountTrail::speed),
                Vec3.CODEC.optionalFieldOf("spread", DEFAULT_SPREAD).forGetter(MountTrail::spread),
                Codec.FLOAT.optionalFieldOf("offset_x", 0.0F).forGetter(MountTrail::offsetX),
                Codec.FLOAT.optionalFieldOf("offset_y", DEFAULT_OFFSET_Y).forGetter(MountTrail::offsetY),
                Codec.FLOAT.optionalFieldOf("offset_z", 0.0F).forGetter(MountTrail::offsetZ),
                Codec.BOOL.optionalFieldOf("moving_only", false).forGetter(MountTrail::movingOnly)
        ).apply(i, MountTrail::new));
        // A field of this shape is an object under one key, not a type-dispatched entry.
        public static final Codec<MountTrail> CODEC = RAW_CODEC.validate(MountTrail::validate).codec();

        private static DataResult<MountTrail> validate(MountTrail trail) {
            boolean finite = Double.isFinite(trail.spread().x) && Double.isFinite(trail.spread().y) && Double.isFinite(trail.spread().z)
                    && Float.isFinite(trail.offsetX()) && Float.isFinite(trail.offsetY()) && Float.isFinite(trail.offsetZ())
                    && Float.isFinite(trail.speed());
            return finite ? DataResult.success(trail) : DataResult.error(() -> "Trail speed, spread and offset must be finite");
        }

        // One call for the whole level: whether a given client draws it stays the client's own distance rule.
        public void emit(Entity mount) {
            if (!(mount.level() instanceof ServerLevel level)) return;
            Vec3 at = mount.position().add(this.offsetX, this.offsetY, this.offsetZ);
            level.sendParticles(this.particle, at.x, at.y, at.z, this.count, this.spread.x, this.spread.y, this.spread.z, this.speed);
        }
    }
}
