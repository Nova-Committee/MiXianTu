package com.iafenvoy.mxt.runtime.talisman;

import java.util.*;

/**
 * Pure scoring of one submitted drawing against a pattern (design doc 66 §4.3). Never throws and never
 * logs: empty / malformed / degenerate input returns completion 0 with {@code degenerate = true}.
 * Position and size are normalised away (centroid + RMS radius); rotation is deliberately not.
 */
public final class TalismanDrawingScorer {
    public static final double CANVAS_WIDTH = 90.0D, CANVAS_HEIGHT = 210.0D;
    public static final double RESAMPLE_STEP = 2.0D;
    public static final double SIMPLIFY_EPSILON = 0.5D;

    /**
     * Fallback for a missing / non-finite recipe tolerance; the design doc default.
     */
    private static final double DEFAULT_TOLERANCE = 0.06D;
    private static final int MIN_SAMPLES = 16, MAX_SAMPLES = 256;
    /**
     * Below this the point cloud has no usable scale (a single click, all points coincident).
     */
    private static final double MIN_RMS = 1.0E-6D;
    private static final double PARAM_EPS = 1.0E-9D;
    private static final double NODE_MERGE_PX = 0.05D;
    /**
     * Coincident-node merging is O(n^2); above this node count numeric duplicates are left alone.
     */
    private static final int NODE_MERGE_LIMIT = 1024;
    private static final double HALF_PI = Math.PI / 2.0D;

    private TalismanDrawingScorer() {
    }

    public record Point(double x, double y) {
    }

    public record Stroke(List<Point> points) {
    }

    /**
     * Judgement parameters. DEFAULT is the one written in the design doc §4.2 (judgement block omitted).
     */
    public record Judgement(double sigma, double directionWeight, double topologyWeight, double orderWeight,
                            boolean strokeCountStrict, double minStrokeLength, boolean preview) {
        public static final Judgement DEFAULT = new Judgement(1.0D, 0.25D, 0.25D, 0.25D, false, 0.02D, true);
    }

    /**
     * completion in [0,1]; the rest are the components of §4.3 step 7, for probes/debug.
     */
    public record Score(double completion, double cover, double precision, double shape, double direction,
                        double topology, double order, double distance, int vertices, boolean closed,
                        boolean degenerate) {
    }

