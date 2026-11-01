package com.iafenvoy.mxt.probe.world;

import com.google.gson.JsonParser;
import com.iafenvoy.mxt.data.item.RiftComponent;
import com.iafenvoy.mxt.data.rift.*;
import com.iafenvoy.mxt.network.payload.RiftEditC2SPayload;
import com.iafenvoy.mxt.network.payload.RiftEditorS2CPayload;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.runtime.rift.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.*;

public final class RiftAnchorProbes {
    private RiftAnchorProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("world/rift/anchor_placement", RiftAnchorProbes::placement),
                Probe.of("world/rift/item_parameters", RiftAnchorProbes::parameters));
    }

    private static RiftTemplate template(ProbeContext c) {
        return new RiftTemplate("batch", new RiftPlacement(Vec3.atCenterOf(c.at(10, 2, 10)), new Vec3(0, 0, 1), new Vec3(1, 0, 0)), false,
                RiftEdits.generate("ellipse", 1.5, 2, 8, 0), new RiftDestination.Position(Optional.of(c.level().dimension().identifier()),
                Vec3.atCenterOf(c.at(24, 1, 24)), Optional.of(45F), Optional.of(12F), true),
                new RiftAppearance("portal", 0xFF45ABCD, -1, 2, .04, .5, .2, 1.5, 30, .2, -.3), 2400, true);
    }

    private static void placement(ProbeContext c) {
        try (EditorPlayer fixture = new EditorPlayer(c)) {
            RiftTemplate data = template(c);
            ItemStack stack = fixture.player.getMainHandItem();
            RiftComponent component = new RiftComponent(Optional.of(data), Optional.empty());
            stack.set(MxtDataComponents.RIFT, component);
            int count = fixture.records().size(), index = 0;
            for (Direction face : Direction.values()) {
                BlockPos block = c.at(4 + index++ * 4, 2, 10);
                Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
                Vec3 hit = Vec3.atCenterOf(block).add(normal.scale(.5));
                c.expect("block use places immediately " + face, MxtItems.RIFT_ANCHOR.get().useOn(new UseOnContext(fixture.player,
                        InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, block, false))), InteractionResult.SUCCESS_SERVER);
                List<RiftRecord> records = fixture.records();
                c.expect("one new portal per click", records.size(), ++count);
                RiftRecord placed = records.stream().filter(r -> r.data().placement().origin().equals(hit)).findFirst().orElseThrow();
                c.expect("clicked face sets normal", placed.data().placement().normal(), normal);
                c.expect("blueprint shape retained", placed.data().shape(), data.shape());
                c.expect("blueprint appearance retained", placed.data().appearance(), data.appearance());
                c.expect("blueprint destination retained", placed.data().destination(), data.destination());
                c.expect("blueprint duration retained", placed.data().durationTicks(), data.durationTicks());
                c.check("new portal is enabled", placed.data().enabled());
                c.check("orthogonal basis", Math.abs(placed.data().placement().normal().dot(placed.data().placement().tangent())) < 1e-7);
                c.expect("item parameters remain unchanged", stack.get(MxtDataComponents.RIFT), component);
                c.expect("placement does not consume the anchor", stack.getCount(), 1);
                c.check("placement never opens an editor", fixture.sent.isEmpty());
            }
            c.expect("placements have distinct identities", fixture.records().stream().map(RiftRecord::id).distinct().count(), (long) count);
            RiftResult outside = RiftEditorService.place(fixture.player, InteractionHand.MAIN_HAND,
                    new RiftPlacement(new Vec3(c.at(8, 1, 8).getX(), c.level().getMaxY() - 1, c.at(8, 1, 8).getZ()), new Vec3(0, 0, 1), new Vec3(1, 0, 0)));
            c.check("actual placement validates world height", outside.isFailure());
            c.expect("invalid placement creates nothing", fixture.records().size(), count);
            stack.remove(MxtDataComponents.RIFT);
            RiftResult defaults = RiftEditorService.place(fixture.player, InteractionHand.MAIN_HAND,
                    new RiftPlacement(Vec3.atCenterOf(c.at(24, 2, 20)), new Vec3(0, 0, 1), new Vec3(1, 0, 0)));
            c.check("empty anchors place a visible enabled default", defaults.isSuccess() && defaults.record().data().enabled());
            c.server().getPlayerList().getOps().remove(fixture.identity);
            c.check("placement rechecks edit permission", RiftEditorService.place(fixture.player, InteractionHand.MAIN_HAND, data.placement()).isFailure());
            c.note("anchor placement: six faces/batch/parameters/identity/enabled/bounds/permissions OK");
        }
    }

    private static void parameters(ProbeContext c) {
        EditorPlayer fixture = new EditorPlayer(c);
        try {
            ItemStack main = fixture.player.getMainHandItem();
            RiftComponent originalMain = main.getOrDefault(MxtDataComponents.RIFT, RiftComponent.EMPTY);
            ItemStack off = new ItemStack(MxtItems.RIFT_ANCHOR.get());
            fixture.player.setItemSlot(EquipmentSlot.OFFHAND, off);
            RiftTemplate data = template(c);
            RiftDestination recorded = new RiftDestination.Return();
            off.set(MxtDataComponents.RIFT, new RiftComponent(Optional.of(data), Optional.of(recorded)));
            UUID token = fixture.open(InteractionHand.OFF_HAND);
            c.expect("air opens item editor", fixture.sent.getLast().action(), "open_item");
            RiftTemplate initial = decoded(fixture.sent.getLast());
            c.expect("air reads the used hand's shape", initial.shape(), data.shape());
            c.expect("recorded destination is visible in editor", initial.destination(), recorded);
            RiftTemplate portable = RiftEdits.placement(data, new RiftPlacement(new Vec3(0, c.level().getMaxY() + 64, 0), new Vec3(0, 0, 1), new Vec3(1, 0, 0)));
            RiftEditorService.handle(fixture.player, new RiftEditC2SPayload(UUID.randomUUID(), "submit", 0, RiftExport.template(portable)));
            c.expect("unknown tokens cannot alter the item", off.get(MxtDataComponents.RIFT).recorded(), Optional.of(recorded));
            fixture.request(token, "submit", portable);
            c.expect("saving item settings is acknowledged", fixture.sent.getLast().action(), "item_accepted");
            c.expect("saved settings round trip", decoded(fixture.sent.getLast()), portable);
            c.expect("offhand receives the blueprint", off.get(MxtDataComponents.RIFT).blueprint(), Optional.of(portable));
            c.expect("saving does not modify the main hand", main.getOrDefault(MxtDataComponents.RIFT, RiftComponent.EMPTY), originalMain);
            c.check("saving clears the previous destination override", off.get(MxtDataComponents.RIFT).recorded().isEmpty());
            c.check("saving settings never creates a world portal", fixture.records().isEmpty());
            fixture.request(token, "submit", data);
            c.expect("rapid requests get an explicit response", fixture.sent.getLast().body(), "rate_limit");
            c.after(5, () -> {
                try {
                    fixture.request(token, "submit", data);
                    c.expect("same session can save again", fixture.sent.getLast().action(), "item_accepted");
                    c.expect("second save replaces the settings", off.get(MxtDataComponents.RIFT).blueprint(), Optional.of(data));
                    fixture.request(fixture.open(InteractionHand.MAIN_HAND), "submit", data);
                    c.expect("main hand can also save parameters", main.get(MxtDataComponents.RIFT).blueprint(), Optional.of(data));
                    UUID invalid = fixture.open(InteractionHand.OFF_HAND);
                    var malformed = JsonParser.parseString(RiftExport.template(data)).getAsJsonObject();
                    malformed.addProperty("duration_ticks", -1);
                    RiftEditorService.handle(fixture.player, new RiftEditC2SPayload(invalid, "submit", 0, malformed.toString()));
                    c.expect("invalid settings produce an error response", fixture.sent.getLast().action(), "error");
                    c.expect("invalid settings preserve the item", off.get(MxtDataComponents.RIFT).blueprint(), Optional.of(data));
                    for (String action : List.of("remove", "copy", "renew")) {
                        fixture.request(fixture.open(InteractionHand.OFF_HAND), action, data);
                        c.expect("item mode rejects world operation " + action, fixture.sent.getLast().body(), "item_only");
                    }
                    tokenRequestAfterReplacement(c, fixture, off, data);
                    fixture.player.setItemSlot(EquipmentSlot.OFFHAND, off);
                    UUID changed = fixture.open(InteractionHand.OFF_HAND);
                    RiftComponent external = new RiftComponent(Optional.of(data), Optional.of(recorded));
                    off.set(MxtDataComponents.RIFT, external);
                    fixture.request(changed, "submit", data);
                    c.expect("external edits are not overwritten", off.get(MxtDataComponents.RIFT), external);
                    c.expect("external edits report conflict", fixture.sent.getLast().body(), "item_changed");
                    UUID revoked = fixture.open(InteractionHand.OFF_HAND);
                    c.server().getPlayerList().getOps().remove(fixture.identity);
                    fixture.request(revoked, "submit", data);
                    c.expect("revoked permission closes item session", fixture.sent.getLast().action(), "close");
                    c.expect("revoked permission leaves settings intact", off.get(MxtDataComponents.RIFT), external);
                    c.check("item editor cannot publish through any tested action", fixture.records().isEmpty());
                    c.note("item parameters: air/main/offhand/save/repeat/roundtrip/portable/conflict/permissions OK");
                } finally {
                    fixture.close();
                }
                c.done();
            });
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static void tokenRequestAfterReplacement(ProbeContext c, EditorPlayer fixture, ItemStack off, RiftTemplate data) {
        UUID token = fixture.open(InteractionHand.OFF_HAND);
        ItemStack replacement = off.copy();
        fixture.player.setItemSlot(EquipmentSlot.OFFHAND, replacement);
        fixture.request(token, "submit", data);
        c.expect("replacing an identical item invalidates the target", fixture.sent.getLast().body(), "item_changed");
        c.expect("replacement item is untouched", replacement.get(MxtDataComponents.RIFT), off.get(MxtDataComponents.RIFT));
    }

    private static RiftTemplate decoded(RiftEditorS2CPayload payload) {
        return RiftTemplate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(payload.body())).result().orElseThrow();
    }

    private static final class EditorPlayer implements AutoCloseable {
        final ProbeContext context;
        final ServerPlayer player;
        final NameAndId identity;
        final ServerOpListEntry oldOp;
        final ServerGamePacketListenerImpl connection;
        final ItemStack main, off;
        final List<RiftEditorS2CPayload> sent = new ArrayList<>();
        final Set<UUID> existing = new HashSet<>();

        EditorPlayer(ProbeContext c) {
            this.context = c;
            this.player = c.player();
            this.identity = new NameAndId(this.player.getGameProfile());
            this.oldOp = c.server().getPlayerList().getOps().get(this.identity);
            this.connection = this.player.connection;
            this.main = this.player.getMainHandItem();
            this.off = this.player.getOffhandItem();
            RiftService.world(c.level()).locations().forEach(location -> this.existing.add(location.id()));
            this.player.connection = new ServerGamePacketListenerImpl(c.server(), this.connection.getConnection(), this.player,
                    CommonListenerCookie.createInitial(this.player.getGameProfile(), false)) {
                @Override
                public void send(@NonNull Packet<?> packet) {
                    if (packet instanceof ClientboundCustomPayloadPacket wrapped && wrapped.payload() instanceof RiftEditorS2CPayload payload)
                        EditorPlayer.this.sent.add(payload);
                    else EditorPlayer.this.connection.send(packet);
                }
            };
            this.player.connection.markClientLoaded();
            c.server().getPlayerList().getOps().add(new ServerOpListEntry(this.identity, LevelBasedPermissionSet.GAMEMASTER, false));
            this.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MxtItems.RIFT_ANCHOR.get()));
            c.playerAt(new Vec3(8.5, 1, 3.5));
            c.look(0, -80);
        }

        UUID open(InteractionHand hand) {
            MxtItems.RIFT_ANCHOR.get().use(this.context.level(), this.player, hand);
            return this.sent.getLast().token();
        }

        void request(UUID token, String action, RiftTemplate data) {
            RiftEditorService.handle(this.player, new RiftEditC2SPayload(token, action, 0, RiftExport.template(data)));
        }

        List<RiftRecord> records() {
            return RiftService.records(this.context.level()).stream().filter(record -> !this.existing.contains(record.id())).toList();
        }

        @Override
        public void close() {
            RiftEditorService.clear();
            for (RiftRecord record : this.records()) RiftService.remove(this.context.level(), record.id());
            this.player.setItemSlot(EquipmentSlot.MAINHAND, this.main);
            this.player.setItemSlot(EquipmentSlot.OFFHAND, this.off);
            this.player.connection = this.connection;
            if (this.oldOp == null) this.context.server().getPlayerList().getOps().remove(this.identity);
            else this.context.server().getPlayerList().getOps().add(this.oldOp);
        }
    }
}
