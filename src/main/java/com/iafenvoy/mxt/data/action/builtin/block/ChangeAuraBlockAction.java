package com.iafenvoy.mxt.data.action.builtin.block;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Changes only the authoritative chunk aura attachment; no client-side mutation is allowed.
 */
public record ChangeAuraBlockAction(Map<Holder<Element>, NumberProvider> aura) implements BlockAction {
    public static final MapCodec<ChangeAuraBlockAction> CODEC = CollectionCodecs.map(Element.CODEC, NumberProvider.CODEC)
            .fieldOf("aura").xmap(ChangeAuraBlockAction::new, ChangeAuraBlockAction::aura);

    @Override
    public void execute(Level level, BlockPos pos, FormulaContext context) {
        if (level.isClientSide()) return;
        Map<Holder<Element>, Double> amounts = new LinkedHashMap<>();
        for (Entry<Holder<Element>, NumberProvider> entry : this.aura.entrySet()) {
            double amount = entry.getValue().evaluate(context);
            if (!Double.isFinite(amount)) return;
            amounts.put(entry.getKey(), amount);
        }
        AuraService.change(level, pos, amounts);
    }

    @Override
    public MapCodec<ChangeAuraBlockAction> codec() {
        return CODEC;
    }
}
