package com.iafenvoy.mxt.probe.cultivation;

import com.google.gson.JsonParser;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.TechniqueAttachment;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.CultivatingEntityCondition;
import com.iafenvoy.mxt.data.cultivation.Cultivation;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.event.FriendEvent;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.probe.ProbeKit;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationMethodService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationMethodService.CultivationResult;
import com.iafenvoy.mxt.runtime.cultivation.CultivationModeService;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueService;
import com.iafenvoy.mxt.runtime.resulting.Failures;
import com.iafenvoy.mxt.runtime.resulting.failures.TechniqueFailure;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;
import java.util.function.Consumer;

/**
 * The cultivation-method pick: which method a body selects, what each of the three conditions answers, and what a
 * settlement does when one of them says no. One test per sub-scenario, against disposable probe bodies on the grid.
 *
 * <p>The old orchestration built its state up leg by leg; here each test sets up only what it reads, and the legs
 * that shared state (settlement, state, stop) stay together because one cannot be reached without the other.
 */
public final class CultivationProbes {
    private CultivationProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.on(Probe.GRID, "cultivation/method/baseline", CultivationProbes::baseline), Probe.on(Probe.GRID, "cultivation/method/technique", CultivationProbes::technique), Probe.on(Probe.GRID, "cultivation/method/attachment", CultivationProbes::attachment), Probe.on(Probe.GRID, "cultivation/method/upkeep", CultivationProbes::upkeep), Probe.on(Probe.GRID, "cultivation/method/pair", CultivationProbes::pair), Probe.on(Probe.GRID, "cultivation/method/partner", CultivationProbes::partner), Probe.on(Probe.GRID, "cultivation/method/actions", CultivationProbes::actions), Probe.on(Probe.GRID, "cultivation/method/pick", CultivationProbes::pick), Probe.on(Probe.GRID, "cultivation/method/forget", CultivationProbes::forget), Probe.on(Probe.GRID, "cultivation/method/mutual", CultivationProbes::mutual));
    }

    // 1. Asking starts nothing, and the method that asks for nothing (requirement ②) answers for a body with no
    // technique at all: a joined body already carries an empty record, so the claim is that no session was written.
    // 2. A method whose own condition says no is neither selectable nor usable.
    private static void baseline(ProbeContext context) {
        Holder<Cultivation> gated = context.require(MxtResourceKeys.CULTIVATION, ProbeDefinitions.TECHNIQUE_MEDITATION);
        LivingEntity solo = spawnProbe(context, context.grid(0));
        List<LivingEntity> probes = new ArrayList<>();
        for (LivingEntity probe : Collections.singletonList(solo)) if (probe != null) probes.add(probe);
        if (!created(context, probes, 1)) return;
        try {
            boolean pickedFree = picks(CultivationModeService.select(solo, contextOf(solo)), ProbeDefinitions.FREE_MEDITATION);
            boolean untouched = solo.getExistingData(MxtAttachments.CULTIVATION).map(spirit -> !spirit.cultivating() && spirit.cultivation().isEmpty()).orElse(true);
            report(context, "cultivation probe: baseline pick=" + pickedFree + " attach=" + !untouched, pickedFree && untouched);

            boolean gatedOut = !asks(gated, solo) && !gated.value().startCondition().test(solo, contextOf(solo));
            report(context, "cultivation probe: gated_out=" + gatedOut, gatedOut);
        } finally {
            for (LivingEntity probe : probes) probe.discard();
        }
    }

    // 3. mxt:technique reads the learned list: the tag entry, the named entry, all of them, and none.
    private static void technique(ProbeContext context) {
        Holder<Cultivation> gated = context.require(MxtResourceKeys.CULTIVATION, ProbeDefinitions.TECHNIQUE_MEDITATION);
        Holder<Cultivation> named = context.require(MxtResourceKeys.CULTIVATION, ProbeDefinitions.NAMED_MEDITATION);
        Holder<Cultivation> strict = context.require(MxtResourceKeys.CULTIVATION, ProbeDefinitions.STRICT_MEDITATION);
        Holder<Cultivation> worldly = context.require(MxtResourceKeys.CULTIVATION, ProbeDefinitions.WORLDLY_MEDITATION);
        Holder<Technique> breathing = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.TECHNIQUE);
        Holder<Technique> sword = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.SWORD_MANUAL);
        LivingEntity learner = spawnProbe(context, context.grid(1));
        List<LivingEntity> probes = new ArrayList<>();
        for (LivingEntity probe : Collections.singletonList(learner)) if (probe != null) probes.add(probe);
        if (!created(context, probes, 1)) return;
        try {
            boolean noneLearned = !asks(named, learner) && !asks(strict, learner) && asks(worldly, learner);
            boolean learnedBreathing = TechniqueService.learn(learner, learner.getData(MxtAttachments.TECHNIQUE), breathing, contextOf(learner)).failure() == null;
            boolean tagOnly = asks(gated, learner) && !asks(named, learner) && !asks(strict, learner) && !asks(worldly, learner);
            boolean learnedSword = TechniqueService.learn(learner, learner.getData(MxtAttachments.TECHNIQUE), sword, contextOf(learner)).failure() == null;
            boolean allOf = asks(named, learner) && asks(strict, learner);
            report(context, "cultivation probe: technique none=" + noneLearned + " learned=" + (learnedBreathing && learnedSword) + " tag=" + tagOnly + " all=" + allOf, noneLearned && learnedBreathing && tagOnly && learnedSword && allOf);
        } finally {
            for (LivingEntity probe : probes) probe.discard();
        }
    }

    // 4. What the attachment holds is a record, not a preference: the moment a higher-priority method becomes
    // applicable the pick moves, while the stored one is still the one running.
    private static void attachment(ProbeContext context) {
        Holder<Cultivation> free = context.require(MxtResourceKeys.CULTIVATION, ProbeDefinitions.FREE_MEDITATION);
        Holder<Technique> breathing = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.TECHNIQUE);
        LivingEntity veteran = spawnProbe(context, context.grid(2));
        List<LivingEntity> probes = new ArrayList<>();
        for (LivingEntity probe : Collections.singletonList(veteran)) if (probe != null) probes.add(probe);
        if (!created(context, probes, 1)) return;
        try {
            CultivationAttachment veteranSpirit = veteran.getData(MxtAttachments.CULTIVATION);
            boolean seatedFree = CultivationModeService.start(veteran, veteranSpirit, free, contextOf(veteran)).started() && runs(veteranSpirit, ProbeDefinitions.FREE_MEDITATION);
            TechniqueService.learn(veteran, veteran.getData(MxtAttachments.TECHNIQUE), breathing, contextOf(veteran));
            boolean movedOn = picks(CultivationModeService.select(veteran, contextOf(veteran)), ProbeDefinitions.TECHNIQUE_MEDITATION) && runs(veteranSpirit, ProbeDefinitions.FREE_MEDITATION);
            CultivationModeService.stop(veteran, veteranSpirit, free);
            report(context, "cultivation probe: attachment seated=" + seatedFree + " moved=" + movedOn + " still_running=" + veteranSpirit.cultivating(), seatedFree && movedOn && !veteranSpirit.cultivating());
        } finally {
            for (LivingEntity probe : probes) probe.discard();
        }
    }

    // 5. What admits a body is start_condition plus the yield condition, never the upkeep one: A sits down while B
    // is not cultivating, and the first settlement aborts on that very upkeep condition.
    private static void upkeep(ProbeContext context) {
        long now = context.level().getGameTime();
        Holder<Cultivation> dual = context.require(MxtResourceKeys.CULTIVATION, ProbeDefinitions.DUAL_MEDITATION);
        Holder<Technique> breathing = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.TECHNIQUE);
        ItemStack carrier = techniqueBook(breathing);
        LivingEntity seatA = spawnProbe(context, context.grid(3));
        LivingEntity seatB = spawnProbe(context, context.grid(3).offset(3, 0, 0));
        List<LivingEntity> probes = new ArrayList<>();
        for (LivingEntity probe : Arrays.asList(seatA, seatB)) if (probe != null) probes.add(probe);
        if (!created(context, probes, 2)) return;
        seatB.setItemInHand(InteractionHand.MAIN_HAND, carrier.copy());
        Set<UUID> friends = friends(probes);
        Consumer<FriendEvent.Relation> relation = relations(friends, new HashSet<>());
        NeoForge.EVENT_BUS.addListener(relation);
        try {
            CultivationAttachment seatSpirit = seatA.getData(MxtAttachments.CULTIVATION);
            boolean yieldHolds = asks(dual, seatA);
            boolean upkeepFalse = !dual.value().tickCondition().test(seatA, contextOf(seatA));
            boolean seated = CultivationModeService.start(seatA, seatSpirit, dual, contextOf(seatA)).started();
            CultivationResult upkeepTick = tickCultivation(seatA, seatSpirit, dual, now);
            boolean aborted = !seatSpirit.cultivating() && upkeepTick.failure() == Failures.CONDITIONS && named(upkeepTick, "the partner is gone");
            report(context, "cultivation probe: upkeep yield=" + yieldHolds + " false=" + upkeepFalse + " seated=" + seated + " failure=" + upkeepTick.failure() + " reason=" + (upkeepTick.abortReason() == null ? "none" : upkeepTick.abortReason().getString()), yieldHolds && upkeepFalse && seated && aborted);
        } finally {
            NeoForge.EVENT_BUS.unregister(relation);
            for (LivingEntity probe : probes) probe.discard();
        }
    }

    // 6. Two bodies each hold a manual, so both sit down: the yield test asks what the partner holds, not whether
    // the partner is already seated. The two resources have flat maxima, so nothing here needs a realm.
    // 7. Taking the manual away turns the due settlement into a no-op, and the same settlement yields once it is back.
    // 9. mxt:cultivating reads the run that is on the body, and can name one method.
    // 11. Both sessions of the pair are put away by the stored method, and the method that asks for nothing still
    // answers for a body that has techniques (the regression baseline of requirement ②).
    private static void pair(ProbeContext context) {
        long now = context.level().getGameTime();
        Holder<Cultivation> free = context.require(MxtResourceKeys.CULTIVATION, ProbeDefinitions.FREE_MEDITATION);
        Holder<Cultivation> dual = context.require(MxtResourceKeys.CULTIVATION, ProbeDefinitions.DUAL_MEDITATION);
        Holder<Technique> breathing = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.TECHNIQUE);
        Holder<Technique> sword = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.SWORD_MANUAL);
        Holder<Resource> waterPower = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.WATER_POWER);
        Holder<Resource> mastery = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.SWORD_MASTERY);
        ItemStack carrier = techniqueBook(breathing);
        LivingEntity runA = spawnProbe(context, context.grid(4));
        LivingEntity runB = spawnProbe(context, context.grid(4).offset(3, 0, 0));
        LivingEntity learner = spawnProbe(context, context.grid(1));
        LivingEntity solo = spawnProbe(context, context.grid(0));
        LivingEntity lonely = spawnProbe(context, context.grid(8));
        List<LivingEntity> probes = new ArrayList<>();
        for (LivingEntity probe : Arrays.asList(runA, runB, learner, solo, lonely))
            if (probe != null) probes.add(probe);
        if (!created(context, probes, 5)) return;
        for (LivingEntity probe : Arrays.asList(runA, runB))
            probe.setItemInHand(InteractionHand.MAIN_HAND, carrier.copy());
        // The baseline leg reads a body that has techniques, which is what its own source leg taught a learner.
        TechniqueService.learn(learner, learner.getData(MxtAttachments.TECHNIQUE), breathing, contextOf(learner));
        TechniqueService.learn(learner, learner.getData(MxtAttachments.TECHNIQUE), sword, contextOf(learner));
        Set<UUID> friends = friends(probes);
        Consumer<FriendEvent.Relation> relation = relations(friends, new HashSet<>());
        NeoForge.EVENT_BUS.addListener(relation);
        try {
            CultivationAttachment runSpiritA = runA.getData(MxtAttachments.CULTIVATION);
            CultivationAttachment runSpiritB = runB.getData(MxtAttachments.CULTIVATION);
            ResourceHolderAttachment runResources = runA.getData(MxtAttachments.RESOURCE_HOLDER);
            ResourceHolderAttachment otherResources = runB.getData(MxtAttachments.RESOURCE_HOLDER);
            ProbeKit.ensureResource(runA, runResources, waterPower, 10.0D);
            ProbeKit.ensureResource(runA, runResources, mastery, 0.0D);
            ProbeKit.ensureResource(runB, otherResources, waterPower, 10.0D);
            boolean bothSeated = CultivationModeService.start(runA, runSpiritA, dual, contextOf(runA)).started() && CultivationModeService.start(runB, runSpiritB, dual, contextOf(runB)).started();
            CultivationResult firstA = tickCultivation(runA, runSpiritA, dual, now);
            CultivationResult firstB = tickCultivation(runB, runSpiritB, dual, now);
            boolean bothPaid = firstA.progressed() && firstB.progressed() && close(runResources.get(waterPower), 9.0D) && close(runResources.get(mastery), 1.0D);
            report(context, "cultivation probe: pair seated=" + bothSeated + " progressed=" + firstA.progressed() + "/" + firstB.progressed() + " power=" + runResources.get(waterPower) + " mastery=" + runResources.get(mastery), bothSeated && bothPaid);

            ItemStack held = runB.getMainHandItem().copy();
            runB.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            long due = runSpiritA.nextCultivateTick();
            double powerBefore = runResources.get(waterPower);
            double masteryBefore = runResources.get(mastery);
            CultivationResult skipped = tickCultivation(runA, runSpiritA, dual, due);
            boolean noYield = skipped.waiting() && !skipped.progressed() && runSpiritA.cultivating() && close(runResources.get(waterPower), powerBefore) && close(runResources.get(mastery), masteryBefore) && runSpiritA.nextCultivateTick() == due;
            runB.setItemInHand(InteractionHand.MAIN_HAND, held);
            CultivationResult resumed = tickCultivation(runA, runSpiritA, dual, due);
            boolean yieldsAgain = resumed.progressed() && close(runResources.get(waterPower), powerBefore - 1.0D) && close(runResources.get(mastery), masteryBefore + 1.0D) && runSpiritA.nextCultivateTick() == due + 20L;
            report(context, "cultivation probe: skipped waiting=" + skipped.waiting() + " resumed=" + yieldsAgain + " power=" + runResources.get(waterPower) + " mastery=" + runResources.get(mastery) + " next_in=" + (runSpiritA.nextCultivateTick() - due), noYield && yieldsAgain);

            boolean state = new CultivatingEntityCondition(Optional.empty()).test(runA, contextOf(runA)) && new CultivatingEntityCondition(Optional.of(dual)).test(runB, contextOf(runB)) && !new CultivatingEntityCondition(Optional.of(free)).test(runA, contextOf(runA)) && !new CultivatingEntityCondition(Optional.empty()).test(lonely, contextOf(lonely));
            report(context, "cultivation probe: state=" + state, state);

            CultivationModeService.stop(runA, runSpiritA, dual);
            CultivationModeService.stop(runB, runSpiritB, dual);
            boolean baselineKept = asks(free, learner) && asks(free, solo);
            report(context, "cultivation probe: stop runs=" + runSpiritA.cultivating() + "/" + runSpiritB.cultivating() + " baseline=" + baselineKept, !runSpiritA.cultivating() && !runSpiritB.cultivating() && baselineKept);
        } finally {
            NeoForge.EVENT_BUS.unregister(relation);
            for (LivingEntity probe : probes) probe.discard();
        }
    }

    // 8. mxt:partner asks about the candidate: the manual has to be in the partner's own hand, the partner has to
    // be inside the range, and count decides how many of them there may be.
    private static void partner(ProbeContext context) {
        Holder<Cultivation> dual = context.require(MxtResourceKeys.CULTIVATION, ProbeDefinitions.DUAL_MEDITATION);
        Holder<Technique> breathing = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.TECHNIQUE);
        ItemStack carrier = techniqueBook(breathing);
        LivingEntity bareA = spawnProbe(context, context.grid(5));
        LivingEntity bareB = spawnProbe(context, context.grid(5).offset(3, 0, 0));
        LivingEntity crowdA = spawnProbe(context, context.grid(6));
        LivingEntity crowd1 = spawnProbe(context, context.grid(6).offset(3, 0, 0));
        LivingEntity crowd2 = spawnProbe(context, context.grid(6).offset(0, 0, 3));
        LivingEntity crowd3 = spawnProbe(context, context.grid(6).offset(3, 0, 3));
        LivingEntity rangeA = spawnProbe(context, context.grid(7));
        LivingEntity rangeFar = spawnProbe(context, context.grid(7).above(8));
        LivingEntity lonely = spawnProbe(context, context.grid(8));
        List<LivingEntity> probes = new ArrayList<>();
        for (LivingEntity probe : Arrays.asList(bareA, bareB, crowdA, crowd1, crowd2, crowd3, rangeA, rangeFar, lonely))
            if (probe != null) probes.add(probe);
        if (!created(context, probes, 9)) return;
        for (LivingEntity probe : Arrays.asList(bareA, crowd1, crowd2, crowd3, rangeFar))
            probe.setItemInHand(InteractionHand.MAIN_HAND, carrier.copy());
        // The two extra crowd members join the friend pool only for the count leg.
        Set<UUID> friends = friends(probes);
        friends.remove(crowd2.getUUID());
        friends.remove(crowd3.getUUID());
        Consumer<FriendEvent.Relation> relation = relations(friends, new HashSet<>());
        NeoForge.EVENT_BUS.addListener(relation);
        try {
            boolean manualOnPartner = !asks(dual, bareA);
            boolean outOfRange = !asks(dual, rangeA);
            boolean noPartner = !asks(dual, lonely);
            boolean onePartner = asks(dual, crowdA);
            friends.add(crowd2.getUUID());
            friends.add(crowd3.getUUID());
            boolean tooMany = !asks(dual, crowdA);
            report(context, "cultivation probe: partner manual=" + !manualOnPartner + " far=" + outOfRange + " none=" + noPartner + " one=" + onePartner + " crowd=" + tooMany, manualOnPartner && outOfRange && noPartner && onePartner && tooMany);
        } finally {
            NeoForge.EVENT_BUS.unregister(relation);
            for (LivingEntity probe : probes) probe.discard();
        }
    }

    // 10. The two actions are the pack's own way in and out: the first picks, the second stops and writes the
    // method's own cooldown, which a restart inside it runs into and one after it does not.
    private static void actions(ProbeContext context) {
        long now = context.level().getGameTime();
        Holder<Cultivation> free = context.require(MxtResourceKeys.CULTIVATION, ProbeDefinitions.FREE_MEDITATION);
        Holder<Technique> breathing = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.TECHNIQUE);
        Holder<Technique> sword = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.SWORD_MANUAL);
        ItemStack carrier = techniqueBook(breathing);
        LivingEntity actor = spawnProbe(context, context.grid(9));
        LivingEntity learner = spawnProbe(context, context.grid(1));
        LivingEntity lonely = spawnProbe(context, context.grid(8));
        List<LivingEntity> probes = new ArrayList<>();
        for (LivingEntity probe : Arrays.asList(actor, learner, lonely)) if (probe != null) probes.add(probe);
        if (!created(context, probes, 3)) return;
        actor.setItemInHand(InteractionHand.MAIN_HAND, carrier.copy());
        // The named form needs a body that has the manual, which is what its own source leg taught a learner.
        TechniqueService.learn(learner, learner.getData(MxtAttachments.TECHNIQUE), breathing, contextOf(learner));
        TechniqueService.learn(learner, learner.getData(MxtAttachments.TECHNIQUE), sword, contextOf(learner));
        try {
            CultivationAttachment actorSpirit = actor.getData(MxtAttachments.CULTIVATION);
            entityAction(context.level(), "{\"type\": \"mxt:cultivate\"}").execute(actor, contextOf(actor));
            boolean actionStarted = runs(actorSpirit, ProbeDefinitions.FREE_MEDITATION);
            entityAction(context.level(), "{\"type\": \"mxt:stop_cultivating\"}").execute(actor, contextOf(actor));
            boolean actionStopped = !actorSpirit.cultivating() && actorSpirit.isCultivationOnCooldown(free, now);
            boolean coolingDown = !CultivationMethodService.start(actorSpirit, free, free.value(), now, () -> true).started();
            boolean cooldownOver = CultivationMethodService.start(actorSpirit, free, free.value(), now + 200L, () -> true).started();
            // The named form starts that very method for a body that has the manual, and stays silent for one that
            // does not: nothing in the data pack can hand a failure back.
            entityAction(context.level(), "{\"type\": \"mxt:cultivate\", \"action\": \"mxt_test:named_meditation\"}").execute(learner, contextOf(learner));
            boolean namedStarted = runs(learner.getData(MxtAttachments.CULTIVATION), ProbeDefinitions.NAMED_MEDITATION);
            entityAction(context.level(), "{\"type\": \"mxt:cultivate\", \"action\": \"mxt_test:named_meditation\"}").execute(lonely, contextOf(lonely));
            boolean namedSilent = lonely.getData(MxtAttachments.CULTIVATION).cultivation().isEmpty();
            report(context, "cultivation probe: actions start=" + actionStarted + " stop=" + actionStopped + " cooling=" + coolingDown + " expired=" + cooldownOver + " named=" + namedStarted + " silent=" + namedSilent, actionStarted && actionStopped && coolingDown && cooldownOver && namedStarted && namedSilent);
        } finally {
            for (LivingEntity probe : probes) probe.discard();
        }
    }

    // 12. The explicit pick is one start, not a preference: the named method runs although the selector would
    // choose another, and a pick that does not apply is refused without disturbing the session already running.
    private static void pick(ProbeContext context) {
        Holder<Cultivation> free = context.require(MxtResourceKeys.CULTIVATION, ProbeDefinitions.FREE_MEDITATION);
        Holder<Cultivation> gated = context.require(MxtResourceKeys.CULTIVATION, ProbeDefinitions.TECHNIQUE_MEDITATION);
        Holder<Cultivation> named = context.require(MxtResourceKeys.CULTIVATION, ProbeDefinitions.NAMED_MEDITATION);
        Holder<Cultivation> basic = context.require(MxtResourceKeys.CULTIVATION, ProbeDefinitions.CULTIVATE);
        Holder<Technique> breathing = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.TECHNIQUE);
        Holder<Technique> sword = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.SWORD_MANUAL);
        ItemStack carrier = techniqueBook(breathing);
        LivingEntity veteran = spawnProbe(context, context.grid(2));
        LivingEntity actor = spawnProbe(context, context.grid(9));
        LivingEntity learner = spawnProbe(context, context.grid(1));
        List<LivingEntity> probes = new ArrayList<>();
        for (LivingEntity probe : Arrays.asList(veteran, actor, learner)) if (probe != null) probes.add(probe);
        if (!created(context, probes, 3)) return;
        actor.setItemInHand(InteractionHand.MAIN_HAND, carrier.copy());
        // This leg reads three bodies the earlier ones left mid-scenario: veteran learned the breathing manual and is
        // off its own free session, actor runs free meditation, and learner runs the named method.
        CultivationAttachment veteranSpirit = veteran.getData(MxtAttachments.CULTIVATION);
        CultivationModeService.start(veteran, veteranSpirit, free, contextOf(veteran));
        TechniqueService.learn(veteran, veteran.getData(MxtAttachments.TECHNIQUE), breathing, contextOf(veteran));
        CultivationModeService.stop(veteran, veteranSpirit, free);
        TechniqueService.learn(learner, learner.getData(MxtAttachments.TECHNIQUE), sword, contextOf(learner));
        CultivationModeService.start(learner, learner.getData(MxtAttachments.CULTIVATION), named, contextOf(learner));
        CultivationAttachment actorSpirit = actor.getData(MxtAttachments.CULTIVATION);
        CultivationModeService.start(actor, actorSpirit, free, contextOf(actor));
        try {
            CultivationAttachment pickSpirit = veteran.getData(MxtAttachments.CULTIVATION);
            CultivationResult namedPick = CultivationModeService.startNamed(veteran, basic);
            boolean pickedAnyway = namedPick.started() && runs(pickSpirit, ProbeDefinitions.CULTIVATE) && picks(CultivationModeService.select(veteran, contextOf(veteran)), ProbeDefinitions.TECHNIQUE_MEDITATION);
            boolean pickRefused = CultivationModeService.startNamed(actor, gated).failure() == Failures.NOT_APPLICABLE && runs(actorSpirit, ProbeDefinitions.FREE_MEDITATION);
            boolean pickRunning = CultivationModeService.startNamed(learner, named).failure() == Failures.ALREADY_ACTIVE;
            report(context, "cultivation probe: pick named=" + pickedAnyway + " refused=" + pickRefused + " running=" + pickRunning + " failure=" + namedPick.failure(), pickedAnyway && pickRefused && pickRunning);
        } finally {
            for (LivingEntity probe : probes) probe.discard();
        }
    }

    // 13. Forgetting takes the technique and its own stage record, and rebuilds what it granted; the realm stage
    // and everything else in the body are other state and stay exactly where they were.
    private static void forget(ProbeContext context) {
        Holder<Technique> breathing = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.TECHNIQUE);
        Holder<Technique> sword = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.SWORD_MANUAL);
        ItemStack carrier = techniqueBook(breathing);
        LivingEntity bareA = spawnProbe(context, context.grid(5));
        List<LivingEntity> probes = new ArrayList<>();
        for (LivingEntity probe : Collections.singletonList(bareA)) if (probe != null) probes.add(probe);
        if (!created(context, probes, 1)) return;
        bareA.setItemInHand(InteractionHand.MAIN_HAND, carrier.copy());
        try {
            TechniqueAttachment bareIdentity = bareA.getData(MxtAttachments.TECHNIQUE);
            CultivationAttachment bareSpirit = bareA.getData(MxtAttachments.CULTIVATION);
            AbilityAttachment bareAbilities = bareA.getData(MxtAttachments.ABILITY_HOLDER);
            bareSpirit.setRealmStage(context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.QI_REFINING));
            boolean bareLearned = TechniqueService.learn(bareA, bareIdentity, sword, contextOf(bareA)).failure() == null;
            bareA.getData(MxtAttachments.TECHNIQUE).techniqueLevels().set(sword, context.require(MxtResourceKeys.PROGRESSION, ProbeDefinitions.PROBE_PROGRESSION_LEVEL));
            boolean bareGranted = bareAbilities.has(context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.PROBE_ABILITY));
            boolean forgotten = TechniqueService.forget(bareA, bareIdentity, sword).failure() == null;
            boolean gone = bareIdentity.learnedTechniques().stream().noneMatch(technique -> HolderHelper.id(technique).equals(ProbeDefinitions.SWORD_MANUAL)) && bareIdentity.techniqueLevels().levels().keySet().stream().noneMatch(owner -> HolderHelper.id(owner).equals(ProbeDefinitions.SWORD_MANUAL)) && !bareAbilities.has(context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.PROBE_ABILITY)) && !bareSpirit.realmStages().isEmpty();
            boolean absent = TechniqueService.forget(bareA, bareIdentity, sword).failure() == TechniqueFailure.ABSENT;
            report(context, "cultivation probe: forget learned=" + bareLearned + " granted=" + bareGranted + " forgotten=" + forgotten + " gone=" + gone + " absent=" + absent, bareLearned && bareGranted && forgotten && gone && absent);
        } finally {
            for (LivingEntity probe : probes) probe.discard();
        }
    }

    // 14. mxt:mutual is both directions where mxt:undirected is either: a friendship held one way only passes
    // mxt:friend and mxt:undirected in that direction and is refused by the both-ways form.
    private static void mutual(ProbeContext context) {
        Holder<Technique> breathing = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.TECHNIQUE);
        ItemStack carrier = techniqueBook(breathing);
        LivingEntity seatB = spawnProbe(context, context.grid(3).offset(3, 0, 0));
        LivingEntity bareB = spawnProbe(context, context.grid(5).offset(3, 0, 0));
        LivingEntity crowd1 = spawnProbe(context, context.grid(6).offset(3, 0, 0));
        List<LivingEntity> probes = new ArrayList<>();
        for (LivingEntity probe : Arrays.asList(seatB, bareB, crowd1)) if (probe != null) probes.add(probe);
        if (!created(context, probes, 3)) return;
        for (LivingEntity probe : Arrays.asList(seatB, crowd1))
            probe.setItemInHand(InteractionHand.MAIN_HAND, carrier.copy());
        Set<UUID> friends = friends(probes);
        // Ordered "judge>candidate" pairs that are not friends, so a friendship going one way only is expressible.
        Set<String> refused = new HashSet<>();
        Consumer<FriendEvent.Relation> relation = relations(friends, refused);
        NeoForge.EVENT_BUS.addListener(relation);
        try {
            refused.add(seatB.getUUID() + ">" + bareB.getUUID());
            BiEntityCondition trust = biEntityCondition(context.level(), "{\"type\": \"mxt:friend\"}");
            BiEntityCondition eitherWay = biEntityCondition(context.level(), "{\"type\": \"mxt:undirected\", \"condition\": {\"type\": \"mxt:friend\"}}");
            BiEntityCondition bothWays = biEntityCondition(context.level(), "{\"type\": \"mxt:mutual\", \"condition\": {\"type\": \"mxt:friend\"}}");
            boolean oneWay = trust.test(bareB, seatB, contextOf(bareB)) && !trust.test(seatB, bareB, contextOf(seatB));
            boolean either = eitherWay.test(bareB, seatB, contextOf(bareB)) && eitherWay.test(seatB, bareB, contextOf(seatB));
            boolean notMutual = !bothWays.test(bareB, seatB, contextOf(bareB)) && !bothWays.test(seatB, bareB, contextOf(seatB));
            boolean mutualPair = bothWays.test(bareB, crowd1, contextOf(bareB));
            report(context, "cultivation probe: mutual one_way=" + oneWay + " either=" + either + " refusing=" + notMutual + " pair=" + mutualPair, oneWay && either && notMutual && mutualPair);
        } finally {
            NeoForge.EVENT_BUS.unregister(relation);
            for (LivingEntity probe : probes) probe.discard();
        }
    }

    // The source's own layout guard: a body the entity type refused to create would make every leg below vacuous.
    private static boolean created(ProbeContext context, List<LivingEntity> probes, int expected) {
        if (probes.size() == expected) return true;
        for (LivingEntity probe : probes) probe.discard();
        context.fail("cultivation probe: could not create the probe beings");
        return false;
    }

    // The source's per-leg line: it goes to the run log with its own verdict, and the check carries it to the ledger.
    private static void report(ProbeContext context, String line, boolean value) {
        context.note(line + (value ? " OK" : " MISMATCH"));
        context.check(line, value);
    }

    // The manual the partner leg reads: the technique book carrying the technique component, because a
    // declaration's claimed item is a manual by matching and would carry no component at all.
    private static ItemStack techniqueBook(Holder<Technique> technique) {
        ItemStack book = MxtItems.TECHNIQUE_BOOK.toStack();
        book.set(MxtDataComponents.TECHNIQUE, technique);
        return book;
    }

    private static Set<UUID> friends(List<LivingEntity> probes) {
        Set<UUID> friends = new HashSet<>();
        for (LivingEntity probe : probes) friends.add(probe.getUUID());
        return friends;
    }

    // Friendship is answered by a listener, because only a player can keep a friend list. FALSE overrides the
    // friend pool, which answers by pair and therefore always answers symmetrically.
    private static Consumer<FriendEvent.Relation> relations(Set<UUID> friends, Set<String> refused) {
        return event -> {
            if (refused.contains(event.getJudgeId() + ">" + event.getCandidate().getUUID()))
                event.setResult(TriState.FALSE);
            else if (friends.contains(event.getJudgeId()) && friends.contains(event.getCandidate().getUUID()))
                event.setResult(TriState.TRUE);
        };
    }

    // One settlement of a running method, driven the way the runtime drives it: the place supplies the aura, the
    // caller supplies the upkeep answer, and the time is whatever the caller says it is.
    private static CultivationResult tickCultivation(LivingEntity entity, CultivationAttachment spirit, Holder<Cultivation> action, long gameTime) {
        Cultivation definition = action.value();
        FormulaContext context = contextOf(entity);
        AuraResult aura = AuraService.getPositionAura(entity.level(), entity.blockPosition());
        return CultivationMethodService.tick(entity, spirit, entity.getData(MxtAttachments.RESOURCE_HOLDER), aura, action, definition, gameTime, context, () -> definition.tickCondition().test(entity, context));
    }

    private static FormulaContext contextOf(LivingEntity entity) {
        return FormulaContexts.forEntity(entity);
    }

    private static boolean asks(Holder<Cultivation> action, LivingEntity entity) {
        return CultivationModeService.applicable(entity, action, contextOf(entity));
    }

    private static boolean picks(Optional<Holder<Cultivation>> chosen, Identifier expected) {
        return chosen.map(action -> HolderHelper.id(action).equals(expected)).orElse(false);
    }

    private static boolean runs(CultivationAttachment spirit, Identifier expected) {
        return spirit.cultivating() && spirit.cultivation().map(action -> HolderHelper.id(action).equals(expected)).orElse(false);
    }

    // A pack-named abort carries its own text; every other failure carries none and is told by its enum.
    private static boolean named(CultivationResult result, String expected) {
        return result.abortReason() != null && result.abortReason().getString().equals(expected);
    }

    // Definitions name other definitions through registry holders, so the parse needs the level's registries: plain
    // JsonOps cannot resolve an `mxt:cultivate` action that names its method.
    private static EntityAction entityAction(ServerLevel level, String json) {
        return EntityAction.SINGLE_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), JsonParser.parseString(json)).getOrThrow();
    }

    private static BiEntityCondition biEntityCondition(ServerLevel level, String json) {
        return BiEntityCondition.SINGLE_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), JsonParser.parseString(json)).getOrThrow();
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }

    // One disposable probe entity carrying no spirit root; a pig has no armour and no innate resistance, which keeps
    // what the pipeline hands over equal to what it loses. A null return means the type refused to create one.
    private static LivingEntity spawnProbe(ProbeContext context, BlockPos pos) {
        Pig pig = EntityType.PIG.create(context.level(), EntitySpawnReason.COMMAND);
        if (pig == null) return null;
        pig.setNoAi(true);
        pig.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        context.level().addFreshEntity(pig);
        return pig;
    }
}
