package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.CurrencyValue;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.RealmInstance;
import com.iafenvoy.mxt.data.Sect;
import com.iafenvoy.mxt.data.Title;
import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.AbilityComponent;
import com.iafenvoy.mxt.data.ability.AbilityTrigger;
import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.TargetSelector;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.data.artifact.ItemArchetype;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.aura.AuraMaximum;
import com.iafenvoy.mxt.data.aura.BlockAura;
import com.iafenvoy.mxt.data.aura.ItemAura;
import com.iafenvoy.mxt.data.badge.Badge;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.data.creature.CreatureProfile;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.data.curse.CurseType;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.data.item.ItemBinding;
import com.iafenvoy.mxt.data.item.PillBinding;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.data.item.WeaponBinding;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarVisibility;
import com.iafenvoy.mxt.runtime.creature.CreatureSpawnCondition;
import com.iafenvoy.mxt.runtime.cultivation.CultivationCondition;
import com.iafenvoy.mxt.util.formula.FormulaVariable;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.objecthunter.exp4j.function.Function;

/**
 * The single source of truth for every registry key owned by MiXianTu.
 *
 * <p>The values are deliberately kept separate from the registry instances and
 * registration events. This makes registry identity available to codecs and
 * runtime services without coupling them to a particular registration class.</p>
 */
public final class MxtResourceKeys {
    // Builtin registries
    public static final ResourceKey<Registry<MapCodec<? extends AbilityType>>> ABILITY_TYPE = create("ability_type");
    public static final ResourceKey<Registry<MapCodec<? extends TargetSelector>>> ABILITY_TARGET_SELECTOR_TYPE = create("ability_target_selector_type");
    public static final ResourceKey<Registry<MapCodec<? extends Cost>>> COST_TYPE = create("cost_type");
    public static final ResourceKey<Registry<MapCodec<? extends CurseType>>> CURSE_TYPE = create("curse_type");
    public static final ResourceKey<Registry<MapCodec<? extends AbilityComponent>>> ABILITY_COMPONENT_TYPE = create("ability_component_type");
    public static final ResourceKey<Registry<MapCodec<? extends AbilityTrigger>>> ABILITY_TRIGGER_TYPE = create("ability_trigger_type");
    public static final ResourceKey<Registry<MapCodec<? extends NumberProvider>>> NUMBER_PROVIDER_TYPE = create("number_provider_type");
    public static final ResourceKey<Registry<MapCodec<? extends AuraMaximum>>> AURA_MAXIMUM_TYPE = create("aura_maximum_type");
    public static final ResourceKey<Registry<Function>> FORMULA_FUNCTION = create("formula_function");
    public static final ResourceKey<Registry<FormulaVariable>> FORMULA_VARIABLE = create("formula_variable");
    public static final ResourceKey<Registry<MapCodec<? extends ResourceValueProvider>>> RESOURCE_VALUE_PROVIDER_TYPE = create("resource_value_provider_type");
    public static final ResourceKey<Registry<MapCodec<? extends EntityAction>>> ENTITY_ACTION_TYPE = create("entity_action_type");
    public static final ResourceKey<Registry<MapCodec<? extends BiEntityAction>>> BI_ENTITY_ACTION_TYPE = create("bi_entity_action_type");
    public static final ResourceKey<Registry<MapCodec<? extends BlockAction>>> BLOCK_ACTION_TYPE = create("block_action_type");
    public static final ResourceKey<Registry<MapCodec<? extends ItemAction>>> ITEM_ACTION_TYPE = create("item_action_type");
    public static final ResourceKey<Registry<MapCodec<? extends EntityCondition>>> ENTITY_CONDITION_TYPE = create("entity_condition_type");
    public static final ResourceKey<Registry<MapCodec<? extends BiEntityCondition>>> BI_ENTITY_CONDITION_TYPE = create("bi_entity_condition_type");
    public static final ResourceKey<Registry<MapCodec<? extends BlockCondition>>> BLOCK_CONDITION_TYPE = create("block_condition_type");
    public static final ResourceKey<Registry<MapCodec<? extends ItemCondition>>> ITEM_CONDITION_TYPE = create("item_condition_type");
    public static final ResourceKey<Registry<MapCodec<? extends DamageCondition>>> DAMAGE_CONDITION_TYPE = create("damage_condition_type");
    public static final ResourceKey<Registry<MapCodec<? extends ResourceBarRenderData>>> RESOURCE_BAR_RENDER_DATA_TYPE = create("resource_bar_render_data_type");
    public static final ResourceKey<Registry<ResourceBarContext>> RESOURCE_BAR_CONTEXT = create("resource_bar_context");
    public static final ResourceKey<Registry<MapCodec<? extends ResourceBarVisibility>>> RESOURCE_BAR_VISIBILITY_TYPE = create("resource_bar_visibility_type");
    public static final ResourceKey<Registry<MapCodec<? extends Badge>>> BADGE_TYPE = create("badge_type");
    public static final ResourceKey<Registry<MapCodec<? extends Entry>>> ITEM_MATCHER_ENTRY_TYPE = create("item_matcher_entry_type");
    public static final ResourceKey<Registry<CreatureSpawnCondition>> CREATURE_SPAWN_CONDITION = create("creature_spawn_condition");
    public static final ResourceKey<Registry<CultivationCondition>> CULTIVATION_CONDITION = create("cultivation_condition");

