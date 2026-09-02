package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.data.artifact.ArtifactStateComponent;
import com.iafenvoy.mxt.data.artifact.ForgingResultComponent;
import com.iafenvoy.mxt.event.ArtifactRefineEvent.Post;
import com.iafenvoy.mxt.event.ArtifactRefineEvent.Pre;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.energy.ArtifactSpiritEnergy;
import com.iafenvoy.mxt.runtime.energy.ISpiritEnergy;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-side ownership and energy operations for artifact ItemStacks.
 */
public final class ArtifactService {
    private ArtifactService() {
    }

    public static RefineResult refine(ItemStack stack, Entity owner) {
        UUID ownerUuid = owner.getUUID();
        if (NeoForge.EVENT_BUS.post(new Pre(stack, ownerUuid)).isCanceled())
            return RefineResult.CANCELLED;
        ArtifactStateComponent current = state(stack);
        String requestedOwner = ownerUuid.toString();
        if (current.ownerUuid().isPresent() && !current.ownerUuid().get().equals(requestedOwner))
            return RefineResult.OWNED_BY_OTHER;
        stack.set(MxtDataComponents.ARTIFACT_STATE, current.withOwner(requestedOwner));
        NeoForge.EVENT_BUS.post(new Post(stack, ownerUuid));
        current.archetype().flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.ITEM_ARCHETYPE, id)).ifPresent(definition ->
                definition.refineAction().execute(owner, stack, FormulaContext.of(owner)));
        return RefineResult.REFINED;
    }

    public static boolean isOwner(ItemStack stack, UUID owner) {
        return state(stack).ownerUuid().filter(owner.toString()::equals).isPresent();
    }

    public static double addEnergy(ItemStack stack, double amount, double capacity) {
        return energyStorage(stack, capacity).receive(amount);
    }

    public static double consumeEnergy(ItemStack stack, double amount) {
        return new ArtifactSpiritEnergy(stack, Double.MAX_VALUE).extract(amount);
    }

    public static ArtifactStateComponent state(ItemStack stack) {
        return Optional.ofNullable(stack.get(MxtDataComponents.ARTIFACT_STATE)).orElseGet(ArtifactStateComponent::empty);
    }

    public static ISpiritEnergy energyStorage(ItemStack stack, double capacity) {
        return new ArtifactSpiritEnergy(stack, capacity);
    }

    public static void setEnergy(ItemStack stack, double energy) {
        stack.set(MxtDataComponents.ARTIFACT_STATE, state(stack).withEnergy(energy));
    }

    /**
     * Writes immutable server-computed forge provenance to a completed item.
     */
    public static void applyForgingResult(ItemStack stack, ForgingResultComponent result) {
        stack.set(MxtDataComponents.FORGING_RESULT, result);
    }

    public enum RefineResult {REFINED, OWNED_BY_OTHER, CANCELLED}
}
