package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.alchemy.PillDefinition;
import com.iafenvoy.mxt.data.common.AttributeModifierDefinition;
import com.iafenvoy.mxt.data.cultivation.SpiritRootDefinition;
import com.iafenvoy.mxt.data.item.ItemBindingDefinition;
import com.iafenvoy.mxt.data.item.ItemDefinition;
import com.iafenvoy.mxt.data.item.ItemDefinitionReference;
import com.iafenvoy.mxt.data.item.ItemEffectDefinition;
import com.iafenvoy.mxt.data.item.SpiritRootItemEffect;
import com.iafenvoy.mxt.data.weapon.WeaponDefinition;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.alchemy.PillService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ItemAttributeModifiers.Builder;

import java.util.List;
import java.util.Optional;

/**
 * Materializes {@code mxt:item} definitions through the one-way binding to
 * Minecraft's physical Item registry, then dispatches reusable item effects.
 */
public final class ItemBindingService {
    private ItemBindingService() {
    }

    /**
     * Creates a stack when exactly one physical Item binding exists for this category-qualified definition.
     */
    public static Optional<ItemStack> create(ItemDefinitionReference itemDefinition) {
        List<Item> items = MxtDatapackRegistries.holders(MxtDatapackRegistries.ITEM_BINDING)
                .map(Holder.Reference::value)
                .filter(binding -> binding.definition().equals(itemDefinition))
                .map(ItemBindingDefinition::item)
                .distinct()
                .toList();
        return items.size() == 1 ? create(items.getFirst(), itemDefinition) : Optional.empty();
    }

    /**
     * Resolves a runtime recipe output when its ID identifies exactly one data-driven item category.
     */
    public static Optional<ItemStack> create(Identifier itemDefinition) {
        List<ItemDefinitionReference> definitions = MxtDatapackRegistries.itemReferences(itemDefinition).toList();
        return definitions.size() == 1 ? create(definitions.getFirst()) : Optional.empty();
    }

    /**
     * Creates a stack from the client-synchronised datapack registries.
     */
    public static Optional<ItemStack> create(Provider access, ItemDefinitionReference itemDefinition) {
        List<Item> items = MxtDatapackRegistries.holders(access, MxtDatapackRegistries.ITEM_BINDING)
                .map(Holder.Reference::value)
                .filter(binding -> binding.definition().equals(itemDefinition))
                .map(ItemBindingDefinition::item)
                .distinct()
                .toList();
        return items.size() == 1 ? create(access, items.getFirst(), itemDefinition) : Optional.empty();
    }

    /**
     * Creates one stack using an explicitly chosen physical Item binding.
     */
    public static Optional<ItemStack> create(Item item, ItemDefinitionReference itemDefinition) {
        ItemStack stack = new ItemStack(item);
        return bind(stack, itemDefinition) ? Optional.of(stack) : Optional.empty();
    }

    /**
     * Resolves a runtime recipe output using an explicitly supplied physical item.
     */
    public static Optional<ItemStack> create(Item item, Identifier itemDefinition) {
        List<ItemDefinitionReference> definitions = MxtDatapackRegistries.itemReferences(itemDefinition).toList();
        return definitions.size() == 1 ? create(item, definitions.getFirst()) : Optional.empty();
    }

    /**
     * Creates one client-side display stack using an explicitly chosen physical Item binding.
     */
    public static Optional<ItemStack> create(Provider access, Item item, ItemDefinitionReference itemDefinition) {
        ItemStack stack = new ItemStack(item);
        return bind(access, stack, itemDefinition) ? Optional.of(stack) : Optional.empty();
    }

    /**
     * Binds a stack to its logical item definition after validating the physical Item bridge.
     */
    public static boolean bind(ItemStack stack, ItemDefinitionReference itemDefinition) {
        Optional<ItemDefinition> definition = MxtDatapackRegistries.get(itemDefinition);
        if (definition.isEmpty() || !isBound(stack.getItem(), itemDefinition)) return false;
        stack.set(MxtDataComponents.ITEM_DEFINITION, itemDefinition);
        applyPresentation(stack, itemDefinition.id(), definition.get());
        refreshWeapon(stack);
        return true;
    }

    /**
     * Resolves a runtime recipe output when its ID identifies exactly one data-driven item category.
     */
    public static boolean bind(ItemStack stack, Identifier itemDefinition) {
        List<ItemDefinitionReference> definitions = MxtDatapackRegistries.itemReferences(itemDefinition).toList();
        return definitions.size() == 1 && bind(stack, definitions.getFirst());
    }

