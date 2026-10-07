package com.iafenvoy.mxt.data.talisman.type;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.ability.ActionCarrier;
import com.iafenvoy.mxt.data.ability.TargetSelector;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.talisman.TalismanType;
import com.iafenvoy.mxt.data.talisman.TalismanUse;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The targets are whatever the {@code target_selector} finds, filtered by {@code target_condition} - the
 * same selector and the same reading of a target as an ability uses, so a range that covers nobody is still a use
 * that happened (unlike a crosshair with nobody under it).
 */
public record AreaTalismanType(int maxUse, TargetSelector targetSelector, BiEntityCondition targetCondition,
                               EntityAction entityAction, BiEntityAction biEntityAction) implements TalismanType {
    public static final MapCodec<AreaTalismanType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("max_use", 0).forGetter(AreaTalismanType::maxUse),
            TargetSelector.CODEC.fieldOf("target_selector").forGetter(AreaTalismanType::targetSelector),
            BiEntityCondition.optionalCodec("target_condition").forGetter(AreaTalismanType::targetCondition),
            EntityAction.optionalCodec("entity_action").forGetter(AreaTalismanType::entityAction),
            BiEntityAction.optionalCodec("bi_entity_action").forGetter(AreaTalismanType::biEntityAction)
    ).apply(i, AreaTalismanType::new));

    @Override
    public Plan plan(TalismanUse use) {
        return Plan.of(this.targetSelector.select(use.user(), use.formula(), use.origin())
                .filter(target -> this.targetCondition.test(use.user(), target,
                        ActionCarrier.targetContext(use.user(), target, use.formula())))
                .toList());
    }

    @Override
    public void apply(TalismanUse use, Plan plan) {
        TalismanType.runActions(this.entityAction, this.biEntityAction, use, plan.targets());
    }

    @Override
    public MapCodec<AreaTalismanType> codec() {
        return CODEC;
    }
}
