package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.HotbarIcon;
import com.iafenvoy.mxt.data.ability.target.SelfTargetSelector;
import com.iafenvoy.mxt.data.ability.type.TriggeredAbilityType;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.trigger.Trigger;
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
                      Optional<HotbarIcon> icon,
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
            HotbarIcon.CODEC.optionalFieldOf("icon").forGetter(Ability::icon),
            AbilityComponent.CODEC.listOf().optionalFieldOf("components", List.of()).forGetter(Ability::components),
            AttributeEntry.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(Ability::modifiers),
            DamageCondition.optionalCodec("damage_condition").forGetter(Ability::damageCondition),
            EntityCondition.optionalCodec("condition").forGetter(Ability::condition),
            EntityAction.optionalCodec("entity_action").forGetter(Ability::entityAction),
            TargetSelector.CODEC.optionalFieldOf("target_selector", SelfTargetSelector.INSTANCE).forGetter(Ability::targetSelector),
            BiEntityCondition.optionalCodec("target_condition").forGetter(Ability::targetCondition),
            BiEntityAction.optionalCodec("bi_entity_action").forGetter(Ability::biEntityAction),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("element_affinity", List.of()).forGetter(Ability::elementAffinity)
    ).apply(i, Ability::new));

    public List<Trigger> triggers() {
        return this.type instanceof TriggeredAbilityType triggered ? triggered.triggers() : List.of();
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
