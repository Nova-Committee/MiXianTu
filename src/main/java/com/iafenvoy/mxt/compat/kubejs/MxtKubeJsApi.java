package com.iafenvoy.mxt.compat.kubejs;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment.State;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.Abilities;
import com.iafenvoy.mxt.data.ability.Togglable;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.data.trigger.TriggerContext;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Reason;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityActivationService;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.ability.AbilityService.UseResult;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService.BreakthroughResult;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService.Failure;
import com.iafenvoy.mxt.runtime.cultivation.CultivationToggleService;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.iafenvoy.mxt.runtime.curse.CurseService.ApplyFailure;
import com.iafenvoy.mxt.runtime.curse.CurseService.ApplyResult;
import com.iafenvoy.mxt.runtime.element.ElementReactionService;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Result;
import com.iafenvoy.mxt.runtime.trigger.TriggerDispatcher;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.runtime.world.AuraWorldAttachment.Area;
import com.iafenvoy.mxt.runtime.world.AuraWorldAttachment.Shape;
import com.iafenvoy.mxt.runtime.world.SoulService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Optional-script boundary. It exposes validated operations, never attachment internals.
 */
public final class MxtKubeJsApi {
    private MxtKubeJsApi() {
    }

    public static Optional<Ability> ability(@NotNull Identifier id) {
        return MxtDatapackRegistries.get(MxtResourceKeys.ABILITY, id);
    }

    public static Optional<Curse> curse(@NotNull Identifier id) {
        return MxtDatapackRegistries.get(MxtResourceKeys.CURSE, id);
    }

    public static UseResult useAbility(@NotNull Entity actor, Identifier id, FormulaContext context) {
        if (actor.level().isClientSide())
            return new UseResult(false, false, AbilityService.Failure.SERVER_ONLY, null, Map.of());
        Holder<Ability> ability = Abilities.resolve(actor.level().registryAccess(), id).orElse(null);
        if (ability == null)
            return new UseResult(false, false, AbilityService.Failure.NOT_GRANTED, null, Map.of());
        return AbilityService.use(ability, actor, actor.getData(MxtAttachments.ABILITY_HOLDER),
                actor.getData(MxtAttachments.RESOURCE_HOLDER), actor.level().getGameTime(), context);
    }

    /**
     * Presses a named ability, whatever kind it is: a cast, a switch or something that opens a window. The
     * carrier is the stack the script names, and an ability that acts on an item refuses without one.
     */
    public static Togglable.Result activateAbility(@NotNull LivingEntity holder, Identifier id, @Nullable ItemStack carrier) {
        Holder<Ability> ability = Abilities.resolve(holder.level().registryAccess(), id).orElse(null);
        if (ability == null || holder.level().isClientSide())
            return Togglable.Result.refused(Togglable.Failure.UNAVAILABLE);
        return AbilityActivationService.activate(holder, ability, carrier);
    }

    /**
     * Adds one source's claim, the only way an ability is granted. An unknown ability answers {@code false}
     * instead of throwing.
     */
    public static boolean grantAbility(@NotNull Entity entity, Identifier id, Identifier source) {
        if (entity.level().isClientSide()) return false;
        if (Abilities.resolve(entity.level().registryAccess(), id).isEmpty()) return false;
        AbilityAttachment attachment = entity.getData(MxtAttachments.ABILITY_HOLDER);
        return changed(entity, attachment.grant(id, source));
    }

    /**
     * Drops one source's claim; the ability goes only with its last source, and its cooldowns and stored values
     * go with it. An unknown ability answers {@code false}.
     */
    public static boolean revokeAbility(@NotNull Entity entity, Identifier id, Identifier source) {
        if (entity.level().isClientSide()) return false;
        if (findAbility(entity, id).isEmpty()) return false;
        AbilityAttachment attachment = entity.getData(MxtAttachments.ABILITY_HOLDER);
        return changed(entity, attachment.revoke(id, source));
    }

    /**
     * Read from the attachment, not the registry: a definition that was disabled or deleted still answers.
     */
    public static boolean hasAbility(@NotNull Entity entity, Identifier id) {
        return findAbility(entity, id).isPresent();
    }

    /**
     * Every ability the entity holds, sorted; read from the attachment, as {@link #hasAbility}.
     */
    public static List<String> abilities(@NotNull Entity entity) {
        return abilityKeys(entity).map(Identifier::toString).sorted().toList();
    }

