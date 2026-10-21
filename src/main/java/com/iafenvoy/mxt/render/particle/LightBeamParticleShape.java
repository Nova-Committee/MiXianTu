package com.iafenvoy.mxt.render.particle;

import com.iafenvoy.mxt.particle.LightBeamParticleOptions;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public final class LightBeamParticleShape {
    public static final int SIDES = 8;
    public static final int MAX_CHANNELS = 1024;
    public static final int MAX_SEGMENTS = 2048;
    public static final int SIDE_VERTICES_PER_LAYER = SIDES * 4 * 4;
    public static final int CAP_VERTICES_PER_CAP = SIDES * 4;
    public static final int CAP_VERTICES_PER_LAYER = CAP_VERTICES_PER_CAP * 2;
    public static final int VERTICES_PER_SEGMENT = SIDE_VERTICES_PER_LAYER * 2 + CAP_VERTICES_PER_LAYER * 2;
    private static final int CURVED_SECTIONS = 16;
    private static final double[] RINGS = {0.0D, 0.2D, 0.5D, 0.8D, 1.0D};
    private static final double[] CYLINDER_RINGS = {0.0D, 0.25D, 0.5D, 0.75D, 1.0D};
    private static final double[] CIRCLE_X = new double[SIDES + 1];
    private static final double[] CIRCLE_Z = new double[SIDES + 1];

    static {
        for (int side = 0; side <= SIDES; side++) {
            CIRCLE_X[side] = Math.cos(side * Math.TAU / SIDES);
            CIRCLE_Z[side] = Math.sin(side * Math.TAU / SIDES);
        }
    }

    private final LightBeamParticleOptions options;
    private final double x;
    private final double z;
    private final double lower;
    private final double height;
    private final double scale;
    private final double bendRadius;
    private final double pathLower;
    private final double pathUpper;
    private final long seed;
    private final AABB bounds;
    private final double[] spacings;
    private final double[] offsets;
    private Sample[] samples;

    public LightBeamParticleShape(LightBeamParticleOptions options, double x, double y, double z) {
        this(options, x, y, z, 0L);
    }

    public LightBeamParticleShape(LightBeamParticleOptions options, double x, double y, double z, long seed) {
        this.options = options;
        this.x = x + options.x();
        this.z = z + options.z();
        this.lower = y + options.yMin();
        this.height = (double) options.yMax() - options.yMin();
        this.scale = Math.max(options.width() * 8.0D, 1.0D);
        this.bendRadius = Math.min(this.height * 0.25D, options.terminalReach() * 0.5D);
        double extension = options.terminalReach() + (Math.PI * 0.5D - 2.0D) * this.bendRadius;
        this.pathLower = options.bottomEffect() ? -extension : 0.0D;
        this.pathUpper = this.height + (options.topEffect() ? extension : 0.0D);
        this.seed = seed;
        this.bounds = options.bounds(x, y, z);
        this.spacings = new double[this.channelCount()];
        this.offsets = new double[this.spacings.length];
        for (int channel = 0; channel < this.spacings.length; channel++) {
            this.spacings[channel] = (0.55D + 0.35D * this.random(channel, 0, 1)) * (options.spread() ? 0.62D : 1.0D);
            this.offsets[channel] = this.random(channel, 0, 2);
        }
    }

    public AABB bounds() {
        return this.bounds;
    }

    public int channelCount() {
        return (int) Math.clamp(Math.ceil(Math.PI * this.options.radius() * this.options.radius()
                * this.options.density()), 1.0D, MAX_CHANNELS);
    }

    public void segments(float age, float phase, Consumer<Segment> sink) {
        this.segments(age, phase, MAX_SEGMENTS, sink);
    }

    public void segments(float age, float phase, int limit, Consumer<Segment> sink) {
        if (limit <= 0) return;
        limit = Math.min(limit, MAX_SEGMENTS);
        float fade = Math.clamp(Math.min(age / 3.0F, (this.options.lifetime() - age) / 5.0F), 0.0F, 1.0F);
        if (fade == 0.0F) return;
        if (this.options.glow()) {
            sink.accept(new Segment(-1, 0, new Vec3(this.x, this.lower + this.height * 0.5D, this.z),
                    this.lower, this.height, this.options.radius(), this.options.radius(), 0, this.options.color(), fade));
            if (--limit == 0) return;
        }
        int channels = this.channelCount();
        int channelStride = Math.max(1, Math.ceilDiv(channels, limit));
        int firstChannel = Math.floorMod((int) this.seed, channelStride);
        int selectedChannels = Math.ceilDiv(channels - firstChannel, channelStride);
        int quota = limit / selectedChannels;
        double travel = age * (double) this.options.flowSpeed() * this.options.flowDirection().sign() * 0.24D - phase;
        for (int channel = firstChannel; channel < channels; channel += channelStride) {
            double spacing = this.spacings[channel], offset = this.offsets[channel];
            double bottom = (this.pathLower / this.scale - travel) * spacing + offset;
            double top = (this.pathUpper / this.scale - travel) * spacing + offset;
            int stride = Math.max(1, (int) Math.ceil((Math.ceil((this.pathUpper - this.pathLower) / this.scale * spacing) + 2.0D) / quota));
            long first = (long) Math.floor(bottom) - 1L;
            long last = (long) Math.floor(top);
            first += Math.floorMod(-first, stride);
            for (long cell = first; cell <= last; cell += stride) {
                Sample sample = this.sample(channel, cell);
                double from = ((cell + sample.start - offset) / spacing + travel) * this.scale;
                if (from >= this.pathUpper || from + sample.length <= this.pathLower) continue;
                sink.accept(new Segment(channel, cell, new Vec3(this.x + sample.x,
                        this.lower + from + sample.length * 0.5D, this.z + sample.z), this.lower + from, sample.length,
                        sample.coreRadius, sample.haloRadius, sample.softness, sample.color, fade * sample.opacity));
            }
        }
    }

    private Sample sample(int channel, long cell) {
        if (this.samples == null) {
            int capacity = (int) Math.clamp(Math.ceil((this.pathUpper - this.pathLower) / this.scale + 3) * this.channelCount() * 2,
                    16, MAX_SEGMENTS * 2);
            this.samples = new Sample[Integer.highestOneBit(capacity - 1) << 1];
        }
        long hash = (cell ^ channel * 0x9E3779B97F4A7C15L) * 0xBF58476D1CE4E5B9L;
        int index = (int) (hash ^ hash >>> 32) & (this.samples.length - 1);
        Sample cached = this.samples[index];
        if (cached != null && cached.channel == channel && cached.cell == cell) return cached;
        double variation = this.random(channel, cell, 13), widthVariation = this.random(channel, cell, 53);
        double length = this.options.spread() ? 0.44D + 0.28D * variation + 0.14D * widthVariation : 0.18D + 0.58D * variation;
        double start = 0.05D + (0.9D - length) * this.random(channel, cell, 29);
        double size = length / this.spacings[channel] * this.scale;
        double softness = smoothstep(0.35D, 0.9D, this.random(channel, cell, 97));
        double coreRadius = this.options.width() * (this.options.spread() ? 0.30D + 0.28D * widthVariation
                : (0.06D + 0.12D * widthVariation) * (1.0D + 1.6D * softness));
        coreRadius = Math.min(coreRadius, size * 0.18D);
        double haloRadius = Math.min(Math.min(coreRadius * (this.options.spread() ? 1.6D + 0.9D * softness : 2.2D + 2.5D * softness),
                this.options.strandHalfWidth()), size * 0.45D);
        double radius = this.options.radius() * (this.options.filled() ? Math.sqrt(this.random(channel, cell, 101)) : 1.0D);
        double angle = this.random(channel, cell, 103) * Math.TAU;
        float opacity = (float) (0.24D + 0.76D * this.random(channel, cell, 67));
        double brightness = 0.55D + 0.45D * this.random(channel, cell, 79);
        int color = ARGB.color(ARGB.alpha(this.options.color()), (int) (ARGB.red(this.options.color()) * brightness),
                (int) (ARGB.green(this.options.color()) * brightness), (int) (ARGB.blue(this.options.color()) * brightness));
        return this.samples[index] = new Sample(channel, cell, start, size, coreRadius, haloRadius,
                Math.cos(angle) * radius, Math.sin(angle) * radius, (float) softness, opacity, color);
    }

    public AABB segmentBounds(Segment segment) {
        if (segment.glow()) return new AABB(this.x - segment.coreRadius, this.lower, this.z - segment.coreRadius,
                this.x + segment.coreRadius, this.lower + this.height, this.z + segment.coreRadius);
        Vec3 a = this.frame(segment, Math.max(segment.bottom, this.lower + this.pathLower)).center;
        Vec3 b = this.frame(segment, Math.min(segment.bottom + segment.length, this.lower + this.pathUpper)).center;
        return new AABB(Math.min(segment.center.x(), Math.min(a.x(), b.x())), Math.min(a.y(), b.y()),
                Math.min(segment.center.z(), Math.min(a.z(), b.z())), Math.max(segment.center.x(), Math.max(a.x(), b.x())),
                Math.max(a.y(), b.y()), Math.max(segment.center.z(), Math.max(a.z(), b.z()))).inflate(segment.haloRadius);
    }

    public void writeInstance(Segment segment, Vec3 camera, ByteBuffer target) {
        double dx = segment.center.x() - this.x, dz = segment.center.z() - this.z;
        double distance = Math.hypot(dx, dz);
        if (distance < 1.0E-10D) {
            double angle = this.random(segment.channel, segment.cell, 107) * Math.TAU;
            dx = Math.cos(angle);
            dz = Math.sin(angle);
        } else {
            dx /= distance;
            dz /= distance;
        }
        put(target, (float) (segment.center.x() - camera.x()), (float) (this.lower - camera.y()),
                (float) (segment.center.z() - camera.z()), (float) this.height);
        put(target, (float) (segment.bottom - this.lower), (float) segment.length, (float) segment.coreRadius, (float) segment.haloRadius);
        put(target, (float) dx, (float) dz, segment.glow() ? 2 : this.options.spread() ? 1 : 0, this.options.width());
        put(target, !segment.glow() && this.options.bottomEffect() ? 1 : 0, !segment.glow() && this.options.topEffect() ? 1 : 0,
                this.options.terminalReach(), (float) this.bendRadius);
        put(target, ARGB.red(segment.color) / 255.0F, ARGB.green(segment.color) / 255.0F,
                ARGB.blue(segment.color) / 255.0F, ARGB.alpha(segment.color) / 255.0F);
        put(target, segment.alpha, segment.softness, (float) (Math.max(segment.bottom, this.lower + this.pathLower) - this.lower),
                (float) (Math.min(segment.bottom + segment.length, this.lower + this.pathUpper) - this.lower));
    }

    private static void put(ByteBuffer target, float a, float b, float c, float d) {
        target.putFloat(a).putFloat(b).putFloat(c).putFloat(d);
    }

    private record Sample(int channel, long cell, double start, double length, double coreRadius, double haloRadius,
                          double x, double z, float softness, float opacity, int color) {
    }

    public void write(Vec3 camera, float age, float phase, VertexSink sink) {
        this.segments(age, phase, segment -> this.writeSegment(segment, camera, sink));
    }

    public void writeSegment(Segment segment, Vec3 camera, VertexSink sink) {
        double from = Math.max(0.0D, (this.lower + this.pathLower - segment.bottom) / segment.length);
        double to = Math.min(1.0D, (this.lower + this.pathUpper - segment.bottom) / segment.length);
        boolean curved = this.curved(segment);
        double[] rings = this.spread(segment) ? RINGS : CYLINDER_RINGS;
        int sections = curved ? CURVED_SECTIONS : rings.length - 1;
        for (int layer = 0; layer < 2; layer++)
            for (int ring = 0; ring < sections; ring++) {
                double bottom = curved ? this.curvedProgress(segment, ring, from, to) : Math.clamp(rings[ring], from, to);
                double top = curved ? this.curvedProgress(segment, ring + 1, from, to) : Math.clamp(rings[ring + 1], from, to);
                double radius = layer == 0 ? segment.coreRadius : segment.haloRadius;
                double bottomRadius = radius * this.radiusScale(segment, bottom);
                double topRadius = radius * this.radiusScale(segment, top);
                double bottomY = segment.bottom + segment.length * bottom, topY = segment.bottom + segment.length * top;
                Frame bottomFrame = this.frame(segment, bottomY), topFrame = this.frame(segment, topY);
                int bottomColor = this.color(segment, layer, bottomY), topColor = this.color(segment, layer, topY);
                for (int side = 0; side < SIDES; side++) {
                    this.vertex(segment, camera, bottomFrame, layer, side, bottom, bottomRadius, bottomColor, curved, false, sink);
                    this.vertex(segment, camera, topFrame, layer, side, top, topRadius, topColor, curved, false, sink);
                    this.vertex(segment, camera, topFrame, layer, side + 1, top, topRadius, topColor, curved, false, sink);
                    this.vertex(segment, camera, bottomFrame, layer, side + 1, bottom, bottomRadius, bottomColor, curved, false, sink);
                }
            }
        if (!this.spread(segment))
            for (int layer = 0; layer < 2; layer++) {
                double radius = layer == 0 ? segment.coreRadius : segment.haloRadius;
                this.cap(segment, camera, layer, radius, from, false, sink);
                this.cap(segment, camera, layer, radius, to, true, sink);
            }
    }

    public int vertexCount(Segment segment) {
        return SIDES * 4 * (this.curved(segment) ? CURVED_SECTIONS : 4) * 2
                + (this.spread(segment) ? 0 : CAP_VERTICES_PER_LAYER * 2);
    }

    public Vec3 sortCenter(Segment segment) {
        double clippedBottom = Math.max(segment.bottom, this.lower + this.pathLower);
        double clippedTop = Math.min(segment.bottom + segment.length, this.lower + this.pathUpper);
        return this.frame(segment, (clippedBottom + clippedTop) * 0.5D).center;
    }

    private boolean curved(Segment segment) {
        return !segment.glow() && (this.options.bottomEffect() && segment.bottom < this.lower + this.bendRadius
                || this.options.topEffect() && segment.bottom + segment.length > this.lower + this.height - this.bendRadius);
    }

    private double curvedProgress(Segment segment, int ring, double from, double to) {
        double first = this.options.bottomEffect() ? this.lower + this.bendRadius * (1.0D - Math.PI * 0.5D) : this.lower;
        double second = this.options.bottomEffect() ? this.lower + this.bendRadius : this.lower;
        double third = this.options.topEffect() ? this.lower + this.height - this.bendRadius : this.lower + this.height;
        double fourth = this.options.topEffect() ? this.lower + this.height + this.bendRadius * (Math.PI * 0.5D - 1.0D)
                : this.lower + this.height;
        double a = Math.clamp((first - segment.bottom) / segment.length, from, to);
        double b = Math.clamp((second - segment.bottom) / segment.length, from, to);
        double c = Math.clamp((third - segment.bottom) / segment.length, from, to);
        double d = Math.clamp((fourth - segment.bottom) / segment.length, from, to);
        if (ring <= 1) return from + (a - from) * ring;
        if (ring <= 7) return a + (b - a) * (ring - 1) / 6.0D;
        if (ring <= 9) return b + (c - b) * (ring - 7) / 2.0D;
        if (ring <= 15) return c + (d - c) * (ring - 9) / 6.0D;
        return to;
    }

    private Frame frame(Segment segment, double y) {
        double position = y - this.lower;
        boolean bottom = !segment.glow() && this.options.bottomEffect() && position < this.bendRadius;
        boolean top = !segment.glow() && this.options.topEffect() && position > this.height - this.bendRadius;
        if (!bottom && !top) return new Frame(new Vec3(segment.center.x(), y, segment.center.z()), 0, 0, 0, 1);
        double dx = segment.center.x() - this.x, dz = segment.center.z() - this.z;
        double distance = Math.hypot(dx, dz);
        if (distance < 1.0E-10D) {
            double angle = this.random(segment.channel, segment.cell, 107) * Math.TAU;
            dx = Math.cos(angle);
            dz = Math.sin(angle);
        } else {
            dx /= distance;
            dz /= distance;
        }
        double along = bottom ? this.bendRadius - position : position - this.height + this.bendRadius;
        double angle = Math.min(along / this.bendRadius, Math.PI * 0.5D);
        double sin = Math.sin(angle), cos = Math.cos(angle);
        double outward = this.bendRadius * (1.0D - cos) + Math.max(0.0D, along - this.bendRadius * Math.PI * 0.5D);
        double planeDistance = this.bendRadius * (1.0D - sin);
        Vec3 center = new Vec3(segment.center.x() + dx * outward,
                this.lower + (bottom ? planeDistance : this.height - planeDistance), segment.center.z() + dz * outward);
        return new Frame(center, dx, dz, bottom ? -sin : sin, cos);
    }

    private boolean spread(Segment segment) {
        return !segment.glow() && this.options.spread();
    }

    private double radiusScale(Segment segment, double progress) {
        if (this.spread(segment)) return 4.0D * progress * (1.0D - progress);
        return 1.0D;
    }

    private void cap(Segment segment, Vec3 camera, int layer, double radius, double progress, boolean top, VertexSink sink) {
        double y = segment.bottom + segment.length * progress;
        int color = this.color(segment, layer, y, true);
        Frame frame = this.frame(segment, y);
        boolean curved = this.curved(segment);
        for (int side = 0; side < SIDES; side++) {
            this.vertex(segment, camera, frame, layer, side, progress, 0.0D, color, curved, true, sink);
            this.vertex(segment, camera, frame, layer, top ? side + 1 : side, progress, radius, color, curved, true, sink);
            this.vertex(segment, camera, frame, layer, top ? side : side + 1, progress, radius, color, curved, true, sink);
            this.vertex(segment, camera, frame, layer, side, progress, 0.0D, color, curved, true, sink);
        }
    }

    private int color(Segment segment, int layer, double y) {
        return this.color(segment, layer, y, false);
    }

    private int color(Segment segment, int layer, double y, boolean isCap) {
        if (segment.glow()) return ARGB.multiplyAlpha(segment.color, segment.alpha);
        double fadeRange = Math.min(this.height * 0.08D, this.options.width() * 2.0D);
        double position = y - this.lower;
        boolean plain = isCap || !this.options.spread();
        double bottomFade = this.options.bottomEffect()
                ? smoothstep(this.pathLower, this.pathLower + this.options.terminalReach() * 0.3D, position)
                : plain ? 1.0D : Math.clamp(position / fadeRange, 0.0D, 1.0D);
        double topFade = this.options.topEffect()
                ? 1.0D - smoothstep(this.pathUpper - this.options.terminalReach() * 0.3D, this.pathUpper, position)
                : plain ? 1.0D : Math.clamp((this.height - position) / fadeRange, 0.0D, 1.0D);
        float strength = layer == 0 ? 0.85F - 0.45F * segment.softness : 0.12F + 0.16F * segment.softness;
        return ARGB.multiplyAlpha(segment.color, (float) (segment.alpha * bottomFade * topFade * strength));
    }

    private void vertex(Segment segment, Vec3 camera, Frame frame, int layer, int side, double progress, double radius, int color,
                        boolean curved, boolean cap, VertexSink sink) {
        // Subtract the camera before packing floats to preserve distant-world precision.
        float modeOffset = (this.spread(segment) ? 0.0F : 4.0F) + (cap ? 8.0F : 0.0F)
                + (curved ? 16.0F : 0.0F) + (segment.glow() ? 32.0F : 0.0F);
        double rx = CIRCLE_X[side] * radius * (segment.glow() && layer == 0 ? -1 : 1), rz = CIRCLE_Z[side] * radius;
        double projected = frame.dx * rx + frame.dz * rz;
        sink.add((float) (frame.center.x() - camera.x() + rx + (frame.cos - 1.0D) * frame.dx * projected),
                (float) (frame.center.y() - camera.y() - frame.sin * projected),
                (float) (frame.center.z() - camera.z() + rz + (frame.cos - 1.0D) * frame.dz * projected),
                (float) side / SIDES + layer * 2.0F + modeOffset, (float) progress, color);
    }

    private record Frame(Vec3 center, double dx, double dz, double sin, double cos) {
    }

    private double random(int channel, long cell, int salt) {
        long value = this.seed ^ channel * 0x9E3779B97F4A7C15L ^ cell * 0xD1B54A32D192ED03L ^ salt * 0x94D049BB133111EBL;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return ((value ^ value >>> 31) >>> 11) * 0x1.0p-53D;
    }

    private static double smoothstep(double from, double to, double value) {
        double t = Math.clamp((value - from) / (to - from), 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    public record Segment(int channel, long cell, Vec3 center, double bottom, double length,
                          double coreRadius, double haloRadius, float softness, int color, float alpha) {
        public boolean glow() {
            return this.channel < 0;
        }
    }

    @FunctionalInterface
    public interface VertexSink {
        void add(float x, float y, float z, float u, float v, int color);
    }
}
