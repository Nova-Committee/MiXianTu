package com.iafenvoy.mxt.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;

public abstract class ArtifactRefineEvent extends EntityEvent {
    private final ItemStack stack;

    protected ArtifactRefineEvent(Entity owner, ItemStack stack) {
        super(owner);
        this.stack = stack;
    }

    public ItemStack getStack() {
        return this.stack;
    }

    public static final class Pre extends ArtifactRefineEvent implements ICancellableEvent {
        public Pre(Entity owner, ItemStack stack) {
            super(owner, stack);
        }
    }

    public static final class Post extends ArtifactRefineEvent {
        public Post(Entity owner, ItemStack stack) {
            super(owner, stack);
        }
    }
}