    /**
     * Which sources keep that ability granted, empty when it is not held.
     */
    public static List<String> abilitySources(@NotNull Entity entity, Identifier id) {
        return findAbility(entity, id)
                .map(held -> entity.getData(MxtAttachments.ABILITY_HOLDER).sources().of(held).stream()
                        .map(Identifier::toString).sorted().toList())
                .orElseGet(List::of);
    }

    // A source change moves which triggers the entity listens for, so the runtime index is rebuilt.
    private static boolean changed(Entity entity, boolean changed) {
        if (changed && entity instanceof LivingEntity living)
            AbilityEventBridge.rebuildTriggerSubscriptions(living);
        return changed;
    }

    // The ledger is the only source of truth a lookup uses; a client script reads nothing.
    private static Stream<Identifier> abilityKeys(Entity entity) {
        if (entity.level().isClientSide()) return Stream.empty();
        return entity.getData(MxtAttachments.ABILITY_HOLDER).sources().keys().stream();
    }

    private static Optional<Identifier> findAbility(Entity entity, Identifier id) {
        return abilityKeys(entity).filter(held -> held.equals(id)).findFirst();
    }

    public static ApplyResult applyCurse(@NotNull Entity target, Identifier id, int stacks, Identifier source, FormulaContext context) {
        if (target.level().isClientSide())
            return new ApplyResult(null, false, ApplyFailure.SERVER_ONLY);
        Holder<Curse> curse = MxtDatapackRegistries.holder(MxtResourceKeys.CURSE, id).orElse(null);
        if (curse == null) return new ApplyResult(null, false, ApplyFailure.CONDITION);
        return CurseService.apply(target, curse, stacks, target.level().getGameTime(), context, source);
    }

    public static boolean removeCurse(@NotNull Entity target, Identifier id) {
        return !target.level().isClientSide() && MxtDatapackRegistries.holder(MxtResourceKeys.CURSE, id)
                .map(curse -> CurseService.remove(target, curse, Reason.EXPLICIT, target.level().getGameTime()).isPresent()).orElse(false);
    }

    /**
     * Lets go of one source's claim; the curse goes only when no other source holds it.
     */
    public static boolean releaseCurse(@NotNull Entity target, Identifier id, Identifier source) {
        return !target.level().isClientSide() && findCurseHolder(target, id)
                .map(curse -> CurseService.release(target, curse, source, Reason.EXPLICIT,
                        target.level().getGameTime(), FormulaContext.of(target)).isPresent()).orElse(false);
    }

    /**
     * Which sources keep that curse alive, empty when it is not held.
     */
    public static Set<Identifier> curseSources(@NotNull Entity target, Identifier id) {
        return findCurseHolder(target, id)
                .map(curse -> target.getData(MxtAttachments.CURSE_HOLDER).sources().of(curse))
                .orElseGet(Set::of);
    }

    /**
     * {@link #applyCurse} with a duration the definition may shorten but never be outlasted by.
     */
    public static ApplyResult applyCurseFor(@NotNull Entity target, Identifier id, int stacks, Identifier source,
                                            long durationTicks, FormulaContext context) {
        if (target.level().isClientSide()) return new ApplyResult(null, false, ApplyFailure.SERVER_ONLY);
        if (durationTicks < 0L) throw new IllegalArgumentException("Curse duration must not be negative");
        return MxtDatapackRegistries.holder(MxtResourceKeys.CURSE, id)
                .map(curse -> CurseService.applyWithDuration(target, curse, stacks, target.level().getGameTime(),
                        context, source, Optional.of(durationTicks)))
                .orElseGet(() -> new ApplyResult(null, false, ApplyFailure.UNKNOWN));
    }

    /**
     * Read from the attachment, not the registry: a definition that was disabled or deleted still answers.
     */
    public static boolean hasCurse(@NotNull Entity target, Identifier id) {
        return findCurse(target, id).isPresent();
    }

    public static int curseStacks(@NotNull Entity target, Identifier id) {
        return findCurse(target, id).map(State::stacks).orElse(0);
    }

    /**
     * Ticks left on that curse, {@code -1} when it never expires, {@code 0} when the entity does not hold it.
     */
    public static long curseRemainingTicks(@NotNull Entity target, Identifier id) {
        return findCurse(target, id)
                .map(state -> state.expiresAt() < 0L ? -1L : Math.max(0L, state.expiresAt() - target.level().getGameTime()))
                .orElse(0L);
    }

