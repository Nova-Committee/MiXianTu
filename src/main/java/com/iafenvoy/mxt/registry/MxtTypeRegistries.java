package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.ability.AbilityComponent;
import com.iafenvoy.mxt.data.ability.AbilityTrigger;
import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.condition.*;
import com.iafenvoy.mxt.data.curse.CurseType;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderer;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarVisibility;
import com.iafenvoy.mxt.runtime.behavior.DomainBehavior;
import com.iafenvoy.mxt.runtime.creature.CreatureSpawnCondition;
import com.iafenvoy.mxt.runtime.cultivation.CultivationCondition;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.ValueModifier;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.NewRegistryEvent;

/**
 * Java-owned registries of codecs. Datapacks may select entries but can never extend these registries.
 */
public final class MxtTypeRegistries {
    public static final ResourceKey<Registry<MapCodec<? extends AbilityType>>> ABILITY_TYPE_KEY = key("ability_type");
    public static final DefaultedRegistry<MapCodec<? extends AbilityType>> ABILITY_TYPE = new DefaultedMappedRegistry<>("empty", ABILITY_TYPE_KEY, Lifecycle.stable(), false);
    public static final ResourceKey<Registry<MapCodec<? extends CurseType>>> CURSE_TYPE_KEY = key("curse_type");
    public static final DefaultedRegistry<MapCodec<? extends CurseType>> CURSE_TYPE = new DefaultedMappedRegistry<>("empty", CURSE_TYPE_KEY, Lifecycle.stable(), false);
    public static final ResourceKey<Registry<MapCodec<? extends AbilityComponent>>> ABILITY_COMPONENT_TYPE_KEY = key("ability_component_type");
    public static final DefaultedRegistry<MapCodec<? extends AbilityComponent>> ABILITY_COMPONENT_TYPE = new DefaultedMappedRegistry<>("empty", ABILITY_COMPONENT_TYPE_KEY, Lifecycle.stable(), false);
    public static final ResourceKey<Registry<MapCodec<? extends AbilityTrigger>>> ABILITY_TRIGGER_TYPE_KEY = key("ability_trigger_type");
    public static final DefaultedRegistry<MapCodec<? extends AbilityTrigger>> ABILITY_TRIGGER_TYPE = new DefaultedMappedRegistry<>("use", ABILITY_TRIGGER_TYPE_KEY, Lifecycle.stable(), false);
    public static final ResourceKey<Registry<MapCodec<? extends NumberProvider>>> NUMBER_PROVIDER_TYPE_KEY = key("number_provider_type");
    public static final DefaultedRegistry<MapCodec<? extends NumberProvider>> NUMBER_PROVIDER_TYPE = new DefaultedMappedRegistry<>("constant", NUMBER_PROVIDER_TYPE_KEY, Lifecycle.stable(), false);
    public static final ResourceKey<Registry<MapCodec<? extends ValueModifier>>> VALUE_MODIFIER_TYPE_KEY = key("value_modifier_type");
    public static final DefaultedRegistry<MapCodec<? extends ValueModifier>> VALUE_MODIFIER_TYPE = new DefaultedMappedRegistry<>("add", VALUE_MODIFIER_TYPE_KEY, Lifecycle.stable(), false);
    public static final ResourceKey<Registry<MapCodec<? extends ResourceValueProvider>>> RESOURCE_VALUE_PROVIDER_TYPE_KEY = key("resource_value_provider_type");
    public static final DefaultedRegistry<MapCodec<? extends ResourceValueProvider>> RESOURCE_VALUE_PROVIDER_TYPE = new DefaultedMappedRegistry<>("current", RESOURCE_VALUE_PROVIDER_TYPE_KEY, Lifecycle.stable(), false);
    public static final ResourceKey<Registry<MapCodec<? extends EntityAction>>> ENTITY_ACTION_TYPE_KEY = key("entity_action_type");
    public static final DefaultedRegistry<MapCodec<? extends EntityAction>> ENTITY_ACTION_TYPE = new DefaultedMappedRegistry<>("no_op", ENTITY_ACTION_TYPE_KEY, Lifecycle.stable(), false);
    public static final ResourceKey<Registry<MapCodec<? extends BiEntityAction>>> BI_ENTITY_ACTION_TYPE_KEY = key("bi_entity_action_type");
    public static final DefaultedRegistry<MapCodec<? extends BiEntityAction>> BI_ENTITY_ACTION_TYPE = new DefaultedMappedRegistry<>("no_op", BI_ENTITY_ACTION_TYPE_KEY, Lifecycle.stable(), false);
    public static final ResourceKey<Registry<MapCodec<? extends BlockAction>>> BLOCK_ACTION_TYPE_KEY = key("block_action_type");
    public static final DefaultedRegistry<MapCodec<? extends BlockAction>> BLOCK_ACTION_TYPE = new DefaultedMappedRegistry<>("no_op", BLOCK_ACTION_TYPE_KEY, Lifecycle.stable(), false);
    public static final ResourceKey<Registry<MapCodec<? extends ItemAction>>> ITEM_ACTION_TYPE_KEY = key("item_action_type");
    public static final DefaultedRegistry<MapCodec<? extends ItemAction>> ITEM_ACTION_TYPE = new DefaultedMappedRegistry<>("no_op", ITEM_ACTION_TYPE_KEY, Lifecycle.stable(), false);
    public static final ResourceKey<Registry<MapCodec<? extends EntityCondition>>> ENTITY_CONDITION_TYPE_KEY = key("entity_condition_type");
    public static final DefaultedRegistry<MapCodec<? extends EntityCondition>> ENTITY_CONDITION_TYPE = new DefaultedMappedRegistry<>("always_true", ENTITY_CONDITION_TYPE_KEY, Lifecycle.stable(), false);
    public static final ResourceKey<Registry<MapCodec<? extends BiEntityCondition>>> BI_ENTITY_CONDITION_TYPE_KEY = key("bi_entity_condition_type");
    public static final DefaultedRegistry<MapCodec<? extends BiEntityCondition>> BI_ENTITY_CONDITION_TYPE = new DefaultedMappedRegistry<>("always_true", BI_ENTITY_CONDITION_TYPE_KEY, Lifecycle.stable(), false);
    public static final ResourceKey<Registry<MapCodec<? extends BlockCondition>>> BLOCK_CONDITION_TYPE_KEY = key("block_condition_type");
    public static final DefaultedRegistry<MapCodec<? extends BlockCondition>> BLOCK_CONDITION_TYPE = new DefaultedMappedRegistry<>("always_true", BLOCK_CONDITION_TYPE_KEY, Lifecycle.stable(), false);
    public static final ResourceKey<Registry<MapCodec<? extends ItemCondition>>> ITEM_CONDITION_TYPE_KEY = key("item_condition_type");
    public static final DefaultedRegistry<MapCodec<? extends ItemCondition>> ITEM_CONDITION_TYPE = new DefaultedMappedRegistry<>("always_true", ITEM_CONDITION_TYPE_KEY, Lifecycle.stable(), false);
    public static final ResourceKey<Registry<MapCodec<? extends DamageCondition>>> DAMAGE_CONDITION_TYPE_KEY = key("damage_condition_type");
    public static final DefaultedRegistry<MapCodec<? extends DamageCondition>> DAMAGE_CONDITION_TYPE = new DefaultedMappedRegistry<>("always_true", DAMAGE_CONDITION_TYPE_KEY, Lifecycle.stable(), false);
    public static final ResourceKey<Registry<MapCodec<? extends ResourceBarRenderer>>> RESOURCE_BAR_RENDERER_TYPE_KEY = key("resource_bar_renderer_type");
    public static final DefaultedRegistry<MapCodec<? extends ResourceBarRenderer>> RESOURCE_BAR_RENDERER_TYPE = new DefaultedMappedRegistry<>("missing", RESOURCE_BAR_RENDERER_TYPE_KEY, Lifecycle.stable(), false);
    public static final ResourceKey<Registry<MapCodec<? extends ResourceBarVisibility>>> RESOURCE_BAR_VISIBILITY_TYPE_KEY = key("resource_bar_visibility_type");
    public static final DefaultedRegistry<MapCodec<? extends ResourceBarVisibility>> RESOURCE_BAR_VISIBILITY_TYPE = new DefaultedMappedRegistry<>("always", RESOURCE_BAR_VISIBILITY_TYPE_KEY, Lifecycle.stable(), false);
    public static final ResourceKey<Registry<DomainBehavior>> FORGING_COMPLETION_BEHAVIOR_KEY = key("forging_completion_behavior");
    public static final DefaultedRegistry<DomainBehavior> FORGING_COMPLETION_BEHAVIOR = behavior("forging_completion_behavior");
    public static final ResourceKey<Registry<DomainBehavior>> FORMATION_LIFECYCLE_BEHAVIOR_KEY = key("formation_lifecycle_behavior");
    public static final DefaultedRegistry<DomainBehavior> FORMATION_LIFECYCLE_BEHAVIOR = behavior("formation_lifecycle_behavior");
    public static final ResourceKey<Registry<DomainBehavior>> TRIBULATION_STAGE_BEHAVIOR_KEY = key("tribulation_stage_behavior");
    public static final DefaultedRegistry<DomainBehavior> TRIBULATION_STAGE_BEHAVIOR = behavior("tribulation_stage_behavior");
    public static final ResourceKey<Registry<DomainBehavior>> CULTIVATION_OUTCOME_BEHAVIOR_KEY = key("cultivation_outcome_behavior");
    public static final DefaultedRegistry<DomainBehavior> CULTIVATION_OUTCOME_BEHAVIOR = behavior("cultivation_outcome_behavior");
    public static final ResourceKey<Registry<DomainBehavior>> CONTRACT_LIFECYCLE_BEHAVIOR_KEY = key("contract_lifecycle_behavior");
    public static final DefaultedRegistry<DomainBehavior> CONTRACT_LIFECYCLE_BEHAVIOR = behavior("contract_lifecycle_behavior");
    public static final ResourceKey<Registry<DomainBehavior>> ALCHEMY_OUTCOME_BEHAVIOR_KEY = key("alchemy_outcome_behavior");
    public static final DefaultedRegistry<DomainBehavior> ALCHEMY_OUTCOME_BEHAVIOR = behavior("alchemy_outcome_behavior");
    public static final ResourceKey<Registry<DomainBehavior>> REALM_LIFECYCLE_BEHAVIOR_KEY = key("realm_lifecycle_behavior");
    public static final DefaultedRegistry<DomainBehavior> REALM_LIFECYCLE_BEHAVIOR = behavior("realm_lifecycle_behavior");
    public static final ResourceKey<Registry<DomainBehavior>> ARTIFACT_LIFECYCLE_BEHAVIOR_KEY = key("artifact_lifecycle_behavior");
    public static final DefaultedRegistry<DomainBehavior> ARTIFACT_LIFECYCLE_BEHAVIOR = behavior("artifact_lifecycle_behavior");
    public static final ResourceKey<Registry<CreatureSpawnCondition>> CREATURE_SPAWN_CONDITION_KEY = key("creature_spawn_condition");
    public static final DefaultedRegistry<CreatureSpawnCondition> CREATURE_SPAWN_CONDITION = new DefaultedMappedRegistry<>("always", CREATURE_SPAWN_CONDITION_KEY, Lifecycle.stable(), false);
    public static final ResourceKey<Registry<CultivationCondition>> CULTIVATION_CONDITION_KEY = key("cultivation_condition");
    public static final DefaultedRegistry<CultivationCondition> CULTIVATION_CONDITION = new DefaultedMappedRegistry<>("always", CULTIVATION_CONDITION_KEY, Lifecycle.stable(), false);

