package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.item.ItemBinding;
import com.iafenvoy.mxt.data.item.PillBinding;
import com.iafenvoy.mxt.data.item.PillComponent;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.data.item.TechniqueReadingComponent;
import com.iafenvoy.mxt.data.item.WeaponBinding;
import com.iafenvoy.mxt.data.quality.QualityChain;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.alchemy.PillService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.iafenvoy.mxt.util.matcher.builtin.ItemEntry;
import com.iafenvoy.mxt.util.matcher.builtin.TagEntry;
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

import java.util.*;
import java.util.stream.Stream;

/**
 * Resolves datapack gameplay bindings for items already registered by Minecraft, a mod or KubeJS: a binding table
 * attaches this mod's rules to a stack, while what the item itself is stays with its own content registry. No
 * logical item definition is ever stored in an ItemStack.
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

    public static Optional<WeaponBinding> weapon(Provider access, ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtResourceKeys.WEAPON_BINDING)
                .map(Reference::value), stack);
    }

    private static Optional<PillBinding> pill(Provider access, ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtResourceKeys.PILL_BINDING)
                .map(Reference::value), stack);
    }

    // What a stack teaches is the stack's own mxt:technique component; the declaration for that technique only
    // says how reading one feels, and a technique with no declaration is still read, with the defaults. A stack
    // carrying no component is read as whatever a declaration claiming it names.
    public static Optional<TechniqueBinding> technique(ItemStack stack) {
        return technique(MxtDatapackRegistries.holders(MxtResourceKeys.TECHNIQUE_BINDING), stack);
    }

    public static Optional<TechniqueBinding> technique(Provider access, ItemStack stack) {
        return technique(MxtDatapackRegistries.holders(access, MxtResourceKeys.TECHNIQUE_BINDING), stack);
    }

    // The carrier the mod offers for a technique: the item a declaration claims for it, the item that declaration
    // generates a stack from, or the jade slip. A claimed item is a manual by matching, so only a generated stack
    // needs the component written onto it.
    public static ItemStack techniqueCarrier(Provider access, Holder<Technique> technique) {
        TechniqueBinding declaration = declaration(access, technique);
        Item claimed = declaration == null ? null : claimedItem(declaration.entries());
        if (claimed != null) return new ItemStack(claimed);
        Item carrier = declaration == null ? MxtItems.CULTIVATION_JADE_SLIP.get()
                : declaration.carrierItem().orElse(MxtItems.CULTIVATION_JADE_SLIP.get());
        ItemStack stack = new ItemStack(carrier);
        stack.set(MxtDataComponents.TECHNIQUE.get(), technique);
        return stack;
    }

    private static TechniqueBinding declaration(Provider access, Holder<Technique> technique) {
        return MxtDatapackRegistries.holders(access, MxtResourceKeys.TECHNIQUE_BINDING)
                .map(Reference::value)
                .filter(binding -> HolderHelper.id(binding.technique()).equals(HolderHelper.id(technique)))
                .findFirst()
                .orElse(null);
    }

    // Only an entry naming one item can stand for a declaration here: a tag expands to a whole set, and a matcher
    // that reads the stack names no item at all.
    private static Item claimedItem(List<Entry> entries) {
        for (Entry entry : entries) {
            if (entry instanceof ItemEntry(Item item)) return item;
            if (entry instanceof TagEntry(TagKey<Item> tag))
                return BuiltInRegistries.ITEM.get(tag).flatMap(set -> set.stream().findFirst())
                        .map(Holder::value).orElse(null);
        }
        return null;
    }

    private static Optional<TechniqueBinding> technique(Stream<Reference<TechniqueBinding>> declarations, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        Holder<Technique> taught = stack.get(MxtDataComponents.TECHNIQUE.get());
        Optional<TechniqueBinding> declared = taught == null
                ? ItemMatcher.find(declarations.map(Reference::value), stack)
                : declarations.map(Reference::value)
                .filter(binding -> HolderHelper.id(binding.technique()).equals(HolderHelper.id(taught)))
                .findFirst()
                .or(() -> Optional.of(TechniqueBinding.defaults(taught)));
        TechniqueReadingComponent reading = stack.get(MxtDataComponents.TECHNIQUE_READING.get());
        return reading == null ? declared : declared.map(reading::applyTo);
    }

    public static ResolvedBindings resolve(Provider access, ItemStack stack) {
        // An empty stack answers nothing: a wildcard matcher would otherwise claim it, and every component read
        // below would be a miss anyway.
        if (stack.isEmpty()) return new ResolvedBindings(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
        Optional<PillBinding> declared = pill(access, stack);
        PillComponent pill = stack.get(MxtDataComponents.PILL.get());
        Optional<PillBinding> merged = pill == null ? declared
                : Optional.of(pill.applyTo(declared.orElseGet(PillBinding::defaults)));
        return new ResolvedBindings(binding(access, stack), weapon(access, stack), merged, technique(access, stack),
                Optional.ofNullable(stack.get(MxtDataComponents.QUALITY_CHAIN.get())));
    }

    public static Optional<Holder<QualityChain>> qualityChain(Provider access, ItemStack stack) {
        return resolve(access, stack).qualityChain();
    }

    private static void refreshEquipped(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        refreshWeapon(entity, entity.getItemBySlot(EquipmentSlot.MAINHAND));
        refreshWeapon(entity, entity.getItemBySlot(EquipmentSlot.OFFHAND));
    }

    public static void tickMainHandWeapon(LivingEntity holder) {
        if (holder.level().isClientSide()) return;
        ItemStack stack = holder.getMainHandItem();
        ResolvedBindings bindings = resolve(holder.level().registryAccess(), stack);
        if (!ItemQualityService.canUse(holder, stack, bindings)) return;
        FormulaContext context = FormulaContext.of(holder);
        bindings.weapon().ifPresent(weapon -> weapon.tickAction().execute(holder, context));
    }

    public static void onMainHandWeaponAttack(LivingEntity holder, Entity target) {
        if (holder.level().isClientSide()) return;
        ItemStack stack = holder.getMainHandItem();
        ResolvedBindings bindings = resolve(holder.level().registryAccess(), stack);
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
        ResolvedBindings bindings = resolve(holder.level().registryAccess(), stack);
        if (!ItemQualityService.canUse(holder, stack, bindings)) return;
        FormulaContext context = FormulaContext.of(holder);
        bindings.weapon().ifPresent(weapon -> weapon.useAction().execute(holder, context));
    }

    public static void onUseFinish(LivingEntity entity, ItemStack stack) {
        if (entity.level().isClientSide()) return;
        ResolvedBindings bindings = resolve(entity.level().registryAccess(), stack);
        if (!ItemQualityService.canUse(entity, stack, bindings)) return;
        FormulaContext context = FormulaContext.of(entity);
        bindings.item().map(ItemBinding::actions).orElse(List.of()).forEach(action -> action.execute(entity, context));
        bindings.pill().ifPresent(definition -> PillService.consume(entity, definition));
    }

    // Public because it is one of the readings ItemElements takes when it asks what an item is made of; resolve()
    // wraps it for callers that want several binding kinds at once.
    public static Optional<ItemBinding> binding(Provider access, ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtResourceKeys.ITEM_BINDING)
                .map(Reference::value), stack);
    }

    private static void refreshWeapon(LivingEntity entity, ItemStack stack) {
        ResolvedBindings bindings = resolve(entity.level().registryAccess(), stack);
        bindings.weapon().ifPresent(weapon -> {
            ItemAttributeModifiers baseline = baselineModifiers(stack, weapon);
            ItemAttributeModifiers modifiers = ItemQualityService.canUse(entity, stack, bindings)
                    ? weaponModifiers(baseline, weapon, entity)
                    : baseline;
            if (!modifiers.equals(stack.get(DataComponents.ATTRIBUTE_MODIFIERS))) {
                stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
            }
        });
    }

    // The item's own modifiers plus any attribute another system applied. Everything this declaration writes is
    // dropped first because it is re-derived: the ids it declares would otherwise be added again on every tick.
    private static ItemAttributeModifiers baselineModifiers(ItemStack stack, WeaponBinding weapon) {
        ItemAttributeModifiers current = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (current == null)
            return stack.getPrototype().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        Set<Identifier> declared = new HashSet<>();
        for (AttributeEntry attribute : weapon.attributes()) declared.add(attribute.modifier().id());
        Builder builder = ItemAttributeModifiers.builder();
        current.modifiers().forEach(entry -> {
            Identifier id = entry.modifier().id();
            if (declared.contains(id) || isBindingModifier(id)) return;
            builder.add(entry.attribute(), entry.modifier(), entry.slot(), entry.display());
        });
        return builder.build();
    }

    // The mod namespace. Only leftovers from before a weapon's numbers became ordinary attributes are still
    // written under it, and they are stripped so a stack saved back then does not keep its old replacement.
    private static boolean isBindingModifier(Identifier id) {
        return id != null && MiXianTu.MOD_ID.equals(id.getNamespace());
    }

    // Only what the declaration writes is added, and what the item type itself ships with is left alone: a pack
    // that wants a different attack number rewrites the item's own modifiers instead of overlaying them.
    private static ItemAttributeModifiers weaponModifiers(ItemAttributeModifiers baseline, WeaponBinding weapon,
                                                          LivingEntity entity) {
        Builder builder = ItemAttributeModifiers.builder();
        baseline.modifiers().forEach(entry -> builder.add(entry.attribute(), entry.modifier(), entry.slot(), entry.display()));
        FormulaContext context = FormulaContext.of(entity);
        for (AttributeEntry attribute : weapon.attributes()) {
            add(builder, attribute.attribute(), attribute.modifier().id(),
                    attribute.amount(context), attribute.modifier().operation());
        }
        return builder.build();
    }

    private static void add(Builder builder, Holder<Attribute> attribute, Identifier id, double value, Operation operation) {
        if (Double.isFinite(value) && value != 0.0D) {
            builder.add(attribute, new AttributeModifier(id, value, operation), EquipmentSlotGroup.MAINHAND);
        }
    }

    // Immutable resolution snapshot, so one operation does not repeat the matcher scans. The stack's own
    // mxt:quality_chain is read here too, because it outranks every declaration's.
    public record ResolvedBindings(Optional<ItemBinding> item, Optional<WeaponBinding> weapon,
                                   Optional<PillBinding> pill, Optional<TechniqueBinding> technique,
                                   Optional<Holder<QualityChain>> qualityChainOverride) {
        public Optional<Holder<QualityChain>> qualityChain() {
            return this.qualityChainOverride
                    .or(() -> this.weapon.flatMap(WeaponBinding::qualityChain))
                    .or(() -> this.pill.flatMap(PillBinding::qualityChain))
                    .or(() -> this.technique.flatMap(TechniqueBinding::qualityChain))
                    .or(() -> this.item.flatMap(ItemBinding::qualityChain));
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
