package com.iafenvoy.mxt.data.formation;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ScalableParticleOptionsBase;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.phys.Vec2;

import java.util.List;
import java.util.function.Function;

/**
 * One stroke of a formation's figure. The renderer can walk exactly two things — a circle, by angle from due east, and a
 * polyline, by arc length from its first point — so those are the only two strokes there are. Coordinates are in units
 * of the array's radius, which is what lets one figure scale with every array that reads it.
 *
 * <p>Whatever its geometry, a stroke carries a {@link Common}: the paint it is drawn in, how big and how far apart its
 * dusts sit, and the window of the figure's progress it is drawn over. Those fields are read off the stroke itself, next
 * to the geometry's own, so one file writes them the same way for both kinds. Every {@code [x, z]} pair is in the plane
 * of the figure, the second number being the z axis, so a point is written the way {@link MiscCodecs#HORIZONTAL_PAIR}
 * reads it.
 */
public sealed interface FormationStroke permits FormationStroke.Circle, FormationStroke.Polyline {
    Codec<FormationStroke> CODEC = MxtRegistries.FORMATION_STROKE_TYPE.byNameCodec().dispatch("type", FormationStroke::codec, Function.identity());
    /**
     * A figure is a drawing, so the points in one stroke are bounded the way a drawing's are: the client samples by
     * {@code spacing} anyway, and past this a definition costs more to read than it can ever draw.
     */
    int MAX_POINTS = 4096;
    /**
     * The largest radius one stroke may have, in units of the array's radius. A stroke is allowed past the ring — a
     * figure need not match the array's own edge exactly — but not arbitrarily far.
     */
    double MAX_RADIUS = 64.0D;

    MapCodec<? extends FormationStroke> codec();

    Common common();

    /**
     * The paint this stroke is drawn in: one colour flat along it, or a {@link Gradient} ramp. A stroke has no colour to
     * fall back to, so one of the two is always there.
     */
    default Either<Integer, Gradient> color() {
        return this.common().color();
    }

    /**
     * How big one dust of this stroke is drawn, and how far apart the dusts are.
     */
    default float size() {
        return this.common().size();
    }

    default double spacing() {
        return this.common().spacing();
    }

    /**
     * Where in the figure's progress this stroke begins and ends, so strokes can be drawn one after another instead of
     * all at once.
     */
    default double start() {
        return this.common().start();
    }

    default double end() {
        return this.common().end();
    }

    /**
     * The length of this stroke in units of the array's radius, which is what a ramp's {@code at} is measured along.
     */
    double length();

    /**
     * The colour this stroke draws with {@code along} its own length: 0 at its first point, 1 at its last. A ramp holds
     * its end colours outside its outermost stops, so it need not reach either end of the stroke.
     */
    default int colorAt(double along) {
        return this.color().map(Function.identity(), gradient -> gradient.colorAt(along));
    }

    /**
     * What every stroke carries whatever its geometry is: the paint it is drawn in, how finely it is laid out, and the
     * window of the figure's progress it is drawn over. Its fields are written next to the geometry's own, so a circle
     * and a polyline take them the same way.
     */
    record Common(Either<Integer, Gradient> color, float size, double spacing, double start, double end) {
        /**
         * What a stroke that says nothing about its layout gets: the density and the dust size a plain outline wants.
         */
        public static final float DEFAULT_SIZE = 2.0F;
        public static final double DEFAULT_SPACING = 0.33D;
        public static final MapCodec<Common> CODEC = RecordCodecBuilder.<Common>mapCodec(i -> i.group(
                Codec.either(ExtraCodecs.STRING_RGB_COLOR, Gradient.CODEC).fieldOf("color").forGetter(Common::color),
                Codec.floatRange(ScalableParticleOptionsBase.MIN_SCALE, ScalableParticleOptionsBase.MAX_SCALE).optionalFieldOf("size", DEFAULT_SIZE).forGetter(Common::size),
                Codec.doubleRange(0.01D, 64.0D).optionalFieldOf("spacing", DEFAULT_SPACING).forGetter(Common::spacing),
                Codec.doubleRange(0.0D, 1.0D).optionalFieldOf("start", 0.0D).forGetter(Common::start),
                Codec.doubleRange(0.0D, 1.0D).optionalFieldOf("end", 1.0D).forGetter(Common::end)
        ).apply(i, Common::new)).validate(Common::validate);

        public Common {
            // The color is authored as a hex string, which carries an alpha byte; dust is RGB24, so the alpha is
            // dropped here instead of at the draw call.
            color = color.mapLeft(ARGB::transparent);
        }

