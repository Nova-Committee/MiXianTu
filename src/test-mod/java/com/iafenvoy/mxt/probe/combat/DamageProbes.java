package com.iafenvoy.mxt.probe.combat;

import com.iafenvoy.mxt.attachment.TechniqueAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.item.HoldBinding;
import com.iafenvoy.mxt.event.AbilityUseEvent.Pre;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueHold;
import com.iafenvoy.mxt.runtime.damage.DamageCalculationService;
import com.iafenvoy.mxt.runtime.damage.DamageElements;
import com.iafenvoy.mxt.runtime.element.ElementReactionService;
import com.iafenvoy.mxt.runtime.hold.HoldLookup;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.progression.ProgressionDamageMultiplier;
import com.iafenvoy.mxt.testmod.MxtTestTechniqueItems;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The damage pipeline: shaping on the striker's side and reduction on the target's. One test per fixture leg, so a
 * single broken factor cannot hide the rest.
 */
public final class DamageProbes {
    // Arena relative spots for the throwaway bodies; every test puts its own pair out of the player's way.
    private static final BlockPos STRIKER = new BlockPos(8, 1, 8);
    private static final BlockPos TARGET = new BlockPos(8, 1, 11);
    private static final BlockPos SECOND_TARGET = new BlockPos(11, 1, 11);

