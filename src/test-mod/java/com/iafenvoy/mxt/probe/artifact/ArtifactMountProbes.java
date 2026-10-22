package com.iafenvoy.mxt.probe.artifact;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.api.MountVehicle;
import com.iafenvoy.mxt.data.ability.type.MountAbilityType;
import com.iafenvoy.mxt.entity.FlyingSwordEntity;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.runtime.artifact.FlightService;
import com.iafenvoy.mxt.testmod.ProbeMount;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * The contract a named vehicle type has to keep: the framework's own body, the test mod's own, and a type that is not
 * a vehicle refused instead of spawned.
 */
public final class ArtifactMountProbes {
    private ArtifactMountProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("artifact/mount/vehicle_contract", ArtifactMountProbes::vehicleContract));
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

    private static JsonObject bareMount() {
        JsonObject bareMount = new JsonObject();
        bareMount.addProperty("speed", 0.2D);
        return bareMount;
    }

    private static MountAbilityType parseMount(JsonObject json) {
        return MountAbilityType.CODEC.codec().parse(JsonOps.INSTANCE, json).result().orElse(null);
    }
}
