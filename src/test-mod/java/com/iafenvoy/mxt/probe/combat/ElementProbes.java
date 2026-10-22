package com.iafenvoy.mxt.probe.combat;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.AbilityEffect;
import com.iafenvoy.mxt.data.aura.AuraRequirement;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.condition.AlwaysCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.AuraElementEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.ElementAttachmentEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.HasElementEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.item.ItemElementCondition;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.*;
import com.iafenvoy.mxt.runtime.damage.DamageCalculationService;
import com.iafenvoy.mxt.runtime.damage.DamageElements;
import com.iafenvoy.mxt.runtime.element.ElementReactionService;
import com.iafenvoy.mxt.runtime.resulting.failures.CultivationFailure;
import com.iafenvoy.mxt.runtime.resulting.failures.CultivationIdentityFailure;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.testmod.MxtTestItems;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The element channel: which element a strike is read as, what it leaves behind, which reaction answers it, and what
 * the enable/disable module does to all of it. One test per fixture leg, against disposable probe bodies.
 */
public final class ElementProbes {
    // Arena relative spots for the throwaway bodies; every test puts its own pair out of the player's way.
    private static final BlockPos CASTER = new BlockPos(8, 1, 8);
    private static final BlockPos VICTIM = new BlockPos(8, 1, 11);
    private static final BlockPos SECOND_CASTER = new BlockPos(11, 1, 8);
    private static final BlockPos SECOND_VICTIM = new BlockPos(11, 1, 11);

