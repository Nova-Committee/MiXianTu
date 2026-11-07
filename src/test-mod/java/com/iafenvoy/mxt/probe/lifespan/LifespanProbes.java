package com.iafenvoy.mxt.probe.lifespan;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.iafenvoy.saturn.registry.SaturnAttachments;
import com.iafenvoy.saturn.attachment.AbilityAttachment;
import com.iafenvoy.saturn.attachment.ResourceHolderAttachment;

import com.iafenvoy.reincarnation.registry.ReincarnationAttachments;
import com.iafenvoy.reincarnation.config.ReincarnationServerConfig;
import com.iafenvoy.reincarnation.api.LifespanApi;
import com.iafenvoy.reincarnation.api.RebirthMode;
import com.iafenvoy.reincarnation.api.ReincarnationApi;
import com.iafenvoy.reincarnation.data.information.getter.LifespanGetter;
import com.iafenvoy.reincarnation.lifespan.Lifespan;
import com.iafenvoy.reincarnation.storage.Generations;
import com.google.gson.JsonParser;
import com.iafenvoy.jupiter.config.entry.BooleanEntry;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.*;
import com.iafenvoy.reincarnation.config.ReincarnationServerConfig.LifespanOutcome;
import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.reincarnation.data.lifespan.AddLifespanAction;
import com.iafenvoy.reincarnation.data.lifespan.RemoveLifespanAction;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.reincarnation.event.LifeSpanEndEvent;
import com.iafenvoy.reincarnation.event.LifeSpanRebirthEvent;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.probe.ProbeKit;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.saturn.runtime.ability.AbilityGrantService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.reincarnation.lifespan.LifespanService;
import com.iafenvoy.mxt.runtime.damage.DamageCalculationService;
import com.iafenvoy.saturn.runtime.resulting.Failures;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.saturn.util.formula.number.Constant;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import com.iafenvoy.reincarnation.registry.tag.AttachmentTypeTags;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.pig.Pig;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;

