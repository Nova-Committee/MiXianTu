package com.iafenvoy.mxt.runtime.rift;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.registry.MxtTicketTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Server-thread only. Each handle owns one share; only the last close removes the Minecraft ticket.
public final class RiftChunkTicket implements AutoCloseable {
    private static final int RADIUS = 2;
    private static final Map<Key, Shared> ACTIVE = new HashMap<>();
    private final Shared shared;
    private boolean closed;

    private RiftChunkTicket(Shared shared) {
        this.shared = shared;
    }

    public static RiftChunkTicket acquire(ServerLevel level, ChunkPos pos) {
        Key key = new Key(level, pos);
        Shared shared = ACTIVE.computeIfAbsent(key, Shared::new);
        RiftChunkTicket ticket = new RiftChunkTicket(shared);
        shared.references++;
        try {
            // Refresh the fallback timeout so a later traveller gets its full waiting window.
            level.getChunkSource().addTicketWithRadius(MxtTicketTypes.RIFT.get(), pos, RADIUS);
            return ticket;
        } catch (RuntimeException | Error failure) {
            ticket.close();
            throw failure;
        }
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        if (!this.shared.closed && --this.shared.references == 0) this.shared.close();
    }

    public static void unload(ServerLevel level) {
        for (Shared shared : List.copyOf(ACTIVE.values())) if (shared.key.level == level) shared.close();
    }

    public static void clear() {
        for (Shared shared : List.copyOf(ACTIVE.values())) shared.close();
    }

    private record Key(ServerLevel level, ChunkPos pos) {
    }

    private static final class Shared implements AutoCloseable {
        private final Key key;
        private int references;
        private boolean closed;

        Shared(Key key) {
            this.key = key;
        }

        @Override
        public void close() {
            if (this.closed) return;
            this.closed = true;
            ACTIVE.remove(this.key, this);
            try {
                this.key.level.getChunkSource().removeTicketWithRadius(MxtTicketTypes.RIFT.get(), this.key.pos, RADIUS);
            } catch (RuntimeException failure) {
                MiXianTu.LOGGER.warn("Failed to release rift chunk ticket in {} at {}; fallback expiry remains active",
                        this.key.level.dimension().identifier(), this.key.pos, failure);
            }
        }
    }
}
