package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.data.ability.type.StorageAbilityType;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.item.ItemStorageService;
import com.iafenvoy.saturn.data.ability.Ability;
import com.iafenvoy.saturn.data.storage.builtin.ContainerDataStorage;
import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.saturn.util.formula.FormulaContexts;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves authorized storage on the carrier or the player using the ability's ID.
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
        int capacity = this.capacity(access, stack, ability, viewer);
        if (slot < 0 || slot >= capacity) return ItemStack.EMPTY;
        return storage(stack, ability, viewer).get(slot).copy();
    }

    @Override
    public boolean set(Provider access, ItemStack stack, Holder<Ability> ability, int slot, ItemStack value, Player viewer) {
        int capacity = this.capacity(access, stack, ability, viewer);
        if (slot < 0 || slot >= capacity || value == stack || value.getCount() > value.getMaxStackSize()) return false;
        write(stack, ability, storage(stack, ability, viewer).with(slot, value, capacity), viewer);
        return true;
    }

    static void replace(ItemStack stack, Holder<Ability> ability, List<ItemStack> contents, Player viewer) {
        int capacity = contents.size();
        List<ItemStack> previous = storage(stack, ability, viewer).contents();
        List<ItemStack> next = new ArrayList<>(contents);
        // A reduced capacity hides slots without erasing their contents.
        if (previous.size() > capacity) next.addAll(previous.subList(capacity, previous.size()));
        write(stack, ability, ContainerDataStorage.of(next.size(), next), viewer);
    }

    public boolean mayAccess(Provider access, ItemStack stack, Holder<Ability> ability, Player viewer) {
        return this.capacity(access, stack, ability, viewer) > 0;
    }

    int capacity(Provider access, ItemStack stack, Holder<Ability> ability, Player viewer) {
        if (!hasAccess(access, stack, ability, viewer)) return 0;
        return this.slots(access, stack, ability, FormulaContexts.forEntity(viewer));
    }

    public static boolean hasAccess(Provider access, ItemStack stack, Holder<Ability> ability, Player viewer) {
        return !viewer.level().isClientSide() && !stack.isEmpty() && (shared(ability) || stack.getCount() == 1)
                && ArtifactService.abilities(access, stack).contains(ability)
                && ArtifactService.definition(access, stack).filter(artifact -> ArtifactService.mayUse(stack, artifact, viewer.getUUID())).isPresent();
    }

    private static boolean shared(Holder<Ability> ability) {
        return ability.value().type() instanceof StorageAbilityType storage && storage.shared();
    }

    private static void write(ItemStack stack, Holder<Ability> ability, ContainerDataStorage contents, Player viewer) {
        if (shared(ability)) {
            viewer.getData(MxtAttachments.SHARED_STORAGE).set(HolderHelper.id(ability), contents);
        } else {
            stack.set(DataComponents.MAX_STACK_SIZE, 1);
            ItemStorageService.set(stack, ability, contents);
        }
    }

    static ContainerDataStorage storage(ItemStack stack, Holder<Ability> ability, Player viewer) {
        if (shared(ability)) return viewer.getData(MxtAttachments.SHARED_STORAGE).get(HolderHelper.id(ability));
        return ItemStorageService.get(stack, ability, ContainerDataStorage.class).orElse(ContainerDataStorage.INSTANCE);
    }
}
