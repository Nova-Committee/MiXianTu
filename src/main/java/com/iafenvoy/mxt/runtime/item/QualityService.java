package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.api.QualityProvider;
import com.iafenvoy.mxt.data.quality.DefaultQuality;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.ItemQuality.Modifier;
import com.iafenvoy.mxt.data.quality.ItemQualityTags;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDataMaps;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.item.ItemBindingService.ResolvedBindings;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.HolderLookup.RegistryLookup;
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
import org.jspecify.annotations.Nullable;

import java.util.*;
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
        CARRIERS.add(stack -> {
            C value = stack.get(type.get());
            if (value == null) return Optional.empty();
            Holder<T> holder = extract.apply(value).orElse(null);
            // A holder naming a definition the current pack no longer provides answers nothing rather than a dead
            // tier, so the stack falls through to the table.
            return holder == null || !holder.isBound() ? Optional.empty() : holder.value().defaultQuality();
        });
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
        ItemStack stack = event.getEntity().getItemInHand(event.getHand());
        Optional<Failure> failure = checkForEvent(event.getEntity(), stack);
        if (failure.isPresent()) {
            event.setCanceled(true);
            notifyCannotUse(event.getEntity(), failure.orElseThrow());
            return;
        }
        notifyStillFull(event.getEntity(), stack);
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
            // Cancel returns -1; the following decrement would completeUsingItem unless the live use is cleared.
            event.getEntity().releaseUsingItem();
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

    // The one resolution order, and it is three steps deep: what the stack carries as an explicit tier, else the
    // tier of the definition it carries, else what the table says about that item. Read with a Provider because the
    // component is re-resolved through the registry the caller has.
    public static Optional<Holder<ItemQuality>> find(Provider access, ItemStack stack) {
        return find(registry(access).orElse(null), stack);
    }

    // The same read for a caller that already looked the registry up (the use gate does).
    static Optional<Holder<ItemQuality>> find(@Nullable RegistryLookup<ItemQuality> registry, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return intrinsic(registry, stack)
                .or(() -> carried(stack))
                .or(() -> Optional.ofNullable(stack.getData(MxtDataMaps.DEFAULT_QUALITY)).map(DefaultQuality::quality));
    }

    // The carried source needs no registry: a carrier answers from the holder already on the stack.
    private static Optional<Holder<ItemQuality>> carried(ItemStack stack) {
        for (Function<ItemStack, Optional<Holder<ItemQuality>>> carrier : CARRIERS) {
            Optional<Holder<ItemQuality>> quality = carrier.apply(stack);
            if (quality.isPresent()) return quality;
        }
        return Optional.empty();
    }

    // A registry the client has not been sent is not an error here: the stack simply resolves to whatever the
    // remaining slots answer.
    static Optional<RegistryLookup<ItemQuality>> registry(Provider access) {
        return access.lookup(MxtResourceKeys.ITEM_QUALITY).map(lookup -> (RegistryLookup<ItemQuality>) lookup);
    }

    // Why an entity may not use an item. Furnace quality is one of the checks above; pill caps are extra and
    // never replace them. A named pill that is missing refuses instead of matching another pill.
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
         * The pill binding's use cap has already been reached.
         */
        MAX_USES,
        /**
         * The pill binding is still on cooldown.
         */
        COOLDOWN,
        /**
         * The stack names a pill that is no longer in the registry, and must not fall back to another pill.
         */
        UNBOUND
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
        // A stack naming a pill whose reference lost its value refuses: falling through would eat the item for
        // nothing instead of reporting UNBOUND, which is the one outcome this state must never produce.
        if (bindings.pill().unbound()) return Optional.of(Failure.UNBOUND);
        FormulaContext context = FormulaContext.of(user);
        if (!bindings.conditionsMet(user, context)) return Optional.of(Failure.BINDING_CONDITIONS);
        Optional<RegistryLookup<ItemQuality>> registry = registry(access);
        Optional<Holder<ItemQuality>> quality = find(registry.orElse(null), stack);
        if (quality.isPresent() && !quality.orElseThrow().value().condition().test(user, context))
            return Optional.of(Failure.QUALITY_CONDITIONS);
        return bindings.pill().identity().flatMap(holder -> PillService.usageFailure(user, holder))
                .map(QualityService::fromPill);
    }

    private static Failure fromPill(PillService.Failure failure) {
        return switch (failure) {
            case MAX_USES -> Failure.MAX_USES;
            case COOLDOWN -> Failure.COOLDOWN;
            case UNBOUND -> Failure.UNBOUND;
            case CONDITIONS -> Failure.BINDING_CONDITIONS;
        };
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
    // an item may show a definition's default without anything written on it. A forged piece does carry it, since
    // settlement stamps this same component - so clearing takes a forged tier back to the definition's default.
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

    // How a tier reads wherever one is listed: the pack's name for it, in the colour that same tier gave itself.
    public static Component displayName(Holder<ItemQuality> quality) {
        return coloredName(quality, DefinitionText.name(quality));
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

    // The tier the stack itself carries. With a registry the id is re-resolved, so an id the current pack does not
    // provide answers nothing rather than a dead holder; without one the held holder is the whole answer, which is
    // the same rule attachments follow - a holder outlives the pack that wrote it until the stack is decoded again.
    private static Optional<Holder<ItemQuality>> intrinsic(@Nullable RegistryLookup<ItemQuality> registry, ItemStack stack) {
        Holder<ItemQuality> direct = stack.get(MxtDataComponents.QUALITY.get());
        if (direct == null) return Optional.empty();
        if (registry == null) return direct.isBound() ? Optional.of(direct) : Optional.empty();
        return direct.unwrapKey().flatMap(registry::get).map(holder -> holder);
    }
}
