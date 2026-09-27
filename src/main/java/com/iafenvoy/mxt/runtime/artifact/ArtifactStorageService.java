package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.storage.builtin.ContainerDataStorage;
import com.iafenvoy.mxt.runtime.item.ItemStorageService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The container an ability declares for the stack that carries it. The limit comes from the definition, never from
 * the payload, and only the owner may reach the contents, only on the server; the contents are one entry of the
 * stack's ability storage, addressed by that ability's own id.
 */
public final class ArtifactStorageService implements ISpiritStorage {
    public static final ArtifactStorageService INSTANCE = new ArtifactStorageService();

    private ArtifactStorageService() {
    }

    @Override
    public int slots(Provider access, ItemStack stack, Holder<Ability> ability, FormulaContext context) {
        return ArtifactService.storageSlots(stack, ability, context);
    }

    @Override
    public ItemStack get(Provider access, ItemStack stack, Holder<Ability> ability, int slot, Player viewer) {
        if (!this.mayAccess(access, stack, ability, viewer)) return ItemStack.EMPTY;
        if (slot < 0 || slot >= this.slots(access, stack, ability, FormulaContexts.forEntity(viewer))) return ItemStack.EMPTY;
        return storage(stack, ability).get(slot);
    }

    @Override
    public boolean set(Provider access, ItemStack stack, Holder<Ability> ability, int slot, ItemStack value, Player viewer) {
        if (!this.mayAccess(access, stack, ability, viewer)) return false;
        int capacity = this.slots(access, stack, ability, FormulaContexts.forEntity(viewer));
        if (slot < 0 || slot >= capacity || value.getCount() > value.getMaxStackSize()) return false;
        write(stack, ability, storage(stack, ability).with(slot, value, capacity), viewer);
        return true;
    }

    // The whole contents at once, which is what a screen writes back: one update per change instead of one per slot,
    // and the stored list stays at the capacity the definition declares.
    public boolean replace(Provider access, ItemStack stack, Holder<Ability> ability, List<ItemStack> contents, Player viewer) {
        if (!this.mayAccess(access, stack, ability, viewer)) return false;
        int capacity = this.slots(access, stack, ability, FormulaContexts.forEntity(viewer));
        if (capacity <= 0) return false;
        write(stack, ability, ContainerDataStorage.of(capacity, contents), viewer);
        return true;
    }

    public boolean mayAccess(Provider access, ItemStack stack, Holder<Ability> ability, Player viewer) {
        if (viewer.level().isClientSide() || this.slots(access, stack, ability, FormulaContexts.forEntity(viewer)) <= 0)
            return false;
        return ArtifactService.definition(access, stack)
                .map(holder -> ArtifactService.mayUse(stack, holder, viewer.getUUID())).orElse(false);
    }

    private static void write(ItemStack stack, Holder<Ability> ability, ContainerDataStorage contents, Player viewer) {
        ItemStorageService.set(stack, HolderHelper.id(ability), contents);
    }

    private static ContainerDataStorage storage(ItemStack stack, Holder<Ability> ability) {
        return ItemStorageService.get(stack, HolderHelper.id(ability), ContainerDataStorage.class)
                .orElseGet(() -> ContainerDataStorage.of(0, List.of()));
    }
}
