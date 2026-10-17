package com.iafenvoy.mxt.probe.creature;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.condition.builtin.entity.ContractEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.ProgressionEntityCondition;
import com.iafenvoy.mxt.data.creature.ContractBehaviors;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.trigger.TriggerSignals;
import com.iafenvoy.mxt.data.trigger.builtin.BuiltinTrigger;
import com.iafenvoy.mxt.event.dispatcher.EntitySources;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityGrantService;
import com.iafenvoy.mxt.runtime.ability.PassiveAttributeService;
import com.iafenvoy.mxt.runtime.creature.ContractService;
import com.iafenvoy.mxt.runtime.progression.ProgressionAdminService;
import com.iafenvoy.mxt.runtime.progression.ProgressionDamageMultiplier;
import com.iafenvoy.mxt.runtime.progression.ProgressionDriver;
import com.iafenvoy.mxt.runtime.progression.ProgressionService;
import com.iafenvoy.mxt.runtime.trigger.TriggerDispatcher;
import com.iafenvoy.mxt.runtime.trigger.TriggerSubscription;
import com.iafenvoy.mxt.testmod.MxtTestEntities;
import com.iafenvoy.mxt.testmod.ProbeBeast;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.CompareOperation;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.List;

/**
 * A profile that names a chain makes its creature a progression owner, and the owner side is no longer
 * technique-specific: mastery is a stored value the pack feeds, a level's condition still gates it, and what a
 * level grants goes through the one grant entry.
 */
