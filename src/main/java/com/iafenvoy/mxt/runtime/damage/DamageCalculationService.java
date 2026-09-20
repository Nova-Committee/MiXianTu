package com.iafenvoy.mxt.runtime.damage;

import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * The one path every strike this mod deals takes, from "who hit whom with what" to "how much the target
 * really loses". It is deliberately two layers, because the two halves of a hit belong to two different
 * entities and must be answerable from either side alone:
 *
 * <ol>
 *   <li><b>Shaping</b> ({@link #outgoing}) runs where the hit is dealt, on the attacker's side: the value the
 * data pack wrote is multiplied by the caster's mastery ({@code damage_multiplier}) and by the element
 * relation the attacker's spirit roots hold over the target's. Everything this layer needs is known here -
 * the caster, the target, and the ability being cast - which is exactly what a defender-side hook cannot
 * see.</li>
 *   <li><b>Reduction</b> ({@link #incoming}) runs on the target through {@link DamageEventBridge}, on the
 *   defence's side: the element the target is {@code adapted_to} softens (or worsens) what arrives. Living
 *   there rather than here is what makes it cover every source that reaches the entity, a mob's swing and a
 *   fall included, and it is also why the layer must never be applied twice - the incoming event fires
 *   exactly once per damage sequence, whoever dealt it.</li>
 * </ol>
 *
 * <p>The two layers therefore never duplicate work: this class computes layer one and hands the damage over,
 * and the event computes layer two. Anything that only wants to know what a hit is worth without applying it
 * calls the layer directly, which is what the audit and the test-mod probe do.</p>
 *
 * <p>Element strength lives in the element definitions ({@code overcomes[].multiplier} /
 * {@code adapted_to[].multiplier}), so this class owns no number of its own and carries no balance data. Which
 * elements a strike belongs to is read from its damage type by {@link DamageElements}, falling back to the
 * attacker's spirit roots - and both layers read that one answer, so a condition, a shape and a reduction can
 * never disagree about what a hit was made of.</p>
 */
public final class DamageCalculationService {
    /**
     * The formula value a casting ability exposes for layer one. A pack may read it in its own formulas, and
     * an ability that belongs to no mastery chain simply does not set it, which reads as "no mastery bonus".
     */
    public static final String DAMAGE_MULTIPLIER = "damage_multiplier";

    private DamageCalculationService() {
    }

    /**
     * Shapes one hit and applies it, returning the amount handed to the target, or zero when nothing was.
     *
     * <p>The source is built here rather than by the caller so attribution is not each call site's own
     * decision: a declared damage type is used verbatim with the attacker recorded as the cause, and without
     * one the vanilla player/mob attack source is chosen, which is what makes a kill count as the attacker's.
     * The reduction layer is not applied here - it runs in the incoming event, which this call raises.</p>
     *
     * @param attacker the entity credited with the hit, or null for damage that belongs to nobody
     * @param context  the formula context the hit was evaluated in, or null when there is none
     */
    public static double deal(@Nullable Entity attacker, Entity target, double amount,
                              Optional<Holder<DamageType>> damageType, @Nullable FormulaContext context) {
        if (!(target.level() instanceof ServerLevel level)) return 0.0D;
        Set<Holder<Element>> elements = DamageElements.strike(level, damageType, attacker);
        double shaped = outgoing(attacker, target, amount, context, elements);
        if (shaped <= 0.0D) return 0.0D;
        target.hurtServer(level, source(level, attacker, damageType), (float) shaped);
        return shaped;
    }

    /**
     * Layer one: the amount this attacker forms against this target, before the target answers for it.
     *
     * <p>A pack's own number is the base, and both multiplications are read from data - the mastery of the
     * casting ability's chain, and the element edges the attacker's roots hold over the target's. A result
     * that is not a positive finite number is reported as no damage at all, because a formula that overflowed
     * or produced nonsense must not reach the health of an entity.</p>
     *
     * <p>The mastery factor belongs to the casting and the element factor to the attacker, which is a
     * distinction that shows on damage a caster deals to itself: a technique's own backlash is still that
     * technique's damage and is scaled by its level, while it has no second party to hold an element edge
     * against and therefore reads no relation at all.</p>
     *
     * <p>The elements are passed in rather than derived here, because they are the one part of a strike that
     * depends on how it was declared: a claimed damage type names them, and only in its absence are they the
     * attacker's roots ({@link DamageElements#strike}). The four-argument overload is that fallback reading,
     * for a hit that carries no damage type of its own.</p>
     */
    public static double outgoing(@Nullable Entity attacker, Entity target, double amount, @Nullable FormulaContext context,
                                  Set<Holder<Element>> elements) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return 0.0D;
        double result = amount * masteryMultiplier(context) * overcomeMultiplier(elements, target);
        return Double.isFinite(result) && result > 0.0D ? result : 0.0D;
    }

    /**
     * {@link #outgoing(Entity, Entity, double, FormulaContext, Set)} for a hit that declares no damage type:
     * the attacker's own spirit-root elements are the strike's elements.
     */
    public static double outgoing(@Nullable Entity attacker, Entity target, double amount, @Nullable FormulaContext context) {
        return outgoing(attacker, target, amount, context, attacker == null ? Set.of() : Elements.of(attacker));
    }

    /**
     * Layer two: the amount the target really takes from a hit of this size. The element comes from the
     * source, which is what makes this layer cover a lava tick or another mod's sword as readily as one of our
     * own hits; a source with no attacker and no claimed damage type carries no element, and an entity with no
     * roots has nothing to be adapted with, so both read as an unchanged amount.
     */
    public static double incoming(LivingEntity target, DamageSource source, double amount) {
        return incoming(target, DamageElements.strike(source), amount);
    }

    /**
     * {@link #incoming(LivingEntity, DamageSource, double)} for a caller that holds only the attacker: the
     * strike's elements are that attacker's roots.
     */
    public static double incoming(LivingEntity target, @Nullable Entity attacker, double amount) {
        return incoming(target, attacker == null ? Set.of() : Elements.of(attacker), amount);
    }

    /**
     * The reduction layer with the strike's elements already resolved, for a caller that needs the same answer
     * twice - the incoming-damage event reduces with them and then builds the element up on the target, and
     * reading the source once is what keeps those two in step.
     */
    public static double incoming(LivingEntity target, Set<Holder<Element>> attacking, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return 0.0D;
        double result = amount * adaptationMultiplier(target, attacking);
        return Double.isFinite(result) && result > 0.0D ? result : 0.0D;
    }

    /**
     * What the striking elements are worth against the target's, as one multiplier. Every matching edge
     * multiplies, so two elements that both overcome the target both count, and the numbers a pack wrote are
     * the whole of the result.
     */
    public static double overcomeMultiplier(Set<Holder<Element>> attacking, Entity target) {
        if (!(target instanceof LivingEntity defender)) return 1.0D;
        Set<Holder<Element>> defending = Elements.of(defender);
        if (attacking.isEmpty() || defending.isEmpty()) return 1.0D;
        double result = 1.0D;
        for (Holder<Element> source : attacking)
            for (Holder<Element> victim : defending)
                result *= source.value().overcomeMultiplier(victim);
        return result;
    }

    /**
     * What the target's own relations are worth against the striking elements, as one multiplier. Only the
     * defender's {@code adapted_to} is read: being overcome is the attacker's advantage, not a second edge on
     * the receiving side.
     */
    public static double adaptationMultiplier(LivingEntity target, Set<Holder<Element>> attacking) {
        Set<Holder<Element>> defending = Elements.of(target);
        if (attacking.isEmpty() || defending.isEmpty()) return 1.0D;
        double result = 1.0D;
        for (Holder<Element> victim : defending)
            for (Holder<Element> source : attacking)
                result *= victim.value().adaptationMultiplier(source);
        return result;
    }

    /**
     * The mastery multiplier the casting ability put on its formula context, or one when the context carries
     * none. A value a pack somehow wrote is not trusted: it is only read as a bonus while it is finite and
     * non-negative, which is the same rule the definition was validated under.
     */
    public static double masteryMultiplier(@Nullable FormulaContext context) {
        if (context == null) return 1.0D;
        double value = context.explicit(DAMAGE_MULTIPLIER);
        return Double.isFinite(value) && value >= 0.0D ? value : 1.0D;
    }

    /**
     * The damage source a strike of this kind is credited with. With no declared type this is the vanilla
     * reading of "the attacker hit it" - {@code playerAttack} carries the player as attacker, which is what
     * makes a kill count as theirs - and with no attacker it falls back to the anonymous generic source.
     */
    public static DamageSource source(Level level, @Nullable Entity attacker, Optional<Holder<DamageType>> damageType) {
        return damageType
                .map(type -> new DamageSource(type, attacker))
                .orElseGet(() -> switch (attacker) {
                    case Player player -> level.damageSources().playerAttack(player);
                    // An attacker is a player in play, but the rule is written for any living attacker.
                    case LivingEntity living -> level.damageSources().mobAttack(living);
                    case null, default -> level.damageSources().generic();
                });
    }
}
