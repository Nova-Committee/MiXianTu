package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.item.ItemBinding;
import com.iafenvoy.mxt.data.item.PillBinding;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.data.item.WeaponBinding;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.alchemy.PillService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.item.component.ItemAttributeModifiers.Builder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves datapack gameplay bindings for items already registered by Minecraft, a mod or KubeJS. No logical
 * item definition is ever stored in an ItemStack.
 */
@EventBusSubscriber
public final class ItemBindingService {
    private ItemBindingService() {
    }

    @SubscribeEvent
    public static void onEntityTick(Post event) {
        if (event.getEntity() instanceof LivingEntity entity) {
            refreshEquipped(entity);
            tickMainHandWeapon(entity);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        onMainHandWeaponAttack(event.getEntity(), event.getTarget());
    }

    @SubscribeEvent
    public static void onItemUse(RightClickItem event) {
        if (event.getHand() == InteractionHand.MAIN_HAND)
            onMainHandWeaponUse(event.getEntity());
    }

    @SubscribeEvent
    public static void onUseFinish(Finish event) {
        if (!ItemQualityService.canUse(event.getEntity(), event.getItem())) return;
        onUseFinish(event.getEntity(), event.getItem());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerLoggedInEvent event) {
        refreshEquipped(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerClone(Clone event) {
        refreshEquipped(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerRespawnEvent event) {
        refreshEquipped(event.getEntity());
    }

    public static List<EntityAction> actions(ItemStack stack) {
        return binding(stack).map(ItemBinding::actions).orElse(List.of());
    }

    public static List<EntityAction> actions(Provider access, ItemStack stack) {
        return binding(access, stack).map(ItemBinding::actions).orElse(List.of());
    }

    public static Optional<WeaponBinding> weapon(ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(MxtResourceKeys.WEAPON_BINDING)
                .map(Reference::value), stack);
    }

    public static Optional<WeaponBinding> weapon(Provider access, ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtResourceKeys.WEAPON_BINDING)
                .map(Reference::value), stack);
    }

    public static Optional<PillBinding> pill(ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(MxtResourceKeys.PILL_BINDING)
                .map(Reference::value), stack);
    }

