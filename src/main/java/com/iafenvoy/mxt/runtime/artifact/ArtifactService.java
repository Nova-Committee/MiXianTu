package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.data.artifact.ArtifactStateComponent;
import com.iafenvoy.mxt.data.artifact.ForgingResultComponent;
import com.iafenvoy.mxt.data.artifact.ItemArchetype;
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
    /**
     * How much of its declared capacity an artifact gains by taking in one full feeding. Nourishment is kept as
     * the share of a feeding rather than as an amount of energy, so this constant alone decides what the ceiling
     * can become and no number a data pack writes can enter the bonus.
     */
    private static final double NOURISHMENT_CAPACITY_BONUS = 0.5D;
    /**
     * One full feeding is the whole of the nourishment an artifact can take in, so a stored value above one says
     * nothing the ceiling does not already say; it is clamped where it is read as well as where it is written,
     * because a save or a pack can put any finite number into the component.
     */
    private static final double MAX_NOURISHMENT = 1.0D;

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

    /**
     * Fills an artifact with the spirit energy it is handed and reports how much of it was really taken.
     * <p>
     * The capacity this write is measured against is the one the stack's own archetype declares, so the number
     * a data pack writes is what decides how much the artifact can hold. The caller's capacity is only the
     * fallback for a stack that resolves to no usable archetype, which is why a caller that declares its own
     * capacity keeps working unchanged.
     * <p>
     * Energy that was really accepted also feeds the artifact, so nourishment is only ever earned by a charge
     * the server performed and never handed to an item by writing the component.
     */
    public static double addEnergy(ItemStack stack, double amount, double fallbackCapacity, FormulaContext context) {
        double capacity = capacity(stack, fallbackCapacity, context);
        double accepted = energyStorage(stack, capacity).receive(amount);
        if (accepted > 0.0D) feed(stack, accepted, capacity);
        return accepted;
    }

    /**
     * The spirit energy one stack can hold: what its archetype declares, plus the bounded bonus the artifact has
     * earned by being fed.
     * <p>
     * A stack falls back to the caller's capacity when it names no archetype, when that archetype no longer
     * resolves - a datapack reload can remove an entry a save still points at - and when the declared number is
     * not a size a store could use. The field defaults to zero, so reading a zero declaration as "nothing
     * declared" is also what keeps a pack that adds an archetype for its abilities or its flight from silently
     * turning every charge path into a store that can never be filled. A caller capacity that is itself unusable
     * resolves to zero as well, because a store that cannot say how much it holds must refuse the energy rather
     * than throw out of the energy adapter.
     */
    public static double capacity(ItemStack stack, double fallbackCapacity, FormulaContext context) {
        ArtifactStateComponent state = state(stack);
        double base = state.archetype()
                .flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.ITEM_ARCHETYPE, id))
                .map(definition -> declaredCapacity(definition, context))
                .orElse(0.0D);
        if (!Double.isFinite(base) || base <= 0.0D) base = fallbackCapacity;
        if (!Double.isFinite(base) || base <= 0.0D) return 0.0D;
        double resolved = base * (1.0D + NOURISHMENT_CAPACITY_BONUS * Math.clamp(state.nourishment(), 0.0D, MAX_NOURISHMENT));
        // A declaration large enough for the bonus to overflow saturates rather than reaching the energy adapter
        // as an infinity, which the adapter would reject.
        return Double.isFinite(resolved) ? resolved : Double.MAX_VALUE;
    }

    /**
     * What one archetype declares as its capacity, or zero when the declaration evaluates to something a store
     * could not use. A non-finite result is reported by the provider rather than quietly becoming a capacity, so
     * a pack author can tell a formula that never produced a number from one that produced zero.
     */
    private static double declaredCapacity(ItemArchetype definition, FormulaContext context) {
        double declared = definition.spiritCapacity().evaluate(context);
        return definition.spiritCapacity().assertFinite(declared) ? declared : 0.0D;
    }

    /**
     * Raises one artifact's nourishment by the share of its capacity that this accepted write filled.
     * <p>
     * Measuring the gain against the capacity that accepted the energy is what bounds it: an artifact filled from
     * empty gains exactly one, a partial charge gains its fraction, and nothing but accepted energy moves the
     * number at all. It is never lowered here, because an artifact that was fed keeps what it was fed even after
     * the energy it took in is spent again.
     */
    private static void feed(ItemStack stack, double accepted, double capacity) {
        if (!Double.isFinite(accepted) || accepted <= 0.0D || !Double.isFinite(capacity) || capacity <= 0.0D) return;
        // Read back rather than reuse a snapshot taken before the write, because the accepted energy is already
        // in the component and withNourishment must carry it forward.
        ArtifactStateComponent state = state(stack);
        double raised = Math.clamp(state.nourishment() + accepted / capacity, 0.0D, MAX_NOURISHMENT);
        if (raised <= state.nourishment()) return;
        stack.set(MxtDataComponents.ARTIFACT_STATE, state.withNourishment(raised));
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