    private static Optional<State> findCurse(Entity target, Identifier id) {
        return findCurseHolder(target, id)
                .map(curse -> target.getData(MxtAttachments.CURSE_HOLDER).instances().get(curse));
    }

    private static Optional<Holder<Curse>> findCurseHolder(Entity target, Identifier id) {
        if (target.level().isClientSide()) return Optional.empty();
        for (Holder<Curse> curse : target.getData(MxtAttachments.CURSE_HOLDER).instances().keySet())
            if (HolderHelper.id(curse).equals(id)) return Optional.of(curse);
        return Optional.empty();
    }

    /**
     * Server-only; lets a rescue integration complete an explicit soul recovery.
     */
    public static boolean reclaimSoul(@NotNull Entity entity) {
        return !entity.level().isClientSide() && SoulService.reclaim(entity);
    }

    /**
     * The live elements the entity's spirit roots name, sorted. Asked of the body, not the registry, so it works
     * without knowing which roots exist.
     */
    public static List<String> elements(@NotNull Entity entity) {
        return Elements.of(entity).stream().map(HolderHelper::id).map(Identifier::toString).sorted().toList();
    }

    public static boolean hasElement(@NotNull Entity entity, Identifier id) {
        return Elements.of(entity).stream().anyMatch(element -> HolderHelper.id(element).equals(id));
    }

    /**
     * How much of one element has built up on the entity, readable on either side because the accumulation is a
     * synchronised attachment. A disabled or unknown element answers {@code 0}, the same rule the
     * {@code mxt:element_attachment} condition follows.
     */
    public static double elementAmount(@NotNull Entity entity, Identifier id) {
        return MxtDatapackRegistries.holder(entity.level().registryAccess(), MxtResourceKeys.ELEMENT, id)
                .map(element -> ElementReactionService.amount(entity, element)).orElse(0.0D);
    }

    /**
     * Builds one element up on the entity and answers the new total, through the pipeline a strike uses, so a
     * reaction can fire here. A negative amount wears the buildup off; a disabled or unknown element changes
     * nothing.
     */
    public static double attachElement(@NotNull Entity entity, Identifier id, double amount) {
        if (entity.level().isClientSide() || !Double.isFinite(amount) || amount == 0.0D) return 0.0D;
        Holder<Element> element = MxtDatapackRegistries.holder(MxtResourceKeys.ELEMENT, id).orElse(null);
        if (element == null) return 0.0D;
        ElementReactionService.apply(entity, element, amount, FormulaContext.of(entity));
        return ElementReactionService.amount(entity, element);
    }

    /**
     * Every spirit root the entity holds, sorted. Read off the body, so a root a pack disabled or deleted is
     * still reported and {@link #removeSpiritRoot} by that name still takes it off. This is the held list; the
     * roots that count right now are {@link #activeSpiritRoots}.
     */
    public static List<String> spiritRoots(@NotNull Entity entity) {
        SpiritIdentityAttachment spirit = identity(entity);
        return spirit == null ? List.of() : spirit.spiritRoots().stream()
                .map(HolderHelper::id).map(Identifier::toString).sorted().toList();
    }

    /**
     * The held roots that count, sorted: a root switched off, or whose element is not live, is left out. The
     * element is resolved against the entity's level registry, so an unresolvable or disabled definition answers
     * false instead of throwing, on either side.
     */
    public static List<String> activeSpiritRoots(@NotNull Entity entity) {
        SpiritIdentityAttachment spirit = identity(entity);
        if (spirit == null) return List.of();
        return spirit.activeSpiritRoots().stream()
                .filter(root -> MxtDatapackRegistries.get(entity.level().registryAccess(), MxtResourceKeys.SPIRIT_ROOT,
                                HolderHelper.id(root))
                        .map(value -> Elements.enabled(value.element())).orElse(false))
                .map(HolderHelper::id).map(Identifier::toString).sorted().toList();
    }

    public static boolean hasSpiritRoot(@NotNull Entity entity, Identifier id) {
        return foundSpiritRoot(entity, id) != null;
    }

    /**
     * Whether that held root is switched on; a root not held answers {@code false} too, so a script that needs
     * to tell the two apart asks {@link #hasSpiritRoot} as well.
     */
    public static boolean isSpiritRootEnabled(@NotNull Entity entity, Identifier id) {
        SpiritIdentityAttachment spirit = identity(entity);
        Holder<SpiritRoot> root = foundSpiritRoot(entity, id);
        return spirit != null && root != null && spirit.isSpiritRootEnabled(root);
    }

