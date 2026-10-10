package com.iafenvoy.mxt.data.talisman.type;

import com.iafenvoy.mxt.data.ability.ActionCarrier;
import com.iafenvoy.mxt.data.ability.target.RayTargetSelector;
import com.iafenvoy.mxt.data.ability.target.TargetOrder;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.talisman.TalismanType;
import com.iafenvoy.mxt.data.talisman.TalismanUse;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * The targets are the entities along the user's line of sight, read by the same
 * {@link RayTargetSelector} a ray ability uses. Nobody under the crosshair is not "an empty area": the use cannot
 * happen, and {@link Plan#impossible()} says so before the definition's own condition is asked and before anything
 * is paid.
 */
public record CrosshairTalismanType(int maxUse, NumberProvider length, NumberProvider radius, int limit,
                                    TargetOrder order, BiEntityCondition targetCondition,
                                    EntityAction entityAction, BiEntityAction biEntityAction) implements TalismanType {
    public static final MapCodec<CrosshairTalismanType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("max_use", 0).forGetter(CrosshairTalismanType::maxUse),
            NumberProvider.CODEC.fieldOf("length").forGetter(CrosshairTalismanType::length),
            NumberProvider.CODEC.optionalFieldOf("radius", new Constant(0.5D)).forGetter(CrosshairTalismanType::radius),
            Codec.INT.optionalFieldOf("limit", 0).forGetter(CrosshairTalismanType::limit),
            TargetOrder.CODEC.optionalFieldOf("order", TargetOrder.NEAREST).forGetter(CrosshairTalismanType::order),
            BiEntityCondition.optionalCodec("target_condition").forGetter(CrosshairTalismanType::targetCondition),
            EntityAction.optionalCodec("entity_action").forGetter(CrosshairTalismanType::entityAction),
            BiEntityAction.optionalCodec("bi_entity_action").forGetter(CrosshairTalismanType::biEntityAction)
    ).apply(i, CrosshairTalismanType::new));

    @Override
    public Plan plan(TalismanUse use) {
        // The actor is never its own crosshair target, which is why the selector excludes it outright.
        List<Entity> targets = new RayTargetSelector(this.length, this.radius, false, this.limit, this.order)
                .select(use.user(), use.formula(), use.origin())
                .filter(target -> this.targetCondition.test(use.user(), target,
                        ActionCarrier.targetContext(use.user(), target, use.formula())))
                .toList();
        return targets.isEmpty() ? Plan.impossible() : Plan.of(targets);
    }

    @Override
    public void apply(TalismanUse use, Plan plan) {
        TalismanType.runActions(this.entityAction, this.biEntityAction, use, plan.targets());
    }

    @Override
    public MapCodec<CrosshairTalismanType> codec() {
        return CODEC;
    }
}
