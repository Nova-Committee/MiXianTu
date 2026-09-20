package com.iafenvoy.mxt.runtime.damage;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The one path every strike this mod deals takes, from "who hit whom with what" to "how much the target
 * really loses". It is deliberately two layers, because the two halves of a hit belong to two different
 * entities and must be answerable from either side alone:
 *
 * <ol>
 *   <li><b>Shaping</b> ({@link #outgoing}) runs where the hit is dealt, on the attacker's side: the value the
 * data pack wrote is multiplied by the caster's mastery ({@code damage_multiplier}), by the element affinity
 * of the roots the casting was adapted to ({@code element_modifier}), by the attacker's own physiques
 * ({@code damage_dealt_multiplier}) and by the element relation the attacker's spirit roots hold over the
 * target's. Everything this layer needs is known here - the caster, the target, and the ability being cast -
 * which is exactly what a defender-side hook cannot see.</li>
 *   <li><b>Reduction</b> ({@link #incoming}) runs on the target through {@link DamageEventBridge}, on the
 *   defence's side: the element the target is {@code adapted_to} softens (or worsens) what arrives, and the
 *   target's own physiques ({@code damage_taken_multiplier}) answer for it. Living there rather than here is
 *   what makes it cover every source that reaches the entity, a mob's swing and a fall included, and it is
 *   also why the layer must never be applied twice - the incoming event fires exactly once per damage
 *   sequence, whoever dealt it.</li>
 * </ol>
 *
 * <p>The two layers therefore never duplicate work: this class computes layer one and hands the damage over,
 * and the event computes layer two. Anything that only wants to know what a hit is worth without applying it
 * calls the layer directly, which is what the audit and the test-mod probe do.</p>
 *
 * <p>The cultivation identity of the two parties enters here and only here, which is what keeps a spirit root
 * and a physique from being two different kinds of thing to a damage number. A root speaks through its element
 * (both layers, from the definitions' own multipliers) and through its {@code element_ability_modifier}, which
 * a casting exposes as {@code element_modifier} and this class applies - so a pack writes an elemental damage
 * formula as the number it means, instead of multiplying the affinity in by hand. A physique speaks through
 * {@code damage_dealt_multiplier} and {@code damage_taken_multiplier}, which are deliberately element-free: a
 * physique is about what a body is, not about what it is made of.</p>
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
    /**
     * The formula value a casting ability adapted to an element exposes: the {@code element_ability_modifier}
     * of the caster's matching spirit roots. Layer one applies it, so a pack does not have to remember it in
     * every damage formula - and a pack that wants the same number for something other than damage reads the
     * same name off the same context.
     */
    public static final String ELEMENT_MODIFIER = "element_modifier";

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
     * <p>A pack's own number is the base, and every multiplication is read from data: the mastery of the
     * casting ability's chain, the element affinity of the roots the casting was adapted to, the attacker's own
     * physiques, and the element edges the attacker's roots hold over the target's. A result that is not a
     * positive finite number is reported as no damage at all, because a formula that overflowed or produced
     * nonsense must not reach the health of an entity.</p>
     *
     * <p>The mastery and affinity factors belong to the casting and the element edges and physiques to the
     * attacker, which is a distinction that shows on damage a caster deals to itself: a technique's own backlash
     * is still that technique's damage and is scaled by its level and by the affinity it was cast with, while it
     * has no second party to hold an element edge against and therefore reads no relation at all - but the
     * caster's own physique does still speak for it, because that is a property of the body dealing the blow
     * rather than of the pair.</p>
     *
     * <p>The elements are passed in rather than derived here, because they are the one part of a strike that
     * depends on how it was declared: a claimed damage type names them, and only in its absence are they the
     * attacker's roots ({@link DamageElements#strike}). The four-argument overload is that fallback reading,
     * for a hit that carries no damage type of its own.</p>
     */
    public static double outgoing(@Nullable Entity attacker, Entity target, double amount, @Nullable FormulaContext context,
                                  Set<Holder<Element>> elements) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return 0.0D;
        double result = amount * masteryMultiplier(context) * elementMultiplier(context)
                * physiqueMultiplier(attacker, true) * overcomeMultiplier(elements, target);
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
     * roots has nothing to be adapted with, so both read as an unchanged amount. A target with a physique is
     * answered for by it all the same, because "what this body takes" is a property of the body and of nothing
     * about where the blow came from.
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
        double result = amount * adaptationMultiplier(target, attacking) * physiqueMultiplier(target, false);
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
     * The element affinity multiplier the casting ability put on its formula context, or one when the context
     * carries none - a casting that names no element, or damage that came from somewhere other than a casting,
     * has no affinity to scale by.
     *
     * <p>The number itself is the spirit roots': a casting adapted to an element reads the
     * {@code element_ability_modifier} of the caster's matching roots, averaged or taken best as the ability
     * asked for, and a caster with no matching root never reaches this point - the cast was refused before any
     * damage existed. An affinity of zero is a root that says "nothing comes through me", and it is honoured
     * rather than read as "absent", the same way the cast gate honours it.</p>
     */
    public static double elementMultiplier(@Nullable FormulaContext context) {
        if (context == null) return 1.0D;
        double value = context.explicit(ELEMENT_MODIFIER);
        return Double.isFinite(value) && value >= 0.0D ? value : 1.0D;
    }

    /**
     * What the entity's own physiques are worth to one side of a hit: {@code damage_dealt_multiplier} for the
     * blow it deals, {@code damage_taken_multiplier} for the blow it receives. Several physiques multiply,
     * because each is its own source of the effect, and an entity with none - or with none switched on - reads
     * as an unchanged amount.
     *
     * <p>A multiplier is evaluated against the holder's own formula context, never the other party's: what a
     * body takes cannot depend on who is asking, and a physique is a property of that body. The context is only
     * built when a provider actually needs one, so a physique whose numbers are written constants costs a
     * lookup rather than a context on every strike; a provider that throws or produces nonsense contributes
     * nothing, which is the same rule the passive attribute path follows for the same kind of formula.</p>
     */
    public static double physiqueMultiplier(@Nullable Entity entity, boolean dealt) {
        if (!(entity instanceof LivingEntity holder)) return 1.0D;
        SpiritIdentityAttachment spirit = holder.getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
        if (spirit == null) return 1.0D;
        List<Holder<Physique>> physiques = spirit.activePhysiques();
        if (physiques.isEmpty()) return 1.0D;
        FormulaContext context = null;
        double result = 1.0D;
        for (Holder<Physique> physique : physiques) {
            NumberProvider provider = dealt
                    ? physique.value().damageDealtMultiplier() : physique.value().damageTakenMultiplier();
            if (provider == null) continue;
            final double value;
            if (provider instanceof Constant constant) value = constant.value();
            else {
                if (context == null) context = FormulaContext.of(holder);
                try {
                    value = provider.evaluate(context);
                } catch (RuntimeException exception) {
                    continue;
                }
            }
            if (Double.isFinite(value) && value >= 0.0D) result *= value;
        }
        return Double.isFinite(result) ? result : 1.0D;
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
