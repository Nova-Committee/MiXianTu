package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.api.QualityProvider;
import com.iafenvoy.mxt.data.quality.DefaultQuality;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.ItemQuality.Modifier;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.item.ItemBindingService.ResolvedBindings;
import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.Failures;
import com.iafenvoy.mxt.runtime.resulting.failures.QualityFailure;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.MiscUtil;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.iafenvoy.mxt.util.profiling.ProfilerScope;
import com.iafenvoy.mxt.util.profiling.ServerProfiling;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The one quality service: what tier a stack reads at, which ladder that tier belongs to, the catalogue the tooltip
 * orders itself by, and the gate that refuses an item whose own tier's condition does not hold. Resolution has three
 * sources, in this order - the {@code mxt:quality} component, which every writer stamps (forging included); the
 * definition the stack itself carries, registered as a carrier below; and the {@code mxt:default_quality} table,
 * which answers for items nothing has stamped. A module that needs more than that (a forging curve, a drawing
 * grade, a herb's potency) settles it inside that module and writes this same component.
 */
@EventBusSubscriber
public final class QualityService {
    // Fixed at class load, so our own carriers come before an addon's. Every lookup walks the whole list, which is
    // why a carrier is a registration rather than a scan of whatever components a stack happens to carry: two
    // providers on one stack would otherwise settle by component iteration order.
    private static final List<Function<ItemStack, Optional<Holder<ItemQuality>>>> CARRIERS = new ArrayList<>();

    static {
        QualityCarriers.register();
    }

    private QualityService() {
    }

    /**
     * Registers a carrier component whose value is the definition itself.
     */
    public static <T extends QualityProvider> void carry(Supplier<? extends DataComponentType<Holder<T>>> type) {
        carry(type, Optional::of);
    }

    /**
     * Registers a carrier component that keeps the definition inside a record of its own.
     */
    public static <C, T extends QualityProvider> void carry(Supplier<? extends DataComponentType<C>> type, Function<C, Optional<Holder<T>>> extract) {
        CARRIERS.add(stack -> Optional.ofNullable(stack.get(type)).flatMap(extract).map(Holder::value).flatMap(QualityProvider::quality));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.ITEM)) {
            Optional<Failure> failure = checkForEvent(event.getEntity(), event.getEntity().getMainHandItem());
            if (failure.isPresent()) {
                event.setCanceled(true);
                notifyCannotUse(event.getEntity(), failure.orElseThrow());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemUse(RightClickItem event) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.ITEM)) {
            ItemStack stack = event.getEntity().getItemInHand(event.getHand());
            Optional<Failure> failure = checkForEvent(event.getEntity(), stack);
            if (failure.isPresent()) {
                event.setCanceled(true);
                notifyCannotUse(event.getEntity(), failure.orElseThrow());
                return;
            }
            notifyStillFull(event.getEntity(), stack);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockUse(RightClickBlock event) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.ITEM)) {
            Optional<Failure> failure = checkForEvent(event.getEntity(), event.getEntity().getItemInHand(event.getHand()));
            if (failure.isPresent()) {
                event.setCanceled(true);
                notifyCannotUse(event.getEntity(), failure.orElseThrow());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseStart(Start event) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.ITEM)) {
            Optional<Failure> failure = checkForEvent(event.getEntity(), event.getItem());
            if (failure.isPresent()) {
                event.setCanceled(true);
                notifyCannotUse(event.getEntity(), failure.orElseThrow());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseTick(Tick event) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.ITEM)) {
            Optional<Failure> failure = checkForEvent(event.getEntity(), event.getItem());
            if (failure.isPresent()) {
                event.setCanceled(true);
                // Cancel returns -1; the following decrement would completeUsingItem unless the live use is cleared.
                event.getEntity().releaseUsingItem();
                notifyCannotUse(event.getEntity(), failure.orElseThrow());
            }
        }
    }

    // Public because an interaction that runs below the gate's own priority still has to report the refusal it
    // sees.
    public static void notifyCannotUse(LivingEntity entity, Failure failure) {
        if (entity instanceof ServerPlayer player)
            player.sendSystemMessage(Component.translatable("actionbar.mxt.item.cannot_use", failure.message())
                    .withStyle(ChatFormatting.RED), true);
    }

    // A pill bound to a food is still that food's own use, and vanilla refuses a food the player is full of below
    // this gate - where the refusal would otherwise be silent. The carrier itself is not a food and never lands here.
    private static void notifyStillFull(LivingEntity entity, ItemStack stack) {
        if (!(entity instanceof ServerPlayer player) || stack.isEmpty()) return;
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null || player.canEat(food.canAlwaysEat())) return;
        if (ItemBindingService.resolvePill(player.level().registryAccess(), stack).effects().isEmpty()) return;
        player.sendSystemMessage(Component.translatable("actionbar.mxt.pill.still_full")
                .withStyle(ChatFormatting.RED), true);
    }

    private static Optional<Failure> checkForEvent(LivingEntity user, ItemStack stack) {
        return check(user.level().registryAccess(), user, stack);
    }

    public static Optional<Holder<ItemQuality>> find(Provider access, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return Optional.ofNullable(stack.get(MxtDataComponents.QUALITY))
                .or(() -> carried(stack))
                .or(() -> ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtResourceKeys.DEFAULT_QUALITY).map(Reference::value), stack).map(DefaultQuality::quality));
    }

    // The carried source needs no registry: a carrier answers from the holder already on the stack.
    private static Optional<Holder<ItemQuality>> carried(ItemStack stack) {
        for (Function<ItemStack, Optional<Holder<ItemQuality>>> carrier : CARRIERS) {
            Optional<Holder<ItemQuality>> quality = carrier.apply(stack);
            if (quality.isPresent()) return quality;
        }
        return Optional.empty();
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
        if (!bindings.conditionsMet(user, context)) return Optional.of(QualityFailure.BINDING_CONDITIONS);
        Optional<Holder<ItemQuality>> quality = find(access, stack);
        if (quality.isPresent() && !quality.orElseThrow().value().condition().test(user, context))
            return Optional.of(QualityFailure.QUALITY_CONDITIONS);
        return bindings.pill().identity().flatMap(holder -> PillService.usageFailure(user, holder)).map(QualityService::fromPill);
    }

    // A pill binding's own refusals are reported as the quality check they stand for; the two it names are reported
    // as themselves, and anything else keeps the generic binding-conditions reason.
    private static Failure fromPill(Failure failure) {
        if (failure == Failures.MAX_USES || failure == Failures.COOLDOWN) return failure;
        return QualityFailure.BINDING_CONDITIONS;
    }

    static Optional<Failure> check(Provider access, LivingEntity user, ItemStack stack) {
        return check(access, user, stack, ItemBindingService.resolve(access, stack));
    }

    public static void set(ItemStack stack, Holder<ItemQuality> quality) {
        stack.set(MxtDataComponents.QUALITY, quality);
    }

    public static void clear(ItemStack stack) {
        stack.remove(MxtDataComponents.QUALITY);
    }

    // Whether the stack carries the component, which is a different question from whether a tier resolves for it:
    // an item may show a definition's default without anything written on it. A forged piece does carry it, since
    // settlement stamps this same component - so clearing takes a forged tier back to the definition's default.
    public static boolean hasOverride(ItemStack stack) {
        return stack.get(MxtDataComponents.QUALITY) != null;
    }

    // The same 1.0 the codec defaults to, so an item whose quality declares no modifier settles exactly as it did
    // before the modifier had a consumer.
    public static final double DEFAULT_MODIFIER = 1.0D;

    // All three modifiers are multipliers, so a zero or negative one could only erase or invert the amount it
    // settles: a broken formula has to leave that amount alone rather than cancel it.
    public static double modifier(Holder<ItemQuality> quality, Function<ItemQuality, Modifier> selector, FormulaContext context) {
        double value = selector.apply(quality.value()).modifier().evaluate(context);
        return MiscUtil.positive(value) > 0.0D ? value : DEFAULT_MODIFIER;
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
        OptionalInt color = quality.value().color();
        return color.isEmpty() ? text
                : text.copy().withStyle(style -> style.withColor(TextColor.fromRgb(color.getAsInt())));
    }

    // How a tier reads wherever one is listed: the pack's name for it in the colour that same tier gave itself, or
    // the tier's id when no pack translated a name, because a listed tier has to say which tier it is.
    public static Component displayName(Holder<ItemQuality> quality) {
        return coloredName(quality, DefinitionText.nameOrId(quality));
    }
}
