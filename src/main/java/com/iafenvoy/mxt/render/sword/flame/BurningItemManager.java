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
import java.util.*;

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
        if (this.level != minecraft.level || !MxtClientConfig.INSTANCE.flames.enabled.getValue()) this.close();
        this.level = minecraft.level;
        this.frame++;
        for (List<Entry> group : this.visible) group.clear();
        this.camera = null;
        long now = System.nanoTime();
        this.delta = minecraft.isPaused() ? 0 : Math.clamp((now - this.lastNanos) * 1.0E-9F, 0, 0.05F);
        if (this.lastNanos == 0) this.delta = 1 / 60.0F;
        this.lastNanos = now;
        this.time += this.delta;
        Iterator<Entry> iterator = this.entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (this.frame - entry.lastSeen > 120 || this.level == null || this.level.getEntity(entry.id) == null) {
                this.release(entry);
                iterator.remove();
            }
        }
    }

    public static void submit(SwordAuraRenderState state, Quaternionf rotation, CameraRenderState camera) {
        INSTANCE.collect(state, rotation, camera);
    }

    private void collect(SwordAuraRenderState state, Quaternionf rotation, CameraRenderState camera) {
        if (this.level == null || !MxtClientConfig.INSTANCE.flames.enabled.getValue()
                || ((state.auraColor >>> 24) & 0xFF) == 0) return;
        int lod = LODController.level(state.distanceToCameraSq);
        if (lod == 3) return;
        Entry entry = this.entries.get(state.entityId);
        if (entry == null) {
            if (this.entries.size() >= SurfaceFluidSimulation.CAPACITY) {
                Entry oldest = null;
                for (Entry candidate : this.entries.values())
                    if (candidate.lastSeen != this.frame && (oldest == null || candidate.lastSeen < oldest.lastSeen))
                        oldest = candidate;
                if (oldest == null) return;
                this.release(oldest);
                this.entries.remove(oldest.id);
            }
            entry = new Entry(state.entityId);
            this.entries.put(state.entityId, entry);
        }
        if (entry.lastSeen == this.frame) return;
        entry.reset = entry.reset || entry.lastSeen != this.frame - 1 || entry.lod == 2;
        entry.lastSeen = this.frame;
        entry.lod = lod;
        entry.state = state;
        entry.rotation.set(rotation);
        if (lod == 2) this.release(entry);
        else if (entry.slot < 0) {
            entry.slot = this.slots.nextClearBit(0);
            this.slots.set(entry.slot);
            entry.reset = true;
        }
        entry.update = entry.reset || (this.delta > 0 && (lod == 0 || (this.frame & 1) == 0));
        entry.step = entry.reset ? 1 / 60.0F : Math.clamp(this.time - entry.lastUpdateTime, 0, 0.05F);
        this.camera = camera;
        this.visible.get(lod).add(entry);
    }

    private void render() {
        if (this.camera == null) return;
        if (this.renderer == null) this.renderer = new FlameRenderer();
        if (!this.renderer.render(this.visible, this.camera, this.time, this.delta)) return;
        for (List<Entry> group : this.visible)
            for (Entry entry : group) {
                if (entry.update) entry.lastUpdateTime = this.time;
                entry.reset = false;
            }
    }

    private void release(Entry entry) {
        if (entry.slot >= 0) this.slots.clear(entry.slot);
        entry.slot = -1;
    }

    private void close() {
        if (this.renderer != null) this.renderer.close();
        this.renderer = null;
        this.entries.clear();
        this.slots.clear();
        for (List<Entry> group : this.visible) group.clear();
        this.camera = null;
        this.level = null;
        this.time = 0;
        this.lastNanos = 0;
    }

    public static final class Entry {
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
            this.seed = ((id * 0x9E3779B9) >>> 8) / 16777216.0F;
        }

        void write(ByteBuffer buffer, CameraRenderState camera) {
            MxtClientConfig.Flames config = MxtClientConfig.INSTANCE.flames;
            put(buffer, (float) (this.state.x - camera.pos.x), (float) (this.state.y + this.state.centerOffsetY - camera.pos.y),
                    (float) (this.state.z - camera.pos.z), this.state.animationTime * 0.05F);
            this.axis(buffer, 1, 0, 0);
            this.axis(buffer, 0, 1, 0);
            this.axis(buffer, 0, 0, 1);
            put(buffer, this.state.length, this.state.bladeWidth, this.state.thickness, this.state.handleLength);
            put(buffer, this.state.guardWidth, this.state.radialFlame ? 1.0F : 0.0F, this.seed, this.lod);
            this.scratch.set((float) (config.windX.getValue() - this.state.velocity.x * 20),
                    (float) (config.windY.getValue() - this.state.velocity.y * 20),
                    (float) (config.windZ.getValue() - this.state.velocity.z * 20));
            this.scratch.mul(config.windCoefficient.getValue().floatValue());
            if (this.scratch.lengthSquared() > 144) this.scratch.normalize(12);
            this.rotation.transformInverse(this.scratch);
            this.scratch.div(this.state.scale);
            put(buffer, this.scratch.x, this.scratch.y, this.scratch.z, 0);
            put(buffer, this.slot, this.reset ? 1 : 0, this.update ? 1 : 0, this.step);
            put(buffer, ((this.state.auraColor >>> 16) & 255) / 255.0F,
                    ((this.state.auraColor >>> 8) & 255) / 255.0F,
                    (this.state.auraColor & 255) / 255.0F,
                    ((this.state.auraColor >>> 24) & 255) / 255.0F);
        }

        private void axis(ByteBuffer buffer, float x, float y, float z) {
            this.rotation.transform(this.scratch.set(x, y, z)).mul(this.state.scale);
            put(buffer, this.scratch.x, this.scratch.y, this.scratch.z, 0);
        }

        private static void put(ByteBuffer buffer, float x, float y, float z, float w) {
            buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(w);
        }
    }
}
