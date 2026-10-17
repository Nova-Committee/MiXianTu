package com.iafenvoy.mxt.probe.artifact;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.api.MountVehicle;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.render.MountPose;
import com.iafenvoy.mxt.data.ability.render.MountRender;
import com.iafenvoy.mxt.data.ability.render.builtin.GeckoLibMountRender;
import com.iafenvoy.mxt.data.ability.render.builtin.ItemMountRender;
import com.iafenvoy.mxt.data.ability.type.FlightDisplay;
import com.iafenvoy.mxt.data.ability.type.MountAbilityType;
import com.iafenvoy.mxt.data.action.NoOpAction;
import com.iafenvoy.mxt.data.action.builtin.entity.PlaySoundAction;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtMountRenders;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.runtime.artifact.FlightService;
import com.iafenvoy.mxt.entity.FlyingSwordEntity;
import com.iafenvoy.mxt.testmod.MxtTestEntities;
import com.iafenvoy.mxt.testmod.ProbeMount;
import com.iafenvoy.mxt.testmod.ProbeMountRenders;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * What a mount definition declares about being drawn and flown: the display transform, the render dispatch table, the
 * seven poses, the written geometry and hooks, and the contract the named vehicle type has to keep.
 */
public final class ArtifactMountProbes {
    private ArtifactMountProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("artifact/mount/display", ArtifactMountProbes::display),
                Probe.of("artifact/mount/render", ArtifactMountProbes::render),
                Probe.of("artifact/mount/render/states", ArtifactMountProbes::renderStates),
                Probe.of("artifact/mount/render/unknown", ArtifactMountProbes::unknownRender),
                Probe.of("artifact/mount/pose", ArtifactMountProbes::poses),
                Probe.of("artifact/mount/fields", ArtifactMountProbes::fields),
                Probe.of("artifact/mount/hooks", ArtifactMountProbes::hooks),
                Probe.of("artifact/mount/entity_type", ArtifactMountProbes::entityType),
                Probe.of("artifact/mount/vehicle_contract", ArtifactMountProbes::vehicleContract),
                Probe.of("artifact/mount/ignored_fields", ArtifactMountProbes::ignoredFields));
    }

    // A written display is read the way a vanilla model's is: translation in sixteenths of a block, rotation in
    // degrees, scale as a multiplier. A written vehicle adds seats, pose, the box and the seat layout on top.
    private static void display(ProbeContext context) {
        JsonObject written = new JsonObject();
        written.add("translation", numbers(0.0D, 4.0D, 0.0D));
        written.add("rotation", numbers(90.0D, 0.0D, -45.0D));
        written.add("scale", numbers(2.0D, 0.0D, 2.0D));
        boolean displayRead = FlightDisplay.CODEC.parse(JsonOps.INSTANCE, written).result()
                .filter(display -> close(display.translation().y(), 0.25D) && close(display.scale().y(), 0.0D))
                .isPresent();
        context.check("artifact roster flight display reads 4/16 as 0.25", displayRead);
    }

    // What a mount is drawn by: three types on one dispatch table, the item one as the default, and a written
    // display that is only absent when nobody wrote one.
    private static void render(ProbeContext context) {
        JsonObject itemRender = new JsonObject();
        itemRender.addProperty("type", "mxt:item");
        JsonObject probeRender = new JsonObject();
        probeRender.addProperty("type", "mxt_test:probe_render");
        probeRender.addProperty("label", "hello");
        MountRender readItem = MountRender.CODEC.parse(JsonOps.INSTANCE, itemRender).result().orElse(null);
        MountRender readGecko = MountRender.CODEC.parse(JsonOps.INSTANCE, geckoRender()).result().orElse(null);
        MountRender readProbe = MountRender.CODEC.parse(JsonOps.INSTANCE, probeRender).result().orElse(null);
        boolean renderTypes = readItem == ItemMountRender.INSTANCE
                && readGecko instanceof GeckoLibMountRender gecko
                && gecko.model().equals(ProbeDefinitions.id("vehicle/probe")) && gecko.animations().isPresent()
                && gecko.transitionTicks() == 7 && close(gecko.scale(), 1.5D)
                && readProbe instanceof ProbeMountRenders.ProbeMountRender(String label) && label.equals("hello");
        context.check("artifact roster render types item/geckolib/other-mod", renderTypes);
    }

    // An unwritten table asks for the animation named after the pose; a written one only replaces the names it
    // mentions. Both pose groups are covered, because each one drives its own controller.
    private static void renderStates(ProbeContext context) {
        MountRender readGecko = MountRender.CODEC.parse(JsonOps.INSTANCE, geckoRender()).result().orElse(null);
        boolean renderStates = readGecko instanceof GeckoLibMountRender gecko
                && gecko.animationFor(MountPose.MOVING).equals("fly")
                && gecko.animationFor(MountPose.IDLE).equals("idle")
                && gecko.animationFor(MountPose.ASCENDING).equals("ascending")
                && gecko.animationFor(MountPose.DESCENDING).equals("descending")
                && gecko.animationFor(MountPose.EMPTY).equals("empty")
                && gecko.animationFor(MountPose.RIDDEN).equals("ridden")
                && gecko.animationFor(MountPose.CARRYING).equals("carrying");
        context.check("artifact roster render states moving=fly and the rest named after the pose", renderStates);
    }

    // A type nobody registered is a load-time error, which is what keeps a typo from silently becoming the
    // item model on every client.
    private static void unknownRender(ProbeContext context) {
        JsonObject unknownRender = new JsonObject();
        unknownRender.addProperty("type", "mxt_test:nothing_here");
        boolean unknown = MountRender.CODEC.parse(JsonOps.INSTANCE, unknownRender).error().isPresent()
                && MxtRegistries.MOUNT_RENDER_TYPE.getKey(ItemMountRender.INSTANCE.codec()).equals(MxtMountRenders.ITEM.getKey().identifier())
                && MxtRegistries.MOUNT_RENDER_TYPE.getOptional(MxtMountRenders.GECKOLIB.getKey().identifier()).isPresent();
        context.check("artifact roster unknown render type is refused and both builtins exist", unknown);
    }

    // The two pose groups: vertical beats horizontal, and the crew group counts riders. Both are total, so a
    // group never has "no answer" for a controller to fall back on.
    private static void poses(ProbeContext context) {
        boolean poses = MountPose.motion(0.0D, 0.0D) == MountPose.IDLE
                && MountPose.motion(0.5D, 0.0D) == MountPose.MOVING
                && MountPose.motion(0.0D, 0.5D) == MountPose.ASCENDING
                && MountPose.motion(0.5D, -0.5D) == MountPose.DESCENDING
                && MountPose.crew(0) == MountPose.EMPTY && MountPose.crew(1) == MountPose.RIDDEN
                && MountPose.crew(5) == MountPose.CARRYING
                && MountPose.IDLE.axis() == MountPose.Axis.MOTION && MountPose.CARRYING.axis() == MountPose.Axis.CREW;
        context.check("artifact roster poses vertical-first and crew-counted in two axes", poses);
    }

    // What a written mount document means, and what a bare one falls back to for every field it does not write.
    private static void fields(ProbeContext context) {
        MountAbilityType readMount = parseMount(writtenMount());
        MountAbilityType defaultMount = parseMount(bareMount());
        boolean mountFields = readMount != null && defaultMount != null
                && readMount.seats() == 3 && readMount.sit() && close(readMount.stepHeight(), 0.6D)
                && close(readMount.seatOffset(0).y(), 1.0D) && close(readMount.seatOffset(1).z(), -1.0D)
                // Past the last written offset the last one is reused rather than the whole list being dropped.
                && close(readMount.seatOffset(2).z(), -1.0D)
                && defaultMount.seats() == 1 && !defaultMount.sit()
                && close(defaultMount.width(), MountAbilityType.DEFAULT_WIDTH)
                && close(defaultMount.height(), MountAbilityType.DEFAULT_HEIGHT)
                && close(defaultMount.seatOffset(0).y(), MountAbilityType.DEFAULT_SEAT_HEIGHT)
                && close(defaultMount.seatOffset(1).z(), -MountAbilityType.DEFAULT_SEAT_SPACING)
                && defaultMount.actions().onMount() == NoOpAction.INSTANCE && defaultMount.trail().isEmpty();
        context.check("artifact roster mount reads seats/pose/step/offsets and defaults them", mountFields);
    }

    // The mount's own hooks and its trail: three hooks, of which the written one is the one that is read, and a
    // trail whose interval/flag are written while its geometry falls back to the defaults.
    private static void hooks(ProbeContext context) {
        MountAbilityType readMount = parseMount(writtenMount());
        boolean mountHooks = readMount != null && readMount.actions().onMount() instanceof PlaySoundAction sound
                && close(sound.volume(), 0.25F) && readMount.actions().onDismount() == NoOpAction.INSTANCE
                && readMount.trail().filter(trail -> trail.interval() == 4 && trail.movingOnly() && trail.count() == 1
                && trail.spread().equals(MountAbilityType.MountTrail.DEFAULT_SPREAD)
                && close(trail.offsetY(), MountAbilityType.MountTrail.DEFAULT_OFFSET_Y)).isPresent();
        context.check("artifact roster mount reads mount_action=on_mount sound and trail interval=4 moving_only", mountHooks);
    }

    // Which body flies it: absent means the framework's own vehicle, a named one is resolved through the entity
    // registry, and an id nobody registered fails the load rather than the take-off.
    private static void entityType(ProbeContext context) {
        MountAbilityType defaultMount = parseMount(bareMount());
        JsonObject namedVehicle = bareMount();
        namedVehicle.addProperty("entity_type", "mxt_test:probe_mount");
        MountAbilityType namedMount = parseMount(namedVehicle);
        JsonObject unregisteredVehicle = bareMount();
        unregisteredVehicle.addProperty("entity_type", "mxt_test:not_registered");
        boolean vehicleField = defaultMount != null && defaultMount.entityType().isEmpty()
                && namedMount != null && namedMount.entityType().orElse(null) == MxtTestEntities.PROBE_MOUNT.get()
                && MountAbilityType.CODEC.codec().parse(JsonOps.INSTANCE, unregisteredVehicle).error().isPresent();
        context.check("artifact roster mount entity_type defaults to the framework vehicle and rejects an unknown id", vehicleField);
    }

    // The contract behind the field: the named type has to implement MountVehicle. The framework's own does, the
    // test mod's own does, and a vanilla type that does not is refused instead of spawning a body nobody can ride.
    private static void vehicleContract(ProbeContext context) {
        MountAbilityType defaultMount = parseMount(bareMount());
        JsonObject namedVehicle = bareMount();
        namedVehicle.addProperty("entity_type", "mxt_test:probe_mount");
        MountAbilityType namedMount = parseMount(namedVehicle);
        JsonObject plainVehicle = bareMount();
        plainVehicle.addProperty("entity_type", "minecraft:armor_stand");
        MountAbilityType plainMount = parseMount(plainVehicle);
        MountVehicle namedBody = namedMount == null ? null : FlightService.createVehicle(context.level(), namedMount).orElse(null);
        MountVehicle ownBody = defaultMount == null ? null : FlightService.createVehicle(context.level(), defaultMount).orElse(null);
        boolean vehicleContract = namedBody instanceof ProbeMount && ownBody instanceof FlyingSwordEntity
                && plainMount != null && FlightService.createVehicle(context.level(), plainMount).isEmpty();
        // Both of those are real entities in the level by now, so the assertion takes them back out again.
        if (namedBody instanceof Entity spawned) spawned.discard();
        if (ownBody instanceof Entity spawned) spawned.discard();
        context.check("artifact roster mount spawns the type a definition names and refuses one that is not a vehicle", vehicleContract);
    }

    // A field a vehicle never reads is ignored rather than refused: the same document with it decodes to the
    // very definition the one without it does.
    private static void ignoredFields(ProbeContext context) {
        JsonObject legalMount = new JsonObject();
        legalMount.addProperty("type", "mxt:mount");
        legalMount.addProperty("speed", 0.1D);
        legalMount.addProperty("name", "probe mount");
        legalMount.addProperty("description", "probe mount");
        JsonObject illegalMount = legalMount.deepCopy();
        illegalMount.add("entity_action", JsonParser.parseString("{\"type\": \"mxt:no_op\"}"));
        Ability legalMountAbility = Ability.DIRECT_CODEC.parse(JsonOps.INSTANCE, legalMount).result().orElse(null);
        Ability inertMountAbility = Ability.DIRECT_CODEC.parse(JsonOps.INSTANCE, illegalMount).result().orElse(null);
        boolean inertFields = legalMountAbility != null && legalMountAbility.equals(inertMountAbility);
        context.check("artifact roster mount ignores a field it never reads", inertFields);
    }

    // The mount document the field and hook legs read, and the bare one they fall back to.
    private static JsonObject writtenMount() {
        JsonObject writtenMount = new JsonObject();
        writtenMount.addProperty("speed", 0.2D);
        writtenMount.addProperty("seats", 3);
        writtenMount.addProperty("sit", true);
        writtenMount.addProperty("step_height", 0.6D);
        JsonArray offsets = new JsonArray();
        offsets.add(numbers(0.0D, 1.0D, 0.0D));
        offsets.add(numbers(0.0D, 1.0D, -1.0D));
        writtenMount.add("seat_offsets", offsets);
        JsonObject mountAction = new JsonObject();
        JsonObject onMountSound = new JsonObject();
        onMountSound.addProperty("type", "mxt:play_sound");
        onMountSound.addProperty("sound", "minecraft:block.beacon.activate");
        onMountSound.addProperty("volume", 0.25D);
        mountAction.add("on_mount", onMountSound);
        writtenMount.add("mount_action", mountAction);
        JsonObject writtenTrail = new JsonObject();
        JsonObject writtenTrailParticle = new JsonObject();
        writtenTrailParticle.addProperty("type", "minecraft:cloud");
        writtenTrail.add("particle", writtenTrailParticle);
        writtenTrail.addProperty("interval", 4);
        writtenTrail.addProperty("moving_only", true);
        writtenMount.add("trail", writtenTrail);
        return writtenMount;
    }

    private static JsonObject bareMount() {
        JsonObject bareMount = new JsonObject();
        bareMount.addProperty("speed", 0.2D);
        return bareMount;
    }

    // The GeckoLib render document: one written pose name, and every other pose left to its own default name.
    private static JsonObject geckoRender() {
        JsonObject geckoRender = new JsonObject();
        geckoRender.addProperty("type", "mxt:geckolib");
        geckoRender.addProperty("model", "mxt_test:vehicle/probe");
        geckoRender.addProperty("texture", "mxt_test:textures/entity/vehicle/probe.png");
        geckoRender.addProperty("animations", "mxt_test:vehicle/probe");
        geckoRender.addProperty("transition_ticks", 7);
        geckoRender.addProperty("scale", 1.5D);
        JsonObject geckoStates = new JsonObject();
        geckoStates.addProperty("moving", "fly");
        geckoRender.add("states", geckoStates);
        return geckoRender;
    }

    private static MountAbilityType parseMount(JsonObject json) {
        return MountAbilityType.CODEC.codec().parse(JsonOps.INSTANCE, json).result().orElse(null);
    }

    private static JsonArray numbers(double... values) {
        JsonArray array = new JsonArray();
        for (double value : values) array.add(value);
        return array;
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }
}