    /**
     * reference: pattern strokes in normalized canvas coordinates ([0,1]^2, from the recipe JSON).
     * player: strokes in bitmap pixels (canvas space, x in [0,90], y in [0,210]).
     * tolerance: pattern.tolerance (fraction of canvas height). Never throws.
     */
    public static Score score(List<Stroke> reference, List<Stroke> player, double tolerance, Judgement judgement) {
        Judgement params = judgement == null ? Judgement.DEFAULT : judgement;
        double tolPx = Double.isFinite(tolerance) && tolerance > 0.0D
                ? tolerance * CANVAS_HEIGHT : DEFAULT_TOLERANCE * CANVAS_HEIGHT;
        double minStrokePx = nonNegative(params.minStrokeLength()) * CANVAS_HEIGHT;

        // Step 0: degenerate input. Points are scaled to pixels first, so both sides share one pipeline.
        List<List<P>> refRaw = toPixelSpace(reference, CANVAS_WIDTH, CANVAS_HEIGHT);
        List<List<P>> playerRaw = toPixelSpace(player, 1.0D, 1.0D);
        if (countPoints(refRaw) < 2 || countPoints(playerRaw) < 2) return degenerate();

        // Step 1.1-1.3: thinning, short player strokes dropped, arc-length resampling.
        List<List<P>> refThin = thin(refRaw);
        List<List<P>> playerThin = dropShorterThan(thin(playerRaw), minStrokePx);
        if (countPoints(refThin) < 2 || countPoints(playerThin) < 2
                || arcLength(playerThin) < minStrokePx) return degenerate();

        List<List<P>> refPx = resample(refThin);
        List<List<P>> playerPx = resample(playerThin);

        // Step 2: centroid + RMS normalisation, no rotation; tolerance follows the reference scale.
        double refRms = rms(refPx);
        double playerRms = rms(playerPx);
        if (refRms <= MIN_RMS || playerRms <= MIN_RMS) return degenerate();
        double tol2 = (tolPx / refRms) * (tolPx / refRms);
        double simplifyPx = SIMPLIFY_EPSILON * tolPx;

        // Step 1.4-1.6: distances use the dense resampled segments, topology uses the simplified key
        // point graph (splitting it at intersections is what makes nodes / faces countable).
        List<List<P>> refKeys = simplifyAll(refPx, simplifyPx);
        List<List<P>> playerKeys = simplifyAll(playerPx, simplifyPx);
        Side refSide = new Side(translateScale(refPx, refRms), buildGraph(refKeys, tolPx));
        Side playerSide = new Side(translateScale(playerPx, playerRms), buildGraph(playerKeys, tolPx));

        // Step 3-4: cover and the direction term share one nearest-segment query per reference point.
        double errorSum = 0.0D;
        double weightSum = 0.0D;
        double directionSum = 0.0D;
        for (List<P> stroke : refSide.strokes) {
            for (int i = 0; i < stroke.size(); i++) {
                P p = stroke.get(i);
                int nearest = playerSide.nearest(p, 0, playerSide.segmentCount, tol2);
                double error = nearest < 0 ? 1.0D : Math.min(1.0D, playerSide.segDist2(nearest, p) / tol2);
                errorSum += error;
                double weight = 1.0D - error;
                if (weight > 0.0D) {
                    double[] tangent = tangentAt(stroke, i);
                    double sx = playerSide.sbx[nearest] - playerSide.sax[nearest];
                    double sy = playerSide.sby[nearest] - playerSide.say[nearest];
                    if ((tangent[0] != 0.0D || tangent[1] != 0.0D) && (sx != 0.0D || sy != 0.0D)) {
                        directionSum += weight * foldedAngle(tangent[0], tangent[1], sx, sy) / HALF_PI;
                        weightSum += weight;
                    }
                }
            }
        }
        double cover = refSide.cloud.isEmpty() ? 0.0D : 1.0D - errorSum / refSide.cloud.size();
        double direction = weightSum > 0.0D ? directionSum / weightSum : 1.0D;

        double playerErrorSum = 0.0D;
        for (P p : playerSide.cloud) {
            int nearest = refSide.nearest(p, 0, refSide.segmentCount, tol2);
            playerErrorSum += nearest < 0 ? 1.0D : Math.min(1.0D, refSide.segDist2(nearest, p) / tol2);
        }
        double precision = playerSide.cloud.isEmpty() ? 0.0D : 1.0D - playerErrorSum / playerSide.cloud.size();
        double shape = cover + precision > 0.0D ? 2.0D * cover * precision / (cover + precision) : 0.0D;

        double topology = 0.15D * countTerm(refSide.strokeCount, playerSide.strokeCount, 0.0D)
                + 0.25D * countTerm(refSide.endpoints, playerSide.endpoints, 1.0D)
                // One crossing, or one closed face, is not a difference. With a 12px tolerance whether a line
                // touches another or runs a few pixels short of it is a coin flip, and the shape term already
                // judges where the lines are; counting it twice is what a merely wobbly trace paid for.
                + 0.25D * countTerm(refSide.intersections, playerSide.intersections, 1.0D)
                + 0.25D * countTerm(refSide.faces, playerSide.faces, 1.0D)
                + 0.10D * lengthTerm(refSide.totalLength, playerSide.totalLength);

        // Step 5-6: order term, total distance and the exponential completion mapping.
        double order = orderDistance(refSide, playerSide, params, tol2);
        double sigma = Double.isFinite(params.sigma()) && params.sigma() > 0.0D
                ? params.sigma() : Judgement.DEFAULT.sigma();
        double total = (1.0D - shape) + nonNegative(params.directionWeight()) * direction
                + nonNegative(params.topologyWeight()) * topology + nonNegative(params.orderWeight()) * order;
        if (!Double.isFinite(total)) return degenerate();
        double completion = Math.min(1.0D, Math.max(0.0D, Math.exp(-total / sigma)));
        return new Score(completion, clamp01(cover), clamp01(precision), clamp01(shape), clamp01(direction),
                clamp01(topology), clamp01(order), total, countPoints(playerKeys), anyClosed(playerKeys, tolPx), false);
    }

