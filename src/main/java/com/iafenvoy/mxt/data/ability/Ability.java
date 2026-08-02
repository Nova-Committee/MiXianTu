package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.data.ability.AbilityType.Triggered;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.builtin.BiEntityNoOpAction;
import com.iafenvoy.mxt.data.action.builtin.NoOpEntityAction;
import com.iafenvoy.mxt.data.AttributeModifier;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.AlwaysTrueBiEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.AlwaysTrueDamageCondition;
import com.iafenvoy.mxt.data.condition.builtin.AlwaysTrueEntityCondition;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;
import com.mojang.datafixers.util.Either;

import java.util.List;
import java.util.Optional;

/**
 * A named ability. Behaviour is selected by the built-in type identifier.
 */
public record Ability(AbilityType type, List<ResourceCost> costs, NumberProvider castTime, NumberProvider cooldown,
                      List<AbilityComponent> components, List<AttributeModifier> modifiers,
                      DamageCondition damageCondition, EntityCondition condition, EntityAction entityAction,
                      BiEntityCondition targetCondition, BiEntityAction biEntityAction,
                      List<Either<Holder<Element>, TagKey<Element>>> elementAffinity) {
    public static final Codec<Ability> DIRECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AbilityType.MAP_CODEC.forGetter(Ability::type),
            ResourceCost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(Ability::costs),
            NumberProvider.CODEC.optionalFieldOf("cast_time", new Constant(0.0D)).forGetter(Ability::castTime),
            NumberProvider.CODEC.optionalFieldOf("cooldown", new Constant(0.0D)).forGetter(Ability::cooldown),
            AbilityComponent.CODEC.listOf().optionalFieldOf("components", List.of()).forGetter(Ability::components),
            AttributeModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(Ability::modifiers),
            DamageCondition.CODEC.optionalFieldOf("damage_condition", AlwaysTrueDamageCondition.INSTANCE).forGetter(Ability::damageCondition),
            EntityCondition.CODEC.optionalFieldOf("condition", AlwaysTrueEntityCondition.INSTANCE).forGetter(Ability::condition),
            EntityAction.CODEC.optionalFieldOf("entity_action", NoOpEntityAction.INSTANCE).forGetter(Ability::entityAction),
            BiEntityCondition.CODEC.optionalFieldOf("target_condition", AlwaysTrueBiEntityCondition.INSTANCE).forGetter(Ability::targetCondition),
            BiEntityAction.CODEC.optionalFieldOf("bi_entity_action", BiEntityNoOpAction.INSTANCE).forGetter(Ability::biEntityAction),
            RegistryCodecs.holderOrTagList(MxtRegistryKeys.ELEMENT).optionalFieldOf("element_affinity", List.of()).forGetter(Ability::elementAffinity)
    ).apply(instance, Ability::new));
    public static final Codec<Holder<Ability>> CODEC = RegistryFixedCodec.create(MxtRegistryKeys.ABILITY);

    public Optional<AbilityTrigger> trigger() {
        return this.type instanceof Triggered triggered ? Optional.of(triggered.trigger()) : Optional.empty();
    }
}
