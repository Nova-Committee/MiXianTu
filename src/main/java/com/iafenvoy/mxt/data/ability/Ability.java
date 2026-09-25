package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.data.ability.target.SelfTargetSelector;
import com.iafenvoy.mxt.data.ability.type.FlightControlAbilityType;
import com.iafenvoy.mxt.data.ability.type.MountAbilityType;
import com.iafenvoy.mxt.data.ability.type.TriggeredAbilityType;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.action.NoOpAction;
import com.iafenvoy.mxt.data.condition.AlwaysTrueCondition;
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
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
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
 *
 * <p>Every ability is a registry entry, named by its own id: its registry holder is what the grant ledger, the
 * cooldowns and the wheel hand around. {@code hidden} keeps an entry out of the wheel and the HUD without
 * stopping it, and {@code item_action} reaches the host stack, which is what a carried ability's failure
 * compensation acts on.
 */
public record Ability(Component name, Component description, AbilityType type, List<Cost> costs,
                      NumberProvider castTime, NumberProvider cooldown,
                      Optional<IconReference> icon,
                      List<DataStorage> storages, List<AttributeEntry> modifiers,
                      DamageCondition damageCondition, EntityCondition condition, EntityAction entityAction,
                      TargetSelector targetSelector, BiEntityCondition targetCondition, BiEntityAction biEntityAction,
                      List<Either<Holder<Element>, TagKey<Element>>> elementAffinity,
                      AffinityMode elementAffinityMode, boolean hidden, ItemAction itemAction)
        implements DataStorageDeclaration, NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.ABILITY.identifier());
    public static final Codec<Holder<Ability>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ABILITY);
    // Nineteen fields in sixteen slots: three pairs keep the group within RecordCodecBuilder's limit, and the
    // JSON keys are unchanged by it.
    public static final MapCodec<Ability> MAP_CODEC = RecordCodecBuilder.<Ability>mapCodec(i -> i.group(
                    ContextNameCodec.name(CATEGORY).forGetter(Ability::name),
                    ContextNameCodec.description(CATEGORY).forGetter(Ability::description),
                    AbilityType.CODEC.forGetter(Ability::type),
                    Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(Ability::costs),
                    MiscCodecs.pair(
                                    NumberProvider.CODEC.optionalFieldOf("cast_time", new Constant(0.0D)),
                                    NumberProvider.CODEC.optionalFieldOf("cooldown", new Constant(0.0D)))
                            .forGetter(ability -> Pair.of(ability.castTime(), ability.cooldown())),
                    MiscCodecs.pair(
                                    IconReference.CODEC.optionalFieldOf("icon"),
                                    Codec.BOOL.optionalFieldOf("hidden", false))
                            .forGetter(ability -> Pair.of(ability.icon(), ability.hidden())),
                    DataStorage.CODEC.listOf().optionalFieldOf("components", List.of()).forGetter(Ability::storages),
                    AttributeEntry.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(Ability::modifiers),
                    DamageCondition.optionalCodec("damage_condition").forGetter(Ability::damageCondition),
                    EntityCondition.optionalCodec("condition").forGetter(Ability::condition),
                    EntityAction.optionalCodec("entity_action").forGetter(Ability::entityAction),
                    TargetSelector.CODEC.optionalFieldOf("target_selector", SelfTargetSelector.INSTANCE).forGetter(Ability::targetSelector),
                    BiEntityCondition.optionalCodec("target_condition").forGetter(Ability::targetCondition),
                    BiEntityAction.optionalCodec("bi_entity_action").forGetter(Ability::biEntityAction),
                    MiscCodecs.pair(
                                    RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("element_affinity", List.of()),
                                    AffinityMode.CODEC.optionalFieldOf("element_affinity_mode", AffinityMode.AVERAGE))
                            .forGetter(ability -> Pair.of(ability.elementAffinity(), ability.elementAffinityMode())),
                    ItemAction.optionalCodec("item_action").forGetter(Ability::itemAction)
            ).apply(i, (name, description, type, costs, timings, iconHidden, storages, modifiers, damageCondition,
                        condition, entityAction, targetSelector, targetCondition, biEntityAction, affinity, itemAction) ->
                    new Ability(name, description, type, costs, timings.getFirst(), timings.getSecond(), iconHidden.getFirst(),
                            storages, modifiers, damageCondition, condition, entityAction, targetSelector, targetCondition,
                            biEntityAction, affinity.getFirst(), affinity.getSecond(), iconHidden.getSecond(), itemAction)))
            .validate(Ability::validate);
    public static final Codec<Ability> DIRECT_CODEC = MAP_CODEC.codec();

    // A vehicle describes a ride and a flying skill is a switch: neither runs a cast, so a field that only a cast
    // would read is a pack mistake rather than a value to keep. A mount reads nothing but its own fields, its fuel
    // and its condition; the skill adds the gate's cooldown, and a declared cooldown component is what that reads.
    private static final List<String> CAST_ONLY = List.of("cast_time", "entity_action", "target_selector",
            "target_condition", "bi_entity_action", "modifiers", "damage_condition", "element_affinity",
            "element_affinity_mode", "item_action");

    private static DataResult<Ability> validate(Ability ability) {
        List<String> inert = switch (ability.type()) {
            case MountAbilityType ignored -> List.of("cooldown", "components", "cast_time", "entity_action",
                    "target_selector", "target_condition", "bi_entity_action", "modifiers", "damage_condition",
                    "element_affinity", "element_affinity_mode", "item_action");
            case FlightControlAbilityType ignored -> CAST_ONLY;
            default -> List.of();
        };
        List<String> written = inert.stream().filter(key -> isWritten(key, ability)).toList();
        if (written.isEmpty()) return DataResult.success(ability);
        String name = ability.type() instanceof MountAbilityType ? "mxt:mount" : "mxt:flight_control";
        return DataResult.error(() -> name + " is never activated, so it cannot declare " + String.join(", ", written));
    }

    private static boolean isWritten(String key, Ability ability) {
        return switch (key) {
            case "cast_time" -> !isZero(ability.castTime());
            case "cooldown" -> !isZero(ability.cooldown());
            case "entity_action" -> ability.entityAction() != NoOpAction.INSTANCE;
            case "bi_entity_action" -> ability.biEntityAction() != NoOpAction.INSTANCE;
            case "item_action" -> ability.itemAction() != NoOpAction.INSTANCE;
            case "target_selector" -> ability.targetSelector() != SelfTargetSelector.INSTANCE;
            case "target_condition" -> ability.targetCondition() != AlwaysTrueCondition.INSTANCE;
            case "damage_condition" -> ability.damageCondition() != AlwaysTrueCondition.INSTANCE;
            case "modifiers" -> !ability.modifiers().isEmpty();
            case "components" -> !ability.storages().isEmpty();
            case "element_affinity" -> !ability.elementAffinity().isEmpty();
            case "element_affinity_mode" -> ability.elementAffinityMode() != AffinityMode.AVERAGE;
            // The two types this guards against both exit above, so an unknown key never reaches here.
            default -> false;
        };
    }

    private static boolean isZero(NumberProvider provider) {
        return provider instanceof Constant(double value) && value == 0.0D;
    }

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
