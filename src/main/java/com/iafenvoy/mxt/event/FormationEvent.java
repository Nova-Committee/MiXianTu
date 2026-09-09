package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.runtime.formation.FormationInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Lifecycle hooks for world-backed formation instances. The active formation id is
 * exposed through {@link FormationInstance#formation()}; no separate identifier field
 * is duplicated on the event.
 */
public abstract class FormationEvent extends Event {
    private final ServerLevel level;
    private final BlockPos controller;
    private final FormationInstance instance;

    protected FormationEvent(ServerLevel level, BlockPos controller, FormationInstance instance) {
        this.level = level;
        this.controller = controller;
        this.instance = instance;
    }

    public ServerLevel level() {
        return this.level;
    }

    public BlockPos controller() {
        return this.controller;
    }

    public FormationInstance instance() {
        return this.instance;
    }

    public static final class Activate extends FormationEvent implements ICancellableEvent {
        public Activate(ServerLevel level, BlockPos pos, FormationInstance instance) {
            super(level, pos, instance);
        }
    }

    public static final class Deactivate extends FormationEvent {
        public Deactivate(ServerLevel level, BlockPos pos, FormationInstance instance) {
            super(level, pos, instance);
        }
    }

    public static final class Tick extends FormationEvent implements ICancellableEvent {
        public Tick(ServerLevel level, BlockPos pos, FormationInstance instance) {
            super(level, pos, instance);
        }
    }
}