    public static Optional<PillBinding> pill(Provider access, ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtResourceKeys.PILL_BINDING)
                .map(Reference::value), stack);
    }

    public static Optional<TechniqueBinding> technique(ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(MxtResourceKeys.TECHNIQUE_BINDING)
                .map(Reference::value), stack);
    }

    public static Optional<TechniqueBinding> technique(Provider access, ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtResourceKeys.TECHNIQUE_BINDING)
                .map(Reference::value), stack);
    }

    public static ResolvedBindings resolve(ItemStack stack) {
        return new ResolvedBindings(binding(stack), weapon(stack), pill(stack), technique(stack));
    }

    public static ResolvedBindings resolve(Provider access, ItemStack stack) {
        return new ResolvedBindings(binding(access, stack), weapon(access, stack), pill(access, stack), technique(access, stack));
    }

    public static Optional<TagKey<ItemQuality>> qualityGroup(ItemStack stack) {
        return resolve(stack).qualityGroup();
    }

    public static Optional<TagKey<ItemQuality>> qualityGroup(Provider access, ItemStack stack) {
        return resolve(access, stack).qualityGroup();
    }

    public static boolean conditionsMet(LivingEntity entity, ItemStack stack, FormulaContext context) {
        return resolve(stack).conditionsMet(entity, context);
    }

    public static void refreshEquipped(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        refreshWeapon(entity, entity.getItemBySlot(EquipmentSlot.MAINHAND));
        refreshWeapon(entity, entity.getItemBySlot(EquipmentSlot.OFFHAND));
    }

    public static void tickMainHandWeapon(LivingEntity holder) {
        if (holder.level().isClientSide()) return;
        ItemStack stack = holder.getMainHandItem();
        ResolvedBindings bindings = resolve(stack);
        if (!ItemQualityService.canUse(holder, stack, bindings)) return;
        FormulaContext context = FormulaContext.of(holder);
        bindings.weapon().ifPresent(weapon -> weapon.tickAction().execute(holder, context));
    }

    public static void onMainHandWeaponAttack(LivingEntity holder, Entity target) {
        if (holder.level().isClientSide()) return;
        ItemStack stack = holder.getMainHandItem();
        ResolvedBindings bindings = resolve(stack);
        if (!ItemQualityService.canUse(holder, stack, bindings)) return;
        FormulaContext context = FormulaContext.of(holder, Map.of(
                "target_is_living", target instanceof LivingEntity ? 1.0D : 0.0D,
                "target_health", target instanceof LivingEntity living ? (double) living.getHealth() : 0.0D
        ));
        bindings.weapon().ifPresent(weapon -> weapon.attackAction().execute(holder, target, context));
    }

    public static void onMainHandWeaponUse(LivingEntity holder) {
        if (holder.level().isClientSide()) return;
        ItemStack stack = holder.getMainHandItem();
        ResolvedBindings bindings = resolve(stack);
        if (!ItemQualityService.canUse(holder, stack, bindings)) return;
        FormulaContext context = FormulaContext.of(holder);
        bindings.weapon().ifPresent(weapon -> weapon.useAction().execute(holder, context));
    }

    public static void onUseFinish(LivingEntity entity, ItemStack stack) {
        if (entity.level().isClientSide()) return;
        ResolvedBindings bindings = resolve(stack);
        if (!ItemQualityService.canUse(entity, stack, bindings)) return;
        FormulaContext context = FormulaContext.of(entity);
        bindings.item().map(ItemBinding::actions).orElse(List.of()).forEach(action -> action.execute(entity, context));
        bindings.pill().ifPresent(definition -> PillService.consume(entity, definition));
    }

    private static Optional<ItemBinding> binding(ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(MxtResourceKeys.ITEM_BINDING)
                .map(Reference::value), stack);
    }

    // Public because it is one of the readings ItemElements takes when it asks what an item is made of; resolve()
    // wraps it for callers that want several binding kinds at once.
    public static Optional<ItemBinding> binding(Provider access, ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtResourceKeys.ITEM_BINDING)
                .map(Reference::value), stack);
    }

    private static void refreshWeapon(LivingEntity entity, ItemStack stack) {
        ResolvedBindings bindings = resolve(stack);
        bindings.weapon().ifPresent(weapon -> {
            ItemAttributeModifiers baseline = baselineModifiers(stack);
            ItemAttributeModifiers modifiers = ItemQualityService.canUse(entity, stack, bindings)
                    ? weaponModifiers(stack, baseline, weapon, entity)
                    : baseline;
            if (!modifiers.equals(stack.get(DataComponents.ATTRIBUTE_MODIFIERS))) {
                stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
            }
        });
    }

    // The item's own modifiers plus any attribute another system applied; binding modifiers are stripped, since
    // they are re-derived.
    private static ItemAttributeModifiers baselineModifiers(ItemStack stack) {
        ItemAttributeModifiers current = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (current == null)
            return stack.getPrototype().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        Builder builder = ItemAttributeModifiers.builder();
        current.modifiers().forEach(entry -> {
            if (!isBindingModifier(entry.modifier().id()))
                builder.add(entry.attribute(), entry.modifier(), entry.slot());
        });
        return builder.build();
    }

    // The mod namespace, which no vanilla or third-party attribute modifier uses.
    private static boolean isBindingModifier(Identifier id) {
        return id != null && MiXianTu.MOD_ID.equals(id.getNamespace());
    }

    // attack_damage and attack_speed are the weapon's own numbers, so a declaration replaces the item's default for
    // that attribute - only that default, and declaring zero leaves the item's own number alone.
    private static ItemAttributeModifiers weaponModifiers(ItemStack stack, ItemAttributeModifiers baseline,
                                                          WeaponBinding weapon, LivingEntity entity) {
        Builder builder = ItemAttributeModifiers.builder();
        Item item = stack.getItem();
        FormulaContext context = FormulaContext.of(entity);
        double damage = weapon.attackDamage().evaluate(context);
        double speed = weapon.attackSpeed().evaluate(context);
        Set<Identifier> defaults = defaultModifierIds(stack);
        baseline.modifiers().forEach(entry -> {
            if (replaces(entry, defaults, Attributes.ATTACK_DAMAGE, damage)) return;
            if (replaces(entry, defaults, Attributes.ATTACK_SPEED, speed)) return;
            builder.add(entry.attribute(), entry.modifier(), entry.slot());
        });
        // A declared speed at or below -4 was measured to park the held item too low in first person (the player's
        // base is 4), so declarations must stay above it - see research/23 §10.15.
        add(builder, Attributes.ATTACK_DAMAGE, modifierId(item, "attack_damage"), damage, Operation.ADD_VALUE);
        add(builder, Attributes.ATTACK_SPEED, modifierId(item, "attack_speed"), speed, Operation.ADD_VALUE);
        for (int index = 0; index < weapon.attributes().size(); index++) {
            AttributeEntry attribute = weapon.attributes().get(index);
            add(builder, attribute.attribute(), attribute.modifier().id(),
                    attribute.amount(context), attribute.modifier().operation());
        }
        return builder.build();
    }

    // The ids of the modifiers the item type itself ships with - the ones a declared damage or speed may replace.
    private static Set<Identifier> defaultModifierIds(ItemStack stack) {
        Set<Identifier> ids = new HashSet<>();
        stack.getPrototype().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .modifiers().forEach(entry -> ids.add(entry.modifier().id()));
        return ids;
    }

    private static boolean replaces(ItemAttributeModifiers.Entry entry, Set<Identifier> defaults,
                                    Holder<Attribute> attribute, double declared) {
        if (declared == 0.0D) return false;
        return defaults.contains(entry.modifier().id())
                && HolderHelper.id(entry.attribute()).equals(HolderHelper.id(attribute));
    }

    private static void add(Builder builder, Holder<Attribute> attribute, Identifier id, double value, Operation operation) {
        if (Double.isFinite(value) && value != 0.0D) {
            builder.add(attribute, new AttributeModifier(id, value, operation), EquipmentSlotGroup.MAINHAND);
        }
    }

    private static Identifier modifierId(Item item, String suffix) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID,
                "weapon_binding/" + itemId.getNamespace() + "/" + itemId.getPath() + "/" + suffix);
    }

    // Immutable resolution snapshot, so one operation does not repeat the matcher scans.
    public record ResolvedBindings(Optional<ItemBinding> item, Optional<WeaponBinding> weapon,
                                   Optional<PillBinding> pill, Optional<TechniqueBinding> technique) {
        public Optional<TagKey<ItemQuality>> qualityGroup() {
            return this.weapon.flatMap(WeaponBinding::qualityGroup)
                    .or(() -> this.pill.flatMap(PillBinding::qualityGroup))
                    .or(() -> this.technique.flatMap(TechniqueBinding::qualityGroup))
                    .or(() -> this.item.flatMap(ItemBinding::qualityGroup));
        }

        public boolean conditionsMet(LivingEntity entity, FormulaContext context) {
            return this.weapon.map(value -> value.conditions().stream()
                    .allMatch(condition -> condition.value().test(entity, context))).orElse(true)
                    && this.pill.map(value -> value.conditions().stream()
                    .allMatch(condition -> condition.value().test(entity, context))).orElse(true)
                    && this.technique.map(value -> value.conditions().stream()
                    .allMatch(condition -> condition.value().test(entity, context))).orElse(true)
                    && this.item.map(value -> value.conditions().stream()
                    .allMatch(condition -> condition.value().test(entity, context))).orElse(true);
        }
    }
}