    /**
     * Binds a client-side display stack using the synced datapack registries.
     */
    public static boolean bind(Provider access, ItemStack stack, ItemDefinitionReference itemDefinition) {
        Optional<ItemDefinition> definition = MxtDatapackRegistries.get(access, itemDefinition);
        if (definition.isEmpty() || !isBound(access, stack.getItem(), itemDefinition)) return false;
        stack.set(MxtDataComponents.ITEM_DEFINITION, itemDefinition);
        applyPresentation(stack, itemDefinition.id(), definition.get());
        refreshWeapon(access, stack);
        return true;
    }

    /**
     * Resolves the logical item definition selected by this stack.
     */
    public static Optional<ResolvedItem> find(ItemStack stack) {
        ItemDefinitionReference explicit = stack.get(MxtDataComponents.ITEM_DEFINITION);
        if (explicit != null) {
            return isBound(stack.getItem(), explicit)
                    ? MxtDatapackRegistries.get(explicit).map(value -> new ResolvedItem(explicit, value))
                    : Optional.empty();
        }
        List<ItemDefinitionReference> matches = MxtDatapackRegistries.holders(MxtDatapackRegistries.ITEM_BINDING)
                .map(Holder.Reference::value)
                .filter(binding -> binding.item() == stack.getItem())
                .map(ItemBindingDefinition::definition)
                .distinct()
                .toList();
        return matches.size() == 1
                ? MxtDatapackRegistries.get(matches.getFirst()).map(value -> new ResolvedItem(matches.getFirst(), value))
                : Optional.empty();
    }

    /**
     * Resolves a stack using the client-synchronised datapack registries.
     */
    public static Optional<ResolvedItem> find(Provider access, ItemStack stack) {
        ItemDefinitionReference explicit = stack.get(MxtDataComponents.ITEM_DEFINITION);
        if (explicit != null) {
            return isBound(access, stack.getItem(), explicit)
                    ? MxtDatapackRegistries.get(access, explicit).map(value -> new ResolvedItem(explicit, value))
                    : Optional.empty();
        }
        List<ItemDefinitionReference> matches = MxtDatapackRegistries.holders(access, MxtDatapackRegistries.ITEM_BINDING)
                .map(Holder.Reference::value)
                .filter(binding -> binding.item() == stack.getItem())
                .map(ItemBindingDefinition::definition)
                .distinct()
                .toList();
        return matches.size() == 1
                ? MxtDatapackRegistries.get(access, matches.getFirst()).map(value -> new ResolvedItem(matches.getFirst(), value))
                : Optional.empty();
    }

