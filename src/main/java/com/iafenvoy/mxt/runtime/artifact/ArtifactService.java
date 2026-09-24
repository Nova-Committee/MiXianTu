package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.action.builtin.item.ConsumeHealthItemAction;
import com.iafenvoy.mxt.data.action.builtin.item.meta.SequenceItemAction;
import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.artifact.ArtifactStateComponent;
import com.iafenvoy.mxt.data.artifact.ForgingResultComponent;
import com.iafenvoy.mxt.data.artifact.ItemAbilitiesComponent;
import com.iafenvoy.mxt.data.ability.Abilities;
import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.type.FlightAbilityType;
import com.iafenvoy.mxt.data.ability.type.StorageAbilityType;
import com.iafenvoy.mxt.data.ability.type.UpkeepAbilityType;
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
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;

/**
 * Ownership, aura and resolution for artifact ItemStacks. Everything reads the registries the caller hands in
 * rather than the running server, because the same questions are asked on a client, where the server-side entry
 * points of {@code MxtDatapackRegistries} throw.
 */
public final class ArtifactService {
    // A share of the declared ceiling, so no pack number enters it.
    private static final double NOURISHMENT_CAPACITY_BONUS = 0.5D;
    // A stored value above one says nothing the ceiling does not, so it is clamped wherever it is read.
    private static final double MAX_NOURISHMENT = 1.0D;
    // A chest's own grid: nine to a row, and six rows is as tall as the container background goes.
    public static final int STORAGE_COLUMNS = 9;
    public static final int MAX_STORAGE_ROWS = 6;
    // A layout limit rather than a balance one: storage is drawn as a chest, so a definition asking for more than
    // six rows could never be shown and the frame refuses to hold what it cannot open.
    public static final int MAX_STORAGE_SLOTS = STORAGE_COLUMNS * MAX_STORAGE_ROWS;

    private ArtifactService() {
    }

    // Highest priority wins, registry order breaks ties. Two definitions claiming one item is reported by
    // ServerCache while the pack loads.
    public static Optional<Reference<Artifact>> definition(Provider access, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return MxtDatapackRegistries.holders(access, MxtResourceKeys.ARTIFACT)
                .filter(holder -> holder.value().entries().stream().anyMatch(entry -> entry.matches(stack)))
                .min(Comparator.comparingInt(holder -> holder.value().priority()));
    }