        // Reported as a decode error rather than thrown from the constructor, so the file can be named: a window that
        // never opens is a stroke that can never be drawn.
        private static DataResult<Common> validate(Common common) {
            if (common.end() <= common.start())
                return DataResult.error(() -> "A stroke has to be drawn after it starts: `end` above `start`");
            return DataResult.success(common);
        }
    }

    /**
     * A whole circle at {@code center}, drawn from {@code startAngle} degrees past due east, which is the same place the
     * ring of a plain array starts; the angle increases from there.
     */
    record Circle(Common common, Vec2 center, double radius, double startAngle) implements FormationStroke {
        public static final MapCodec<Circle> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Common.CODEC.forGetter(Circle::common),
                MiscCodecs.HORIZONTAL_PAIR.optionalFieldOf("center", Vec2.ZERO).forGetter(Circle::center),
                // A circle with no radius is a figure that cannot be drawn at all.
                Codec.doubleRange(1.0E-6D, MAX_RADIUS).fieldOf("radius").forGetter(Circle::radius),
                // One turn either way already covers every distinct place a circle can begin at.
                Codec.doubleRange(-360.0D, 360.0D).optionalFieldOf("start_angle", 0.0D).forGetter(Circle::startAngle)
        ).apply(i, Circle::new));

        @Override
        public MapCodec<Circle> codec() {
            return CODEC;
        }

        @Override
        public double length() {
            return Math.PI * 2.0D * this.radius;
        }
    }

    /**
     * A stroke through its points, in order: the renderer walks it from the first to the last, so where a stroke starts
     * is also how it grows.
     */
    record Polyline(Common common, List<Vec2> points) implements FormationStroke {
        public static final MapCodec<Polyline> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Common.CODEC.forGetter(Polyline::common),
                MiscCodecs.HORIZONTAL_PAIR.listOf(2, MAX_POINTS).fieldOf("points").forGetter(Polyline::points)
        ).apply(i, Polyline::new));

        public Polyline {
            points = List.copyOf(points);
        }

        @Override
        public MapCodec<Polyline> codec() {
            return CODEC;
        }

        @Override
        public double length() {
            double total = 0.0D;
            for (int index = 1; index < this.points.size(); index++) {
                Vec2 from = this.points.get(index - 1), to = this.points.get(index);
                total += Math.hypot(to.x - from.x, to.y - from.y);
            }
            return total;
        }
    }

    /**
     * A ramp along one stroke: {@code at} 0 is its first point and 1 its last, and the stops are read in the order they
     * are written, so a ramp that goes backwards is refused rather than quietly reordered.
     */
    record Gradient(List<Stop> stops) {
        /**
         * A ramp is painted in flat steps by the client, so there is no point in hundreds of stops.
         */
        public static final int MAX_STOPS = 16;
        public static final Codec<Gradient> CODEC = Stop.CODEC.listOf(2, MAX_STOPS)
                .xmap(Gradient::new, Gradient::stops)
                .validate(Gradient::inOrder);

        public int colorAt(double along) {
            List<Stop> stops = this.stops;
            if (along <= stops.getFirst().at()) return stops.getFirst().color();
            for (int index = 1; index < stops.size(); index++) {
                Stop lower = stops.get(index - 1), upper = stops.get(index);
                if (along <= upper.at()) {
                    double span = upper.at() - lower.at();
                    return blend(lower.color(), upper.color(), span <= 0.0D ? 0.0D : (along - lower.at()) / span);
                }
            }
            return stops.getLast().color();
        }

        private static DataResult<Gradient> inOrder(Gradient gradient) {
            List<Stop> stops = gradient.stops();
            for (int index = 1; index < stops.size(); index++)
                if (stops.get(index).at() < stops.get(index - 1).at())
                    return DataResult.error(() -> "A gradient's stops have to be written in order of `at`");
            return DataResult.success(gradient);
        }

        private static int blend(int from, int to, double along) {
            return channel(from, to, 16, along) | channel(from, to, 8, along) | channel(from, to, 0, along);
        }

        private static int channel(int from, int to, int shift, double along) {
            int start = (from >> shift) & 0xFF, end = (to >> shift) & 0xFF;
            return (int) Math.round(start + (end - start) * along) << shift;
        }
    }

    /**
     * One stop of a ramp: where along the stroke it sits, and the colour there.
     */
    record Stop(double at, int color) {
        public static final Codec<Stop> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0.0D, 1.0D).fieldOf("at").forGetter(Stop::at),
                ExtraCodecs.STRING_RGB_COLOR.fieldOf("color").forGetter(Stop::color)
        ).apply(i, Stop::new));

        public Stop {
            color = ARGB.transparent(color);
        }
    }
}
