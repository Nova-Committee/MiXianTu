package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Applies an ordinary vanilla status effect to a living entity.
 */
public record ApplyEffectAction(MobEffect effect, NumberProvider durationTicks,
                                NumberProvider amplifier) implements EntityAction {
    public static final MapCodec<ApplyEffectAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BuiltInRegistries.MOB_EFFECT.byNameCodec().fieldOf("effect").forGetter(ApplyEffectAction::effect),
            NumberProvider.CODEC.fieldOf("duration_ticks").forGetter(ApplyEffectAction::durationTicks),
            NumberProvider.CODEC.optionalFieldOf("amplifier", new Constant(0.0D)).forGetter(ApplyEffectAction::amplifier)
    ).apply(i, ApplyEffectAction::new));

    @Override
    public void execute(EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        if (!(entity instanceof LivingEntity living)) return;
        double duration = this.durationTicks.evaluate(context);
        double amplifier = this.amplifier.evaluate(context);
        if (!Double.isFinite(duration) || !Double.isFinite(amplifier) || duration < 1.0D || duration > Integer.MAX_VALUE || amplifier < 0.0D || amplifier > 255.0D)
            return;
        living.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(this.effect), (int) Math.round(duration), (int) Math.round(amplifier)));
    }

    @Override
    public MapCodec<ApplyEffectAction> codec() {
        return CODEC;
    }
}
