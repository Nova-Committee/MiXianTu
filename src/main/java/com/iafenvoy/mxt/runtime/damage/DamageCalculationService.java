package com.iafenvoy.mxt.runtime.damage;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.data.item.ItemBinding;
import com.iafenvoy.mxt.data.item.WeaponBinding;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactUpkeepService;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.runtime.cultivation.ItemElements;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 *
 * <p>Two strikes are not scaled the same way as the rest. One is damage with no attacker: the mastery and the
 * affinity are still the casting's own and still apply - a technique's backlash is worse for a stronger
 * cultivator - but no second party is there to hold an element edge against, so the relation reads as one
 * rather than as the striker's own roots measured against themselves; see the shaping layer below. The other
 * is a damage type the pack listed in {@link #NO_BONUS}: that strike travels as the number it was handed, on
 * both layers, and leaves no element behind.</p>
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
    /**
     * Damage types this pipeline passes straight through. A strike whose type is tagged here is neither shaped
     * nor reduced and leaves no element behind: the number that was handed in is the number the target takes,
     * and only vanilla's own mitigation (armour, enchantments, resistance, absorption) still applies.
     *
     * <p>The tag lives on the vanilla {@code damage_type} registry, so it covers every source alike - our own
     * actions, another mod's sword and a vanilla hazard. The mod ships one entry, the void
     * ({@code minecraft:out_of_world}), because falling out of the world is an execution by position: nobody
     * is stronger for it and no body should be tougher against it. A pack may add its own types to the same
     * tag, or replace the file outright.</p>
     */
    public static final TagKey<DamageType> NO_BONUS =
            TagKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "no_bonus"));

    private DamageCalculationService() {
    }

    /**
     * Whether this strike is exempt from every bonus and reduction this pipeline applies. Asked of the
     * {@link DamageSource} because that is what a strike travels as: the type is readable on both sides, so one
     * question answers for the shaping layer and the reduction layer alike.
     */
    public static boolean bypasses(DamageSource source) {
        return source.is(NO_BONUS);
    }

    /**
     * Shapes one hit and applies it, returning the amount handed to the target, or zero when nothing was.
     *
     * <p>The source is built here rather than by the caller so attribution is not each call site's own
     * decision: a declared damage type is used verbatim with the attacker recorded as the cause, and without
     * one the vanilla player/mob attack source is chosen, which is what makes a kill count as the attacker's.
     * The reduction layer is not applied here - it runs in the incoming event, which this call raises.</p>
     *
     * <p>A strike whose type is in {@link #NO_BONUS} skips the shaping entirely and travels as the amount it
     * was handed. The event that follows skips the reduction for the same reason, so the two halves agree
     * without either having to know that the other exists; the elements are not even read, because a strike
     * nobody scales has no element to leave behind either.</p>
     *
     * @param attacker the entity credited with the hit, or null for damage that belongs to nobody
     * @param context  the formula context the hit was evaluated in, or null when there is none
     */
    public static double deal(@Nullable Entity attacker, Entity target, double amount,
                              Optional<Holder<DamageType>> damageType, @Nullable FormulaContext context) {
        if (!(target.level() instanceof ServerLevel level)) return 0.0D;
        if (!Double.isFinite(amount) || amount <= 0.0D) return 0.0D;
        DamageSource source = source(level, attacker, damageType);
        if (bypasses(source)) {
            target.hurtServer(level, source, (float) amount);
            return amount;
        }
        double shaped = outgoing(attacker, target, amount, context, DamageElements.strike(level, damageType, attacker));
        if (shaped <= 0.0D) return 0.0D;
        target.hurtServer(level, source, (float) shaped);
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
     * <p>With no attacker at all there is still no second party, so the last two factors are one: the relations
     * are not read even when a declared damage type gives the strike an element of its own. Nothing else
     * changes - the mastery and the affinity stay, because they belong to the casting rather than to the
     * pair.</p>
     *
     * <p>The elements are passed in rather than derived here, because they are the one part of a strike that
     * depends on how it was declared: a claimed damage type names them, and only in its absence are they the
     * attacker's roots ({@link DamageElements#strike}). The four-argument overload is that fallback reading,
     * for a hit that carries no damage type of its own.</p>
     */
    public static double outgoing(@Nullable Entity attacker, Entity target, double amount, @Nullable FormulaContext context,
                                  Set<Holder<Element>> elements) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return 0.0D;
        double pair = attacker == null ? 1.0D : physiqueMultiplier(attacker, true) * overcomeMultiplier(elements, target);
        double result = amount * masteryMultiplier(context) * elementMultiplier(context)
                * selfConflictMultiplier(attacker) * pair;
        return Double.isFinite(result) && result > 0.0D ? result : 0.0D;
    }

    /**
     * What an element is worth in the hand of somebody it conflicts with: one factor per element the striker is
     * wielding, taken from that element's own {@code conflict_multiplier}, whenever one of the striker's active
     * spirit roots lists it in {@code conflicting_elements}.
     *
     * <p>Only the striker's <em>main hand</em> is read, and only through {@link ItemElements}, so "what they are
     * wielding" means the same thing here as it does anywhere else in the mod: a declaration on the weapon, the
     * item or the artifact, or failing that the element of the aura the stack carries. An empty hand, an item
     * that declares nothing, a striker with no roots and a root that lists nothing all read as {@code 1.0},
     * which is the default of the field as well - a pack opts into this rule, and a pack that never writes it
     * cannot be affected by it.</p>
     *
     * <p>The factor is applied once per wielding element however many roots conflict with it, because the number
     * is that element's own statement about being mis-wielded rather than a property of the pair, and because a
     * body with two conflicting roots would otherwise silently square it. It is also deliberately not conditional
     * on the strike being made of that element: a cultivator fighting their own weapon is weakened whatever they
     * channel through it, which is the whole point of the rule.</p>
     *
     * <p>Reading the hand costs a walk over the item-binding registries, so this is one lookup per strike rather
     * than one per element compared.</p>
     */
    public static double selfConflictMultiplier(@Nullable Entity attacker) {
        if (!(attacker instanceof LivingEntity holder)) return 1.0D;
        ItemStack held = holder.getMainHandItem();
        if (held.isEmpty()) return 1.0D;
        Set<Holder<Element>> wielded = ItemElements.of(holder.level().registryAccess(), held);
        if (wielded.isEmpty()) return 1.0D;
        SpiritIdentityAttachment spirit = holder.getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
        if (spirit == null) return 1.0D;
        List<Holder<SpiritRoot>> roots = spirit.activeSpiritRoots();
        if (roots.isEmpty()) return 1.0D;
        double result = 1.0D;
        for (Holder<Element> element : wielded) {
            if (Double.isFinite(result) && conflicts(holder.level().registryAccess(), roots, element))
                result *= element.value().conflictMultiplier();
        }
        return Double.isFinite(result) ? result : 1.0D;
    }

    /**
     * Whether any of these active roots declares a conflict with this element. The declaration is read exactly
     * the way every other {@code conflicting_elements} reader reads it, so a disabled element matches nothing.
     */
    private static boolean conflicts(RegistryAccess access, List<Holder<SpiritRoot>> roots, Holder<Element> element) {
        for (Holder<SpiritRoot> root : roots) {
            SpiritRoot definition = MxtDatapackRegistries.get(access, MxtResourceKeys.SPIRIT_ROOT, root).orElse(null);
            if (definition != null && Elements.matches(definition.conflictingElements(), element)) return true;
        }
        return false;
    }

    /**
     * What an entity lets through of the elemental attachment a strike leaves on it: every {@code
     * attachment_multiplier} declared by the items it carries, multiplied together, or one when none of them
     * declares anything.
     *
     * <p>"Carried" is the artifact system's own reading of the word - both hands and every equipped Curios slot
     * ({@link ArtifactUpkeepService#carried}) - so a ward works the same way whether a pack puts it in a hand or
     * in a charm slot. An item that declares nothing contributes nothing, and an entity carrying nothing is
     * never affected: the default of the field is one as well, so a pack opts into this rule.</p>
     *
     * <p>This is deliberately the <em>only</em> place the number is applied, and it is applied to the buildup
     * rather than to a reaction's effect: what a reaction then does is the reaction's own action, and a carrier
     * that wants to soften that instead has a physique's {@code damage_taken_multiplier} for it. Resisting the
     * buildup is the item-side answer to "抵消部分元素反应" - reactions simply answer later, or never.</p>
     */
    public static double attachmentMultiplier(LivingEntity target) {
        List<ItemStack> carried = ArtifactUpkeepService.carried(target);
        double result = 1.0D;
        for (ItemStack stack : carried) {
            if (stack.isEmpty()) continue;
            result *= usable(ItemBindingService.weapon(target.level().registryAccess(), stack)
                    .map(WeaponBinding::attachmentMultiplier).orElse(1.0D));
            result *= usable(ItemBindingService.binding(target.level().registryAccess(), stack)
                    .map(ItemBinding::attachmentMultiplier).orElse(1.0D));
            result *= usable(ArtifactService.definition(target.level().registryAccess(), stack)
                    .map(holder -> holder.value().attachmentMultiplier()).orElse(1.0D));
        }
        return Double.isFinite(result) ? result : 1.0D;
    }

    /**
     * One declared multiplier, read as "no opinion" when it is not a usable number. The codecs refuse these
     * already, so this only guards a value that arrived some other way.
     */
    private static double usable(double value) {
        return Double.isFinite(value) && value >= 0.0D ? value : 1.0D;
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
     *
     * <p>A source whose type is in {@link #NO_BONUS} has no answer to give: the caller asking this question
     * holds a strike the pack exempted, so the amount is returned as it stands.</p>
     */
    public static double incoming(LivingEntity target, DamageSource source, double amount) {
        if (bypasses(source)) return Double.isFinite(amount) && amount > 0.0D ? amount : 0.0D;
        return incoming(target, DamageElements.reading(source).elements(), amount);
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
            if (provider instanceof Constant(double value1)) value = value1;
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
