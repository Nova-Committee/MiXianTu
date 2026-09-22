package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.data.artifact.ArtifactStorageComponent;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Default storage for artifacts whose definition declares a positive slot count. The limit comes from the
 * definition, never from the payload, and only the owner may reach the contents, only on the server.
 */
public final class ArtifactStorageService implements ISpiritStorage {
    public static final ArtifactStorageService INSTANCE = new ArtifactStorageService();

    private ArtifactStorageService() {
    }

    @Override
    public int slots(Provider access, ItemStack stack, FormulaContext context) {
        return ArtifactService.storageSlots(access, stack, context);
    }

    @Override
    public ItemStack get(Provider access, ItemStack stack, int slot, Player viewer) {
        if (!this.mayAccess(access, stack, viewer)) return ItemStack.EMPTY;
        int capacity = this.slots(access, stack, FormulaContexts.forEntity(viewer));
        if (slot < 0 || slot >= capacity) return ItemStack.EMPTY;
        return storage(stack).get(slot);
    }

    @Override
    public boolean set(Provider access, ItemStack stack, int slot, ItemStack value, Player viewer) {
        if (!this.mayAccess(access, stack, viewer)) return false;
        int capacity = this.slots(access, stack, FormulaContexts.forEntity(viewer));
        if (slot < 0 || slot >= capacity || value.getCount() > value.getMaxStackSize()) return false;
        stack.set(MxtDataComponents.ARTIFACT_STORAGE, storage(stack).with(slot, value, capacity));
        return true;
    }

    // The whole contents at once, which is what a screen writes back: one component update per change instead of
    // one per slot, and the stored list stays at the capacity the definition declares.
    public boolean replace(Provider access, ItemStack stack, List<ItemStack> contents, Player viewer) {
        if (!this.mayAccess(access, stack, viewer)) return false;
        int capacity = this.slots(access, stack, FormulaContexts.forEntity(viewer));
        if (capacity <= 0) return false;
        stack.set(MxtDataComponents.ARTIFACT_STORAGE, ArtifactStorageComponent.of(capacity, contents));
        return true;
    }

    public boolean mayAccess(Provider access, ItemStack stack, Player viewer) {
        if (viewer.level().isClientSide() || this.slots(access, stack, FormulaContexts.forEntity(viewer)) <= 0)
            return false;
        return ArtifactService.definition(access, stack)
                .map(holder -> ArtifactService.mayUse(stack, holder, viewer.getUUID())).orElse(false);
    }

    private static ArtifactStorageComponent storage(ItemStack stack) {
        return Optional.ofNullable(stack.get(MxtDataComponents.ARTIFACT_STORAGE)).orElseGet(() -> new ArtifactStorageComponent(List.of()));
    }
}
