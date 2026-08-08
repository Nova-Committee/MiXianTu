package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.builtin.entity.meta.NoOpAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.meta.AlwaysTrueEntityCondition;
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
    public static final Codec<Holder<Tribulation>> CODEC = RegistryFixedCodec.create(MxtRegistryKeys.TRIBULATION);
    public static final Codec<Tribulation> DIRECT_CODEC = RecordCodecBuilder.<Tribulation>create(instance -> instance.group(
            EntityCondition.CODEC.optionalFieldOf("trigger_condition", AlwaysTrueEntityCondition.INSTANCE).forGetter(Tribulation::triggerCondition),
            Phase.CODEC.listOf().fieldOf("phases").forGetter(Tribulation::phases),
            NumberProvider.CODEC.optionalFieldOf("difficulty_scale", new Constant(1.0D)).forGetter(Tribulation::difficultyScale),
            EntityAction.CODEC.optionalFieldOf("success_action", NoOpAction.INSTANCE).forGetter(Tribulation::successAction),
            EntityAction.CODEC.optionalFieldOf("fail_action", NoOpAction.INSTANCE).forGetter(Tribulation::failAction)
    ).apply(instance, Tribulation::new)).validate(Tribulation::validate);

    private static DataResult<Tribulation> validate(Tribulation value) {
        if (value.phases.isEmpty())
            return DataResult.error(() -> "Tribulation requires at least one phase");
        return DataResult.success(value);
    }

    public record Phase(NumberProvider duration, EntityAction startAction, EntityAction endAction) {
        public static final Codec<Phase> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                NumberProvider.CODEC.fieldOf("duration").forGetter(Phase::duration),
                EntityAction.CODEC.optionalFieldOf("start_action", NoOpAction.INSTANCE).forGetter(Phase::startAction),
                EntityAction.CODEC.optionalFieldOf("end_action", NoOpAction.INSTANCE).forGetter(Phase::endAction)
        ).apply(instance, Phase::new));
    }
}
