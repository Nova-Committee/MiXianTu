package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.attachment.AuraChunkData;
import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Changes only the authoritative chunk aura attachment; no client-side mutation is allowed.
 */
public record ChangeAuraBlockAction(NumberProvider amount) implements BlockAction {
    public static final MapCodec<ChangeAuraBlockAction> CODEC = NumberProvider.CODEC.fieldOf("amount").xmap(ChangeAuraBlockAction::new, ChangeAuraBlockAction::amount);

    @Override
    public void execute(Level level, BlockPos pos, FormulaContext context) {
        if (level.isClientSide()) return;
        double amount = this.amount.evaluate(context);
        if (!Double.isFinite(amount)) return;
        AuraChunkData aura = level.getChunkAt(pos).getData(MxtAttachments.AURA_CHUNK);
        aura.setConcentration(Math.max(0.0D, aura.concentration() + amount));
    }

    @Override
    public MapCodec<ChangeAuraBlockAction> codec() {
        return CODEC;
    }
}
