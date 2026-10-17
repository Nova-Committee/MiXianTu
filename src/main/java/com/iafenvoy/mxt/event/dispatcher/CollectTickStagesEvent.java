package com.iafenvoy.mxt.event.dispatcher;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Asks every module which per-entity tick stages it owns. Posted once, before the first stage runs, because the table
 * {@link EntityTickStages} keeps is built from the answers and then sorted.
 */
public final class CollectTickStagesEvent extends Event {
    private final List<EntityTickStages.Stage> stages = new ArrayList<>();

    public void add(String id, int order, Consumer<LivingEntity> handler) {
        this.stages.add(new EntityTickStages.Stage(id, order, handler));
    }

    public List<EntityTickStages.Stage> stages() {
        return this.stages;
    }
}
