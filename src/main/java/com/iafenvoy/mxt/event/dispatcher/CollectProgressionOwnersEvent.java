package com.iafenvoy.mxt.event.dispatcher;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.EntityEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Asks every system whose definitions own a progression chain which of them the body holds. A definition that declares
 * no chain is not an owner, so a system names one only when its own rules say so.
 */
public final class CollectProgressionOwnersEvent extends EntityEvent {
    private final List<EntitySources.Owner<?>> owners = new ArrayList<>();

    public CollectProgressionOwnersEvent(Entity entity) {
        super(entity);
    }

    public void add(EntitySources.Owner<?> owner) {
        this.owners.add(owner);
    }

    public List<EntitySources.Owner<?>> owners() {
        return List.copyOf(this.owners);
    }
}
