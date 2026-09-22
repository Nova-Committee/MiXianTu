package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.data.artifact.ForgingResultComponent;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.ItemQuality.Modifier;
import com.iafenvoy.mxt.data.quality.ItemQualityTags;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbService;
import com.iafenvoy.mxt.runtime.item.ItemBindingService.ResolvedBindings;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;

import java.util.*;
import java.util.function.Function;

/**
 * Resolves an item's quality and exposes the tag-defined quality catalogue. Direct stack data takes precedence
 * over a forge result, which takes precedence over a binding's tag-defined default quality.
 */
@EventBusSubscriber
public final class ItemQualityService {
    private ItemQualityService() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        Optional<Failure> failure = checkForEvent(event.getEntity(), event.getEntity().getMainHandItem());
        if (failure.isPresent()) {
            event.setCanceled(true);
            notifyCannotUse(event.getEntity(), failure.orElseThrow());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemUse(RightClickItem event) {
        Optional<Failure> failure = checkForEvent(event.getEntity(), event.getEntity().getItemInHand(event.getHand()));
        if (failure.isPresent()) {
            event.setCanceled(true);
            notifyCannotUse(event.getEntity(), failure.orElseThrow());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockUse(RightClickBlock event) {
        Optional<Failure> failure = checkForEvent(event.getEntity(), event.getEntity().getItemInHand(event.getHand()));
        if (failure.isPresent()) {
            event.setCanceled(true);
            notifyCannotUse(event.getEntity(), failure.orElseThrow());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseStart(Start event) {
        Optional<Failure> failure = checkForEvent(event.getEntity(), event.getItem());
        if (failure.isPresent()) {
            event.setCanceled(true);
            notifyCannotUse(event.getEntity(), failure.orElseThrow());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseTick(Tick event) {
        Optional<Failure> failure = checkForEvent(event.getEntity(), event.getItem());
        if (failure.isPresent()) {
            event.setCanceled(true);
            notifyCannotUse(event.getEntity(), failure.orElseThrow());
        }
    }

    // Public because an interaction that runs below the gate's own priority still has to report the refusal it
    // sees.
    public static void notifyCannotUse(LivingEntity entity, Failure failure) {
        if (entity instanceof ServerPlayer player)
            player.sendSystemMessage(Component.translatable("actionbar.mxt.item.cannot_use",
                            Component.translatable("actionbar.mxt.item.cannot_use." + failure.name().toLowerCase(Locale.ROOT)))
                    .withStyle(ChatFormatting.RED), true);
    }

    private static Optional<Failure> checkForEvent(LivingEntity user, ItemStack stack) {
        if (user.level().isClientSide())
            return check(user.level().registryAccess(), user, stack);
        return check(user, stack);
    }

    public static Optional<Holder<ItemQuality>> find(ItemStack stack) {
        return find(MxtDatapackRegistries.registry(MxtResourceKeys.ITEM_QUALITY), stack, ItemBindingService.resolve(stack));
    }

    public static Optional<Holder<ItemQuality>> find(Provider access, ItemStack stack) {
        return find(access.lookupOrThrow(MxtResourceKeys.ITEM_QUALITY), stack, ItemBindingService.resolve(access, stack), access);
    }

    // Why an entity may not use an item: the gate is the union of three independent data-driven checks, so it
    // reports which one refused instead of only that the item is unusable.
    public enum Failure {
        /**
         * A matching binding's own conditions did not all pass.
         */
        BINDING_CONDITIONS,
        /**
         * The condition of the item's resolved quality did not pass.
         */
        QUALITY_CONDITIONS,
        /**
         * The item's resolved quality is missing, or is not a member of the binding's quality group.
         */
        QUALITY_GROUP
    }

    public static boolean canUse(LivingEntity user, ItemStack stack) {
        return canUse(user, stack, ItemBindingService.resolve(stack));
    }

    static boolean canUse(LivingEntity user, ItemStack stack, ResolvedBindings bindings) {
        return check(user, stack, bindings).isEmpty();
    }

    static boolean canUse(Provider access, LivingEntity user, ItemStack stack) {
        return check(access, user, stack).isEmpty();
    }

    // The reason canUse() would refuse this item, empty while the item is usable.
    public static Optional<Failure> check(LivingEntity user, ItemStack stack) {
        return check(user, stack, ItemBindingService.resolve(stack));
    }

    static Optional<Failure> check(LivingEntity user, ItemStack stack, ResolvedBindings bindings) {
        if (stack.isEmpty()) return Optional.empty();
        FormulaContext context = FormulaContext.of(user);
        if (!bindings.conditionsMet(user, context)) return Optional.of(Failure.BINDING_CONDITIONS);
        Optional<Holder<ItemQuality>> quality = find(MxtDatapackRegistries.registry(MxtResourceKeys.ITEM_QUALITY), stack, bindings);
        if (quality.isPresent() && !quality.orElseThrow().value().condition().test(user, context))
            return Optional.of(Failure.QUALITY_CONDITIONS);
        return bindings.qualityGroup()
                .filter(group -> quality.map(value -> !value.is(group)).orElse(true))
                .map(group -> Failure.QUALITY_GROUP);
    }

    static Optional<Failure> check(Provider access, LivingEntity user, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        ResolvedBindings bindings = ItemBindingService.resolve(access, stack);
        FormulaContext context = FormulaContext.of(user);
        if (!bindings.conditionsMet(user, context)) return Optional.of(Failure.BINDING_CONDITIONS);
        Optional<Holder<ItemQuality>> quality = find(access.lookupOrThrow(MxtResourceKeys.ITEM_QUALITY), stack, bindings, access);
        if (quality.isPresent() && !quality.orElseThrow().value().condition().test(user, context))
            return Optional.of(Failure.QUALITY_CONDITIONS);
        return bindings.qualityGroup()
                .filter(group -> quality.map(value -> !value.is(group)).orElse(true))
                .map(group -> Failure.QUALITY_GROUP);
    }

    public static void set(ItemStack stack, Holder<ItemQuality> quality) {
        stack.set(MxtDataComponents.ITEM_QUALITY.get(), quality);
    }

    public static void clear(ItemStack stack) {
        stack.remove(MxtDataComponents.ITEM_QUALITY.get());
    }

    // The same 1.0 the codec defaults to, so an item whose quality declares no modifier settles exactly as it did
    // before the modifier had a consumer.
    public static final double DEFAULT_MODIFIER = 1.0D;

    // All three modifiers are multipliers, so a zero or negative one could only erase or invert the amount it
    // settles: a broken formula has to leave that amount alone rather than cancel it.
    public static double modifier(Holder<ItemQuality> quality, Function<ItemQuality, Modifier> selector, FormulaContext context) {
        double value = selector.apply(quality.value()).modifier().evaluate(context);
        return Double.isFinite(value) && value > 0.0D ? value : DEFAULT_MODIFIER;
    }

    // A stack without a quality is not an error here: content declaring no quality has no modifier to apply,
    // which is precisely the DEFAULT_MODIFIER the codec would have supplied.
    public static double modifier(Provider access, ItemStack stack, Function<ItemQuality, Modifier> selector, FormulaContext context) {
        Optional<Holder<ItemQuality>> quality = find(access, stack);
        return quality.isPresent() ? modifier(quality.orElseThrow(), selector, context) : DEFAULT_MODIFIER;
    }

    // Like find(ItemStack), only usable while a server runs.
    public static double modifier(ItemStack stack, Function<ItemQuality, Modifier> selector, FormulaContext context) {
        Optional<Holder<ItemQuality>> quality = find(stack);
        return quality.isPresent() ? modifier(quality.orElseThrow(), selector, context) : DEFAULT_MODIFIER;
    }

    /**
     * Returns enabled qualities in the explicit tooltip-order tag, then all remaining entries.
     */
    public static List<Holder<ItemQuality>> ordered() {
        return ordered(MxtDatapackRegistries.registry(MxtResourceKeys.ITEM_QUALITY));
    }

    /**
     * Client-safe counterpart of {@link #ordered()}.
     */
    public static List<Holder<ItemQuality>> ordered(Provider access) {
        return ordered(access.lookupOrThrow(MxtResourceKeys.ITEM_QUALITY));
    }

    private static List<Holder<ItemQuality>> ordered(RegistryLookup<ItemQuality> registry) {
        Set<Holder<ItemQuality>> values = new LinkedHashSet<>();
        registry.get(ItemQualityTags.TOOLTIP_ORDER).ifPresent(tag -> tag.forEach(quality -> addEnabled(values, quality)));
        registry.listElements().forEach(quality -> addEnabled(values, quality));
        return List.copyOf(values);
    }

    /**
     * Returns enabled qualities in a named group, preserving the tag's declared order.
     */
    public static List<Holder<ItemQuality>> group(Identifier group) {
        return group(MxtDatapackRegistries.registry(MxtResourceKeys.ITEM_QUALITY), group);
    }

    /**
     * Client-safe counterpart of {@link #group(Identifier)}.
     */
    public static List<Holder<ItemQuality>> group(Provider access, Identifier group) {
        return group(access.lookupOrThrow(MxtResourceKeys.ITEM_QUALITY), group);
    }

    private static List<Holder<ItemQuality>> group(RegistryLookup<ItemQuality> registry, Identifier group) {
        return registry.get(ItemQualityTags.group(group))
                .map(tag -> tag.stream().filter(ItemQualityService::enabled).toList())
                .orElse(List.of());
    }

    /**
     * Lists all group tags. A group is any {@code group/<path>} tag on {@code item_quality}.
     */
    public static List<TagKey<ItemQuality>> groups() {
        return groups(MxtDatapackRegistries.registry(MxtResourceKeys.ITEM_QUALITY));
    }

    /**
     * Client-safe counterpart of {@link #groups()}.
     */
    public static List<TagKey<ItemQuality>> groups(Provider access) {
        return groups(access.lookupOrThrow(MxtResourceKeys.ITEM_QUALITY));
    }

    private static List<TagKey<ItemQuality>> groups(RegistryLookup<ItemQuality> registry) {
        return registry.listTagIds()
                .filter(ItemQualityTags::isGroup)
                .sorted(Comparator.comparing(tag -> tag.location().toString()))
                .toList();
    }

    public static boolean inGroup(Holder<ItemQuality> quality, Identifier group) {
        return enabled(quality) && quality.is(ItemQualityTags.group(group));
    }

    private static Optional<Holder<ItemQuality>> find(RegistryLookup<ItemQuality> registry, ItemStack stack,
                                                      ResolvedBindings bindings) {
        return intrinsic(stack)
                .or(() -> groupDefault(registry, bindings.qualityGroup()))
                .or(() -> SpiritHerbService.find(stack).map(SpiritHerb::quality).filter(ItemQualityService::enabled));
    }

    private static Optional<Holder<ItemQuality>> find(RegistryLookup<ItemQuality> registry, ItemStack stack,
                                                      ResolvedBindings bindings, Provider access) {
        return intrinsic(stack)
                .or(() -> groupDefault(registry, bindings.qualityGroup()))
                .or(() -> SpiritHerbService.find(access, stack).map(SpiritHerb::quality).filter(ItemQualityService::enabled));
    }

    private static Optional<Holder<ItemQuality>> groupDefault(RegistryLookup<ItemQuality> registry,
                                                              Optional<TagKey<ItemQuality>> qualityGroup) {
        return qualityGroup
                .flatMap(registry::get)
                .flatMap(values -> values.stream().filter(ItemQualityService::enabled).reduce((first, second) -> second));
    }

    private static Optional<Holder<ItemQuality>> intrinsic(ItemStack stack) {
        Holder<ItemQuality> direct = stack.get(MxtDataComponents.ITEM_QUALITY.get());
        if (enabled(direct)) return Optional.of(direct);
        ForgingResultComponent forged = stack.get(MxtDataComponents.FORGING_RESULT);
        if (forged != null && enabled(forged.quality())) return Optional.of(forged.quality());
        return Optional.empty();
    }

    private static void addEnabled(Set<Holder<ItemQuality>> values, Holder<ItemQuality> quality) {
        if (enabled(quality)) values.add(quality);
    }

    private static boolean enabled(Holder<ItemQuality> quality) {
        return quality != null && !MxtDatapackRegistries.isDisabled(MxtResourceKeys.ITEM_QUALITY, quality);
    }
}
