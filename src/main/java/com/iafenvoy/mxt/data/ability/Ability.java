package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.data.ability.target.SelfTargetSelector;
import com.iafenvoy.mxt.data.ability.type.TriggeredAbilityType;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.data.storage.DataStorageDeclaration;
import com.iafenvoy.mxt.data.trigger.Trigger;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A named ability; behaviour is selected by the built-in type identifier. The declared state list keeps the
 * datapack key {@code components} for compatibility even though the values are now held by the ability
 * attachment. {@code element_affinity_mode} says how a cultivator of several matching roots is read: the average
 * of them (the default, and the reading every ability has always had) or the best.
 */
public record Ability(Component name, Component description, AbilityType type, List<Cost> costs,
                      NumberProvider castTime, NumberProvider cooldown,
                      Optional<IconReference> icon,
                      List<DataStorage> storages, List<AttributeEntry> modifiers,
                      DamageCondition damageCondition, EntityCondition condition, EntityAction entityAction,
                      TargetSelector targetSelector, BiEntityCondition targetCondition, BiEntityAction biEntityAction,
                      List<Either<Holder<Element>, TagKey<Element>>> elementAffinity,
                      AffinityMode elementAffinityMode) implements DataStorageDeclaration, NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.ABILITY.identifier());
    public static final Codec<Holder<Ability>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ABILITY);
    public static final Codec<Ability> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(Ability::name),
            ContextNameCodec.description(CATEGORY).forGetter(Ability::description),
            AbilityType.CODEC.forGetter(Ability::type),
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(Ability::costs),
            // Seventeen components; one pair keeps the group at sixteen.
            MiscCodecs.pair(
                    NumberProvider.CODEC.optionalFieldOf("cast_time", new Constant(0.0D)),
                    NumberProvider.CODEC.optionalFieldOf("cooldown", new Constant(0.0D)))
                    .forGetter(ability -> Pair.of(ability.castTime(), ability.cooldown())),
            IconReference.CODEC.optionalFieldOf("icon").forGetter(Ability::icon),
            DataStorage.CODEC.listOf().optionalFieldOf("components", List.of()).forGetter(Ability::storages),
            AttributeEntry.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(Ability::modifiers),
            DamageCondition.optionalCodec("damage_condition").forGetter(Ability::damageCondition),
            EntityCondition.optionalCodec("condition").forGetter(Ability::condition),
            EntityAction.optionalCodec("entity_action").forGetter(Ability::entityAction),
            TargetSelector.CODEC.optionalFieldOf("target_selector", SelfTargetSelector.INSTANCE).forGetter(Ability::targetSelector),
            BiEntityCondition.optionalCodec("target_condition").forGetter(Ability::targetCondition),
            BiEntityAction.optionalCodec("bi_entity_action").forGetter(Ability::biEntityAction),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("element_affinity", List.of()).forGetter(Ability::elementAffinity),
            AffinityMode.CODEC.optionalFieldOf("element_affinity_mode", AffinityMode.AVERAGE).forGetter(Ability::elementAffinityMode)
    ).apply(i, (name, description, type, costs, timings, icon, storages, modifiers, damageCondition, condition,
                entityAction, targetSelector, targetCondition, biEntityAction, elementAffinity, elementAffinityMode) ->
            new Ability(name, description, type, costs, timings.getFirst(), timings.getSecond(), icon, storages,
                    modifiers, damageCondition, condition, entityAction, targetSelector, targetCondition,
                    biEntityAction, elementAffinity, elementAffinityMode)));

    // How the element_ability_modifier of several matching roots becomes the one element_modifier a cast exposes.
    public enum AffinityMode {
        AVERAGE, MAX;
        public static final Codec<AffinityMode> CODEC = Codec.STRING.xmap(
                value -> valueOf(value.toUpperCase(Locale.ROOT)),
                value -> value.name().toLowerCase(Locale.ROOT));
    }

    public List<Trigger> triggers() {
        return this.type instanceof TriggeredAbilityType triggered ? triggered.triggers() : List.of();
    }

    // Actions and composite ability types may refer to abilities through holders: keep diagnostic output shallow
    // so logging a cyclic datapack definition cannot recurse through its holder.
    @Override
    public @NonNull String toString() {
        return "Ability[type=" + this.type.getClass().getSimpleName() + ", costs=" + this.costs.size()
                + ", storages=" + this.storages.size() + ", modifiers=" + this.modifiers.size()
                + ", elementAffinity=" + this.elementAffinity.size() + "]";
    }
}
