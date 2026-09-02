package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;

/**
 * Multi-stage heavenly tribulation definition.
 */
public record Tribulation(EntityCondition triggerCondition, List<Phase> phases,
                          NumberProvider difficultyScale, EntityAction successAction,
                          EntityAction failAction) {
    public static final Codec<Holder<Tribulation>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.TRIBULATION);
    public static final Codec<Tribulation> DIRECT_CODEC = RecordCodecBuilder.<Tribulation>create(i -> i.group(
            EntityCondition.optionalCodec("trigger_condition").forGetter(Tribulation::triggerCondition),
            Phase.CODEC.listOf().fieldOf("phases").forGetter(Tribulation::phases),
            NumberProvider.CODEC.optionalFieldOf("difficulty_scale", new Constant(1.0D)).forGetter(Tribulation::difficultyScale),
            EntityAction.optionalCodec("success_action").forGetter(Tribulation::successAction),
            EntityAction.optionalCodec("fail_action").forGetter(Tribulation::failAction)
    ).apply(i, Tribulation::new)).validate(Tribulation::validate);

    private static DataResult<Tribulation> validate(Tribulation value) {
        if (value.phases.isEmpty())
            return DataResult.error(() -> "Tribulation requires at least one phase");
        return DataResult.success(value);
    }

    public record Phase(NumberProvider duration, EntityAction startAction, EntityAction endAction) {
        public static final Codec<Phase> CODEC = RecordCodecBuilder.create(i -> i.group(
                NumberProvider.CODEC.fieldOf("duration").forGetter(Phase::duration),
                EntityAction.optionalCodec("start_action").forGetter(Phase::startAction),
                EntityAction.optionalCodec("end_action").forGetter(Phase::endAction)
        ).apply(i, Phase::new));
    }
}