    /**
     * Returns a logical item ID when bound, otherwise the physical Item registry ID.
     */
    public static Identifier identifier(ItemStack stack) {
        return find(stack).map(item -> item.reference().id()).orElseGet(() -> BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /**
     * Matches a logical item definition first and preserves support for unbound vanilla Item IDs.
     */
    public static boolean matches(ItemStack stack, Identifier id) {
        return find(stack).map(resolved -> resolved.reference().id().equals(id))
                .orElseGet(() -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id));
    }

    /**
     * Matches an exact category-qualified logical item reference.
     */
    public static boolean matches(ItemStack stack, ItemDefinitionReference reference) {
        return find(stack).map(resolved -> resolved.reference().equals(reference)).orElse(false);
    }

    /**
     * Resolves all registered effects in item-definition order, skipping disabled or missing entries.
     */
    public static List<ResolvedEffect> effects(ItemStack stack) {
        return find(stack).stream()
                .flatMap(item -> item.definition().effects().stream()
                        .map(effect -> new ResolvedEffect(HolderHelper.id(effect), effect.value())))
                .toList();
    }

    /**
     * Resolves item effects from the client-synchronised datapack registries.
     */
    public static List<ResolvedEffect> effects(Provider access, ItemStack stack) {
        return find(access, stack).stream()
                .flatMap(item -> item.definition().effects().stream()
                        .map(effect -> new ResolvedEffect(HolderHelper.id(effect), effect.value())))
                .toList();
    }

    /**
     * Re-applies runtime-dependent weapon attributes after equipment changes or datapack reloads.
     */
    public static void refreshEquipped(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        refreshWeapon(entity.getItemBySlot(EquipmentSlot.MAINHAND));
        refreshWeapon(entity.getItemBySlot(EquipmentSlot.OFFHAND));
    }

    /**
     * Dispatches consumption effects after vanilla has finished consuming the physical stack.
     */
    public static void onUseFinish(LivingEntity entity, ItemStack stack) {
        if (entity.level().isClientSide()) return;
        for (ResolvedEffect resolved : effects(stack)) {
            if (resolved.definition() instanceof PillDefinition pill) {
                PillService.consume(entity, pill);
            } else if (resolved.definition() instanceof SpiritRootItemEffect(Holder<SpiritRootDefinition> spiritRoot)) {
                CultivationIdentityService.grantSpiritRoot(entity, HolderHelper.id(spiritRoot), spiritRoot.value());
            }
        }
    }

    private static boolean isBound(Item item, ItemDefinitionReference itemDefinition) {
        return MxtDatapackRegistries.holders(MxtDatapackRegistries.ITEM_BINDING)
                .map(Holder.Reference::value)
                .anyMatch(binding -> binding.item() == item && binding.definition().equals(itemDefinition));
    }

    private static boolean isBound(Provider access, Item item, ItemDefinitionReference itemDefinition) {
        return MxtDatapackRegistries.holders(access, MxtDatapackRegistries.ITEM_BINDING)
                .map(Holder.Reference::value)
                .anyMatch(binding -> binding.item() == item && binding.definition().equals(itemDefinition));
    }

    private static void refreshWeapon(ItemStack stack) {
        List<ResolvedEffect> weapons = effects(stack).stream()
                .filter(effect -> effect.definition() instanceof WeaponDefinition)
                .toList();
        if (weapons.isEmpty()) return;
        find(stack).ifPresent(item -> applyPresentation(stack, item.reference().id(), item.definition()));
        ItemAttributeModifiers modifiers = weaponModifiers(weapons);
        if (!modifiers.equals(stack.get(DataComponents.ATTRIBUTE_MODIFIERS))) {
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
        }
    }

    private static void refreshWeapon(Provider access, ItemStack stack) {
        ItemDefinitionReference reference = stack.get(MxtDataComponents.ITEM_DEFINITION);
        if (reference == null) return;
        List<ResolvedEffect> weapons = MxtDatapackRegistries.get(access, reference).stream()
                .flatMap(item -> item.effects().stream()
                        .map(effect -> new ResolvedEffect(HolderHelper.id(effect), effect.value())))
                .filter(effect -> effect.definition() instanceof WeaponDefinition)
                .toList();
        if (weapons.isEmpty()) return;
        ItemAttributeModifiers modifiers = weaponModifiers(weapons);
        if (!modifiers.equals(stack.get(DataComponents.ATTRIBUTE_MODIFIERS))) {
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
        }
    }

    private static void applyPresentation(ItemStack stack, Identifier itemDefinition, ItemDefinition definition) {
        Identifier model = definition.modelFor(itemDefinition);
        if (!model.equals(stack.get(DataComponents.ITEM_MODEL))) stack.set(DataComponents.ITEM_MODEL, model);
    }

    private static ItemAttributeModifiers weaponModifiers(List<ResolvedEffect> weapons) {
        Builder builder = ItemAttributeModifiers.builder();
        for (ResolvedEffect resolved : weapons) {
            WeaponDefinition definition = (WeaponDefinition) resolved.definition();
            add(builder, Attributes.ATTACK_DAMAGE, modifierId(resolved.id(), "attack_damage"), definition.attackDamage().evaluate(FormulaContext.EMPTY), Operation.ADD_VALUE);
            add(builder, Attributes.ATTACK_SPEED, modifierId(resolved.id(), "attack_speed"), definition.attackSpeed().evaluate(FormulaContext.EMPTY), Operation.ADD_VALUE);
            for (int index = 0; index < definition.attributes().size(); index++) {
                AttributeModifierDefinition attribute = definition.attributes().get(index);
                Identifier modifierId = modifierId(resolved.id(), "attribute_" + index);
                add(builder, attribute.attribute(), modifierId, attribute.value().evaluate(FormulaContext.EMPTY), operation(attribute.operation()));
            }
        }
        return builder.build();
    }

    private static void add(Builder builder, Holder<Attribute> attribute,
                            Identifier id, double value, Operation operation) {
        if (Double.isFinite(value) && value != 0.0D) {
            builder.add(attribute, new AttributeModifier(id, value, operation), EquipmentSlotGroup.MAINHAND);
        }
    }

    private static Identifier modifierId(Identifier effect, String suffix) {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID,
                "item_effect/" + effect.getNamespace() + "/" + effect.getPath() + "/" + suffix);
    }

    private static Operation operation(AttributeModifierDefinition.Operation operation) {
        return switch (operation) {
            case ADD_VALUE -> Operation.ADD_VALUE;
            case ADD_MULTIPLIED_BASE -> Operation.ADD_MULTIPLIED_BASE;
            case ADD_MULTIPLIED_TOTAL -> Operation.ADD_MULTIPLIED_TOTAL;
        };
    }

    public record ResolvedItem(ItemDefinitionReference reference, ItemDefinition definition) {
    }

    public record ResolvedEffect(Identifier id, ItemEffectDefinition definition) {
    }
}
