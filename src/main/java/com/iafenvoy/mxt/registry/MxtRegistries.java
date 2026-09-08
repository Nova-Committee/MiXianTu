package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.data.ability.AbilityComponent;
import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.TargetSelector;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.aura.AuraMaximum;
import com.iafenvoy.mxt.data.badge.Badge;
import com.iafenvoy.mxt.data.condition.*;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.curse.CurseType;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarVisibility;
import com.iafenvoy.mxt.data.trigger.Trigger;
import com.iafenvoy.mxt.util.formula.FormulaVariable;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.objecthunter.exp4j.function.Function;

import java.util.LinkedList;
import java.util.List;

@EventBusSubscriber
public final class MxtRegistries {
    private static final List<DefaultedRegistry<?>> REGISTRIES = new LinkedList<>();

    public static final DefaultedRegistry<MapCodec<? extends AbilityType>> ABILITY_TYPE = create("empty", MxtResourceKeys.ABILITY_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends TargetSelector>> ABILITY_TARGET_SELECTOR_TYPE = create("self", MxtResourceKeys.ABILITY_TARGET_SELECTOR_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends Cost>> COST_TYPE = create("resource", MxtResourceKeys.COST_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends CurseType>> CURSE_TYPE = create("empty", MxtResourceKeys.CURSE_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends AbilityComponent>> ABILITY_COMPONENT_TYPE = create("empty", MxtResourceKeys.ABILITY_COMPONENT_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends Trigger>> TRIGGER_TYPE = create("use", MxtResourceKeys.TRIGGER_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends NumberProvider>> NUMBER_PROVIDER_TYPE = create("constant", MxtResourceKeys.NUMBER_PROVIDER_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends AuraMaximum>> AURA_MAXIMUM_TYPE = create("initial_multiplier", MxtResourceKeys.AURA_MAXIMUM_TYPE);
    public static final DefaultedRegistry<Function> FORMULA_FUNCTION = create("round", MxtResourceKeys.FORMULA_FUNCTION);
    public static final DefaultedRegistry<FormulaVariable> FORMULA_VARIABLE = create("zero", MxtResourceKeys.FORMULA_VARIABLE);
    public static final DefaultedRegistry<MapCodec<? extends ResourceValueProvider>> RESOURCE_VALUE_PROVIDER_TYPE = create("current", MxtResourceKeys.RESOURCE_VALUE_PROVIDER_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends EntityAction>> ENTITY_ACTION_TYPE = create("no_op", MxtResourceKeys.ENTITY_ACTION_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends BiEntityAction>> BI_ENTITY_ACTION_TYPE = create("no_op", MxtResourceKeys.BI_ENTITY_ACTION_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends BlockAction>> BLOCK_ACTION_TYPE = create("no_op", MxtResourceKeys.BLOCK_ACTION_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends ItemAction>> ITEM_ACTION_TYPE = create("no_op", MxtResourceKeys.ITEM_ACTION_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends EntityCondition>> ENTITY_CONDITION_TYPE = create("always_true", MxtResourceKeys.ENTITY_CONDITION_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends BiEntityCondition>> BI_ENTITY_CONDITION_TYPE = create("always_true", MxtResourceKeys.BI_ENTITY_CONDITION_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends BlockCondition>> BLOCK_CONDITION_TYPE = create("always_true", MxtResourceKeys.BLOCK_CONDITION_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends ItemCondition>> ITEM_CONDITION_TYPE = create("always_true", MxtResourceKeys.ITEM_CONDITION_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends DamageCondition>> DAMAGE_CONDITION_TYPE = create("always_true", MxtResourceKeys.DAMAGE_CONDITION_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends ResourceBarRenderData>> RESOURCE_BAR_RENDER_DATA_TYPE = create("missing", MxtResourceKeys.RESOURCE_BAR_RENDER_DATA_TYPE);
    public static final DefaultedRegistry<ResourceBarContext> RESOURCE_BAR_CONTEXT = create("self_hud", MxtResourceKeys.RESOURCE_BAR_CONTEXT);
    public static final DefaultedRegistry<MapCodec<? extends ResourceBarVisibility>> RESOURCE_BAR_VISIBILITY_TYPE = create("always", MxtResourceKeys.RESOURCE_BAR_VISIBILITY_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends Badge>> BADGE_TYPE = create("empty", MxtResourceKeys.BADGE_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends Entry>> ITEM_MATCHER_ENTRY_TYPE = create("item", MxtResourceKeys.ITEM_MATCHER_ENTRY_TYPE);

    private static <T> DefaultedRegistry<T> create(String defaultKey, ResourceKey<? extends Registry<T>> key) {
        DefaultedRegistry<T> registry = new DefaultedMappedRegistry<>(defaultKey, key, Lifecycle.stable(), false);
        REGISTRIES.add(registry);
        return registry;
    }

    @SubscribeEvent
    public static void newRegistries(NewRegistryEvent event) {
        REGISTRIES.forEach(event::register);
    }
}
