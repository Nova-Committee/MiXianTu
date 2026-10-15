package com.iafenvoy.mxt.probe.talisman;

import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer.Judgement;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer.Point;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer.Score;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer.Stroke;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The drawing scorer: a reference shape, a few ways of tracing it, and the bands the design doc expects
 * (research/66 section 13.2). It needs no world at all, which is why it was the only talisman leg a dedicated
 * server could run before the port.
 *
 * <p>Everything is measured first and logged, then checked, so one run reports every number even when an early
 * check fails. Bands rather than values - the algorithm is heuristic, so tuning may move a number inside its band
 * without failing, while an ordering or magnitude change must fail.
 */
public final class TalismanProbes {
    private TalismanProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("talisman/scoring", TalismanProbes::scoring));
    }

    private static void scoring(ProbeContext context) {
        List<Stroke> reference = sigil();
        List<Stroke> traced = canvas(reference);
        Score perfect = TalismanDrawingScorer.score(reference, traced, 0.06D, Judgement.DEFAULT);
        Score jitter = TalismanDrawingScorer.score(reference, jitter(traced, 2.0D), 0.06D, Judgement.DEFAULT);
        Score turned = TalismanDrawingScorer.score(reference, rotate(traced, 15.0D), 0.06D, Judgement.DEFAULT);
        Score quarter = TalismanDrawingScorer.score(reference, rotate(traced, 90.0D), 0.06D, Judgement.DEFAULT);
        Score moved = TalismanDrawingScorer.score(reference, translate(traced, 60.0D, -40.0D), 0.06D, Judgement.DEFAULT);
        Score doubled = TalismanDrawingScorer.score(reference, scale(traced, 2.0D), 0.06D, Judgement.DEFAULT);
        Score halved = TalismanDrawingScorer.score(reference, scale(traced, 0.5D), 0.06D, Judgement.DEFAULT);
        Score missing = TalismanDrawingScorer.score(reference,
                new ArrayList<>(traced.subList(0, traced.size() - 1)), 0.06D, Judgement.DEFAULT);
        Score extra = TalismanDrawingScorer.score(reference, extra(traced), 0.06D, Judgement.DEFAULT);
        Score scribble = TalismanDrawingScorer.score(reference, scribble(), 0.06D, Judgement.DEFAULT);
        Score swapped = TalismanDrawingScorer.score(reference, swapFirstTwo(traced), 0.06D, Judgement.DEFAULT);
        // A human-like trace, and the two ways a human differs structurally from the JSON: another stroke order,
        // and lifting the brush in the middle of a multi-segment stroke.
        Score handSame = TalismanDrawingScorer.score(reference, hand(traced, 2.0D, 3.0D, 8.0D), 0.06D, Judgement.DEFAULT);
        Score handOrder = TalismanDrawingScorer.score(reference, swapFirstTwo(hand(traced, 2.0D, 3.0D, 8.0D)),
                0.06D, Judgement.DEFAULT);
        Score handSplit = TalismanDrawingScorer.score(reference, splitMiddle(hand(traced, 2.0D, 3.0D, 8.0D), 1),
                0.06D, Judgement.DEFAULT);
        Score handRough = TalismanDrawingScorer.score(reference, hand(traced, 5.0D, 6.0D, 16.0D), 0.06D, Judgement.DEFAULT);
        Score again = TalismanDrawingScorer.score(reference, traced, 0.06D, Judgement.DEFAULT);
        Score stated = TalismanDrawingScorer.score(reference, traced, 0.06D,
                new Judgement(1.0D, 0.25D, 0.25D, 0.25D, false, 0.02D, true));
        Map<String, Score> measured = new LinkedHashMap<>();
        measured.put("perfect", perfect);
        measured.put("jitter", jitter);
        measured.put("turned15", turned);
        measured.put("turned90", quarter);
        measured.put("moved", moved);
        measured.put("doubled", doubled);
        measured.put("halved", halved);
        measured.put("missing", missing);
        measured.put("extra", extra);
        measured.put("scribble", scribble);
        measured.put("swapped", swapped);
        measured.put("handSame", handSame);
        measured.put("handOrder", handOrder);
        measured.put("handSplit", handSplit);
        measured.put("handRough", handRough);
        for (Map.Entry<String, Score> entry : measured.entrySet())
            context.note("[MXT] talisman scoring " + entry.getKey() + " = " + entry.getValue().completion()
                    + " (shape " + entry.getValue().shape() + " direction " + entry.getValue().direction()
                    + " topology " + entry.getValue().topology() + " order " + entry.getValue().order() + ")");

        List<String> failures = new ArrayList<>();
        if (perfect.completion() < 0.90D || perfect.degenerate())
            failures.add("a perfect retrace scored " + perfect.completion() + " instead of at least 0.90");
        if (jitter.completion() < 0.85D || jitter.completion() > 0.98D)
            failures.add("a two-pixel jitter scored " + jitter.completion() + " instead of 0.85..0.98");
        if (jitter.completion() >= perfect.completion())
            failures.add("a two-pixel jitter scored " + jitter.completion() + ", not below the perfect retrace "
                    + perfect.completion());
        if (turned.completion() >= jitter.completion() - 0.05D)
            failures.add("a fifteen degree rotation scored " + turned.completion()
                    + ", not clearly below the jitter " + jitter.completion());
        if (quarter.completion() > 0.30D)
            failures.add("a ninety degree rotation scored " + quarter.completion() + " instead of at most 0.30");
        if (perfect.completion() - moved.completion() > 0.05D)
            failures.add("the same shape drawn elsewhere scored " + moved.completion() + " against "
                    + perfect.completion());
        if (perfect.completion() - doubled.completion() > 0.05D)
            failures.add("the same shape drawn twice as large scored " + doubled.completion() + " against "
                    + perfect.completion());
        if (halved.completion() < 0.80D || halved.completion() > 0.95D)
            failures.add("the same shape drawn at half size scored " + halved.completion() + " instead of 0.80..0.95");
        if (missing.completion() >= jitter.completion())
            failures.add("a missing stroke scored " + missing.completion() + ", not below the jitter "
                    + jitter.completion());
        if (extra.completion() >= jitter.completion())
            failures.add("an extra stroke scored " + extra.completion() + ", not below the jitter "
                    + jitter.completion());
        if (scribble.completion() > 0.45D || perfect.completion() <= scribble.completion())
            failures.add("a scribble over the whole canvas scored " + scribble.completion()
                    + " against the perfect retrace " + perfect.completion());
        if (Double.compare(again.completion(), perfect.completion()) != 0)
            failures.add("the same input scored " + again.completion() + " then " + perfect.completion());
        if (Double.compare(stated.completion(), perfect.completion()) != 0)
            failures.add("the default judgement scored " + perfect.completion()
                    + " and the same values written out " + stated.completion());
        // Which stroke came first is not on the screen, so it must not cost anything either.
        if (Math.abs(swapped.completion() - perfect.completion()) > 0.01D)
            failures.add("the same three strokes in another order scored " + swapped.completion()
                    + " against the reference order " + perfect.completion());
        if (handSame.completion() < 0.85D)
            failures.add("a hand-like trace (2px wobble, 3px bow, 8px overshoot) scored "
                    + handSame.completion() + " instead of at least 0.85");
        if (Math.abs(handOrder.completion() - handSame.completion()) > 0.02D)
            failures.add("the same hand-like trace in another stroke order scored " + handOrder.completion()
                    + " against " + handSame.completion());
        if (handRough.completion() < 0.70D || handRough.completion() >= handSame.completion())
            failures.add("a rough hand-like trace (5px wobble, 6px bow, 16px overshoot) scored "
                    + handRough.completion() + " against the careful one " + handSame.completion());

        List<List<Stroke>> degenerate = List.of(List.of(),
                List.of(new Stroke(List.of(new Point(5.0D, 5.0D)))),
                List.of(new Stroke(List.of(new Point(5.0D, 5.0D), new Point(5.0D, 5.0D)))),
                List.of(new Stroke(List.of(new Point(Double.NaN, 0.0D), new Point(1.0D, Double.POSITIVE_INFINITY)))));
        for (List<Stroke> input : degenerate) {
            Score score = TalismanDrawingScorer.score(reference, input, 0.06D, Judgement.DEFAULT);
            if (score.completion() != 0.0D || !score.degenerate())
                failures.add("a degenerate drawing scored " + score.completion() + " with degenerate="
                        + score.degenerate());
        }
        for (String failure : failures) context.fail(failure);
        if (failures.isEmpty()) context.note("talisman scoring: OK");
    }

    // The three strokes of the design doc's example formula, normalized the way a recipe writes them.
    private static List<Stroke> sigil() {
        return List.of(
                new Stroke(List.of(new Point(0.50D, 0.06D), new Point(0.50D, 0.94D))),
                new Stroke(List.of(new Point(0.22D, 0.28D), new Point(0.50D, 0.10D), new Point(0.78D, 0.28D))),
                new Stroke(List.of(new Point(0.30D, 0.72D), new Point(0.70D, 0.72D))));
    }

    // The same shape in bitmap pixels, which is what a player's strokes are measured in.
    private static List<Stroke> canvas(List<Stroke> normalized) {
        List<Stroke> strokes = new ArrayList<>();
        for (Stroke stroke : normalized) {
            List<Point> points = new ArrayList<>();
            for (Point point : stroke.points())
                points.add(new Point(point.x() * TalismanDrawingScorer.CANVAS_WIDTH,
                        point.y() * TalismanDrawingScorer.CANVAS_HEIGHT));
            strokes.add(new Stroke(List.copyOf(points)));
        }
        return strokes;
    }

    // Alternating offsets rather than a random one: the same leg has to fail the same way twice.
    private static List<Stroke> jitter(List<Stroke> strokes, double amount) {
        List<Stroke> result = new ArrayList<>();
        int index = 0;
        for (Stroke stroke : strokes) {
            List<Point> points = new ArrayList<>();
            for (Point point : stroke.points()) {
                double offset = (index++ % 2 == 0) ? amount : -amount;
                points.add(new Point(point.x() + offset, point.y() - offset));
            }
            result.add(new Stroke(List.copyOf(points)));
        }
        return result;
    }

    private static List<Stroke> rotate(List<Stroke> strokes, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians), sin = Math.sin(radians);
        double centerX = TalismanDrawingScorer.CANVAS_WIDTH / 2.0D, centerY = TalismanDrawingScorer.CANVAS_HEIGHT / 2.0D;
        List<Stroke> result = new ArrayList<>();
        for (Stroke stroke : strokes) {
            List<Point> points = new ArrayList<>();
            for (Point point : stroke.points()) {
                double x = point.x() - centerX, y = point.y() - centerY;
                points.add(new Point(centerX + x * cos - y * sin, centerY + x * sin + y * cos));
            }
            result.add(new Stroke(List.copyOf(points)));
        }
        return result;
    }

    private static List<Stroke> translate(List<Stroke> strokes, double dx, double dy) {
        List<Stroke> result = new ArrayList<>();
        for (Stroke stroke : strokes) {
            List<Point> points = new ArrayList<>();
            for (Point point : stroke.points()) points.add(new Point(point.x() + dx, point.y() + dy));
            result.add(new Stroke(List.copyOf(points)));
        }
        return result;
    }

    private static List<Stroke> scale(List<Stroke> strokes, double factor) {
        double centerX = TalismanDrawingScorer.CANVAS_WIDTH / 2.0D, centerY = TalismanDrawingScorer.CANVAS_HEIGHT / 2.0D;
        List<Stroke> result = new ArrayList<>();
        for (Stroke stroke : strokes) {
            List<Point> points = new ArrayList<>();
            for (Point point : stroke.points())
                points.add(new Point(centerX + (point.x() - centerX) * factor,
                        centerY + (point.y() - centerY) * factor));
            result.add(new Stroke(List.copyOf(points)));
        }
        return result;
    }

    private static List<Stroke> extra(List<Stroke> strokes) {
        List<Stroke> result = new ArrayList<>(strokes);
        result.add(new Stroke(List.of(new Point(4.0D, 200.0D), new Point(86.0D, 200.0D))));
        return result;
    }

    // A dense zigzag across the canvas: high coverage, low precision, which the F1 of the shape term must punish.
    private static List<Stroke> scribble() {
        List<Point> points = new ArrayList<>();
        for (int step = 0; step <= 20; step++)
            points.add(new Point(step * 4.5D, step % 2 == 0 ? 10.0D : 200.0D));
        return List.of(new Stroke(List.copyOf(points)));
    }

    private static List<Stroke> swapFirstTwo(List<Stroke> strokes) {
        List<Stroke> result = new ArrayList<>(strokes);
        if (result.size() >= 2) {
            Stroke first = result.get(0);
            result.set(0, result.get(1));
            result.set(1, first);
        }
        return result;
    }

    /**
     * How a careful player actually traces: the path resampled every pixel, given a smooth wobble plus a bow, and
     * run {@code overshoot} pixels past both ends. Synthetic hands are deterministic, so the leg fails the same way
     * twice; the point is to see which term a human-like trace loses its points to.
     */
    private static List<Stroke> hand(List<Stroke> traced, double wobble, double bow, double overshoot) {
        List<Stroke> result = new ArrayList<>();
        for (Stroke stroke : traced) {
            List<Point> dense = densify(stroke.points(), 1.0D);
            if (dense.size() < 2) continue;
            double length = pathLength(dense);
            List<Point> points = new ArrayList<>();
            double travelled = 0.0D;
            for (int index = 0; index < dense.size(); index++) {
                Point previous = dense.get(Math.max(0, index - 1));
                Point next = dense.get(Math.min(dense.size() - 1, index + 1));
                double dx = next.x() - previous.x();
                double dy = next.y() - previous.y();
                double norm = Math.hypot(dx, dy);
                double t = length <= 0.0D ? 0.0D : travelled / length;
                double offset = norm <= 0.0D ? 0.0D
                        : wobble * Math.sin(t * Math.PI * 3.0D) + bow * Math.sin(t * Math.PI);
                double nx = norm <= 0.0D ? 0.0D : -dy / norm;
                double ny = norm <= 0.0D ? 0.0D : dx / norm;
                points.add(new Point(dense.get(index).x() + nx * offset, dense.get(index).y() + ny * offset));
                if (index + 1 < dense.size()) travelled += Math.hypot(
                        dense.get(index + 1).x() - dense.get(index).x(),
                        dense.get(index + 1).y() - dense.get(index).y());
            }
            extend(points, dense, overshoot);
            result.add(new Stroke(List.copyOf(points)));
        }
        return result;
    }

    /**
     * Runs a hand path past both of its ends, because nobody lifts the brush exactly on the endpoint.
     */
    private static void extend(List<Point> points, List<Point> dense, double overshoot) {
        if (overshoot <= 0.0D) return;
        Point first = dense.get(0);
        Point afterFirst = dense.get(1);
        Point last = dense.getLast();
        Point beforeLast = dense.get(dense.size() - 2);
        points.addFirst(along(first, afterFirst, -overshoot));
        points.add(along(last, beforeLast, -overshoot));
    }

    private static Point along(Point from, Point towards, double distance) {
        double dx = from.x() - towards.x();
        double dy = from.y() - towards.y();
        double norm = Math.hypot(dx, dy);
        if (norm <= 0.0D) return from;
        return new Point(from.x() + dx / norm * distance, from.y() + dy / norm * distance);
    }

    /**
     * Even sampling of a polyline at a fixed spacing, endpoints included.
     */
    private static List<Point> densify(List<Point> points, double step) {
        List<Point> out = new ArrayList<>();
        if (points.size() < 2) return new ArrayList<>(points);
        out.add(points.getFirst());
        for (int index = 1; index < points.size(); index++) {
            Point a = points.get(index - 1);
            Point b = points.get(index);
            double span = Math.hypot(b.x() - a.x(), b.y() - a.y());
            int pieces = Math.max(1, (int) Math.ceil(span / step));
            for (int piece = 1; piece <= pieces; piece++) {
                double t = (double) piece / pieces;
                out.add(new Point(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t));
            }
        }
        return out;
    }

    private static double pathLength(List<Point> points) {
        double total = 0.0D;
        for (int index = 1; index < points.size(); index++)
            total += Math.hypot(points.get(index).x() - points.get(index - 1).x(),
                    points.get(index).y() - points.get(index - 1).y());
        return total;
    }

    /**
     * One stroke of {@code index} cut in two at its middle, as a player who lifts the brush halfway would.
     */
    private static List<Stroke> splitMiddle(List<Stroke> strokes, int index) {
        List<Stroke> result = new ArrayList<>();
        for (int position = 0; position < strokes.size(); position++) {
            Stroke stroke = strokes.get(position);
            if (position != index || stroke.points().size() < 3) {
                result.add(stroke);
                continue;
            }
            int middle = stroke.points().size() / 2;
            result.add(new Stroke(List.copyOf(stroke.points().subList(0, middle + 1))));
            result.add(new Stroke(List.copyOf(stroke.points().subList(middle, stroke.points().size()))));
        }
        return result;
    }
}
