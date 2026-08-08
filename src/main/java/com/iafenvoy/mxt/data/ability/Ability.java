package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.data.AttributeModifier;
import com.iafenvoy.mxt.data.ability.type.TriggeredAbilityType;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.builtin.bientity.meta.BiEntityNoOpAction;
import com.iafenvoy.mxt.data.action.builtin.entity.meta.NoOpAction;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.bientity.meta.AlwaysTrueBiEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.damage.AlwaysTrueDamageCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.meta.AlwaysTrueEntityCondition;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.NonNull;

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
    public static final Codec<Holder<Ability>> CODEC = RegistryFixedCodec.create(MxtRegistryKeys.ABILITY);
    public static final Codec<Ability> DIRECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AbilityType.CODEC.fieldOf("ability").forGetter(Ability::type),
            ResourceCost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(Ability::costs),
            NumberProvider.CODEC.optionalFieldOf("cast_time", new Constant(0.0D)).forGetter(Ability::castTime),
            NumberProvider.CODEC.optionalFieldOf("cooldown", new Constant(0.0D)).forGetter(Ability::cooldown),
            AbilityComponent.CODEC.listOf().optionalFieldOf("components", List.of()).forGetter(Ability::components),
            AttributeModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(Ability::modifiers),
            DamageCondition.CODEC.optionalFieldOf("damage_condition", AlwaysTrueDamageCondition.INSTANCE).forGetter(Ability::damageCondition),
            EntityCondition.CODEC.optionalFieldOf("condition", AlwaysTrueEntityCondition.INSTANCE).forGetter(Ability::condition),
            EntityAction.CODEC.optionalFieldOf("entity_action", NoOpAction.INSTANCE).forGetter(Ability::entityAction),
            BiEntityCondition.CODEC.optionalFieldOf("target_condition", AlwaysTrueBiEntityCondition.INSTANCE).forGetter(Ability::targetCondition),
            BiEntityAction.CODEC.optionalFieldOf("bi_entity_action", BiEntityNoOpAction.INSTANCE).forGetter(Ability::biEntityAction),
            RegistryCodecs.holderOrTagList(MxtRegistryKeys.ELEMENT).optionalFieldOf("element_affinity", List.of()).forGetter(Ability::elementAffinity)
    ).apply(instance, Ability::new));

    public Optional<AbilityTrigger> trigger() {
        return this.type instanceof TriggeredAbilityType triggered ? Optional.of(triggered.trigger()) : Optional.empty();
    }

    /**
     * Actions and composite ability types may refer to abilities through holders. Keep diagnostic
     * output shallow so logging a cyclic datapack definition cannot recurse through its holder.
     */
    @Override
    public @NonNull String toString() {
        return "Ability[type=" + this.type.getClass().getSimpleName() + ", costs=" + this.costs.size()
                + ", components=" + this.components.size() + ", modifiers=" + this.modifiers.size()
                + ", elementAffinity=" + this.elementAffinity.size() + "]";
    }
}
