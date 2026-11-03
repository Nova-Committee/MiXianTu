package com.iafenvoy.mxt.runtime.rift;

import com.google.gson.JsonParser;
import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.item.RiftComponent;
import com.iafenvoy.mxt.data.rift.*;
import com.iafenvoy.mxt.item.RiftAnchorItem;
import com.iafenvoy.mxt.network.payload.RiftEditC2SPayload;
import com.iafenvoy.mxt.network.payload.RiftEditorS2CPayload;
import com.iafenvoy.mxt.network.payload.RiftEditorS2CPayload.EditorAction;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.resulting.failures.RiftFailure;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class RiftEditorService {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private RiftEditorService() {
    }

    public static boolean mayEdit(ServerPlayer player) {
        return ServerCommandManager.mayChange(player.createCommandSourceStack());
    }

    public static void open(ServerPlayer player, @Nullable UUID id, @Nullable RiftTemplate initial) {
        if (!mayEdit(player)) return;
        ServerLevel level = player.level();
        RiftRecord record = id == null ? null : RiftService.get(level, id);
        if (id != null && (record == null || record.data().placement().origin().distanceToSqr(player.position()) > 64 * 64))
            return;
        if (record == null && initial == null)
            initial = new RiftTemplate("portal", new RiftPlacement(player.position().add(player.getLookAngle().scale(3)),
                    new Vec3(0, 0, 1), new Vec3(1, 0, 0)), false, RiftShape.DEFAULT, new RiftDestination.Position(
                    Optional.of(level.dimension().identifier()), player.position(), Optional.empty(), Optional.empty(), false), RiftAppearance.DEFAULT, 0, false);
        Session session = new Session(UUID.randomUUID(), player, level, id, RiftService.now(level));
        SESSIONS.put(player.getUUID(), session);
        RiftService.world(level);
        send(session, EditorAction.OPEN, record, record == null ? RiftExport.template(initial) : "");
    }

    public static void openItem(ServerPlayer player, InteractionHand hand) {
        if (!mayEdit(player)) return;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof RiftAnchorItem)) return;
        RiftTemplate initial = itemTemplate(player, stack);
        Session session = new Session(UUID.randomUUID(), player, player.level(), null, RiftService.now(player.level()));
        session.item = new ItemTarget(hand, stack, stack.getOrDefault(MxtDataComponents.RIFT, RiftComponent.EMPTY));
        SESSIONS.put(player.getUUID(), session);
        send(session, EditorAction.OPEN_ITEM, null, RiftExport.template(initial));
    }

    public static Result<RiftRecord> place(ServerPlayer player, InteractionHand hand, RiftPlacement placement) {
        if (!mayEdit(player)) return Result.failure(RiftFailure.PERMISSION);
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof RiftAnchorItem)) return Result.failure(RiftFailure.HOLD_ANCHOR);
        return RiftService.publish(player.level(), null, 0, itemTemplate(player, stack, placement), player.getUUID(), true);
    }

    private static RiftTemplate itemTemplate(ServerPlayer player, ItemStack stack, RiftPlacement placement) {
        RiftComponent component = stack.getOrDefault(MxtDataComponents.RIFT, RiftComponent.EMPTY);
        RiftTemplate base = component.blueprint().orElse(null);
        RiftDestination destination = component.recorded().orElseGet(() -> base != null ? base.destination()
                : new RiftDestination.Position(Optional.of(player.level().dimension().identifier()), player.position(), Optional.empty(), Optional.empty(), false));
        return new RiftTemplate(base == null ? "portal" : base.key(), placement, false, base == null ? RiftShape.DEFAULT : base.shape(),
                destination, base == null ? RiftAppearance.DEFAULT : base.appearance(), base == null ? 0 : base.durationTicks(), base == null || base.enabled());
    }

    private static RiftTemplate itemTemplate(ServerPlayer player, ItemStack stack) {
        return itemTemplate(player, stack, new RiftPlacement(player.position().add(player.getLookAngle().scale(3)),
                new Vec3(0, 0, 1), new Vec3(1, 0, 0)));
    }

    public static void handle(RiftEditC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        handle(player, payload);
    }

    public static void handle(ServerPlayer player, RiftEditC2SPayload payload) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.token.equals(payload.token())) return;
        if (!valid(session)) {
            close(session);
            return;
        }
        long now = RiftService.now(session.level);
        session.lastSeen = now;
        if (payload.action().equals("heartbeat")) return;
        if (payload.action().equals("close")) {
            close(session);
            return;
        }
        if (session.item != null && !session.item.matches(player)) {
            send(session, EditorAction.ERROR, null, "item_changed");
            return;
        }
        if (now - session.lastAction < 4) {
            send(session, EditorAction.ERROR, null, "rate_limit");
            return;
        }
        session.lastAction = now;
        RiftRecord record = session.id == null ? null : RiftService.get(session.level, session.id);
        if (session.id != null && (record == null || record.data().placement().origin().distanceToSqr(player.position()) > 64 * 64)) {
            send(session, EditorAction.ERROR, null, "missing");
            return;
        }
        switch (payload.action()) {
            case "reload" -> {
                if (session.item != null)
                    send(session, EditorAction.ITEM_ACCEPTED, null, RiftExport.template(itemTemplate(player, session.item.stack())));
                else if (record != null) send(session, EditorAction.ACCEPTED, record, "");
                else send(session, EditorAction.ERROR, null, "missing");
            }
            case "copy" -> {
                if (session.item != null) {
                    send(session, EditorAction.ERROR, null, "item_only");
                    return;
                }
                try {
                    RiftTemplate data = RiftTemplate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(payload.body())).result().orElse(null);
                    if (data == null || RiftService.validate(session.level, data) instanceof Result.FailureResult<Void>
                            || data.placement().origin().distanceToSqr(player.position()) > 64 * 64) {
                        send(session, EditorAction.ERROR, null, "invalid_json");
                        return;
                    }
                    open(player, null, data);
                } catch (RuntimeException malformed) {
                    send(session, EditorAction.ERROR, null, "invalid_json");
                }
            }
            case "remove" -> {
                if (session.item != null) {
                    send(session, EditorAction.ERROR, null, "item_only");
                    return;
                }
                if (record == null || record.revision() != payload.revision()) {
                    send(session, EditorAction.ERROR, null, "revision_conflict");
                    return;
                }
                RiftService.remove(session.level, record.id());
                close(session);
            }
            case "submit", "renew", "blueprint", "export_preset", "export_layout", "export_relative", "export_local",
                 "export_world" -> {
                RiftTemplate data;
                try {
                    DataResult<RiftTemplate> parsed = RiftTemplate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(payload.body()));
                    if (parsed.error().isPresent()) {
                        send(session, EditorAction.ERROR, null, parsed.error().get().message());
                        return;
                    }
                    data = parsed.result().orElseThrow();
                } catch (RuntimeException malformed) {
                    send(session, EditorAction.ERROR, null, "invalid_json");
                    return;
                }
                if (session.item == null && data.placement().origin().distanceToSqr(player.position()) > 64 * 64) {
                    send(session, EditorAction.ERROR, null, "distance");
                    return;
                }
                Result<Void> validated = session.item == null ? RiftService.validate(session.level, data) : RiftService.validateTemplate(session.level, data);
                if (validated instanceof Result.FailureResult<Void>(
                        Failure failure2
                )) {
                    send(session, EditorAction.ERROR, null, failure2.name());
                    return;
                }
                if (session.item != null && (payload.action().equals("submit") || payload.action().equals("blueprint"))) {
                    ItemTarget target = session.item;
                    RiftComponent component = new RiftComponent(Optional.of(data), Optional.empty());
                    target.stack().set(MxtDataComponents.RIFT, component);
                    session.item = new ItemTarget(target.hand(), target.stack(), component);
                    send(session, EditorAction.ITEM_ACCEPTED, null, RiftExport.template(data));
                    return;
                }
                if (session.item != null && payload.action().equals("renew")) {
                    send(session, EditorAction.ERROR, null, "item_only");
                    return;
                }
                if (payload.action().equals("blueprint")) {
                    ItemStack stack = player.getMainHandItem();
                    if (!(stack.getItem() instanceof RiftAnchorItem)) {
                        send(session, EditorAction.ERROR, null, "hold_anchor");
                        return;
                    }
                    stack.set(MxtDataComponents.RIFT, new RiftComponent(Optional.of(data), Optional.empty()));
                    send(session, EditorAction.BLUEPRINT, null, "");
                    return;
                }
                if (payload.action().startsWith("export_")) {
                    if (payload.action().equals("export_world") && RiftService.world(session.level).locations().size() > 128) {
                        send(session, EditorAction.ERROR, null, "budget");
                        return;
                    }
                    List<RiftTemplate> templates = payload.action().equals("export_world") ? RiftService.records(session.level).stream()
                            .filter(r -> !r.expired(now)).map(RiftRecord::data).limit(128).toList() : List.of(data);
                    String json = payload.action().equals("export_preset") ? RiftExport.preset(data) : RiftExport.layout(templates,
                            payload.action().equals("export_relative") ? Optional.of(player.position()) : Optional.empty(),
                            payload.action().equals("export_local") ? Optional.of(session.level.dimension().identifier()) : Optional.empty());
                    if (json.length() > 262144) send(session, EditorAction.ERROR, null, "budget");
                    else send(session, EditorAction.EXPORT, null, json);
                    return;
                }
                boolean reset = payload.action().equals("renew") || record == null || record.data().durationTicks() != data.durationTicks();
                Result<RiftRecord> result = RiftService.publish(session.level, session.id, payload.revision(), data, player.getUUID(), reset);
                if (result instanceof Result.FailureResult<RiftRecord>(
                        Failure failure1
                )) send(session, EditorAction.ERROR, null, failure1.name());
                else {
                    RiftRecord published = result.getData().orElseThrow();
                    session.id = published.id();
                    send(session, EditorAction.ACCEPTED, published, "");
                }
            }
            default -> send(session, EditorAction.ERROR, null, "unknown_action");
        }
    }

    private static boolean valid(Session session) {
        return session.player.level() == session.level && !session.player.isRemoved() && mayEdit(session.player)
                && session.player.level().getServer().getPlayerList().getPlayer(session.player.getUUID()) == session.player;
    }

    public static void tick(ServerLevel level) {
        for (Session session : List.copyOf(SESSIONS.values()))
            if (session.level == level
                    && (!valid(session) || RiftService.now(level) - session.lastSeen > 1200)) close(session);
    }

    private static void send(Session session, EditorAction action, @Nullable RiftRecord record, String body) {
        PacketDistributor.sendToPlayer(session.player, new RiftEditorS2CPayload(session.token, session.level.dimension().identifier(), action,
                Optional.ofNullable(record), body));
    }

    private static void close(Session session) {
        SESSIONS.remove(session.player.getUUID());
        send(session, EditorAction.CLOSE, null, "");
    }

    public static void clear() {
        SESSIONS.clear();
    }

    private record ItemTarget(InteractionHand hand, ItemStack stack, RiftComponent component) {
        boolean matches(ServerPlayer player) {
            return !this.stack.isEmpty() && player.getItemInHand(this.hand) == this.stack
                    && this.component.equals(this.stack.getOrDefault(MxtDataComponents.RIFT, RiftComponent.EMPTY));
        }
    }

    private static final class Session {
        final UUID token;
        final ServerPlayer player;
        final ServerLevel level;
        @Nullable UUID id;
        @Nullable ItemTarget item;
        long lastSeen, lastAction = Long.MIN_VALUE / 2;

        Session(UUID token, ServerPlayer player, ServerLevel level, @Nullable UUID id, long now) {
            this.token = token;
            this.player = player;
            this.level = level;
            this.id = id;
            this.lastSeen = now;
        }
    }
}
