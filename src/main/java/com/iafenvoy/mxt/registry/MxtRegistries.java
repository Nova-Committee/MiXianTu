package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.data.ability.AbilityComponent;
import com.iafenvoy.mxt.data.ability.AbilityTrigger;
import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.badge.Badge;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.condition.*;
import com.iafenvoy.mxt.data.curse.CurseType;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderer;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarVisibility;
import com.iafenvoy.mxt.runtime.creature.CreatureSpawnCondition;
import com.iafenvoy.mxt.runtime.cultivation.CultivationCondition;
import com.iafenvoy.mxt.util.formula.FormulaVariable;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import net.objecthunter.exp4j.function.Function;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.DefaultedRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.NewRegistryEvent;

/**
 * Java-owned registries of codecs. Datapacks may select entries but can never extend these registries.
 */
@EventBusSubscriber
public final class MxtRegistries {
    public static final DefaultedRegistry<MapCodec<? extends AbilityType>> ABILITY_TYPE = new DefaultedMappedRegistry<>("empty", MxtResourceKeys.ABILITY_TYPE, Lifecycle.stable(), false);
    public static final DefaultedRegistry<MapCodec<? extends CurseType>> CURSE_TYPE = new DefaultedMappedRegistry<>("empty", MxtResourceKeys.CURSE_TYPE, Lifecycle.stable(), false);
    public static final DefaultedRegistry<MapCodec<? extends AbilityComponent>> ABILITY_COMPONENT_TYPE = new DefaultedMappedRegistry<>("empty", MxtResourceKeys.ABILITY_COMPONENT_TYPE, Lifecycle.stable(), false);
    public static final DefaultedRegistry<MapCodec<? extends AbilityTrigger>> ABILITY_TRIGGER_TYPE = new DefaultedMappedRegistry<>("use", MxtResourceKeys.ABILITY_TRIGGER_TYPE, Lifecycle.stable(), false);
    public static final DefaultedRegistry<MapCodec<? extends NumberProvider>> NUMBER_PROVIDER_TYPE = new DefaultedMappedRegistry<>("constant", MxtResourceKeys.NUMBER_PROVIDER_TYPE, Lifecycle.stable(), false);
    public static final DefaultedRegistry<Function> FORMULA_FUNCTION = new DefaultedMappedRegistry<>("round", MxtResourceKeys.FORMULA_FUNCTION, Lifecycle.stable(), false);
    public static final DefaultedRegistry<FormulaVariable> FORMULA_VARIABLE = new DefaultedMappedRegistry<>("zero", MxtResourceKeys.FORMULA_VARIABLE, Lifecycle.stable(), false);
    public static final DefaultedRegistry<MapCodec<? extends ResourceValueProvider>> RESOURCE_VALUE_PROVIDER_TYPE = new DefaultedMappedRegistry<>("current", MxtResourceKeys.RESOURCE_VALUE_PROVIDER_TYPE, Lifecycle.stable(), false);
    public static final DefaultedRegistry<MapCodec<? extends EntityAction>> ENTITY_ACTION_TYPE = new DefaultedMappedRegistry<>("no_op", MxtResourceKeys.ENTITY_ACTION_TYPE, Lifecycle.stable(), false);
    public static final DefaultedRegistry<MapCodec<? extends BiEntityAction>> BI_ENTITY_ACTION_TYPE = new DefaultedMappedRegistry<>("no_op", MxtResourceKeys.BI_ENTITY_ACTION_TYPE, Lifecycle.stable(), false);
    public static final DefaultedRegistry<MapCodec<? extends BlockAction>> BLOCK_ACTION_TYPE = new DefaultedMappedRegistry<>("no_op", MxtResourceKeys.BLOCK_ACTION_TYPE, Lifecycle.stable(), false);
    public static final DefaultedRegistry<MapCodec<? extends ItemAction>> ITEM_ACTION_TYPE = new DefaultedMappedRegistry<>("no_op", MxtResourceKeys.ITEM_ACTION_TYPE, Lifecycle.stable(), false);
    public static final DefaultedRegistry<MapCodec<? extends EntityCondition>> ENTITY_CONDITION_TYPE = new DefaultedMappedRegistry<>("always_true", MxtResourceKeys.ENTITY_CONDITION_TYPE, Lifecycle.stable(), false);
    public static final DefaultedRegistry<MapCodec<? extends BiEntityCondition>> BI_ENTITY_CONDITION_TYPE = new DefaultedMappedRegistry<>("always_true", MxtResourceKeys.BI_ENTITY_CONDITION_TYPE, Lifecycle.stable(), false);
    public static final DefaultedRegistry<MapCodec<? extends BlockCondition>> BLOCK_CONDITION_TYPE = new DefaultedMappedRegistry<>("always_true", MxtResourceKeys.BLOCK_CONDITION_TYPE, Lifecycle.stable(), false);
    public static final DefaultedRegistry<MapCodec<? extends ItemCondition>> ITEM_CONDITION_TYPE = new DefaultedMappedRegistry<>("always_true", MxtResourceKeys.ITEM_CONDITION_TYPE, Lifecycle.stable(), false);
    public static final DefaultedRegistry<MapCodec<? extends DamageCondition>> DAMAGE_CONDITION_TYPE = new DefaultedMappedRegistry<>("always_true", MxtResourceKeys.DAMAGE_CONDITION_TYPE, Lifecycle.stable(), false);
    public static final DefaultedRegistry<MapCodec<? extends ResourceBarRenderer>> RESOURCE_BAR_RENDERER_TYPE = new DefaultedMappedRegistry<>("missing", MxtResourceKeys.RESOURCE_BAR_RENDERER_TYPE, Lifecycle.stable(), false);
    public static final DefaultedRegistry<MapCodec<? extends ResourceBarVisibility>> RESOURCE_BAR_VISIBILITY_TYPE = new DefaultedMappedRegistry<>("always", MxtResourceKeys.RESOURCE_BAR_VISIBILITY_TYPE, Lifecycle.stable(), false);
    public static final DefaultedRegistry<MapCodec<? extends Badge>> BADGE_TYPE = new DefaultedMappedRegistry<>("empty", MxtResourceKeys.BADGE_TYPE, Lifecycle.stable(), false);
    public static final DefaultedRegistry<CreatureSpawnCondition> CREATURE_SPAWN_CONDITION = new DefaultedMappedRegistry<>("always", MxtResourceKeys.CREATURE_SPAWN_CONDITION, Lifecycle.stable(), false);
    public static final DefaultedRegistry<CultivationCondition> CULTIVATION_CONDITION = new DefaultedMappedRegistry<>("always", MxtResourceKeys.CULTIVATION_CONDITION, Lifecycle.stable(), false);

    @SubscribeEvent
    public static void newRegistries(NewRegistryEvent event) {
        event.register(ABILITY_TYPE);
        event.register(CURSE_TYPE);
        event.register(ABILITY_COMPONENT_TYPE);
        event.register(ABILITY_TRIGGER_TYPE);
        event.register(NUMBER_PROVIDER_TYPE);
        event.register(FORMULA_FUNCTION);
        event.register(FORMULA_VARIABLE);
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
        event.register(BADGE_TYPE);
        event.register(CREATURE_SPAWN_CONDITION);
        event.register(CULTIVATION_CONDITION);
    }

}
