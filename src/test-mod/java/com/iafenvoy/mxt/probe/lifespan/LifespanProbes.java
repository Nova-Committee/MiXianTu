package com.iafenvoy.mxt.probe.lifespan;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iafenvoy.jupiter.config.entry.BooleanEntry;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.*;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.config.MxtServerConfig.LifespanOutcome;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.builtin.entity.ModifyLifespanAction;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.event.LifeSpanEndEvent;
import com.iafenvoy.mxt.event.LifeSpanRebirthEvent;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.probe.ProbeKit;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityGrantService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.LifeSpanService;
import com.iafenvoy.mxt.runtime.damage.DamageCalculationService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import com.iafenvoy.mxt.runtime.resulting.Failures;
import com.iafenvoy.mxt.runtime.resulting.Result;

/**
 * The whole lifespan ledger, driven on disposable probes: the master switch, both numbers, seeding, the settlement,
 * each of the three outcomes and the explicit rebirth the command and the script call. Every part is its own probe,
 * so the state an earlier part left in the server config is established again by the part that needs it.
 *
 * <p>Fixture numbers, which is why changing test-pack content changes a part's arithmetic: mxt_test:qi_refining
 * grants 2400 ticks and every part sets the base to 0 unless it says otherwise. The player-only half of the feature
 * is not covered here.
 */
