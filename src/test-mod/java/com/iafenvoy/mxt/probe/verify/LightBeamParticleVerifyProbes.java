package com.iafenvoy.mxt.probe.verify;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.data.ParticleEffect;
import com.iafenvoy.mxt.particle.LightBeamParticleOptions;
import com.iafenvoy.mxt.particle.LightBeamParticleOptions.FlowDirection;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.render.particle.LightBeamParticleFrame;
import com.iafenvoy.mxt.render.particle.LightBeamParticleMesh;
import com.iafenvoy.mxt.render.particle.LightBeamParticleShape;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class LightBeamParticleVerifyProbes {
    private LightBeamParticleVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/light_beam/geometry", LightBeamParticleVerifyProbes::geometry),
                Probe.of("verify/light_beam/flow", LightBeamParticleVerifyProbes::flow),
                Probe.of("verify/light_beam/culling", LightBeamParticleVerifyProbes::culling),
                Probe.of("verify/light_beam/delivery", LightBeamParticleVerifyProbes::delivery),
                Probe.of("verify/light_beam/spread", LightBeamParticleVerifyProbes::spread),
                Probe.of("verify/light_beam/terminals", LightBeamParticleVerifyProbes::terminals),
                Probe.of("verify/light_beam/instancing", LightBeamParticleVerifyProbes::instancing),
                Probe.of("verify/light_beam/filled_glow", LightBeamParticleVerifyProbes::filledGlow));
    }

    private static void filledGlow(ProbeContext context) {
        LightBeamParticleOptions defaults = LightBeamParticleOptions.CODEC.codec().parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        expect(context, "the default beam fills its entire disk", defaults.filled(), true);
        expect(context, "stationary column glow defaults to disabled", defaults.glow(), false);
        Vec3 origin = new Vec3(29_999_900.125D, 80.25D, -29_999_900.375D);
        for (boolean filled : new boolean[]{false, true})
            for (boolean glow : new boolean[]{false, true})
                for (boolean spread : new boolean[]{false, true})
                    for (boolean terminals : new boolean[]{false, true})
                        for (FlowDirection direction : FlowDirection.values()) {
                            String label = " filled=" + filled + " glow=" + glow + " spread=" + spread
                                    + " terminals=" + terminals + " direction=" + direction;
                            LightBeamParticleOptions options = new LightBeamParticleOptions(0xCC55CCFF, 3, -2, -4, 8, .18F,
                                    direction, 2, 200, spread, 2, 4, terminals, terminals, filled, glow);
                            LightBeamParticleShape shape = new LightBeamParticleShape(options, origin.x(), origin.y(), origin.z(), 117L);
                            List<LightBeamParticleShape.Segment> before = segments(shape, 20, 0);
                            List<LightBeamParticleShape.Segment> later = segments(shape, 40, 9);
                            List<LightBeamParticleShape.Segment> glowSegments = before.stream().filter(LightBeamParticleShape.Segment::glow).toList();
                            expect(context, "one optional stationary glow instance" + label, glowSegments.size(), glow ? 1 : 0);
                            boolean placement = true, filledInterior = false, bounds = true, finite = true;
                            for (LightBeamParticleShape.Segment segment : before) {
                                if (!segment.glow()) {
                                    double radius = Math.hypot(segment.center().x() - origin.x() - options.x(),
                                            segment.center().z() - origin.z() - options.z());
                                    placement &= filled ? radius <= options.radius() + 1.0E-7D : Math.abs(radius - options.radius()) < 1.0E-7D;
                                    filledInterior |= radius < options.radius() * .5D;
                                }
                                ByteBuffer data = ByteBuffer.allocate(LightBeamParticleMesh.INSTANCE_BYTES).order(ByteOrder.nativeOrder());
                                shape.writeInstance(segment, origin, data);
                                expect(context, "the fixed instance layout is preserved" + label, data.position(), LightBeamParticleMesh.INSTANCE_BYTES);
                                for (int offset = 0; offset < data.position(); offset += 4)
                                    finite &= Float.isFinite(data.getFloat(offset));
                                expect(context, "shader mode identifies stationary glow" + label,
                                        data.getFloat(40), segment.glow() ? 2.0F : spread ? 1.0F : 0.0F);
                                AABB box = shape.segmentBounds(segment).inflate(1.0E-4D);
                                for (Vertex vertex : vertices(shape, segment, origin)) {
                                    finite &= vertex.finite();
                                    Vec3 world = new Vec3(vertex.x + origin.x(), vertex.y + origin.y(), vertex.z + origin.z());
                                    bounds &= box.contains(world) && shape.bounds().inflate(1.0E-4D).contains(world);
                                }
                            }
                            expect(context, "streak placement follows filled" + label, placement, true);
                            expect(context, "the filled mode includes interior streaks" + label, !filled || filledInterior, true);
                            expect(context, "CPU reference and instance fields remain finite" + label, finite, true);
                            expect(context, "the complete and per-instance bounds contain every vertex" + label, bounds, true);
                            if (glow) {
                                LightBeamParticleShape.Segment column = glowSegments.getFirst();
                                expect(context, "glow has no age or phase motion" + label,
                                        later.stream().filter(LightBeamParticleShape.Segment::glow).toList(), glowSegments);
                                close(context, "glow starts at the lower plane" + label, column.bottom(), origin.y() + options.yMin());
                                close(context, "glow spans the whole vertical column" + label, column.length(), 12);
                                close(context, "glow follows the beam radius" + label, column.coreRadius(), options.radius());
                                ByteBuffer data = ByteBuffer.allocate(LightBeamParticleMesh.INSTANCE_BYTES).order(ByteOrder.nativeOrder());
                                shape.writeInstance(column, origin, data);
                                expect(context, "glow ignores both terminal bends" + label, data.getFloat(48) == 0 && data.getFloat(52) == 0, true);
                                expect(context, "glow has no life before birth" + label, segments(shape, 0, 0).isEmpty(), true);
                                expect(context, "glow disappears after its lifetime" + label, segments(shape, 200, 0).isEmpty(), true);
                            }
                            for (int limit : new int[]{0, 1, 2, 16}) {
                                List<LightBeamParticleShape.Segment> limited = new ArrayList<>();
                                shape.segments(20, 0, limit, limited::add);
                                expect(context, "glow and streaks share the quota limit=" + limit + label, limited.size() <= limit, true);
                            }
                        }
        LightBeamParticleFrame frame = new LightBeamParticleFrame();
        LightBeamParticleOptions glowing = new LightBeamParticleOptions(0xFFFFFFFF, 0, 0, 0, 12, .18F, FlowDirection.UP, 1,
                200, true, 2, 16, true, true, false, true);
        for (int beam = 0; beam < 200; beam++)
            frame.add(new LightBeamParticleShape(glowing, beam % 20, 0, beam / 20, beam), 20, 0, Vec3.ZERO);
        frame.build(Vec3.ZERO, box -> true);
        expect(context, "glow participates in the global frame budget", frame.entries().size() <= LightBeamParticleMesh.MAX_INSTANCES, true);
        expect(context, "each accepted visible beam keeps its glow", frame.entries().stream().filter(entry -> entry.segment().glow()).count(), 200L);
        frame.build(Vec3.ZERO, box -> false);
        expect(context, "the glow obeys per-instance frustum culling", frame.entries().isEmpty(), true);
        LightBeamParticleOptions tiny = new LightBeamParticleOptions(0xFFFFFFFF, 0, 0, 0, .001F, .18F, FlowDirection.UP, 1,
                200, true, 64, 1, true, true, false, true);
        LightBeamParticleShape tinyShape = new LightBeamParticleShape(tiny, 0, 0, 0, 117L);
        LightBeamParticleShape.Segment tinyGlow = segments(tinyShape, 20, 0).stream().filter(LightBeamParticleShape.Segment::glow).findFirst().orElseThrow();
        expect(context, "very short wide glow has finite geometry", vertices(tinyShape, tinyGlow, Vec3.ZERO).stream().allMatch(Vertex::finite), true);
    }

    private static void instancing(ProbeContext context) {
        for (int lod = 0; lod < LightBeamParticleMesh.LOD_COUNT; lod++) {
            List<float[]> vertices = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();
            LightBeamParticleMesh.write(lod, (x, y, ring, mode, u) -> vertices.add(new float[]{x, y, ring, mode, u}), indices::add);
            expect(context, "LOD indices stay within the immutable template lod=" + lod,
                    indices.stream().allMatch(index -> index >= 0 && index < vertices.size()), true);
        }
        expect(context, "near LOD keeps eight sides", LightBeamParticleMesh.lod(31 * 31), 0);
        expect(context, "middle LOD begins at thirty-two blocks", LightBeamParticleMesh.lod(32 * 32), 1);
        expect(context, "far LOD begins at ninety-six blocks", LightBeamParticleMesh.lod(96 * 96), 2);
        Vec3 origin = new Vec3(29_999_900.125D, 80.25D, -29_999_900.375D);
        boolean packed = true, contained = true, stable = true, bounded = true;
        for (boolean bottom : new boolean[]{false, true})
            for (boolean top : new boolean[]{false, true})
                for (boolean spread : new boolean[]{false, true})
                    for (FlowDirection direction : FlowDirection.values()) {
                        LightBeamParticleShape shape = new LightBeamParticleShape(new LightBeamParticleOptions(0xCC55CCFF, 0, 0, 0, 12, .18F,
                                direction, 1, 200, spread, 1, 16, bottom, top), origin.x(), origin.y(), origin.z(), 117L);
                        List<LightBeamParticleShape.Segment> baseline = segments(shape, 20, 0);
                        segments(shape, 150, 30);
                        stable &= baseline.equals(segments(shape, 20, 0));
                        for (LightBeamParticleShape.Segment segment : baseline) {
                            ByteBuffer data = ByteBuffer.allocate(LightBeamParticleMesh.INSTANCE_BYTES).order(ByteOrder.nativeOrder());
                            shape.writeInstance(segment, origin, data);
                            packed &= data.position() == LightBeamParticleMesh.INSTANCE_BYTES;
                            for (int i = 0; i < data.position(); i += 4) packed &= Float.isFinite(data.getFloat(i));
                            packed &= Math.abs(data.getFloat(0) - (segment.center().x() - origin.x())) < 1.0E-5D;
                            packed &= data.getFloat(44) == .18F && data.getFloat(48) == (bottom ? 1 : 0) && data.getFloat(52) == (top ? 1 : 0);
                            AABB segmentBounds = shape.segmentBounds(segment).inflate(1.0E-4D);
                            contained &= vertices(shape, segment, origin).stream().allMatch(vertex -> segmentBounds.contains(origin.add(point(vertex))));
                        }
                        for (int quota : new int[]{0, 1, 2, 7, 128, 2048}) {
                            List<LightBeamParticleShape.Segment> selected = new ArrayList<>();
                            shape.segments(20, 0, quota, selected::add);
                            bounded &= selected.size() <= quota;
                        }
                    }
        expect(context, "all sixteen modes pack finite camera-relative ninety-six-byte records", packed, true);
        expect(context, "per-streak culling includes every actual core and halo vertex", contained, true);
        expect(context, "bounded sample cache preserves seeds after eviction", stable, true);
        expect(context, "channel and cell thinning obey every frame quota", bounded, true);
        LightBeamParticleFrame frame = new LightBeamParticleFrame();
        for (int beam = 0; beam < 200; beam++)
            frame.add(new LightBeamParticleShape(new LightBeamParticleOptions(0xFFFFFFFF, 0, 0, 0, 12, .18F,
                    FlowDirection.UP, 1, 200, true, 2, 16, true, true), beam % 20, 0, beam / 20, beam), 20, 0, Vec3.ZERO);
        frame.build(Vec3.ZERO, box -> true);
        expect(context, "many beams respect the global instance budget", frame.entries().size() <= LightBeamParticleMesh.MAX_INSTANCES, true);
        boolean sorted = true;
        for (int i = 1; i < frame.entries().size(); i++)
            sorted &= frame.entries().get(i - 1).distanceSquared() >= frame.entries().get(i).distanceSquared();
        expect(context, "transparent batches preserve global far-to-near order", sorted, true);
        frame.build(Vec3.ZERO, box -> false);
        expect(context, "individual streak culling rejects entirely hidden streams", frame.entries().isEmpty(), true);
        frame.clear();
        expect(context, "clearing a frame drops previous requests", frame.entries().isEmpty(), true);
    }

    private static LightBeamParticleOptions options(float x, float z, float lower, float upper, float width,
                                                    FlowDirection direction, float speed) {
        return options(x, z, lower, upper, width, direction, speed, LightBeamParticleOptions.DEFAULT_RADIUS);
    }

    private static LightBeamParticleOptions options(float x, float z, float lower, float upper, float width,
                                                    FlowDirection direction, float speed, float radius) {
        return new LightBeamParticleOptions(0xCC55CCFF, x, z, lower, upper, width, direction, speed, 200, false, radius, LightBeamParticleOptions.DEFAULT_DENSITY);
    }

    private static void spread(ProbeContext context) {
        LightBeamParticleOptions defaults = LightBeamParticleOptions.CODEC.codec().parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        expect(context, "spread defaults to false", defaults.spread(), false);
        close(context, "radius defaults to half a block", defaults.radius(), 0.5D);
        close(context, "density defaults to sixteen channels per square block", defaults.density(), 16.0D);
        for (float invalid : new float[]{-1.0F, 0.0F, 1025.0F, Float.NaN, Float.POSITIVE_INFINITY}) {
            JsonObject json = new JsonObject();
            json.addProperty("density", invalid);
            expect(context, "invalid density is rejected=" + invalid,
                    LightBeamParticleOptions.CODEC.codec().parse(JsonOps.INSTANCE, json).result().isPresent(), false);
        }
        for (float invalid : new float[]{-1.0F, 0.0F, 65.0F}) {
            JsonObject json = new JsonObject();
            json.addProperty("radius", invalid);
            expect(context, "invalid radius is rejected=" + invalid,
                    LightBeamParticleOptions.CODEC.codec().parse(JsonOps.INSTANCE, json).result().isPresent(), false);
        }
        for (boolean enabled : new boolean[]{false, true}) {
            JsonObject json = new JsonObject();
            json.addProperty("color", "#CC55CCFF");
            json.addProperty("x", 3.0F);
            json.addProperty("z", -2.0F);
            json.addProperty("y_min", -4.0F);
            json.addProperty("y_max", 128.0F);
            json.addProperty("width", 0.5F);
            json.addProperty("radius", 2.25F);
            json.addProperty("density", 3.5F);
            json.addProperty("flow_direction", "down");
            json.addProperty("flow_speed", 1.5F);
            json.addProperty("lifetime", 200);
            json.addProperty("spread", enabled);
            LightBeamParticleOptions options = LightBeamParticleOptions.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
            expect(context, "JSON selects spread=" + enabled, options.spread(), enabled);
            json.addProperty("type", "mxt:light_beam");
            expect(context, "the registered particle codec selects spread=" + enabled,
                    ParticleTypes.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(), options);
            JsonObject effect = new JsonObject();
            effect.add("particle", json);
            expect(context, "particle options spread does not replace emission spread=" + enabled,
                    ParticleEffect.CODEC.parse(JsonOps.INSTANCE, effect).getOrThrow().spread(), new Vec3(0.5D, 0.5D, 0.5D));
            LightBeamParticleOptions normal = new LightBeamParticleOptions(options.color(), options.x(), options.z(),
                    options.yMin(), options.yMax(), options.width(), options.flowDirection(), options.flowSpeed(), options.lifetime(),
                    false, options.radius(), options.density());
            LightBeamParticleShape shape = new LightBeamParticleShape(options, 0, 0, 0);
            expect(context, "spread preserves complete culling bounds=" + enabled, shape.bounds(), normal.bounds(0, 0, 0));
            LightBeamParticleShape.Segment sample = segments(shape, 20, 0).stream().filter(segment -> segment.bottom() > options.yMin()
                    && segment.bottom() + segment.length() < options.yMax()).findFirst().orElseThrow();
            Vec3 camera = sample.center();
            List<Vertex> mesh = vertices(shape, sample, camera);
            expect(context, "spread selects the correct shader end profile=" + enabled,
                    mesh.getFirst().u >= 4.0F, !enabled);
            for (int layer = 0; layer < 2; layer++) {
                int offset = layer * LightBeamParticleShape.SIDES * 4 * 4;
                double radius = layer == 0 ? sample.coreRadius() : sample.haloRadius();
                double endRadius = enabled ? radius * 0.64D : radius;
                for (int index : new int[]{1, 65}) {
                    Vertex vertex = mesh.get(offset + index);
                    close(context, "spread selects the end radius mode=" + enabled + " layer=" + layer + " vertex=" + index,
                            Math.hypot(vertex.x, vertex.z), endRadius);
                }
                close(context, "both modes preserve the middle radius mode=" + enabled + " layer=" + layer,
                        Math.hypot(mesh.get(offset + 33).x, mesh.get(offset + 33).z), radius);
                if (!enabled) {
                    for (int ringVertex : new int[]{0, 1, 33, 65, 97})
                        close(context, "cylinder keeps constant radius at ring=" + ringVertex + " layer=" + layer,
                                Math.hypot(mesh.get(offset + ringVertex).x, mesh.get(offset + ringVertex).z), radius);
                    int capOffset = LightBeamParticleShape.SIDE_VERTICES_PER_LAYER * 2
                            + layer * LightBeamParticleShape.CAP_VERTICES_PER_LAYER;
                    Vertex bottomCenter = mesh.get(capOffset), bottomRim = mesh.get(capOffset + 1), bottomNext = mesh.get(capOffset + 2);
                    int topOffset = capOffset + LightBeamParticleShape.CAP_VERTICES_PER_CAP;
                    Vertex topCenter = mesh.get(topOffset), topRim = mesh.get(topOffset + 1), topNext = mesh.get(topOffset + 2);
                    close(context, "the bottom disk is flat layer=" + layer, bottomCenter.y, bottomRim.y);
                    close(context, "the top disk is flat layer=" + layer, topCenter.y, topRim.y);
                    close(context, "the bottom disk lies at the segment start layer=" + layer,
                            bottomCenter.y, sample.bottom() - camera.y());
                    close(context, "the top disk lies at the segment end layer=" + layer,
                            topCenter.y, sample.bottom() + sample.length() - camera.y());
                    expect(context, "the bottom cap faces downward layer=" + layer,
                            point(bottomRim).subtract(point(bottomCenter)).cross(point(bottomNext).subtract(point(bottomCenter))).y() < 0, true);
                    expect(context, "the top cap faces upward layer=" + layer,
                            point(topRim).subtract(point(topCenter)).cross(point(topNext).subtract(point(topCenter))).y() > 0, true);
                }
            }
            if (enabled) {
                List<LightBeamParticleShape.Segment> baseline = segments(new LightBeamParticleShape(normal, 0, 0, 0), 20, 0);
                List<LightBeamParticleShape.Segment> wide = segments(shape, 20, 0);
                int compared = 0;
                for (LightBeamParticleShape.Segment segment : wide) {
                    Optional<LightBeamParticleShape.Segment> match = baseline.stream().filter(other -> other.channel() == segment.channel() && other.cell() == segment.cell()).findFirst();
                    if (match.isEmpty()) continue;
                    expect(context, "spread lengthens each matching streak channel=" + segment.channel(), segment.length() > match.get().length(), true);
                    compared++;
                }
                expect(context, "spread comparison covers actual segments", compared > 0, true);
            }
        }
    }

    private static void terminals(ProbeContext context) {
        LightBeamParticleOptions defaults = LightBeamParticleOptions.CODEC.codec().parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        expect(context, "bottom effect defaults to disabled", defaults.bottomEffect(), false);
        expect(context, "top effect defaults to disabled", defaults.topEffect(), false);
        Vec3 origin = new Vec3(29_999_900.125D, 80.25D, -29_999_900.375D);
        for (boolean bottom : new boolean[]{false, true})
            for (boolean top : new boolean[]{false, true})
                for (boolean spread : new boolean[]{false, true})
                    for (FlowDirection direction : FlowDirection.values()) {
                        String label = " bottom=" + bottom + " top=" + top + " spread=" + spread + " direction=" + direction;
                        LightBeamParticleOptions options = new LightBeamParticleOptions(0xCC55CCFF, 3, -2, 0, 12, .18F,
                                direction, 1, 200, spread, 2, 4, bottom, top);
                        LightBeamParticleShape shape = new LightBeamParticleShape(options, origin.x(), origin.y(), origin.z(), 117L);
                        List<LightBeamParticleShape.Segment> before = segments(shape, 20, 0), after = segments(shape, 20.05F, 0);
                        expect(context, "terminal segments respect the shared budget" + label, before.size() <= LightBeamParticleShape.MAX_SEGMENTS, true);
                        boolean contained = true, countMatches = true;
                        int bottomPlane = 0, topPlane = 0, movingSamples = 0;
                        double movementError = 0;
                        for (LightBeamParticleShape.Segment segment : before) {
                            List<Vertex> mesh = vertices(shape, segment, origin);
                            contained &= mesh.stream().allMatch(v -> v.finite() && shape.bounds().inflate(1.0E-4D).contains(origin.add(point(v))));
                            countMatches &= mesh.size() == shape.vertexCount(segment);
                            Vec3 center = shape.sortCenter(segment);
                            Optional<LightBeamParticleShape.Segment> next = after.stream().filter(other -> other.channel() == segment.channel() && other.cell() == segment.cell()).findFirst();
                            if (next.isEmpty()) continue;
                            Vec3 following = shape.sortCenter(next.get());
                            boolean onBottom = Math.abs(center.y() - origin.y()) < 1.0E-7D && Math.abs(following.y() - origin.y()) < 1.0E-7D;
                            boolean onTop = Math.abs(center.y() - origin.y() - 12) < 1.0E-7D && Math.abs(following.y() - origin.y() - 12) < 1.0E-7D;
                            if (!onBottom && !onTop) continue;
                            double r1 = Math.hypot(center.x() - origin.x() - options.x(), center.z() - origin.z() - options.z());
                            double r2 = Math.hypot(following.x() - origin.x() - options.x(), following.z() - origin.z() - options.z());
                            double displacement = (20.05F - 20.0F) * Math.max(options.width() * 8.0D, 1.0D) * .24D * direction.sign();
                            if (onBottom) bottomPlane++;
                            if (onTop) topPlane++;
                            double bendRadius = Math.min(3.0D, options.terminalReach() * .5D);
                            double extension = options.terminalReach() + (Math.PI * .5D - 2.0D) * bendRadius;
                            double lower = origin.y() - (bottom ? extension : 0);
                            double upper = origin.y() + 12 + (top ? extension : 0);
                            if (Math.min(segment.bottom(), next.get().bottom()) <= lower
                                    || Math.max(segment.bottom() + segment.length(), next.get().bottom() + next.get().length()) >= upper)
                                continue;
                            movementError = Math.max(movementError, Math.abs(r2 - r1 - (onBottom ? -displacement : displacement)));
                            movingSamples++;
                        }
                        expect(context, "all terminal core and halo vertices remain inside culling bounds" + label, contained, true);
                        expect(context, "buffer sizing matches every actual mesh" + label, countMatches, true);
                        expect(context, "bottom switch independently enables the lower plane" + label, bottomPlane > 0, bottom);
                        expect(context, "top switch independently enables the upper plane" + label, topPlane > 0, top);
                        expect(context, "horizontal speed has unclipped samples" + label, movingSamples > 0, bottom || top);
                        close(context, "horizontal flow keeps constant speed and reverses with the beam" + label, movementError, 0);
                    }
        LightBeamParticleOptions options = new LightBeamParticleOptions(0xFF55CCFF, 0, 0, 0, 8, .18F, FlowDirection.UP, 0, 200, false, 1, 16, true, true);
        LightBeamParticleShape shape = new LightBeamParticleShape(options, 0, 0, 0);
        LightBeamParticleShape.Segment sample = new LightBeamParticleShape.Segment(0, 0, new Vec3(.4, 0, 0), -.05D, .1D, .03D, .08D, 0, options.color(), 1);
        double bend = .75D;
        Vec3 arc = shape.sortCenter(sample);
        close(context, "bottom turn follows a quarter-circle in the radial plane X", arc.x(), .4D + bend * (1.0D - Math.cos(1.0D)));
        close(context, "bottom turn follows a quarter-circle in the radial plane Y", arc.y(), bend * (1.0D - Math.sin(1.0D)));
        LightBeamParticleShape.Segment horizontal = new LightBeamParticleShape.Segment(0, 0, new Vec3(.4, -.75D, 0), -.8D, .1D, .03D, .08D, 0, options.color(), 1);
        List<Vertex> mesh = vertices(shape, horizontal, Vec3.ZERO);
        double yDiameter = mesh.subList(0, LightBeamParticleShape.SIDES * 4).stream().mapToDouble(Vertex::y).max().orElseThrow()
                - mesh.subList(0, LightBeamParticleShape.SIDES * 4).stream().mapToDouble(Vertex::y).min().orElseThrow();
        close(context, "a horizontal streak retains its round cross-section", yDiameter, .06D);
        expect(context, "zero speed freezes both terminal streams", segments(shape, 20, 0), segments(shape, 20.5F, 0));
        LightBeamParticleShape extreme = new LightBeamParticleShape(new LightBeamParticleOptions(options.color(), 0, 0, 0, .001F, 64,
                FlowDirection.DOWN, 100, 200, false, 64, 1024, true, true), 0, 0, 0);
        List<LightBeamParticleShape.Segment> extremeSegments = segments(extreme, 20, 0);
        expect(context, "tiny height and huge terminals respect segment budget", extremeSegments.size() <= LightBeamParticleShape.MAX_SEGMENTS, true);
        expect(context, "tiny height and huge terminals remain finite and bounded", extremeSegments.stream().allMatch(segment ->
                vertices(extreme, segment, Vec3.ZERO).stream().allMatch(vertex -> vertex.finite()
                        && extreme.bounds().inflate(1.0E-4D).contains(point(vertex)))), true);
        Vec3 player = context.player().position();
        LightBeamParticleOptions wide = new LightBeamParticleOptions(options.color(), 0, 0, 0, 8, .18F, FlowDirection.UP, 1, 200, false, 8, 16, true, false);
        LightBeamParticleOptions plain = new LightBeamParticleOptions(options.color(), 0, 0, 0, 8, .18F, FlowDirection.UP, 1, 200, false, 8, 16);
        expect(context, "terminal extension does not expand vanilla delivery range", send(context, wide, player.add(45, 0, 0), false), false);
        expect(context, "a disabled terminal uses the same vanilla delivery range", send(context, plain, player.add(45, 0, 0), false), false);
        FrustumIntersection frustum = new FrustumIntersection(new Matrix4f());
        expect(context, "an extended terminal alone can keep a beam visible", visible(frustum, wide.bounds(15, 0, 0)), true);
        expect(context, "disabled terminal has no extended culling bounds", visible(frustum, plain.bounds(15, 0, 0)), false);
    }

    private static void geometry(ProbeContext context) {
        LightBeamParticleOptions options = options(3, -2, -4, 12, 0.5F, FlowDirection.UP, 1);
        Vec3 origin = new Vec3(29_999_900.125D, 80.25D, -29_999_900.375D);
        LightBeamParticleShape shape = new LightBeamParticleShape(options, origin.x(), origin.y(), origin.z(), 117L);
        AABB bounds = shape.bounds();
        expect(context, "particle shape uses complete volume bounds", bounds, options.bounds(origin.x(), origin.y(), origin.z()));
        List<LightBeamParticleShape.Segment> segments = segments(shape, 20, 0);
        expect(context, "the volume contains actual short cylinders", !segments.isEmpty(), true);
        LightBeamParticleShape.Segment sample = segments.stream().filter(segment -> segment.bottom() > bounds.minY && segment.bottom() + segment.length() < bounds.maxY).findFirst().orElseThrow();
        List<Vertex> baseline = vertices(shape, sample, origin);
        expect(context, "every cylinder has two capped eight-sided shells", baseline.size(), LightBeamParticleShape.VERTICES_PER_SEGMENT);
        Vertex first = baseline.get(1), next = baseline.get(2);
        expect(context, "adjacent sides extend in both horizontal dimensions", first.x != next.x && first.z != next.z, true);
        double minX = baseline.stream().mapToDouble(Vertex::x).min().orElseThrow();
        double maxX = baseline.stream().mapToDouble(Vertex::x).max().orElseThrow();
        double minZ = baseline.stream().mapToDouble(Vertex::z).min().orElseThrow();
        double maxZ = baseline.stream().mapToDouble(Vertex::z).max().orElseThrow();
        close(context, "a circular streak has equal X and Z diameters", maxX - minX, maxZ - minZ);
        close(context, "the core ring preserves sub-block precision at large coordinates",
                Math.hypot(baseline.get(33).x - (sample.center().x() - origin.x()),
                        baseline.get(33).z - (sample.center().z() - origin.z())), sample.coreRadius());
        Vec3 edge1 = point(baseline.get(33)).subtract(point(baseline.get(32)));
        Vec3 edge2 = point(baseline.get(34)).subtract(point(baseline.get(32)));
        expect(context, "surface winding faces outward for back-face culling", edge1.cross(edge2).x() > 0, true);
        for (int angle = 0; angle < 16; angle++) {
            double radians = angle * Math.PI / 8.0D;
            Vec3 camera = origin.add(Math.sin(radians) * 8, 2, Math.cos(radians) * 8);
            List<Vertex> actual = vertices(shape, sample, camera);
            double drift = 0;
            for (int index = 0; index < actual.size(); index++)
                drift = Math.max(drift, camera.add(point(actual.get(index))).distanceTo(origin.add(point(baseline.get(index)))));
            close(context, "cylinders never rotate with the camera at angle=" + angle, drift, 0);
        }
        boolean contained = true, insideDisk = true;
        for (LightBeamParticleShape.Segment segment : segments) {
            insideDisk &= Math.hypot(segment.center().x() - origin.x() - options.x(),
                    segment.center().z() - origin.z() - options.z()) <= options.radius() + 1.0E-7D;
            contained &= vertices(shape, segment, origin).stream().allMatch(v -> v.finite() && bounds.inflate(1.0E-5D).contains(origin.add(point(v))));
        }
        expect(context, "random emission points stay inside the filled disk", insideDisk, true);
        expect(context, "all core and halo vertices stay within shared bounds", contained, true);
        expect(context, "same channel emits at different horizontal positions", segments.stream().anyMatch(a -> segments.stream().anyMatch(b ->
                a.channel() == b.channel() && a.cell() != b.cell() && Math.hypot(a.center().x() - b.center().x(), a.center().z() - b.center().z()) > 0.01D)), true);
        expect(context, "same seed and time reproduce geometry", segments(shape, 20, 0), segments);
        expect(context, "new particles get a different random layout", segments(new LightBeamParticleShape(options, origin.x(), origin.y(), origin.z(), 118L), 20, 0).equals(segments), false);
        expect(context, "the first frame is transparent", segments(shape, 0, 0).isEmpty(), true);
        expect(context, "the final frame is transparent", segments(shape, 200, 0).isEmpty(), true);
        for (float radius : new float[]{0.001F, 0.5F, 1.0F, 2.0F, 64.0F}) {
            LightBeamParticleShape sized = new LightBeamParticleShape(options(0, 0, -4096, 4096, 0.001F, FlowDirection.UP, 100, radius), 0, 0, 0);
            expect(context, "channel count scales with disk area and is capped radius=" + radius, sized.channelCount(),
                    (int) Math.clamp(Math.ceil(Math.PI * radius * radius * 16), 1, 1024));
            List<LightBeamParticleShape.Segment> extreme = segments(sized, 20, 0);
            for (float age : new float[]{3.0F, 20.5F, 199.0F})
                expect(context, "segment budget holds during flow radius=" + radius + " age=" + age,
                        segments(sized, age, 63.75F).size() <= LightBeamParticleShape.MAX_SEGMENTS, true);
            expect(context, "extreme dimensions respect total segment budget radius=" + radius, extreme.size() <= 2048, true);
            boolean valid = extreme.stream().allMatch(segment -> vertices(sized, segment, Vec3.ZERO).stream()
                    .allMatch(v -> v.finite() && sized.bounds().inflate(1.0E-5D).contains(point(v))));
            expect(context, "extreme geometry is finite and contained radius=" + radius, valid, true);
        }
        LightBeamParticleOptions dense = new LightBeamParticleOptions(options.color(), 0, 0, 0, 8, options.width(), FlowDirection.UP,
                1, 200, false, 1, 32);
        expect(context, "density doubles the requested channels independently of width", new LightBeamParticleShape(dense, 0, 0, 0).channelCount(), 101);
        LightBeamParticleShape shortWide = new LightBeamParticleShape(options(0, 0, 0, 0.001F, 64, FlowDirection.UP, 1), 0, 0, 0);
        boolean shortValid = segments(shortWide, 20, 0).stream().allMatch(segment -> vertices(shortWide, segment, Vec3.ZERO).stream()
                .allMatch(v -> v.finite() && v.y >= -1.0E-6D && v.y <= 0.001001D));
        expect(context, "very short wide columns clip geometry without invalid vertices", shortValid, true);
    }

    private static void flow(ProbeContext context) {
        LightBeamParticleShape up = new LightBeamParticleShape(options(0, 0, 0, 128, 0.5F, FlowDirection.UP, 2), 0, 0, 0);
        LightBeamParticleShape down = new LightBeamParticleShape(options(0, 0, 0, 128, 0.5F, FlowDirection.DOWN, 2), 0, 0, 0);
        LightBeamParticleShape still = new LightBeamParticleShape(options(0, 0, 0, 128, 0.5F, FlowDirection.UP, 0), 0, 0, 0);
        expect(context, "zero speed freezes emission positions", segments(still, 10, 0), segments(still, 10.5F, 0));
        for (LightBeamParticleShape shape : List.of(up, down)) {
            List<LightBeamParticleShape.Segment> before = segments(shape, 10, 0);
            List<LightBeamParticleShape.Segment> after = segments(shape, 10.5F, 0);
            int count = 0;
            double drift = 0, speedError = 0;
            for (LightBeamParticleShape.Segment first : before) {
                Optional<LightBeamParticleShape.Segment> match = after.stream().filter(second -> second.channel() == first.channel() && second.cell() == first.cell()).findFirst();
                if (match.isEmpty()) continue;
                LightBeamParticleShape.Segment second = match.get();
                drift = Math.max(drift, Math.hypot(second.center().x() - first.center().x(), second.center().z() - first.center().z()));
                speedError = Math.max(speedError, Math.abs(second.center().y() - first.center().y() - (shape == up ? 0.96D : -0.96D)));
                count++;
            }
            expect(context, "partial-tick comparison covers actual traveling streaks", count > 0, true);
            close(context, "moving streaks keep their random transverse positions", drift, 0);
            close(context, "partial ticks retain the expected direction and speed", speedError, 0);
            LightBeamParticleShape.Segment sample = before.stream().filter(segment -> segment.bottom() > 1 && segment.bottom() + segment.length() < 127).findFirst().orElseThrow();
            LightBeamParticleShape.Segment second = after.stream().filter(segment -> segment.channel() == sample.channel() && segment.cell() == sample.cell()).findFirst().orElseThrow();
            List<Vertex> firstVertices = vertices(shape, sample, Vec3.ZERO), nextVertices = vertices(shape, second, Vec3.ZERO);
            double geometryError = 0;
            for (int index = 0; index < firstVertices.size(); index++)
                geometryError = Math.max(geometryError, Math.abs(nextVertices.get(index).y - firstVertices.get(index).y - (shape == up ? 0.96D : -0.96D)));
            close(context, "actual cylinder vertices move smoothly with the streak", geometryError, 0);
        }
        expect(context, "phase selects an independent slice of the stream", segments(up, 10, 7).equals(segments(up, 10, 0)), false);
    }

    private static void culling(ProbeContext context) {
        FrustumIntersection frustum = new FrustumIntersection(new Matrix4f());
        LightBeamParticleOptions options = options(0, 0, 0, 256, 0.5F, FlowDirection.UP, 1);
        AABB crossing = options.bounds(0, -128, 0);
        expect(context, "the spawn point can be outside the view", frustum.testPoint(0, -128, 0), false);
        expect(context, "a visible middle section keeps the column", visible(frustum, crossing), true);
        expect(context, "an entirely hidden column is culled", visible(frustum, options.bounds(10, -128, 0)), false);
        expect(context, "a halo entering the view keeps the column", visible(frustum, options.bounds(1.7, -128, 0)), true);
        expect(context, "horizontal offsets participate in culling", visible(frustum,
                options(-10, 0, 0, 256, 0.5F, FlowDirection.UP, 1).bounds(10, -128, 0)), true);
        expect(context, "client creation bypasses the origin distance filter", options.getType().getOverrideLimiter(), true);
    }

    private static void delivery(ProbeContext context) {
        Vec3 player = Vec3.atCenterOf(context.player().blockPosition());
        LightBeamParticleOptions tall = options(0, 0, 0, 1024, 0.5F, FlowDirection.UP, 1);
        expect(context, "normal delivery accepts an origin within 32 blocks", send(context, tall, player.add(31, 0, 0), false), true);
        expect(context, "normal delivery rejects an origin beyond 32 blocks", send(context, tall, player.add(33, 0, 0), false), false);
        expect(context, "force delivery accepts an origin within 512 blocks", send(context, tall, player.add(511, 0, 0), true), true);
        expect(context, "force delivery rejects an origin beyond 512 blocks", send(context, tall, player.add(513, 0, 0), true), false);
        expect(context, "normal delivery still filters a distant origin beside a nearby column", send(context, tall, player.add(8, -1000, 0), false), false);
        expect(context, "force delivery still filters a distant origin beside a nearby column", send(context, tall, player.add(8, -1000, 0), true), false);
        expect(context, "horizontal offset does not extend vanilla packet delivery", send(context,
                options(-1000, 0, 0, 8, 0.5F, FlowDirection.UP, 1), player.add(1000, 0, 0), false), false);
        expect(context, "volume radius does not extend vanilla packet delivery", send(context,
                options(0, 0, 0, 8, 0.18F, FlowDirection.UP, 1, 8), player.add(39, 0, 0), false), false);
        expect(context, "ordinary particles retain their original distance limit", context.level().sendParticles(context.player(),
                ParticleTypes.FLAME, false, false, player.x() + 1000, player.y(), player.z(), 1, 0, 0, 0, 0), false);
    }

    private static boolean send(ProbeContext context, LightBeamParticleOptions options, Vec3 position, boolean force) {
        return context.level().sendParticles(context.player(), options, force, false,
                position.x(), position.y(), position.z(), 1, 0, 0, 0, 0);
    }

    private static boolean visible(FrustumIntersection frustum, AABB box) {
        return frustum.testAab((float) box.minX, (float) box.minY, (float) box.minZ,
                (float) box.maxX, (float) box.maxY, (float) box.maxZ);
    }

    private record Vertex(float x, float y, float z, float u, float v, int color) {
        boolean finite() {
            return Float.isFinite(this.x) && Float.isFinite(this.y) && Float.isFinite(this.z) && Float.isFinite(this.u) && Float.isFinite(this.v);
        }
    }

    private static Vec3 point(Vertex vertex) {
        return new Vec3(vertex.x, vertex.y, vertex.z);
    }

    private static List<LightBeamParticleShape.Segment> segments(LightBeamParticleShape shape, float age, float phase) {
        List<LightBeamParticleShape.Segment> segments = new ArrayList<>();
        shape.segments(age, phase, segments::add);
        return segments;
    }

    private static List<Vertex> vertices(LightBeamParticleShape shape, LightBeamParticleShape.Segment segment, Vec3 camera) {
        List<Vertex> vertices = new ArrayList<>();
        shape.writeSegment(segment, camera, (x, y, z, u, v, color) -> vertices.add(new Vertex(x, y, z, u, v, color)));
        return vertices;
    }

    private static void close(ProbeContext context, String message, double actual, double expected) {
        expect(context, message + " actual=" + actual + " expected=" + expected,
                Double.isFinite(actual) && Math.abs(actual - expected) <= 1.0E-5D, true);
    }

    private static void expect(ProbeContext context, String message, Object actual, Object expected) {
        context.expect(message, actual, expected);
        context.note(message + ": " + (Objects.equals(actual, expected) ? "OK" : "MISMATCH")
                + " actual=" + actual + " expected=" + expected);
    }
}