    /**
     * Grants one spirit root through the service the data pack action uses, so conflict rules and granted
     * abilities behave identically. Unknown or disabled definitions are refused, not granted by name.
     */
    public static CultivationIdentityService.Result grantSpiritRoot(@NotNull LivingEntity entity, Identifier id) {
        if (entity.level().isClientSide())
            return new CultivationIdentityService.Result(false, CultivationIdentityService.Failure.SERVER_ONLY);
        Holder<SpiritRoot> root = MxtDatapackRegistries.holder(MxtResourceKeys.SPIRIT_ROOT, id).orElse(null);
        return root == null
                ? new CultivationIdentityService.Result(false, CultivationIdentityService.Failure.DISABLED)
                : CultivationIdentityService.grantSpiritRoot(entity, id, root.value());
    }

    public static boolean removeSpiritRoot(@NotNull LivingEntity entity, Identifier id) {
        return !entity.level().isClientSide() && CultivationIdentityService.removeSpiritRoot(entity, id);
    }

    /**
     * Switches a held root on or off without giving it up.
     */
    public static CultivationToggleService.Result setSpiritRootEnabled(@NotNull LivingEntity entity, Identifier id, boolean enabled) {
        if (entity.level().isClientSide())
            return new CultivationToggleService.Result(false, CultivationToggleService.Failure.SERVER_ONLY);
        Holder<SpiritRoot> root = foundSpiritRoot(entity, id);
        return root == null
                ? new CultivationToggleService.Result(false, CultivationToggleService.Failure.NOT_HELD)
                : CultivationToggleService.setSpiritRootEnabled(entity, root, enabled);
    }

    /**
     * Every physique the entity holds, sorted; read off the body, as {@link #spiritRoots}.
     */
    public static List<String> physiques(@NotNull Entity entity) {
        SpiritIdentityAttachment spirit = identity(entity);
        return spirit == null ? List.of() : spirit.physiques().stream()
                .map(HolderHelper::id).map(Identifier::toString).sorted().toList();
    }

    public static List<String> activePhysiques(@NotNull Entity entity) {
        SpiritIdentityAttachment spirit = identity(entity);
        return spirit == null ? List.of() : spirit.activePhysiques().stream()
                .map(HolderHelper::id).map(Identifier::toString).sorted().toList();
    }

    public static boolean hasPhysique(@NotNull Entity entity, Identifier id) {
        return foundPhysique(entity, id) != null;
    }

    public static boolean isPhysiqueEnabled(@NotNull Entity entity, Identifier id) {
        SpiritIdentityAttachment spirit = identity(entity);
        Holder<Physique> physique = foundPhysique(entity, id);
        return spirit != null && physique != null && spirit.isPhysiqueEnabled(physique);
    }

    /**
     * Grants one physique through the service the data pack action uses, including its holder condition and
     * exclusive tags, read against the entity as it is right now.
     */
    public static CultivationIdentityService.Result grantPhysique(@NotNull LivingEntity entity, Identifier id) {
        if (entity.level().isClientSide())
            return new CultivationIdentityService.Result(false, CultivationIdentityService.Failure.SERVER_ONLY);
        Holder<Physique> physique = MxtDatapackRegistries.holder(MxtResourceKeys.PHYSIQUE, id).orElse(null);
        return physique == null
                ? new CultivationIdentityService.Result(false, CultivationIdentityService.Failure.DISABLED)
                : CultivationIdentityService.grantPhysique(entity, id, physique.value(), FormulaContext.of(entity));
    }

    public static boolean removePhysique(@NotNull LivingEntity entity, Identifier id) {
        return !entity.level().isClientSide() && CultivationIdentityService.removePhysique(entity, id);
    }

    public static CultivationToggleService.Result setPhysiqueEnabled(@NotNull LivingEntity entity, Identifier id, boolean enabled) {
        if (entity.level().isClientSide())
            return new CultivationToggleService.Result(false, CultivationToggleService.Failure.SERVER_ONLY);
        Holder<Physique> physique = foundPhysique(entity, id);
        return physique == null
                ? new CultivationToggleService.Result(false, CultivationToggleService.Failure.NOT_HELD)
                : CultivationToggleService.setPhysiqueEnabled(entity, physique, enabled);
    }