    // Datapack registries
    public static final ResourceKey<Registry<Resource>> RESOURCE = create("resource");
    public static final ResourceKey<Registry<Badge>> BADGE = create("badge");
    public static final ResourceKey<Registry<RealmStage>> REALM_STAGE = create("realm_stage");
    public static final ResourceKey<Registry<Element>> ELEMENT = create("element");
    public static final ResourceKey<Registry<SpiritRoot>> SPIRIT_ROOT = create("spirit_root");
    public static final ResourceKey<Registry<Physique>> PHYSIQUE = create("physique");
    public static final ResourceKey<Registry<Ability>> ABILITY = create("ability");
    public static final ResourceKey<Registry<Curse>> CURSE = create("curse");
    public static final ResourceKey<Registry<ForgingMethod>> FORGING_METHOD = create("forging_method");
    public static final ResourceKey<Registry<ForgingBlueprint>> FORGING_BLUEPRINT = create("forging_blueprint");
    public static final ResourceKey<Registry<CultivationTechnique>> CULTIVATION_TECHNIQUE = create("cultivation_technique");
    public static final ResourceKey<Registry<CultivateAction>> CULTIVATE_ACTION = create("cultivate_action");
    public static final ResourceKey<Registry<ItemArchetype>> ITEM_ARCHETYPE = create("item_archetype");
    public static final ResourceKey<Registry<SpiritHerb>> SPIRIT_HERB = create("spirit_herb");
    public static final ResourceKey<Registry<Formation>> FORMATION = create("formation");
    public static final ResourceKey<Registry<Tribulation>> TRIBULATION = create("tribulation");
    public static final ResourceKey<Registry<CreatureProfile>> CREATURE_PROFILE = create("creature_profile");
    public static final ResourceKey<Registry<ContractType>> CONTRACT_TYPE = create("contract_type");
    public static final ResourceKey<Registry<Title>> TITLE = create("title");
    public static final ResourceKey<Registry<Sect>> SECT = create("sect");
    public static final ResourceKey<Registry<RealmInstance>> REALM_INSTANCE = create("realm_instance");
    public static final ResourceKey<Registry<CurrencyValue>> CURRENCY = create("currency");
    public static final ResourceKey<Registry<ItemBinding>> ITEM_BINDING = create("item_binding");
    public static final ResourceKey<Registry<WeaponBinding>> WEAPON_BINDING = create("weapon_binding");
    public static final ResourceKey<Registry<PillBinding>> PILL_BINDING = create("pill_binding");
    public static final ResourceKey<Registry<TechniqueBinding>> TECHNIQUE_BINDING = create("technique_binding");
    public static final ResourceKey<Registry<AuraZone>> AURA_ZONE = create("aura_zone");
    public static final ResourceKey<Registry<BlockAura>> BLOCK_AURA = create("block_aura");
    public static final ResourceKey<Registry<ItemAura>> ITEM_AURA = create("item_aura");
    public static final ResourceKey<Registry<ItemQuality>> ITEM_QUALITY = create("item_quality");

    private static <T> ResourceKey<Registry<T>> create(String path) {
        return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path));
    }
}
