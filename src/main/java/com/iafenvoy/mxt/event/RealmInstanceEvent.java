package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.data.RealmInstance;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.UUID;

public abstract class RealmInstanceEvent extends Event {
    private final ServerLevel level;
    private final Holder<RealmInstance> definition;
    private final UUID member;

    protected RealmInstanceEvent(ServerLevel level, Holder<RealmInstance> definition, UUID member) {
        this.level = level;
        this.definition = definition;
        this.member = member;
    }

    public ServerLevel level() {
        return this.level;
    }

    public Holder<RealmInstance> definition() {
        return this.definition;
    }

    public UUID member() {
        return this.member;
    }

    public static final class EnterPre extends RealmInstanceEvent implements ICancellableEvent {
        public EnterPre(ServerLevel level, Holder<RealmInstance> definition, UUID member) {
            super(level, definition, member);
        }
    }

    public static final class EnterPost extends RealmInstanceEvent {
        public EnterPost(ServerLevel level, Holder<RealmInstance> definition, UUID member) {
            super(level, definition, member);
        }
    }

    public static final class Exit extends RealmInstanceEvent {
        public Exit(ServerLevel level, Holder<RealmInstance> definition, UUID member) {
            super(level, definition, member);
        }
    }
}
