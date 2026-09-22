package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.compat.CuriosIntegration;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.action.builtin.item.ConsumeHealthItemAction;
import com.iafenvoy.mxt.data.action.builtin.item.meta.SequenceItemAction;
import com.iafenvoy.mxt.data.artifact.ArtifactStateComponent;
import com.iafenvoy.mxt.data.artifact.ForgingResultComponent;
import com.iafenvoy.mxt.data.artifact.ItemAbilitiesComponent;
import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.artifact.ability.FlightArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.UpkeepArtifactAbility;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.event.ArtifactRefineEvent.Post;
import com.iafenvoy.mxt.event.ArtifactRefineEvent.Pre;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.energy.ArtifactSpiritEnergy;
import com.iafenvoy.mxt.runtime.energy.ISpiritEnergy;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.PlayerNames;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ownership, aura and resolution for artifact ItemStacks.
 *
 * <p>Everything reads the registries the caller hands in rather than the running server: the same questions are
 * asked on a client, where the server-side entry points of {@code MxtDatapackRegistries} throw.</p>
 */
public final class ArtifactService {
    /** How much of its declared ceiling one full feeding is worth. Kept as a share, so no pack number enters it. */
    private static final double NOURISHMENT_CAPACITY_BONUS = 0.5D;
    /** A stored value above one says nothing the ceiling does not, so it is clamped wherever it is read. */
    private static final double MAX_NOURISHMENT = 1.0D;
    /** A chest's own grid: nine to a row, and six rows is as tall as the container background goes. */
    public static final int STORAGE_COLUMNS = 9;
    public static final int MAX_STORAGE_ROWS = 6;
    /**
     * The most slots one artifact can have. A layout limit rather than a balance one: storage is drawn as a chest,
     * so a definition asking for more than six rows could never be shown, and the frame refuses to hold what it
     * cannot open.
     */
    public static final int MAX_STORAGE_SLOTS = STORAGE_COLUMNS * MAX_STORAGE_ROWS;

    private ArtifactService() {
    }

    /**
     * The definition claiming this stack, with the holder so its id stays available. The highest
     * {@code priority} wins and registry order breaks ties; two definitions claiming one item is reported by
     * {@code ServerCache} while the pack loads.
     */
    public static Optional<Reference<Artifact>> definition(Provider access, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return MxtDatapackRegistries.holders(access, MxtResourceKeys.ARTIFACT)
                .filter(holder -> holder.value().entries().stream().anyMatch(entry -> entry.matches(stack)))
                .min(Comparator.comparingInt(holder -> holder.value().priority()));
    }

    /**
     * Writes ownership of this stack to one entity, and records what that owner is called while it is known.
     *
     * <p>The name is written here rather than looked up later because this is the only moment the owner is in
     * hand: a tooltip is drawn on a client that may have no way to turn a UUID back into a name - the player may
     * be offline, or connected to a server that never told it - so the readable half of the answer is stored
     * with the authoritative half. It stays display-only; every ownership question is asked of the UUID.</p>
     */
    public static RefineResult refine(ItemStack stack, Entity owner) {
        UUID ownerUuid = owner.getUUID();
        if (NeoForge.EVENT_BUS.post(new Pre(stack, ownerUuid)).isCanceled())
            return RefineResult.CANCELLED;
        ArtifactStateComponent current = state(stack);
        String requestedOwner = ownerUuid.toString();
        if (current.ownerUuid().isPresent() && !current.ownerUuid().get().equals(requestedOwner))
            return RefineResult.OWNED_BY_OTHER;
        stack.set(MxtDataComponents.ARTIFACT_STATE, current.withOwner(requestedOwner, PlayerNames.displayName(owner)));
        NeoForge.EVENT_BUS.post(new Post(stack, ownerUuid));
        // What claiming does - which includes what it costs, because the default of that action is the price.
        // It runs only for a binding that was really written, and it is deliberately not checked against the
        // holder first: refusing a claim the price would kill is not this method's business, and every writer of
        // a binding pays the same price.
        definition(owner.level().registryAccess(), stack).ifPresent(holder ->
                holder.value().claimAction().execute(owner, stack, FormulaContext.of(owner)));
        return RefineResult.REFINED;
    }

    public static boolean isOwner(ItemStack stack, UUID owner) {
        return state(stack).ownerUuid().filter(owner.toString()::equals).isPresent();
    }

