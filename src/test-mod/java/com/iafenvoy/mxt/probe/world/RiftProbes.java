package com.iafenvoy.mxt.probe.world;

import com.iafenvoy.mxt.item.block.entity.RiftBlockEntity;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.runtime.rift.RiftColors;
import com.iafenvoy.mxt.runtime.rift.RiftConnections;
import com.iafenvoy.mxt.runtime.rift.RiftConnections.Loop;
import com.iafenvoy.mxt.runtime.rift.RiftMesh;
import com.iafenvoy.mxt.runtime.rift.RiftTeleportService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The rift's linking rule, the mesh it is drawn from and its portal behaviour, one GameTest per step of the old
 * probe: every part drives the calls the renderer and the portal use and then inspects the world that came out.
 * What cannot be checked from a server is the drawing itself.
 */
public final class RiftProbes {
    // The north west block of the wall, well inside the arena: the layout reaches six east and one south of it.
    private static final BlockPos BASE = new BlockPos(8, 1, 8);

    private RiftProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("world/rift/linking", RiftProbes::linking),
                Probe.of("world/rift/mesh", RiftProbes::mesh),
                Probe.of("world/rift/loop_share", RiftProbes::loopShare),
                Probe.of("world/rift/isolated", RiftProbes::isolated),
                Probe.of("world/rift/reach", RiftProbes::reach),
                Probe.of("world/rift/colour", RiftProbes::colour),
                Probe.of("world/rift/persistence", RiftProbes::persistence),
                Probe.of("world/rift/arrival", RiftProbes::arrival),
                Probe.of("world/rift/portal", RiftProbes::portal),
                Probe.of("world/rift/removal", RiftProbes::removal));
    }

    // 1. Linking: the middle of the wall has all eight neighbours as links, a corner has three, and the
    //    wall is one network of nine blocks.
    private static void linking(ProbeContext context) {
        ServerLevel level = context.server().overworld();
        Identifier here = level.dimension().identifier();
        BlockPos base = context.at(BASE);
        BlockPos middle = base.offset(1, 1, 0);
        List<BlockPos> touched = new ArrayList<>();
        try {
            RiftBlockEntity centre = wall(level, base, here, touched);
            if (centre == null) {
                context.fail("rift probe: could not place the wall");
                return;
            }
            int links = RiftConnections.connected(level, middle).size();
            RiftBlockEntity corner = level.getBlockEntity(base) instanceof RiftBlockEntity found ? found : null;
            int cornerLinks = corner == null ? -1 : RiftConnections.connected(level, base).size();
            int network = RiftConnections.componentSize(level, middle);
            context.check("rift probe: links=" + links + " corner=" + cornerLinks + " network=" + network,
                    links == 8 && cornerLinks == 3 && network == 9);
        } finally {
            clear(level, touched);
        }
    }

    // 2. The wall's mesh: eight links, twelve triangles, every vertex within half a thickness of the plane the
    //    wall lies in, a link half reaching exactly the midpoint it meets, and a width that really is the
    //    configured thickness rather than a number of its own.
    private static void mesh(ProbeContext context) {
        double thickness = RiftMesh.DEFAULT_THICKNESS;
        ServerLevel level = context.server().overworld();
        Identifier here = level.dimension().identifier();
        BlockPos base = context.at(BASE);
        BlockPos middle = base.offset(1, 1, 0);
        List<BlockPos> touched = new ArrayList<>();
        try {
            RiftBlockEntity centre = wall(level, base, here, touched);
            if (centre == null) {
                context.fail("rift probe: could not place the wall");
                return;
            }
            List<BlockPos> wallLinks = RiftConnections.connected(level, middle);
            List<Loop> loops = RiftConnections.loops(middle, wallLinks);
            List<Vec3> wallMesh = new ArrayList<>(RiftMesh.node(thickness));
            for (BlockPos link : wallLinks) wallMesh.addAll(RiftMesh.linkShare(local(link, middle), thickness));
            double flatness = 0.0;
            for (Vec3 vertex : wallMesh) flatness = Math.max(flatness, Math.abs(vertex.z - 0.5));
            List<Vec3> straightLink = RiftMesh.linkShare(local(base.offset(2, 1, 0), middle), thickness);
            List<Vec3> diagonalLink = RiftMesh.linkShare(local(base.offset(2, 2, 0), middle), thickness);
            double straightReach = axialReach(straightLink, new Vec3(1.0, 0.0, 0.0));
            double diagonalReach = axialReach(diagonalLink, new Vec3(1.0, 1.0, 0.0));
            double linkWidth = radialReach(diagonalLink, new Vec3(1.0, 1.0, 0.0)) / Math.sqrt(2.0) * 2.0;
            context.check("rift probe: mesh_links=" + wallLinks.size() + " triangles=" + loops.size()
                            + " flatness=" + "%.4f".formatted(flatness)
                            + " half_link=" + "%.4f".formatted(straightReach) + "/" + "%.4f".formatted(diagonalReach)
                            + " link_width=" + "%.4f".formatted(linkWidth),
                    wallLinks.size() == 8 && loops.size() == 12
                            && flatness <= thickness / 2.0 + 1.0E-6
                            && close(straightReach, 0.5) && close(diagonalReach, Math.sqrt(2.0) / 2.0)
                            && close(linkWidth, thickness));

            // Counting the loops of the whole wall catches a triangle two of its blocks both claim or neither
            // draws: nine blocks see forty-eight loops, which is sixteen triangles three ways.
            int loopTotal = 0;
            for (int x = 0; x < 3; x++)
                for (int y = 0; y < 3; y++) {
                    BlockPos at = base.offset(x, y, 0);
                    if (!(level.getBlockEntity(at) instanceof RiftBlockEntity)) continue;
                    loopTotal += RiftConnections.loops(at, RiftConnections.connected(level, at)).size();
                }
            context.check("rift probe: wall_loops=" + loopTotal + " triangles=" + (loopTotal / 3), loopTotal == 48);
        } finally {
            clear(level, touched);
        }
    }

    // 3. A loop is drawn by its three blocks as three shares that tile it, and each share is a slab whose
    //    two surfaces are one thickness apart. Every block of the loop agrees the loop is there, the
    //    shares add up to the whole triangle, and each of them is a third of it.
    private static void loopShare(ProbeContext context) {
        double thickness = RiftMesh.DEFAULT_THICKNESS;
        ServerLevel level = context.server().overworld();
        Identifier here = level.dimension().identifier();
        BlockPos base = context.at(BASE);
        BlockPos middle = base.offset(1, 1, 0);
        List<BlockPos> touched = new ArrayList<>();
        try {
            RiftBlockEntity centre = wall(level, base, here, touched);
            if (centre == null) {
                context.fail("rift probe: could not place the wall");
                return;
            }
            List<Loop> loops = RiftConnections.loops(middle, RiftConnections.connected(level, middle));
            Loop loop = loops.isEmpty() ? null : loops.getFirst();
            Vec3 forward = loop == null ? RiftMesh.CENTRE : local(loop.forward(), middle);
            Vec3 backward = loop == null ? RiftMesh.CENTRE : local(loop.backward(), middle);
            double whole = RiftMesh.area(List.of(RiftMesh.CENTRE, forward, backward));
            double first = RiftMesh.area(RiftMesh.triangleFace(RiftMesh.CENTRE, forward, backward));
            double second = RiftMesh.area(RiftMesh.triangleFace(forward, backward, RiftMesh.CENTRE));
            double third = RiftMesh.area(RiftMesh.triangleFace(backward, RiftMesh.CENTRE, forward));
            double slab = slabThickness(RiftMesh.triangleShare(RiftMesh.CENTRE, forward, backward, thickness));
            boolean agreed = loop != null
                    && agreesOnLoop(level, loop.forward(), middle, loop.backward())
                    && agreesOnLoop(level, loop.backward(), middle, loop.forward());
            context.check("rift probe: loop_area=" + "%.4f".formatted(whole)
                            + " shares=" + "%.4f".formatted(first) + "/" + "%.4f".formatted(second) + "/" + "%.4f".formatted(third)
                            + " slab=" + "%.4f".formatted(slab) + " agreed=" + agreed,
                    agreed && close(first, whole / 3.0) && close(second, whole / 3.0) && close(third, whole / 3.0)
                            && close(first + second + third, whole) && close(slab, thickness));
        } finally {
            clear(level, touched);
        }
    }

    // 4. A rift with nothing next to it draws exactly one point: no links, no triangles, and the point is
    //    a cube of the configured thickness rather than a flat square.
    private static void isolated(ProbeContext context) {
        double thickness = RiftMesh.DEFAULT_THICKNESS;
        ServerLevel level = context.server().overworld();
        Identifier here = level.dimension().identifier();
        BlockPos base = context.at(BASE);
        List<BlockPos> touched = new ArrayList<>();
        try {
            RiftBlockEntity centre = wall(level, base, here, touched);
            if (centre == null) {
                context.fail("rift probe: could not place the wall");
                return;
            }
            BlockPos lonePos = base.offset(6, 0, 0);
            RiftBlockEntity lone = placeRift(level, lonePos, here, touched);
            List<BlockPos> loneLinks = lone == null ? List.of() : RiftConnections.connected(level, lonePos);
            List<Vec3> point = RiftMesh.node(thickness);
            double pointSide = cubeSide(point);
            boolean loneAlone = lone != null && RiftConnections.isolated(level, lonePos);
            context.check("rift probe: lone_alone=" + loneAlone + " links=" + loneLinks.size()
                            + " triangles=" + RiftConnections.loops(lonePos, loneLinks).size()
                            + " point_vertices=" + point.size() + " point_side=" + "%.4f".formatted(pointSide),
                    loneAlone && loneLinks.isEmpty() && RiftConnections.loops(lonePos, loneLinks).isEmpty()
                            && point.size() == 24 && close(pointSide, thickness));
        } finally {
            clear(level, touched);
        }
    }

    // 5. Linking is decided by position alone, and reaches the whole 3x3x3: a rift one layer up links to
    //    all nine wall blocks and to the one placed beside the wall, whichever target each of them has.
    private static void reach(ProbeContext context) {
        ServerLevel level = context.server().overworld();
        Identifier here = level.dimension().identifier();
        BlockPos base = context.at(BASE);
        List<BlockPos> touched = new ArrayList<>();
        try {
            RiftBlockEntity centre = wall(level, base, here, touched);
            if (centre == null) {
                context.fail("rift probe: could not place the wall");
                return;
            }
            RiftBlockEntity layer = placeRift(level, base.offset(1, 1, 1), Level.NETHER.identifier(), touched);
            RiftBlockEntity beside = placeRift(level, base.offset(0, 0, 1), here, touched);
            int layerLinks = layer == null ? -1 : RiftConnections.connected(level, base.offset(1, 1, 1)).size();
            int besideLinks = beside == null ? -1 : RiftConnections.connected(level, base.offset(0, 0, 1)).size();
            RiftBlockEntity corner = level.getBlockEntity(base) instanceof RiftBlockEntity found ? found : null;
            int cornerWithExtras = corner == null ? -1 : RiftConnections.connected(level, base).size();
            boolean reached = layer != null && beside != null
                    && !RiftConnections.isolated(level, base.offset(1, 1, 1));
            context.check("rift probe: layer_links=" + layerLinks + " beside_links=" + besideLinks
                            + " corner_links=" + cornerWithExtras + " reached=" + reached,
                    layerLinks == 10 && besideLinks == 5 && cornerWithExtras == 5 && reached);
            level.setBlockAndUpdate(base.offset(1, 1, 1), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(base.offset(0, 0, 1), Blocks.AIR.defaultBlockState());
        } finally {
            clear(level, touched);
        }
    }

    // 6. Colour follows the target dimension unless it is overridden, and the derivation is stable.
    private static void colour(ProbeContext context) {
        ServerLevel level = context.server().overworld();
        Identifier here = level.dimension().identifier();
        BlockPos base = context.at(BASE);
        List<BlockPos> touched = new ArrayList<>();
        try {
            RiftBlockEntity centre = wall(level, base, here, touched);
            if (centre == null) {
                context.fail("rift probe: could not place the wall");
                return;
            }
            RiftBlockEntity lone = placeRift(level, base.offset(6, 0, 0), here, touched);
            if (lone == null) {
                context.fail("rift probe: could not place the lone rift");
                return;
            }
            Identifier end = Level.END.identifier();
            int derived = RiftColors.forDimension(end);
            lone.setTarget(end);
            int resolvedAutomatic = RiftColors.resolve(lone);
            lone.setColor(0xFF12AB34);
            int resolvedOverride = RiftColors.resolve(lone);
            context.check("rift probe: colour_stable=" + (derived == RiftColors.forDimension(end))
                            + " distinct=" + (derived != RiftColors.forDimension(Level.NETHER.identifier()))
                            + " automatic=" + (resolvedAutomatic == derived)
                            + " override=" + RiftColors.format(resolvedOverride),
                    derived == RiftColors.forDimension(end) && derived != RiftColors.forDimension(Level.NETHER.identifier())
                            && resolvedAutomatic == derived && resolvedOverride == 0xFF12AB34);
        } finally {
            clear(level, touched);
        }
    }

    // 7. Destination and colour override survive a save and load, and nothing else is stored: a rift has
    //    no direction and no opacity to lose. The metadata-carrying form is used because a static load has
    //    to read the block entity id back out of the tag.
    private static void persistence(ProbeContext context) {
        ServerLevel level = context.server().overworld();
        Identifier here = level.dimension().identifier();
        BlockPos base = context.at(BASE);
        List<BlockPos> touched = new ArrayList<>();
        try {
            RiftBlockEntity centre = wall(level, base, here, touched);
            if (centre == null) {
                context.fail("rift probe: could not place the wall");
                return;
            }
            BlockPos lonePos = base.offset(6, 0, 0);
            RiftBlockEntity lone = placeRift(level, lonePos, here, touched);
            if (lone == null) {
                context.fail("rift probe: could not place the lone rift");
                return;
            }
            Identifier end = Level.END.identifier();
            lone.setTarget(end);
            lone.setColor(0xFF12AB34);
            CompoundTag saved = lone.saveWithFullMetadata(level.registryAccess());
            BlockEntity reloaded = BlockEntity.loadStatic(lonePos, level.getBlockState(lonePos), saved, level.registryAccess());
            boolean persisted = reloaded instanceof RiftBlockEntity copy
                    && copy.target().equals(end)
                    && copy.color() == 0xFF12AB34;
            context.check("rift probe: persisted=" + persisted, persisted);
        } finally {
            clear(level, touched);
        }
    }

    // 8. An arrival with no rift to land at carves one that leads back, and a second arrival reuses it
    //    rather than stacking a new rift next to the first.
    private static void arrival(ProbeContext context) {
        ServerLevel level = context.server().overworld();
        Identifier here = level.dimension().identifier();
        BlockPos base = context.at(BASE);
        List<BlockPos> touched = new ArrayList<>();
        List<BlockPos> touchedInEnd = new ArrayList<>();
        try {
            RiftBlockEntity centre = wall(level, base, here, touched);
            if (centre == null) {
                context.fail("rift probe: could not place the wall");
                return;
            }
            ServerLevel endLevel = context.server().getLevel(Level.END);
            BlockPos openSky = new BlockPos(0, 200, 0);
            Vec3 firstArrival = endLevel == null ? Vec3.ZERO : RiftTeleportService.arrival(endLevel, level.dimension(), openSky);
            BlockPos carved = endLevel == null ? null : findRiftNear(endLevel, BlockPos.containing(firstArrival), here);
            Vec3 secondArrival = endLevel == null ? Vec3.ZERO : RiftTeleportService.arrival(endLevel, level.dimension(), openSky);
            boolean reused = carved != null && firstArrival.equals(secondArrival);
            if (carved != null) touchedInEnd.add(carved);
            context.check("rift probe: carved_return=" + (carved != null) + " reused=" + reused,
                    carved != null && reused);
        } finally {
            clear(level, touched);
            ServerLevel endLevel = context.server().getLevel(Level.END);
            if (endLevel != null) clear(endLevel, touchedInEnd);
        }
    }

    // 9. The portal itself reports a transition into the configured dimension.
    private static void portal(ProbeContext context) {
        ServerLevel level = context.server().overworld();
        Identifier here = level.dimension().identifier();
        BlockPos base = context.at(BASE);
        BlockPos middle = base.offset(1, 1, 0);
        List<BlockPos> touched = new ArrayList<>();
        try {
            RiftBlockEntity centre = wall(level, base, here, touched);
            if (centre == null) {
                context.fail("rift probe: could not place the wall");
                return;
            }
            Identifier end = Level.END.identifier();
            centre.setTarget(end);
            Entity probe = EntityType.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
            if (probe == null) {
                context.fail("rift probe: could not create the probe entity");
                return;
            }
            TeleportTransition transition = MxtBlocks.RIFT.get().getPortalDestination(level, probe, middle);
            boolean leadsToEnd = transition != null && transition.newLevel().dimension().equals(Level.END);
            context.check("rift probe: transition=" + (transition == null ? "-"
                            : transition.newLevel().dimension().identifier() + "@" + "%.1f".formatted(transition.position().y)),
                    leadsToEnd);
            probe.discard();
        } finally {
            clear(level, touched);
        }
    }

    // 10. Linking is read from the world each time: removing one block drops it out of the network.
    private static void removal(ProbeContext context) {
        ServerLevel level = context.server().overworld();
        Identifier here = level.dimension().identifier();
        BlockPos base = context.at(BASE);
        BlockPos middle = base.offset(1, 1, 0);
        List<BlockPos> touched = new ArrayList<>();
        try {
            RiftBlockEntity centre = wall(level, base, here, touched);
            if (centre == null) {
                context.fail("rift probe: could not place the wall");
                return;
            }
            level.setBlockAndUpdate(base, Blocks.AIR.defaultBlockState());
            int afterRemoval = RiftConnections.connected(level, middle).size();
            context.check("rift probe: after_removal=" + afterRemoval, afterRemoval == 7);
        } finally {
            clear(level, touched);
        }
    }

    // A 3x3 wall of rifts. Every block of it is inside the others' 3x3x3 reach, so all nine link up.
    private static RiftBlockEntity wall(ServerLevel level, BlockPos base, Identifier here, List<BlockPos> touched) {
        RiftBlockEntity centre = null;
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++) {
                RiftBlockEntity placed = placeRift(level, base.offset(x, y, 0), here, touched);
                if (x == 1 && y == 1) centre = placed;
            }
        return centre;
    }

    private static RiftBlockEntity placeRift(ServerLevel level, BlockPos pos, Identifier target,
                                             List<BlockPos> touched) {
        level.setBlockAndUpdate(pos, MxtBlocks.RIFT.get().defaultBlockState());
        touched.add(pos.immutable());
        if (level.getBlockEntity(pos) instanceof RiftBlockEntity rift) {
            rift.configure(target, RiftColors.AUTO);
            return rift;
        }
        return null;
    }

    // Takes the apparatus away again, so the next part starts from the empty arena.
    private static void clear(ServerLevel level, List<BlockPos> touched) {
        for (BlockPos pos : touched) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    // The closest rift leading to {@code target} within a small box, which is how the arrival service's own
    // "carve one and then find it again" behaviour is observed from the outside.
    @Nullable
    private static BlockPos findRiftNear(ServerLevel level, BlockPos around, Identifier target) {
        BlockPos min = around.offset(-3, -3, -3);
        BlockPos max = around.offset(3, 3, 3);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof RiftBlockEntity rift && rift.target().equals(target))
                return pos.immutable();
        }
        return null;
    }

    // A neighbour's centre in {@code self}'s block-local coordinates, which is where the renderer places the
    // links it draws: a neighbour is at most one block away on each axis.
    private static Vec3 local(BlockPos other, BlockPos self) {
        return RiftMesh.CENTRE.add(other.getX() - self.getX(), other.getY() - self.getY(), other.getZ() - self.getZ());
    }

    // The side of a vertex list that is a cube, or -1 when its three extents differ, which is how a mesh claiming
    // to be a point is caught being a flat square instead.
    private static double cubeSide(List<Vec3> vertices) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3 vertex : vertices) {
            minX = Math.min(minX, vertex.x);
            minY = Math.min(minY, vertex.y);
            minZ = Math.min(minZ, vertex.z);
            maxX = Math.max(maxX, vertex.x);
            maxY = Math.max(maxY, vertex.y);
            maxZ = Math.max(maxZ, vertex.z);
        }
        double width = maxX - minX;
        double height = maxY - minY;
        double depth = maxZ - minZ;
        return close(width, height) && close(height, depth) ? width : -1.0;
    }

    // How far a mesh reaches along an axis, measured from the rift's own centre. A link half must reach exactly
    // as far as the midpoint it meets its neighbour's half at.
    private static double axialReach(List<Vec3> vertices, Vec3 axis) {
        Vec3 unit = axis.normalize();
        double reach = 0.0;
        for (Vec3 vertex : vertices) reach = Math.max(reach, vertex.subtract(RiftMesh.CENTRE).dot(unit));
        return reach;
    }

    // How far a mesh reaches away from an axis, measured from the rift's own centre. For a beam that is the corner
    // of its cross-section, so it checks the thickness the link claims to have.
    private static double radialReach(List<Vec3> vertices, Vec3 axis) {
        Vec3 unit = axis.normalize();
        double reach = 0.0;
        for (Vec3 vertex : vertices) {
            Vec3 relative = vertex.subtract(RiftMesh.CENTRE);
            reach = Math.max(reach, relative.subtract(unit.scale(relative.dot(unit))).length());
        }
        return reach;
    }

    // Whether the block at {@code partner} also sees the loop through {@code self} and {@code other}. All three
    // blocks of a loop draw their own share, so two agreeing would leave a gap and this is how it shows up.
    private static boolean agreesOnLoop(ServerLevel level, BlockPos partner, BlockPos self, BlockPos other) {
        if (!(level.getBlockEntity(partner) instanceof RiftBlockEntity)) return false;
        for (Loop candidate : RiftConnections.loops(partner, RiftConnections.connected(level, partner)))
            if (candidate.forward().equals(self) && candidate.backward().equals(other)
                    || candidate.forward().equals(other) && candidate.backward().equals(self)) return true;
        return false;
    }

    // The distance between the two surfaces of a filled triangle share, which is the thickness the fill claims to
    // have. A share is laid out top face, bottom face and then the rims, so a surface vertex and the one four
    // places after it are the same corner of the triangle above and below.
    private static double slabThickness(List<Vec3> share) {
        if (share.size() < 8) return -1.0;
        return share.get(0).distanceTo(share.get(4));
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }
}
