package com.iafenvoy.mxt.event;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.UUID;

public abstract class RealmInstanceEvent extends Event {
    private final ServerLevel level;
    private final Identifier definition;
    private final UUID member;

    protected RealmInstanceEvent(ServerLevel level, Identifier definition, UUID member) {
        this.level = level;
        this.definition = definition;
        this.member = member;
    }

    public ServerLevel level() {
        return this.level;
    }

    public Identifier definition() {
        return this.definition;
    }

    public UUID member() {
        return this.member;
    }

    public static final class EnterPre extends RealmInstanceEvent implements ICancellableEvent {
        public EnterPre(ServerLevel level, Identifier id, UUID member) {
            super(level, id, member);
        }
    }

    public static final class EnterPost extends RealmInstanceEvent {
        public EnterPost(ServerLevel level, Identifier id, UUID member) {
            super(level, id, member);
        }
    }

    public static final class Exit extends RealmInstanceEvent {
        public Exit(ServerLevel level, Identifier id, UUID member) {
            super(level, id, member);
        }
    }
}