    private MxtTypeRegistries() {
    }

    public static void newRegistries(NewRegistryEvent event) {
        event.register(ABILITY_TYPE);
        event.register(CURSE_TYPE);
        event.register(ABILITY_COMPONENT_TYPE);
        event.register(ABILITY_TRIGGER_TYPE);
        event.register(NUMBER_PROVIDER_TYPE);
        event.register(VALUE_MODIFIER_TYPE);
        event.register(RESOURCE_VALUE_PROVIDER_TYPE);
        event.register(ENTITY_ACTION_TYPE);
        event.register(BI_ENTITY_ACTION_TYPE);
        event.register(BLOCK_ACTION_TYPE);
        event.register(ITEM_ACTION_TYPE);
        event.register(ENTITY_CONDITION_TYPE);
        event.register(BI_ENTITY_CONDITION_TYPE);
        event.register(BLOCK_CONDITION_TYPE);
        event.register(ITEM_CONDITION_TYPE);
        event.register(DAMAGE_CONDITION_TYPE);
        event.register(RESOURCE_BAR_RENDERER_TYPE);
        event.register(RESOURCE_BAR_VISIBILITY_TYPE);
        event.register(FORGING_COMPLETION_BEHAVIOR);
        event.register(FORMATION_LIFECYCLE_BEHAVIOR);
        event.register(TRIBULATION_STAGE_BEHAVIOR);
        event.register(CULTIVATION_OUTCOME_BEHAVIOR);
        event.register(CONTRACT_LIFECYCLE_BEHAVIOR);
        event.register(ALCHEMY_OUTCOME_BEHAVIOR);
        event.register(REALM_LIFECYCLE_BEHAVIOR);
        event.register(ARTIFACT_LIFECYCLE_BEHAVIOR);
        event.register(CREATURE_SPAWN_CONDITION);
        event.register(CULTIVATION_CONDITION);
    }

    private static <T> ResourceKey<Registry<T>> key(String path) {
        return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path));
    }

    private static DefaultedRegistry<DomainBehavior> behavior(String path) {
        return new DefaultedMappedRegistry<>("no_op", key(path), Lifecycle.stable(), false);
    }
}
