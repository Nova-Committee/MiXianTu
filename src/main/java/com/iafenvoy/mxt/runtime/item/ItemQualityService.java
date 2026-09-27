package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.data.artifact.ForgingResultComponent;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.ItemQuality.Modifier;
import com.iafenvoy.mxt.data.quality.ItemQualityTags;
import com.iafenvoy.mxt.data.quality.QualityLadders;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.item.ItemBindingService.ResolvedBindings;
import com.iafenvoy.mxt.runtime.talisman.TalismanService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
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
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * Resolves an item's quality, the ladder it is read on, and the tag-defined quality catalogue. The stack's own
 * component takes precedence over a forge result, which takes precedence over a definition's or a spirit herb's
 * declaration.
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
        return check(user.level().registryAccess(), user, stack);
    }

    // The one resolution order: an explicit component, what a settlement wrote, the definition claiming the stack,
    // the entry tier of the ladder that stack reads, and last a spirit herb's own declaration. Read with a Provider
    // because the client draws tooltips from the same order.
    public static Optional<Holder<ItemQuality>> find(Provider access, ItemStack stack) {
        return find(registry(access).orElse(null), stack, ItemBindingService.resolve(access, stack), access);
    }

    private static Optional<Holder<ItemQuality>> find(@Nullable RegistryLookup<ItemQuality> registry, ItemStack stack,
                                                      ResolvedBindings bindings, Provider access) {
        Optional<Holder<ItemQuality>> declared = intrinsic(registry, stack);
        return declared
                .or(() -> definitionDefault(access, stack))
                .or(() -> ladderDefault(access, declared.orElse(null)))
                .or(() -> SpiritHerbService.find(access, stack).map(SpiritHerb::quality));
    }

    // A registry the client has not been sent is not an error here: the stack simply resolves to whatever the
    // remaining slots answer.
    private static Optional<RegistryLookup<ItemQuality>> registry(Provider access) {
        return access.lookup(MxtResourceKeys.ITEM_QUALITY).map(lookup -> (RegistryLookup<ItemQuality>) lookup);
    }

    // What the definition claiming this stack says its own tier is: an artifact, the sigil written on a talisman
    // carrier, or the technique it teaches.
    private static Optional<Holder<ItemQuality>> definitionDefault(Provider access, ItemStack stack) {
        Optional<Holder<ItemQuality>> artifact = ArtifactService.definition(access, stack)
                .flatMap(holder -> holder.value().quality());
        if (artifact.isPresent()) return artifact;
        Optional<Holder<ItemQuality>> talisman = TalismanService.quality(stack);
        if (talisman.isPresent()) return talisman;
        return ItemBindingService.technique(access, stack)
                .flatMap(binding -> binding.technique().value().quality());
    }

    // The entry tier of the ladder the stack's own tier sits on, which is where a stack with no tier of its own
    // starts. A stack carrying nothing, or carrying a tier on no ladder, has no such slot.
    private static Optional<Holder<ItemQuality>> ladderDefault(Provider access, @Nullable Holder<ItemQuality> tier) {
        return ladder(access, tier).flatMap(ladder -> Optional.of(ladder.first()));
    }

    // The ladder a tier sits on, which is also the ladder a stack carrying it is read on.
    public static Optional<QualityLadders.Ladder> ladder(Provider access, @Nullable Holder<ItemQuality> tier) {
        return QualityLadders.of(access, tier);
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
         * The item's resolved quality is missing, or is not a tier of the ladder the stack reads.
         */
        QUALITY_CHAIN
    }

    public static boolean canUse(LivingEntity user, ItemStack stack) {
        return canUse(user, stack, ItemBindingService.resolve(user.level().registryAccess(), stack));
    }

    static boolean canUse(LivingEntity user, ItemStack stack, ResolvedBindings bindings) {
        return check(user.level().registryAccess(), user, stack, bindings).isEmpty();
    }

    static boolean canUse(Provider access, LivingEntity user, ItemStack stack) {
        return check(access, user, stack).isEmpty();
    }

    // The reason canUse() would refuse this item, empty while the item is usable.
    public static Optional<Failure> check(LivingEntity user, ItemStack stack) {
        return check(user, stack, ItemBindingService.resolve(user.level().registryAccess(), stack));
    }

    static Optional<Failure> check(LivingEntity user, ItemStack stack, ResolvedBindings bindings) {
        return check(user.level().registryAccess(), user, stack, bindings);
    }

    static Optional<Failure> check(Provider access, LivingEntity user, ItemStack stack, ResolvedBindings bindings) {
        if (stack.isEmpty()) return Optional.empty();
        FormulaContext context = FormulaContext.of(user);
        if (!bindings.conditionsMet(user, context)) return Optional.of(Failure.BINDING_CONDITIONS);
        Optional<RegistryLookup<ItemQuality>> registry = registry(access);
        Optional<Holder<ItemQuality>> quality = find(registry.orElse(null), stack, bindings, access);
        if (quality.isPresent() && !quality.orElseThrow().value().condition().test(user, context))
            return Optional.of(Failure.QUALITY_CONDITIONS);
        // The ladder this stack's tier sits on is a membership gate: a tier that does not walk into it cannot be
        // used.
        return QualityLadders.of(access, quality.orElse(null))
                .filter(ladder -> !ladder.isMember(quality.orElse(null)))
                .map(ladder -> Failure.QUALITY_CHAIN);
    }

    static Optional<Failure> check(Provider access, LivingEntity user, ItemStack stack) {
        return check(access, user, stack, ItemBindingService.resolve(access, stack));
    }

    public static void set(ItemStack stack, Holder<ItemQuality> quality) {
        stack.set(MxtDataComponents.QUALITY.get(), quality);
    }

    public static void clear(ItemStack stack) {
        stack.remove(MxtDataComponents.QUALITY.get());
    }

    // Whether the stack carries the component, which is a different question from whether a tier resolves for it:
    // an item may show a definition's default without anything written on it.
    public static boolean hasOverride(ItemStack stack) {
        return stack.get(MxtDataComponents.QUALITY.get()) != null;
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

    /**
     * Returns the qualities in the explicit tooltip-order tag, then all remaining entries.
     */
    // The colour the pack gave this tier, applied to whatever text names it. A quality without one leaves the
    // text exactly as it was, because a tint is something a pack opts into rather than a default to fall back to.
    public static Component coloredName(Holder<ItemQuality> quality, Component text) {
        Optional<Integer> color = quality.value().color();
        return color.isEmpty() ? text
                : text.copy().withStyle(style -> style.withColor(TextColor.fromRgb(color.orElseThrow())));
    }

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
        registry.get(ItemQualityTags.TOOLTIP_ORDER).ifPresent(tag -> tag.forEach(values::add));
        registry.listElements().forEach(values::add);
        return List.copyOf(values);
    }

    // The component and the settlement both sit on the stack, so both are read through the registry the caller
    // already looked up: an id the current pack does not provide resolves to nothing rather than a dead holder.
    private static Optional<Holder<ItemQuality>> intrinsic(@Nullable RegistryLookup<ItemQuality> registry, ItemStack stack) {
        Holder<ItemQuality> direct = stack.get(MxtDataComponents.QUALITY.get());
        if (direct != null)
            return registry == null ? Optional.empty()
                    : direct.unwrapKey().flatMap(registry::get).map(holder -> holder);
        ForgingResultComponent forged = stack.get(MxtDataComponents.FORGING_RESULT);
        if (forged != null) return Optional.of(forged.quality());
        return Optional.empty();
    }
}