public final class ProgressionProbes {
    private ProgressionProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("creature/progression/entry", ProgressionProbes::entry),
                Probe.of("creature/progression/contract_condition", ProgressionProbes::contractCondition),
                Probe.of("creature/progression/advance", ProgressionProbes::advance),
                Probe.of("creature/progression/reading", ProgressionProbes::reading),
                Probe.of("creature/progression/admin", ProgressionProbes::admin),
                Probe.of("creature/progression/transition", ProgressionProbes::transition),
                Probe.of("creature/progression/prune", ProgressionProbes::prune),
                Probe.of("creature/progression/ending", ProgressionProbes::ending),
                Probe.of("creature/progression/death", ProgressionProbes::death));
    }

    // The entry level is where a creature stands before any record exists, which is what makes an entry level's
    // own configuration the "born with it" list.
    private static void entry(ProbeContext context) {
        ServerLevel level = context.level();
        ProbeBeast.reset();
        List<ProbeBeast> spawned = new ArrayList<>();
        try {
            ProbeBeast beast = spawnProbeBeast(level, context.player().blockPosition().offset(4, 0, 0));
            spawned.add(beast);
            AbilityAttachment abilities = beast.getData(MxtAttachments.ABILITY_HOLDER);
            boolean entry = stoodOn(beast, ProbeDefinitions.BEAST_1)
                    && EntitySources.held(beast, ProbeDefinitions.PROBE_BEAST_PROFILE)
                    .flatMap(ProgressionService::next)
                    .map(held -> HolderHelper.id(held).equals(ProbeDefinitions.BEAST_2)).orElse(false)
                    && beast.getData(MxtAttachments.CREATURE_SPIRIT).profileLevels().levels().isEmpty()
                    && !abilities.has(context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.PROGRESSION_GROWTH));
            context.note("progression probe: entry=" + entry);
            context.check("progression entry", entry);
        } finally {
            for (ProbeBeast mob : spawned) mob.discard();
        }
    }

    // The contract condition answers from the record, so a body nobody took answers bound=false; the
    // contradictory shape is refused when the definition loads rather than read as a silent false.
    private static void contractCondition(ProbeContext context) {
        ServerLevel level = context.level();
        ProbeBeast.reset();
        List<ProbeBeast> spawned = new ArrayList<>();
        try {
            ProbeBeast beast = spawnProbeBeast(level, context.player().blockPosition().offset(4, 0, 0));
            spawned.add(beast);
            boolean contractAsk = new ContractEntityCondition(false, List.of(), List.of()).test(beast, FormulaContext.of(beast))
                    && !new ContractEntityCondition(true, List.of(), List.of()).test(beast, FormulaContext.of(beast))
                    && ContractEntityCondition.CODEC.codec().parse(JsonOps.INSTANCE, contradictoryContractAsk()).error().isPresent();
            context.note("progression probe: contract_ask=" + contractAsk);
            context.check("progression contract_ask", contractAsk);
        } finally {
            for (ProbeBeast mob : spawned) mob.discard();
        }
    }

    // Mastery is the pack's business: nothing climbs until the stored value reaches what the level asks, and the
    // level's own action runs once on entering it, before the signal goes out.
    private static void advance(ProbeContext context) {
        ServerLevel level = context.level();
        ProbeBeast.reset();
        List<ProbeBeast> spawned = new ArrayList<>();
        Holder<Ability> growthAbility = context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.PROGRESSION_GROWTH);
        Holder<Resource> growth = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.BEAST_GROWTH);
        // The id a creature's grants are written under: one category, so ending the bond can revoke exactly them.
        Identifier grantSource = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "grant/creature/"
                + ProbeDefinitions.PROBE_BEAST_PROFILE.getNamespace() + "/" + ProbeDefinitions.PROBE_BEAST_PROFILE.getPath());
        String identity = "progression_probe";
        List<Long> signals = new ArrayList<>();
        ProbeBeast beast = null;
        try {
            beast = spawnProbeBeast(level, context.player().blockPosition().offset(4, 0, 0));
            spawned.add(beast);
            TriggerDispatcher.register(new TriggerSubscription(beast.getUUID(), "probe", identity,
                    new BuiltinTrigger(TriggerSignals.PROGRESSION_LEVEL), signal -> true,
                    signal -> signals.add(signal.gameTime()), false));
            ResourceHolderAttachment resources = beast.getData(MxtAttachments.RESOURCE_HOLDER);
            AbilityAttachment abilities = beast.getData(MxtAttachments.ABILITY_HOLDER);
            double baseHealth = beast.getAttributeValue(Attributes.MAX_HEALTH);

            resources.set(growth, 9.0D, 0.0D, 1000.0D, -1L, "probe");
            boolean belowTarget = !ProgressionDriver.tick(beast) && stoodOn(beast, ProbeDefinitions.BEAST_1);
            resources.set(growth, 10.0D, 0.0D, 1000.0D, -1L, "probe");
            boolean promoted = ProgressionDriver.tick(beast) && stoodOn(beast, ProbeDefinitions.BEAST_2) && signals.size() == 1;
            AbilityGrantService.recalculate(beast);
            PassiveAttributeService.tick(beast);
            boolean granted = abilities.has(growthAbility)
                    && abilities.sources().entries().stream()
                    .anyMatch(line -> line.getKey().equals(growthAbility) && line.getValue().equals(grantSource))
                    && close(beast.getAttributeValue(Attributes.MAX_HEALTH), baseHealth + 3.0D)
                    // The level's own action ran once on entering it, and it ran before the signal went out.
                    && close(resources.get(growth), 11.0D);
            boolean advance = belowTarget && promoted && granted;
            context.note("progression probe: below_target=" + belowTarget
                    + " promoted=" + promoted + " signals=" + signals.size() + " granted=" + granted
                    + (advance ? " OK" : " MISMATCH"));
            context.check("progression advance: below_target", belowTarget);
            context.check("progression advance: promoted", promoted);
            context.check("progression advance: granted", granted);
            context.check("progression advance", advance);
        } finally {
            if (beast != null) TriggerDispatcher.unregister(beast.getUUID(), "probe", identity);
            for (ProbeBeast mob : spawned) mob.discard();
        }
    }

    // The condition is asked every time, so paid mastery is still not enough when the level says no; the read
    // side is the same owner-agnostic condition a data pack writes.
    private static void reading(ProbeContext context) {
        ServerLevel level = context.level();
        ProbeBeast.reset();
        List<ProbeBeast> spawned = new ArrayList<>();
        Holder<Progression> second = context.require(MxtResourceKeys.PROGRESSION, ProbeDefinitions.BEAST_2);
        Holder<Progression> third = context.require(MxtResourceKeys.PROGRESSION, ProbeDefinitions.BEAST_3);
        Holder<Ability> growthAbility = context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.PROGRESSION_GROWTH);
        Holder<Resource> growth = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.BEAST_GROWTH);
        String identity = "progression_probe";
        List<Long> signals = new ArrayList<>();
        ProbeBeast beast = null;
        try {
            beast = spawnProbeBeast(level, context.player().blockPosition().offset(4, 0, 0));
            spawned.add(beast);
            TriggerDispatcher.register(new TriggerSubscription(beast.getUUID(), "probe", identity,
                    new BuiltinTrigger(TriggerSignals.PROGRESSION_LEVEL), signal -> true,
                    signal -> signals.add(signal.gameTime()), false));
            ResourceHolderAttachment resources = beast.getData(MxtAttachments.RESOURCE_HOLDER);
            // The reading leg reads a creature that already climbed once, so the promotion is the setup here.
            resources.set(growth, 10.0D, 0.0D, 1000.0D, -1L, "probe");
            ProgressionDriver.tick(beast);
            AbilityGrantService.recalculate(beast);

            resources.set(growth, 25.0D, 0.0D, 1000.0D, -1L, "probe");
            boolean gated = !ProgressionDriver.tick(beast) && stoodOn(beast, ProbeDefinitions.BEAST_2) && signals.size() == 1;
            boolean reads = new ProgressionEntityCondition(second, CompareOperation.GREATER_THAN_OR_EQUAL,
                    List.of(ProbeDefinitions.PROBE_BEAST_PROFILE)).test(beast, FormulaContext.of(beast))
                    && new ProgressionEntityCondition(second, CompareOperation.EQUAL,
                    List.of(ProbeDefinitions.PROBE_BEAST_PROFILE)).test(beast, FormulaContext.of(beast))
                    && !new ProgressionEntityCondition(third, CompareOperation.GREATER_THAN_OR_EQUAL,
                    List.of(ProbeDefinitions.PROBE_BEAST_PROFILE)).test(beast, FormulaContext.of(beast))
                    && close(ProgressionDamageMultiplier.of(beast, growthAbility), 1.5D);
            boolean reading = gated && reads;
            context.note("progression probe: gated=" + gated + " reads=" + reads
                    + (reading ? " OK" : " MISMATCH"));
            context.check("progression reading: gated", gated);
            context.check("progression reading: reads", reads);
            context.check("progression reading", reading);
        } finally {
            if (beast != null) TriggerDispatcher.unregister(beast.getUUID(), "probe", identity);
            for (ProbeBeast mob : spawned) mob.discard();
        }
    }

    // The administrative write records a level the way a promotion does - same rebuild, same signal - and only
    // the chain is checked: a level of another owner's chain is refused, the same one is not a write.
    private static void admin(ProbeContext context) {
        ServerLevel level = context.level();
        ProbeBeast.reset();
        List<ProbeBeast> spawned = new ArrayList<>();
        Holder<Progression> third = context.require(MxtResourceKeys.PROGRESSION, ProbeDefinitions.BEAST_3);
        Holder<Progression> foreign = context.require(MxtResourceKeys.PROGRESSION, ProbeDefinitions.SWORD_ART_1);
        Holder<Ability> growthAbility = context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.PROGRESSION_GROWTH);
        Holder<Resource> growth = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.BEAST_GROWTH);
        String identity = "progression_probe";
        List<Long> signals = new ArrayList<>();
        ProbeBeast beast = null;
        try {
            beast = spawnProbeBeast(level, context.player().blockPosition().offset(4, 0, 0));
            spawned.add(beast);
            TriggerDispatcher.register(new TriggerSubscription(beast.getUUID(), "probe", identity,
                    new BuiltinTrigger(TriggerSignals.PROGRESSION_LEVEL), signal -> true,
                    signal -> signals.add(signal.gameTime()), false));
            ResourceHolderAttachment resources = beast.getData(MxtAttachments.RESOURCE_HOLDER);
            // The write is observed from the level a natural promotion reached, so that promotion is the setup.
            resources.set(growth, 10.0D, 0.0D, 1000.0D, -1L, "probe");
            ProgressionDriver.tick(beast);
            AbilityGrantService.recalculate(beast);

            boolean foreignRefused = ProgressionAdminService.setLevel(beast, ProbeDefinitions.PROBE_BEAST_PROFILE, foreign, false).failure()
                    == ProgressionAdminService.Failure.FOREIGN_LEVEL;
            boolean written = ProgressionAdminService.setLevel(beast, ProbeDefinitions.PROBE_BEAST_PROFILE, third, false).changed()
                    && stoodOn(beast, ProbeDefinitions.BEAST_3) && signals.size() == 2
                    && close(ProgressionDamageMultiplier.of(beast, growthAbility), 2.0D);
            boolean unchanged = ProgressionAdminService.setLevel(beast, ProbeDefinitions.PROBE_BEAST_PROFILE, third, false).failure()
                    == ProgressionAdminService.Failure.SAME_LEVEL;
            boolean unknown = ProgressionAdminService.setLevel(beast, ProbeDefinitions.SWORD_MANUAL, third, false).failure()
                    == ProgressionAdminService.Failure.UNKNOWN_OWNER;
            boolean admin = foreignRefused && written && unchanged && unknown;
            context.note("progression probe: foreign_refused=" + foreignRefused
                    + " written=" + written + " unchanged=" + unchanged + " unknown_owner=" + unknown
                    + (admin ? " OK" : " MISMATCH"));
            context.check("progression admin: foreign_refused", foreignRefused);
            context.check("progression admin: written", written);
            context.check("progression admin: unchanged", unchanged);
            context.check("progression admin: unknown_owner", unknown);
            context.check("progression admin", admin);
        } finally {
            if (beast != null) TriggerDispatcher.unregister(beast.getUUID(), "probe", identity);
            for (ProbeBeast mob : spawned) mob.discard();
        }
    }

    // Stepping back one level is the same walk as stepping up, so the record is rewritten and the level that was
    // left behind stops granting; stepping back onto the entry level is the absence of a record again.
    private static void transition(ProbeContext context) {
        ServerLevel level = context.level();
        ProbeBeast.reset();
        List<ProbeBeast> spawned = new ArrayList<>();
        Holder<Progression> third = context.require(MxtResourceKeys.PROGRESSION, ProbeDefinitions.BEAST_3);
        Holder<Ability> growthAbility = context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.PROGRESSION_GROWTH);
        ProbeBeast beast = null;
        try {
            beast = spawnProbeBeast(level, context.player().blockPosition().offset(4, 0, 0));
            spawned.add(beast);
            // The demotion walks down from the top of the chain, so the creature is put there first.
            ProgressionAdminService.setLevel(beast, ProbeDefinitions.PROBE_BEAST_PROFILE, third, false);
            boolean demoted = EntitySources.held(beast, ProbeDefinitions.PROBE_BEAST_PROFILE)
                    .map(ProgressionService::demote).orElse(false)
                    && stoodOn(beast, ProbeDefinitions.BEAST_2) && close(ProgressionDamageMultiplier.of(beast, growthAbility), 1.5D);
            boolean atEntry = EntitySources.held(beast, ProbeDefinitions.PROBE_BEAST_PROFILE)
                    .map(ProgressionService::demote).orElse(false)
                    && stoodOn(beast, ProbeDefinitions.BEAST_1)
                    && beast.getData(MxtAttachments.CREATURE_SPIRIT).profileLevels().levels().isEmpty();
            boolean transition = demoted && atEntry;
            context.note("progression probe: demoted=" + demoted
                    + " at_entry=" + atEntry + (transition ? " OK" : " MISMATCH"));
            context.check("progression transition: demoted", demoted);
            context.check("progression transition: at_entry", atEntry);
            context.check("progression transition", transition);
        } finally {
            for (ProbeBeast mob : spawned) mob.discard();
        }
    }

    // A record that is no longer on its owner's chain is a data-pack symptom, so it is swept rather than read:
    // the creature falls back to its entry level and what the record granted is rebuilt away.
    private static void prune(ProbeContext context) {
        ServerLevel level = context.level();
        ProbeBeast.reset();
        List<ProbeBeast> spawned = new ArrayList<>();
        Holder<Ability> growthAbility = context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.PROGRESSION_GROWTH);
        ProbeBeast beast = null;
        try {
            beast = spawnProbeBeast(level, context.player().blockPosition().offset(4, 0, 0));
            spawned.add(beast);
            AbilityAttachment abilities = beast.getData(MxtAttachments.ABILITY_HOLDER);
            double baseHealth = beast.getAttributeValue(Attributes.MAX_HEALTH);
            beast.getData(MxtAttachments.CREATURE_SPIRIT).profileLevels()
                    .set(context.require(MxtResourceKeys.CREATURE_PROFILE, ProbeDefinitions.PROBE_BEAST_PROFILE),
                            context.require(MxtResourceKeys.PROGRESSION, ProbeDefinitions.SWORD_ART_1));
            int swept = ProgressionService.pruneForeignLevels(beast);
            AbilityGrantService.recalculate(beast);
            PassiveAttributeService.tick(beast);
            boolean prune = swept == 1 && stoodOn(beast, ProbeDefinitions.BEAST_1)
                    && close(ProgressionDamageMultiplier.of(beast, growthAbility), 1.0D)
                    && !abilities.has(growthAbility)
                    && close(beast.getAttributeValue(Attributes.MAX_HEALTH), baseHealth);
            context.note("progression probe: swept=" + swept + " prune=" + prune
                    + (prune ? " OK" : " MISMATCH"));
            context.check("progression prune", prune);
        } finally {
            for (ProbeBeast mob : spawned) mob.discard();
        }
    }

    // The bond paid for the levels, so ending it takes them: a release clears the record and revokes what it
    // granted, and the same clear runs when the creature dies instead.
    private static void ending(ProbeContext context) {
        ServerLevel level = context.level();
        ServerPlayer player = context.player();
        ProbeBeast.reset();
        List<ProbeBeast> spawned = new ArrayList<>();
        Holder<Ability> growthAbility = context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.PROGRESSION_GROWTH);
        Holder<Resource> growth = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.BEAST_GROWTH);
        ProbeBeast beast = null;
        try {
            beast = spawnProbeBeast(level, player.blockPosition().offset(4, 0, 0));
            spawned.add(beast);
            ResourceHolderAttachment resources = beast.getData(MxtAttachments.RESOURCE_HOLDER);
            AbilityAttachment abilities = beast.getData(MxtAttachments.ABILITY_HOLDER);
            double baseHealth = beast.getAttributeValue(Attributes.MAX_HEALTH);
            Holder<ContractType> open = context.require(MxtResourceKeys.CONTRACT_TYPE, ProbeDefinitions.OPEN_CONTRACT);
            boolean bound = ContractService.bind(open, player, beast, true).changed();
            // While bound the condition reports the record: the type it names and the order it is under.
            boolean contractReads = new ContractEntityCondition(true, List.of(), List.of()).test(beast, FormulaContext.of(beast))
                    && new ContractEntityCondition(true, List.of(Either.left(open)), List.of()).test(beast, FormulaContext.of(beast))
                    && !new ContractEntityCondition(true, List.of(Either.left(context.require(MxtResourceKeys.CONTRACT_TYPE, ProbeDefinitions.CONTRACT))), List.of())
                    .test(beast, FormulaContext.of(beast))
                    && new ContractEntityCondition(true, List.of(), List.of(ContractBehaviors.FOLLOW.id())).test(beast, FormulaContext.of(beast))
                    && !new ContractEntityCondition(true, List.of(), List.of(ContractBehaviors.WANDER.id())).test(beast, FormulaContext.of(beast));
            resources.set(growth, 10.0D, 0.0D, 1000.0D, -1L, "probe");
            boolean regrown = ProgressionDriver.tick(beast) && stoodOn(beast, ProbeDefinitions.BEAST_2);
            AbilityGrantService.recalculate(beast);
            boolean released = ContractService.release(beast, player.getUUID(), true).changed();
            PassiveAttributeService.tick(beast);
            boolean cleared = stoodOn(beast, ProbeDefinitions.BEAST_1)
                    && beast.getData(MxtAttachments.CREATURE_SPIRIT).profileLevels().levels().isEmpty()
                    && !abilities.has(growthAbility)
                    && close(beast.getAttributeValue(Attributes.MAX_HEALTH), baseHealth)
                    && new ContractEntityCondition(false, List.of(), List.of()).test(beast, FormulaContext.of(beast));
            boolean ending = bound && contractReads && regrown && released && cleared;
            context.note("progression probe: bound=" + bound + " contract_reads=" + contractReads
                    + " regrown=" + regrown + " released=" + released + " cleared=" + cleared
                    + (ending ? " OK" : " MISMATCH"));
            context.check("progression ending: bound", bound);
            context.check("progression ending: contract_reads", contractReads);
            context.check("progression ending: regrown", regrown);
            context.check("progression ending: released", released);
            context.check("progression ending: cleared", cleared);
            context.check("progression ending", ending);
        } finally {
            for (ProbeBeast mob : spawned) mob.discard();
        }
    }

    private static void death(ProbeContext context) {
        ServerLevel level = context.level();
        ServerPlayer player = context.player();
        ProbeBeast.reset();
        List<ProbeBeast> spawned = new ArrayList<>();
        Holder<Resource> growth = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.BEAST_GROWTH);
        ProbeBeast beast = null;
        try {
            beast = spawnProbeBeast(level, player.blockPosition().offset(4, 0, 0));
            spawned.add(beast);
            ResourceHolderAttachment resources = beast.getData(MxtAttachments.RESOURCE_HOLDER);
            Holder<ContractType> open = context.require(MxtResourceKeys.CONTRACT_TYPE, ProbeDefinitions.OPEN_CONTRACT);
            boolean rebound = ContractService.bind(open, player, beast, true).changed();
            resources.set(growth, 10.0D, 0.0D, 1000.0D, -1L, "probe");
            boolean grown = ProgressionDriver.tick(beast) && stoodOn(beast, ProbeDefinitions.BEAST_2);
            beast.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
            boolean death = rebound && grown && !beast.getData(MxtAttachments.CONTRACT).bound()
                    && beast.getData(MxtAttachments.CREATURE_SPIRIT).profileLevels().levels().isEmpty();
            context.note("progression probe: rebound=" + rebound + " grown=" + grown
                    + " death=" + death + (death ? " OK" : " MISMATCH"));
            context.check("progression death: rebound", rebound);
            context.check("progression death: grown", grown);
            context.check("progression death", death);
        } finally {
            for (ProbeBeast mob : spawned) mob.discard();
        }
    }

    // The level the creature answers with for its own profile: the record, or the entry level it never left.
    private static boolean stoodOn(Mob creature, Identifier level) {
        return EntitySources.held(creature, ProbeDefinitions.PROBE_BEAST_PROFILE)
                .flatMap(ProgressionService::current)
                .map(held -> HolderHelper.id(held).equals(level)).orElse(false);
    }

    // Asks which contract a body signed while demanding that it signed none, which has no answer.
    private static JsonObject contradictoryContractAsk() {
        JsonObject object = new JsonObject();
        object.addProperty("bound", false);
        object.addProperty("type", "mxt_test:open_contract");
        return object;
    }

    private static ProbeBeast spawnProbeBeast(ServerLevel level, BlockPos pos) {
        ProbeBeast beast = MxtTestEntities.PROBE_BEAST.get().create(level, EntitySpawnReason.COMMAND);
        if (beast == null) throw new IllegalStateException("The probe beast type failed to create an entity");
        beast.setNoAi(true);
        beast.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        level.addFreshEntity(beast);
        return beast;
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }
}
