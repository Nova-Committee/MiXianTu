package com.iafenvoy.mxt.data.resource.builtin;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider;
import com.iafenvoy.mxt.runtime.aura.AuraLookup;
import com.iafenvoy.mxt.runtime.world.AuraClientState;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;

// The complete resolved concentration, including stored and block/formation contributions.
public enum ActualConcentrationResourceValueProvider implements ResourceValueProvider {
    INSTANCE;

    public static final MapCodec<ActualConcentrationResourceValueProvider> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public double resolve(ResourceHolderAttachment holder, Holder<Resource> resource, FormulaContext context) {
        return 0.0D;
    }

    @Override
    public double resolve(LivingEntity entity, Holder<Resource> resource, FormulaContext context) {
        Holder<Aura> aura = AuraLookup.holder(entity, resource).orElse(null);
        if (aura == null) return 0.0D;
        if (entity.level().isClientSide()) {
            return AuraClientState.current().actualPool(aura).amount();
        }
        return AuraService.getPositionAura(entity.level(), entity.blockPosition()).pool(aura).amount();
    }

    @Override
    public MapCodec<ActualConcentrationResourceValueProvider> codec() {
        return CODEC;
    }
}
