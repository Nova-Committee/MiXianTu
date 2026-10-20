package com.iafenvoy.mxt.util.profiling;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.concurrent.CompletableFuture;

// Only the owning server thread is sampled; worker threads need their own verified tick boundaries.
@EventBusSubscriber
public final class ServerProfiling {
    private static final ProfilerScope NOOP = () -> {
    };
    private static volatile Session current;

    private ServerProfiling() {
    }

    public static boolean enabled(MinecraftServer server) {
        Session session = current;
        return session != null && session.server == server && session.profiler.enabled();
    }

    public static boolean setEnabled(MinecraftServer server, boolean enabled) {
        Session session = current;
        if (session == null || session.server != server || !server.isSameThread()) return false;
        if (session.profiler.enabled() != enabled) {
            session.profiler.setEnabled(enabled);
            session.active = 0L;
            session.epoch++;
            ProfilerSubscriptions.changed(server);
        }
        return true;
    }

    public static CompletableFuture<ProfilerWindowSnapshot> snapshot(MinecraftServer server) {
        Session session = current;
        return session != null && session.server == server ? session.profiler.snapshot()
                : CompletableFuture.completedFuture(ProfilerWindowSnapshot.EMPTY);
    }

    public static ProfilerScope scope(Source source) {
        Session session = current;
        if (session == null || !session.profiler.enabled() || !session.server.isSameThread()) return NOOP;
        return session.scope(source);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStarting(ServerAboutToStartEvent event) {
        Session previous = current;
        current = new Session(event.getServer());
        ProfilerSubscriptions.start(event.getServer());
        setEnabled(event.getServer(), MiXianTu.isDevMode());
        if (previous != null) previous.profiler.close();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(ServerTickEvent.Post event) {
        Session session = current;
        if (session == null || session.server != event.getServer()) return;
        session.profiler.flushTick();
        session.active = 0L;
        session.epoch++;
        ProfilerSubscriptions.tick(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStopped(ServerStoppedEvent event) {
        Session session = current;
        if (session == null || session.server != event.getServer()) return;
        current = null;
        ProfilerSubscriptions.stop(event.getServer());
        session.profiler.close();
    }

    public enum Source {
        RESOURCE("resource"),
        AURA("aura"),
        CULTIVATION("cultivation"),
        PROGRESSION("progression"),
        ABILITY("ability"),
        TRIGGER("trigger"),
        COMBAT("combat"),
        FORMATION("formation"),
        CURSE("curse"),
        TRIBULATION("tribulation"),
        ARTIFACT("artifact"),
        SPIRIT("spirit"),
        CREATURE("creature"),
        ALCHEMY("alchemy"),
        HERB("herb"),
        FORGING("forging"),
        TALISMAN("talisman"),
        CRAFTING("crafting"),
        ECONOMY("economy"),
        SECRET_REALM("secret_realm"),
        RIFT("rift"),
        ITEM("item"),
        ENTITY("entity"),
        AURA_QUERY("aura/query", AURA),
        AURA_BLOCK_CACHE("aura/block_cache", AURA),
        CULTIVATION_DISTRIBUTION("cultivation/distribution", CULTIVATION),
        FORMATION_STRUCTURE("formation/structure", FORMATION),
        ABILITY_RECONCILE("ability/reconcile", ABILITY),
        ABILITY_CAST("ability/cast", ABILITY),
        ABILITY_CHANNEL("ability/channel", ABILITY),
        ECONOMY_CHANGE("economy/change", ECONOMY),
        COST("cost");

        private final String id;
        private final Source parent;

        Source(String path) {
            this(path, null);
        }

        Source(String path, Source parent) {
            this.id = "server/" + path;
            this.parent = parent;
        }

        public String id() {
            return this.id;
        }

        public String translationKey() {
            return "mxt.profiling.mxt." + this.id.substring("server/".length()).replace('/', '.');
        }
    }

    private static final class Session {
        private final MinecraftServer server;
        private final Profiler profiler = new Profiler();
        private final ProfilerSource[] sources = new ProfilerSource[Source.values().length];
        private long active;
        private long epoch;

        private Session(MinecraftServer server) {
            this.server = server;
            for (Source source : Source.values())
                this.sources[source.ordinal()] = this.profiler.sources().register(source.id());
        }

        // Reentrant calls share their outermost source; named children also open their parent when entered alone.
        private ProfilerScope scope(Source source) {
            long bit = 1L << source.ordinal();
            if ((this.active & bit) != 0L) return NOOP;
            ProfilerScope parent = source.parent == null ? NOOP : this.scope(source.parent);
            this.active |= bit;
            ProfilerScope sample = this.profiler.scope(this.sources[source.ordinal()]);
            long opened = this.epoch;
            return new ProfilerScope() {
                private boolean closed;

                @Override
                public void close() {
                    if (this.closed || !Session.this.server.isSameThread()) return;
                    this.closed = true;
                    try {
                        sample.close();
                    } finally {
                        if (Session.this.epoch == opened) Session.this.active &= ~bit;
                        parent.close();
                    }
                }
            };
        }
    }
}
