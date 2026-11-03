package com.iafenvoy.mxt.util;

import com.iafenvoy.mxt.network.payload.ProfilerSnapshotS2CPayload;
import com.iafenvoy.mxt.screen.profiling.ProfilerOpenScreenData;
import com.iafenvoy.saturn.profiling.ProfilerFrame;
import com.iafenvoy.saturn.profiling.ProfilerWindowSnapshot;
import com.iafenvoy.saturn.profiling.ServerProfiling;
import com.iafenvoy.saturn.screen.opener.ScreenOpener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber
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
        ScreenOpener.open(player, new ProfilerOpenScreenData(subscription.token, subscription.local));
        return true;
    }

    // A command grants a token; only the GUI's acknowledgement starts sampling delivery.
    public static void update(ServerPlayer player, UUID token, boolean open) {
        State state = current;
        if (state == null || !state.server.isSameThread()) return;
        Subscription subscription = state.subscriptions.get(player.getUUID());
        if (subscription == null || subscription.player != player || !subscription.token.equals(token)) return;
        if (!open || !mayView(player.permissions())) {
            state.remove(subscription);
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

    // Saturn owns the profiling session; the GUI grants are this mod's, so their lease is ticked here. Both hooks
    // run at Saturn's own priority, and the mod loads after the library, so a frame is published from a window the
    // library has already flushed for this tick.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStarting(ServerAboutToStartEvent event) {
        start(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStopped(ServerStoppedEvent event) {
        stop(event.getServer());
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
            if (!state.valid(subscription)) state.remove(subscription);
            else if (subscription.local != local(server)) {
                state.remove(subscription);
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
        private long tick, sequence, revision;
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

        // The GUI is never told to close: the client re-checks the grant itself (permissions, connection, player)
        // every tick, and a dropped subscription simply stops the samples.
        private void remove(Subscription subscription) {
            this.subscriptions.remove(subscription.player.getUUID(), subscription);
            LocalView view = this.localView;
            if (view != null && view.token.equals(subscription.token)) this.localView = null;
        }

        private void publish(ProfilerWindowSnapshot window) {
            ProfilerFrame frame = new ProfilerFrame(++this.sequence, ServerProfiling.enabled(this.server), window);
            for (Subscription subscription : this.subscriptions.values().toArray(Subscription[]::new)) {
                if (!this.valid(subscription)) {
                    this.remove(subscription);
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
