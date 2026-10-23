package com.iafenvoy.mxt.render.rift;

import com.iafenvoy.mxt.data.rift.RiftPlacement;
import com.iafenvoy.mxt.data.rift.RiftVertex;
import com.iafenvoy.mxt.network.payload.RiftSyncS2CPayload;
import com.iafenvoy.mxt.runtime.rift.RiftEdits;
import com.iafenvoy.mxt.runtime.rift.RiftGeometry;
import com.iafenvoy.mxt.runtime.rift.RiftVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.*;

@EventBusSubscriber(Dist.CLIENT)
public final class RiftClientState {
    private static ClientLevel world;
    private static UUID generation;
    private static final Map<UUID, Entry> PORTALS = new LinkedHashMap<>();
    private static final Map<UUID, Long> REVISIONS = new LinkedHashMap<>();

    private RiftClientState() {
    }

    public static void accept(RiftSyncS2CPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().identifier().equals(payload.dimension())) return;
        if (world != level) {
            clear();
            world = level;
        }
        if (payload.reset()) {
            PORTALS.clear();
            REVISIONS.clear();
            generation = payload.generation();
            return;
        }
        if (!payload.generation().equals(generation)) return;
        long revision = REVISIONS.getOrDefault(payload.id(), -1L);
        if (payload.revision() < revision) return;
        REVISIONS.put(payload.id(), payload.revision());
        if (REVISIONS.size() > 8192) REVISIONS.remove(REVISIONS.keySet().iterator().next());
        if (payload.visual().isEmpty()) {
            PORTALS.remove(payload.id());
            return;
        }
        if (PORTALS.size() >= 4096 && !PORTALS.containsKey(payload.id())) return;
        RiftVisual visual = payload.visual().get();
        Optional<List<RiftGeometry.Triangle>> triangles = RiftGeometry.triangulate(visual.shape()).result();
        if (triangles.isEmpty()) return;
        PORTALS.put(payload.id(), new Entry(payload.id(), payload.revision(), visual,
                RiftGeometry.mesh(visual.shape(), visual.placement(), triangles.get())));
    }

    public static List<Entry> entries() {
        if (world != Minecraft.getInstance().level) clear();
        return List.copyOf(PORTALS.values());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        int budget = 32;
        for (Entry entry : entries()) {
            double density = entry.visual().appearance().particles();
            if (!entry.visual().enabled() || density <= 0 || entry.mesh().bounds().distanceToSqr(mc.player.position()) > 48 * 48)
                continue;
            int count = (int) (density * 4 + mc.level.getRandom().nextDouble());
            List<RiftVertex> vertices = entry.visual().shape().vertices();
            for (int i = 0; i < count && budget > 0; i++, budget--) {
                int edge = mc.level.getRandom().nextInt(vertices.size());
                RiftVertex point = RiftEdits.lerp(vertices.get(edge), vertices.get((edge + 1) % vertices.size()), mc.level.getRandom().nextDouble());
                RiftPlacement placement = entry.visual().placement();
                Vec3 position = placement.world(point).add(placement.normal().scale((mc.level.getRandom().nextDouble() - 0.5) * 0.12));
                mc.level.addParticle(new DustParticleOptions(entry.visual().color(), 0.45F), position.x, position.y, position.z, 0, 0.01, 0);
            }
            if (budget == 0) break;
        }
    }

    private static void clear() {
        PORTALS.clear();
        REVISIONS.clear();
        world = null;
        generation = null;
    }

    public record Entry(UUID id, long revision, RiftVisual visual, RiftGeometry.Mesh mesh) {
    }
}
