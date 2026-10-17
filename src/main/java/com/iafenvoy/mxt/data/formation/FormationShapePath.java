package com.iafenvoy.mxt.data.formation;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a figure into the points a renderer has to place: every stroke is walked from its own start to a fraction of
 * its own length, so the strokes of one figure grow together unless they were given windows of their own. A circle is
 * walked by angle and a polyline by arc length, which are the only two walks there are, and each point carries the
 * colour its stroke has at that place along itself and the dust size that stroke asked for.
 *
 * <p>The point count follows from the drawn length and the stroke's own spacing rather than from a fixed budget, which
 * is what keeps a large figure from losing its outline. A band is a piece of that walk rather than the whole figure
 * again, so a client that lays one out per tick never doubles up on what the frame before it already placed.
 */
public final class FormationShapePath {
    /**
     * A client-side ceiling, not a rule the author has to respect: spacing already decides the density, and this only
     * stops a tiny spacing on a huge radius from flooding one frame with particles.
     */
    public static final int MAX_POINTS_PER_STROKE = 2048;

    private FormationShapePath() {
    }

    public static List<Dot> outline(FormationShape shape, Vec3 center, double radius, float progress) {
        return band(shape, center, radius, 0.0F, progress);
    }

    /**
     * The part of the figure between two progress values, each stroke cut at its own share of them, laid out at its own
     * spacing and drawn only inside the window it asked for.
     */
    public static List<Dot> band(FormationShape shape, Vec3 center, double radius, float from, float to) {
        double start = Mth.clamp(from, 0.0F, 1.0F);
        double end = Mth.clamp(to, 0.0F, 1.0F);
        if (!(end > start)) return List.of();
        List<Dot> dots = new ArrayList<>();
        for (FormationStroke stroke : shape.strokes()) {
            // The figure's progress lands on the stroke's own window, so a stroke waits its turn instead of starting
            // with the others; a ramp still reads along the whole stroke.
            double span = stroke.end() - stroke.start();
            double localFrom = Mth.clamp((start - stroke.start()) / span, 0.0D, 1.0D);
            double localTo = Mth.clamp((end - stroke.start()) / span, 0.0D, 1.0D);
            if (!(localTo > localFrom)) continue;
            switch (stroke) {
                case FormationStroke.Circle circle -> arc(dots, center, radius, circle, localFrom, localTo);
                case FormationStroke.Polyline polyline -> polyline(dots, center, radius, polyline, localFrom, localTo);
            }
        }
        return dots;
    }

    /**
     * One dot of a figure: where it goes, the colour it is drawn in, and how big its dust is.
     */
    public record Dot(Vec3 position, int color, float size) {
    }

    // The circle is walked by angle rather than by chords, because the ring is the smooth outline: a pentagon-shaped
    // "ring" would not be the circle the stroke's radius actually describes.
    private static void arc(List<Dot> out, Vec3 origin, double scale, FormationStroke.Circle stroke, double from,
                            double to) {
        double radius = stroke.radius() * scale;
        if (!(radius > 0.0D)) return;
        double circumference = Math.PI * 2.0D * radius;
        int steps = steps(circumference * (to - from), stroke.spacing());
        if (steps == 0) return;
        Vec3 center = place(origin, stroke.center(), scale);
        double start = Math.toRadians(stroke.startAngle()) + circumference * from / radius;
        double sweep = circumference * (to - from) / radius;
        for (int index = 0; index <= steps; index++) {
            double along = fraction(from, to, index, steps);
            double angle = start + sweep * index / steps;
            out.add(new Dot(new Vec3(center.x + radius * Math.cos(angle), center.y, center.z + radius * Math.sin(angle)),
                    stroke.colorAt(along), stroke.size()));
        }
    }

    private static void polyline(List<Dot> out, Vec3 origin, double scale, FormationStroke.Polyline stroke, double from,
                                 double to) {
        List<Vec3> points = new ArrayList<>(stroke.points().size());
        for (Vec2 point : stroke.points()) points.add(place(origin, point, scale));
        double total = length(points);
        double drawn = total * (to - from);
        int steps = steps(drawn, stroke.spacing());
        if (steps == 0) return;
        double start = total * from;
        double step = drawn / steps;
        for (int index = 0; index <= steps; index++)
            out.add(new Dot(pointAt(points, start + step * index),
                    stroke.colorAt(fraction(from, to, index, steps)), stroke.size()));
    }

    // How far along the whole stroke this point of a band sits: what a ramp is read against.
    private static double fraction(double from, double to, int index, int steps) {
        return from + (to - from) * index / steps;
    }

    // The figure lies in the plane of the controller, and a stroke's coordinates are in units of the array's radius.
    private static Vec3 place(Vec3 origin, Vec2 point, double scale) {
        return new Vec3(origin.x + point.x * scale, origin.y, origin.z + point.y * scale);
    }

    private static Vec3 pointAt(List<Vec3> vertices, double distance) {
        double left = distance;
        for (int index = 1; index < vertices.size(); index++) {
            Vec3 from = vertices.get(index - 1);
            Vec3 to = vertices.get(index);
            double segment = from.distanceTo(to);
            if (left <= segment || index == vertices.size() - 1) {
                double t = segment <= 0.0D ? 0.0D : Mth.clamp(left / segment, 0.0D, 1.0D);
                return from.lerp(to, t);
            }
            left -= segment;
        }
        return vertices.getLast();
    }

    private static double length(List<Vec3> vertices) {
        double total = 0.0D;
        for (int index = 1; index < vertices.size(); index++)
            total += vertices.get(index - 1).distanceTo(vertices.get(index));
        return total;
    }

    // Zero means nothing is drawn yet, which is what progress 0 has to look like; a spacing that does not divide the
    // length rounds up, so the stroke always ends exactly on the point the progress names.
    private static int steps(double length, double spacing) {
        if (!(length > 0.0D)) return 0;
        double count = Math.ceil(length / spacing);
        if (!(count > 1.0D)) return 1;
        // One step is one interval and a stroke draws both of its ends, so the point count is one above this.
        return (int) Math.min(MAX_POINTS_PER_STROKE - 1, count);
    }
}