    /** Whether the stack has been refined at all, whoever it belongs to. */
    public static boolean hasOwner(ItemStack stack) {
        return state(stack).ownerUuid().isPresent();
    }

    /**
     * Whether one entity may use an artifact where ownership is read - flight, and the storage. A definition
     * asking for an owner refuses until it has one; one that does not is open to anybody until it is refined and
     * answers to its owner alone from then on. Refining therefore stays worth doing without being a prerequisite
     * nothing in the game can currently satisfy.
     *
     * <p>{@code mxt:owned_by} is not routed through here: that condition asks whether the owner <em>is</em> the
     * holder, which is a question a pack asks on purpose and has one answer regardless of this flag.</p>
     */
    public static boolean mayUse(ItemStack stack, Holder<Artifact> artifact, UUID user) {
        if (isOwner(stack, user)) return true;
        return !artifact.value().requireOwner() && !hasOwner(stack);
    }

    /** The ceiling on a declared hold: past this the number is a pack mistake rather than a longer gesture. */
    public static final int MAX_HOLD_TICKS = 72_000;

    /**
     * How long a definition asks to be held down. Zero means it does not take the gesture over at all, which is
     * how a pack turns the long press off for one artifact; anything past {@link #MAX_HOLD_TICKS} is clamped.
     */
    public static int holdTicks(Artifact artifact, FormulaContext context) {
        return (int) Math.clamp(Math.floor(evaluate(artifact.holdTicks(), context)), 0.0D, MAX_HOLD_TICKS);
    }

    public static int holdTicks(Provider access, ItemStack stack, FormulaContext context) {
        return definition(access, stack).map(holder -> holdTicks(holder.value(), context)).orElse(0);
    }

    /**
     * What claiming this stack charges in health, which is what the tooltip and the action bar repeat. Nothing
     * is decided by it: the price is charged by {@code claim_action} when it runs, and whether the holder
     * survives it is not asked. A plainly stated price (a bare {@code mxt:consume_health}, or the sum of every
     * such entry of a top-level {@code mxt:sequence} - the list form every action field accepts) is what this
     * reads; a price buried behind {@code mxt:chance} or {@code mxt:if_else} reads as nothing, and the lines
     * that quote a number then simply say nothing.
     */
    public static double claimHealthCost(Provider access, ItemStack stack, FormulaContext context) {
        return definition(access, stack)
                .map(holder -> statedPrice(holder.value().claimAction(), context))
                .orElse(0.0D);
    }

    /**
     * The health price an action states, if it states one plainly. Only the two shapes that always run are read:
     * the charging action itself, and the sequence a written list becomes, whose plainly stated prices add up
     * because every entry of it runs. Anything conditional states nothing here.
     */
    private static double statedPrice(ItemAction action, FormulaContext context) {
        if (action instanceof ConsumeHealthItemAction(NumberProvider amount))
            return Math.max(0.0D, evaluate(amount, context));
        if (action instanceof SequenceItemAction(List<ItemAction> actions))
            return actions.stream().mapToDouble(inner -> statedPrice(inner, context)).sum();
        return 0.0D;
    }

    /**
     * Whether the definition's own condition lets this holder claim the stack. Asked by the long press before
     * it settles anything; {@link #refine} deliberately does not, because it is also the entry point of the loot
     * function and of scripts, where the pack has already decided who the owner is.
     */
    public static boolean mayClaim(Provider access, ItemStack stack, Entity holder, FormulaContext context) {
        return definition(access, stack)
                .map(holder_ -> holder_.value().claimCondition().test(holder, context))
                .orElse(false);
    }

    /** The periodic price this stack charges whoever carries it, if its definition declares one. */
    public static Optional<UpkeepArtifactAbility> upkeep(Provider access, ItemStack stack) {
        return definition(access, stack).flatMap(holder -> holder.value().upkeep());
    }

    /**
     * Whether at least one declared aura has room left, which is what makes a pour worth starting - the question
     * a hold answers before it takes a click over.
     */
    public static boolean hasRoom(Provider access, ItemStack stack, FormulaContext context) {
        Artifact artifact = definition(access, stack).map(Reference::value).orElse(null);
        if (artifact == null) return false;
        for (Holder<Aura> aura : artifact.spiritCapacity().keySet())
            if (capacity(access, stack, aura, 0.0D, context) > stored(stack, aura)) return true;
        return false;
    }