public final class LifespanProbes {
    private LifespanProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("lifespan/switch_off", LifespanProbes::switchOff),
                Probe.of("lifespan/switch_on", LifespanProbes::switchOn),
                Probe.of("lifespan/ledger", LifespanProbes::ledger),
                Probe.of("lifespan/seed", LifespanProbes::seed),
                Probe.of("lifespan/settle", LifespanProbes::settle),
                Probe.of("lifespan/pre_cancel", LifespanProbes::preCancel),
                Probe.of("lifespan/death", LifespanProbes::death),
                Probe.of("lifespan/reincarnate", LifespanProbes::reincarnate),
                Probe.kitted("lifespan/breakthrough", LifespanProbes::breakthrough),
                Probe.of("lifespan/action", LifespanProbes::action),
                Probe.of("lifespan/no_ledger", LifespanProbes::noLedger),
                Probe.of("lifespan/explicit_reincarnate", LifespanProbes::explicitReincarnate),
                Probe.of("lifespan/reincarnate_action", LifespanProbes::reincarnateAction));
    }

    // 1. Master switch off: nothing settles, no ledger appears, and a write still lands on the books.
    private static void switchOff(ProbeContext context) {
        Snapshot was = snapshot();
        List<LivingEntity> probes = new ArrayList<>();
        boolean ok = true;
        try {
            MxtServerConfig.Lifespan settings = MxtServerConfig.INSTANCE.lifespan;
            LivingEntity dormant = probe(context, probes, 1, null);
            if (dormant == null) return;

            settings.enabled.setValue(false);
            settings.baseLifespan.setValue(24_000L);
            boolean dormantSettled = LifeSpanService.settle(dormant);
            boolean dormantLedger = dormant.getExistingData(MxtAttachments.LIFESPAN).isPresent();
            boolean dormantWrite = LifeSpanService.set(dormant, 100L).failure() == null;
            ok &= context.check("lifespan probe: switch_off settled=" + dormantSettled + " ledger=" + dormantLedger
                            + " written=" + LifeSpanService.remaining(dormant),
                    !dormantSettled && !dormantLedger && dormantWrite && LifeSpanService.remaining(dormant) == 100L);
            verdict(context, ok);
        } finally {
            restore(was);
            discard(probes);
        }
    }

    // 2. Switching it back on resumes that ledger where the write left it: switching off never back-pays. The write
    // part 1 asserts is repeated, because a part runs on its own and shares no state with the one before it.
    private static void switchOn(ProbeContext context) {
        Snapshot was = snapshot();
        List<LivingEntity> probes = new ArrayList<>();
        boolean ok = true;
        try {
            MxtServerConfig.Lifespan settings = MxtServerConfig.INSTANCE.lifespan;
            LivingEntity dormant = probe(context, probes, 1, null);
            if (dormant == null) return;

            settings.enabled.setValue(false);
            settings.baseLifespan.setValue(24_000L);
            LifeSpanService.set(dormant, 100L);
            settings.enabled.setValue(true);
            settings.agePerSettle.setValue(20);
            boolean resumed = LifeSpanService.settle(dormant) && LifeSpanService.remaining(dormant) == 80L;
            ok &= context.check("lifespan probe: switch_on remaining=" + LifeSpanService.remaining(dormant)
                    + "/" + LifeSpanService.total(dormant), resumed);
            verdict(context, ok);
        } finally {
            restore(was);
            discard(probes);
        }
    }

    // 3. The ledger: a set rewrites both numbers, a positive add grows both, a negative one only spends
    //    what is left, and a negative set is refused.
    private static void ledger(ProbeContext context) {
        Snapshot was = snapshot();
        List<LivingEntity> probes = new ArrayList<>();
        boolean ok = true;
        try {
            MxtServerConfig.Lifespan settings = MxtServerConfig.INSTANCE.lifespan;
            LivingEntity ledger = probe(context, probes, 1, null);
            if (ledger == null) return;

            settings.enabled.setValue(true);
            settings.agePerSettle.setValue(20);
            LifeSpanService.set(ledger, 100L);
            boolean setLedger = LifeSpanService.remaining(ledger) == 100L && LifeSpanService.total(ledger) == 100L;
            LifeSpanService.add(ledger, 50L);
            boolean added = LifeSpanService.remaining(ledger) == 150L && LifeSpanService.total(ledger) == 150L;
            LifeSpanService.add(ledger, -30L);
            boolean spent = LifeSpanService.remaining(ledger) == 120L && LifeSpanService.total(ledger) == 150L;
            boolean refusedSet = LifeSpanService.set(ledger, -1L).failure() != null
                    && LifeSpanService.remaining(ledger) == 120L && LifeSpanService.total(ledger) == 150L;
            // Ageing of zero settles without spending anything.
            settings.agePerSettle.setValue(0);
            boolean ageless = LifeSpanService.settle(ledger) && LifeSpanService.remaining(ledger) == 120L;
            settings.agePerSettle.setValue(20);
            ok &= context.check("lifespan probe: ledger set=" + setLedger + " added=" + added + " spent=" + spent
                            + " refused=" + refusedSet + " ageless=" + ageless,
                    setLedger && added && spent && refusedSet && ageless);
            verdict(context, ok);
        } finally {
            restore(was);
            discard(probes);
        }
    }

    // 4. Seeding: a body with no ledger only takes the configured base, and a base of zero seeds nothing.
    //    The zero-base half runs on its own probe, because it is the only thing that has to leave a body
    //    with a ledger behind.
    private static void seed(ProbeContext context) {
        Snapshot was = snapshot();
        List<LivingEntity> probes = new ArrayList<>();
        boolean ok = true;
        try {
            MxtServerConfig.Lifespan settings = MxtServerConfig.INSTANCE.lifespan;
            LivingEntity seeded = probe(context, probes, 1, null);
            LivingEntity plain = probe(context, probes, 2, null);
            if (seeded == null || plain == null) return;

            settings.enabled.setValue(true);
            settings.agePerSettle.setValue(20);
            settings.baseLifespan.setValue(24_000L);
            LifeSpanService.add(seeded, 40L);
            boolean fromBase = LifeSpanService.remaining(seeded) == 24_040L && LifeSpanService.total(seeded) == 24_040L;
            settings.baseLifespan.setValue(0L);
            LifeSpanService.add(plain, 40L);
            boolean fromZero = LifeSpanService.remaining(plain) == 40L && LifeSpanService.total(plain) == 40L;
            ok &= context.check("lifespan probe: seed base=" + LifeSpanService.remaining(seeded)
                    + " zero=" + LifeSpanService.remaining(plain), fromBase && fromZero);
            verdict(context, ok);
        } finally {
            restore(was);
            discard(probes);
        }
    }

    // 5. Settlement and the NONE outcome: 25 spends down to 5 and then closes the account at 0.
    private static void settle(ProbeContext context) {
        Snapshot was = snapshot();
        List<LivingEntity> probes = new ArrayList<>();
        boolean ok = true;
        try {
            MxtServerConfig.Lifespan settings = MxtServerConfig.INSTANCE.lifespan;
            LivingEntity settled = probe(context, probes, 1, null);
            if (settled == null) return;

            settings.enabled.setValue(true);
            settings.agePerSettle.setValue(20);
            settings.onExpire.setValue(LifespanOutcome.NONE);
            LifeSpanService.set(settled, 25L);
            List<LifespanOutcome> outcomes = new ArrayList<>();
            Consumer<LifeSpanEndEvent.Post> recorder = event -> outcomes.add(event.getOutcome());
            NeoForge.EVENT_BUS.addListener(recorder);
            boolean decremented;
            boolean expired;
            try {
                decremented = LifeSpanService.settle(settled) && LifeSpanService.remaining(settled) == 5L
                        && LifeSpanService.total(settled) == 25L;
                expired = LifeSpanService.settle(settled);
            } finally {
                NeoForge.EVENT_BUS.unregister(recorder);
            }
            boolean closed = LifeSpanService.remaining(settled) == -1L && LifeSpanService.total(settled) == 0L;
            ok &= context.check("lifespan probe: settle decremented=" + decremented + " closed=" + closed
                            + " outcomes=" + outcomes,
                    decremented && expired && closed && outcomes.equals(List.of(LifespanOutcome.NONE)));
            verdict(context, ok);
        } finally {
            restore(was);
            discard(probes);
        }
    }

    // 6. Cancelling the end inside the event: writing nothing closes the account, buying time keeps the
    //    countdown running - which is the whole point of the cancellable event.
    private static void preCancel(ProbeContext context) {
        Snapshot was = snapshot();
        List<LivingEntity> probes = new ArrayList<>();
        boolean ok = true;
        try {
            MxtServerConfig.Lifespan settings = MxtServerConfig.INSTANCE.lifespan;
            LivingEntity cancelled = probe(context, probes, 1, null);
            if (cancelled == null) return;

            settings.enabled.setValue(true);
            settings.agePerSettle.setValue(20);
            Consumer<LifeSpanEndEvent.Pre> refuse = event -> event.setCanceled(true);
            Consumer<LifeSpanEndEvent.Pre> extend = event -> {
                event.setCanceled(true);
                if (event.getEntity() instanceof LivingEntity living) LifeSpanService.add(living, 50L);
            };
            LifeSpanService.set(cancelled, 5L);
            NeoForge.EVENT_BUS.addListener(refuse);
            try {
                LifeSpanService.settle(cancelled);
            } finally {
                NeoForge.EVENT_BUS.unregister(refuse);
            }
            boolean refusedEnd = LifeSpanService.remaining(cancelled) == -1L && LifeSpanService.total(cancelled) == 0L;
            LifeSpanService.set(cancelled, 5L);
            NeoForge.EVENT_BUS.addListener(extend);
            try {
                LifeSpanService.settle(cancelled);
            } finally {
                NeoForge.EVENT_BUS.unregister(extend);
            }
            boolean extended = LifeSpanService.remaining(cancelled) == 50L;
            ok &= context.check("lifespan probe: pre_cancel closed=" + refusedEnd
                    + " extended=" + LifeSpanService.remaining(cancelled), refusedEnd && extended);
            verdict(context, ok);
        } finally {
            restore(was);
            discard(probes);
        }
    }

    // 7. DEATH on a non-player: it really dies, and it dies of the mxt:lifespan cause that the no_bonus
    //    tag keeps out of the element pipeline.
    private static void death(ProbeContext context) {
        Snapshot was = snapshot();
        List<LivingEntity> probes = new ArrayList<>();
        boolean ok = true;
        try {
            MxtServerConfig.Lifespan settings = MxtServerConfig.INSTANCE.lifespan;
            LivingEntity doomed = probe(context, probes, 1, null);
            if (doomed == null) return;

            settings.enabled.setValue(true);
            settings.agePerSettle.setValue(20);
            settings.onExpire.setValue(LifespanOutcome.DEATH);
            LifeSpanService.set(doomed, 5L);
            LifeSpanService.settle(doomed);
            DamageSource cause = doomed.getLastDamageSource();
            boolean died = !doomed.isAlive() && cause != null && DamageCalculationService.bypasses(cause)
                    && HolderHelper.id(cause.typeHolder()).equals(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "lifespan"));
            ok &= context.check("lifespan probe: death alive=" + doomed.isAlive() + " cause="
                            + (cause == null ? "none" : HolderHelper.id(cause.typeHolder())),
                    died);
            verdict(context, ok);
        } finally {
            restore(was);
            discard(probes);
        }
    }

    // 8. REINCARNATE: the realm, its progress and its minor stage records are gone and the abilities they
    //    granted are taken back, while roots and techniques survive and the ledger restarts at the base.
    private static void reincarnate(ProbeContext context) {
        Snapshot was = snapshot();
        List<LivingEntity> probes = new ArrayList<>();
        boolean ok = true;
        try {
            MxtServerConfig.Lifespan settings = MxtServerConfig.INSTANCE.lifespan;
            LivingEntity reborn = probe(context, probes, 1, ProbeDefinitions.PROBE_FIRE_ROOT);
            if (reborn == null) return;

            settings.enabled.setValue(true);
            settings.agePerSettle.setValue(20);
            settings.onExpire.setValue(LifespanOutcome.REINCARNATE);
            settings.baseLifespan.setValue(24_000L);
            reincarnationPolicy();
            Holder<RealmStage> qiRefining = context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.QI_REFINING);
            CultivationAttachment rebornSpirit = reborn.getData(MxtAttachments.CULTIVATION);
            MinorStageAttachment rebornStages = reborn.getData(MxtAttachments.MINOR_STAGES);
            TechniqueAttachment rebornTechniques = reborn.getData(MxtAttachments.TECHNIQUE);
            AbilityAttachment rebornAbilities = reborn.getData(MxtAttachments.ABILITY_HOLDER);
            rebornSpirit.setRealmStage(qiRefining);
            rebornSpirit.setCultivationProgress(ProbeKit.requireProfile(context, ProbeDefinitions.QI), 80.0D);
            rebornStages.raiseMinorStageRecord(qiRefining, 2);
            rebornTechniques.addLearnedTechnique(context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.TECHNIQUE));
            AbilityGrantService.recalculate(reborn);
            boolean realmAbility = rebornAbilities.has(context.require(MxtResourceKeys.ABILITY, context.id("awaken_divine_sense")));
            LifeSpanService.set(reborn, 5L);
            boolean rebornSettled = LifeSpanService.settle(reborn);
            boolean reset = rebornSpirit.realmStages().isEmpty() && rebornStages.minorStageRecord(qiRefining) == -1
                    && !rebornAbilities.has(context.require(MxtResourceKeys.ABILITY, context.id("awaken_divine_sense")));
            // What the body still is keeps its grants, so the root's own ability survives the realm's.
            boolean kept = rebornTechniques.learnedTechniques().contains(context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.TECHNIQUE))
                    && !reborn.getData(MxtAttachments.SPIRIT_IDENTITY).spiritRoots().isEmpty()
                    && rebornAbilities.has(context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.PROBE_AFFINITY_ABILITY));
            boolean rebornLedger = LifeSpanService.remaining(reborn) == 24_000L && LifeSpanService.total(reborn) == 24_000L;
            ok &= context.check("lifespan probe: reincarnate realm_ability=" + realmAbility
                            + " realms=" + rebornSpirit.realmStages().size()
                            + " minor_stage=" + rebornStages.minorStageRecord(qiRefining)
                            + " kept=" + kept + " ledger=" + LifeSpanService.remaining(reborn),
                    realmAbility && rebornSettled && reset && kept && rebornLedger);
            verdict(context, ok);
        } finally {
            restore(was);
            discard(probes);
        }
    }

    // 9. The realm entry: the fixture stage pays its own lifespan once, on the breakthrough that reaches it.
    //    The cost is paid in spirit_power, whose cap in this pack only opens for a body standing in the
    //    spirit_power chain (a plain mortal can hold none of it, so the resource would clamp to 0 and the
    //    attempt would fail as INSUFFICIENT_RESOURCE). Seating the climber there first is what makes the
    //    payment possible; the qi chain is a different chain, so this stays the mortal -> first realm step.
    private static void breakthrough(ProbeContext context) {
        Snapshot was = snapshot();
        List<LivingEntity> probes = new ArrayList<>();
        boolean ok = true;
        try {
            MxtServerConfig.Lifespan settings = MxtServerConfig.INSTANCE.lifespan;
            LivingEntity climber = probe(context, probes, 1, null);
            if (climber == null) return;

            settings.enabled.setValue(true);
            settings.agePerSettle.setValue(20);
            settings.onExpire.setValue(LifespanOutcome.NONE);
            settings.baseLifespan.setValue(0L);
            CultivationAttachment climbSpirit = climber.getData(MxtAttachments.CULTIVATION);
            ResourceHolderAttachment climbResources = climber.getData(MxtAttachments.RESOURCE_HOLDER);
            climbSpirit.setRealmStage(context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.SPIRIT_POWER_REFINING));
            Holder<Resource> climbPayment = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.SPIRIT_POWER);
            ProbeKit.ensureResource(climber, climbResources, climbPayment, 100.0D);
            climbSpirit.setCultivationProgress(ProbeKit.requireProfile(context, ProbeDefinitions.QI), 100.0D);
            CultivationService.BreakthroughResult breakthrough = CultivationService.attempt(climber, climbSpirit,
                    climbResources, ProbeKit.requireProfile(context, ProbeDefinitions.QI), FormulaContext.of(climber), () -> true);
            boolean declared = context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.QI_REFINING).value().lifespan().isPresent();
            // The pack's cap is part of the arithmetic: 25 is the fixture's own cost, and a body that cannot hold
            // that much turns the whole part into an INSUFFICIENT_RESOURCE answer that says nothing about lifespan.
            boolean affordable = climbResources.get(climbPayment) >= 25.0D;
            boolean granted = breakthrough.isSuccess() && LifeSpanService.remaining(climber) == 2_400L
                    && LifeSpanService.total(climber) == 2_400L;
            ok &= context.check("lifespan probe: breakthrough advanced=" + breakthrough.isSuccess()
                            + " failure=" + breakthrough.failure() + " declared=" + declared
                            + " affordable=" + affordable + " granted=" + LifeSpanService.remaining(climber),
                    affordable && granted);
            verdict(context, ok);
        } finally {
            restore(was);
            discard(probes);
        }
    }

    // 10. The action entry, both modes, plus the load-time refusal of a negative set.
    private static void action(ProbeContext context) {
        Snapshot was = snapshot();
        List<LivingEntity> probes = new ArrayList<>();
        boolean ok = true;
        try {
            MxtServerConfig.Lifespan settings = MxtServerConfig.INSTANCE.lifespan;
            LivingEntity acted = probe(context, probes, 1, null);
            if (acted == null) return;

            settings.baseLifespan.setValue(0L);
            new ModifyLifespanAction(ModifyLifespanAction.Mode.ADD, new Constant(30.0D))
                    .execute(acted, FormulaContext.of(acted));
            boolean actionAdded = LifeSpanService.remaining(acted) == 30L && LifeSpanService.total(acted) == 30L;
            new ModifyLifespanAction(ModifyLifespanAction.Mode.ADD, new Constant(-10.0D))
                    .execute(acted, FormulaContext.of(acted));
            new ModifyLifespanAction(ModifyLifespanAction.Mode.SET, new Constant(-5.0D))
                    .execute(acted, FormulaContext.of(acted));
            boolean actionTook = LifeSpanService.remaining(acted) == 20L && LifeSpanService.total(acted) == 30L;
            JsonObject negativeSet = new JsonObject();
            negativeSet.addProperty("mode", "set");
            negativeSet.addProperty("amount", -5);
            JsonObject negativeAdd = new JsonObject();
            negativeAdd.addProperty("mode", "add");
            negativeAdd.addProperty("amount", -5);
            boolean loadRefusal = ModifyLifespanAction.CODEC.codec().parse(JsonOps.INSTANCE, negativeSet).error().isPresent()
                    && ModifyLifespanAction.CODEC.codec().parse(JsonOps.INSTANCE, negativeAdd).result().isPresent();
            ok &= context.check("lifespan probe: action added=" + actionAdded + " took=" + actionTook
                            + " load_refusal=" + loadRefusal + " remaining=" + LifeSpanService.remaining(acted),
                    actionAdded && actionTook && loadRefusal);
            verdict(context, ok);
        } finally {
            restore(was);
            discard(probes);
        }
    }

    // 11. Regression: neither adding nothing nor settling may give a body that never had a ledger one.
    //     A stage whose formula rounds to zero goes through add(..., 0) on every breakthrough, so a zero
    //     that opened a 0/0 account would sentence the body at the next settlement.
    private static void noLedger(ProbeContext context) {
        Snapshot was = snapshot();
        List<LivingEntity> probes = new ArrayList<>();
        boolean ok = true;
        try {
            MxtServerConfig.Lifespan settings = MxtServerConfig.INSTANCE.lifespan;
            LivingEntity untouched = probe(context, probes, 1, null);
            if (untouched == null) return;

            settings.enabled.setValue(true);
            settings.baseLifespan.setValue(0L);
            boolean zeroAdd = LifeSpanService.add(untouched, 0L).failure() == null
                    && untouched.getExistingData(MxtAttachments.LIFESPAN).isEmpty();
            boolean clean = !LifeSpanService.settle(untouched)
                    && untouched.getExistingData(MxtAttachments.LIFESPAN).isEmpty()
                    && LifeSpanService.remaining(untouched) == -1L;
            ok &= context.check("lifespan probe: no_ledger zero_add=" + zeroAdd + " settle_created="
                    + untouched.getExistingData(MxtAttachments.LIFESPAN).isPresent(), zeroAdd && clean);
            verdict(context, ok);
        } finally {
            restore(was);
            discard(probes);
        }
    }

    // 12. The explicit entry point the command, the script and the action call, on part 8's policy: it runs
    //     the same reset without an expiry - so no end event fires - and it works while the system is
    //     switched off, because a command is not time passing. Its own Pre may refuse it outright.
    private static void explicitReincarnate(ProbeContext context) {
        Snapshot was = snapshot();
        List<LivingEntity> probes = new ArrayList<>();
        boolean ok = true;
        try {
            MxtServerConfig.Lifespan settings = MxtServerConfig.INSTANCE.lifespan;
            LivingEntity restart = probe(context, probes, 1, ProbeDefinitions.PROBE_FIRE_ROOT);
            if (restart == null) return;

            reincarnationPolicy();
            settings.enabled.setValue(false);
            settings.baseLifespan.setValue(24_000L);
            Holder<RealmStage> qiRefining = context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.QI_REFINING);
            CultivationAttachment restartSpirit = restart.getData(MxtAttachments.CULTIVATION);
            MinorStageAttachment restartIdentity = restart.getData(MxtAttachments.MINOR_STAGES);
            AbilityAttachment restartAbilities = restart.getData(MxtAttachments.ABILITY_HOLDER);
            restartSpirit.setRealmStage(qiRefining);
            restartIdentity.raiseMinorStageRecord(qiRefining, 2);
            AbilityGrantService.recalculate(restart);
            boolean restartAbility = restartAbilities.has(context.require(MxtResourceKeys.ABILITY, context.id("awaken_divine_sense")));
            LifeSpanService.set(restart, 5L);
            boolean[] ended = {false};
            int[] rebirths = {0};
            Consumer<LifeSpanEndEvent.Post> watch = event -> ended[0] = true;
            Consumer<LifeSpanRebirthEvent.Post> counted = event -> rebirths[0]++;
            Consumer<LifeSpanRebirthEvent.Pre> veto = event -> event.setCanceled(true);
            NeoForge.EVENT_BUS.addListener(watch);
            NeoForge.EVENT_BUS.addListener(counted);
            NeoForge.EVENT_BUS.addListener(veto);
            Result refused;
            try {
                refused = LifeSpanService.reincarnate(restart);
            } finally {
                NeoForge.EVENT_BUS.unregister(veto);
            }
            // A refused rebirth changes nothing at all: the realm is still there and the ledger still reads 5.
            boolean restartRefused = refused.failure() != null && refused.failure() == Failures.CANCELLED
                    && !restartSpirit.realmStages().isEmpty() && LifeSpanService.remaining(restart) == 5L;
            Result restarted;
            try {
                restarted = LifeSpanService.reincarnate(restart);
            } finally {
                NeoForge.EVENT_BUS.unregister(watch);
                NeoForge.EVENT_BUS.unregister(counted);
            }
            boolean restartReset = restartSpirit.realmStages().isEmpty()
                    && restartIdentity.minorStageRecord(qiRefining) == -1
                    && !restartAbilities.has(context.require(MxtResourceKeys.ABILITY, context.id("awaken_divine_sense")))
                    && restartAbilities.has(context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.PROBE_AFFINITY_ABILITY));
            boolean restartLedger = LifeSpanService.remaining(restart) == 24_000L && LifeSpanService.total(restart) == 24_000L;
            ok &= context.check("lifespan probe: explicit_reincarnate ran=" + (restarted.failure() == null)
                            + " refused=" + restartRefused + " ability=" + restartAbility
                            + " realms=" + restartSpirit.realmStages().size()
                            + " kept_root=" + restartAbilities.has(context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.PROBE_AFFINITY_ABILITY))
                            + " ledger=" + LifeSpanService.remaining(restart)
                            + " rebirth_post=" + rebirths[0] + " end_event=" + ended[0],
                    restartAbility && restarted.failure() == null && restartRefused && restartReset && restartLedger
                            && rebirths[0] == 1 && !ended[0]);
            verdict(context, ok);
        } finally {
            restore(was);
            discard(probes);
        }
    }

    // 13. The data pack entry: mxt:reincarnate decodes by its own type name and runs that same reset, which
    //     is what a pack's own reincarnation pill needs instead of waiting for a life to run out.
    private static void reincarnateAction(ProbeContext context) {
        Snapshot was = snapshot();
        List<LivingEntity> probes = new ArrayList<>();
        boolean ok = true;
        try {
            MxtServerConfig.Lifespan settings = MxtServerConfig.INSTANCE.lifespan;
            LivingEntity restart = probe(context, probes, 1, ProbeDefinitions.PROBE_FIRE_ROOT);
            if (restart == null) return;

            reincarnationPolicy();
            settings.enabled.setValue(false);
            settings.baseLifespan.setValue(24_000L);
            Holder<RealmStage> qiRefining = context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.QI_REFINING);
            CultivationAttachment restartSpirit = restart.getData(MxtAttachments.CULTIVATION);
            restartSpirit.setRealmStage(qiRefining);
            AbilityGrantService.recalculate(restart);
            EntityAction action = EntityAction.SINGLE_CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString("{\"type\": \"mxt:reincarnate\"}")).getOrThrow();
            action.execute(restart, FormulaContext.of(restart));
            boolean actionRebirth = restartSpirit.realmStages().isEmpty()
                    && LifeSpanService.remaining(restart) == 24_000L;
            ok &= context.check("lifespan probe: reincarnate_action realms=" + restartSpirit.realmStages().size()
                    + " ledger=" + LifeSpanService.remaining(restart), actionRebirth);
            verdict(context, ok);
        } finally {
            restore(was);
            discard(probes);
        }
    }

    // The policy part 8 writes, repeated by the parts that rely on it: every switch is back at its fixture value.
    private static void reincarnationPolicy() {
        MxtServerConfig.Reincarnation reincarnation = MxtServerConfig.INSTANCE.reincarnation;
        reincarnation.kills.setValue(false);
        reincarnation.resetCultivation.setValue(true);
        reincarnation.clearMinorStages.setValue(true);
        reincarnation.cancelTribulation.setValue(true);
        reincarnation.clearResources.setValue(false);
        reincarnation.keepSpiritRoots.setValue(true);
        reincarnation.keepTechniques.setValue(true);
        reincarnation.keepSoul.setValue(true);
    }

    // One disposable probe entity carrying exactly one spirit root, so what survives a rebirth is never a question.
    private static LivingEntity spawnProbe(ProbeContext context, BlockPos pos, Identifier root) {
        Pig pig = EntityType.PIG.create(context.level(), EntitySpawnReason.COMMAND);
        if (pig == null) return null;
        pig.setNoAi(true);
        pig.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        context.level().addFreshEntity(pig);
        if (root != null)
            CultivationIdentityService.grantSpiritRoot(pig, context.require(MxtResourceKeys.SPIRIT_ROOT, root));
        return pig;
    }

    // A probe being at an arena relative spot above the player's own, or null with the failure already recorded.
    private static LivingEntity probe(ProbeContext context, List<LivingEntity> probes, int above, Identifier root) {
        LivingEntity spawned = spawnProbe(context, context.playerSpot().above(above), root);
        if (spawned == null) {
            context.fail("lifespan probe: could not create the probe beings");
            return null;
        }
        probes.add(spawned);
        return spawned;
    }

    private static void verdict(ProbeContext context, boolean ok) {
        if (ok) context.note("lifespan probe: OK");
        else context.fail("lifespan probe: MISMATCH");
    }

    private static void discard(List<LivingEntity> probes) {
        for (LivingEntity probe : probes) probe.discard();
    }

    // The server config the part writes; every part puts it back, because the config is shared and outlives a probe.
    private record Snapshot(boolean enabled, long base, int age, LifespanOutcome outcome, double fraction,
                            boolean[] policy) {
    }

    private static Snapshot snapshot() {
        MxtServerConfig.Lifespan settings = MxtServerConfig.INSTANCE.lifespan;
        return new Snapshot(settings.enabled.getValue(), settings.baseLifespan.getValue(), settings.agePerSettle.getValue(),
                settings.onExpire.getValue(), settings.warningFraction.getValue(), policy());
    }

    private static void restore(Snapshot snapshot) {
        MxtServerConfig.Lifespan settings = MxtServerConfig.INSTANCE.lifespan;
        settings.enabled.setValue(snapshot.enabled());
        settings.baseLifespan.setValue(snapshot.base());
        settings.agePerSettle.setValue(snapshot.age());
        settings.onExpire.setValue(snapshot.outcome());
        settings.warningFraction.setValue(snapshot.fraction());
        BooleanEntry[] entries = policyEntries();
        for (int index = 0; index < entries.length; index++) entries[index].setValue(snapshot.policy()[index]);
    }

    private static boolean[] policy() {
        BooleanEntry[] entries = policyEntries();
        boolean[] values = new boolean[entries.length];
        for (int index = 0; index < entries.length; index++) values[index] = entries[index].getValue();
        return values;
    }

    private static BooleanEntry[] policyEntries() {
        MxtServerConfig.Reincarnation reincarnation = MxtServerConfig.INSTANCE.reincarnation;
        return new BooleanEntry[]{reincarnation.kills, reincarnation.resetCultivation, reincarnation.clearMinorStages,
                reincarnation.cancelTribulation, reincarnation.clearResources, reincarnation.keepSpiritRoots,
                reincarnation.keepTechniques, reincarnation.keepSoul};
    }
}
