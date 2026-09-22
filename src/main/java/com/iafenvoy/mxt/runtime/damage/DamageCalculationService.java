package com.iafenvoy.mxt.runtime.damage;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
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
 * The single path for every strike: shaping ({@link #outgoing}) on the attacker's side, reduction
 * ({@link #incoming}) on the target's through {@link DamageEventBridge}. Do not recompute any of it elsewhere
 * ({@code research/29}).
 */
public final class DamageCalculationService {
    // Formula value the casting ability exposes for layer one; unset means no mastery bonus.
    public static final String DAMAGE_MULTIPLIER = "damage_multiplier";
    // Formula value a casting adapted to an element exposes; applied by layer one, not by the pack.
    public static final String ELEMENT_MODIFIER = "element_modifier";
    // Types tagged here are neither shaped nor reduced and leave no element; only vanilla mitigation applies.
    // Vanilla damage_type tag, so it covers every source - our actions, another mod's sword, a hazard. The mod
    // ships the void (minecraft:out_of_world); a pack may replace that file.
    public static final TagKey<DamageType> NO_BONUS =
            TagKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "no_bonus"));

    private DamageCalculationService() {
    }

    // The type is read off the DamageSource because that is what a strike travels as, so one question answers
    // for both layers.
    public static boolean bypasses(DamageSource source) {
        return source.is(NO_BONUS);
    }

    // The source is built here, not by the caller, so attribution is not each call site's own decision: a kill
    // counts as the attacker's. Reduction is not applied here - it runs in the event this call raises.
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

    // Every factor is read from data; anything that is not a positive finite number is reported as no damage at
    // all. Pair factors (element relation, attacker physique) need a second party, so with no attacker they are
    // one - but mastery and affinity still apply, because they belong to the casting and not to the pair.
    public static double outgoing(@Nullable Entity attacker, Entity target, double amount, @Nullable FormulaContext context,
                                  Set<Holder<Element>> elements) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return 0.0D;
        double pair = attacker == null ? 1.0D : physiqueMultiplier(attacker, true) * overcomeMultiplier(elements, target);
        double result = amount * masteryMultiplier(context) * elementMultiplier(context)
                * selfConflictMultiplier(attacker) * pair;
        return Double.isFinite(result) && result > 0.0D ? result : 0.0D;
    }

    // One factor per wielding element, taken from that element's own conflict_multiplier, applied once no
    // matter how many roots conflict with it - two conflicting roots must not square it. Not conditional on the
    // strike being of that element. Reading the hand walks the binding registries: one lookup per strike, never
    // one per element compared.
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

    // Read the same way every other conflicting_elements reader reads it, so a disabled element matches nothing.
    private static boolean conflicts(RegistryAccess access, List<Holder<SpiritRoot>> roots, Holder<Element> element) {
        for (Holder<SpiritRoot> root : roots) {
            SpiritRoot definition = MxtDatapackRegistries.get(access, MxtResourceKeys.SPIRIT_ROOT, root).orElse(null);
            if (definition != null && Elements.matches(definition.conflictingElements(), element)) return true;
        }
        return false;
    }

    // The only place this number is applied, and it is applied to the buildup, not to a reaction's effect: a
    // carrier that wants to soften that has a physique's damage_taken_multiplier instead.
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

    // The codecs already refuse these; this only guards a value that arrived some other way.
    private static double usable(double value) {
        return Double.isFinite(value) && value >= 0.0D ? value : 1.0D;
    }

    // For a hit that declares no damage type: the attacker's own root elements are the strike's elements.
    public static double outgoing(@Nullable Entity attacker, Entity target, double amount, @Nullable FormulaContext context) {
        return outgoing(attacker, target, amount, context, attacker == null ? Set.of() : Elements.of(attacker));
    }

    // Reading the source is what makes this layer cover a lava tick or another mod's sword as readily as one of
    // our hits: no attacker and no claimed type means no element. A target with a physique is answered for all
    // the same - "what this body takes" does not depend on where the blow came from.
    public static double incoming(LivingEntity target, DamageSource source, double amount) {
        if (bypasses(source)) return Double.isFinite(amount) && amount > 0.0D ? amount : 0.0D;
        return incoming(target, DamageElements.reading(source).elements(), amount);
    }

    // For a caller that holds only the attacker: the strike's elements are that attacker's roots.
    public static double incoming(LivingEntity target, @Nullable Entity attacker, double amount) {
        return incoming(target, attacker == null ? Set.of() : Elements.of(attacker), amount);
    }

    // For a caller that needs the same answer twice: the incoming event reduces with these elements and then
    // builds the element up on the target, so reading the source once is what keeps the two in step.
    public static double incoming(LivingEntity target, Set<Holder<Element>> attacking, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return 0.0D;
        double result = amount * adaptationMultiplier(target, attacking) * physiqueMultiplier(target, false);
        return Double.isFinite(result) && result > 0.0D ? result : 0.0D;
    }

    // Every matching pair multiplies, so two elements that both overcome the target both count.
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

    // Only the defender's adapted_to is read: being overcome is the attacker's advantage, not a second edge on
    // the receiving side.
    public static double adaptationMultiplier(LivingEntity target, Set<Holder<Element>> attacking) {
        Set<Holder<Element>> defending = Elements.of(target);
        if (attacking.isEmpty() || defending.isEmpty()) return 1.0D;
        double result = 1.0D;
        for (Holder<Element> victim : defending)
            for (Holder<Element> source : attacking)
                result *= victim.value().adaptationMultiplier(source);
        return result;
    }

    // A value a pack somehow wrote is only read as a bonus while it is finite and non-negative, the same rule
    // the definition was validated under.
    public static double masteryMultiplier(@Nullable FormulaContext context) {
        if (context == null) return 1.0D;
        double value = context.explicit(DAMAGE_MULTIPLIER);
        return Double.isFinite(value) && value >= 0.0D ? value : 1.0D;
    }

    // A casting that names no element, or damage that came from somewhere other than a casting, has no affinity.
    // An affinity of zero is a root that says "nothing comes through me" and is honoured, not read as absent.
    public static double elementMultiplier(@Nullable FormulaContext context) {
        if (context == null) return 1.0D;
        double value = context.explicit(ELEMENT_MODIFIER);
        return Double.isFinite(value) && value >= 0.0D ? value : 1.0D;
    }

    // _dealt for the blow it deals, _taken for the blow it receives; several physiques multiply. A multiplier is
    // evaluated against the holder's own context, never the other party's: a physique is a property of that body
    // and must not depend on who is asking.
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

    // With no declared type this is the vanilla reading of "the attacker hit it": playerAttack carries the
    // player, which is what makes a kill count as theirs.
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