    private DamageProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("combat/damage/elements", DamageProbes::elements),
                Probe.of("combat/damage/mastery", DamageProbes::mastery),
                Probe.of("combat/damage/technique_carrier", DamageProbes::techniqueCarrier),
                Probe.of("combat/damage/foreign", DamageProbes::foreign),
                Probe.of("combat/damage/physique", DamageProbes::physique),
                Probe.of("combat/damage/affinity", DamageProbes::affinity),
                Probe.of("combat/damage/unowned", DamageProbes::unowned),
                Probe.of("combat/damage/no_bonus", DamageProbes::noBonus),
                Probe.of("combat/damage/self_conflict", DamageProbes::selfConflict),
                Probe.of("combat/damage/origin", DamageProbes::origin),
                Probe.of("combat/damage/ward", DamageProbes::ward),
                Probe.of("combat/damage/claim", DamageProbes::claim));
    }

    // Both layers against bodies whose element edges are fixture-known: fire overcomes water x1.5 and water is
    // adapted to fire x0.5, so 10 reads as outgoing 15.0 / adapted 7.5.
    private static void elements(ProbeContext context) {
        LivingEntity attacker = spawnProbe(context, context.at(STRIKER), ProbeDefinitions.PROBE_FIRE_ROOT);
        LivingEntity defender = spawnProbe(context, context.at(TARGET), ProbeDefinitions.PROBE_WATER_ROOT);
        try {
            if (attacker == null || defender == null) {
                context.fail("damage probe: could not create the probe entities");
                return;
            }
            FormulaContext formula = FormulaContext.of(attacker);
            double outgoing = DamageCalculationService.outgoing(attacker, defender, 10.0D, formula);
            double adapted = DamageCalculationService.incoming(defender, attacker, outgoing);
            float before = defender.getHealth();
            DamageCalculationService.deal(attacker, defender, 10.0D, Optional.empty(), formula);
            double lost = before - defender.getHealth();
            boolean elements = close(outgoing, 15.0D) && close(adapted, 7.5D) && close(lost, 7.5D);
            String report = "damage probe: element outgoing=" + outgoing + " adapted=" + adapted + " health_lost=" + lost;
            context.note(report + (elements ? " OK" : " MISMATCH"));
            context.check(report, elements);
        } finally {
            if (attacker != null) attacker.discard();
            if (defender != null) defender.discard();
        }
    }

    // Sword_manual stage 1 is x1.1 and stage 2 x1.25, so 4 x 1.25 = 3.75 lost; the value must also reach the
    // context a real cast dispatches with, which is driven through AbilityService and read off the event it posts.
    private static void mastery(ProbeContext context) {
        ServerLevel level = context.level();
        LivingEntity attacker = spawnProbe(context, context.at(STRIKER), ProbeDefinitions.PROBE_FIRE_ROOT);
        // The mastery leg needs a defender of its own: the first hit leaves the target briefly invulnerable,
        // and a second strike in that window is refused rather than measured.
        LivingEntity masteryDefender = spawnProbe(context, context.at(TARGET), ProbeDefinitions.PROBE_WATER_ROOT);
        try {
            if (attacker == null || masteryDefender == null) {
                context.fail("damage probe: could not create the probe entities");
                return;
            }
            TechniqueAttachment spirit = attacker.getData(MxtAttachments.TECHNIQUE);
            Holder<Technique> technique = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.PROBE_TECHNIQUE);
            Holder<Ability> ability = context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.PROBE_ABILITY);
            spirit.addLearnedTechnique(technique);
            double entry = ProgressionDamageMultiplier.of(attacker, ability);
            spirit.techniqueLevels().set(technique, context.require(MxtResourceKeys.PROGRESSION, ProbeDefinitions.PROBE_PROGRESSION_LEVEL));
            double advanced = ProgressionDamageMultiplier.of(attacker, ability);
            FormulaContext formula = FormulaContext.of(attacker);
            float masteryBefore = masteryDefender.getHealth();
            DamageCalculationService.deal(attacker, masteryDefender, 4.0D, Optional.empty(),
                    formula.with(DamageCalculationService.DAMAGE_MULTIPLIER, advanced));
            double masteryLost = masteryBefore - masteryDefender.getHealth();
            double[] dispatched = {Double.NaN};
            Consumer<Pre> listener = event ->
                    dispatched[0] = event.getContext().explicit(DamageCalculationService.DAMAGE_MULTIPLIER);
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                AbilityService.useCarried(ability, attacker,
                        attacker.getData(MxtAttachments.ABILITY_HOLDER), attacker.getData(MxtAttachments.RESOURCE_HOLDER),
                        level.getGameTime(), formula, null);
            } finally {
                NeoForge.EVENT_BUS.unregister(listener);
            }
            boolean mastery = close(entry, 1.1D) && close(advanced, 1.25D) && close(masteryLost, 3.75D)
                    && close(dispatched[0], 1.25D);
            String report = "damage probe: mastery entry=" + entry + " advanced=" + advanced
                    + " health_lost=" + masteryLost + " dispatched=" + dispatched[0];
            context.note(report + (mastery ? " OK" : " MISMATCH"));
            context.check(report, mastery);
        } finally {
            if (attacker != null) attacker.discard();
            if (masteryDefender != null) masteryDefender.discard();
        }
    }

    // What makes a manual is the stack's own component: a technique book out of the creative menu teaches nothing,
    // while the carrier the mod generates teaches its technique, using the item the declaration names.
    private static void techniqueCarrier(ProbeContext context) {
        ItemStack blankBook = MxtItems.TECHNIQUE_BOOK.toStack();
        Holder<Technique> declaredTechnique = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.TECHNIQUE);
        ItemStack generated = ItemBindingService.techniqueCarrier(context.registries(), declaredTechnique);
        Holder<Technique> customTechnique = context.require(MxtResourceKeys.TECHNIQUE, context.id("azure_water_manual"));
        ItemStack customCarrier = ItemBindingService.techniqueCarrier(context.registries(), customTechnique);
        boolean blankless = ItemBindingService.technique(context.registries(), blankBook).isEmpty();
        boolean carrier = generated.is(MxtItems.TECHNIQUE_BOOK.get())
                && customCarrier.is(MxtTestTechniqueItems.AZURE_WATER_MANUAL.get())
                && blankless
                && ItemBindingService.technique(context.registries(), generated)
                .filter(binding -> HolderHelper.id(binding.technique()).equals(ProbeDefinitions.TECHNIQUE))
                .isPresent()
                // The technique asks for a hold of its own, which the built-in book reads in its pinned pose.
                && ItemBindingService.reading(context.registries(), generated)
                .filter(reading -> reading.holdTicks(FormulaContext.EMPTY) == 40 && reading.holdAnimation() == ItemUseAnimation.BLOCK)
                .isPresent()
                // A manual that brings an item of its own keeps the pose its technique asks for.
                && ItemBindingService.reading(context.registries(), customCarrier)
                .filter(reading -> reading.holdAnimation() == ItemUseAnimation.BUNDLE)
                .isPresent()
                && HoldLookup.hold(generated) instanceof TechniqueHold bookHold
                && bookHold.holdAnimation(context.registries(), generated) == HoldBinding.DEFAULT_HOLD_ANIMATION
                && HoldLookup.hold(blankBook) == null;
        String report = "technique probe: blank_teaches=" + !blankless
                + " generated=" + generated.getItem() + " custom=" + customCarrier.getItem();
        context.note(report + (carrier ? " OK" : " MISMATCH"));
        context.check(report, carrier);
    }

    // A source the pipeline never saw must still be reduced: this is a plain vanilla mob attack, so only the
    // defender's adaptation may touch it - 4 x 0.5 = 2 - never the attacker's edge.
    private static void foreign(ProbeContext context) {
        ServerLevel level = context.level();
        LivingEntity attacker = spawnProbe(context, context.at(STRIKER), ProbeDefinitions.PROBE_FIRE_ROOT);
        LivingEntity foreignDefender = spawnProbe(context, context.at(TARGET), ProbeDefinitions.PROBE_WATER_ROOT);
        try {
            if (attacker == null || foreignDefender == null) {
                context.fail("damage probe: could not create the probe entities");
                return;
            }
            float foreignBefore = foreignDefender.getHealth();
            foreignDefender.hurtServer(level, level.damageSources().mobAttack(attacker), 4.0F);
            double foreignLost = foreignBefore - foreignDefender.getHealth();
            boolean foreign = close(foreignLost, 2.0D);
            String report = "damage probe: foreign health_lost=" + foreignLost;
            context.note(report + (foreign ? " OK" : " MISMATCH"));
            context.check(report, foreign);
        } finally {
            if (attacker != null) attacker.discard();
            if (foreignDefender != null) foreignDefender.discard();
        }
    }

    // The physique half, granted to both sides (dealt x1.5, taken x0.5): 10 * 1.5 * 1.5 = 22.5, then
    // 22.5 * 0.5 * 0.5 = 5.625. The taken multiplier is a formula, evaluated against its own holder.
    private static void physique(ProbeContext context) {
        LivingEntity bodyAttacker = spawnProbe(context, context.at(STRIKER), ProbeDefinitions.PROBE_FIRE_ROOT);
        LivingEntity bodyDefender = spawnProbe(context, context.at(TARGET), ProbeDefinitions.PROBE_WATER_ROOT);
        try {
            if (bodyAttacker == null || bodyDefender == null) {
                context.fail("damage probe: could not create the probe entities");
                return;
            }
            boolean bodyGranted = grantProbePhysique(context, bodyAttacker, ProbeDefinitions.PROBE_PHYSIQUE)
                    && grantProbePhysique(context, bodyDefender, ProbeDefinitions.PROBE_PHYSIQUE);
            FormulaContext bodyFormula = FormulaContext.of(bodyAttacker);
            double bodyOutgoing = DamageCalculationService.outgoing(bodyAttacker, bodyDefender, 10.0D, bodyFormula);
            double bodyIncoming = DamageCalculationService.incoming(bodyDefender, bodyAttacker, bodyOutgoing);
            float bodyBefore = bodyDefender.getHealth();
            DamageCalculationService.deal(bodyAttacker, bodyDefender, 10.0D, Optional.empty(), bodyFormula);
            double bodyLost = bodyBefore - bodyDefender.getHealth();
            boolean physique = bodyGranted && close(bodyOutgoing, 22.5D) && close(bodyIncoming, 5.625D)
                    && close(bodyLost, 5.625D);
            String report = "damage probe: physique outgoing=" + bodyOutgoing
                    + " taken=" + bodyIncoming + " health_lost=" + bodyLost;
            context.note(report + (physique ? " OK" : " MISMATCH"));
            context.check(report, physique);
        } finally {
            if (bodyAttacker != null) bodyAttacker.discard();
            if (bodyDefender != null) bodyDefender.discard();
        }
    }

    // The spirit root's affinity is a shaping factor now, not just a value a formula must remember:
    // 10 * 1.1 * 1.5 = 16.5, then 16.5 * 0.5 = 8.25 once the water body answers.
    private static void affinity(ProbeContext context) {
        ServerLevel level = context.level();
        LivingEntity affinityAttacker = spawnProbe(context, context.at(STRIKER), ProbeDefinitions.PROBE_FIRE_ROOT);
        LivingEntity affinityDefender = spawnProbe(context, context.at(TARGET), ProbeDefinitions.PROBE_WATER_ROOT);
        try {
            if (affinityAttacker == null || affinityDefender == null) {
                context.fail("damage probe: could not create the probe entities");
                return;
            }
            FormulaContext affinityFormula = FormulaContext.of(affinityAttacker)
                    .with(DamageCalculationService.ELEMENT_MODIFIER, ProbeDefinitions.PROBE_FIRE_AFFINITY);
            double affinityOutgoing = DamageCalculationService.outgoing(affinityAttacker, affinityDefender, 10.0D, affinityFormula);
            double affinityIncoming = DamageCalculationService.incoming(affinityDefender, affinityAttacker, affinityOutgoing);
            float affinityBefore = affinityDefender.getHealth();
            DamageCalculationService.deal(affinityAttacker, affinityDefender, 10.0D, Optional.empty(), affinityFormula);
            double affinityLost = affinityBefore - affinityDefender.getHealth();
            double[] castAffinity = {Double.NaN};
            Consumer<Pre> affinityListener = event ->
                    castAffinity[0] = event.getContext().explicit(DamageCalculationService.ELEMENT_MODIFIER);
            NeoForge.EVENT_BUS.addListener(affinityListener);
            try {
                Holder<Ability> affinityAbility = context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.PROBE_AFFINITY_ABILITY);
                AbilityService.useCarried(affinityAbility, affinityAttacker,
                        affinityAttacker.getData(MxtAttachments.ABILITY_HOLDER), affinityAttacker.getData(MxtAttachments.RESOURCE_HOLDER),
                        level.getGameTime(), FormulaContext.of(affinityAttacker), null);
            } finally {
                NeoForge.EVENT_BUS.unregister(affinityListener);
            }
            boolean affinity = close(castAffinity[0], ProbeDefinitions.PROBE_FIRE_AFFINITY)
                    && close(affinityOutgoing, 16.5D) && close(affinityIncoming, 8.25D) && close(affinityLost, 8.25D);
            String report = "damage probe: affinity outgoing=" + affinityOutgoing
                    + " reduced=" + affinityIncoming + " health_lost=" + affinityLost + " cast=" + castAffinity[0];
            context.note(report + (affinity ? " OK" : " MISMATCH"));
            context.check(report, affinity);
        } finally {
            if (affinityAttacker != null) affinityAttacker.discard();
            if (affinityDefender != null) affinityDefender.discard();
        }
    }

    // Nobody credited reads no element relation even when the declared type would lend it one: minecraft:magic is
    // fire-claimed in the test package, so that relation exists and must not be read.
    private static void unowned(ProbeContext context) {
        LivingEntity attacker = spawnProbe(context, context.at(STRIKER), ProbeDefinitions.PROBE_FIRE_ROOT);
        LivingEntity unownedDefender = spawnProbe(context, context.at(TARGET), ProbeDefinitions.PROBE_WATER_ROOT);
        try {
            if (attacker == null || unownedDefender == null) {
                context.fail("damage probe: could not create the probe entities");
                return;
            }
            // The cast's own factors stay, hence a mastery of 2.0 - that separates 8.0 (correct: 4 * 2, relation
            // dropped) from 12.0 (relation read anyway) and 4.0 (mastery dropped too), which 1.0 could not.
            Holder<DamageType> magicDamage = context.registries().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(DamageTypes.MAGIC);
            Set<Holder<Element>> declared = DamageElements.of(magicDamage);
            FormulaContext unownedFormula = FormulaContext.of(attacker)
                    .with(DamageCalculationService.DAMAGE_MULTIPLIER, 2.0D);
            double unowned = DamageCalculationService.outgoing(null, unownedDefender, 4.0D, unownedFormula, declared);
            float unownedBefore = unownedDefender.getHealth();
            DamageCalculationService.deal(null, unownedDefender, 4.0D, Optional.of(magicDamage), unownedFormula);
            double unownedLost = unownedBefore - unownedDefender.getHealth();
            boolean unattributed = close(unowned, 8.0D) && close(unownedLost, 4.0D);
            String report = "damage probe: unattributed outgoing=" + unowned + " health_lost=" + unownedLost;
            context.note(report + (unattributed ? " OK" : " MISMATCH"));
            context.check(report, unattributed);
        } finally {
            if (attacker != null) attacker.discard();
            if (unownedDefender != null) unownedDefender.discard();
        }
    }

    // A type the pack exempted travels as the number it was handed. Both bodies carry the same physique, so the
    // readings are far apart: 6.0 means both layers stood aside; this also proves the void is in the default tag.
    private static void noBonus(ProbeContext context) {
        ServerLevel level = context.level();
        LivingEntity taggedAttacker = spawnProbe(context, context.at(STRIKER), ProbeDefinitions.PROBE_FIRE_ROOT);
        LivingEntity taggedDefender = spawnProbe(context, context.at(TARGET), ProbeDefinitions.PROBE_WATER_ROOT);
        try {
            if (taggedAttacker == null || taggedDefender == null) {
                context.fail("damage probe: could not create the probe entities");
                return;
            }
            Holder<DamageType> voidDamage = context.registries().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(DamageTypes.FELL_OUT_OF_WORLD);
            boolean tagged = DamageCalculationService.bypasses(level.damageSources().fellOutOfWorld())
                    && !DamageCalculationService.bypasses(level.damageSources().magic());
            boolean taggedBodies = grantProbePhysique(context, taggedAttacker, ProbeDefinitions.PROBE_PHYSIQUE)
                    && grantProbePhysique(context, taggedDefender, ProbeDefinitions.PROBE_PHYSIQUE);
            FormulaContext taggedFormula = FormulaContext.of(taggedAttacker);
            float taggedBefore = taggedDefender.getHealth();
            double handed = DamageCalculationService.deal(taggedAttacker, taggedDefender, 6.0D,
                    Optional.of(voidDamage), taggedFormula);
            double taggedLost = taggedBefore - taggedDefender.getHealth();
            boolean exempt = tagged && taggedBodies && close(handed, 6.0D) && close(taggedLost, 6.0D);
            String report = "damage probe: no_bonus tagged=" + tagged + " dealt=" + handed
                    + " health_lost=" + taggedLost;
            context.note(report + (exempt ? " OK" : " MISMATCH"));
            context.check(report, exempt);
        } finally {
            if (taggedAttacker != null) taggedAttacker.discard();
            if (taggedDefender != null) taggedDefender.discard();
        }
    }

    // A weapon the striker's own roots conflict with weakens everything they deal: the fixture root is a fire body
    // conflicting with water, the golden sword is water, and mxt_test:water prices it at 0.5.
    private static void selfConflict(ProbeContext context) {
        LivingEntity conflictAttacker = spawnProbe(context, context.at(STRIKER), ProbeDefinitions.PROBE_ANTI_WATER_ROOT);
        LivingEntity conflictDefender = spawnProbe(context, context.at(TARGET), null);
        try {
            if (conflictAttacker == null || conflictDefender == null) {
                context.fail("damage probe: could not create the probe entities");
                return;
            }
            // The victim has no roots and fire meets nothing, so no relation confuses this: 4 reads as 2 in that
            // hand and as 4 in an empty one.
            conflictAttacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_SWORD));
            FormulaContext conflictFormula = FormulaContext.of(conflictAttacker);
            double conflicting = DamageCalculationService.outgoing(conflictAttacker, conflictDefender, 4.0D, conflictFormula);
            conflictAttacker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            double unarmed = DamageCalculationService.outgoing(conflictAttacker, conflictDefender, 4.0D, conflictFormula);
            conflictAttacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_SWORD));
            float conflictBefore = conflictDefender.getHealth();
            DamageCalculationService.deal(conflictAttacker, conflictDefender, 4.0D, Optional.empty(), conflictFormula);
            double conflictLost = conflictBefore - conflictDefender.getHealth();
            boolean selfConflict = close(conflicting, 2.0D) && close(unarmed, 4.0D) && close(conflictLost, 2.0D);
            String report = "damage probe: self_conflict wielded=" + conflicting
                    + " unarmed=" + unarmed + " health_lost=" + conflictLost;
            context.note(report + (selfConflict ? " OK" : " MISMATCH"));
            context.check(report, selfConflict);
        } finally {
            if (conflictAttacker != null) conflictAttacker.discard();
            if (conflictDefender != null) conflictDefender.discard();
        }
    }

    // Only an element the strike declared rubs off. Both victims are rootless, so neither has an edge; the first
    // travels as minecraft:magic, which the test package has fire claim, the second declares nothing.
    private static void origin(ProbeContext context) {
        LivingEntity attacker = spawnProbe(context, context.at(STRIKER), ProbeDefinitions.PROBE_FIRE_ROOT);
        LivingEntity claimedVictim = spawnProbe(context, context.at(TARGET), null);
        LivingEntity rootVictim = spawnProbe(context, context.at(SECOND_TARGET), null);
        try {
            if (attacker == null || claimedVictim == null || rootVictim == null) {
                context.fail("damage probe: could not create the probe entities");
                return;
            }
            Holder<DamageType> magicDamage = context.registries().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(DamageTypes.MAGIC);
            float claimedBefore = claimedVictim.getHealth();
            Holder<Element> probeFire = context.require(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_FIRE_ELEMENT);
            DamageCalculationService.deal(attacker, claimedVictim, 10.0D, Optional.of(magicDamage), FormulaContext.of(attacker));
            double claimedLeft = ElementReactionService.amount(claimedVictim, probeFire);
            double claimedLost = claimedBefore - claimedVictim.getHealth();
            float rootBefore = rootVictim.getHealth();
            DamageCalculationService.deal(attacker, rootVictim, 10.0D, Optional.empty(), FormulaContext.of(attacker));
            double rootLeft = ElementReactionService.amount(rootVictim, probeFire);
            double rootLost = rootBefore - rootVictim.getHealth();
            boolean strikeOrigin = close(claimedLeft, 4.0D) && close(claimedLost, 10.0D)
                    && close(rootLeft, 0.0D) && close(rootLost, 10.0D);
            String report = "damage probe: origin claimed_left=" + claimedLeft
                    + " root_left=" + rootLeft + " health_lost=" + claimedLost + "/" + rootLost;
            context.note(report + (strikeOrigin ? " OK" : " MISMATCH"));
            context.check(report, strikeOrigin);
        } finally {
            if (attacker != null) attacker.discard();
            if (claimedVictim != null) claimedVictim.discard();
            if (rootVictim != null) rootVictim.discard();
        }
    }

    // What the victim carries decides how much of that element gets through: the ward artifact prices itself at
    // 0.5, so the same claimed fire strike leaves 2 where a bare body kept 4, and the strike's damage is untouched.
    private static void ward(ProbeContext context) {
        LivingEntity attacker = spawnProbe(context, context.at(STRIKER), ProbeDefinitions.PROBE_FIRE_ROOT);
        LivingEntity claimedVictim = spawnProbe(context, context.at(TARGET), null);
        LivingEntity wardVictim = spawnProbe(context, context.at(SECOND_TARGET), null);
        try {
            if (attacker == null || claimedVictim == null || wardVictim == null) {
                context.fail("damage probe: could not create the probe entities");
                return;
            }
            Holder<DamageType> magicDamage = context.registries().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(DamageTypes.MAGIC);
            Holder<Element> probeFire = context.require(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_FIRE_ELEMENT);
            wardVictim.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PRISMARINE_SHARD));
            double wardMultiplier = DamageCalculationService.attachmentMultiplier(wardVictim);
            float wardBefore = wardVictim.getHealth();
            DamageCalculationService.deal(attacker, wardVictim, 10.0D, Optional.of(magicDamage), FormulaContext.of(attacker));
            double wardLeft = ElementReactionService.amount(wardVictim, probeFire);
            double wardLost = wardBefore - wardVictim.getHealth();
            boolean warded = close(DamageCalculationService.attachmentMultiplier(claimedVictim), 1.0D)
                    && close(wardMultiplier, 0.5D) && close(wardLeft, 2.0D) && close(wardLost, 10.0D);
            String report = "damage probe: attachment_ward multiplier=" + wardMultiplier
                    + " left=" + wardLeft + " health_lost=" + wardLost;
            context.note(report + (warded ? " OK" : " MISMATCH"));
            context.check(report, warded);
        } finally {
            if (attacker != null) attacker.discard();
            if (claimedVictim != null) claimedVictim.discard();
            if (wardVictim != null) wardVictim.discard();
        }
    }

    // A type's claim may price the buildup itself: the fixture element claims minecraft:magic plainly, so fire's own
    // 4.0 answers, and minecraft:lava with a 2.0 of its own - one element read off two types leaves two amounts.
    private static void claim(ProbeContext context) {
        LivingEntity attacker = spawnProbe(context, context.at(STRIKER), ProbeDefinitions.PROBE_FIRE_ROOT);
        LivingEntity claimedVictim = spawnProbe(context, context.at(TARGET), null);
        LivingEntity lavaVictim = spawnProbe(context, context.at(SECOND_TARGET), null);
        try {
            if (attacker == null || claimedVictim == null || lavaVictim == null) {
                context.fail("damage probe: could not create the probe entities");
                return;
            }
            Holder<DamageType> magicDamage = context.registries().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(DamageTypes.MAGIC);
            Holder<DamageType> lavaDamage = context.registries().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(DamageTypes.LAVA);
            Holder<Element> probeFire = context.require(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_FIRE_ELEMENT);
            DamageCalculationService.deal(attacker, claimedVictim, 10.0D, Optional.of(magicDamage), FormulaContext.of(attacker));
            double claimedLeft = ElementReactionService.amount(claimedVictim, probeFire);
            float lavaBefore = lavaVictim.getHealth();
            DamageCalculationService.deal(attacker, lavaVictim, 10.0D, Optional.of(lavaDamage), FormulaContext.of(attacker));
            double lavaLeft = ElementReactionService.amount(lavaVictim, probeFire);
            double lavaLost = lavaBefore - lavaVictim.getHealth();
            boolean claimAmount = close(lavaLeft, 2.0D) && close(lavaLost, 10.0D);
            String report = "damage probe: claim amount lava_left=" + lavaLeft
                    + " magic_left=" + claimedLeft + " health_lost=" + lavaLost;
            context.note(report + (claimAmount ? " OK" : " MISMATCH"));
            context.check(report, claimAmount);
        } finally {
            if (attacker != null) attacker.discard();
            if (claimedVictim != null) claimedVictim.discard();
            if (lavaVictim != null) lavaVictim.discard();
        }
    }

    // A disposable pig carrying exactly one spirit root: no armour and no innate resistance keep the health it
    // loses equal to what the pipeline handed over, and a null root is how "the type, not the caster" is told apart.
    private static LivingEntity spawnProbe(ProbeContext context, BlockPos pos, @Nullable Identifier root) {
        Pig pig = EntityType.PIG.create(context.level(), EntitySpawnReason.COMMAND);
        if (pig == null) return null;
        pig.setNoAi(true);
        pig.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        context.level().addFreshEntity(pig);
        if (root != null)
            CultivationIdentityService.grantSpiritRoot(pig, context.require(MxtResourceKeys.SPIRIT_ROOT, root));
        return pig;
    }

    // Grants through the authoritative service a data pack action would take; false means the definition was
    // refused, and the leg that asked for it fails rather than measuring a body that never got it.
    private static boolean grantProbePhysique(ProbeContext context, LivingEntity entity, Identifier id) {
        return CultivationIdentityService.grantPhysique(entity, context.require(MxtResourceKeys.PHYSIQUE, id),
                FormulaContext.of(entity)).changed();
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }
}