    private ElementProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("combat/element/claimed", ElementProbes::claimed),
                Probe.of("combat/element/declared", ElementProbes::declared),
                Probe.of("combat/element/reaction", ElementProbes::reaction),
                Probe.of("combat/element/gated", ElementProbes::gated),
                Probe.of("combat/element/conditions", ElementProbes::conditions),
                Probe.of("combat/element/toggle", ElementProbes::toggle),
                Probe.of("combat/element/multi_element", ElementProbes::multiElement),
                Probe.of("combat/element/weights", ElementProbes::weights),
                Probe.of("combat/element/loop", ElementProbes::loop),
                Probe.of("combat/element/conflict", ElementProbes::conflict),
                Probe.of("combat/element/five_phases", ElementProbes::fivePhases),
                Probe.of("combat/element/bloom", ElementProbes::bloom),
                Probe.of("combat/element/settle", ElementProbes::settle),
                Probe.of("combat/element/item_element", ElementProbes::itemElement));
    }

    // A claimed damage type names the element even when the caster has no roots at all. The fixture arithmetic is
    // fire overcomes water x1.5 and water is adapted to fire x0.5, so a fire strike of 10 lands as 7.5.
    private static void claimed(ProbeContext context) {
        Holder<Element> fire = context.require(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_FIRE_ELEMENT);
        Holder<DamageType> magic = context.registries().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.MAGIC);
        LivingEntity rootless = spawnProbe(context, context.at(CASTER), null);
        LivingEntity waterVictim = spawnProbe(context, context.at(VICTIM), ProbeDefinitions.PROBE_WATER_ROOT);
        try {
            if (rootless == null || waterVictim == null) {
                context.fail("element probe: could not create the probe entities");
                return;
            }
            boolean typeClaimsFire = DamageElements.of(magic).contains(fire);
            double beforeClaim = waterVictim.getHealth();
            DamageCalculationService.deal(rootless, waterVictim, 10.0D, Optional.of(magic), FormulaContext.of(rootless));
            double claimedLost = beforeClaim - waterVictim.getHealth();
            boolean claimed = typeClaimsFire && close(claimedLost, 7.5D);
            String report = "element probe: claimed magic -> fire, health_lost=" + claimedLost;
            context.note(report + (claimed ? " OK" : " MISMATCH"));
            context.check(report, claimed);
        } finally {
            if (rootless != null) rootless.discard();
            if (waterVictim != null) waterVictim.discard();
        }
    }

    // An action that declares an element is read as that element, not as the caster's roots: a water caster would
    // otherwise land 10 * 1.0 * 0.5 = 5 on a water body. The probe skill is the type that carries these fields.
    private static void declared(ProbeContext context) {
        LivingEntity waterCaster = spawnProbe(context, context.at(CASTER), ProbeDefinitions.PROBE_WATER_ROOT);
        LivingEntity declaredVictim = spawnProbe(context, context.at(VICTIM), ProbeDefinitions.PROBE_WATER_ROOT);
        try {
            if (waterCaster == null || declaredVictim == null) {
                context.fail("element probe: could not create the probe entities");
                return;
            }
            Ability declaration = context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.PROBE_ELEMENTAL).value();
            FormulaContext casterFormula = FormulaContext.of(waterCaster);
            double beforeDeclared = declaredVictim.getHealth();
            AbilityEffect.runOn(declaration.type(), waterCaster, declaredVictim, casterFormula, null);
            double declaredLost = beforeDeclared - declaredVictim.getHealth();
            boolean declared = close(declaredLost, 7.5D);
            String report = "element probe: declared element on a water caster, health_lost=" + declaredLost;
            context.note(report + (declared ? " OK" : " MISMATCH"));
            context.check(report, declared);
        } finally {
            if (waterCaster != null) waterCaster.discard();
            if (declaredVictim != null) declaredVictim.discard();
        }
    }

    // A strike leaves its element behind, and enough of it is answered by the fixture reaction, which takes the
    // buildup away and deals its own 6 (no attacker, no claim: unchanged).
    private static void reaction(ProbeContext context) {
        Holder<Element> fire = context.require(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_FIRE_ELEMENT);
        LivingEntity reactionVictim = spawnProbe(context, context.at(CASTER), ProbeDefinitions.PROBE_WATER_ROOT);
        try {
            if (reactionVictim == null) {
                context.fail("element probe: could not create the probe entities");
                return;
            }
            ElementReactionService.applyFromStrike(reactionVictim, Set.of(fire), FormulaContext.of(reactionVictim));
            double built = ElementReactionService.amount(reactionVictim, fire);
            double beforeReaction = reactionVictim.getHealth();
            ElementReactionService.apply(reactionVictim, fire, 4.0D, FormulaContext.of(reactionVictim));
            double reactionLost = beforeReaction - reactionVictim.getHealth();
            double leftOver = ElementReactionService.amount(reactionVictim, fire);
            boolean reaction = close(built, 4.0D) && close(reactionLost, 6.0D) && close(leftOver, 0.0D);
            String report = "element probe: attachment built=" + built
                    + " reaction damage=" + reactionLost + " left=" + leftOver;
            context.note(report + (reaction ? " OK" : " MISMATCH"));
            context.check(report, reaction);
        } finally {
            if (reactionVictim != null) reactionVictim.discard();
        }
    }

    // A definition is taken out of the world while the pack loads, not by a query-time tag: the fixture whose file
    // carries a false `neoforge:conditions` block is absent while an ordinary one is present.
    private static void gated(ProbeContext context) {
        boolean gated = MxtDatapackRegistries.holder(context.registries(), MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_GATED_ELEMENT).isEmpty();
        boolean plain = MxtDatapackRegistries.holder(context.registries(), MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_INERT_ELEMENT).isPresent();
        boolean gatedOut = gated && plain;
        String report = "element probe: condition-gated element absent=" + gated
                + " plain element present=" + plain;
        context.note(report + (gatedOut ? " OK" : " MISMATCH"));
        context.check(report, gatedOut);
    }

    // The conditions that read elements: by element, by element tag, and by what has built up. The aura leg asks the
    // environment for the same number first and brackets it, so it tests the aggregation rather than this world's stock.
    private static void conditions(ProbeContext context) {
        ServerLevel level = context.level();
        Holder<Element> fire = context.require(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_FIRE_ELEMENT);
        LivingEntity toggleProbe = spawnProbe(context, context.at(CASTER), ProbeDefinitions.PROBE_FIRE_ROOT);
        LivingEntity waterVictim = spawnProbe(context, context.at(SECOND_CASTER), ProbeDefinitions.PROBE_WATER_ROOT);
        LivingEntity inertHolder = spawnProbe(context, context.at(VICTIM), ProbeDefinitions.PROBE_INERT_ROOT);
        try {
            if (toggleProbe == null || waterVictim == null || inertHolder == null) {
                context.fail("element probe: could not create the probe entities");
                return;
            }
            boolean hasElement = new HasElementEntityCondition(List.of(Either.left(fire)))
                    .test(toggleProbe, FormulaContext.of(toggleProbe))
                    && !new HasElementEntityCondition(List.of(Either.left(fire)))
                    .test(waterVictim, FormulaContext.of(waterVictim))
                    && new HasElementEntityCondition(List.of(Either.right(elementTag())))
                    .test(toggleProbe, FormulaContext.of(toggleProbe));
            // A chunk emptied by an earlier run reads as "zero fire aura" and the condition is still expected to say so.
            AuraResult resolved = AuraService.getPositionAura(level, toggleProbe.blockPosition());
            double fireAura = resolved.aura().entrySet().stream()
                    .filter(entry -> entry.getKey().value().auraType().filter(fire::equals).isPresent())
                    .mapToDouble(entry -> entry.getValue().amount()).sum();
            boolean auraElement = new AuraElementEntityCondition(Map.of(fire, minimumRange(Math.max(0.0D, fireAura - 0.5D))))
                    .test(toggleProbe, FormulaContext.of(toggleProbe))
                    && !new AuraElementEntityCondition(Map.of(fire, minimumRange(fireAura + 0.5D)))
                    .test(toggleProbe, FormulaContext.of(toggleProbe));
            ElementReactionService.apply(inertHolder, fire, 3.0D, FormulaContext.of(inertHolder));
            boolean attachment = new ElementAttachmentEntityCondition(Map.of(fire, minimumRange(2.0D)))
                    .test(inertHolder, FormulaContext.of(inertHolder))
                    && !new ElementAttachmentEntityCondition(Map.of(fire, minimumRange(5.0D)))
                    .test(inertHolder, FormulaContext.of(inertHolder));
            boolean conditions = hasElement && auraElement && attachment;
            String report = "element probe: conditions has_element=" + hasElement
                    + " aura_element=" + auraElement + " (fire_aura=" + fireAura + ") attachment=" + attachment;
            context.note(report + (conditions ? " OK" : " MISMATCH"));
            context.check(report, conditions);
        } finally {
            if (toggleProbe != null) toggleProbe.discard();
            if (waterVictim != null) waterVictim.discard();
            if (inertHolder != null) inertHolder.discard();
        }
    }

    // The enable/disable module: off means held but inert, on means everything comes back, and a root the body does
    // not hold has no state to switch.
    private static void toggle(ProbeContext context) {
        Holder<Element> fire = context.require(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_FIRE_ELEMENT);
        Holder<SpiritRoot> fireRoot = context.require(MxtResourceKeys.SPIRIT_ROOT, ProbeDefinitions.PROBE_FIRE_ROOT);
        LivingEntity toggleProbe = spawnProbe(context, context.at(CASTER), ProbeDefinitions.PROBE_FIRE_ROOT);
        try {
            if (toggleProbe == null) {
                context.fail("element probe: could not create the probe entities");
                return;
            }
            boolean off = CultivationToggleService.setSpiritRootEnabled(toggleProbe, fireRoot, false).changed()
                    && !CultivationToggleService.isSpiritRootEnabled(toggleProbe, fireRoot)
                    && Elements.of(toggleProbe).isEmpty()
                    && toggleProbe.getData(MxtAttachments.SPIRIT_IDENTITY).spiritRoots().contains(fireRoot);
            boolean on = CultivationToggleService.setSpiritRootEnabled(toggleProbe, fireRoot, true).changed()
                    && CultivationToggleService.isSpiritRootEnabled(toggleProbe, fireRoot)
                    && Elements.of(toggleProbe).contains(fire);
            boolean unchanged = !CultivationToggleService.setSpiritRootEnabled(toggleProbe, fireRoot, true).changed();
            boolean notHeld = CultivationToggleService.setSpiritRootEnabled(toggleProbe,
                    context.require(MxtResourceKeys.SPIRIT_ROOT, ProbeDefinitions.PROBE_WATER_ROOT), false).failure()
                    == CultivationFailure.NOT_HELD;
            boolean toggle = off && on && unchanged && notHeld;
            String report = "element probe: toggle off=" + off + " on=" + on
                    + " unchanged=" + unchanged + " not_held=" + notHeld;
            context.note(report + (toggle ? " OK" : " MISMATCH"));
            context.check(report, toggle);
        } finally {
            if (toggleProbe != null) toggleProbe.discard();
        }
    }

    // A root that binds two elements: the body carries both (the single-element shape would have had to pick one),
    // the conflict rule is asked as a set from both sides, and the affinity follows whichever element the cast names.
    private static void multiElement(ProbeContext context) {
        Holder<Element> fire = context.require(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_FIRE_ELEMENT);
        Holder<Element> water = context.require(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_WATER_ELEMENT);
        Holder<Element> earth = context.require(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_EARTH_ELEMENT);
        Holder<SpiritRoot> dualRoot = context.require(MxtResourceKeys.SPIRIT_ROOT, ProbeDefinitions.PROBE_DUAL_ROOT);
        Holder<SpiritRoot> dualAntiWater = context.require(MxtResourceKeys.SPIRIT_ROOT, ProbeDefinitions.PROBE_ANTI_WATER_ROOT);
        Holder<SpiritRoot> dualInert = context.require(MxtResourceKeys.SPIRIT_ROOT, ProbeDefinitions.PROBE_INERT_ROOT);
        Holder<SpiritRoot> dualMetal = context.require(MxtResourceKeys.SPIRIT_ROOT, ProbeDefinitions.PROBE_METAL_ROOT);
        LivingEntity toggleProbe = spawnProbe(context, context.at(CASTER), ProbeDefinitions.PROBE_FIRE_ROOT);
        try {
            if (toggleProbe == null) {
                context.fail("element probe: could not create the probe entities");
                return;
            }
            boolean dualGranted = CultivationIdentityService.grantSpiritRoot(toggleProbe, dualRoot).failure() == null;
            SpiritIdentityAttachment dualIdentity = toggleProbe.getData(MxtAttachments.SPIRIT_IDENTITY);
            FormulaContext affinityFormula = FormulaContext.EMPTY;
            double onWater = CultivationAffinity.abilityMultiplier(dualIdentity, List.of(Either.left(water)), affinityFormula, Ability.AffinityMode.AVERAGE);
            double onEarth = CultivationAffinity.abilityMultiplier(dualIdentity, List.of(Either.left(earth)), affinityFormula, Ability.AffinityMode.AVERAGE);
            boolean dualConflicts = dualRoot.value().conflictsWith(dualAntiWater.value()) && dualRoot.value().conflictsWith(dualInert.value());
            boolean multiElement = dualGranted && Elements.of(toggleProbe).equals(Set.of(fire, water))
                    && close(onWater, 1.1D) && close(onEarth, 0.0D)
                    && dualConflicts && !dualRoot.value().conflictsWith(dualMetal.value());
            String report = "element probe: multi element water=" + onWater
                    + " earth=" + onEarth + " conflicts=" + dualConflicts;
            context.note(report + (multiElement ? " OK" : " MISMATCH"));
            context.check(report, multiElement);
        } finally {
            if (toggleProbe != null) toggleProbe.discard();
        }
    }

    // Weights are proportions, not multipliers: against the same fire-only place a 70/30 root draws a 0.7 share of
    // the pool and a 1/1 root draws 0.5. The aura result is built here, so the numbers do not depend on this world.
    private static void weights(ProbeContext context) {
        Holder<SpiritRoot> dualRoot = context.require(MxtResourceKeys.SPIRIT_ROOT, ProbeDefinitions.PROBE_DUAL_ROOT);
        Holder<SpiritRoot> dualEvenRoot = context.require(MxtResourceKeys.SPIRIT_ROOT, ProbeDefinitions.PROBE_DUAL_EVEN_ROOT);
        SpiritIdentityAttachment heavyFire = new SpiritIdentityAttachment();
        heavyFire.setSpiritRoots(List.of(dualRoot));
        SpiritIdentityAttachment evenRoots = new SpiritIdentityAttachment();
        evenRoots.setSpiritRoots(List.of(dualEvenRoot));
        // The source is deliberately absent: the affinity below never reads it, and naming a zone here would make
        // the numbers depend on what this world happens to hold after all.
        AuraResult fireOnly = new AuraResult(Map.of(context.require(MxtResourceKeys.AURA, ProbeDefinitions.SPIRIT_POWER), AuraPool.natural(1.0D, 1.0D, 0.0D)),
                AuraZone.Rules.DEFAULT, 0.0D, 0.0D, AlwaysCondition.INSTANCE, AuraZone.Distribution.EQUAL,
                null, AuraResult.SourceKind.CHUNK);
        double heavy = CultivationAffinity.multiplier(heavyFire, null, fireOnly, FormulaContext.EMPTY);
        double even = CultivationAffinity.multiplier(evenRoots, null, fireOnly, FormulaContext.EMPTY);
        double bareWeight = context.require(MxtResourceKeys.SPIRIT_ROOT, ProbeDefinitions.PROBE_FIRE_ROOT).value().elements().getFirst().weight();
        boolean weighted = close(heavy, 1.87D) && close(even, 1.65D) && close(bareWeight, 1.0D);
        String report = "element probe: weights 70/30=" + heavy + " 1/1=" + even + " bare=" + bareWeight;
        context.note(report + (weighted ? " OK" : " MISMATCH"));
        context.check(report, weighted);
    }

    // A reaction whose own action applies the element it just consumed feeds itself, which would have no floor
    // without the reentrancy guard: the nested application joins the chain already running for this body.
    private static void loop(ProbeContext context) {
        Holder<Element> water = context.require(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_WATER_ELEMENT);
        LivingEntity loopVictim = spawnProbe(context, context.at(CASTER), null);
        try {
            if (loopVictim == null) {
                context.fail("element probe: could not create the probe entities");
                return;
            }
            // Every pass takes the demand away and puts it straight back, so the body carries exactly what was
            // applied however many passes the bound allows.
            ElementReactionService.apply(loopVictim, water, 8.0D, FormulaContext.of(loopVictim));
            double loopLeft = ElementReactionService.amount(loopVictim, water);
            boolean loop = close(loopLeft, 8.0D);
            String report = "element probe: self-feeding reaction left=" + loopLeft;
            context.note(report + (loop ? " OK" : " MISMATCH"));
            context.check(report, loop);
        } finally {
            if (loopVictim != null) loopVictim.discard();
        }
    }

    // Who a spirit root rules out. The fixture root carries fire and declares both water and the inert element, so
    // one body answers all three rules: either element is refused, and a switched-off root is not in force either.
    private static void conflict(ProbeContext context) {
        Holder<SpiritRoot> antiWater = context.require(MxtResourceKeys.SPIRIT_ROOT, ProbeDefinitions.PROBE_ANTI_WATER_ROOT);
        Holder<SpiritRoot> probeWater = context.require(MxtResourceKeys.SPIRIT_ROOT, ProbeDefinitions.PROBE_WATER_ROOT);
        Holder<SpiritRoot> probeInert = context.require(MxtResourceKeys.SPIRIT_ROOT, ProbeDefinitions.PROBE_INERT_ROOT);
        LivingEntity conflictProbe = spawnProbe(context, context.at(CASTER), null);
        try {
            if (conflictProbe == null) {
                context.fail("element probe: could not create the probe entities");
                return;
            }
            boolean blocked = CultivationIdentityService.grantSpiritRoot(conflictProbe, antiWater).failure() == null
                    && CultivationIdentityService.grantSpiritRoot(conflictProbe, probeWater).failure()
                    == CultivationIdentityFailure.ELEMENT_CONFLICT;
            boolean inertBlocked = CultivationIdentityService.grantSpiritRoot(conflictProbe, probeInert).failure()
                    == CultivationIdentityFailure.ELEMENT_CONFLICT;
            boolean reopened = CultivationToggleService.setSpiritRootEnabled(conflictProbe, antiWater, false).changed()
                    && CultivationIdentityService.grantSpiritRoot(conflictProbe, probeWater).failure() == null;
            boolean conflict = blocked && inertBlocked && reopened;
            String report = "element probe: conflict blocked=" + blocked
                    + " inert_blocked=" + inertBlocked + " reopened=" + reopened;
            context.note(report + (conflict ? " OK" : " MISMATCH"));
            context.check(report, conflict);
        } finally {
            if (conflictProbe != null) conflictProbe.discard();
        }
    }

    // The five-phase set the test package ships: metal beats wood in the shaping layer and wood is soft against metal
    // in the reduction layer, so the same 4 lands as 4 * 1.4 * 1.25 = 7 while the opposite direction lands as 4.
    private static void fivePhases(ProbeContext context) {
        Holder<Element> metal = context.require(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_METAL_ELEMENT);
        LivingEntity metalCaster = spawnProbe(context, context.at(CASTER), ProbeDefinitions.PROBE_METAL_ROOT);
        LivingEntity woodVictim = spawnProbe(context, context.at(VICTIM), ProbeDefinitions.PROBE_WOOD_ROOT);
        LivingEntity woodCaster = spawnProbe(context, context.at(SECOND_CASTER), ProbeDefinitions.PROBE_WOOD_ROOT);
        LivingEntity metalVictim = spawnProbe(context, context.at(SECOND_VICTIM), ProbeDefinitions.PROBE_METAL_ROOT);
        try {
            if (metalCaster == null || woodVictim == null || woodCaster == null || metalVictim == null) {
                context.fail("element probe: could not create the probe entities");
                return;
            }
            // Neither strike declares a damage type, so both read their element off the striker's roots - and a
            // roots reading reduces without rubbing off, which is why metal_left is 0.
            double metalBefore = woodVictim.getHealth();
            DamageCalculationService.deal(metalCaster, woodVictim, 4.0D, Optional.empty(), FormulaContext.of(metalCaster));
            double metalOnWood = metalBefore - woodVictim.getHealth();
            double woodBefore = metalVictim.getHealth();
            DamageCalculationService.deal(woodCaster, metalVictim, 4.0D, Optional.empty(), FormulaContext.of(woodCaster));
            double woodOnMetal = woodBefore - metalVictim.getHealth();
            double metalLeft = ElementReactionService.amount(woodVictim, metal);
            boolean fivePhases = close(metalOnWood, 7.0D) && close(woodOnMetal, 4.0D) && close(metalLeft, 0.0D)
                    && new HasElementEntityCondition(List.of(Either.right(fivePhasesTag())))
                    .test(metalCaster, FormulaContext.of(metalCaster));
            String report = "element probe: five phases metal_on_wood=" + metalOnWood
                    + " wood_on_metal=" + woodOnMetal + " metal_left=" + metalLeft;
            context.note(report + (fivePhases ? " OK" : " MISMATCH"));
            context.check(report, fivePhases);
        } finally {
            if (metalCaster != null) metalCaster.discard();
            if (woodVictim != null) woodVictim.discard();
            if (woodCaster != null) woodCaster.discard();
            if (metalVictim != null) metalVictim.discard();
        }
    }

    // A reaction demanding two elements at once, spending only one and acting twice: wood alone does not answer it,
    // and afterwards the water that completed the demand is still there while the earth it left has not.
    private static void bloom(ProbeContext context) {
        Holder<Element> wood = context.require(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_WOOD_ELEMENT);
        Holder<Element> water = context.require(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_WATER_ELEMENT);
        Holder<Element> earth = context.require(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_EARTH_ELEMENT);
        LivingEntity bloomVictim = spawnProbe(context, context.at(CASTER), null);
        try {
            if (bloomVictim == null) {
                context.fail("element probe: could not create the probe entities");
                return;
            }
            ElementReactionService.apply(bloomVictim, wood, 6.0D, FormulaContext.of(bloomVictim));
            boolean halfDemand = close(ElementReactionService.amount(bloomVictim, wood), 6.0D);
            double bloomBefore = bloomVictim.getHealth();
            ElementReactionService.apply(bloomVictim, water, 4.0D, FormulaContext.of(bloomVictim));
            double bloomLost = bloomBefore - bloomVictim.getHealth();
            double woodLeft = ElementReactionService.amount(bloomVictim, wood);
            double waterLeft = ElementReactionService.amount(bloomVictim, water);
            double earthLeft = ElementReactionService.amount(bloomVictim, earth);
            boolean bloom = halfDemand && close(woodLeft, 0.0D) && close(waterLeft, 4.0D)
                    && close(earthLeft, 2.0D) && close(bloomLost, 3.0D);
            String report = "element probe: two-element reaction half=" + halfDemand
                    + " wood=" + woodLeft + " water=" + waterLeft + " earth=" + earthLeft + " damage=" + bloomLost;
            context.note(report + (bloom ? " OK" : " MISMATCH"));
            context.check(report, bloom);
        } finally {
            if (bloomVictim != null) bloomVictim.discard();
        }
    }

    // A reaction that consumes nothing answers the same demand on every pass, so one application fires it exactly as
    // many times as the chain allows and leaves the buildup untouched. The count is the documented bound.
    private static void settle(ProbeContext context) {
        Holder<Element> earth = context.require(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_EARTH_ELEMENT);
        LivingEntity settleVictim = spawnProbe(context, context.at(CASTER), null);
        try {
            if (settleVictim == null) {
                context.fail("element probe: could not create the probe entities");
                return;
            }
            settleVictim.getData(MxtAttachments.ELEMENT_ATTACHMENT).add(earth, 10.0D);
            int settleFired = ElementReactionService.trigger(settleVictim, FormulaContext.of(settleVictim));
            double settleLeft = ElementReactionService.amount(settleVictim, earth);
            boolean settle = settleFired == 8 && close(settleLeft, 10.0D);
            String report = "element probe: unconsumed reaction fired=" + settleFired + " left=" + settleLeft;
            context.note(report + (settle ? " OK" : " MISMATCH"));
            context.check(report, settle);
        } finally {
            if (settleVictim != null) settleVictim.discard();
        }
    }

    // What an item is made of: the sword's weapon binding declares fire, the jade declares the whole #mxt_test:basic
    // tag, the spirit crystal is read off the aura it carries, and a stick matches nothing. The condition agrees.
    private static void itemElement(ProbeContext context) {
        Holder<Element> fire = context.require(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_FIRE_ELEMENT);
        Holder<Element> water = context.require(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_WATER_ELEMENT);
        LivingEntity toggleProbe = spawnProbe(context, context.at(CASTER), ProbeDefinitions.PROBE_FIRE_ROOT);
        try {
            if (toggleProbe == null) {
                context.fail("element probe: could not create the probe entities");
                return;
            }
            Set<Holder<Element>> swordElements = ItemElements.of(context.registries(),
                    new ItemStack(Items.DIAMOND_SWORD));
            Set<Holder<Element>> jadeElements = ItemElements.of(context.registries(),
                    new ItemStack(Items.AMETHYST_SHARD));
            Set<Holder<Element>> crystalElements = ItemElements.of(context.registries(),
                    MxtTestItems.QINGXIAO_SPIRIT_CRYSTAL.toStack());
            Set<Holder<Element>> plainElements = ItemElements.of(context.registries(), new ItemStack(Items.STICK));
            boolean itemElement = swordElements.equals(Set.of(fire)) && jadeElements.equals(Set.of(fire, water))
                    && crystalElements.equals(Set.of(fire)) && plainElements.isEmpty()
                    && new ItemElementCondition(List.of(Either.left(water)))
                    .test(toggleProbe, new ItemStack(Items.AMETHYST_SHARD), FormulaContext.of(toggleProbe))
                    && !new ItemElementCondition(List.of(Either.left(water)))
                    .test(toggleProbe, new ItemStack(Items.DIAMOND_SWORD), FormulaContext.of(toggleProbe));
            String report = "element probe: item element sword=" + elementIds(swordElements)
                    + " jade=" + elementIds(jadeElements) + " crystal=" + elementIds(crystalElements)
                    + " plain=" + plainElements.size();
            context.note(report + (itemElement ? " OK" : " MISMATCH"));
            context.check(report, itemElement);
        } finally {
            if (toggleProbe != null) toggleProbe.discard();
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

    private static TagKey<Element> elementTag() {
        return TagKey.create(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_ELEMENT_TAG);
    }

    // Sorted, for a probe line that has to show which elements an item was read as rather than only how many.
    private static List<String> elementIds(Set<Holder<Element>> elements) {
        return elements.stream().map(HolderHelper::id).map(Object::toString).sorted().toList();
    }

    private static TagKey<Element> fivePhasesTag() {
        return TagKey.create(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_FIVE_PHASES_TAG);
    }

    private static AuraRequirement minimumRange(double minimum) {
        return new AuraRequirement(new Constant(minimum), new Constant(1.0E9D));
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }
}
