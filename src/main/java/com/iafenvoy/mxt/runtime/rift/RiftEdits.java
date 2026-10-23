package com.iafenvoy.mxt.runtime.rift;

import com.iafenvoy.mxt.data.rift.RiftPlacement;
import com.iafenvoy.mxt.data.rift.RiftShape;
import com.iafenvoy.mxt.data.rift.RiftTemplate;
import com.iafenvoy.mxt.data.rift.RiftVertex;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class RiftEdits {
    private RiftEdits() {
    }

    public static RiftTemplate shape(RiftTemplate data, RiftShape shape) {
        return new RiftTemplate(data.key(), data.placement(), false, shape, data.destination(), data.appearance(), data.durationTicks(), data.enabled());
    }

    public static RiftTemplate placement(RiftTemplate data, RiftPlacement placement) {
        return new RiftTemplate(data.key(), placement, false, data.shape(), data.destination(), data.appearance(), data.durationTicks(), data.enabled());
    }

    public static RiftShape transform(RiftShape shape, Set<Integer> selected, RiftVertex pivot, double du, double dv,
                                      double degrees, double scaleU, double scaleV, double shear) {
        double angle = Math.toRadians(degrees), cos = Math.cos(angle), sin = Math.sin(angle);
        List<RiftVertex> points = new ArrayList<>();
        for (int i = 0; i < shape.vertices().size(); i++) {
            RiftVertex p = shape.vertices().get(i);
            if (!selected.isEmpty() && !selected.contains(i)) {
                points.add(p);
                continue;
            }
            double v = (p.v() - pivot.v()) * scaleV, u = (p.u() - pivot.u()) * scaleU + v * shear;
            points.add(new RiftVertex(pivot.u() + u * cos - v * sin + du, pivot.v() + u * sin + v * cos + dv));
        }
        return new RiftShape(points);
    }

    public static RiftShape generate(String type, double width, double height, int count, long seed) {
        count = Math.max(3, Math.min(64, count));
        List<RiftVertex> points = new ArrayList<>();
        switch (type) {
            case "rectangle" -> points.addAll(List.of(new RiftVertex(-width / 2, 0), new RiftVertex(width / 2, 0),
                    new RiftVertex(width / 2, height), new RiftVertex(-width / 2, height)));
            case "ellipse", "polygon" -> {
                for (int i = 0; i < count; i++) {
                    double angle = Math.PI * 2 * i / count;
                    points.add(new RiftVertex(Math.cos(angle) * width / 2, height / 2 + Math.sin(angle) * height / 2));
                }
            }
            case "arch" -> {
                points.add(new RiftVertex(-width / 2, 0));
                points.add(new RiftVertex(width / 2, 0));
                for (int i = 0; i <= count; i++) {
                    double angle = Math.PI * i / count;
                    points.add(new RiftVertex(Math.cos(angle) * width / 2, height * 0.6 + Math.sin(angle) * height * 0.4));
                }
            }
            case "rounded" -> {
                count = Math.max(4, Math.min(120, count));
                double radius = Math.min(width, height) * 0.2;
                for (int corner = 0; corner < 4; corner++) {
                    double cx = (corner == 0 || corner == 3 ? 1 : -1) * (width / 2 - radius);
                    double cy = height / 2 + (corner < 2 ? 1 : -1) * (height / 2 - radius);
                    for (int i = 0; i <= count / 4; i++) {
                        double angle = Math.PI / 2 * (corner + i / (double) (count / 4));
                        points.add(new RiftVertex(cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius));
                    }
                }
            }
            case "fissure" -> {
                count = Math.max(4, count);
                SplittableRandom random = new SplittableRandom(seed);
                points.add(new RiftVertex(0, 0));
                for (int i = 1; i < count / 2; i++) {
                    double t = i / (double) (count / 2);
                    points.add(new RiftVertex(width * Math.sin(t * Math.PI) * (0.25 + random.nextDouble() * 0.25), height * t));
                }
                points.add(new RiftVertex(0, height));
                for (int i = count / 2 - 1; i > 0; i--) {
                    double t = i / (double) (count / 2);
                    points.add(new RiftVertex(-width * Math.sin(t * Math.PI) * (0.25 + random.nextDouble() * 0.25), height * t));
                }
            }
            default -> {
                return shapeDefault(width, height);
            }
        }
        return new RiftShape(points);
    }

    private static RiftShape shapeDefault(double width, double height) {
        return transform(RiftShape.DEFAULT, Set.of(), new RiftVertex(0, 0), 0, 0, 0, width / 2, height / 2.5, 0);
    }

    public static RiftShape align(RiftShape shape, Set<Integer> selected, String mode) {
        Set<Integer> indices = selected.isEmpty() ? new HashSet<>() : selected;
        if (selected.isEmpty()) for (int i = 0; i < shape.vertices().size(); i++) indices.add(i);
        List<RiftVertex> points = new ArrayList<>(shape.vertices());
        double u = indices.stream().mapToDouble(i -> points.get(i).u()).average().orElse(0);
        double v = indices.stream().mapToDouble(i -> points.get(i).v()).average().orElse(0);
        for (int i : indices) {
            RiftVertex p = points.get(i);
            points.set(i, switch (mode) {
                case "horizontal" -> new RiftVertex(p.u(), v);
                case "vertical" -> new RiftVertex(u, p.v());
                case "symmetric" -> new RiftVertex(Math.copySign(Math.abs(p.u()), p.u()), p.v());
                default -> p;
            });
        }
        return new RiftShape(points);
    }

    public static RiftShape chamfer(RiftShape shape, Set<Integer> selected, double fraction) {
        List<RiftVertex> points = new ArrayList<>();
        List<RiftVertex> source = shape.vertices();
        for (int i = 0; i < source.size(); i++) {
            RiftVertex p = source.get(i);
            if (!selected.isEmpty() && !selected.contains(i)) {
                points.add(p);
                continue;
            }
            points.add(lerp(p, source.get((i + source.size() - 1) % source.size()), fraction));
            points.add(lerp(p, source.get((i + 1) % source.size()), fraction));
        }
        return new RiftShape(points);
    }

    public static RiftShape offset(RiftShape shape, double amount) {
        List<RiftVertex> result = new ArrayList<>();
        List<RiftVertex> points = shape.vertices();
        double sign = Math.signum(RiftGeometry.area(points));
        for (int i = 0; i < points.size(); i++) {
            RiftVertex a = points.get((i + points.size() - 1) % points.size()), b = points.get(i), c = points.get((i + 1) % points.size());
            double dx = b.u() - a.u(), dy = b.v() - a.v(), ex = c.u() - b.u(), ey = c.v() - b.v();
            double length = Math.hypot(dx, dy), next = Math.hypot(ex, ey);
            double nx = sign * dy / length, ny = -sign * dx / length, mx = sign * ey / next, my = -sign * ex / next;
            double denominator = 1 + nx * mx + ny * my;
            result.add(new RiftVertex(b.u() + amount * (nx + mx) / denominator, b.v() + amount * (ny + my) / denominator));
        }
        return new RiftShape(result);
    }

    public static RiftShape curve(RiftShape shape, int edge, RiftVertex control1, RiftVertex control2, double tolerance) {
        List<RiftVertex> points = shape.vertices();
        int index = Math.floorMod(edge, points.size());
        List<RiftVertex> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            result.add(points.get(i));
            if (i == index)
                sample(points.get(i), control1, control2, points.get((i + 1) % points.size()), Math.max(0.001, tolerance), 0, result);
        }
        return new RiftShape(result);
    }

    private static void sample(RiftVertex a, RiftVertex b, RiftVertex c, RiftVertex d, double tolerance, int depth, List<RiftVertex> result) {
        if (result.size() > RiftShape.MAX_VERTICES || depth >= 10) {
            result.add(lerp(a, d, 0.5));
            return;
        }
        double dx = d.u() - a.u(), dy = d.v() - a.v(), length = Math.max(1e-12, Math.hypot(dx, dy));
        double error = Math.max(Math.abs(dx * (b.v() - a.v()) - dy * (b.u() - a.u())),
                Math.abs(dx * (c.v() - a.v()) - dy * (c.u() - a.u()))) / length;
        if (error <= tolerance) return;
        RiftVertex ab = lerp(a, b, 0.5), bc = lerp(b, c, 0.5), cd = lerp(c, d, 0.5);
        RiftVertex abc = lerp(ab, bc, 0.5), bcd = lerp(bc, cd, 0.5), mid = lerp(abc, bcd, 0.5);
        sample(a, ab, abc, mid, tolerance, depth + 1, result);
        result.add(mid);
        sample(mid, bcd, cd, d, tolerance, depth + 1, result);
    }

    public static RiftVertex lerp(RiftVertex a, RiftVertex b, double t) {
        return new RiftVertex(a.u() + (b.u() - a.u()) * t, a.v() + (b.v() - a.v()) * t);
    }

    public static double snap(double value, double spacing) {
        return spacing > 0 ? Math.rint(value / spacing) * spacing : value;
    }

    public static RiftPlacement plane(Vec3 first, Vec3 second, Vec3 third) {
        Vec3 tangent = second.subtract(first), normal = tangent.cross(third.subtract(first));
        return new RiftPlacement(first, normal, tangent);
    }
}