    /**
     * The abilities this stack offers: what its definition grants plus whatever the component was written with,
     * so an artifact, a scripted stack and a plain stack all reach the ability runtime the same way.
     */
    public static List<Holder<Ability>> abilities(Provider access, ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        LinkedHashSet<Holder<Ability>> granted = new LinkedHashSet<>();
        definition(access, stack).ifPresent(holder -> granted.addAll(resolveAbilities(access, holder.value().grantedAbilities())));
        ItemAbilitiesComponent component = stack.getOrDefault(MxtDataComponents.ITEM_ABILITIES.get(), new ItemAbilitiesComponent(List.of()));
        component.abilities().forEach(id -> MxtDatapackRegistries.holder(access, MxtResourceKeys.ABILITY, id).ifPresent(granted::add));
        return List.copyOf(granted);
    }

    /**
     * Expands the ids and tags of one grant entry into the abilities they name, in the order they were written.
     * The runtime reading and the tooltip both come through here, so a stack cannot grant one set of abilities
     * while its tooltip describes another.
     */
    public static List<Holder<Ability>> resolveAbilities(Provider access,
                                                        Collection<Either<Holder<Ability>, TagKey<Ability>>> values) {
        RegistryLookup<Ability> abilities = access.lookupOrThrow(MxtResourceKeys.ABILITY);
        List<Holder<Ability>> resolved = new ArrayList<>();
        for (Either<Holder<Ability>, TagKey<Ability>> value : values)
            value.ifLeft(resolved::add)
                    .ifRight(tag -> abilities.listElements().filter(holder -> holder.is(tag)).forEach(resolved::add));
        return List.copyOf(resolved);
    }

    public static List<Identifier> abilityIds(Provider access, ItemStack stack) {
        return abilities(access, stack).stream().map(HolderHelper::id).toList();
    }

    public static Optional<FlightArtifactAbility> flight(Provider access, ItemStack stack) {
        return definition(access, stack).flatMap(holder -> holder.value().flight());
    }

    /**
     * How many slots this artifact's storage has: what its definition declares, rounded up to a whole row and cut
     * at the six rows a chest-shaped screen can draw.
     *
     * <p>The rounding is what lets one number serve as both the capacity and the screen's size - a declaration of
     * ten slots would otherwise show eighteen cells of which eight silently refuse to hold anything. A definition
     * that declares more than {@link #MAX_STORAGE_SLOTS} is cut to it rather than keeping slots nothing can open.</p>
     */
    public static int storageSlots(Provider access, ItemStack stack, FormulaContext context) {
        int declared = definition(access, stack).flatMap(holder -> holder.value().storage())
                .map(storage -> (int) Math.clamp(Math.floor(evaluate(storage.slots(), context)), 0.0D, (double) Integer.MAX_VALUE))
                .orElse(0);
        if (declared <= 0) return 0;
        int rows = Math.clamp((declared + STORAGE_COLUMNS - 1) / STORAGE_COLUMNS, 0, MAX_STORAGE_ROWS);
        return rows * STORAGE_COLUMNS;
    }

    public static boolean curiosEquipable(Provider access, ItemStack stack) {
        return definition(access, stack).map(holder -> holder.value().curiosEquipable()).orElse(false);
    }

