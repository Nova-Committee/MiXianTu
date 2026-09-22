package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.runtime.formation.FormationInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.Optional;

/**
 * Lifecycle hooks for world-backed formation instances. The active configuration is read from
 * {@link FormationInstance#formation()}; no identifier is duplicated on the event.
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

    /**
     * One settled period, posted once the formation's upkeep for it has been charged. Deliberately not cancellable:
     * the resource is already spent, so cancelling would drain the payer while suppressing every effect. Anything
     * that wants to suppress the work belongs on {@link TickEffects}.
     */
    public static final class Tick extends FormationEvent {
        public Tick(ServerLevel level, BlockPos pos, FormationInstance instance) {
            super(level, pos, instance);
        }
    }

    /**
     * The part of a period a listener may suppress: {@code tick_action} and the per-entity enter/tick/exit actions.
     * Upkeep for the period is already charged and is not refunded; to stop the formation, take it down.
     */
    public static final class TickEffects extends FormationEvent implements ICancellableEvent {
        public TickEffects(ServerLevel level, BlockPos pos, FormationInstance instance) {
            super(level, pos, instance);
        }
    }

    /**
     * Upkeep for this period could not be paid: no payer could be resolved, or the payer is short of a resource.
     * Cancelling keeps the formation registered and lets the period pass, paying and doing nothing.
     */
    public static final class UpkeepFailed extends FormationEvent implements ICancellableEvent {
        private final Optional<Entity> payer;
        private final Optional<Identifier> failedResource;

        public UpkeepFailed(ServerLevel level, BlockPos pos, FormationInstance instance, Optional<Entity> payer,
                            Optional<Identifier> failedResource) {
            super(level, pos, instance);
            this.payer = payer;
            this.failedResource = failedResource;
        }

        // Empty when the owner is missing: offline, dead, or never recorded.
        public Optional<Entity> payer() {
            return this.payer;
        }

        // Empty when there was nobody to pay at all.
        public Optional<Identifier> failedResource() {
            return this.failedResource;
        }
    }
}
