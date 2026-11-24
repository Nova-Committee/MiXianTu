package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.saturn.data.ability.Ability;
import com.iafenvoy.saturn.data.storage.builtin.ContainerDataStorage;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

public final class ArtifactStorageContainer extends SimpleContainer {
    private final ServerPlayer viewer;
    private final ItemStack carrier;
    private final Holder<Ability> ability;

    public ArtifactStorageContainer(ServerPlayer viewer, ItemStack carrier, Holder<Ability> ability, int capacity) {
        super(capacity);
        this.viewer = viewer;
        this.carrier = carrier;
        this.ability = ability;
        ContainerDataStorage stored = ArtifactStorageService.storage(carrier, ability, viewer);
        for (int slot = 0; slot < capacity; slot++) this.getItems().set(slot, stored.get(slot).copy());
    }

    @Override
    public void setChanged() {
        if (this.hasAccess(this.viewer))
            ArtifactStorageService.replace(this.carrier, this.ability, this.getItems(), this.viewer);
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return this.hasAccess(player);
    }

    private boolean hasAccess(Player player) {
        Provider access = this.viewer.registryAccess();
        return player == this.viewer && this.viewer.isAlive() && !this.viewer.isSpectator() && isCarried(this.viewer, this.carrier)
                && ArtifactStorageService.hasAccess(access, this.carrier, this.ability, this.viewer);
    }

    public static boolean isCarried(ServerPlayer viewer, ItemStack carrier) {
        if (carrier.isEmpty()) return false;
        if (ArtifactUpkeepService.carried(viewer).stream().anyMatch(stack -> stack == carrier)) return true;
        for (int slot = 0; slot < viewer.getInventory().getContainerSize(); slot++)
            if (viewer.getInventory().getItem(slot) == carrier) return true;
        return false;
    }
}
