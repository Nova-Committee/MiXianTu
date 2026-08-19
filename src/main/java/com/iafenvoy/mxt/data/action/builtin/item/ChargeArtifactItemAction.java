package com.iafenvoy.mxt.data.action.builtin.item;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public record ChargeArtifactItemAction(NumberProvider amount, NumberProvider capacity) implements ItemAction {
    public static final MapCodec<ChargeArtifactItemAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("amount").forGetter(ChargeArtifactItemAction::amount),
            NumberProvider.CODEC.fieldOf("capacity").forGetter(ChargeArtifactItemAction::capacity)
    ).apply(i, ChargeArtifactItemAction::new));

    @Override
    public void execute(Entity holder, ItemStack stack, FormulaContext context) {
        ArtifactService.addEnergy(stack, this.amount.evaluate(context), this.capacity.evaluate(context));
    }

    @Override
    public MapCodec<ChargeArtifactItemAction> codec() {
        return CODEC;
    }
}
