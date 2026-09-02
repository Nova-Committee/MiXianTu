package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.compat.kubejs.type.condition.JsItemCondition;
import com.iafenvoy.mxt.data.condition.AlwaysTrueCondition;
import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.condition.builtin.item.*;
import com.iafenvoy.mxt.data.condition.builtin.item.meta.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.iafenvoy.mxt.data.condition.SimpleConditions.createItem;

@SuppressWarnings("unused")
public final class MxtItemConditions {
    public static final DeferredRegister<MapCodec<? extends ItemCondition>> REGISTRY = DeferredRegister.create(MxtRegistries.ITEM_CONDITION_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<AlwaysTrueCondition>> ALWAYS_TRUE = REGISTRY.register("always_true", () -> AlwaysTrueCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<JsItemCondition>> JS = REGISTRY.register("js", () -> JsItemCondition.CODEC);

    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<AndItemCondition>> AND = REGISTRY.register("and", () -> AndItemCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ItemIdCondition>> ITEM_ID = REGISTRY.register("item_id", () -> ItemIdCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<OwnedByItemCondition>> OWNED_BY = REGISTRY.register("owned_by", () -> OwnedByItemCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ArtifactEnergyRangeItemCondition>> ENERGY_RANGE = REGISTRY.register("energy_range", () -> ArtifactEnergyRangeItemCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ItemTagCondition>> ITEM_TAG = REGISTRY.register("item_tag", () -> ItemTagCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ItemMatcherCondition>> ITEM_MATCHER = REGISTRY.register("item_matcher", () -> ItemMatcherCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<AmountCondition>> AMOUNT = REGISTRY.register("amount", () -> AmountCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<FuelCondition>> FUEL = REGISTRY.register("fuel", () -> FuelCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<IsEquipableCondition>> IS_EQUIPABLE = REGISTRY.register("is_equipable", () -> IsEquipableCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<RelativeDurabilityCondition>> RELATIVE_DURABILITY = REGISTRY.register("relative_durability", () -> RelativeDurabilityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ChanceCondition>> CHANCE = REGISTRY.register("chance", () -> ChanceCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ConstantCondition>> CONSTANT = REGISTRY.register("constant", () -> ConstantCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<NotCondition>> NOT = REGISTRY.register("not", () -> NotCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<OrCondition>> OR = REGISTRY.register("or", () -> OrCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ArmorValueCondition>> ARMOR_VALUE = REGISTRY.register("armor_value", () -> ArmorValueCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<DurabilityCondition>> DURABILITY = REGISTRY.register("durability", () -> DurabilityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<? extends ItemCondition>> ON_COOLDOWN = REGISTRY.register("on_cooldown", () -> createItem(ctx -> ctx.holder() instanceof Player player && player.getCooldowns().isOnCooldown(ctx.stack())));
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<IngredientCondition>> INGREDIENT = REGISTRY.register("ingredient", () -> IngredientCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ToolAbilityCondition>> TOOL_ABILITY = REGISTRY.register("tool_ability", () -> ToolAbilityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<BaseEnchantmentCondition>> BASE_ENCHANTMENT = REGISTRY.register("base_enchantment", () -> BaseEnchantmentCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<HasComponentCondition>> HAS_COMPONENT = REGISTRY.register("has_component", () -> HasComponentCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ComponentCondition>> COMPONENT = REGISTRY.register("component", () -> ComponentCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<SpiritStorageNotFullCondition>> SPIRIT_STORAGE_NOT_FULL = REGISTRY.register("spirit_storage_not_full", () -> SpiritStorageNotFullCondition.CODEC);
}