    // Read, never created: asking whether a body holds a root must not leave it holding an empty identity.
    private static SpiritIdentityAttachment identity(Entity entity) {
        return entity.getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
    }

    private static Holder<SpiritRoot> foundSpiritRoot(Entity entity, Identifier id) {
        SpiritIdentityAttachment spirit = identity(entity);
        if (spirit == null) return null;
        for (Holder<SpiritRoot> root : spirit.spiritRoots())
            if (HolderHelper.id(root).equals(id)) return root;
        return null;
    }

    private static Holder<Physique> foundPhysique(Entity entity, Identifier id) {
        SpiritIdentityAttachment spirit = identity(entity);
        if (spirit == null) return null;
        for (Holder<Physique> physique : spirit.physiques())
            if (HolderHelper.id(physique).equals(id)) return physique;
        return null;
    }

    public static BreakthroughResult tryBreakthrough(@NotNull LivingEntity entity, @NotNull Identifier auraId, FormulaContext context) {
        if (entity.level().isClientSide())
            return new BreakthroughResult(false, Failure.SERVER_ONLY, null, Map.of());
        if (MxtDatapackRegistries.get(MxtResourceKeys.AURA, auraId).isEmpty())
            return new BreakthroughResult(false, Failure.DISABLED, null, Map.of());
        return CultivationService.attempt(entity, entity.getData(MxtAttachments.CULTIVATION),
                entity.getData(MxtAttachments.RESOURCE_HOLDER), auraId, context, () -> true);
    }

    /**
     * Adds non-negative cultivation only; a script cannot set arbitrary negative or non-finite state.
     */
    public static boolean addCultivation(LivingEntity entity, Identifier auraId, double amount) {
        if (entity == null || entity.level().isClientSide() || !Double.isFinite(amount) || amount < 0.0D) return false;
        Holder<Aura> aura = MxtDatapackRegistries.holder(MxtResourceKeys.AURA, auraId).orElse(null);
        if (aura == null) return false;
        CultivationService.addProgressForChain(entity, aura, amount, FormulaContext.of(entity));
        return true;
    }

    /**
     * The same all-or-nothing resource transaction abilities and other server systems use.
     */
    public static Result tryConsumeResources(Entity entity, List<Cost> costs, FormulaContext context) {
        if (!(entity instanceof LivingEntity payer) || entity.level().isClientSide())
            return new Result(false, null, Map.of());
        CostTransaction.PayResult payment = CostTransaction.pay(costs, CostContext.of(payer, context, CostOrigin.SCRIPT));
        return new Result(payment.paid(), payment.paid() ? null : payment.failedResource(), payment.resources());
    }

    public static AuraResult aura(Level level, BlockPos position) {
        return AuraService.getPositionAura(level, position);
    }

    /**
     * Publishes a server-authoritative custom trigger signal; finite numeric values are also added to the formula
     * context. The actor and level cannot be spoofed by the script.
     */
    public static boolean publishTrigger(@NotNull Entity actor, @NotNull Identifier signal,
                                         Map<String, Object> values) {
        if (actor.level().isClientSide()) return false;
        TriggerContext context = new TriggerContext().actor(actor).level(actor.level());
        FormulaContext formula = FormulaContext.of(actor);
        if (values != null)
            for (Entry<String, Object> entry : values.entrySet()) {
                context.set(entry.getKey(), entry.getValue());
                if (entry.getValue() instanceof Number number && Double.isFinite(number.doubleValue()))
                    formula = formula.with(entry.getKey(), number.doubleValue());
            }
        context.formula(formula);
        TriggerDispatcher.publish(signal, context, actor.level().getGameTime());
        return true;
    }

    public static String addAuraBox(Level level, Identifier zone, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int priority) {
        if (!(level instanceof ServerLevel server) || MxtDatapackRegistries.get(MxtResourceKeys.AURA_ZONE, zone).isEmpty())
            throw new IllegalArgumentException("Aura areas require a loaded server aura_zone");
        return server.getData(MxtAttachments.AURA_WORLD).add(new Area(zone, new Shape(minX, minY, minZ, maxX, maxY, maxZ), priority));
    }

    public static boolean removeAuraArea(Level level, String id) {
        return level instanceof ServerLevel server && server.getData(MxtAttachments.AURA_WORLD).remove(id);
    }
}
