package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * One carrier's contents as a live container: a server-side mirror that writes every change straight back, and
 * deliberately holds no stack - a carrier can move or be handed over, and writing into a stack nobody carries is how
 * items disappear. Every read and write re-resolves it; {@link #stillValid} says no once it is gone.
 */
public final class ArtifactStorageContainer extends SimpleContainer {
    private final ServerPlayer viewer;
    private final Holder<Ability> ability;

    // Opens one carrier's container: the mirror starts with what the stack holds for that ability.
    public ArtifactStorageContainer(ServerPlayer viewer, Holder<Ability> ability, int capacity) {
        super(capacity);
        this.viewer = viewer;
        this.ability = ability;
        Provider access = this.access();
        this.carrier().ifPresent(stack -> {
            // Filled through the raw list: writing here is loading, not a change, and must not be written back.
            for (int slot = 0; slot < capacity; slot++)
                this.getItems().set(slot, ArtifactStorageService.INSTANCE.get(access, stack, this.ability, slot, this.viewer).copy());
        });
    }

    // Every change the screen makes lands here - a slot set, a slot emptied, a shift-click - and the whole
    // contents go back into the stack in one component update.
    @Override
    public void setChanged() {
        Provider access = this.access();
        this.carrier().ifPresent(stack ->
                ArtifactStorageService.INSTANCE.replace(access, stack, this.ability, this.getItems(), this.viewer));
    }

    // A menu that says no is closed by the server (a chest menu asks its container, and the server asks its menu
    // once a tick).
    @Override
    public boolean stillValid(@NonNull Player player) {
        return this.carrier().isPresent();
    }

    private Optional<ItemStack> carrier() {
        return ArtifactService.carried(this.access(), this.viewer, HolderHelper.id(this.ability));
    }

    private Provider access() {
        return this.viewer.level().registryAccess();
    }
}
