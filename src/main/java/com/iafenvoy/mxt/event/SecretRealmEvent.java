package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * Secret realm lifecycle. The dimension key is part of every payload because that is what an instance is:
 * {@code Create} and {@code Destroy} bracket a dimension, and the member events describe one visit to it.
 */
public abstract class SecretRealmEvent extends Event {
    private final MinecraftServer server;
    private final Holder<SecretRealm> definition;
    private final ResourceKey<Level> dimension;
    private final int index;
    private final Optional<UUID> owner;

    protected SecretRealmEvent(MinecraftServer server, Holder<SecretRealm> definition, ResourceKey<Level> dimension,
                               int index, Optional<UUID> owner) {
        this.server = server;
        this.definition = definition;
        this.dimension = dimension;
        this.index = index;
        this.owner = owner;
    }

    public MinecraftServer server() {
        return this.server;
    }

    public Holder<SecretRealm> definition() {
        return this.definition;
    }

    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    public int index() {
        return this.index;
    }

    public Optional<UUID> owner() {
        return this.owner;
    }

    public static final class Create extends SecretRealmEvent {
        public Create(MinecraftServer server, Holder<SecretRealm> definition, ResourceKey<Level> dimension, int index,
                      Optional<UUID> owner) {
            super(server, definition, dimension, index, owner);
        }
    }

    public static final class Destroy extends SecretRealmEvent {
        public Destroy(MinecraftServer server, Holder<SecretRealm> definition, ResourceKey<Level> dimension, int index,
                       Optional<UUID> owner) {
            super(server, definition, dimension, index, owner);
        }
    }

    public abstract static class MemberEvent extends SecretRealmEvent {
        private final UUID member;

        protected MemberEvent(MinecraftServer server, Holder<SecretRealm> definition, ResourceKey<Level> dimension,
                              int index, Optional<UUID> owner, UUID member) {
            super(server, definition, dimension, index, owner);
            this.member = member;
        }

        public UUID member() {
            return this.member;
        }
    }

    public static final class EnterPre extends MemberEvent implements ICancellableEvent {
        public EnterPre(MinecraftServer server, Holder<SecretRealm> definition, ResourceKey<Level> dimension, int index,
                        Optional<UUID> owner, UUID member) {
            super(server, definition, dimension, index, owner, member);
        }
    }

    public static final class EnterPost extends MemberEvent {
        public EnterPost(MinecraftServer server, Holder<SecretRealm> definition, ResourceKey<Level> dimension, int index,
                         Optional<UUID> owner, UUID member) {
            super(server, definition, dimension, index, owner, member);
        }
    }

    public static final class Exit extends MemberEvent {
        public Exit(MinecraftServer server, Holder<SecretRealm> definition, ResourceKey<Level> dimension, int index,
                    Optional<UUID> owner, UUID member) {
            super(server, definition, dimension, index, owner, member);
        }
    }
}
