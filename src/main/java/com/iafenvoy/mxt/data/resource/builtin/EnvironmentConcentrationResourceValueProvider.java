package com.iafenvoy.mxt.data.resource.builtin;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.registry.cache.RegistryCaches;
import com.iafenvoy.mxt.runtime.world.AuraClientState;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.saturn.attachment.ResourceHolderAttachment;
import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.saturn.data.resource.ResourceValueProvider;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

// Only the environmental template contribution, excluding chunk storage and emitters.
public enum EnvironmentConcentrationResourceValueProvider implements ResourceValueProvider {
    INSTANCE;

    public static final MapCodec<EnvironmentConcentrationResourceValueProvider> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public double resolve(ResourceHolderAttachment holder, Holder<Resource> resource, FormulaContext context) {
        return 0.0D;
    }

    @Override
    public double resolve(LivingEntity entity, Holder<Resource> resource, FormulaContext context) {
        if (entity.level() instanceof ServerLevel level) {
            Holder<Aura> aura = RegistryCaches.RESOURCE_AURA.get(resource).orElse(null);
            return aura == null ? 0.0D : AuraService.getSensedAura(level, entity.blockPosition()).pool(aura).amount();
        }
        return entity instanceof Player player && player.isLocalPlayer()
                ? AuraClientState.current().environmentPoolForResource(resource).amount() : 0.0D;
    }

    @Override
    public MapCodec<EnvironmentConcentrationResourceValueProvider> codec() {
        return CODEC;
    }
}
