package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
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
public record ApplyEffectEntityAction(MobEffect effect, NumberProvider durationTicks,
                                      NumberProvider amplifier) implements EntityAction {
    public static final MapCodec<ApplyEffectEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BuiltInRegistries.MOB_EFFECT.byNameCodec().fieldOf("effect").forGetter(ApplyEffectEntityAction::effect),
            NumberProvider.CODEC.fieldOf("duration_ticks").forGetter(ApplyEffectEntityAction::durationTicks),
            NumberProvider.CODEC.optionalFieldOf("amplifier", new Constant(0.0D)).forGetter(ApplyEffectEntityAction::amplifier)
    ).apply(instance, ApplyEffectEntityAction::new));

    @Override
    public void execute(Entity entity) {
        this.execute(entity, FormulaContext.EMPTY);
    }

    @Override
    public void execute(Entity entity, FormulaContext context) {
        if (!(entity instanceof LivingEntity living)) return;
        double duration = this.durationTicks.evaluate(context);
        double amplifier = this.amplifier.evaluate(context);
        if (!Double.isFinite(duration) || !Double.isFinite(amplifier) || duration < 1.0D || duration > Integer.MAX_VALUE || amplifier < 0.0D || amplifier > 255.0D)
            return;
        living.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(this.effect), (int) Math.round(duration), (int) Math.round(amplifier)));
    }

    @Override
    public MapCodec<ApplyEffectEntityAction> codec() {
        return CODEC;
    }
}
