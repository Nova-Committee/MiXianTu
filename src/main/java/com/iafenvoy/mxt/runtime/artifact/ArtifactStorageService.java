package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.data.artifact.ArtifactStorageComponent;
import com.iafenvoy.mxt.data.artifact.ItemArchetype;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Default storage implementation for datapack-defined artifacts with positive {@code storage_slots}.
 */
public final class ArtifactStorageService implements ISpiritStorage {
    public static final ArtifactStorageService INSTANCE = new ArtifactStorageService();
    private static final int MAX_SLOTS = 256;

    private ArtifactStorageService() {
    }

    @Override
    public int slots(ItemStack stack) {
        return this.slots(stack, FormulaContext.EMPTY);
    }

    public int slots(ItemStack stack, FormulaContext context) {
        return ArtifactService.state(stack).archetype().flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.ITEM_ARCHETYPE, id))
                .map(definition -> configuredSlots(definition, context))
                .orElse(0);
    }

    @Override
    public ItemStack get(ItemStack stack, int slot, Player viewer) {
        if (!this.mayAccess(stack, viewer)) return ItemStack.EMPTY;
        int capacity = this.slots(stack, FormulaContexts.forEntity(viewer));
        if (slot < 0 || slot >= capacity) return ItemStack.EMPTY;
        return storage(stack).get(slot);
    }

    @Override
    public boolean set(ItemStack stack, int slot, ItemStack value, Player viewer) {
        if (!this.mayAccess(stack, viewer)) return false;
        int capacity = this.slots(stack, FormulaContexts.forEntity(viewer));
        if (slot < 0 || slot >= capacity || value.getCount() > value.getMaxStackSize()) return false;
        stack.set(MxtDataComponents.ARTIFACT_STORAGE, storage(stack).with(slot, value, capacity));
        return true;
    }

    public boolean mayAccess(ItemStack stack, Player viewer) {
        return !viewer.level().isClientSide() && ArtifactService.isOwner(stack, viewer.getUUID()) && this.slots(stack, FormulaContexts.forEntity(viewer)) > 0;
    }

    public static int configuredSlots(ItemArchetype definition, FormulaContext context) {
        double evaluated = definition.storageSlots().evaluate(context);
        if (!Double.isFinite(evaluated)) return 0;
        return Math.clamp((int) Math.floor(evaluated), 0, MAX_SLOTS);
    }

    private static ArtifactStorageComponent storage(ItemStack stack) {
        return Optional.ofNullable(stack.get(MxtDataComponents.ARTIFACT_STORAGE)).orElseGet(() -> new ArtifactStorageComponent(List.of()));
    }
}
