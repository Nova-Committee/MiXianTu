package com.iafenvoy.mxt.data.action.builtin.item;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.context.action.ItemActionContext;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;

/**
 * Charges one named aura of an artifact from a declared amount.
 *
 * <p>The aura is named because an artifact stores as many kinds as its definition lists, and "fill it up" is
 * only an instruction once the kind is known. The declared capacity is the fallback rather than the ceiling: a
 * stack whose definition names that aura is measured against what the definition declares, so this number only
 * decides how much a stack that no definition claims can hold.</p>
 */
public record ChargeArtifactItemAction(Holder<Aura> aura, NumberProvider amount, NumberProvider capacity) implements ItemAction {
    public static final MapCodec<ChargeArtifactItemAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Aura.CODEC.fieldOf("aura").forGetter(ChargeArtifactItemAction::aura),
            NumberProvider.CODEC.fieldOf("amount").forGetter(ChargeArtifactItemAction::amount),
            NumberProvider.CODEC.optionalFieldOf("capacity", new Constant(0.0D)).forGetter(ChargeArtifactItemAction::capacity)
    ).apply(i, ChargeArtifactItemAction::new));

    @Override
    public void execute(@NonNull ItemActionContext ctx) {
        // Resolving the definition needs the acting entity's registries, so an action with no actor has nothing
        // to charge against.
        if (ctx.holder() == null) return;
        FormulaContext context = ctx.formula();
        ArtifactService.addEnergy(ctx.holder().level().registryAccess(), ctx.stack(), this.aura,
                this.amount.evaluate(context), this.capacity.evaluate(context), context);
    }

    @Override
    public @NonNull MapCodec<ChargeArtifactItemAction> codec() {
        return CODEC;
    }
}
