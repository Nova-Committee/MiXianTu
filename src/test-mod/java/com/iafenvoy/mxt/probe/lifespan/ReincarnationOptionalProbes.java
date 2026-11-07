package com.iafenvoy.mxt.probe.lifespan;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.probe.*;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.saturn.attachment.ResourceHolderAttachment;
import com.iafenvoy.saturn.registry.SaturnAttachments;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.fml.ModList;

import java.util.List;

public final class ReincarnationOptionalProbes {
    public static List<Probe> probes() {
        return List.of(Probe.of("lifespan/optional_dependency", ReincarnationOptionalProbes::breakthrough));
    }

    private static void breakthrough(ProbeContext context) {
        ServerPlayer player = context.player();
        CultivationAttachment cultivation = player.getData(MxtAttachments.CULTIVATION);
        ResourceHolderAttachment resources = player.getData(SaturnAttachments.RESOURCE_HOLDER);
        cultivation.setRealmStage(context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.SPIRIT_POWER_REFINING));
        ProbeKit.ensureResource(player, resources, context.require(SaturnResourceKeys.RESOURCE, ProbeDefinitions.SPIRIT_POWER), 100);
        Holder<Aura> profile = ProbeKit.requireProfile(context, ProbeDefinitions.QI);
        cultivation.setCultivationProgress(profile, 100);
        Result<Void> result = CultivationService.attempt(player, cultivation, resources, profile, FormulaContext.of(player), () -> true);
        context.check("breakthrough with optional Reincarnation=" + ModList.get().isLoaded("reincarnation"), result.getFailure().isEmpty());
        context.check("generic success action remains available", player.hasEffect(MobEffects.REGENERATION));
        context.check("realm stage advances without a Java lifespan bridge", cultivation.realmStages().containsValue(
                context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.QI_REFINING)));
        boolean installed = ModList.get().isLoaded("reincarnation");
        context.check("lifespan realm is gated by a data loading condition",
                context.registries().lookupOrThrow(MxtResourceKeys.REALM_STAGE).containsKey(context.id("lifespan_breakthrough")) == installed);
        context.check("lifespan aura is gated by a data loading condition",
                context.registries().lookupOrThrow(MxtResourceKeys.AURA).containsKey(context.id("lifespan_breakthrough")) == installed);
        context.done();
    }
}
