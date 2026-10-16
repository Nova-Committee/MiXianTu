package com.iafenvoy.mxt.render.particle;

import com.iafenvoy.mxt.data.formation.FormationDrawIn;
import com.iafenvoy.mxt.data.formation.FormationShapePath;
import com.iafenvoy.mxt.particle.FormationParticleOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The formation figures this client is drawing, one per controller block. The server only says which figures exist and
 * what they look like; the clock that grows one is this side's own, so an outline moves at the client's tick rate
 * instead of stepping once per order. An order also carries how far the figure already is, so a player who walks up in
 * the middle of a draw-in lays that part out at once and only the rest is grown.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class FormationFigures {
    /**
     * How often a whole figure is laid out again, so the dust it is made of never thins out. It is the cadence the
     * server used to redraw on, which is short enough for the shortest lived dust at the default size.
     */
    private static final int REFRESH_TICKS = 10;
    private static final FormationFigures INSTANCE = new FormationFigures();
    private final Map<BlockPos, Figure> figures = new HashMap<>();
    private ClientLevel level;

    private FormationFigures() {
    }

    /**
     * Takes one order. Called by the order's own particle, which is nothing but a carrier: the figure it names outlives
     * it, and this side decides what a figure that is no longer ordered stops being drawn.
     */
    public static void accept(ClientLevel level, Vec3 center, FormationParticleOptions options) {
        INSTANCE.put(level, center, options);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        INSTANCE.tick();
    }

    @SubscribeEvent
    public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        INSTANCE.clear();
    }

    private void put(ClientLevel level, Vec3 center, FormationParticleOptions options) {
        this.follow(level);
        BlockPos key = BlockPos.containing(center);
        Figure figure = this.figures.get(key);
        // A new raise is a new figure even at the same spot: its clock starts over and what was drawn before is
        // replaced rather than grown into.
        if (figure == null || figure.raisedAt != options.drawIn().raisedAt()) {
            figure = new Figure(center, options.drawIn(), level.getGameTime());
            this.figures.put(key, figure);
        }
        figure.seen = level.getGameTime();
        figure.options = options;
    }

    private void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            this.clear();
            return;
        }
        this.follow(level);
        long now = level.getGameTime();
        Iterator<Map.Entry<BlockPos, Figure>> iterator = this.figures.entrySet().iterator();
        while (iterator.hasNext()) {
            Figure figure = iterator.next().getValue();
            // Orders that stopped coming mean the array is gone or out of sight; the dust already placed fades on its
            // own, so stopping here is all that has to happen.
            if (now - figure.seen > figure.options.drawIn().holdTicks()) {
                iterator.remove();
                continue;
            }
            figure.tick(level, now);
        }
    }

    private void follow(ClientLevel level) {
        if (this.level == level) return;
        this.clear();
        this.level = level;
    }

    private void clear() {
        this.figures.clear();
        this.level = null;
    }

    private static final class Figure {
        private final Vec3 center;
        private final long raisedAt;
        private final float start;
        private final long startedAt;
        private final int drawIn;
        private FormationParticleOptions options;
        private long seen;
        private float laid;
        private int refresh;

        Figure(Vec3 center, FormationDrawIn order, long now) {
            this.center = center;
            this.raisedAt = order.raisedAt();
            this.start = order.drawn();
            this.startedAt = now;
            this.drawIn = order.drawInTicks();
            this.laid = order.drawn();
            // Nothing of this figure is on screen yet, so the first tick lays out the part that already exists.
            this.refresh = 0;
        }

        void tick(ClientLevel level, long now) {
            float target = this.target(now);
            if (this.refresh-- <= 0) {
                this.layOut(level, 0.0F, target);
                this.refresh = REFRESH_TICKS;
            } else if (target > this.laid) {
                this.layOut(level, this.laid, target);
            }
            this.laid = target;
        }

        // The client's own ramp, from wherever the first order found the figure to the whole of it. Its slope is the
        // server's, because `drawIn` is the time the server's ramp had left, so the two land together; the end is
        // counted in ticks rather than left to the multiplication, which rounds a hair short of the whole figure.
        private float target(long now) {
            if (this.drawIn <= 0) return 1.0F;
            long grown = now - this.startedAt;
            if (grown >= this.drawIn) return 1.0F;
            return this.start + (1.0F - this.start) * (float) grown / this.drawIn;
        }

        private void layOut(ClientLevel level, float from, float to) {
            for (FormationShapePath.Dot dot : FormationShapePath.band(this.options.shape().value(), this.center,
                    this.options.radius(), from, to)) {
                // One dust per point, because a stroke has the colour and the size it asked for: both travel on the
                // point. The limiter is bypassed on purpose: the distance the client would measure is the camera's, and
                // the far side of a formation is beyond it whenever the array is bigger than 32 blocks.
                level.addParticle(new DustParticleOptions(dot.color(), dot.size()), true, false,
                        dot.position().x, dot.position().y, dot.position().z, 0.0D, 0.0D, 0.0D);
            }
        }
    }
}
