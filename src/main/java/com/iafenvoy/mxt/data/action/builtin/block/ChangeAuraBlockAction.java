package com.iafenvoy.mxt.data.action.builtin.block;

import com.iafenvoy.mxt.data.context.action.BlockActionContext;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Changes only the authoritative chunk aura attachment; no client-side mutation is allowed.
 */
public record ChangeAuraBlockAction(Map<Holder<Resource>, NumberProvider> aura) implements BlockAction {
    public static final MapCodec<ChangeAuraBlockAction> CODEC = CollectionCodecs.map(Resource.CODEC, NumberProvider.CODEC)
            .fieldOf("aura").xmap(ChangeAuraBlockAction::new, ChangeAuraBlockAction::aura);

    @Override
    public void execute(@NonNull BlockActionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        FormulaContext context = ctx.formula();
        if (level.isClientSide()) return;
        Map<Holder<Resource>, Double> amounts = new LinkedHashMap<>();
        for (Entry<Holder<Resource>, NumberProvider> entry : this.aura.entrySet()) {
            double amount = entry.getValue().evaluate(context);
            if (!Double.isFinite(amount)) return;
            amounts.put(entry.getKey(), amount);
        }
        AuraService.change(level, pos, amounts);
    }

    @Override
    public @NonNull MapCodec<ChangeAuraBlockAction> codec() {
        return CODEC;
    }
}
