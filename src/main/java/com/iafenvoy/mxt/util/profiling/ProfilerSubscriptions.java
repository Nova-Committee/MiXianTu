package com.iafenvoy.mxt.util.profiling;

import com.iafenvoy.mxt.network.payload.ProfilerGuiS2CPayload;
import com.iafenvoy.mxt.network.payload.ProfilerSnapshotS2CPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ProfilerSubscriptions {
    public static final int UPDATE_TICKS = 5;
    public static final int HEARTBEAT_TICKS = 20;
    private static final int LEASE_TICKS = 60;
    private static volatile State current;

    private ProfilerSubscriptions() {
    }

    public static boolean mayView(PermissionSet permissions) {
        return permissions.hasPermission(Permissions.COMMANDS_ADMIN);
    }

    public static boolean local(MinecraftServer server) {
        return !server.isDedicatedServer() && !server.isPublished();
    }

    public static boolean open(ServerPlayer player) {
        State state = current;
        if (state == null || !state.server.isSameThread() || !mayView(player.permissions())
                || state.server.getPlayerList().getPlayer(player.getUUID()) != player) return false;
        Subscription subscription = new Subscription(player, UUID.randomUUID(), local(state.server), state.tick);
        state.subscriptions.put(player.getUUID(), subscription);
        state.localView = null;
        PacketDistributor.sendToPlayer(player, new ProfilerGuiS2CPayload(subscription.token, true, subscription.local));
        return true;
    }

    // A command grants a token; only the GUI's acknowledgement starts sampling delivery.
    public static void update(ServerPlayer player, UUID token, boolean open) {
        State state = current;
        if (state == null || !state.server.isSameThread()) return;
        Subscription subscription = state.subscriptions.get(player.getUUID());
        if (subscription == null || subscription.player != player || !subscription.token.equals(token)) return;
        if (!open || !mayView(player.permissions())) {
            state.remove(subscription, open);
            return;
        }
        subscription.active = true;
        subscription.renewed = state.tick;
    }

    // Only this immutable publication crosses threads; the subscription map stays on the server thread.
    public static @Nullable ProfilerFrame readLocal(MinecraftServer server, UUID player, UUID token) {
        State state = current;
        LocalView view = state == null || state.server != server ? null : state.localView;
        return view != null && view.player.equals(player) && view.token.equals(token) ? view.frame : null;
    }

    static void start(MinecraftServer server) {
        current = new State(server);
    }

    static void stop(MinecraftServer server) {
        State state = current;
        if (state != null && state.server == server) current = null;
    }

    static void changed(MinecraftServer server) {
        State state = current;
        if (state == null || state.server != server) return;
        state.revision++;
        state.publish(ProfilerWindowSnapshot.EMPTY);
    }

    static void tick(MinecraftServer server) {
        State state = current;
        if (state == null || state.server != server) return;
        state.tick++;
        if (state.subscriptions.isEmpty()) return;
        for (Subscription subscription : state.subscriptions.values().toArray(Subscription[]::new)) {
            if (!state.valid(subscription)) state.remove(subscription, true);
            else if (subscription.local != local(server)) {
                state.remove(subscription, false);
                open(subscription.player);
            }
        }
        if ((!local(server) && state.tick % UPDATE_TICKS != 0L) || state.pending
                || state.subscriptions.values().stream().noneMatch(value -> value.active)) return;
        if (!ServerProfiling.enabled(server)) {
            state.publish(ProfilerWindowSnapshot.EMPTY);
            return;
        }
        state.pending = true;
        long revision = state.revision;
        ServerProfiling.snapshot(server).whenComplete((window, error) -> server.execute(() -> {
            state.pending = false;
            if (current != state || state.revision != revision || error != null) return;
            state.publish(window);
        }));
    }

    private static final class State {
        private final MinecraftServer server;
        private final Map<UUID, Subscription> subscriptions = new HashMap<>();
        private volatile LocalView localView;
        private long tick;
        private long sequence;
        private long revision;
        private boolean pending;

        private State(MinecraftServer server) {
            this.server = server;
        }

        private boolean valid(Subscription subscription) {
            return this.subscriptions.get(subscription.player.getUUID()) == subscription
                    && this.tick - subscription.renewed < LEASE_TICKS
                    && subscription.player.connection.isAcceptingMessages()
                    && this.server.getPlayerList().getPlayer(subscription.player.getUUID()) == subscription.player
                    && mayView(subscription.player.permissions());
        }

        private void remove(Subscription subscription, boolean notify) {
            this.subscriptions.remove(subscription.player.getUUID(), subscription);
            LocalView view = this.localView;
            if (view != null && view.token.equals(subscription.token)) this.localView = null;
            if (notify && subscription.player.connection.isAcceptingMessages())
                PacketDistributor.sendToPlayer(subscription.player, new ProfilerGuiS2CPayload(subscription.token, false, subscription.local));
        }

        private void publish(ProfilerWindowSnapshot window) {
            ProfilerFrame frame = new ProfilerFrame(++this.sequence, ServerProfiling.enabled(this.server), window);
            for (Subscription subscription : this.subscriptions.values().toArray(Subscription[]::new)) {
                if (!this.valid(subscription)) {
                    this.remove(subscription, true);
                    continue;
                }
                if (!subscription.active) continue;
                if (subscription.local)
                    this.localView = new LocalView(subscription.player.getUUID(), subscription.token, frame);
                else
                    PacketDistributor.sendToPlayer(subscription.player, new ProfilerSnapshotS2CPayload(subscription.token, frame));
            }
        }
    }

    private static final class Subscription {
        private final ServerPlayer player;
        private final UUID token;
        private final boolean local;
        private boolean active;
        private long renewed;

        private Subscription(ServerPlayer player, UUID token, boolean local, long tick) {
            this.player = player;
            this.token = token;
            this.local = local;
            this.renewed = tick;
        }
    }

    private record LocalView(UUID player, UUID token, ProfilerFrame frame) {
    }
}
