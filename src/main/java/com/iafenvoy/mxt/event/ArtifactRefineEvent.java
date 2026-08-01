package com.iafenvoy.mxt.event;

import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.UUID;

public abstract class ArtifactRefineEvent extends Event {
    private final ItemStack stack;
    private final UUID owner;

    protected ArtifactRefineEvent(ItemStack stack, UUID owner) {
        this.stack = stack;
        this.owner = owner;
    }

    public ItemStack stack() {
        return this.stack;
    }

    public UUID owner() {
        return this.owner;
    }

    public static final class Pre extends ArtifactRefineEvent implements ICancellableEvent {
        public Pre(ItemStack stack, UUID owner) {
            super(stack, owner);
        }
    }

    public static final class Post extends ArtifactRefineEvent {
        public Post(ItemStack stack, UUID owner) {
            super(stack, owner);
        }
    }
}
