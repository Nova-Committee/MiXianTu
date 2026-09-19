package com.iafenvoy.mxt.compat.kubejs;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.data.trigger.TriggerContext;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Reason;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.ability.AbilityService.UseResult;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService.BreakthroughResult;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService.Failure;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.iafenvoy.mxt.runtime.curse.CurseService.ApplyFailure;
import com.iafenvoy.mxt.runtime.curse.CurseService.ApplyResult;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
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
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

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
        Holder<Ability> ability = MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, id).orElse(null);
        if (ability == null)
            return new UseResult(false, false, AbilityService.Failure.NOT_GRANTED, null, Map.of());
        return AbilityService.use(ability, ability.value(), actor, actor.getData(MxtAttachments.ABILITY_HOLDER),
                actor.getData(MxtAttachments.RESOURCE_HOLDER), actor.level().getGameTime(), context);
    }

    /**
     * Adds one source's claim on an ability, which is how every other module grants one. An unknown ability is
     * not an error: nothing can be held by a name the ledger does not have, so the answer is simply {@code false}.
     */
    public static boolean grantAbility(@NotNull Entity entity, Identifier id, Identifier source) {
        if (entity.level().isClientSide()) return false;
        AbilityAttachment attachment = entity.getData(MxtAttachments.ABILITY_HOLDER);
        return MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, id)
                .map(ability -> changed(entity, attachment.grant(ability, source))).orElse(false);
    }

    /**
     * Drops one source's claim. The ability itself only disappears when that was its last source, which is also
     * when its cooldowns and its stored values go; an unknown ability answers {@code false}.
     */
    public static boolean revokeAbility(@NotNull Entity entity, Identifier id, Identifier source) {
        if (entity.level().isClientSide()) return false;
        AbilityAttachment attachment = entity.getData(MxtAttachments.ABILITY_HOLDER);
        return findAbilityHolder(entity, id)
                .map(ability -> changed(entity, attachment.revoke(ability, source))).orElse(false);
    }

    /**
     * Whether the entity holds that ability, read from the attachment rather than from the registry, so an
     * ability whose definition was disabled or deleted still answers honestly.
     */
    public static boolean hasAbility(@NotNull Entity entity, Identifier id) {
        return findAbilityHolder(entity, id).isPresent();
    }

    /**
     * Every ability the entity holds, sorted, read from the attachment for the same reason as {@link #hasAbility}.
     */
    public static List<String> abilities(@NotNull Entity entity) {
        return abilityKeys(entity).map(HolderHelper::id).map(Identifier::toString).sorted().toList();
    }

    /**
     * Which sources keep that ability granted right now, empty when the entity does not hold it.
     */
    public static List<String> abilitySources(@NotNull Entity entity, Identifier id) {
        return findAbilityHolder(entity, id)
                .map(ability -> entity.getData(MxtAttachments.ABILITY_HOLDER).sources().of(ability).stream()
                        .map(Identifier::toString).sorted().toList())
                .orElseGet(List::of);
    }

    /**
     * A source change moves which triggers the entity listens for, so the runtime index is rebuilt whenever
     * something actually changed.
     */
    private static boolean changed(Entity entity, boolean changed) {
        if (changed && entity instanceof LivingEntity living)
            AbilityEventBridge.rebuildTriggerSubscriptions(living);
        return changed;
    }

    /**
     * What the entity's ability ledger actually holds, which is the only source of truth a lookup uses. A client
     * script reads nothing: a grant lives on the server, and the copy a client happens to hold is not what any
     * answer here should be based on, which is also what {@link #hasAbility} and {@link #abilitySources} answer.
     */
    private static Stream<Holder<Ability>> abilityKeys(Entity entity) {
        if (entity.level().isClientSide()) return Stream.empty();
        return entity.getData(MxtAttachments.ABILITY_HOLDER).sources().keys().stream();
    }

    private static Optional<Holder<Ability>> findAbilityHolder(Entity entity, Identifier id) {
        return abilityKeys(entity).filter(ability -> HolderHelper.id(ability).equals(id)).findFirst();
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
     * Lets go of one source's claim, which only removes the curse when no other source holds it.
     */
    public static boolean releaseCurse(@NotNull Entity target, Identifier id, Identifier source) {
        return !target.level().isClientSide() && findCurseHolder(target, id)
                .map(curse -> CurseService.release(target, curse, source, Reason.EXPLICIT,
                        target.level().getGameTime(), FormulaContext.of(target)).isPresent()).orElse(false);
    }

    /**
     * Which sources keep that curse alive right now, empty when the entity does not hold it.
     */
    public static Set<Identifier> curseSources(@NotNull Entity target, Identifier id) {
        return findCurseHolder(target, id)
                .map(curse -> target.getData(MxtAttachments.CURSE_HOLDER).sources().of(curse))
                .orElseGet(Set::of);
    }

    /**
     * Same as {@link #applyCurse}, with a duration the definition may shorten but never be outlasted by.
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
     * Whether the entity holds that curse, read from the attachment rather than from the registry, so a curse
     * whose definition was disabled or deleted still answers honestly.
     */
    public static boolean hasCurse(@NotNull Entity target, Identifier id) {
        return findCurse(target, id).isPresent();
    }

    public static int curseStacks(@NotNull Entity target, Identifier id) {
        return findCurse(target, id).map(CurseHolderAttachment.State::stacks).orElse(0);
    }

    /**
     * Ticks left on that curse, or {@code -1} when it never expires. A curse the entity does not hold answers
     * {@code 0}, so a script can tell the two apart.
     */
    public static long curseRemainingTicks(@NotNull Entity target, Identifier id) {
        return findCurse(target, id)
                .map(state -> state.expiresAt() < 0L ? -1L : Math.max(0L, state.expiresAt() - target.level().getGameTime()))
                .orElse(0L);
    }

    private static Optional<CurseHolderAttachment.State> findCurse(Entity target, Identifier id) {
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
     * Lets a rescue integration complete an explicit, server-authoritative soul recovery.
     */
    public static boolean reclaimSoul(@NotNull Entity entity) {
        return !entity.level().isClientSide() && SoulService.reclaim(entity);
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
     * Adds non-negative cultivation only; content scripts cannot set arbitrary negative or non-finite state.
     */
    public static boolean addCultivation(LivingEntity entity, Identifier auraId, double amount) {
        if (entity == null || entity.level().isClientSide() || !Double.isFinite(amount) || amount < 0.0D) return false;
        Holder<Aura> aura = MxtDatapackRegistries.holder(MxtResourceKeys.AURA, auraId).orElse(null);
        if (aura == null) return false;
        CultivationService.addProgressForChain(entity, aura, amount, FormulaContext.of(entity));
        return true;
    }

    /**
     * Performs the same all-or-nothing resource transaction used by abilities and other server systems.
     */
    public static Result tryConsumeResources(Entity entity, List<ResourceCost> costs, FormulaContext context) {
        if (entity == null || entity.level().isClientSide())
            return new Result(false, null, Map.of());
        try {
            return ResourceTransactions.tryConsume(entity instanceof LivingEntity living ? living : null,
                    entity.getData(MxtAttachments.RESOURCE_HOLDER),
                    ResourceTransactions.evaluate(costs, context));
        } catch (IllegalArgumentException exception) {
            return new Result(false, null, Map.of());
        }
    }

    public static AuraResult aura(Level level, BlockPos position) {
        return AuraService.getPositionAura(level, position);
    }

    /**
     * Publishes a server-authoritative custom trigger signal: finite numeric values are also added to
     * the formula context, and the actor and level cannot be spoofed.
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