    /**
     * The stack on this player that one definition claims right now, wherever they keep it: both hands, the rest
     * of the inventory, and the Curios slots.
     *
     * <p>Deliberately wider than what a wheel page reads. A page answers "what does the item in this slot offer",
     * and follows that slot; a screen that is already open has to keep working while the artifact is moved around
     * the inventory, and has to stop the moment the artifact leaves the player - because what it writes into would
     * otherwise be a stack nobody carries, which is how items disappear.</p>
     */
    public static Optional<ItemStack> carried(Provider access, Player player, Identifier artifactId) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            if (holds(access, player.getInventory().getItem(slot), artifactId))
                return Optional.of(player.getInventory().getItem(slot));
        for (ItemStack stack : CuriosIntegration.equippedLive(player))
            if (holds(access, stack, artifactId)) return Optional.of(stack);
        return Optional.empty();
    }

    private static boolean holds(Provider access, ItemStack stack, Identifier artifactId) {
        if (stack == null || stack.isEmpty()) return false;
        return definition(access, stack).map(holder -> HolderHelper.id(holder).equals(artifactId)).orElse(false);
    }

    /**
     * The aura this stack can hold of one kind: what its definition declares, plus the bonus it has earned by
     * being fed. A stack that claims no definition, or one that does not name this aura, falls back to the
     * caller's capacity - so a caller with its own ceiling keeps working and an unmentioned aura stays
     * unstorable.
     */
    public static int capacity(Provider access, ItemStack stack, Holder<Aura> aura, double fallbackCapacity, FormulaContext context) {
        double declared = definition(access, stack)
                .map(holder -> holder.value().spiritCapacity().get(aura))
                .map(provider -> evaluate(provider, context))
                .orElse(0.0D);
        if (!Double.isFinite(declared) || declared <= 0.0D) declared = fallbackCapacity;
        if (!Double.isFinite(declared) || declared <= 0.0D) return 0;
        double resolved = declared * (1.0D + NOURISHMENT_CAPACITY_BONUS * Math.clamp(state(stack).nourishment(), 0.0D, MAX_NOURISHMENT));
        // Saturate rather than hand an infinity to a store that would have to reject it.
        return Double.isFinite(resolved) ? (int) Math.clamp(Math.floor(resolved), 0.0D, Integer.MAX_VALUE) : Integer.MAX_VALUE;
    }

    public static int stored(ItemStack stack, Holder<Aura> aura) {
        return store(stack).get(aura);
    }

    public static void setEnergy(ItemStack stack, Holder<Aura> aura, int value) {
        stack.set(MxtDataComponents.SPIRIT_STORAGE, store(stack).with(aura, Math.max(0, value)));
    }

    /**
     * Fills one aura of an artifact and reports how much was really taken. What was accepted also feeds the
     * artifact, so nourishment is only ever earned by a charge the server performed.
     */
    public static int addEnergy(Provider access, ItemStack stack, Holder<Aura> aura, double amount,
                                double fallbackCapacity, FormulaContext context) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return 0;
        int capacity = capacity(access, stack, aura, fallbackCapacity, context);
        int stored = stored(stack, aura);
        int accepted = (int) Math.min(Math.floor(amount), Math.max(0, capacity - stored));
        if (accepted <= 0) return 0;
        setEnergy(stack, aura, stored + accepted);
        if (capacity > 0) feed(stack, (double) accepted / (double) capacity);
        return accepted;
    }

    public static int consumeEnergy(ItemStack stack, Holder<Aura> aura, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return 0;
        int stored = stored(stack, aura);
        int extracted = (int) Math.min(Math.floor(amount), Math.max(0, stored));
        if (extracted > 0) setEnergy(stack, aura, stored - extracted);
        return extracted;
    }

    /**
     * Raises nourishment by the share of one feeding an accepted write filled. Never lowered: an artifact keeps
     * what it was fed after the aura it took in is spent again.
     */
    private static void feed(ItemStack stack, double share) {
        if (!Double.isFinite(share) || share <= 0.0D) return;
        ArtifactStateComponent current = state(stack);
        double raised = Math.clamp(current.nourishment() + share, 0.0D, MAX_NOURISHMENT);
        if (raised <= current.nourishment()) return;
        stack.set(MxtDataComponents.ARTIFACT_STATE, current.withNourishment(raised));
    }

    public static ArtifactStateComponent state(ItemStack stack) {
        return stack.getOrDefault(MxtDataComponents.ARTIFACT_STATE, ArtifactStateComponent.empty());
    }

    public static ISpiritEnergy energyStorage(ItemStack stack, Holder<Aura> aura, double capacity) {
        return new ArtifactSpiritEnergy(stack, aura, capacity);
    }

    /** Writes immutable server-computed forge provenance to a completed item. */
    public static void applyForgingResult(ItemStack stack, ForgingResultComponent result) {
        stack.set(MxtDataComponents.FORGING_RESULT, result);
    }

    private static SpiritStorageComponent store(ItemStack stack) {
        return stack.getOrDefault(MxtDataComponents.SPIRIT_STORAGE, SpiritStorageComponent.EMPTY);
    }

    /**
     * What one provider declares, or zero when it evaluates to something a store could not use. A non-finite
     * result is reported by the provider instead of quietly becoming a capacity.
     */
    private static double evaluate(NumberProvider provider, FormulaContext context) {
        double value = provider.evaluate(context);
        return provider.assertFinite(value) ? value : 0.0D;
    }

    public enum RefineResult {REFINED, OWNED_BY_OTHER, CANCELLED}
}