    // The owner's name is written here because this is the only moment the owner is in hand: a tooltip is drawn
    // on a client that may have no way to turn a UUID back into a name (offline player, a server that never told
    // it). Display-only - every ownership question is asked of the UUID.
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
        // Runs only for a binding that was really written, and deliberately not checked against the holder first:
        // refusing a claim the price would kill is not this method's business, and every writer of a binding pays
        // the same price.
        definition(owner.level().registryAccess(), stack).ifPresent(holder ->
                holder.value().claimAction().execute(owner, stack, FormulaContext.of(owner)));
        return RefineResult.REFINED;
    }

    public static boolean isOwner(ItemStack stack, UUID owner) {
        return state(stack).ownerUuid().filter(owner.toString()::equals).isPresent();
    }

    public static boolean hasOwner(ItemStack stack) {
        return state(stack).ownerUuid().isPresent();
    }

    // Refuses until refined only when require_owner; otherwise open to anybody until refined, and answering to its
    // owner alone from then on. mxt:owned_by is not routed through here - it asks whether the owner *is* the
    // holder, which has one answer regardless of this flag.
    public static boolean mayUse(ItemStack stack, Holder<Artifact> artifact, UUID user) {
        if (isOwner(stack, user)) return true;
        return !artifact.value().requireOwner() && !hasOwner(stack);
    }

    // Past this the number is a pack mistake rather than a longer gesture.
    public static final int MAX_HOLD_TICKS = 72_000;

    // Zero means the definition does not take the gesture over at all, which is how a pack turns the long press
    // off for one artifact.
    public static int holdTicks(Artifact artifact, FormulaContext context) {
        return (int) Math.clamp(Math.floor(evaluate(artifact.holdTicks(), context)), 0.0D, MAX_HOLD_TICKS);
    }

    public static int holdTicks(Provider access, ItemStack stack, FormulaContext context) {
        return definition(access, stack).map(holder -> holdTicks(holder.value(), context)).orElse(0);
    }

    // Display only - the price is charged by claim_action when it runs, and whether the holder survives it is not
    // asked. A price buried behind mxt:chance or mxt:if_else reads as nothing, so a line quoting it says nothing.
    public static double claimHealthCost(Provider access, ItemStack stack, FormulaContext context) {
        return definition(access, stack)
                .map(holder -> statedPrice(holder.value().claimAction(), context))
                .orElse(0.0D);
    }

    // Only the two shapes that always run are read: the charging action itself, and the sequence a written list
    // becomes, whose plainly stated prices add up. Anything conditional states nothing here.
    private static double statedPrice(ItemAction action, FormulaContext context) {
        if (action instanceof ConsumeHealthItemAction(NumberProvider amount))
            return Math.max(0.0D, evaluate(amount, context));
        if (action instanceof SequenceItemAction(List<ItemAction> actions))
            return actions.stream().mapToDouble(inner -> statedPrice(inner, context)).sum();
        return 0.0D;
    }

    // Asked by the long press before it settles anything; refine deliberately does not, because it is also the
    // entry point of the loot function and of scripts, where the pack has already decided who the owner is.
    public static boolean mayClaim(Provider access, ItemStack stack, Entity holder, FormulaContext context) {
        return definition(access, stack)
                .map(holder_ -> holder_.value().claimCondition().test(holder, context))
                .orElse(false);
    }

    /** The periodic price this stack charges whoever carries it, with the id that price is stored under. */
    public static Optional<Upkeep> upkeep(Provider access, ItemStack stack) {
        return abilities(access, stack).stream()
                .filter(ref -> ref.value().type() instanceof UpkeepAbilityType)
                .findFirst()
                .map(ref -> new Upkeep(ref, (UpkeepAbilityType) ref.value().type()));
    }

    public record Upkeep(Holder<Ability> ability, UpkeepAbilityType type) {
    }

    // What makes a pour worth starting - the question a hold answers before it takes a click over.
    public static boolean hasRoom(Provider access, ItemStack stack, FormulaContext context) {
        Artifact artifact = definition(access, stack).map(Reference::value).orElse(null);
        if (artifact == null) return false;
        for (Holder<Aura> aura : artifact.spiritCapacity().keySet())
            if (capacity(access, stack, aura, 0.0D, context) > stored(stack, aura)) return true;
        return false;
    }

    // The definition's grants plus whatever the component was written with, so an artifact, a scripted stack and
    // a plain stack all reach the ability runtime the same way. Every entry is addressed by the id the runtime
    // keys its grant, its cooldowns and its state by, and a tag stands for the abilities it lists.
    public static List<Holder<Ability>> abilities(Provider access, ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        LinkedHashSet<Holder<Ability>> granted = new LinkedHashSet<>();
        definition(access, stack).ifPresent(holder -> granted.addAll(RegistryCodecs.resolve(holder.value().abilities(), access, MxtResourceKeys.ABILITY).toList()));
        ItemAbilitiesComponent component = stack.getOrDefault(MxtDataComponents.ITEM_ABILITIES.get(), new ItemAbilitiesComponent(List.of()));
        component.abilities().forEach(id -> Abilities.resolve(access, id).ifPresent(granted::add));
        return List.copyOf(granted);
    }

    public static List<Identifier> abilityIds(Provider access, ItemStack stack) {
        return abilities(access, stack).stream().map(HolderHelper::id).toList();
    }

    // How this stack's mount is drawn; empty for a stack whose definition declares no flight at all.
    public static Optional<FlightAbilityType> flight(Provider access, ItemStack stack) {
        return first(access, stack, FlightAbilityType.class);
    }

    // Rounded up to a whole row and cut at the six rows a chest-shaped screen can draw, which is what lets one
    // number serve as both the capacity and the screen's size: a declaration of ten slots would otherwise show
    // eighteen cells of which eight silently refuse to hold anything.
    public static int storageSlots(Provider access, ItemStack stack, FormulaContext context) {
        return first(access, stack, StorageAbilityType.class).map(storage -> slotsOf(storage, context)).orElse(0);
    }

    // The first declared ability of a kind: a definition may name several, so "the storage" is a question about
    // the type rather than about a dedicated field. A tag contributes its members in registry order.
    private static <T extends AbilityType> Optional<T> first(Provider access, ItemStack stack, Class<T> kind) {
        return abilities(access, stack).stream()
                .map(ref -> ref.value().type())
                .filter(kind::isInstance)
                .map(kind::cast)
                .findFirst();
    }

    // The entry's own slot count, for a caller that already knows which ability it is holding.
    public static int storageSlots(ItemStack stack, Holder<Ability> ability, FormulaContext context) {
        return ability.value().type() instanceof StorageAbilityType storage ? slotsOf(storage, context) : 0;
    }

    private static int slotsOf(StorageAbilityType storage, FormulaContext context) {
        int declared = (int) Math.clamp(Math.floor(evaluate(storage.slots(), context)), 0.0D, Integer.MAX_VALUE);
        if (declared <= 0) return 0;
        int rows = Math.clamp((declared + STORAGE_COLUMNS - 1) / STORAGE_COLUMNS, 0, MAX_STORAGE_ROWS);
        return rows * STORAGE_COLUMNS;
    }

    public static boolean curiosEquipable(Provider access, ItemStack stack) {
        return definition(access, stack).map(holder -> holder.value().curiosEquipable()).orElse(false);
    }

    // Wherever the holder keeps it: both hands and the Curios slots for anything living, plus the whole inventory
    // when the holder is a player. Deliberately wider than what a wheel page reads - an open screen has to keep
    // working while the artifact is moved around the inventory, and has to stop the moment it leaves the holder,
    // because what it writes into would otherwise be a stack nobody carries, which is how items disappear.
    public static Optional<ItemStack> carried(Provider access, LivingEntity holder, Identifier abilityId) {
        for (ItemStack stack : ArtifactUpkeepService.carried(holder))
            if (holds(access, stack, abilityId)) return Optional.of(stack);
        if (holder instanceof Player player)
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (holds(access, stack, abilityId)) return Optional.of(stack);
            }
        return Optional.empty();
    }

    // Whether this stack offers that ability; the same reading the grant ledger uses, so an item and its
    // abilities cannot disagree about what it holds.
    private static boolean holds(Provider access, ItemStack stack, Identifier abilityId) {
        if (stack == null || stack.isEmpty()) return false;
        return abilities(access, stack).stream().anyMatch(ref -> HolderHelper.id(ref).equals(abilityId));
    }

    // What the definition declares plus the bonus earned by being fed. A stack claiming no definition, or one
    // that does not name this aura, falls back to the caller's capacity - so a caller with its own ceiling keeps
    // working and an unmentioned aura stays unstorable.
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

    // What was accepted also feeds the artifact, so nourishment is only ever earned by a charge the server
    // performed.
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

    // Never lowered: an artifact keeps what it was fed after the aura it took in is spent again.
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

    // Immutable, server-computed forge provenance.
    public static void applyForgingResult(ItemStack stack, ForgingResultComponent result) {
        stack.set(MxtDataComponents.FORGING_RESULT, result);
    }

    private static SpiritStorageComponent store(ItemStack stack) {
        return stack.getOrDefault(MxtDataComponents.SPIRIT_STORAGE, SpiritStorageComponent.EMPTY);
    }

    // Zero when the provider evaluates to something a store could not use; a non-finite result is reported by the
    // provider instead of quietly becoming a capacity.
    private static double evaluate(NumberProvider provider, FormulaContext context) {
        double value = provider.evaluate(context);
        return provider.assertFinite(value) ? value : 0.0D;
    }

    public enum RefineResult {REFINED, OWNED_BY_OTHER, CANCELLED}
}