/**
 * The whole lifespan ledger, driven on disposable probes: the master switch, both numbers, seeding, the settlement,
 * each of the three outcomes and the explicit rebirth the command and the script call. Every part is its own probe,
 * so the state an earlier part left in the server config is established again by the part that needs it.
 *
 * <p>The lifespan_breakthrough fixture grants a net 2400 ticks through success actions.
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
                Probe.of("lifespan/reincarnate_action", LifespanProbes::reincarnateAction),
                Probe.kitted("lifespan/full_player_rebirth", context -> fullPlayerRebirth(context, true)),
                Probe.kitted("lifespan/default_player_rebirth", context -> fullPlayerRebirth(context, false)));
    }

    private static void fullPlayerRebirth(ProbeContext context, boolean retain) {
        Snapshot was = snapshot();
        ServerPlayer old = context.player();
        try {
            var settings = ReincarnationServerConfig.INSTANCE.lifespan;
            settings.enabled.setValue(true);
            settings.baseLifespan.setValue(24_000L);
            reincarnationPolicy();
            if (!retain) {
                Map<TagKey<AttachmentType<?>>, List<Holder<AttachmentType<?>>>> tags = tags();
                tags.put(AttachmentTypeTags.KEEP_ON_REBIRTH, List.of());
                tags(tags);
                ReincarnationServerConfig.INSTANCE.reset.clearResources.setValue(true);
            }
            Holder<RealmStage> stage = context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.QI_REFINING);
            Holder<Technique> technique = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.TECHNIQUE);
            Holder<Resource> resource = context.require(SaturnResourceKeys.RESOURCE, ProbeDefinitions.QI);
            old.getData(MxtAttachments.CULTIVATION).setRealmStage(stage);
            old.getData(MxtAttachments.CULTIVATION).setCultivationProgress(ProbeKit.requireProfile(context, ProbeDefinitions.QI), 80);
            old.getData(MxtAttachments.MINOR_STAGES).raiseMinorStageRecord(stage, 2);
            CultivationIdentityService.grantSpiritRoot(old, context.require(MxtResourceKeys.SPIRIT_ROOT, ProbeDefinitions.PROBE_FIRE_ROOT));
            old.getData(MxtAttachments.TECHNIQUE).addLearnedTechnique(technique);
            old.getData(MxtAttachments.SOUL_STATS).setKarma(12);
            double amount = old.getData(SaturnAttachments.RESOURCE_HOLDER).get(resource);
            LifespanApi.set(old, 123);
            long generation = Generations.current(context.server(), old.getUUID());
            var result = LifespanApi.rebirth(old, RebirthMode.NEW_LIFE, Component.literal("MiXianTu probe"));
            context.check("full rebirth succeeds", result.getFailure().isEmpty());
            if (result.getFailure().isPresent()) return;
            ServerPlayer fresh = (ServerPlayer) result.getData().orElseThrow();
            context.check("player replaced and generation advanced", fresh != old && fresh.connection.player == fresh
                    && Generations.current(context.server(), fresh.getUUID()) == generation + 1);
            context.check("cultivation and minor stages cleared", fresh.getData(MxtAttachments.CULTIVATION).realmStages().isEmpty()
                    && fresh.getData(MxtAttachments.MINOR_STAGES).minorStageRecord(stage) == -1
                    && fresh.getData(MxtAttachments.TRIBULATION).tribulation().isEmpty());
            if (retain) {
                context.check("identity and technique preserved by tag", !fresh.getData(MxtAttachments.SPIRIT_IDENTITY).spiritRoots().isEmpty()
                        && fresh.getData(MxtAttachments.TECHNIQUE).learnedTechniques().contains(technique));
            } else {
                context.check("identity and techniques clear by default", fresh.getData(MxtAttachments.SPIRIT_IDENTITY).spiritRoots().isEmpty()
                        && fresh.getData(MxtAttachments.TECHNIQUE).learnedTechniques().isEmpty());
            }
            context.expect("soul follows tag", fresh.getData(MxtAttachments.SOUL_STATS).karma(), retain ? 12.0D : 0.0D);
            context.expect("resource follows shared config", fresh.getData(SaturnAttachments.RESOURCE_HOLDER).get(resource), retain ? amount : 0.0D);
            context.expect("new lifespan baseline", LifespanApi.get(fresh), new Lifespan(24_000, 24_000));
            context.expect("archived lifespan", ReincarnationApi.view(old.getUUID(), generation).orElseThrow()
                    .read(LifespanGetter.INSTANCE).value().orElseThrow(), new Lifespan(123, 123));
            context.check("root ability follows retained identity", fresh.getData(SaturnAttachments.ABILITY_HOLDER)
                    .has(context.require(SaturnResourceKeys.ABILITY, ProbeDefinitions.PROBE_AFFINITY_ABILITY)) == retain);
            context.note("lifespan probe: full player rebirth checked");
        } finally {
            restore(was);
            ServerPlayer current = old.connection.player;
            if (current != old) {
                context.server().getPlayerList().remove(current);
                current.discard();
            }
        }
    }

    // 1. Master switch off: nothing settles, no ledger appears, and a write still lands on the books.
    private static void switchOff(ProbeContext context) {
        Snapshot was = snapshot();
        List<LivingEntity> probes = new ArrayList<>();
        boolean ok;
        try {
            ReincarnationServerConfig.Lifespan settings = ReincarnationServerConfig.INSTANCE.lifespan;
            LivingEntity dormant = probe(context, probes, 1, null);
            if (dormant == null) return;

            settings.enabled.setValue(false);
            settings.baseLifespan.setValue(24_000L);
            boolean dormantSettled = LifespanService.settle(dormant);
            boolean dormantLedger = dormant.getExistingData(ReincarnationAttachments.LIFESPAN).isPresent();
            boolean dormantWrite = LifespanService.set(dormant, 100L).getFailure().isEmpty();
            ok = context.check("lifespan probe: switch_off settled=" + dormantSettled + " ledger=" + dormantLedger
                            + " written=" + LifespanService.remaining(dormant),
                    !dormantSettled && !dormantLedger && dormantWrite && LifespanService.remaining(dormant) == 100L);
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
        boolean ok;
        try {
            ReincarnationServerConfig.Lifespan settings = ReincarnationServerConfig.INSTANCE.lifespan;
            LivingEntity dormant = probe(context, probes, 1, null);
            if (dormant == null) return;

            settings.enabled.setValue(false);
            settings.baseLifespan.setValue(24_000L);
            LifespanService.set(dormant, 100L);
            settings.enabled.setValue(true);
            settings.agePerSettle.setValue(20);
            boolean resumed = LifespanService.settle(dormant) && LifespanService.remaining(dormant) == 80L;
            ok = context.check("lifespan probe: switch_on remaining=" + LifespanService.remaining(dormant)
                    + "/" + LifespanService.total(dormant), resumed);
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
        boolean ok;
        try {
            ReincarnationServerConfig.Lifespan settings = ReincarnationServerConfig.INSTANCE.lifespan;
            LivingEntity ledger = probe(context, probes, 1, null);
            if (ledger == null) return;

            settings.enabled.setValue(true);
            settings.agePerSettle.setValue(20);
            LifespanService.set(ledger, 100L);
            boolean setLedger = LifespanService.remaining(ledger) == 100L && LifespanService.total(ledger) == 100L;
            LifespanService.add(ledger, 50L);
            boolean added = LifespanService.remaining(ledger) == 150L && LifespanService.total(ledger) == 150L;
            LifespanService.add(ledger, -30L);
            boolean spent = LifespanService.remaining(ledger) == 120L && LifespanService.total(ledger) == 150L;
            boolean refusedSet = LifespanService.set(ledger, -1L).getFailure().isPresent()
                    && LifespanService.remaining(ledger) == 120L && LifespanService.total(ledger) == 150L;
            // Ageing of zero settles without spending anything.
            settings.agePerSettle.setValue(0);
            boolean ageless = LifespanService.settle(ledger) && LifespanService.remaining(ledger) == 120L;
            settings.agePerSettle.setValue(20);
            ok = context.check("lifespan probe: ledger set=" + setLedger + " added=" + added + " spent=" + spent
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
        boolean ok;
        try {
            ReincarnationServerConfig.Lifespan settings = ReincarnationServerConfig.INSTANCE.lifespan;
            LivingEntity seeded = probe(context, probes, 1, null);
            LivingEntity plain = probe(context, probes, 2, null);
            if (seeded == null || plain == null) return;

            settings.enabled.setValue(true);
            settings.agePerSettle.setValue(20);
            settings.baseLifespan.setValue(24_000L);
            LifespanService.add(seeded, 40L);
            boolean fromBase = LifespanService.remaining(seeded) == 24_040L && LifespanService.total(seeded) == 24_040L;
            settings.baseLifespan.setValue(0L);
            LifespanService.add(plain, 40L);
            boolean fromZero = LifespanService.remaining(plain) == 40L && LifespanService.total(plain) == 40L;
            ok = context.check("lifespan probe: seed base=" + LifespanService.remaining(seeded)
                    + " zero=" + LifespanService.remaining(plain), fromBase && fromZero);
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
        boolean ok;
        try {
            ReincarnationServerConfig.Lifespan settings = ReincarnationServerConfig.INSTANCE.lifespan;
            LivingEntity settled = probe(context, probes, 1, null);
            if (settled == null) return;

            settings.enabled.setValue(true);
            settings.agePerSettle.setValue(20);
            settings.onExpire.setValue(LifespanOutcome.NONE);
            LifespanService.set(settled, 25L);
            List<LifespanOutcome> outcomes = new ArrayList<>();
            Consumer<LifeSpanEndEvent.Post> recorder = event -> outcomes.add(event.getOutcome());
            NeoForge.EVENT_BUS.addListener(recorder);
            boolean decremented;
            boolean expired;
            try {
                decremented = LifespanService.settle(settled) && LifespanService.remaining(settled) == 5L
                        && LifespanService.total(settled) == 25L;
                expired = LifespanService.settle(settled);
            } finally {
                NeoForge.EVENT_BUS.unregister(recorder);
            }
            boolean closed = LifespanService.remaining(settled) == -1L && LifespanService.total(settled) == 0L;
            ok = context.check("lifespan probe: settle decremented=" + decremented + " closed=" + closed
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
        boolean ok;
        try {
            ReincarnationServerConfig.Lifespan settings = ReincarnationServerConfig.INSTANCE.lifespan;
            LivingEntity cancelled = probe(context, probes, 1, null);
            if (cancelled == null) return;

            settings.enabled.setValue(true);
            settings.agePerSettle.setValue(20);
            Consumer<LifeSpanEndEvent.Pre> refuse = event -> event.setCanceled(true);
            Consumer<LifeSpanEndEvent.Pre> extend = event -> {
                event.setCanceled(true);
                if (event.getEntity() instanceof LivingEntity living) LifespanService.add(living, 50L);
            };
            LifespanService.set(cancelled, 5L);
            NeoForge.EVENT_BUS.addListener(refuse);
            try {
                LifespanService.settle(cancelled);
            } finally {
                NeoForge.EVENT_BUS.unregister(refuse);
            }
            boolean refusedEnd = LifespanService.remaining(cancelled) == -1L && LifespanService.total(cancelled) == 0L;
            LifespanService.set(cancelled, 5L);
            NeoForge.EVENT_BUS.addListener(extend);
            try {
                LifespanService.settle(cancelled);
            } finally {
                NeoForge.EVENT_BUS.unregister(extend);
            }
            boolean extended = LifespanService.remaining(cancelled) == 50L;
            ok = context.check("lifespan probe: pre_cancel closed=" + refusedEnd
                    + " extended=" + LifespanService.remaining(cancelled), refusedEnd && extended);
            verdict(context, ok);
        } finally {
            restore(was);
            discard(probes);
        }
    }

    // 7. DEATH on a non-player: it really dies, and it dies of the reincarnation:lifespan cause that the no_bonus
    //    tag keeps out of the element pipeline.
    private static void death(ProbeContext context) {
        Snapshot was = snapshot();
        List<LivingEntity> probes = new ArrayList<>();
        boolean ok;
        try {
            ReincarnationServerConfig.Lifespan settings = ReincarnationServerConfig.INSTANCE.lifespan;
            LivingEntity doomed = probe(context, probes, 1, null);
            if (doomed == null) return;

            settings.enabled.setValue(true);
            settings.agePerSettle.setValue(20);
            settings.onExpire.setValue(LifespanOutcome.DEATH);
            LifespanService.set(doomed, 5L);
            LifespanService.settle(doomed);
            DamageSource cause = doomed.getLastDamageSource();
            boolean died = !doomed.isAlive() && cause != null && DamageCalculationService.bypasses(cause)
                    && cause.is(LifespanService.DAMAGE_TYPE);
            ok = context.check("lifespan probe: death alive=" + doomed.isAlive() + " cause="
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
        boolean ok;
        try {
            ReincarnationServerConfig.Lifespan settings = ReincarnationServerConfig.INSTANCE.lifespan;
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
            AbilityAttachment rebornAbilities = reborn.getData(SaturnAttachments.ABILITY_HOLDER);
            rebornSpirit.setRealmStage(qiRefining);
            rebornSpirit.setCultivationProgress(ProbeKit.requireProfile(context, ProbeDefinitions.QI), 80.0D);
            rebornStages.raiseMinorStageRecord(qiRefining, 2);
            rebornTechniques.addLearnedTechnique(context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.TECHNIQUE));
            AbilityGrantService.recalculate(reborn);
            boolean realmAbility = rebornAbilities.has(context.require(SaturnResourceKeys.ABILITY, context.id("awaken_divine_sense")));
            LifespanService.set(reborn, 5L);
            boolean rebornSettled = LifespanService.settle(reborn);
            rebornSpirit = reborn.getData(MxtAttachments.CULTIVATION);
            rebornStages = reborn.getData(MxtAttachments.MINOR_STAGES);
            rebornAbilities = reborn.getData(SaturnAttachments.ABILITY_HOLDER);
            boolean reset = rebornSpirit.realmStages().isEmpty() && rebornStages.minorStageRecord(qiRefining) == -1
                    && !rebornAbilities.has(context.require(SaturnResourceKeys.ABILITY, context.id("awaken_divine_sense")));
            // What the body still is keeps its grants, so the root's own ability survives the realm's.
            boolean kept = rebornTechniques.learnedTechniques().contains(context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.TECHNIQUE))
                    && !reborn.getData(MxtAttachments.SPIRIT_IDENTITY).spiritRoots().isEmpty()
                    && rebornAbilities.has(context.require(SaturnResourceKeys.ABILITY, ProbeDefinitions.PROBE_AFFINITY_ABILITY));
            boolean rebornLedger = LifespanService.remaining(reborn) == 24_000L && LifespanService.total(reborn) == 24_000L;
            ok = context.check("lifespan probe: reincarnate realm_ability=" + realmAbility
                            + " realms=" + rebornSpirit.realmStages().size()
                            + " minor_stage=" + rebornStages.minorStageRecord(qiRefining)
                            + " kept=" + kept + " ledger=" + LifespanService.remaining(reborn),
                    realmAbility && rebornSettled && reset && kept && rebornLedger);
            verdict(context, ok);
        } finally {
            restore(was);
            discard(probes);
        }
    }

    // The spirit_power realm opens the payment resource's cap; the lifespan chain starts from a mortal.
    private static void breakthrough(ProbeContext context) {
        Snapshot was = snapshot();
        List<LivingEntity> probes = new ArrayList<>();
        boolean ok;
        try {
            ReincarnationServerConfig.Lifespan settings = ReincarnationServerConfig.INSTANCE.lifespan;
            LivingEntity climber = probe(context, probes, 1, null);
            if (climber == null) return;

            settings.enabled.setValue(true);
            settings.agePerSettle.setValue(20);
            settings.onExpire.setValue(LifespanOutcome.NONE);
            settings.baseLifespan.setValue(0L);
            CultivationAttachment climbSpirit = climber.getData(MxtAttachments.CULTIVATION);
            ResourceHolderAttachment climbResources = climber.getData(SaturnAttachments.RESOURCE_HOLDER);
            climbSpirit.setRealmStage(context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.SPIRIT_POWER_REFINING));
            Holder<Resource> climbPayment = context.require(SaturnResourceKeys.RESOURCE, ProbeDefinitions.SPIRIT_POWER);
            ProbeKit.ensureResource(climber, climbResources, climbPayment, 100.0D);
            Holder<Aura> aura = context.require(MxtResourceKeys.AURA, context.id("lifespan_breakthrough"));
            FormulaContext formula = FormulaContext.of(climber);
            climbSpirit.setCultivationProgress(aura, 100.0D);
            Result<Void> refused = CultivationService.attempt(climber, climbSpirit,
                    climbResources, aura, formula, () -> false);
            context.check("failed breakthrough does not run success actions", refused.getFailure().isPresent()
                    && LifespanService.remaining(climber) <= 0L && !climber.hasEffect(MobEffects.REGENERATION));
            Result<Void> breakthrough = CultivationService.attempt(climber, climbSpirit,
                    climbResources, aura, formula, () -> true);
            // The pack's cap is part of the arithmetic: 25 is the fixture's own cost, and a body that cannot hold
            // that much turns the whole part into an INSUFFICIENT_RESOURCE answer that says nothing about lifespan.
            boolean affordable = climbResources.get(climbPayment) >= 25.0D;
            boolean granted = (breakthrough instanceof Result.SuccessResult<?>) && LifespanService.remaining(climber) == 2_400L
                    && LifespanService.total(climber) == 3_000L;
            context.check("lifespan and existing success actions compose", climber.hasEffect(MobEffects.REGENERATION));
            Result<Void> repeated = CultivationService.attempt(climber, climbSpirit, climbResources, aura, formula, () -> true);
            context.check("completed chain does not grant lifespan again", repeated.getFailure().isPresent()
                    && LifespanService.remaining(climber) == 2_400L && LifespanService.total(climber) == 3_000L);
            ok = context.check("lifespan probe: breakthrough advanced=" + (breakthrough instanceof Result.SuccessResult<?>)
                            + " failure=" + breakthrough.getFailure().orElse(null)
                            + " affordable=" + affordable + " granted=" + LifespanService.remaining(climber),
                    affordable && granted);
            verdict(context, ok);
        } finally {
            restore(was);
            discard(probes);
        }
    }

    // 10. The add and remove actions.
    private static void action(ProbeContext context) {
        Snapshot was = snapshot();
        List<LivingEntity> probes = new ArrayList<>();
        boolean ok;
        try {
            ReincarnationServerConfig.Lifespan settings = ReincarnationServerConfig.INSTANCE.lifespan;
            LivingEntity acted = probe(context, probes, 1, null);
            if (acted == null) return;

            settings.baseLifespan.setValue(0L);
            new AddLifespanAction(new Constant(30.0D))
                    .execute(acted, FormulaContext.of(acted));
            boolean actionAdded = LifespanService.remaining(acted) == 30L && LifespanService.total(acted) == 30L;
            new RemoveLifespanAction(new Constant(10.0D))
                    .execute(acted, FormulaContext.of(acted));
            new RemoveLifespanAction(new Constant(-5.0D))
                    .execute(acted, FormulaContext.of(acted));
            boolean actionTook = LifespanService.remaining(acted) == 20L && LifespanService.total(acted) == 30L;
            ok = context.check("lifespan probe: action added=" + actionAdded + " took=" + actionTook
                            + " remaining=" + LifespanService.remaining(acted),
                    actionAdded && actionTook);
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
        boolean ok;
        try {
            ReincarnationServerConfig.Lifespan settings = ReincarnationServerConfig.INSTANCE.lifespan;
            LivingEntity untouched = probe(context, probes, 1, null);
            if (untouched == null) return;

            settings.enabled.setValue(true);
            settings.baseLifespan.setValue(0L);
            boolean zeroAdd = LifespanService.add(untouched, 0L).getFailure().isEmpty()
                    && untouched.getExistingData(ReincarnationAttachments.LIFESPAN).isEmpty();
            boolean clean = !LifespanService.settle(untouched)
                    && untouched.getExistingData(ReincarnationAttachments.LIFESPAN).isEmpty()
                    && LifespanService.remaining(untouched) == -1L;
            ok = context.check("lifespan probe: no_ledger zero_add=" + zeroAdd + " settle_created="
                    + untouched.getExistingData(ReincarnationAttachments.LIFESPAN).isPresent(), zeroAdd && clean);
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
        boolean ok;
        try {
            ReincarnationServerConfig.Lifespan settings = ReincarnationServerConfig.INSTANCE.lifespan;
            LivingEntity restart = probe(context, probes, 1, ProbeDefinitions.PROBE_FIRE_ROOT);
            if (restart == null) return;

            reincarnationPolicy();
            settings.enabled.setValue(false);
            settings.baseLifespan.setValue(24_000L);
            Holder<RealmStage> qiRefining = context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.QI_REFINING);
            CultivationAttachment restartSpirit = restart.getData(MxtAttachments.CULTIVATION);
            MinorStageAttachment restartIdentity = restart.getData(MxtAttachments.MINOR_STAGES);
            AbilityAttachment restartAbilities = restart.getData(SaturnAttachments.ABILITY_HOLDER);
            restartSpirit.setRealmStage(qiRefining);
            restartIdentity.raiseMinorStageRecord(qiRefining, 2);
            AbilityGrantService.recalculate(restart);
            boolean restartAbility = restartAbilities.has(context.require(SaturnResourceKeys.ABILITY, context.id("awaken_divine_sense")));
            LifespanService.set(restart, 5L);
            boolean[] ended = {false};
            int[] rebirths = {0};
            Consumer<LifeSpanEndEvent.Post> watch = event -> ended[0] = true;
            Consumer<LifeSpanRebirthEvent.Post> counted = event -> rebirths[0]++;
            Consumer<LifeSpanRebirthEvent.Pre> veto = event -> event.setCanceled(true);
            NeoForge.EVENT_BUS.addListener(watch);
            NeoForge.EVENT_BUS.addListener(counted);
            NeoForge.EVENT_BUS.addListener(veto);
            Result<Void> refused;
            try {
                refused = LifespanService.reincarnate(restart);
            } finally {
                NeoForge.EVENT_BUS.unregister(veto);
            }
            // A refused rebirth changes nothing at all: the realm is still there and the ledger still reads 5.
            boolean restartRefused = refused.getFailure().isPresent() && refused.getFailure().orElse(null) == Failures.CANCELLED
                    && !restartSpirit.realmStages().isEmpty() && LifespanService.remaining(restart) == 5L;
            Result<Void> restarted;
            try {
                restarted = LifespanService.reincarnate(restart);
            } finally {
                NeoForge.EVENT_BUS.unregister(watch);
                NeoForge.EVENT_BUS.unregister(counted);
            }
            restartSpirit = restart.getData(MxtAttachments.CULTIVATION);
            restartIdentity = restart.getData(MxtAttachments.MINOR_STAGES);
            restartAbilities = restart.getData(SaturnAttachments.ABILITY_HOLDER);
            boolean restartReset = restartSpirit.realmStages().isEmpty()
                    && restartIdentity.minorStageRecord(qiRefining) == -1
                    && !restartAbilities.has(context.require(SaturnResourceKeys.ABILITY, context.id("awaken_divine_sense")))
                    && restartAbilities.has(context.require(SaturnResourceKeys.ABILITY, ProbeDefinitions.PROBE_AFFINITY_ABILITY));
            boolean restartLedger = LifespanService.remaining(restart) == 24_000L && LifespanService.total(restart) == 24_000L;
            ok = context.check("lifespan probe: explicit_reincarnate ran=" + (restarted.getFailure().isEmpty())
                            + " refused=" + restartRefused + " ability=" + restartAbility
                            + " realms=" + restartSpirit.realmStages().size()
                            + " kept_root=" + restartAbilities.has(context.require(SaturnResourceKeys.ABILITY, ProbeDefinitions.PROBE_AFFINITY_ABILITY))
                            + " ledger=" + LifespanService.remaining(restart)
                            + " rebirth_post=" + rebirths[0] + " end_event=" + ended[0],
                    restartAbility && restarted.getFailure().isEmpty() && restartRefused && restartReset && restartLedger
                            && rebirths[0] == 1 && !ended[0]);
            verdict(context, ok);
        } finally {
            restore(was);
            discard(probes);
        }
    }

    // 13. The data pack entry: reincarnation:reincarnate decodes by its own type name and runs that same reset, which
    //     is what a pack's own reincarnation pill needs instead of waiting for a life to run out.
    private static void reincarnateAction(ProbeContext context) {
        Snapshot was = snapshot();
        List<LivingEntity> probes = new ArrayList<>();
        boolean ok;
        try {
            ReincarnationServerConfig.Lifespan settings = ReincarnationServerConfig.INSTANCE.lifespan;
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
                    JsonParser.parseString("{\"type\": \"reincarnation:reincarnate\"}")).getOrThrow();
            action.execute(restart, FormulaContext.of(restart));
            LifespanService.flush();
            restartSpirit = restart.getData(MxtAttachments.CULTIVATION);
            boolean actionRebirth = restartSpirit.realmStages().isEmpty()
                    && LifespanService.remaining(restart) == 24_000L;
            ok = context.check("lifespan probe: reincarnate_action realms=" + restartSpirit.realmStages().size()
                    + " ledger=" + LifespanService.remaining(restart), actionRebirth);
            verdict(context, ok);
        } finally {
            restore(was);
            discard(probes);
        }
    }

    private static void reincarnationPolicy() {
        ReincarnationServerConfig.INSTANCE.lifespan.killOnReset.setValue(false);
        ReincarnationServerConfig.INSTANCE.reset.clearResources.setValue(false);
        ReincarnationServerConfig.INSTANCE.reset.resetUnknownModData.setValue(false);
        Map<TagKey<AttachmentType<?>>, List<Holder<AttachmentType<?>>>> tags = tags();
        tags.put(AttachmentTypeTags.KEEP_ON_REBIRTH, List.of(
                NeoForgeRegistries.ATTACHMENT_TYPES.wrapAsHolder(MxtAttachments.SPIRIT_IDENTITY.get()),
                NeoForgeRegistries.ATTACHMENT_TYPES.wrapAsHolder(MxtAttachments.TECHNIQUE.get()),
                NeoForgeRegistries.ATTACHMENT_TYPES.wrapAsHolder(MxtAttachments.SOUL_STATS.get())));
        tags(tags);
    }

    private static Map<TagKey<AttachmentType<?>>, List<Holder<AttachmentType<?>>>> tags() {
        Map<TagKey<AttachmentType<?>>, List<Holder<AttachmentType<?>>>> tags = new HashMap<>();
        NeoForgeRegistries.ATTACHMENT_TYPES.getTags().forEach(tag -> tags.put(tag.key(), tag.stream().toList()));
        return tags;
    }

    private static void tags(Map<TagKey<AttachmentType<?>>, List<Holder<AttachmentType<?>>>> tags) {
        NeoForgeRegistries.ATTACHMENT_TYPES.prepareTagReload(new TagLoader.LoadResult<>(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, tags)).apply();
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
                            boolean[] policy, Map<TagKey<AttachmentType<?>>, List<Holder<AttachmentType<?>>>> tags) {
    }

    private static Snapshot snapshot() {
        ReincarnationServerConfig.Lifespan settings = ReincarnationServerConfig.INSTANCE.lifespan;
        return new Snapshot(settings.enabled.getValue(), settings.baseLifespan.getValue(), settings.agePerSettle.getValue(),
                settings.onExpire.getValue(), settings.warningFraction.getValue(), policy(), tags());
    }

    private static void restore(Snapshot snapshot) {
        ReincarnationServerConfig.Lifespan settings = ReincarnationServerConfig.INSTANCE.lifespan;
        settings.enabled.setValue(snapshot.enabled());
        settings.baseLifespan.setValue(snapshot.base());
        settings.agePerSettle.setValue(snapshot.age());
        settings.onExpire.setValue(snapshot.outcome());
        settings.warningFraction.setValue(snapshot.fraction());
        BooleanEntry[] entries = policyEntries();
        for (int index = 0; index < entries.length; index++) entries[index].setValue(snapshot.policy()[index]);
        tags(snapshot.tags());
    }

    private static boolean[] policy() {
        BooleanEntry[] entries = policyEntries();
        boolean[] values = new boolean[entries.length];
        for (int index = 0; index < entries.length; index++) values[index] = entries[index].getValue();
        return values;
    }

    private static BooleanEntry[] policyEntries() {
        return new BooleanEntry[]{ReincarnationServerConfig.INSTANCE.lifespan.killOnReset,
                ReincarnationServerConfig.INSTANCE.reset.clearResources,
                ReincarnationServerConfig.INSTANCE.reset.resetUnknownModData};
    }
}
