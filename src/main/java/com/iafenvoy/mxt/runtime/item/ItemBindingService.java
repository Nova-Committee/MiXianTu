package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.AttributeModifier;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.item.ItemBinding;
import com.iafenvoy.mxt.data.item.PillBinding;
import com.iafenvoy.mxt.data.item.WeaponBinding;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.alchemy.PillService;
import com.iafenvoy.mxt.util.ItemMatcher;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemAttributeModifiers.Builder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves datapack gameplay bindings for items already registered by Minecraft,
 * a mod, or KubeJS. No logical item definition is stored in an ItemStack.
 */
public final class ItemBindingService {
    private ItemBindingService() {
    }

    public static List<EntityAction> actions(ItemStack stack) {
        return binding(stack).map(ItemBinding::actions).orElse(List.of());
    }

    public static List<EntityAction> actions(Provider access, ItemStack stack) {
        return binding(access, stack).map(ItemBinding::actions).orElse(List.of());
    }

    public static Optional<WeaponBinding> weapon(ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(MxtDatapackRegistries.WEAPON_BINDING)
                .map(Reference::value), stack);
    }

    public static Optional<WeaponBinding> weapon(Provider access, ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtDatapackRegistries.WEAPON_BINDING)
                .map(Reference::value), stack);
    }

    public static Optional<PillBinding> pill(ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(MxtDatapackRegistries.PILL_BINDING)
                .map(Reference::value), stack);
    }

    public static Optional<PillBinding> pill(Provider access, ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtDatapackRegistries.PILL_BINDING)
                .map(Reference::value), stack);
    }

    /**
     * Re-applies weapon attributes after equipment changes or datapack reloads.
     */
    public static void refreshEquipped(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        refreshWeapon(entity.getItemBySlot(EquipmentSlot.MAINHAND));
        refreshWeapon(entity.getItemBySlot(EquipmentSlot.OFFHAND));
    }

    /**
     * Runs the active main-hand weapon's periodic effects server-side.
     */
    public static void tickMainHandWeapon(LivingEntity holder) {
        if (holder.level().isClientSide()) return;
        FormulaContext context = FormulaContext.of(holder);
        weapon(holder.getMainHandItem()).ifPresent(weapon -> weapon.tickAction().execute(holder, context));
    }

    /**
     * Runs the active main-hand weapon's left-click action against the attacked entity.
     */
    public static void onMainHandWeaponAttack(LivingEntity holder, Entity target) {
        if (holder.level().isClientSide()) return;
        FormulaContext context = FormulaContext.of(holder, Map.of(
                "target_is_living", target instanceof LivingEntity ? 1.0D : 0.0D,
                "target_health", target instanceof LivingEntity living ? (double) living.getHealth() : 0.0D
        ));
        weapon(holder.getMainHandItem()).ifPresent(weapon -> weapon.attackAction().execute(holder, target, context));
    }

    /**
     * Runs a main-hand weapon's explicit right-click action.
     */
    public static void onMainHandWeaponUse(LivingEntity holder) {
        if (holder.level().isClientSide()) return;
        FormulaContext context = FormulaContext.of(holder);
        weapon(holder.getMainHandItem()).ifPresent(weapon -> weapon.useAction().execute(holder, context));
    }

    /**
     * Dispatches generic item-binding actions and pill behaviour after vanilla consumption.
     */
    public static void onUseFinish(LivingEntity entity, ItemStack stack) {
        if (entity.level().isClientSide()) return;
        FormulaContext context = FormulaContext.of(entity);
        actions(stack).forEach(action -> action.execute(entity, context));
        pill(stack).ifPresent(definition -> PillService.consume(entity, definition));
    }

    private static Optional<ItemBinding> binding(ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(MxtDatapackRegistries.ITEM_BINDING)
                .map(Reference::value), stack);
    }

    private static Optional<ItemBinding> binding(Provider access, ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtDatapackRegistries.ITEM_BINDING)
                .map(Reference::value), stack);
    }

    private static void refreshWeapon(ItemStack stack) {
        weapon(stack).ifPresent(weapon -> {
            ItemAttributeModifiers modifiers = weaponModifiers(stack.getItem(), weapon);
            if (!modifiers.equals(stack.get(DataComponents.ATTRIBUTE_MODIFIERS))) {
                stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
            }
        });
    }

    private static ItemAttributeModifiers weaponModifiers(Item item, WeaponBinding weapon) {
        Builder builder = ItemAttributeModifiers.builder();
        add(builder, Attributes.ATTACK_DAMAGE, modifierId(item, "attack_damage"),
                weapon.attackDamage().evaluate(FormulaContext.EMPTY), Operation.ADD_VALUE);
        add(builder, Attributes.ATTACK_SPEED, modifierId(item, "attack_speed"),
                weapon.attackSpeed().evaluate(FormulaContext.EMPTY), Operation.ADD_VALUE);
        for (int index = 0; index < weapon.attributes().size(); index++) {
            AttributeModifier attribute = weapon.attributes().get(index);
            add(builder, attribute.attribute(), modifierId(item, "attribute_" + index),
                    attribute.value().evaluate(FormulaContext.EMPTY), operation(attribute.operation()));
        }
        return builder.build();
    }

    private static void add(Builder builder, Holder<Attribute> attribute, Identifier id, double value, Operation operation) {
        if (Double.isFinite(value) && value != 0.0D) {
            builder.add(attribute, new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, value, operation), EquipmentSlotGroup.MAINHAND);
        }
    }

    private static Identifier modifierId(Item item, String suffix) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID,
                "weapon_binding/" + itemId.getNamespace() + "/" + itemId.getPath() + "/" + suffix);
    }

    private static Operation operation(AttributeModifier.Operation operation) {
        return switch (operation) {
            case ADD_VALUE -> Operation.ADD_VALUE;
            case ADD_MULTIPLIED_BASE -> Operation.ADD_MULTIPLIED_BASE;
            case ADD_MULTIPLIED_TOTAL -> Operation.ADD_MULTIPLIED_TOTAL;
        };
    }
}
