package com.iafenvoy.mxt.render.sword.flame;

import com.iafenvoy.mxt.config.MxtClientConfig;
import com.iafenvoy.mxt.render.sword.SwordAuraRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(Dist.CLIENT)
public final class BurningItemManager {
    private static final BurningItemManager INSTANCE = new BurningItemManager();
    private final Map<Integer, Entry> entries = new HashMap<>();
    private final BitSet slots = new BitSet(SurfaceFluidSimulation.CAPACITY);
    private final List<List<Entry>> visible = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    private ClientLevel level;
    private FlameRenderer renderer;
    private CameraRenderState camera;
    private int frame;
    private long lastNanos;
    private float time;
    private float delta;

    private BurningItemManager() {
    }

    @SubscribeEvent
    public static void beginFrame(RenderFrameEvent.Pre event) {
        INSTANCE.begin();
    }

    @SubscribeEvent
    public static void afterLevel(RenderLevelStageEvent.AfterLevel event) {
        INSTANCE.render();
    }

    @SubscribeEvent
    public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        INSTANCE.close();
    }

    private void begin() {
        Minecraft minecraft = Minecraft.getInstance();
        if (level != minecraft.level || !MxtClientConfig.INSTANCE.flames.enabled.getValue()) close();
        level = minecraft.level;
        frame++;
        for (List<Entry> group : visible) group.clear();
        camera = null;
        long now = System.nanoTime();
        delta = minecraft.isPaused() ? 0 : Math.clamp((now - lastNanos) * 1.0E-9F, 0, 0.05F);
        if (lastNanos == 0) delta = 1 / 60.0F;
        lastNanos = now;
        time += delta;
        var iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (frame - entry.lastSeen > 120 || level == null || level.getEntity(entry.id) == null) {
                release(entry);
                iterator.remove();
            }
        }
    }

    public static void submit(SwordAuraRenderState state, Quaternionf rotation, CameraRenderState camera) {
        INSTANCE.collect(state, rotation, camera);
    }

    private void collect(SwordAuraRenderState state, Quaternionf rotation, CameraRenderState camera) {
        if (level == null || !MxtClientConfig.INSTANCE.flames.enabled.getValue()
                || ((state.auraColor >>> 24) & 0xFF) == 0) return;
        int lod = LODController.level(state.distanceToCameraSq);
        if (lod == 3) return;
        Entry entry = entries.get(state.entityId);
        if (entry == null) {
            if (entries.size() >= SurfaceFluidSimulation.CAPACITY) {
                Entry oldest = null;
                for (Entry candidate : entries.values())
                    if (candidate.lastSeen != frame && (oldest == null || candidate.lastSeen < oldest.lastSeen)) oldest = candidate;
                if (oldest == null) return;
                release(oldest);
                entries.remove(oldest.id);
            }
            entry = new Entry(state.entityId);
            entries.put(state.entityId, entry);
        }
        if (entry.lastSeen == frame) return;
        entry.reset = entry.reset || entry.lastSeen != frame - 1 || entry.lod == 2;
        entry.lastSeen = frame;
        entry.lod = lod;
        entry.state = state;
        entry.rotation.set(rotation);
        if (lod == 2) release(entry);
        else if (entry.slot < 0) {
            entry.slot = slots.nextClearBit(0);
            slots.set(entry.slot);
            entry.reset = true;
        }
        entry.update = entry.reset || (delta > 0 && (lod == 0 || (frame & 1) == 0));
        entry.step = entry.reset ? 1 / 60.0F : Math.clamp(time - entry.lastUpdateTime, 0, 0.05F);
        this.camera = camera;
        visible.get(lod).add(entry);
    }

    private void render() {
        if (camera == null) return;
        if (renderer == null) renderer = new FlameRenderer();
        if (!renderer.render(visible, camera, time, delta)) return;
        for (List<Entry> group : visible) for (Entry entry : group) {
            if (entry.update) entry.lastUpdateTime = time;
            entry.reset = false;
        }
    }

    private void release(Entry entry) {
        if (entry.slot >= 0) slots.clear(entry.slot);
        entry.slot = -1;
    }

    private void close() {
        if (renderer != null) renderer.close();
        renderer = null;
        entries.clear();
        slots.clear();
        for (List<Entry> group : visible) group.clear();
        camera = null;
        level = null;
        time = 0;
        lastNanos = 0;
    }

    static final class Entry {
        final int id;
        final float seed;
        final Quaternionf rotation = new Quaternionf();
        final Vector3f scratch = new Vector3f();
        SwordAuraRenderState state;
        int slot = -1;
        int lastSeen = -1;
        int lod = 2;
        boolean reset;
        boolean update;
        float lastUpdateTime;
        float step;

        Entry(int id) {
            this.id = id;
            seed = ((id * 0x9E3779B9) >>> 8) / 16777216.0F;
        }

        void write(ByteBuffer buffer, CameraRenderState camera) {
            var config = MxtClientConfig.INSTANCE.flames;
            put(buffer, (float) (state.x - camera.pos.x), (float) (state.y + state.centerOffsetY - camera.pos.y),
                    (float) (state.z - camera.pos.z), state.animationTime * 0.05F);
            axis(buffer, 1, 0, 0);
            axis(buffer, 0, 1, 0);
            axis(buffer, 0, 0, 1);
            put(buffer, state.length, state.bladeWidth, state.thickness, state.handleLength);
            put(buffer, state.guardWidth, state.radialFlame ? 1.0F : 0.0F, seed, lod);
            scratch.set((float) (config.windX.getValue() - state.velocity.x * 20),
                    (float) (config.windY.getValue() - state.velocity.y * 20),
                    (float) (config.windZ.getValue() - state.velocity.z * 20));
            scratch.mul(config.windCoefficient.getValue().floatValue());
            if (scratch.lengthSquared() > 144) scratch.normalize(12);
            rotation.transformInverse(scratch);
            scratch.div(state.scale);
            put(buffer, scratch.x, scratch.y, scratch.z, 0);
            put(buffer, slot, reset ? 1 : 0, update ? 1 : 0, step);
            put(buffer, ((state.auraColor >>> 16) & 255) / 255.0F,
                    ((state.auraColor >>> 8) & 255) / 255.0F,
                    (state.auraColor & 255) / 255.0F,
                    ((state.auraColor >>> 24) & 255) / 255.0F);
        }

        private void axis(ByteBuffer buffer, float x, float y, float z) {
            rotation.transform(scratch.set(x, y, z)).mul(state.scale);
            put(buffer, scratch.x, scratch.y, scratch.z, 0);
        }

        private static void put(ByteBuffer buffer, float x, float y, float z, float w) {
            buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(w);
        }
    }
}
