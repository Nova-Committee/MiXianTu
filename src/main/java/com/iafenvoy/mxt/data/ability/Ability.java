package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.ability.type.TriggeredAbilityType;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.ability.target.SelfTargetSelector;
import com.iafenvoy.mxt.data.action.builtin.bientity.meta.BiEntityNoOpAction;
import com.iafenvoy.mxt.data.action.builtin.entity.meta.NoOpAction;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.bientity.meta.AlwaysTrueBiEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.damage.AlwaysTrueDamageCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.meta.AlwaysTrueEntityCondition;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
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
public record Ability(AbilityType type, List<Cost> costs, NumberProvider castTime, NumberProvider cooldown,
                      List<AbilityComponent> components, List<AttributeEntry> modifiers,
                      DamageCondition damageCondition, EntityCondition condition, EntityAction entityAction,
                      TargetSelector targetSelector, BiEntityCondition targetCondition, BiEntityAction biEntityAction,
                      List<Either<Holder<Element>, TagKey<Element>>> elementAffinity) {
    public static final Codec<Holder<Ability>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ABILITY);
    public static final Codec<Ability> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            AbilityType.CODEC.forGetter(Ability::type),
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(Ability::costs),
            NumberProvider.CODEC.optionalFieldOf("cast_time", new Constant(0.0D)).forGetter(Ability::castTime),
            NumberProvider.CODEC.optionalFieldOf("cooldown", new Constant(0.0D)).forGetter(Ability::cooldown),
            AbilityComponent.CODEC.listOf().optionalFieldOf("components", List.of()).forGetter(Ability::components),
            AttributeEntry.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(Ability::modifiers),
            DamageCondition.CODEC.optionalFieldOf("damage_condition", AlwaysTrueDamageCondition.INSTANCE).forGetter(Ability::damageCondition),
            EntityCondition.CODEC.optionalFieldOf("condition", AlwaysTrueEntityCondition.INSTANCE).forGetter(Ability::condition),
            EntityAction.CODEC.optionalFieldOf("entity_action", NoOpAction.INSTANCE).forGetter(Ability::entityAction),
            TargetSelector.CODEC.optionalFieldOf("target_selector", SelfTargetSelector.INSTANCE).forGetter(Ability::targetSelector),
            BiEntityCondition.CODEC.optionalFieldOf("target_condition", AlwaysTrueBiEntityCondition.INSTANCE).forGetter(Ability::targetCondition),
            BiEntityAction.CODEC.optionalFieldOf("bi_entity_action", BiEntityNoOpAction.INSTANCE).forGetter(Ability::biEntityAction),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("element_affinity", List.of()).forGetter(Ability::elementAffinity)
    ).apply(i, Ability::new));

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
