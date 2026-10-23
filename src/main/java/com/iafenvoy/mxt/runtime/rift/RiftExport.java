package com.iafenvoy.mxt.runtime.rift;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iafenvoy.mxt.data.rift.RiftDestination;
import com.iafenvoy.mxt.data.rift.RiftPlacement;
import com.iafenvoy.mxt.data.rift.RiftPreset;
import com.iafenvoy.mxt.data.rift.RiftTemplate;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public final class RiftExport {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    private RiftExport() {
    }

    public static String preset(RiftTemplate data) {
        return JSON.toJson(RiftPreset.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, new RiftPreset(data.shape(), data.appearance())).result().orElseThrow());
    }

    public static String layout(List<RiftTemplate> records, Optional<Vec3> entry, Optional<Identifier> localDestination) {
        JsonArray portals = new JsonArray();
        for (int i = 0; i < records.size(); i++) {
            RiftTemplate data = records.get(i);
            RiftPlacement p = data.placement();
            RiftDestination destination = data.destination();
            if (destination instanceof RiftDestination.Position(
                    Optional<Identifier> dimension, Vec3 pos1, Optional<Float> yaw, Optional<Float> pitch,
                    boolean nearby
            ) && localDestination.isPresent() && dimension.equals(localDestination))
                destination = new RiftDestination.Position(Optional.empty(), pos1, yaw, pitch, nearby);
            RiftTemplate template = new RiftTemplate(records.size() == 1 ? data.key() : "portal_" + (i + 1),
                    new RiftPlacement(entry.map(p.origin()::subtract).orElse(p.origin()), p.normal(), p.tangent()), entry.isPresent(),
                    data.shape(), destination, data.appearance(), data.durationTicks(), data.enabled());
            portals.add(RiftTemplate.CODEC.encodeStart(JsonOps.INSTANCE, template).result().orElseThrow());
        }
        JsonObject result = new JsonObject();
        result.add("portals", portals);
        return JSON.toJson(result);
    }

    public static String template(RiftTemplate data) {
        return JSON.toJson(RiftTemplate.CODEC.encodeStart(JsonOps.INSTANCE, data).result().orElseThrow());
    }
}
