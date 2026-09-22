package com.iafenvoy.mxt.runtime.artifact;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * One artifact's contents as a live container: a chest-sized mirror that writes every change straight back into
 * the artifact it was opened from.
 *
 * <p>The stack is deliberately not held: the artifact can be moved, dropped or handed over while the screen is
 * open, and writing into a stack nobody carries any more is how items disappear. Every read and write resolves
 * the artifact from its carrier again, and {@link #stillValid} says no the moment it is gone. Server side only -
 * the client's menu gets a plain mirror and reads its slots from the menu sync.
 */
public final class ArtifactStorageContainer extends SimpleContainer {
    private final ServerPlayer viewer;
    private final Identifier artifact;

    // Opens one artifact's storage to its carrier: the mirror starts with what the artifact holds.
    public ArtifactStorageContainer(ServerPlayer viewer, Identifier artifact, int capacity) {
        super(capacity);
        this.viewer = viewer;
        this.artifact = artifact;
        Provider access = this.access();
        this.carrier().ifPresent(stack -> {
            // Filled through the raw list: writing here is loading, not a change, and must not be written back.
            for (int slot = 0; slot < capacity; slot++)
                this.getItems().set(slot, ArtifactStorageService.INSTANCE.get(access, stack, slot, this.viewer).copy());
        });
    }

    // Every change the screen makes lands here - a slot set, a slot emptied, a shift-click - and the whole
    // contents go back into the artifact in one component update.
    @Override
    public void setChanged() {
        Provider access = this.access();
        this.carrier().ifPresent(stack ->
                ArtifactStorageService.INSTANCE.replace(access, stack, this.getItems(), this.viewer));
    }

    // A menu that says no is closed by the server (a chest menu asks its container, and the server asks its menu
    // once a tick).
    @Override
    public boolean stillValid(@NonNull Player player) {
        return this.carrier().isPresent();
    }

    private Optional<ItemStack> carrier() {
        return ArtifactService.carried(this.access(), this.viewer, this.artifact);
    }

    private Provider access() {
        return this.viewer.level().registryAccess();
    }
}
