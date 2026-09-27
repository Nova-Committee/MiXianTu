package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.runtime.item.PillService;
import com.iafenvoy.mxt.runtime.item.PillService.ModifyMode;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * The only arbitrary write into the pill toxicity ledger. A negative {@code add} is how a pack clears toxicity.
 * The value never goes below 0. A non-living target is a silent no-op.
 */
public record ModifyPillToxicityAction(ModifyMode mode, NumberProvider amount) implements EntityAction {
    public static final MapCodec<ModifyPillToxicityAction> CODEC = RecordCodecBuilder.<ModifyPillToxicityAction>mapCodec(i -> i.group(
            ModifyMode.CODEC.optionalFieldOf("mode", ModifyMode.ADD).forGetter(ModifyPillToxicityAction::mode),
            NumberProvider.CODEC.fieldOf("amount").forGetter(ModifyPillToxicityAction::amount)
    ).apply(i, ModifyPillToxicityAction::new)).validate(action -> action.mode() == ModifyMode.SET
            && action.amount() instanceof Constant(double value) && value < 0.0D
            ? DataResult.error(() -> "modify_pill_toxicity with mode 'set' needs a non-negative amount")
            : DataResult.success(action));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        if (!(entity instanceof LivingEntity living)) return;
        double value = this.amount.evaluate(ctx.formula());
        if (!Double.isFinite(value)) return;
        PillService.modify(living, this.mode, value);
    }

    @Override
    public @NonNull MapCodec<ModifyPillToxicityAction> codec() {
        return CODEC;
    }
}
