package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.registry.BehaviorReferences;
import com.iafenvoy.mxt.registry.BehaviorReferences.Reference;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationCondition;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Multi-stage heavenly tribulation definition.
 */
public record Tribulation(List<CultivationCondition> triggerConditions, List<Phase> phases,
                          NumberProvider difficultyScale, Optional<Identifier> successBehavior,
                          Optional<Identifier> failureBehavior) {
    public static final Codec<Tribulation> DIRECT_CODEC = RecordCodecBuilder.<Tribulation>create(instance -> instance.group(
            AutoIgnoreListCodec.create(MxtTypeRegistries.CULTIVATION_CONDITION.byNameCodec()).optionalFieldOf("trigger_conditions", List.of()).forGetter(Tribulation::triggerConditions),
            Phase.CODEC.listOf().fieldOf("phases").forGetter(Tribulation::phases),
            NumberProvider.CODEC.optionalFieldOf("difficulty_scale", new Constant(1.0D)).forGetter(Tribulation::difficultyScale),
            Identifier.CODEC.optionalFieldOf("success_behavior").forGetter(Tribulation::successBehavior),
            Identifier.CODEC.optionalFieldOf("failure_behavior").forGetter(Tribulation::failureBehavior)
    ).apply(instance, Tribulation::new)).validate(Tribulation::validate);
    public static final Codec<Holder<Tribulation>> CODEC = RegistryFixedCodec.create(MxtRegistryKeys.TRIBULATION);

    private static DataResult<Tribulation> validate(Tribulation value) {
        if (value.phases.isEmpty())
            return DataResult.error(() -> "Tribulation requires at least one phase");
        List<Reference> references = new ArrayList<>();
        references.add(new Reference("success_behavior", value.successBehavior));
        references.add(new Reference("failure_behavior", value.failureBehavior));
        for (int index = 0; index < value.phases.size(); index++) {
            Phase phase = value.phases.get(index);
            references.add(new Reference("phases[" + index + "].start_behavior", phase.startBehavior));
            references.add(new Reference("phases[" + index + "].end_behavior", phase.endBehavior));
        }
        return BehaviorReferences.validate(value, MxtTypeRegistries.TRIBULATION_STAGE_BEHAVIOR, references.toArray(Reference[]::new));
    }

    public record Phase(NumberProvider duration, Optional<Identifier> startBehavior, Optional<Identifier> endBehavior) {
        public static final Codec<Phase> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                NumberProvider.CODEC.fieldOf("duration").forGetter(Phase::duration), Identifier.CODEC.optionalFieldOf("start_behavior").forGetter(Phase::startBehavior), Identifier.CODEC.optionalFieldOf("end_behavior").forGetter(Phase::endBehavior)
        ).apply(instance, Phase::new));
    }
}
