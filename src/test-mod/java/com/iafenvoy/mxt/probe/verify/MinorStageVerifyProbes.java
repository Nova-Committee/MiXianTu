package com.iafenvoy.mxt.probe.verify;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.MinorStageAttachment;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.MinorStageService;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaVariables;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

/**
 * The minor-stage cut: which segment a progress figure falls into, the record it is written to, and the thresholds
 * it unlocks. Nothing else reports either as a number, so it is the only observable entry point.
 */
public final class MinorStageVerifyProbes {
    private MinorStageVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/minor_stages", MinorStageVerifyProbes::minorStages));
    }

    private static void minorStages(ProbeContext context) {
        String failure = verifyMinorStages(context);
        if (failure != null) context.fail(failure);
    }

    // The qi_refining fixture declares nine minor stages on a 100 requirement, so one segment is 100/9 and the
    // index is the segment progress falls into; the last legs prove minor_stage is a formula variable.
    private static String verifyMinorStages(ProbeContext context) {
        RegistryAccess access = context.registries();
        ServerPlayer player = context.player();
        Holder<RealmStage> qiRefining = context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.QI_REFINING);
        double[][] cuts = {{0.0D, 0.0D}, {11.0D, 0.0D}, {12.0D, 1.0D}, {50.0D, 4.0D},
                {99.0D, 8.0D}, {100.0D, 8.0D}, {150.0D, 8.0D}};
        for (double[] cut : cuts) {
            double index = CultivationService.minorStage(qiRefining.value(), cut[0], FormulaContext.EMPTY);
            if (!close(index, cut[1]))
                return "progress " + cut[0] + " reads as minor stage " + index + " instead of " + cut[1];
        }
        double undefined = CultivationService.minorStage(context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.FOUNDATION).value(),
                50.0D, FormulaContext.EMPTY);
        if (!Double.isNaN(undefined))
            return "a stage without minor_stages reads as " + undefined + " instead of NaN";
        CultivationAttachment spirit = player.getData(MxtAttachments.CULTIVATION);
        if (!CultivationService.setRealm(spirit, qiRefining))
            return "the realm cache could not resolve " + ProbeDefinitions.QI_REFINING;
        spirit.setCultivationProgress(context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI), 80.0D);
        FormulaContext formula = ResourceService.formulaContext(player,
                context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.QI), FormulaContext.of(player));
        double read = formula.value("minor_stage");
        if (!close(read, 7.0D)) return "the minor_stage variable reads " + read + " at 80/100 instead of 7";
        spirit.setRealmStages(Map.of());
        // FormulaContext.value() sanitises a variable that cannot answer to 0, so the raw read is the one that
        // says "this body has no stage to cut"; asking value() here could only ever read 0.
        double mortal = FormulaVariables.peek("minor_stage", formula);
        if (!Double.isNaN(mortal)) return "a mortal reads minor_stage as " + mortal + " instead of NaN";
        spirit.setRealmStage(qiRefining);
        spirit.setCultivationProgress(context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI), 80.0D);
        // The record only grows and the refresh writes what the progress says, so at 80/100 of a nine-stage realm
        // the body stands on stage 7; a realm that declares no minor stages can never be recorded by the runtime.
        MinorStageService.refresh(player, context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI), formula);
        MinorStageAttachment identity = player.getData(MxtAttachments.MINOR_STAGES);
        int reached = identity.minorStageRecord(qiRefining);
        if (reached != 7) return "the minor stage record reads " + reached + " at 80/100 instead of 7";
        if (identity.raiseMinorStageRecord(qiRefining, 3)) return "the minor stage record went backwards";
        Holder<RealmStage> noStages = context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.FOUNDATION);
        identity.raiseMinorStageRecord(noStages, 5);
        if (identity.raiseMinorStageRecord(noStages, 3) || identity.minorStageRecord(noStages) != 5)
            return "a realm without minor stages did not keep its own record";
        // The two thresholds the fixture writes: stage 2 grants the first, stage 5 adds the second, and a body
        // that never stood in the realm unlocks nothing from it.
        List<Identifier> atOne = unlockedMinorStageAbilities(qiRefining, 1, access);
        List<Identifier> atTwo = unlockedMinorStageAbilities(qiRefining, 2, access);
        List<Identifier> atFive = unlockedMinorStageAbilities(qiRefining, 5, access);
        if (!atOne.isEmpty()) return "minor stage 1 unlocks " + atOne;
        if (atTwo.size() != 1 || !atTwo.contains(ProbeDefinitions.id("awaken_divine_sense")))
            return "minor stage 2 unlocks " + atTwo + " instead of only the first threshold";
        if (atFive.size() != 2 || !atFive.containsAll(List.of(ProbeDefinitions.id("awaken_divine_sense"),
                ProbeDefinitions.id("sword_focus"))))
            return "minor stage 5 unlocks " + atFive + " instead of both thresholds";
        if (!unlockedMinorStageAbilities(qiRefining, -1, access).isEmpty())
            return "a realm the body never entered unlocks something";
        // The condition reads that record rather than live progress, so a threshold above it is not met even
        // while the realm itself matches.
        if (!realmCondition(player, qiRefining, 7).test(player, FormulaContext.EMPTY))
            return "min_minor_stage 7 is not met by a record of 7";
        if (realmCondition(player, qiRefining, 8).test(player, FormulaContext.EMPTY))
            return "min_minor_stage 8 was met by a record of 7";
        return null;
    }

    private static List<Identifier> unlockedMinorStageAbilities(Holder<RealmStage> stage, int reached, Provider access) {
        return MinorStageService.unlockedAbilities(stage, reached, access).stream().map(HolderHelper::id).toList();
    }

    // Built through the codec the loader uses, so the field is proven to decode as well as to judge.
    private static EntityCondition realmCondition(ServerPlayer player, Holder<RealmStage> stage, int minMinorStage) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "mxt:realm");
        json.addProperty("realm", HolderHelper.id(stage).toString());
        json.addProperty("min_minor_stage", minMinorStage);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, player.level().registryAccess());
        return EntityCondition.CODEC.parse(ops, json).result()
                .orElseThrow(() -> new IllegalStateException("mxt:realm with min_minor_stage does not decode"));
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }
}
