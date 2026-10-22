package com.iafenvoy.mxt.probe.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.attachment.RiftChunkAttachment;
import com.iafenvoy.mxt.attachment.RiftWorldAttachment;
import com.iafenvoy.mxt.data.item.SecretRealmTokenComponent;
import com.iafenvoy.mxt.data.rift.*;
import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.item.RiftAnchorItem;
import com.iafenvoy.mxt.network.payload.RiftEditC2SPayload;
import com.iafenvoy.mxt.network.payload.RiftEditorS2CPayload;
import com.iafenvoy.mxt.network.payload.RiftSyncS2CPayload;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.*;
import com.iafenvoy.mxt.runtime.rift.*;
import com.iafenvoy.mxt.runtime.world.SecretRealmRecord;
import com.iafenvoy.mxt.runtime.world.SecretRealmRegistry;
import com.iafenvoy.mxt.runtime.world.SecretRealmService;
import com.iafenvoy.mxt.screen.rift.RiftEditSession;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.ServerOpList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import org.jspecify.annotations.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RiftProbes {
    private RiftProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("world/rift/geometry", RiftProbes::geometry), Probe.of("world/rift/validation", RiftProbes::validation),
                Probe.of("world/rift/contact", RiftProbes::contact), Probe.of("world/rift/edits", RiftProbes::edits),
                Probe.of("world/rift/history", RiftProbes::history),
                Probe.of("world/rift/lifecycle", RiftProbes::lifecycle), Probe.of("world/rift/layout", RiftProbes::layout),
                Probe.of("world/rift/landing", RiftProbes::landing), Probe.of("world/rift/coordinates", RiftProbes::coordinates),
                Probe.of("world/rift/identity", RiftProbes::identity),
                Probe.of("world/rift/summon", RiftProbes::summon), Probe.of("world/rift/binding", RiftProbes::binding),
                Probe.of("world/rift/preparation", RiftProbes::preparation), Probe.of("world/rift/permissions", RiftProbes::permissions),
                Probe.of("world/rift/wait_cancel", RiftProbes::waitCancel), Probe.of("world/rift/session", RiftProbes::session),
                Probe.of("world/rift/nonliving", RiftProbes::nonliving), Probe.of("world/rift/tracking", RiftProbes::tracking),
                Probe.of("world/rift/ticket_lifecycle", RiftProbes::ticketLifecycle), Probe.of("world/rift/ticket_expiry", RiftProbes::ticketExpiry),
                Probe.of("world/rift/ticket_cleanup", RiftProbes::ticketCleanup),
                Probe.of("world/rift/anchor_selection", RiftProbes::anchorSelection),
                Probe.of("world/rift/chunk_storage", RiftProbes::chunkStorage),
                Probe.of("world/rift/chunk_move", RiftProbes::chunkMove),
                Probe.of("world/rift/chunk_lifecycle", RiftProbes::chunkLifecycle),
                Probe.of("world/rift/chunk_stale", RiftProbes::chunkStale),
                Probe.of("world/rift/chunk_expiry", RiftProbes::chunkExpiry),
                Probe.of("world/rift/chunk_migration", RiftProbes::chunkMigration));
    }

    private static RiftShape concave() {
        return new RiftShape(List.of(new RiftVertex(0, 0), new RiftVertex(3, 0), new RiftVertex(3, 1), new RiftVertex(1, 1), new RiftVertex(1, 3), new RiftVertex(0, 3)));
    }

    private static void close(ProbeContext c, String name, double actual, double expected) {
        c.check(name + " " + actual + " != " + expected, Math.abs(actual - expected) < 1e-7);
    }

    private static RiftTemplate template(ProbeContext c, long duration) {
        return new RiftTemplate("exit", new RiftPlacement(Vec3.atLowerCornerOf(c.at(12, 0, 12)), new Vec3(0, 0, 1), new Vec3(1, 0, 0)), false,
                RiftShape.DEFAULT, new RiftDestination.Position(Optional.of(c.level().dimension().identifier()), Vec3.atLowerCornerOf(c.at(20, 0, 20)), Optional.empty(), Optional.empty(), false), RiftAppearance.DEFAULT, duration, true);
    }

    private static void geometry(ProbeContext c) {
        RiftShape shape = concave();
        List<RiftGeometry.Triangle> triangles = RiftGeometry.triangulate(shape).result().orElseThrow();
        double sum = 0;
        for (RiftGeometry.Triangle t : triangles)
            sum += Math.abs(RiftGeometry.area(List.of(shape.vertices().get(t.a()), shape.vertices().get(t.b()), shape.vertices().get(t.c()))));
        close(c, "triangle area", sum, 5);
        c.expect("n-2 triangles", triangles.size(), 4);
        c.check("concave cutout", !RiftGeometry.contains(shape, new RiftVertex(2, 2)));
        ArrayList<RiftVertex> points = new ArrayList<>(shape.vertices());
        Collections.reverse(points);
        c.check("reverse winding", RiftGeometry.triangulate(new RiftShape(points)).isSuccess());
        RiftPlacement p = new RiftPlacement(new Vec3(29_000_000, 64, -29_000_000), new Vec3(1, 1, 1), new Vec3(1, 0, 0)).normalized().result().orElseThrow();
        RiftVertex local = p.local(p.world(new RiftVertex(.0625, 2)));
        close(c, "far u", local.u(), .0625);
        close(c, "far v", local.v(), 2);
    }

    private static void validation(ProbeContext c) {
        c.check("self intersection", RiftGeometry.triangulate(new RiftShape(List.of(new RiftVertex(0, 0), new RiftVertex(2, 2), new RiftVertex(0, 2), new RiftVertex(2, 0)))).isError());
        c.check("duplicate", RiftGeometry.triangulate(new RiftShape(List.of(new RiftVertex(0, 0), new RiftVertex(0, 0), new RiftVertex(1, 1)))).isError());
        c.check("collinear", RiftGeometry.triangulate(new RiftShape(List.of(new RiftVertex(0, 0), new RiftVertex(1, 0), new RiftVertex(2, 0)))).isError());
        c.check("non finite", RiftGeometry.triangulate(new RiftShape(List.of(new RiftVertex(Double.NaN, 0), new RiftVertex(1, 0), new RiftVertex(0, 1)))).isError());
        c.check("size", RiftGeometry.triangulate(RiftEdits.generate("rectangle", 33, 2, 4, 0)).isError());
        c.check("basis", new RiftPlacement(Vec3.ZERO, new Vec3(1, 0, 0), new Vec3(2, 0, 0)).normalized().isError());
    }

    private static void contact(ProbeContext c) {
        RiftShape shape = concave();
        RiftPlacement placement = new RiftPlacement(Vec3.ZERO, new Vec3(0, 0, 1), new Vec3(1, 0, 0));
        RiftGeometry.Mesh mesh = RiftGeometry.mesh(shape, placement, RiftGeometry.triangulate(shape).result().orElseThrow());
        c.check("front", Double.isFinite(mesh.contact(new AABB(.2, .2, -.1, .8, .8, .1), Vec3.ZERO)));
        c.check("cutout does not trigger", !Double.isFinite(mesh.contact(new AABB(1.8, 1.8, -.1, 2.2, 2.2, .1), Vec3.ZERO)));
        close(c, "swept time", mesh.contact(new AABB(.2, .2, -10, .8, .8, -9), new Vec3(0, 0, 20)), (9 - .025) / 20);
        RiftPlacement sloped = new RiftPlacement(Vec3.ZERO, new Vec3(1, 1, 1), new Vec3(1, 0, 0)).normalized().result().orElseThrow();
        RiftGeometry.Mesh diagonal = RiftGeometry.mesh(RiftShape.DEFAULT, sloped, RiftGeometry.triangulate(RiftShape.DEFAULT).result().orElseThrow());
        Vec3 inside = sloped.world(new RiftVertex(0, 1));
        c.check("sloped", Double.isFinite(diagonal.contact(AABB.ofSize(inside, .2, .2, .2), Vec3.ZERO)));
    }

    private static void edits(ProbeContext c) {
        for (String type : List.of("rectangle", "ellipse", "polygon", "arch", "rounded", "fissure"))
            c.check("generator " + type, RiftGeometry.triangulate(RiftEdits.generate(type, 2, 3, 16, 101)).isSuccess());
        c.expect("seed stable", RiftEdits.generate("fissure", 2, 3, 16, 101), RiftEdits.generate("fissure", 2, 3, 16, 101));
        RiftShape moved = RiftEdits.transform(concave(), Set.of(), new RiftVertex(0, 0), 5, 2, 90, 2, 2, 0);
        close(c, "transform area", Math.abs(RiftGeometry.area(moved.vertices())), 20);
        RiftShape curve = RiftEdits.curve(RiftEdits.generate("rectangle", 2, 3, 4, 0), 0, new RiftVertex(-1, -1), new RiftVertex(1, -1), .02);
        c.check("curve subdivision", curve.vertices().size() > 4 && RiftGeometry.triangulate(curve).isSuccess());
        RiftShape inserted = new RiftShape(List.of(new RiftVertex(-1, 0), new RiftVertex(0, 0), new RiftVertex(1, 0),
                new RiftVertex(1, 3), new RiftVertex(-1, 3)));
        List<RiftGeometry.Triangle> triangles = RiftGeometry.triangulate(inserted).result().orElseThrow();
        c.expect("edge insertion keeps its editable point", inserted.vertices().size(), 5);
        double area = 0;
        for (RiftGeometry.Triangle triangle : triangles)
            area += Math.abs(RiftGeometry.area(List.of(inserted.vertices().get(triangle.a()),
                    inserted.vertices().get(triangle.b()), inserted.vertices().get(triangle.c()))));
        close(c, "edge insertion preserves area", area, 6);
    }

    private static void history(ProbeContext c) {
        RiftTemplate initial = template(c, 0);
        RiftEditSession session = new RiftEditSession(initial);
        RiftTemplate shifted = RiftEdits.shape(initial, RiftEdits.transform(initial.shape(), Set.of(), new RiftVertex(0, 0), .25, 0, 0, 1, 1, 0));
        session.transientDraft(shifted);
        session.transientDraft(RiftEdits.shape(initial, RiftEdits.transform(initial.shape(), Set.of(), new RiftVertex(0, 0), .5, 0, 0, 1, 1, 0)));
        session.finishDrag(initial);
        session.undo();
        c.expect("one drag one undo", session.draft(), initial);
        session.redo();
        close(c, "redo", session.draft().shape().vertices().getFirst().u(), -.5);
    }

    private static void lifecycle(ProbeContext c) {
        RiftResult result = RiftService.publish(c.level(), null, 0, template(c, 8), c.player().getUUID(), true);
        c.check("created", result.isSuccess());
        if (result.isFailure()) return;
        RiftRecord r = result.record();
        long deadline = r.expiresAt();
        RiftResult edited = RiftService.publish(c.level(), r.id(), r.revision(), RiftEdits.shape(r.data(), RiftEdits.transform(r.data().shape(), Set.of(), new RiftVertex(0, 0), .1, 0, 0, 1, 1, 0)), c.player().getUUID(), false);
        c.check("edit success", edited.isSuccess());
        c.expect("expiry preserved", edited.record().expiresAt(), deadline);
        c.check("stale revision rejected", RiftService.publish(c.level(), r.id(), r.revision(), r.data(), null, false).isFailure());
        c.after(12, () -> {
            c.check("expired removed", RiftService.get(c.level(), r.id()) == null);
            c.done();
        });
    }

    private static void chunkStorage(ProbeContext c) {
        RiftTemplate base = template(c, 1200);
        RiftTemplate data = RiftEdits.shape(base, RiftEdits.transform(base.shape(), Set.of(), new RiftVertex(0, 0), 40, 0, 0, 1, 1, 0));
        RiftResult result = RiftService.publish(c.level(), null, 0, data, c.player().getUUID(), true);
        if (!c.check("portal published", result.isSuccess())) return;
        RiftRecord r = result.record();
        Vec3 center = data.placement().origin().add(40, 1.25, 0);
        LevelChunk chunk = c.level().getChunkAt(BlockPos.containing(center));
        try {
            c.expect("center chunk holds complete record", chunk.getData(MxtAttachments.RIFT_CHUNK).get(r.id()), r);
            ChunkPos originChunk = ChunkPos.containing(BlockPos.containing(data.placement().origin()));
            c.check("offset outline uses a different chunk from origin", !originChunk.equals(chunk.getPos()));
            c.expect("directory uses actual outline center", RiftService.world(c.level()).location(r.id()).chunk(), chunk.getPos().pack());
            RiftTemplate rebased = RiftEdits.placement(RiftEdits.shape(data, RiftEdits.transform(data.shape(), Set.of(), new RiftVertex(0, 0),
                    -48, 0, 0, 1, 1, 0)), new RiftPlacement(data.placement().origin().add(48, 0, 0), data.placement().normal(), data.placement().tangent()));
            RiftGeometry.Mesh rebasedMesh = RiftGeometry.mesh(rebased.shape(), rebased.placement(), RiftGeometry.triangulate(rebased.shape()).result().orElseThrow());
            c.expect("rebasing preserves storage chunk", RiftLocation.of(r.edit(rebased, r.expiresAt(), true), rebasedMesh).chunk(), chunk.getPos().pack());
            RiftTemplate negative = RiftEdits.placement(data, new RiftPlacement(new Vec3(-40.25, center.y, -.25),
                    data.placement().normal(), data.placement().tangent()));
            RiftGeometry.Mesh negativeMesh = RiftGeometry.mesh(negative.shape(), negative.placement(), RiftGeometry.triangulate(negative.shape()).result().orElseThrow());
            c.expect("negative center rounds down", RiftLocation.of(r.edit(negative, r.expiresAt(), true), negativeMesh).chunk(), ChunkPos.pack(-1, -1));
            c.check("chunk marked for saving", chunk.isUnsaved());
            CompoundTag nbt = SerializableChunkData.copyOf(c.level(), chunk).write();
            CompoundTag attachments = nbt.getCompoundOrEmpty(AttachmentHolder.ATTACHMENTS_NBT_KEY);
            RiftChunkAttachment saved = RiftChunkAttachment.CODEC.codec().parse(NbtOps.INSTANCE,
                    attachments.getCompoundOrEmpty("mxt:rift_chunk")).result().orElseThrow();
            c.expect("Minecraft chunk serializer retains every field", saved.get(r.id()), r);
            RiftWorldAttachment world = RiftService.world(c.level());
            JsonElement json = RiftWorldAttachment.CODEC.codec().encodeStart(JsonOps.INSTANCE, world).result().orElseThrow();
            c.check("dimension does not serialize full records", !json.getAsJsonObject().has("portals")
                    && !json.toString().contains("destination") && !json.toString().contains("appearance"));
            RiftWorldAttachment restored = RiftWorldAttachment.CODEC.codec().parse(JsonOps.INSTANCE, json).result().orElseThrow();
            c.expect("directory survives restart", restored.location(r.id()), world.location(r.id()));
            c.check("full record cache starts empty", restored.get(r.id()) == null && restored.runtime().mesh(r.id()) == null);
            c.check("dormant sections remain indexed", restored.runtime().query(world.location(r.id()).bounds()).contains(r.id()));
            c.check("dormant records count toward density", !restored.runtime().fits(null, world.runtime().mesh(r.id()), 1));
            restored.release();
        } finally {
            RiftService.remove(c.level(), r.id());
        }
    }

    private static void chunkMove(ProbeContext c) {
        RiftResult result = RiftService.publish(c.level(), null, 0, template(c, 0), null, true);
        if (!c.check("portal published", result.isSuccess())) return;
        RiftRecord old = result.record();
        RiftPlacement origin = old.data().placement();
        LevelChunk previous = c.level().getChunkAt(BlockPos.containing(origin.origin()));
        RiftTemplate shifted = RiftEdits.placement(old.data(), new RiftPlacement(origin.origin().add(48, 0, 0), origin.normal(), origin.tangent()));
        try {
            RiftResult moved = RiftService.publish(c.level(), old.id(), old.revision(), shifted, null, false);
            if (!c.check("cross-chunk move accepted", moved.isSuccess())) return;
            LevelChunk target = c.level().getChunkAt(BlockPos.containing(shifted.placement().origin()));
            c.expect("old owner removes record", previous.getData(MxtAttachments.RIFT_CHUNK).get(old.id()), null);
            c.expect("new owner holds new revision", target.getData(MxtAttachments.RIFT_CHUNK).get(old.id()), moved.record());
            c.expect("directory points to new owner", RiftService.world(c.level()).location(old.id()).chunk(), target.getPos().pack());
            c.check("both chunks marked for saving", previous.isUnsaved() && target.isUnsaved());
            UUID instance = UUID.randomUUID();
            RiftService.bind(c.level(), moved.record(), c.level().dimension().identifier(), 4, instance);
            RiftRecord bound = target.getData(MxtAttachments.RIFT_CHUNK).get(old.id());
            c.expect("binding written to chunk", bound.boundInstance(), Optional.of(instance));
            c.expect("binding updates indexed revision", RiftService.world(c.level()).location(old.id()).revision(), bound.revision());
            RiftTemplate reshaped = RiftEdits.shape(bound.data(), RiftEdits.transform(bound.data().shape(), Set.of(), new RiftVertex(0, 0), 32, 0, 0, 1, 1, 0));
            RiftResult reshapedResult = RiftService.publish(c.level(), bound.id(), bound.revision(), reshaped, null, false);
            if (!c.check("outline-only move accepted", reshapedResult.isSuccess())) return;
            RiftRecord finalRecord = reshapedResult.record();
            LevelChunk finalChunk = c.level().getChunkAt(BlockPos.containing(shifted.placement().origin().add(32, 1.25, 0)));
            c.expect("outline-only move keeps placement origin", finalRecord.data().placement().origin(), bound.data().placement().origin());
            c.expect("outline-only move clears previous center chunk", target.getData(MxtAttachments.RIFT_CHUNK).get(old.id()), null);
            c.expect("outline-only move writes new center chunk", finalChunk.getData(MxtAttachments.RIFT_CHUNK).get(old.id()), finalRecord);
            c.expect("outline-only move preserves binding", finalRecord.boundInstance(), Optional.of(instance));
            previous.getData(MxtAttachments.RIFT_CHUNK).put(old);
            RiftStorage.load(new ChunkEvent.Load(previous, false));
            RiftStorage.refresh(c.level());
            c.expect("old saved copy cannot undo a move", previous.getData(MxtAttachments.RIFT_CHUNK).get(old.id()), null);
            c.expect("new record survives old chunk reload", RiftService.get(c.level(), old.id()), finalRecord);
        } finally {
            RiftService.remove(c.level(), old.id());
        }
    }

    private static void chunkLifecycle(ProbeContext c) {
        RiftTemplate base = template(c, 0);
        ChunkPos boundary = ChunkPos.containing(BlockPos.containing(base.placement().origin()));
        RiftPlacement placement = new RiftPlacement(new Vec3(boundary.getMinBlockX() + 16, base.placement().origin().y,
                boundary.getMinBlockZ() + 8), new Vec3(0, 0, 1), new Vec3(1, 0, 0));
        RiftResult result = RiftService.publish(c.level(), null, 0, RiftEdits.placement(base, placement), null, true);
        if (!c.check("cross-chunk portal published", result.isSuccess())) return;
        UUID id = result.record().id();
        LevelChunk left = c.level().getChunk(boundary.x(), boundary.z());
        LevelChunk owner = c.level().getChunk(boundary.x() + 1, boundary.z());
        RiftWorldAttachment world = RiftService.world(c.level());
        try {
            RiftStorage.load(new ChunkEvent.Load(left, false));
            RiftStorage.load(new ChunkEvent.Load(owner, false));
            RiftStorage.refresh(c.level());
            RiftStorage.unload(new ChunkEvent.Unload(owner));
            RiftStorage.refresh(c.level());
            c.expect("edge keeps whole portal active", world.get(id), result.record());
            c.check("edge retains exact geometry", world.runtime().mesh(id) != null);
            world.evict(id);
            RiftStorage.load(new ChunkEvent.Load(left, false));
            c.expect("load callback defers cross-chunk reads", world.get(id), null);
            RiftStorage.refresh(c.level());
            c.expect("edge reload resolves complete owner record", world.get(id), result.record());
            RiftStorage.unload(new ChunkEvent.Unload(left));
            RiftStorage.refresh(c.level());
            c.expect("last covered chunk releases full record", world.get(id), null);
            c.expect("last covered chunk releases mesh", world.runtime().mesh(id), null);
            c.check("unload preserves directory", world.location(id) != null);
            c.expect("unload preserves persisted record", owner.getData(MxtAttachments.RIFT_CHUNK).get(id), result.record());
            RiftStorage.load(new ChunkEvent.Load(left, false));
            RiftStorage.refresh(c.level());
            c.expect("cold edge restores portal", world.get(id), result.record());
        } finally {
            RiftStorage.load(new ChunkEvent.Load(left, false));
            RiftStorage.load(new ChunkEvent.Load(owner, false));
            RiftService.remove(c.level(), id);
            RiftStorage.refresh(c.level());
        }
    }

    private static void chunkStale(ProbeContext c) {
        RiftResult result = RiftService.publish(c.level(), null, 0, template(c, 0), null, true);
        if (!c.check("portal published", result.isSuccess())) return;
        RiftRecord r = result.record();
        LevelChunk chunk = c.level().getChunkAt(BlockPos.containing(r.data().placement().origin()));
        RiftService.remove(c.level(), r.id());
        chunk.getData(MxtAttachments.RIFT_CHUNK).put(r);
        RiftStorage.load(new ChunkEvent.Load(chunk, false));
        RiftStorage.refresh(c.level());
        c.expect("deleted record pruned on reload", chunk.getData(MxtAttachments.RIFT_CHUNK).get(r.id()), null);
        c.expect("deleted portal stays absent", RiftService.get(c.level(), r.id()), null);
        c.check("deleted sections stay absent", !RiftService.world(c.level()).runtime().query(
                AABB.ofSize(r.data().placement().origin(), 8, 8, 8)).contains(r.id()));
        c.check("pruned chunk marked for saving", chunk.isUnsaved());
    }

    private static void chunkExpiry(ProbeContext c) {
        RiftTemplate base = template(c, 0);
        Vec3 origin = new Vec3(1_200_008, base.placement().origin().y, 1_200_008);
        RiftTemplate data = RiftEdits.placement(base, new RiftPlacement(origin, new Vec3(0, 0, 1), new Vec3(1, 0, 0)));
        RiftRecord r = new RiftRecord(UUID.randomUUID(), 1, data, RiftService.now(c.level()) + 4,
                Optional.empty(), Optional.empty(), -1, Optional.empty());
        RiftGeometry.Mesh mesh = RiftGeometry.mesh(data.shape(), data.placement(), RiftGeometry.triangulate(data.shape()).result().orElseThrow());
        RiftWorldAttachment world = RiftService.world(c.level());
        world.put(r, mesh);
        world.evict(r.id());
        RiftService.schedule(c.level(), r);
        ChunkPos pos = ChunkPos.containing(BlockPos.containing(origin));
        c.expect("owner begins unloaded", c.level().getChunkSource().getChunkNow(pos.x(), pos.z()), null);
        c.after(8, () -> {
            c.expect("dormant expiry removes directory", world.location(r.id()), null);
            c.expect("expiry never loads owner", c.level().getChunkSource().getChunkNow(pos.x(), pos.z()), null);
            c.expect("expiry clears geometry", world.runtime().mesh(r.id()), null);
            c.done();
        });
    }

    private static void chunkMigration(ProbeContext c) {
        RiftWorldAttachment previous = RiftService.world(c.level());
        RiftTemplate base = template(c, 0);
        RiftTemplate data = RiftEdits.shape(base, RiftEdits.transform(base.shape(), Set.of(), new RiftVertex(0, 0), 48, 0, 0, 1, 1, 0));
        RiftRecord r = new RiftRecord(UUID.randomUUID(), 9, data, -1,
                Optional.of(c.player().getUUID()), Optional.of(c.level().dimension().identifier()), 5, Optional.of(UUID.randomUUID()));
        JsonObject legacy = new JsonObject();
        JsonArray portals = new JsonArray();
        portals.add(RiftRecord.CODEC.encodeStart(JsonOps.INSTANCE, r).result().orElseThrow());
        legacy.add("portals", portals);
        RiftWorldAttachment loaded = RiftWorldAttachment.CODEC.codec().parse(JsonOps.INSTANCE, legacy).result().orElseThrow();
        c.level().setData(MxtAttachments.RIFT_WORLD, loaded);
        try {
            c.expect("legacy portal transferred intact", RiftService.get(c.level(), r.id()), r);
            LevelChunk chunk = c.level().getChunkAt(BlockPos.containing(data.placement().origin().add(48, 1.25, 0)));
            c.expect("legacy record now belongs to center chunk", chunk.getData(MxtAttachments.RIFT_CHUNK).get(r.id()), r);
            c.check("legacy payload cleared after transfer", loaded.legacy().isEmpty());
            c.check("legacy identity indexed", loaded.location(r.id()) != null);
            RiftService.world(c.level());
            c.expect("migration is idempotent", loaded.locations().size(), 1);

            LevelChunk originChunk = c.level().getChunkAt(BlockPos.containing(data.placement().origin()));
            originChunk.getData(MxtAttachments.RIFT_CHUNK).put(r);
            chunk.getData(MxtAttachments.RIFT_CHUNK).remove(r.id());
            originChunk.markUnsaved();
            chunk.markUnsaved();
            JsonObject oldDirectory = RiftWorldAttachment.CODEC.codec().encodeStart(JsonOps.INSTANCE, loaded).result().orElseThrow().getAsJsonObject();
            oldDirectory.getAsJsonArray("locations").get(0).getAsJsonObject().addProperty("chunk", originChunk.getPos().pack());
            RiftWorldAttachment oldIndex = RiftWorldAttachment.CODEC.codec().parse(JsonOps.INSTANCE, oldDirectory).result().orElseThrow();
            c.level().setData(MxtAttachments.RIFT_WORLD, oldIndex);
            try {
                c.expect("origin-based save transfers without changing record", RiftService.get(c.level(), r.id()), r);
                c.expect("origin-based save updates directory", oldIndex.location(r.id()).chunk(), chunk.getPos().pack());
                c.expect("origin-based save clears old owner", originChunk.getData(MxtAttachments.RIFT_CHUNK).get(r.id()), null);
                c.expect("origin-based save persists center owner", chunk.getData(MxtAttachments.RIFT_CHUNK).get(r.id()), r);
                oldIndex.evict(r.id());
                c.expect("cold reread follows transferred directory", RiftService.get(c.level(), r.id()), r);
            } finally {
                RiftService.remove(c.level(), r.id());
                oldIndex.release();
                c.level().setData(MxtAttachments.RIFT_WORLD, loaded);
                RiftService.world(c.level());
            }
        } finally {
            RiftService.remove(c.level(), r.id());
            loaded.release();
            c.level().setData(MxtAttachments.RIFT_WORLD, previous);
            RiftService.world(c.level());
        }
    }

    private static void layout(ProbeContext c) {
        RiftTemplate d = template(c, 0);
        d = RiftEdits.placement(d, new RiftPlacement(d.placement().origin(), new Vec3(0, 0, 2), new Vec3(3, 0, 0)));
        String identity = "probe-" + UUID.randomUUID();
        int before = RiftService.world(c.level()).records().size();
        c.check("layout installed", RiftService.layout(c.level(), identity, Vec3.ZERO, List.of(d)));
        c.check("layout idempotent", RiftService.layout(c.level(), identity, Vec3.ZERO, List.of(d)));
        c.expect("one added", RiftService.world(c.level()).records().size(), before + 1);
        UUID id = UUID.nameUUIDFromBytes((identity + "/" + d.key()).getBytes(StandardCharsets.UTF_8));
        close(c, "layout normal normalized", RiftService.get(c.level(), id).data().placement().normal().length(), 1);
        close(c, "layout tangent normalized", RiftService.get(c.level(), id).data().placement().tangent().length(), 1);
        RiftService.remove(c.level(), id);
        c.check("duplicate keys reject", !RiftService.layout(c.level(), identity, Vec3.ZERO, List.of(d, d)));
        c.expect("batch untouched", RiftService.world(c.level()).records().size(), before);
    }

    private static void landing(ProbeContext c) {
        ServerLevel level = c.level();
        Vec3 pos = Vec3.atLowerCornerOf(c.at(15, 1, 15)).add(.5, 0, .5);
        BlockPos feet = BlockPos.containing(pos);
        level.setBlockAndUpdate(feet.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(feet.above(), Blocks.AIR.defaultBlockState());
        c.check("safe supported", RiftLanding.safe(level, c.player(), pos));
        level.setBlockAndUpdate(feet.above(), Blocks.STONE.defaultBlockState());
        c.check("head collision", !RiftLanding.safe(level, c.player(), pos));
        level.setBlockAndUpdate(feet.above(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(feet.below(), Blocks.AIR.defaultBlockState());
        c.check("no support", !RiftLanding.safe(level, c.player(), pos));
        level.setBlockAndUpdate(feet, Blocks.LAVA.defaultBlockState());
        c.check("hazard", !RiftLanding.safe(level, c.player(), pos));
        level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
    }

    private static void coordinates(ProbeContext c) {
        ServerLevel level = c.level();
        Vec3 target = Vec3.atLowerCornerOf(c.at(20, 1, 20)).add(.5, 0, .5);
        level.setBlockAndUpdate(BlockPos.containing(target).below(), Blocks.STONE.defaultBlockState());
        RiftTemplate base = template(c, 0);
        RiftTemplate data = new RiftTemplate(base.key(), base.placement(), false, base.shape(), new RiftDestination.Position(Optional.of(level.dimension().identifier()), target, Optional.of(45F), Optional.of(10F), false), base.appearance(), 0, true);
        RiftResult result = RiftService.publish(level, null, 0, data, null, true);
        if (!c.check("created", result.isSuccess())) return;
        RiftRecord r = result.record();
        c.player().setPos(data.placement().world(new RiftVertex(0, .1)));
        RiftTravelService.tryEnter(level, r, c.player());
        c.after(40, () -> {
            close(c, "exact x", c.player().getX(), target.x);
            close(c, "yaw", c.player().getYRot(), 45);
            RiftService.remove(level, r.id());
            c.done();
        });
    }

    private static void identity(ProbeContext c) {
        Holder<SecretRealm> definition = c.require(MxtResourceKeys.SECRET_REALM, "trial_realm");
        SecretRealmRecord first = SecretRealmService.plan(definition, 0, 100, 0);
        SecretRealmRecord second = SecretRealmService.plan(definition, 0, 100, 0);
        c.check("reused index has new identity", !first.instanceId().equals(second.instanceId()));
        c.expect("identity survives copies", first.idle().restarted(200).withAnchor(Vec3.ZERO).asPrepared().instanceId(), first.instanceId());
    }

    private static void summon(ProbeContext c) {
        ItemStack stack = new ItemStack(MxtItems.SECRET_REALM_TOKEN.get());
        stack.set(MxtDataComponents.SECRET_REALM_TOKEN, new SecretRealmTokenComponent(Optional.of(c.require(MxtResourceKeys.SECRET_REALM, "portal_realm"))));
        c.player().setItemInHand(InteractionHand.MAIN_HAND, stack);
        c.look(0, 0);
        Vec3 origin = c.player().position();
        stack.getItem().use(c.level(), c.player(), InteractionHand.MAIN_HAND);
        SecretRealmTokenComponent token = stack.get(MxtDataComponents.SECRET_REALM_TOKEN);
        if (!c.check("token creates a portal", token != null && token.summoned().isPresent())) return;
        UUID id = token.summoned().orElseThrow();
        RiftRecord portal = RiftService.get(c.level(), id);
        c.expect("summon does not teleport", c.player().position(), origin);
        c.expect("summon keeps item", stack.getCount(), 1);
        c.expect("permanent by default", portal.expiresAt(), -1L);
        c.player().getCooldowns().removeCooldown(c.player().getCooldowns().getCooldownGroup(stack));
        stack.getItem().use(c.level(), c.player(), InteractionHand.MAIN_HAND);
        c.expect("repeat reuses portal", stack.get(MxtDataComponents.SECRET_REALM_TOKEN).summoned(), Optional.of(id));
        RiftService.remove(c.level(), id);
    }

    private static void binding(ProbeContext c) {
        Holder<SecretRealm> definition = c.require(MxtResourceKeys.SECRET_REALM, "portal_realm");
        SecretRealmRecord planned = SecretRealmService.plan(definition, 0, 1, RiftService.now(c.level()));
        SecretRealmRecord instance = SecretRealmService.open(c.server(), planned).orElseThrow();
        ServerLevel destination = c.server().getLevel(instance.dimension());
        destination.setBlockAndUpdate(new BlockPos(0, 64, 0), Blocks.STONE.defaultBlockState());
        RiftTemplate base = template(c, 0);
        RiftTemplate data = new RiftTemplate(base.key(), base.placement(), false, base.shape(),
                new RiftDestination.Realm(definition.unwrapKey().orElseThrow().identifier()), base.appearance(), 0, true);
        RiftResult result = RiftService.publish(c.level(), null, 0, data, null, true);
        if (!c.check("entrance created", result.isSuccess())) {
            SecretRealmService.destroy(c.server(), instance);
            return;
        }
        UUID portalId = result.record().id();
        try {
            c.player().setPos(data.placement().origin().add(0, 0, 0.1));
            RiftTravelService.tryEnter(c.level(), result.record(), c.player());
            RiftRecord bound = RiftService.get(c.level(), portalId);
            if (!c.check("first entry binds instance", bound.boundInstance().equals(Optional.of(instance.instanceId()))))
                return;
            ServerPlayer guest = c.join(new Vec3(10.5, 0, 12.5));
            guest.setPos(data.placement().origin().add(0, 0, 0.1));
            RiftTravelService.tryEnter(c.level(), bound, guest);
            c.expect("full bound instance keeps guest outside", guest.level(), c.level());
            c.expect("full bound instance does not branch", SecretRealmRegistry.of(definition).size(), 1);
            c.expect("membership recorded", SecretRealmRegistry.ofMember(c.player().getUUID()).map(SecretRealmRecord::instanceId), Optional.of(instance.instanceId()));
        } finally {
            RiftService.remove(c.level(), portalId);
            SecretRealmRegistry.at(instance.dimension()).ifPresent(r -> SecretRealmService.destroy(c.server(), r));
        }
    }

    private static void preparation(ProbeContext c) {
        Holder<SecretRealm> definition = c.require(MxtResourceKeys.SECRET_REALM, "portal_realm");
        SecretRealmRecord instance = SecretRealmService.open(c.server(), SecretRealmService.plan(definition, 0, 2, RiftService.now(c.level()))).orElseThrow();
        try {
            ServerLevel level = c.server().getLevel(instance.dimension());
            List<RiftRecord> records = RiftService.world(level).records();
            c.expect("layout publishes once", records.size(), 1);
            c.check("prepared flag", instance.prepared());
            RiftRecord portal = records.getFirst();
            c.expect("entry-relative placement", portal.data().placement().origin(), instance.anchor().orElseThrow().add(3, 0, 0));
            RiftService.remove(level, portal.id());
            SecretRealmService.open(c.server(), instance);
            c.check("reopening does not recreate removed portal", RiftService.world(level).records().isEmpty());
        } finally {
            SecretRealmRegistry.at(instance.dimension()).ifPresent(r -> SecretRealmService.destroy(c.server(), r));
        }
    }

    private static void permissions(ProbeContext c) {
        ServerOpList ops = c.server().getPlayerList().getOps();
        NameAndId identity = new NameAndId(c.player().getGameProfile());
        ServerOpListEntry previous = ops.get(identity);
        try {
            ops.add(new ServerOpListEntry(identity, LevelBasedPermissionSet.MODERATOR, false));
            c.check("level 1 denied", !RiftEditorService.mayEdit(c.player()));
            ops.add(new ServerOpListEntry(identity, LevelBasedPermissionSet.GAMEMASTER, false));
            c.check("level 2 accepted", RiftEditorService.mayEdit(c.player()));
            ops.remove(identity);
            c.check("revoked permission denied", !RiftEditorService.mayEdit(c.player()));
        } finally {
            if (previous == null) ops.remove(identity);
            else ops.add(previous);
        }
    }

    private static void waitCancel(ProbeContext c) {
        RiftTemplate base = template(c, 0);
        RiftDestination.Position far = new RiftDestination.Position(Optional.of(c.level().dimension().identifier()), new Vec3(12_345_678.5, 100, 12_345_678.5), Optional.empty(), Optional.empty(), false);
        RiftResult result = RiftService.publish(c.level(), null, 0, new RiftTemplate(base.key(), base.placement(), false, base.shape(), far, base.appearance(), 0, true), null, true);
        if (!c.check("waiting portal created", result.isSuccess())) return;
        c.player().setPos(base.placement().origin());
        try {
            RiftTravelService.tryEnter(c.level(), result.record(), c.player());
            c.check("unloaded destination waits", RiftTravelService.waiting(c.player().getUUID()));
            RiftService.remove(c.level(), result.record().id());
            c.check("removal cancels waiting", !RiftTravelService.waiting(c.player().getUUID()));
        } finally {
            RiftService.remove(c.level(), result.record().id());
        }
    }

    private static long tickets(ProbeContext c, ChunkPos pos) {
        return c.level().getDataStorage().computeIfAbsent(TicketStorage.TYPE).getTickets(pos.pack()).stream()
                .filter(ticket -> ticket.getType() == MxtTicketTypes.RIFT.get()).count();
    }

    private static void ticketLifecycle(ProbeContext c) {
        ChunkPos pos = new ChunkPos(710_001, 710_001);
        try (RiftChunkTicket first = RiftChunkTicket.acquire(c.level(), pos); RiftChunkTicket second = RiftChunkTicket.acquire(c.level(), pos)) {
            c.expect("two handles share one Minecraft ticket", tickets(c, pos), 1L);
            first.close();
            first.close();
            c.expect("duplicate close cannot release another handle", tickets(c, pos), 1L);
            second.close();
            c.expect("last close releases ticket", tickets(c, pos), 0L);
        }

        IllegalStateException failure = new IllegalStateException("probe body failure");
        try (RiftChunkTicket _ = RiftChunkTicket.acquire(c.level(), pos)) {
            throw failure;
        } catch (IllegalStateException expected) {
            c.check("resource close preserves body exception", expected == failure);
        }
        c.expect("try-with-resources releases on exception", tickets(c, pos), 0L);

        try (RiftChunkTicket stale = RiftChunkTicket.acquire(c.level(), pos)) {
            RiftChunkTicket.unload(c.level());
            c.expect("world unload releases live handles", tickets(c, pos), 0L);
            try (RiftChunkTicket _ = RiftChunkTicket.acquire(c.level(), pos)) {
                stale.close();
                c.expect("old handle cannot release a new generation", tickets(c, pos), 1L);
            }
        }
        c.expect("replacement generation releases normally", tickets(c, pos), 0L);

        try (RiftChunkTicket shutdown = RiftChunkTicket.acquire(c.level(), pos)) {
            RiftChunkTicket.clear();
            RiftChunkTicket.clear();
            c.expect("shutdown releases once", tickets(c, pos), 0L);
        }
    }

    private static void ticketExpiry(ProbeContext c) {
        TicketType type = MxtTicketTypes.RIFT.get();
        TicketStorage storage = new TicketStorage();
        ChunkPos pos = new ChunkPos(710_002, 710_002);
        storage.addTicketWithRadius(type, pos, 2);
        for (int tick = 0; tick < 250; tick++) storage.purgeStaleTickets(null);
        c.expect("ticket survives valid wait", storage.getTickets(pos.pack()).size(), 1);
        storage.addTicketWithRadius(type, pos, 2);
        for (int tick = 0; tick < 300; tick++) storage.purgeStaleTickets(null);
        c.expect("later acquisition refreshes full timeout", storage.getTickets(pos.pack()).size(), 1);
        storage.purgeStaleTickets(null);
        c.expect("orphan expires without a loaded chunk holder", storage.getTickets(pos.pack()).size(), 0);

        ChunkPos actual = new ChunkPos(710_003, 710_003);
        try (RiftChunkTicket abandoned = RiftChunkTicket.acquire(c.level(), actual)) {
            TicketStorage active = c.level().getDataStorage().computeIfAbsent(TicketStorage.TYPE);
            Ticket ticket = active.getTickets(actual.pack()).stream().filter(t -> t.getType() == type).findFirst().orElseThrow();
            for (int tick = 0; tick <= 300; tick++) ticket.decreaseTicksLeft();
            active.purgeStaleTickets(c.level().getChunkSource().chunkMap);
            c.expect("missed close cannot retain real world ticket", tickets(c, actual), 0L);
        }
    }

    private static void ticketCleanup(ProbeContext c) {
        RiftTemplate base = template(c, 0);
        RiftDestination.Position far = new RiftDestination.Position(Optional.of(c.level().dimension().identifier()), new Vec3(9_876_550.5, 100, 9_876_550.5), Optional.empty(), Optional.empty(), false);
        RiftResult result = RiftService.publish(c.level(), null, 0, new RiftTemplate(base.key(), base.placement(), false, base.shape(), far, base.appearance(), 0, true), null, true);
        if (!c.check("cleanup portal created", result.isSuccess())) return;
        AtomicBoolean throwOnCheck = new AtomicBoolean();
        IllegalStateException expected = new IllegalStateException("probe eligibility failure");
        Vec3 origin = base.placement().origin();
        var item = new ItemEntity(c.level(), origin.x, origin.y, origin.z, new ItemStack(Blocks.STONE)) {
            @Override
            public boolean canTeleport(@NonNull Level from, @NonNull Level to) {
                if (throwOnCheck.get()) throw expected;
                return super.canTeleport(from, to);
            }
        };
        c.level().addFreshEntity(item);
        ChunkPos pos = ChunkPos.containing(BlockPos.containing(far.pos()));
        try {
            RiftTravelService.tryEnter(c.level(), result.record(), item);
            c.check("unloaded target starts wait", RiftTravelService.waiting(item.getUUID()));
            c.expect("wait acquires one ticket", tickets(c, pos), 1L);
            RiftTravelService.tick(c.level());
            c.check("scheduled chunk is not mistaken for ready chunk", RiftTravelService.waiting(item.getUUID()));

            throwOnCheck.set(true);
            try {
                RiftTravelService.tick(c.level());
                c.fail("eligibility exception must propagate");
            } catch (IllegalStateException failure) {
                c.check("original exception survives cleanup", failure == expected);
            }
            throwOnCheck.set(false);
            c.check("exception removes wait", !RiftTravelService.waiting(item.getUUID()));
            c.expect("exception releases ticket", tickets(c, pos), 0L);

            RiftTravelService.tryEnter(c.level(), result.record(), item);
            item.setPos(origin.add(0, 0, 5));
            RiftTravelService.tick(c.level());
            c.check("leaving portal removes wait", !RiftTravelService.waiting(item.getUUID()));
            c.expect("leaving portal releases ticket", tickets(c, pos), 0L);

            item.setPos(origin);
            RiftTravelService.tryEnter(c.level(), result.record(), item);
            RiftTravelService.unload(c.level());
            c.check("unload removes wait", !RiftTravelService.waiting(item.getUUID()));
            c.expect("unload releases ticket", tickets(c, pos), 0L);

            RiftTravelService.tryEnter(c.level(), result.record(), item);
            RiftTravelService.clear();
            RiftTravelService.clear();
            c.check("shutdown removes wait", !RiftTravelService.waiting(item.getUUID()));
            c.expect("shutdown releases ticket", tickets(c, pos), 0L);
        } finally {
            throwOnCheck.set(false);
            RiftService.remove(c.level(), result.record().id());
            item.discard();
        }
    }

    private static void session(ProbeContext c) {
        ServerPlayer player = c.player();
        ServerOpList ops = c.server().getPlayerList().getOps();
        NameAndId identity = new NameAndId(player.getGameProfile());
        ServerOpListEntry previousOp = ops.get(identity);
        ServerGamePacketListenerImpl previousConnection = player.connection;
        List<RiftEditorS2CPayload> sent = new ArrayList<>();
        player.connection = new ServerGamePacketListenerImpl(c.server(), previousConnection.getConnection(), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override
            public void send(@NonNull Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket(
                        CustomPacketPayload payload1
                )
                        && payload1 instanceof RiftEditorS2CPayload payload) sent.add(payload);
                else previousConnection.send(packet);
            }
        };
        player.connection.markClientLoaded();
        int before = RiftService.world(c.level()).records().size();
        try {
            ops.add(new ServerOpListEntry(identity, LevelBasedPermissionSet.GAMEMASTER, false));
            RiftEditorService.open(player, null, template(c, 0));
            if (!c.check("authorized session opens", !sent.isEmpty() && sent.getLast().action().equals("open"))) return;
            UUID token = sent.getLast().token();
            RiftEditorService.handle(player, new RiftEditC2SPayload(UUID.randomUUID(), "submit", 0, RiftExport.template(template(c, 0))));
            c.expect("unknown token cannot create", RiftService.world(c.level()).records().size(), before);
            ops.remove(identity);
            RiftEditorService.handle(player, new RiftEditC2SPayload(token, "submit", 0, RiftExport.template(template(c, 0))));
            c.expect("revoked session cannot create", RiftService.world(c.level()).records().size(), before);
            c.expect("revoked session closes", sent.getLast().action(), "close");
        } finally {
            RiftEditorService.clear();
            player.connection = previousConnection;
            if (previousOp == null) ops.remove(identity);
            else ops.add(previousOp);
        }
    }

    private static void anchorSelection(ProbeContext c) {
        ServerPlayer player = c.player();
        ServerOpList ops = c.server().getPlayerList().getOps();
        NameAndId identity = new NameAndId(player.getGameProfile());
        ServerOpListEntry previousOp = ops.get(identity);
        ServerGamePacketListenerImpl previousConnection = player.connection;
        ItemStack previousStack = player.getMainHandItem();
        List<RiftEditorS2CPayload> sent = new ArrayList<>();
        List<UUID> created = new ArrayList<>();
        BlockPos rear = c.at(10, 2, 10), front = c.at(10, 2, 7);
        BlockState rearState = c.level().getBlockState(rear);
        BlockState frontState = c.level().getBlockState(front);
        player.connection = new ServerGamePacketListenerImpl(c.server(), previousConnection.getConnection(), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override
            public void send(@NonNull Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket(
                        CustomPacketPayload payload1
                )
                        && payload1 instanceof RiftEditorS2CPayload payload) sent.add(payload);
                else previousConnection.send(packet);
            }
        };
        player.connection.markClientLoaded();
        try {
            ops.add(new ServerOpListEntry(identity, LevelBasedPermissionSet.GAMEMASTER, false));
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MxtItems.RIFT_ANCHOR.get()));
            player.setShiftKeyDown(false);
            c.playerAt(new Vec3(10.5, 1, 5.5));
            c.look(0, 0);
            c.level().setBlockAndUpdate(rear, Blocks.STONE.defaultBlockState());
            c.level().setBlockAndUpdate(front, Blocks.AIR.defaultBlockState());
            Vec3 origin = Vec3.atLowerCornerOf(c.at(10, 1, 8)).add(.5, 0, 0);
            RiftTemplate data = RiftEdits.placement(template(c, 0), new RiftPlacement(origin, new Vec3(0, 0, 1), new Vec3(1, 0, 0)));
            RiftResult published = RiftService.publish(c.level(), null, 0, data, player.getUUID(), true);
            if (!c.check("selection portal created", published.isSuccess())) return;
            RiftRecord portal = published.record();
            created.add(portal.id());
            int count = RiftService.world(c.level()).records().size();
            RiftAnchorItem anchor = MxtItems.RIFT_ANCHOR.get();
            Vec3 eye = player.getEyePosition();
            BlockHitResult rearHit = new BlockHitResult(new Vec3(eye.x, eye.y, rear.getZ()), Direction.NORTH, rear, false);

            c.expect("block right-click succeeds", anchor.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, rearHit)), InteractionResult.SUCCESS_SERVER);
            c.expect("block behind portal opens existing identity", sent.getLast().record().map(RiftRecord::id), Optional.of(portal.id()));
            c.expect("block behind portal returns current revision", sent.getLast().record().map(RiftRecord::revision), Optional.of(portal.revision()));
            sent.clear();
            anchor.use(c.level(), player, InteractionHand.MAIN_HAND);
            c.expect("air use shares selection", sent.getLast().record().map(RiftRecord::id), Optional.of(portal.id()));

            c.level().setBlockAndUpdate(front, Blocks.STONE.defaultBlockState());
            BlockHitResult frontHit = new BlockHitResult(new Vec3(eye.x, eye.y, front.getZ()), Direction.NORTH, front, false);
            sent.clear();
            anchor.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, frontHit));
            c.check("foreground block hides portal", sent.getLast().record().isEmpty());
            RiftTemplate draft = RiftTemplate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(sent.getLast().body())).result().orElseThrow();
            c.expect("unselected block creates draft at hit face", draft.placement().origin(), frontHit.getLocation());
            c.expect("unselected draft retains clicked normal", draft.placement().normal(), new Vec3(0, 0, -1));

            RiftResult flush = RiftService.publish(c.level(), portal.id(), portal.revision(),
                    RiftEdits.placement(data, new RiftPlacement(new Vec3(origin.x, origin.y, front.getZ()), data.placement().normal(), data.placement().tangent())), player.getUUID(), false);
            if (!c.check("flush portal update succeeds", flush.isSuccess())) return;
            portal = flush.record();
            sent.clear();
            anchor.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, frontHit));
            c.expect("portal flush with block face remains selectable", sent.getLast().record().map(RiftRecord::id), Optional.of(portal.id()));

            RiftResult restored = RiftService.publish(c.level(), portal.id(), portal.revision(), data, player.getUUID(), false);
            if (!c.check("portal position restored", restored.isSuccess())) return;
            portal = restored.record();
            c.level().setBlockAndUpdate(front, Blocks.AIR.defaultBlockState());
            c.playerAt(new Vec3(10.5, 1, 9.5));
            c.look(180, 0);
            sent.clear();
            anchor.use(c.level(), player, InteractionHand.MAIN_HAND);
            c.expect("back face selects same portal", sent.getLast().record().map(RiftRecord::id), Optional.of(portal.id()));

            RiftResult nearer = RiftService.publish(c.level(), null, 0, RiftEdits.placement(data,
                    new RiftPlacement(origin.add(0, 0, .75), data.placement().normal(), data.placement().tangent())), player.getUUID(), true);
            if (!c.check("nearer portal created", nearer.isSuccess())) return;
            created.add(nearer.record().id());
            sent.clear();
            anchor.use(c.level(), player, InteractionHand.MAIN_HAND);
            c.expect("overlapping sight line selects nearest portal", sent.getLast().record().map(RiftRecord::id), Optional.of(nearer.record().id()));
            RiftService.remove(c.level(), nearer.record().id());

            player.setShiftKeyDown(true);
            sent.clear();
            anchor.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, rearHit));
            c.check("sneaking records destination without opening editor", sent.isEmpty());
            c.expect("sneaking records player position", player.getMainHandItem().get(MxtDataComponents.RIFT).recorded()
                    .map(destination -> destination instanceof RiftDestination.Position position ? position.pos() : null), Optional.of(player.position()));
            player.setShiftKeyDown(false);
            sent.clear();
            anchor.use(c.level(), player, InteractionHand.MAIN_HAND);
            c.expect("selecting never creates world records", RiftService.world(c.level()).records().size(), count);
            RiftEditorS2CPayload opened = sent.getLast();
            RiftEditorService.handle(player, new RiftEditC2SPayload(opened.token(), "remove", portal.revision(), ""));
            c.expect("selected session deletes existing portal", RiftService.get(c.level(), portal.id()), null);
            c.expect("deleting selected portal closes editor", sent.getLast().action(), "close");

            ops.remove(identity);
            sent.clear();
            c.expect("unauthorized block use denied", anchor.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, rearHit)), InteractionResult.FAIL);
            c.check("unauthorized block use sends no editor", sent.isEmpty());
            c.note("anchor selection: block/air/back/occluded/flush/nearest/record/remove/permission OK");
        } finally {
            RiftEditorService.clear();
            for (UUID id : created) RiftService.remove(c.level(), id);
            c.level().setBlockAndUpdate(front, frontState);
            c.level().setBlockAndUpdate(rear, rearState);
            player.setShiftKeyDown(false);
            player.setItemSlot(EquipmentSlot.MAINHAND, previousStack);
            player.connection = previousConnection;
            if (previousOp == null) ops.remove(identity);
            else ops.add(previousOp);
        }
    }

    private static void nonliving(ProbeContext c) {
        Vec3 target = Vec3.atLowerCornerOf(c.at(20, 1, 20)).add(0.5, 0, 0.5);
        ItemEntity item = new ItemEntity(c.level(), c.at(12, 1, 12).getX(), c.at(12, 1, 12).getY(), c.at(12, 1, 12).getZ(), new ItemStack(Blocks.STONE));
        c.level().addFreshEntity(item);
        RiftTemplate base = template(c, 0);
        RiftTemplate data = new RiftTemplate(base.key(), base.placement(), false, base.shape(), new RiftDestination.Position(Optional.of(c.level().dimension().identifier()), target, Optional.empty(), Optional.empty(), false), base.appearance(), 0, true);
        RiftResult result = RiftService.publish(c.level(), null, 0, data, null, true);
        try {
            if (!c.check("item portal created", result.isSuccess())) return;
            RiftTravelService.tryEnter(c.level(), result.record(), item);
            c.expect("nonliving exact position", item.position(), target);
            c.check("returned entity has cooldown", item.isOnPortalCooldown());
        } finally {
            if (result.isSuccess()) RiftService.remove(c.level(), result.record().id());
            item.discard();
        }
    }

    private static void tracking(ProbeContext c) {
        ServerPlayer player = c.player();
        ServerGamePacketListenerImpl previous = player.connection;
        List<RiftSyncS2CPayload> sent = new ArrayList<>();
        player.connection = new ServerGamePacketListenerImpl(c.server(), previous.getConnection(), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override
            public void send(@NonNull Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket(
                        CustomPacketPayload payload1
                )
                        && payload1 instanceof RiftSyncS2CPayload payload) sent.add(payload);
                else previous.send(packet);
            }
        };
        player.connection.markClientLoaded();
        RiftTracking.clear();
        RiftTemplate base = template(c, 0);
        int x = Math.floorDiv(c.at(12, 0, 12).getX(), 16) * 16 + 16;
        RiftPlacement p = new RiftPlacement(new Vec3(x, base.placement().origin().y, base.placement().origin().z), new Vec3(0, 0, 1), new Vec3(1, 0, 0));
        RiftResult result = RiftService.publish(c.level(), null, 0, RiftEdits.placement(base, p), null, true);
        if (!result.isSuccess()) {
            player.connection = previous;
            c.fail("tracking portal publish: " + result.detail());
            return;
        }
        UUID id = result.record().id();
        int z = Math.floorDiv(c.at(12, 0, 12).getZ(), 16);
        LevelChunk left = c.level().getChunk(Math.floorDiv(x, 16) - 1, z);
        LevelChunk right = c.level().getChunk(Math.floorDiv(x, 16), z);
        try {
            RiftTracking.watch(new ChunkWatchEvent.Sent(player, left, c.level()));
            c.expect("first chunk sends one visual", sent.stream().filter(v -> v.id().equals(id) && v.visual().isPresent()).count(), 1L);
            RiftTracking.watch(new ChunkWatchEvent.Sent(player, right, c.level()));
            c.expect("second chunk deduplicates", sent.stream().filter(v -> v.id().equals(id) && v.visual().isPresent()).count(), 1L);
            sent.clear();
            RiftTracking.unwatch(new ChunkWatchEvent.UnWatch(player, left.getPos(), c.level()));
            c.check("remaining chunk retains visual", sent.stream().noneMatch(v -> v.id().equals(id) && v.visual().isEmpty()));
            RiftTracking.unwatch(new ChunkWatchEvent.UnWatch(player, right.getPos(), c.level()));
            c.expect("last chunk removes visual", sent.stream().filter(v -> v.id().equals(id) && v.visual().isEmpty()).count(), 1L);
        } finally {
            RiftTracking.clear();
            player.connection = previous;
            RiftService.remove(c.level(), id);
        }
    }
}
