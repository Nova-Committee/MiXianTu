package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.data.artifact.ForgingResultData;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.ItemQualityTags;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbService;
import com.iafenvoy.mxt.runtime.item.ItemBindingService.ResolvedBindings;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Comparator;

/**
 * Resolves an item's quality and exposes the tag-defined quality catalogue.
 * Direct stack data takes precedence over a forge result, which in turn takes
 * precedence over a binding's tag-defined default quality.
 */
public final class ItemQualityService {
    private ItemQualityService() {
    }

    public static Optional<Holder<ItemQuality>> find(ItemStack stack) {
        return find(MxtDatapackRegistries.registry(MxtResourceKeys.ITEM_QUALITY), stack, ItemBindingService.resolve(stack));
    }

    public static Optional<Holder<ItemQuality>> find(HolderLookup.Provider access, ItemStack stack) {
        return find(access.lookupOrThrow(MxtResourceKeys.ITEM_QUALITY), stack, ItemBindingService.resolve(access, stack), access);
    }

    /**
     * Determines whether a living entity may use this item. All matching item
     * bindings and the resolved quality must pass their conditions. A binding
     * quality group also requires the resolved quality to be a member.
     */
    public static boolean canUse(LivingEntity user, ItemStack stack) {
        return canUse(user, stack, ItemBindingService.resolve(stack));
    }

    static boolean canUse(LivingEntity user, ItemStack stack, ResolvedBindings bindings) {
        if (stack.isEmpty()) return true;
        FormulaContext context = FormulaContext.of(user);
        if (!bindings.conditionsMet(user, context)) return false;
        Optional<Holder<ItemQuality>> quality = find(MxtDatapackRegistries.registry(MxtResourceKeys.ITEM_QUALITY), stack, bindings);
        if (quality.isPresent() && !quality.orElseThrow().value().condition().test(user, context)) return false;
        return bindings.qualityGroup()
                .map(group -> quality.map(value -> value.is(group)).orElse(false))
                .orElse(true);
    }

    public static void set(ItemStack stack, Holder<ItemQuality> quality) {
        stack.set(MxtDataComponents.ITEM_QUALITY.get(), quality);
    }

    public static void clear(ItemStack stack) {
        stack.remove(MxtDataComponents.ITEM_QUALITY.get());
    }

    /** Returns enabled qualities in the explicit tooltip-order tag, then all remaining entries. */
    public static List<Holder<ItemQuality>> ordered() {
        return ordered(MxtDatapackRegistries.registry(MxtResourceKeys.ITEM_QUALITY));
    }

    /** Client-safe counterpart of {@link #ordered()}. */
    public static List<Holder<ItemQuality>> ordered(HolderLookup.Provider access) {
        return ordered(access.lookupOrThrow(MxtResourceKeys.ITEM_QUALITY));
    }

    private static List<Holder<ItemQuality>> ordered(HolderLookup.RegistryLookup<ItemQuality> registry) {
        Set<Holder<ItemQuality>> values = new LinkedHashSet<>();
        registry.get(ItemQualityTags.TOOLTIP_ORDER).ifPresent(tag -> tag.forEach(quality -> addEnabled(values, quality)));
        registry.listElements().forEach(quality -> addEnabled(values, quality));
        return List.copyOf(values);
    }

    /** Returns enabled qualities in a named group, preserving the tag's declared order. */
    public static List<Holder<ItemQuality>> group(Identifier group) {
        return group(MxtDatapackRegistries.registry(MxtResourceKeys.ITEM_QUALITY), group);
    }

    /** Client-safe counterpart of {@link #group(Identifier)}. */
    public static List<Holder<ItemQuality>> group(HolderLookup.Provider access, Identifier group) {
        return group(access.lookupOrThrow(MxtResourceKeys.ITEM_QUALITY), group);
    }

    private static List<Holder<ItemQuality>> group(HolderLookup.RegistryLookup<ItemQuality> registry, Identifier group) {
        return registry.get(ItemQualityTags.group(group))
                .map(tag -> tag.stream().filter(ItemQualityService::enabled).toList())
                .orElse(List.of());
    }

    /** Lists all group tags. A group is any {@code group/<path>} tag on {@code item_quality}. */
    public static List<TagKey<ItemQuality>> groups() {
        return groups(MxtDatapackRegistries.registry(MxtResourceKeys.ITEM_QUALITY));
    }

    /** Client-safe counterpart of {@link #groups()}. */
    public static List<TagKey<ItemQuality>> groups(HolderLookup.Provider access) {
        return groups(access.lookupOrThrow(MxtResourceKeys.ITEM_QUALITY));
    }

    private static List<TagKey<ItemQuality>> groups(HolderLookup.RegistryLookup<ItemQuality> registry) {
        return registry.listTagIds()
                .filter(ItemQualityTags::isGroup)
                .sorted(Comparator.comparing(tag -> tag.location().toString()))
                .toList();
    }

    public static boolean inGroup(Holder<ItemQuality> quality, Identifier group) {
        return enabled(quality) && quality.is(ItemQualityTags.group(group));
    }

    private static Optional<Holder<ItemQuality>> find(HolderLookup.RegistryLookup<ItemQuality> registry, ItemStack stack,
                                                       ResolvedBindings bindings) {
        return intrinsic(stack)
                .or(() -> groupDefault(registry, bindings.qualityGroup()))
                .or(() -> SpiritHerbService.find(stack).map(herb -> herb.quality()).filter(ItemQualityService::enabled));
    }

    private static Optional<Holder<ItemQuality>> find(HolderLookup.RegistryLookup<ItemQuality> registry, ItemStack stack,
                                                       ResolvedBindings bindings, HolderLookup.Provider access) {
        return intrinsic(stack)
                .or(() -> groupDefault(registry, bindings.qualityGroup()))
                .or(() -> SpiritHerbService.find(access, stack).map(herb -> herb.quality()).filter(ItemQualityService::enabled));
    }

    private static Optional<Holder<ItemQuality>> groupDefault(HolderLookup.RegistryLookup<ItemQuality> registry,
                                                                Optional<TagKey<ItemQuality>> qualityGroup) {
        return qualityGroup
                .flatMap(registry::get)
                .flatMap(values -> values.stream().filter(ItemQualityService::enabled).reduce((first, second) -> second));
    }

    private static Optional<Holder<ItemQuality>> intrinsic(ItemStack stack) {
        Holder<ItemQuality> direct = stack.get(MxtDataComponents.ITEM_QUALITY.get());
        if (enabled(direct)) return Optional.of(direct);
        ForgingResultData forged = stack.get(MxtDataComponents.FORGING_RESULT);
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
