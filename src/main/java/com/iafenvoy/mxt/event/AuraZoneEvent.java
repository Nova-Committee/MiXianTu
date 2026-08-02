package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.runtime.world.AuraResult;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Server-side lifecycle events for resolved aura environments.
 */
public abstract class AuraZoneEvent extends Event {
    private final ServerLevel level;
    private final BlockPos position;
    private final AuraResult result;

    protected AuraZoneEvent(ServerLevel level, BlockPos position, AuraResult result) {
        this.level = level;
        this.position = position;
        this.result = result;
    }

    public ServerLevel level() {
        return this.level;
    }

    public BlockPos position() {
        return this.position;
    }

    public AuraResult result() {
        return this.result;
    }

    public static final class Enter extends AuraZoneEvent {
        private final Entity entity;

        public Enter(Entity e, AuraResult r) {
            super((ServerLevel) e.level(), e.blockPosition(), r);
            this.entity = e;
        }

        public Entity entity() {
            return this.entity;
        }
    }

    public static final class Leave extends AuraZoneEvent {
        private final Entity entity;

        public Leave(Entity e, AuraResult r) {
            super((ServerLevel) e.level(), e.blockPosition(), r);
            this.entity = e;
        }

        public Entity entity() {
            return this.entity;
        }
    }

    public static final class Tick extends AuraZoneEvent {
        public Tick(ServerLevel l, BlockPos p, AuraResult r) {
            super(l, p, r);
        }
    }

    public static final class Override extends AuraZoneEvent implements ICancellableEvent {
        private final Identifier zone;

        public Override(ServerLevel l, BlockPos p, AuraResult r, Identifier zone) {
            super(l, p, r);
            this.zone = zone;
        }

        public Identifier zone() {
            return this.zone;
        }
    }
}
