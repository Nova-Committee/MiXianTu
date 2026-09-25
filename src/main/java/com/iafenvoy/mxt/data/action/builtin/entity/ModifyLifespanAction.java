package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.runtime.cultivation.LifeSpanService;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * The only arbitrary write into the lifespan ledger: {@code add} extends a life (or shortens it with a negative
 * amount, which is how a curse takes years away), {@code set} rewrites both numbers. A non-living target is a
 * silent no-op, and the write lands even while the lifespan system is switched off.
 */
public record ModifyLifespanAction(Mode mode, NumberProvider amount) implements EntityAction {
    public static final MapCodec<ModifyLifespanAction> CODEC = RecordCodecBuilder.<ModifyLifespanAction>mapCodec(i -> i.group(
            Mode.CODEC.optionalFieldOf("mode", Mode.ADD).forGetter(ModifyLifespanAction::mode),
            NumberProvider.CODEC.fieldOf("amount").forGetter(ModifyLifespanAction::amount)
    ).apply(i, ModifyLifespanAction::new)).validate(action -> action.mode() == Mode.SET
            && action.amount() instanceof Constant(double value) && value < 0.0D
            ? DataResult.error(() -> "modify_lifespan with mode 'set' needs a non-negative amount")
            : DataResult.success(action));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        if (!(entity instanceof LivingEntity living)) return;
        double value = this.amount.evaluate(ctx.formula());
        if (!Double.isFinite(value)) return;
        long ticks = Math.round(value);
        if (this.mode == Mode.SET) LifeSpanService.set(living, ticks);
        else LifeSpanService.add(living, ticks);
    }

    @Override
    public @NonNull MapCodec<ModifyLifespanAction> codec() {
        return CODEC;
    }

    public enum Mode implements StringRepresentable {
        ADD, SET;

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