    /**
     * §4.3 step 5: per-stroke F1, matched <em>by shape</em> rather than by position in the list: which stroke the
     * player drew first is not on the screen (the guide only shows numbers when the recipe asks for it), so it
     * cannot change what the drawing is worth. A count mismatch still scales the whole term down.
     */
    private static double orderDistance(Side ref, Side player, Judgement params, double tol2) {
        int n = ref.strokes.size();
        int m = player.strokes.size();
        if (n != m && params.strokeCountStrict()) return 1.0D;
        int pairs = Math.min(n, m);
        if (pairs == 0) return 1.0D;
        double[][] cover = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < m; k++) {
                double refCover = strokeCover(ref, i, player, k, tol2);
                double playerCover = strokeCover(player, k, ref, i, tol2);
                cover[i][k] = refCover + playerCover > 0.0D
                        ? 2.0D * refCover * playerCover / (refCover + playerCover) : 0.0D;
            }
        }
        boolean[] takenRef = new boolean[n];
        boolean[] takenPlayer = new boolean[m];
        double sum = 0.0D;
        for (int pair = 0; pair < pairs; pair++) {
            double best = -1.0D;
            int bestRef = -1;
            int bestPlayer = -1;
            for (int i = 0; i < n; i++) {
                if (takenRef[i]) continue;
                for (int k = 0; k < m; k++) {
                    if (takenPlayer[k] || cover[i][k] <= best) continue;
                    best = cover[i][k];
                    bestRef = i;
                    bestPlayer = k;
                }
            }
            if (bestRef < 0) break;
            takenRef[bestRef] = true;
            takenPlayer[bestPlayer] = true;
            sum += best;
        }
        double mean = sum / pairs;
        return n == m ? 1.0D - mean : 1.0D - mean * pairs / Math.max(n, m);
    }

    /**
     * 1 - mean per-point error of one stroke of {@code from} against one stroke of {@code to}.
     */
    private static double strokeCover(Side from, int fromStroke, Side to, int toStroke, double tol2) {
        List<P> points = from.strokes.get(fromStroke);
        if (points.isEmpty()) return 0.0D;
        int lo = to.strokeStart[toStroke];
        int hi = to.strokeEnd[toStroke];
        double minX = to.strokeMinX[toStroke];
        double minY = to.strokeMinY[toStroke];
        double maxX = to.strokeMaxX[toStroke];
        double maxY = to.strokeMaxY[toStroke];
        double sum = 0.0D;
        for (P p : points) {
            int nearest = to.nearest(p, lo, hi, tol2, minX, minY, maxX, maxY);
            sum += nearest < 0 ? 1.0D : Math.min(1.0D, to.segDist2(nearest, p) / tol2);
        }
        return 1.0D - sum / points.size();
    }

    private static double countTerm(int a, int b, double slack) {
        double diff = Math.abs(a - b) - slack;
        return diff <= 0.0D ? 0.0D : diff / Math.max(Math.max(a, b), 1);
    }

    /**
     * Total length is the one item with a relative slack (5%), so it needs its own form.
     */
    private static double lengthTerm(double a, double b) {
        double larger = Math.max(a, b);
        double diff = Math.abs(a - b) - 0.05D * larger;
        return diff <= 0.0D ? 0.0D : diff / Math.max(larger, 1.0D);
    }

    /**
     * Topology counts of the simplified key point graph; segments are split at their intersections.
     */
    private static Graph buildGraph(List<List<P>> keys, double tolPx) {
        Dsu dsu = new Dsu();
        List<P> nodes = new ArrayList<>();
        List<List<Integer>> handles = new ArrayList<>(keys.size());
        for (List<P> stroke : keys) {
            List<Integer> ids = new ArrayList<>(stroke.size());
            for (P p : stroke) {
                ids.add(dsu.add());
                nodes.add(p);
            }
            handles.add(ids);
            // Closed stroke: first and last endpoint are the same node (DTW.md §1 "merge endpoints").
            if (ids.size() >= 2 && dist(stroke.getFirst(), stroke.getLast()) < 2.0D * tolPx)
                dsu.union(ids.getFirst(), ids.getLast());
        }
        List<double[]> geometry = new ArrayList<>();
        List<int[]> ends = new ArrayList<>();
        for (int s = 0; s < keys.size(); s++) {
            List<P> stroke = keys.get(s);
            List<Integer> ids = handles.get(s);
            for (int i = 0; i + 1 < stroke.size(); i++) {
                P a = stroke.get(i);
                P b = stroke.get(i + 1);
                geometry.add(new double[]{a.x(), a.y(), b.x(), b.y()});
                ends.add(new int[]{ids.get(i), ids.get(i + 1)});
            }
        }
        int count = geometry.size();
        List<List<Split>> splits = new ArrayList<>(count);
        for (int i = 0; i < count; i++) splits.add(new ArrayList<>());
        int intersections = 0;
        for (int i = 0; i < count; i++) {
            double[] a = geometry.get(i);
            int[] aEnds = ends.get(i);
            double aMinX = Math.min(a[0], a[2]);
            double aMaxX = Math.max(a[0], a[2]);
            double aMinY = Math.min(a[1], a[3]);
            double aMaxY = Math.max(a[1], a[3]);
            for (int k = i + 1; k < count; k++) {
                int[] bEnds = ends.get(k);
                if (sameNode(dsu, aEnds[0], bEnds[0]) || sameNode(dsu, aEnds[0], bEnds[1])
                        || sameNode(dsu, aEnds[1], bEnds[0]) || sameNode(dsu, aEnds[1], bEnds[1])) continue;
                double[] b = geometry.get(k);
                if (Math.max(aMinX, Math.min(b[0], b[2])) > Math.min(aMaxX, Math.max(b[0], b[2]))
                        || Math.max(aMinY, Math.min(b[1], b[3])) > Math.min(aMaxY, Math.max(b[1], b[3])))
                    continue;
                double rx = a[2] - a[0];
                double ry = a[3] - a[1];
                double sx = b[2] - b[0];
                double sy = b[3] - b[1];
                double denom = rx * sy - ry * sx;
                if (Math.abs(denom) < 1.0E-12D) continue;
                double qx = b[0] - a[0];
                double qy = b[1] - a[1];
                double t = (qx * sy - qy * sx) / denom;
                double u = (qx * ry - qy * rx) / denom;
                if (t < -PARAM_EPS || t > 1.0D + PARAM_EPS || u < -PARAM_EPS || u > 1.0D + PARAM_EPS) continue;
                intersections++;
                // Touching an endpoint reuses that endpoint's node, so the two strokes really connect.
                int node;
                if (t <= PARAM_EPS) node = aEnds[0];
                else if (t >= 1.0D - PARAM_EPS) node = aEnds[1];
                else if (u <= PARAM_EPS) node = bEnds[0];
                else if (u >= 1.0D - PARAM_EPS) node = bEnds[1];
                else {
                    node = dsu.add();
                    nodes.add(new P(a[0] + t * rx, a[1] + t * ry));
                }
                addSplit(splits.get(i), t, node, aEnds, dsu);
                addSplit(splits.get(k), u, node, bEnds, dsu);
            }
        }
        // Node set is final now; collapse handles that landed on top of each other numerically.
        if (nodes.size() <= NODE_MERGE_LIMIT) {
            double limit = NODE_MERGE_PX * NODE_MERGE_PX;
            for (int i = 0; i < nodes.size(); i++) {
                for (int k = i + 1; k < nodes.size(); k++) {
                    if (distanceSq(nodes.get(i), nodes.get(k)) <= limit) dsu.union(i, k);
                }
            }
        }
        int[] degree = new int[nodes.size()];
        Dsu components = new Dsu(nodes.size());
        for (int i = 0; i < nodes.size(); i++) components.add();
        int edges = 0;
        for (int i = 0; i < count; i++) {
            int[] segmentEnds = ends.get(i);
            List<Split> segmentSplits = splits.get(i);
            segmentSplits.sort(Comparator.comparingDouble(Split::t));
            int previous = segmentEnds[0];
            for (Split split : segmentSplits) {
                if (dsu.find(previous) != dsu.find(split.node)) {
                    int ra = dsu.find(previous);
                    int rb = dsu.find(split.node);
                    degree[ra]++;
                    degree[rb]++;
                    components.union(ra, rb);
                    edges++;
                }
                previous = split.node;
            }
            if (dsu.find(previous) != dsu.find(segmentEnds[1])) {
                int ra = dsu.find(previous);
                int rb = dsu.find(segmentEnds[1]);
                degree[ra]++;
                degree[rb]++;
                components.union(ra, rb);
                edges++;
            }
        }
        int nodeCount = 0;
        int endpointCount = 0;
        for (int i = 0; i < nodes.size(); i++) {
            if (dsu.find(i) == i) {
                nodeCount++;
                if (degree[i] == 1) endpointCount++;
            }
        }
        int componentCount = 0;
        boolean[] seen = new boolean[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            int root = components.find(dsu.find(i));
            if (!seen[root]) {
                seen[root] = true;
                componentCount++;
            }
        }
        return new Graph(keys.size(), endpointCount, intersections, edges - nodeCount + componentCount + 1);
    }

    private static void addSplit(List<Split> splits, double t, int node, int[] ends, Dsu dsu) {
        if (t <= PARAM_EPS) {
            dsu.union(node, ends[0]);
            return;
        }
        if (t >= 1.0D - PARAM_EPS) {
            dsu.union(node, ends[1]);
            return;
        }
        splits.add(new Split(t, node));
    }

    private static boolean sameNode(Dsu dsu, int a, int b) {
        return dsu.find(a) == dsu.find(b);
    }

    private static List<List<P>> simplifyAll(List<List<P>> strokes, double epsilon) {
        List<List<P>> out = new ArrayList<>(strokes.size());
        for (List<P> stroke : strokes) out.add(simplify(stroke, epsilon));
        return out;
    }

    /**
     * Ramer-Douglas-Peucker; endpoints are always kept.
     */
    private static List<P> simplify(List<P> points, double epsilon) {
        int n = points.size();
        if (n <= 2) return new ArrayList<>(points);
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{0, n - 1});
        while (!stack.isEmpty()) {
            int[] range = stack.pop();
            double worst = -1.0D;
            int worstIndex = -1;
            for (int i = range[0] + 1; i < range[1]; i++) {
                double d = pointSegmentDistance(points.get(i), points.get(range[0]), points.get(range[1]));
                if (d > worst) {
                    worst = d;
                    worstIndex = i;
                }
            }
            if (worstIndex >= 0 && worst > epsilon) {
                keep[worstIndex] = true;
                stack.push(new int[]{range[0], worstIndex});
                stack.push(new int[]{worstIndex, range[1]});
            }
        }
        List<P> out = new ArrayList<>();
        for (int i = 0; i < n; i++) if (keep[i]) out.add(points.get(i));
        return out;
    }

    private static boolean anyClosed(List<List<P>> keys, double tolPx) {
        for (List<P> stroke : keys) {
            if (stroke.size() >= 2 && dist(stroke.getFirst(), stroke.getLast()) < 2.0D * tolPx)
                return true;
        }
        return false;
    }

    /**
     * Point source is scaled into pixel space here: pattern [u,v] by the canvas, player coordinates 1:1.
     */
    private static List<List<P>> toPixelSpace(List<Stroke> strokes, double scaleX, double scaleY) {
        List<List<P>> out = new ArrayList<>();
        if (strokes == null) return out;
        for (Stroke stroke : strokes) {
            if (stroke == null || stroke.points() == null) continue;
            List<P> points = new ArrayList<>(stroke.points().size());
            for (Point p : stroke.points()) {
                if (p != null && Double.isFinite(p.x()) && Double.isFinite(p.y()))
                    points.add(new P(p.x() * scaleX, p.y() * scaleY));
            }
            if (points.size() >= 2) out.add(points);
        }
        return out;
    }

    /**
     * Drop every point closer than 1px to the previously kept one (mouse event duplicates).
     */
    private static List<List<P>> thin(List<List<P>> strokes) {
        List<List<P>> out = new ArrayList<>(strokes.size());
        for (List<P> stroke : strokes) {
            List<P> kept = new ArrayList<>(stroke.size());
            for (P p : stroke) {
                if (kept.isEmpty() || distanceSq(kept.getLast(), p) >= 1.0D) kept.add(p);
            }
            if (kept.size() >= 2) out.add(kept);
        }
        return out;
    }

    private static List<List<P>> dropShorterThan(List<List<P>> strokes, double minLength) {
        List<List<P>> out = new ArrayList<>(strokes.size());
        for (List<P> stroke : strokes) if (strokeLength(stroke) >= minLength) out.add(stroke);
        return out;
    }

    private static List<List<P>> resample(List<List<P>> strokes) {
        List<List<P>> out = new ArrayList<>(strokes.size());
        for (List<P> stroke : strokes) out.add(resampleStroke(stroke));
        return out;
    }

    /**
     * Even arc-length sampling with S = clamp(round(length / RESAMPLE_STEP), 16, 256) points.
     */
    private static List<P> resampleStroke(List<P> points) {
        if (points.size() < 2) return repeated(points.isEmpty() ? new P(0.0D, 0.0D) : points.getFirst());
        int n = points.size();
        double[] cumulative = new double[n];
        for (int i = 1; i < n; i++) cumulative[i] = cumulative[i - 1] + dist(points.get(i - 1), points.get(i));
        double length = cumulative[n - 1];
        if (length <= 0.0D) return repeated(points.getFirst());
        int samples = (int) Math.round(length / RESAMPLE_STEP);
        samples = Math.max(MIN_SAMPLES, Math.min(MAX_SAMPLES, samples));
        double step = length / (samples - 1);
        List<P> out = new ArrayList<>(samples);
        int segment = 0;
        for (int k = 0; k < samples; k++) {
            double target = k * step;
            while (segment < n - 2 && cumulative[segment + 1] < target) segment++;
            double span = cumulative[segment + 1] - cumulative[segment];
            double t = span <= 0.0D ? 0.0D : (target - cumulative[segment]) / span;
            t = Math.min(1.0D, Math.max(0.0D, t));
            P a = points.get(segment);
            P b = points.get(segment + 1);
            out.add(new P(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t));
        }
        return out;
    }

    private static List<P> repeated(P p) {
        List<P> out = new ArrayList<>(MIN_SAMPLES);
        for (int i = 0; i < MIN_SAMPLES; i++) out.add(p);
        return out;
    }

    private static double rms(List<List<P>> strokes) {
        double[] centre = centroid(strokes);
        int n = countPoints(strokes);
        if (n == 0) return 0.0D;
        double sum = 0.0D;
        for (List<P> stroke : strokes) {
            for (P p : stroke) {
                double dx = p.x() - centre[0];
                double dy = p.y() - centre[1];
                sum += dx * dx + dy * dy;
            }
        }
        return Math.sqrt(sum / n);
    }

    private static List<List<P>> translateScale(List<List<P>> strokes, double rms) {
        double[] centre = centroid(strokes);
        List<List<P>> out = new ArrayList<>(strokes.size());
        for (List<P> stroke : strokes) {
            List<P> moved = new ArrayList<>(stroke.size());
            for (P p : stroke) moved.add(new P((p.x() - centre[0]) / rms, (p.y() - centre[1]) / rms));
            out.add(moved);
        }
        return out;
    }

    private static double[] centroid(List<List<P>> strokes) {
        double x = 0.0D;
        double y = 0.0D;
        int n = 0;
        for (List<P> stroke : strokes) {
            for (P p : stroke) {
                x += p.x();
                y += p.y();
                n++;
            }
        }
        return n == 0 ? new double[]{0.0D, 0.0D} : new double[]{x / n, y / n};
    }

    private static int countPoints(List<List<P>> strokes) {
        int n = 0;
        for (List<P> stroke : strokes) n += stroke.size();
        return n;
    }

    private static double arcLength(List<List<P>> strokes) {
        double total = 0.0D;
        for (List<P> stroke : strokes) total += strokeLength(stroke);
        return total;
    }

    private static double strokeLength(List<P> points) {
        double total = 0.0D;
        for (int i = 1; i < points.size(); i++) total += dist(points.get(i - 1), points.get(i));
        return total;
    }

    private static double[] tangentAt(List<P> stroke, int i) {
        P a = stroke.get(Math.max(0, i - 1));
        P b = stroke.get(Math.min(stroke.size() - 1, i + 1));
        return new double[]{b.x() - a.x(), b.y() - a.y()};
    }

    /**
     * Tangents are undirected, so the angle is folded into [0, pi/2].
     */
    private static double foldedAngle(double tx, double ty, double sx, double sy) {
        double angle = Math.atan2(Math.abs(tx * sy - ty * sx), tx * sx + ty * sy);
        return angle > HALF_PI ? Math.PI - angle : angle;
    }

    private static double pointSegmentDistance(P p, P a, P b) {
        return Math.sqrt(segDist2(p.x(), p.y(), a.x(), a.y(), b.x(), b.y()));
    }

    private static double segDist2(double px, double py, double ax, double ay, double bx, double by) {
        double ex = bx - ax;
        double ey = by - ay;
        double lengthSq = ex * ex + ey * ey;
        double t = lengthSq <= 0.0D ? 0.0D : ((px - ax) * ex + (py - ay) * ey) / lengthSq;
        if (t < 0.0D) t = 0.0D;
        else if (t > 1.0D) t = 1.0D;
        double dx = px - (ax + t * ex);
        double dy = py - (ay + t * ey);
        return dx * dx + dy * dy;
    }

    private static double aabbDist2(double px, double py, double minX, double minY, double maxX, double maxY) {
        double dx = px < minX ? minX - px : (px > maxX ? px - maxX : 0.0D);
        double dy = py < minY ? minY - py : (py > maxY ? py - maxY : 0.0D);
        return dx * dx + dy * dy;
    }

    private static double dist(P a, P b) {
        return Math.sqrt(distanceSq(a, b));
    }

    private static double distanceSq(P a, P b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return dx * dx + dy * dy;
    }

    private static double nonNegative(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }

    private static double clamp01(double value) {
        return Math.min(1.0D, Math.max(0.0D, value));
    }

    private static Score degenerate() {
        return new Score(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0, false, true);
    }

    /**
     * Normalized side: point cloud, dense segment set with per-stroke ranges, and topology counts.
     */
    private static final class Side {
        final List<List<P>> strokes;
        final List<P> cloud;
        final double[] sax;
        final double[] say;
        final double[] sbx;
        final double[] sby;
        final int[] strokeStart;
        final int[] strokeEnd;
        final double[] strokeMinX;
        final double[] strokeMinY;
        final double[] strokeMaxX;
        final double[] strokeMaxY;
        final double minX;
        final double minY;
        final double maxX;
        final double maxY;
        final double totalLength;
        final int segmentCount;
        final int strokeCount;
        final int endpoints;
        final int intersections;
        final int faces;

        Side(List<List<P>> strokes, Graph graph) {
            this.strokes = strokes;
            this.cloud = new ArrayList<>();
            for (List<P> stroke : strokes) this.cloud.addAll(stroke);
            int segments = 0;
            for (List<P> stroke : strokes) if (stroke.size() > 1) segments += stroke.size() - 1;
            this.segmentCount = segments;
            this.sax = new double[segments];
            this.say = new double[segments];
            this.sbx = new double[segments];
            this.sby = new double[segments];
            this.strokeStart = new int[strokes.size()];
            this.strokeEnd = new int[strokes.size()];
            this.strokeMinX = new double[strokes.size()];
            this.strokeMinY = new double[strokes.size()];
            this.strokeMaxX = new double[strokes.size()];
            this.strokeMaxY = new double[strokes.size()];
            this.strokeCount = graph.strokes;
            this.endpoints = graph.endpoints;
            this.intersections = graph.intersections;
            this.faces = graph.faces;
            double length = 0.0D;
            int index = 0;
            boolean any = false;
            double globalMinX = 0.0D;
            double globalMinY = 0.0D;
            double globalMaxX = 0.0D;
            double globalMaxY = 0.0D;
            for (int s = 0; s < strokes.size(); s++) {
                List<P> stroke = strokes.get(s);
                this.strokeStart[s] = index;
                double mnx = 0.0D;
                double mny = 0.0D;
                double mxx = 0.0D;
                double mxy = 0.0D;
                for (int i = 0; i < stroke.size(); i++) {
                    P p = stroke.get(i);
                    if (i == 0) {
                        mnx = p.x();
                        mxx = p.x();
                        mny = p.y();
                        mxy = p.y();
                    } else {
                        mnx = Math.min(mnx, p.x());
                        mxx = Math.max(mxx, p.x());
                        mny = Math.min(mny, p.y());
                        mxy = Math.max(mxy, p.y());
                    }
                    if (i + 1 < stroke.size()) {
                        P q = stroke.get(i + 1);
                        this.sax[index] = p.x();
                        this.say[index] = p.y();
                        this.sbx[index] = q.x();
                        this.sby[index] = q.y();
                        length += Math.hypot(q.x() - p.x(), q.y() - p.y());
                        index++;
                    }
                }
                this.strokeEnd[s] = index;
                this.strokeMinX[s] = mnx;
                this.strokeMinY[s] = mny;
                this.strokeMaxX[s] = mxx;
                this.strokeMaxY[s] = mxy;
                if (!stroke.isEmpty()) {
                    if (any) {
                        globalMinX = Math.min(globalMinX, mnx);
                        globalMinY = Math.min(globalMinY, mny);
                        globalMaxX = Math.max(globalMaxX, mxx);
                        globalMaxY = Math.max(globalMaxY, mxy);
                    } else {
                        globalMinX = mnx;
                        globalMinY = mny;
                        globalMaxX = mxx;
                        globalMaxY = mxy;
                        any = true;
                    }
                }
            }
            this.minX = globalMinX;
            this.minY = globalMinY;
            this.maxX = globalMaxX;
            this.maxY = globalMaxY;
            this.totalLength = length;
        }

        /**
         * Nearest segment index, or -1 when the AABB proves the distance is already >= lim2.
         */
        int nearest(P p, int lo, int hi, double lim2) {
            return this.nearest(p, lo, hi, lim2, this.minX, this.minY, this.maxX, this.maxY);
        }

        int nearest(P p, int lo, int hi, double lim2, double minX, double minY, double maxX, double maxY) {
            if (aabbDist2(p.x(), p.y(), minX, minY, maxX, maxY) >= lim2) return -1;
            int best = -1;
            double bestDist = Double.MAX_VALUE;
            for (int i = lo; i < hi; i++) {
                double d = this.segDist2(i, p);
                if (d < bestDist) {
                    bestDist = d;
                    best = i;
                }
            }
            return best;
        }

        double segDist2(int index, P p) {
            return TalismanDrawingScorer.segDist2(p.x(), p.y(), this.sax[index], this.say[index],
                    this.sbx[index], this.sby[index]);
        }
    }

    private record Graph(int strokes, int endpoints, int intersections, int faces) {
    }

    private record Split(double t, int node) {
    }

    private static final class Dsu {
        private int[] parent;
        private int size;

        Dsu() {
            this(16);
        }

        Dsu(int capacity) {
            this.parent = new int[Math.max(1, capacity)];
        }

        int add() {
            if (this.size == this.parent.length) this.parent = Arrays.copyOf(this.parent, this.parent.length * 2);
            this.parent[this.size] = this.size;
            return this.size++;
        }

        int find(int x) {
            while (this.parent[x] != x) {
                this.parent[x] = this.parent[this.parent[x]];
                x = this.parent[x];
            }
            return x;
        }

        void union(int a, int b) {
            int ra = this.find(a);
            int rb = this.find(b);
            if (ra != rb) this.parent[rb] = ra;
        }
    }

    private record P(double x, double y) {
    }
}
