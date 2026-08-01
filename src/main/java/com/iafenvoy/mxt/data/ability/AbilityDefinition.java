package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.data.ability.AbilityType.Triggered;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.builtin.BiEntityNoOpAction;
import com.iafenvoy.mxt.data.action.builtin.NoOpEntityAction;
import com.iafenvoy.mxt.data.common.AttributeModifierDefinition;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.AlwaysTrueBiEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.AlwaysTrueDamageCondition;
import com.iafenvoy.mxt.data.condition.builtin.AlwaysTrueEntityCondition;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * A named ability. Behaviour is selected by the built-in type identifier.
 */
public record AbilityDefinition(Identifier type, AbilityType typedType,
                                List<ResourceCost> costs, NumberProvider castTime, NumberProvider cooldown,
                                List<AbilityComponent> components, List<AttributeModifierDefinition> modifiers,
                                DamageCondition damageCondition,
                                EntityCondition condition, EntityAction entityAction, BiEntityCondition targetCondition,
                                BiEntityAction biEntityAction, List<Identifier> elementAffinity) {
    public AbilityDefinition(Identifier type, List<ResourceCost> costs, NumberProvider castTime, NumberProvider cooldown) {
        this(type, AbilityType.forIdentifier(type), costs, castTime, cooldown, List.of(), List.of(), AlwaysTrueDamageCondition.INSTANCE, AlwaysTrueEntityCondition.INSTANCE, NoOpEntityAction.INSTANCE, AlwaysTrueBiEntityCondition.INSTANCE, BiEntityNoOpAction.INSTANCE, List.of());
    }

    public AbilityDefinition(Identifier type, List<ResourceCost> costs, NumberProvider castTime, NumberProvider cooldown,
                             List<AbilityComponent> components, EntityCondition condition, EntityAction entityAction) {
        this(type, AbilityType.forIdentifier(type), costs, castTime, cooldown, components, List.of(), AlwaysTrueDamageCondition.INSTANCE, condition, entityAction, AlwaysTrueBiEntityCondition.INSTANCE, BiEntityNoOpAction.INSTANCE, List.of());
    }

    public AbilityDefinition(Identifier type, AbilityType typedType, List<ResourceCost> costs, NumberProvider castTime, NumberProvider cooldown,
                             List<AbilityComponent> components, DamageCondition damageCondition, EntityCondition condition, EntityAction entityAction) {
        this(type, typedType, costs, castTime, cooldown, components, List.of(), damageCondition, condition, entityAction, AlwaysTrueBiEntityCondition.INSTANCE, BiEntityNoOpAction.INSTANCE, List.of());
    }

    public Optional<AbilityTrigger> trigger() {
        return this.typedType instanceof Triggered triggered ? Optional.of(triggered.trigger()) : Optional.empty();
    }

    public static final Codec<AbilityDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AbilityType.MAP_CODEC.forGetter(AbilityDefinition::typedType),
            ResourceCost.CODEC.listOf().optionalFieldOf("costs", List.of()).forGetter(AbilityDefinition::costs),
            NumberProvider.CODEC.optionalFieldOf("cast_time", new Constant(0.0D)).forGetter(AbilityDefinition::castTime),
            NumberProvider.CODEC.optionalFieldOf("cooldown", new Constant(0.0D)).forGetter(AbilityDefinition::cooldown),
            AbilityComponent.CODEC.listOf().optionalFieldOf("components", List.of()).forGetter(AbilityDefinition::components),
            AttributeModifierDefinition.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(AbilityDefinition::modifiers),
            DamageCondition.CODEC.optionalFieldOf("damage_condition", AlwaysTrueDamageCondition.INSTANCE).forGetter(AbilityDefinition::damageCondition),
            EntityCondition.CODEC.optionalFieldOf("condition", AlwaysTrueEntityCondition.INSTANCE).forGetter(AbilityDefinition::condition),
            EntityAction.CODEC.optionalFieldOf("entity_action", NoOpEntityAction.INSTANCE).forGetter(AbilityDefinition::entityAction),
            BiEntityCondition.CODEC.optionalFieldOf("target_condition", AlwaysTrueBiEntityCondition.INSTANCE).forGetter(AbilityDefinition::targetCondition),
            BiEntityAction.CODEC.optionalFieldOf("bi_entity_action", BiEntityNoOpAction.INSTANCE).forGetter(AbilityDefinition::biEntityAction),
            Identifier.CODEC.listOf().optionalFieldOf("element_affinity", List.of()).forGetter(AbilityDefinition::elementAffinity)
    ).apply(instance, (typedType, costs, castTime, cooldown, components, modifiers, damageCondition, condition, entityAction, targetCondition, biEntityAction, elementAffinity) ->
            new AbilityDefinition(typedType.id(), typedType, costs, castTime, cooldown, components, modifiers, damageCondition, condition, entityAction, targetCondition, biEntityAction, elementAffinity)));
}
