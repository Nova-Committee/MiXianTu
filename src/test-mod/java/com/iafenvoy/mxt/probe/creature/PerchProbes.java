package com.iafenvoy.mxt.probe.creature;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.runtime.perch.PerchEventBridge;
import com.iafenvoy.mxt.runtime.perch.PerchService;
import com.iafenvoy.mxt.testmod.MxtTestEntities;
import com.iafenvoy.mxt.testmod.ProbeBeast;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The perch facility between throwaway creatures, which is the shape a content mod's pet would use: the seat is
 * what {@code positionRider} computes, and the release policy is driven by handing the bridge the event the game
 * hands it.
 */
public final class PerchProbes {
    private PerchProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("creature/perch/seat", PerchProbes::seat),
                Probe.of("creature/perch/height", PerchProbes::height),
                Probe.of("creature/perch/capacity", PerchProbes::capacity),
                Probe.of("creature/perch/policy", PerchProbes::policy),
                Probe.of("creature/perch/answer", PerchProbes::answer));
    }

    // A passenger nobody says anything about lands on the head, which is the baseline the audit recorded; a
    // declared offset replaces that seat and turns with the vehicle.
    private static void seat(ProbeContext context) {
        ServerLevel level = context.level();
        MxtServerConfig.Perch settings = MxtServerConfig.INSTANCE.perch;
        int wasMax = settings.maxPerches.getValue();
        boolean wasSneak = settings.dropWhenSneaking.getValue();
        boolean wasFall = settings.dropOnFall.getValue();
        boolean wasPowder = settings.dropInPowderSnow.getValue();
        settings.maxPerches.setValue(2);
        ProbeBeast.reset();
        List<Entity> spawned = new ArrayList<>();
        BlockPos origin = context.player().blockPosition();
        // The two points a humanoid's shoulders are at, in the vehicle's own frame: x is the vehicle's left, z is
        // the way it faces, and y counts down from the vehicle's top.
        Vec3 left = new Vec3(0.5D, -0.4D, 0.0D);
        try {
            ProbeBeast vehicle = spawnProbeBeast(level, origin.above());
            vehicle.setYRot(0.0F);
            spawned.add(vehicle);
            ProbeBeast first = spawnProbeBeast(level, origin.above(3));
            spawned.add(first);
            double tall = vehicle.getDimensions(vehicle.getPose()).height();

            boolean ridden = first.startRiding(vehicle);
            vehicle.positionRider(first);
            boolean defaultSeat = ridden && close(first.getX(), vehicle.getX())
                    && close(first.getY(), vehicle.getY() + tall) && close(first.getZ(), vehicle.getZ());
            first.stopRiding();

            boolean seated = PerchService.perch(first, vehicle, left).changed();
            vehicle.positionRider(first);
            boolean declaredSeat = seated && PerchService.perchOffset(first).isPresent()
                    && close(first.getX(), vehicle.getX() + left.x)
                    && close(first.getY(), vehicle.getY() + tall + left.y)
                    && close(first.getZ(), vehicle.getZ());
            vehicle.setYRot(-90.0F);
            vehicle.positionRider(first);
            boolean turned = close(first.getX(), vehicle.getX())
                    && close(first.getY(), vehicle.getY() + tall + left.y)
                    && close(first.getZ(), vehicle.getZ() - left.x);
            vehicle.setYRot(0.0F);
            context.note("perch probe: default_seat=" + defaultSeat
                    + " declared_seat=" + declaredSeat + " turned=" + turned
                    + ((defaultSeat && declaredSeat && turned) ? " OK" : " MISMATCH"));
            context.check("perch seat: default_seat", defaultSeat);
            context.check("perch seat: declared_seat", declaredSeat);
            context.check("perch seat: turned", turned);
        } finally {
            settings.maxPerches.setValue(wasMax);
            settings.dropWhenSneaking.setValue(wasSneak);
            settings.dropOnFall.setValue(wasFall);
            settings.dropInPowderSnow.setValue(wasPowder);
            for (Entity entity : spawned) entity.discard();
        }
    }

    // y counts down from the vehicle's own top, so a shorter vehicle carries the same offset lower by exactly
    // its own height difference rather than by a number baked into the seat.
    private static void height(ProbeContext context) {
        ServerLevel level = context.level();
        MxtServerConfig.Perch settings = MxtServerConfig.INSTANCE.perch;
        int wasMax = settings.maxPerches.getValue();
        boolean wasSneak = settings.dropWhenSneaking.getValue();
        boolean wasFall = settings.dropOnFall.getValue();
        boolean wasPowder = settings.dropInPowderSnow.getValue();
        settings.maxPerches.setValue(2);
        ProbeBeast.reset();
        List<Entity> spawned = new ArrayList<>();
        BlockPos origin = context.player().blockPosition();
        Vec3 left = new Vec3(0.5D, -0.4D, 0.0D);
        try {
            ProbeBeast vehicle = spawnProbeBeast(level, origin.above());
            vehicle.setYRot(0.0F);
            spawned.add(vehicle);
            ProbeBeast first = spawnProbeBeast(level, origin.above(3));
            spawned.add(first);
            double tall = vehicle.getDimensions(vehicle.getPose()).height();
            PerchService.perch(first, vehicle, left);

            Chicken chicken = EntityType.CHICKEN.create(level, EntitySpawnReason.COMMAND);
            if (chicken == null) {
                context.fail("perch probe: could not create the shorter vehicle");
                return;
            }
            chicken.setNoAi(true);
            BlockPos coop = context.at(8, 1, 8);
            chicken.setPos(coop.getX() + 0.5D, coop.getY(), coop.getZ() + 0.5D);
            level.addFreshEntity(chicken);
            spawned.add(chicken);
            double shortVehicle = chicken.getDimensions(chicken.getPose()).height();
            boolean released = PerchService.release(first).changed() && !first.isPassenger()
                    && PerchService.perchOffset(first).isEmpty();
            PerchService.perch(first, chicken, left);
            chicken.positionRider(first);
            boolean followsHeight = shortVehicle < tall
                    && close(first.getY() - chicken.getY(), shortVehicle + left.y);
            context.note("perch probe: released=" + released
                    + " follows_height=" + followsHeight
                    + ((released && followsHeight) ? " OK" : " MISMATCH"));
            context.check("perch height: released", released);
            context.check("perch height: follows_height", followsHeight);
        } finally {
            settings.maxPerches.setValue(wasMax);
            settings.dropWhenSneaking.setValue(wasSneak);
            settings.dropOnFall.setValue(wasFall);
            settings.dropInPowderSnow.setValue(wasPowder);
            for (Entity entity : spawned) entity.discard();
        }
    }

    // The count is the service's own, since a forced boarding skips the vanilla one, and moving an already
    // perched creature to its other side is a move rather than a second slot.
    private static void capacity(ProbeContext context) {
        ServerLevel level = context.level();
        MxtServerConfig.Perch settings = MxtServerConfig.INSTANCE.perch;
        int wasMax = settings.maxPerches.getValue();
        boolean wasSneak = settings.dropWhenSneaking.getValue();
        boolean wasFall = settings.dropOnFall.getValue();
        boolean wasPowder = settings.dropInPowderSnow.getValue();
        settings.maxPerches.setValue(2);
        ProbeBeast.reset();
        List<Entity> spawned = new ArrayList<>();
        BlockPos origin = context.player().blockPosition();
        Vec3 left = new Vec3(0.5D, -0.4D, 0.0D);
        Vec3 right = new Vec3(-0.5D, -0.4D, 0.0D);
        try {
            ProbeBeast vehicle = spawnProbeBeast(level, origin.above());
            vehicle.setYRot(0.0F);
            spawned.add(vehicle);
            ProbeBeast first = spawnProbeBeast(level, origin.above(3));
            ProbeBeast second = spawnProbeBeast(level, origin.above(6));
            ProbeBeast third = spawnProbeBeast(level, origin.above(9));
            spawned.add(first);
            spawned.add(second);
            spawned.add(third);

            PerchService.release(first);
            boolean firstTwo = PerchService.perch(first, vehicle, left).changed()
                    && PerchService.perch(second, vehicle, right).changed();
            boolean thirdRefused = PerchService.perch(third, vehicle, left).failure() == PerchService.Failure.FULL
                    && !third.isPassenger();
            boolean moved = PerchService.perch(first, vehicle, right).changed()
                    && PerchService.perch(third, vehicle, left).failure() == PerchService.Failure.FULL;
            vehicle.positionRider(first);
            boolean otherSide = close(first.getX(), vehicle.getX() + right.x);
            boolean capacity = firstTwo && thirdRefused && moved && otherSide;
            context.note("perch probe: two_seated=" + firstTwo
                    + " third_refused=" + thirdRefused + " moved=" + moved + " other_side=" + otherSide
                    + (capacity ? " OK" : " MISMATCH"));
            context.check("perch capacity: two_seated", firstTwo);
            context.check("perch capacity: third_refused", thirdRefused);
            context.check("perch capacity: moved", moved);
            context.check("perch capacity: other_side", otherSide);
            context.check("perch capacity", capacity);
        } finally {
            settings.maxPerches.setValue(wasMax);
            settings.dropWhenSneaking.setValue(wasSneak);
            settings.dropOnFall.setValue(wasFall);
            settings.dropInPowderSnow.setValue(wasPowder);
            for (Entity entity : spawned) entity.discard();
        }
    }

    // The release policy, through the bridge: sneaking is the manual way down, a fall and powder snow are two of
    // the automatic ones, a switch turned off leaves the perch alone, and a record whose ride ended behind its
    // back is cleared instead of being applied to the next one.
    private static void policy(ProbeContext context) {
        ServerLevel level = context.level();
        MxtServerConfig.Perch settings = MxtServerConfig.INSTANCE.perch;
        int wasMax = settings.maxPerches.getValue();
        boolean wasSneak = settings.dropWhenSneaking.getValue();
        boolean wasFall = settings.dropOnFall.getValue();
        boolean wasPowder = settings.dropInPowderSnow.getValue();
        settings.maxPerches.setValue(2);
        ProbeBeast.reset();
        List<Entity> spawned = new ArrayList<>();
        BlockPos origin = context.player().blockPosition();
        Vec3 left = new Vec3(0.5D, -0.4D, 0.0D);
        try {
            ProbeBeast vehicle = spawnProbeBeast(level, origin.above());
            vehicle.setYRot(0.0F);
            spawned.add(vehicle);
            ProbeBeast first = spawnProbeBeast(level, origin.above(3));
            spawned.add(first);

            PerchService.release(first);
            PerchService.perch(first, vehicle, left);
            vehicle.setShiftKeyDown(true);
            PerchEventBridge.onEntityTick(new EntityTickEvent.Post(first));
            boolean manual = !first.isPassenger() && PerchService.perchOffset(first).isEmpty();
            vehicle.setShiftKeyDown(false);
            settings.dropWhenSneaking.setValue(false);
            PerchService.perch(first, vehicle, left);
            vehicle.setShiftKeyDown(true);
            PerchEventBridge.onEntityTick(new EntityTickEvent.Post(first));
            boolean switchOff = PerchService.perchOffset(first).isPresent();
            settings.dropWhenSneaking.setValue(true);
            vehicle.setShiftKeyDown(false);
            PerchEventBridge.onEntityTick(new EntityTickEvent.Post(first));
            boolean kept = PerchService.perchOffset(first).isPresent();
            vehicle.fallDistance = 1.0D;
            PerchEventBridge.onEntityTick(new EntityTickEvent.Post(first));
            boolean fell = PerchService.perchOffset(first).isEmpty();
            vehicle.fallDistance = 0.0D;
            PerchService.perch(first, vehicle, left);
            vehicle.isInPowderSnow = true;
            PerchEventBridge.onEntityTick(new EntityTickEvent.Post(first));
            boolean powder = PerchService.perchOffset(first).isEmpty();
            vehicle.isInPowderSnow = false;
            PerchService.perch(first, vehicle, left);
            first.stopRiding();
            PerchEventBridge.onEntityTick(new EntityTickEvent.Post(first));
            boolean stale = PerchService.perchOffset(first).isEmpty();
            boolean policy = manual && switchOff && kept && fell && powder && stale;
            context.note("perch probe: manual=" + manual + " switch_off=" + switchOff
                    + " kept=" + kept + " fell=" + fell + " powder=" + powder + " stale=" + stale
                    + (policy ? " OK" : " MISMATCH"));
            context.check("perch policy: manual", manual);
            context.check("perch policy: switch_off", switchOff);
            context.check("perch policy: kept", kept);
            context.check("perch policy: fell", fell);
            context.check("perch policy: powder", powder);
            context.check("perch policy: stale", stale);
            context.check("perch policy", policy);
        } finally {
            settings.maxPerches.setValue(wasMax);
            settings.dropWhenSneaking.setValue(wasSneak);
            settings.dropOnFall.setValue(wasFall);
            settings.dropInPowderSnow.setValue(wasPowder);
            for (Entity entity : spawned) entity.discard();
        }
    }

    // A creature that answers for itself: the framework asks where it wants to sit, reports a refusal as one, and
    // tells the creature once it is on - an addon therefore ships a creature, never a seat.
    private static void answer(ProbeContext context) {
        ServerLevel level = context.level();
        MxtServerConfig.Perch settings = MxtServerConfig.INSTANCE.perch;
        int wasMax = settings.maxPerches.getValue();
        boolean wasSneak = settings.dropWhenSneaking.getValue();
        boolean wasFall = settings.dropOnFall.getValue();
        boolean wasPowder = settings.dropInPowderSnow.getValue();
        settings.maxPerches.setValue(2);
        ProbeBeast.reset();
        List<Entity> spawned = new ArrayList<>();
        try {
            ProbeBeast host = spawnProbeBeast(level, context.at(10, 1, 8));
            ProbeBeast answerer = spawnProbeBeast(level, context.at(12, 1, 8));
            LivingEntity plain = spawnProbe(level, context.at(14, 1, 8), null);
            spawned.add(host);
            spawned.add(answerer);
            if (plain != null) spawned.add(plain);
            ProbeBeast.reset();
            ProbeBeast.perchAnswer(Optional.of(new Vec3(0.25D, -0.3D, 0.0D)));
            boolean asked = PerchService.perch(answerer, host).changed()
                    && PerchService.perchOffset(answerer)
                    .filter(offset -> close(offset.x, 0.25D) && close(offset.y, -0.3D)).isPresent()
                    && ProbeBeast.calls().contains("seat:0") && ProbeBeast.calls().contains("perched");
            boolean unperched = PerchService.release(answerer).changed() && ProbeBeast.calls().contains("unperched");
            ProbeBeast.perchAnswer(Optional.empty());
            boolean notWilling = PerchService.perch(answerer, host).failure() == PerchService.Failure.NOT_WILLING;
            boolean notPerchable = plain != null
                    && PerchService.perch(plain, host).failure() == PerchService.Failure.NOT_WILLING;
            boolean answers = asked && unperched && notWilling && notPerchable;
            context.note("perch probe: answered=" + asked + " unperched=" + unperched
                    + " not_willing=" + notWilling + " not_perchable=" + notPerchable
                    + (answers ? " OK" : " MISMATCH"));
            context.check("perch answer: answered", asked);
            context.check("perch answer: unperched", unperched);
            context.check("perch answer: not_willing", notWilling);
            context.check("perch answer: not_perchable", notPerchable);
            context.check("perch answer", answers);
        } finally {
            settings.maxPerches.setValue(wasMax);
            settings.dropWhenSneaking.setValue(wasSneak);
            settings.dropOnFall.setValue(wasFall);
            settings.dropInPowderSnow.setValue(wasPowder);
            for (Entity entity : spawned) entity.discard();
        }
    }

    /**
     * Ignoring a field is only a guarantee if it leaves no trace, so the two decoded definitions are compared. The
     * rarity key is one of them: it is what this shape read before a tier became a reference.
     */
    private static boolean ignoresForeignFields() {
        JsonObject foreign = new JsonObject();
        foreign.addProperty("element", "mxt_test:fire");
        foreign.addProperty("overcomes", "mxt_test:water");
        foreign.addProperty("damage_types", "mxt_test:fire");
        foreign.addProperty("rarity", "probe");
        return Physique.DIRECT_CODEC.parse(JsonOps.INSTANCE, named(foreign)).result()
                .equals(Physique.DIRECT_CODEC.parse(JsonOps.INSTANCE, named(new JsonObject())).result());
    }

    // A definition with no name or description would fail on those fields alone, which would make every leg below
    // vacuous: both are only derivable from an entry id, and bare JsonOps carries none.
    private static JsonObject named(JsonObject object) {
        object.addProperty("name", "probe");
        object.addProperty("description", "probe");
        return object;
    }

    // One-field JSON object, for decode checks that ask whether a single key is enough to break a definition.
    private static JsonObject single(String key, Object value) {
        JsonObject object = new JsonObject();
        if (value instanceof Number number) object.addProperty(key, number);
        else if (value instanceof Boolean flag) object.addProperty(key, flag);
        else object.addProperty(key, String.valueOf(value));
        return object;
    }

    private static JsonArray numbers(double... values) {
        JsonArray array = new JsonArray();
        for (double value : values) array.add(value);
        return array;
    }

    // One disposable probe entity carrying exactly one spirit root, so the element it strikes or is struck with is
    // never a question; the perch leg spawns the rootless form, which is a body nothing else speaks for.
    private static LivingEntity spawnProbe(ServerLevel level, BlockPos pos, @Nullable Identifier root) {
        Pig pig = EntityType.PIG.create(level, EntitySpawnReason.COMMAND);
        if (pig == null) return null;
        pig.setNoAi(true);
        pig.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        level.addFreshEntity(pig);
        if (root != null) CultivationIdentityService.grantSpiritRoot(pig,
                level.registryAccess().lookupOrThrow(MxtResourceKeys.SPIRIT_ROOT).get(root)
                        .orElseThrow(() -> new IllegalStateException("the test pack is missing " + root)));
        return pig;
    }

    private static ProbeBeast spawnProbeBeast(ServerLevel level, BlockPos pos) {
        ProbeBeast beast = MxtTestEntities.PROBE_BEAST.get().create(level, EntitySpawnReason.COMMAND);
        if (beast == null) throw new IllegalStateException("The probe beast type failed to create an entity");
        beast.setNoAi(true);
        beast.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        level.addFreshEntity(beast);
        return beast;
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }
}
