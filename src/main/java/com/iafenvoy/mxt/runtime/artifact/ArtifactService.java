package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.data.artifact.ArtifactStateData;
import com.iafenvoy.mxt.data.artifact.ForgingResultData;
import com.iafenvoy.mxt.event.ArtifactRefineEvent.Post;
import com.iafenvoy.mxt.event.ArtifactRefineEvent.Pre;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.runtime.behavior.BehaviorContext;
import com.iafenvoy.mxt.runtime.behavior.BehaviorContext.Kind;
import com.iafenvoy.mxt.runtime.behavior.DomainBehaviorService;
import com.iafenvoy.mxt.runtime.energy.ArtifactSpiritEnergy;
import com.iafenvoy.mxt.runtime.energy.ISpiritEnergy;
import com.iafenvoy.mxt.util.formula.FormulaContext;
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

    public static RefineResult refine(ItemStack stack, UUID owner) {
        if (NeoForge.EVENT_BUS.post(new Pre(stack, owner)).isCanceled())
            return RefineResult.CANCELLED;
        ArtifactStateData current = state(stack);
        String requestedOwner = owner.toString();
        if (current.ownerUuid().isPresent() && !current.ownerUuid().get().equals(requestedOwner))
            return RefineResult.OWNED_BY_OTHER;
        stack.set(MxtDataComponents.ARTIFACT_STATE, current.withOwner(requestedOwner));
        NeoForge.EVENT_BUS.post(new Post(stack, owner));
        current.archetype().flatMap(id -> MxtDatapackRegistries.get(MxtDatapackRegistries.ITEM_ARCHETYPE, id)).ifPresent(definition ->
                DomainBehaviorService.execute(MxtTypeRegistries.ARTIFACT_LIFECYCLE_BEHAVIOR, definition.refineBehavior(),
                        BehaviorContext.of(Kind.ARTIFACT_REFINE, current.archetype().orElseThrow(), null, FormulaContext.EMPTY, true)));
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

    public static ArtifactStateData state(ItemStack stack) {
        return Optional.ofNullable(stack.get(MxtDataComponents.ARTIFACT_STATE)).orElseGet(ArtifactStateData::empty);
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
    public static void applyForgingResult(ItemStack stack, ForgingResultData result) {
        stack.set(MxtDataComponents.FORGING_RESULT, result);
    }

    public enum RefineResult {REFINED, OWNED_BY_OTHER, CANCELLED}
}
