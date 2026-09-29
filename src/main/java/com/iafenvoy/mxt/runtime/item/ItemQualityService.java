package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.data.artifact.ForgingResultComponent;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.ItemQuality.Modifier;
import com.iafenvoy.mxt.data.quality.ItemQualityTags;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService;
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

    private static Optional<Failure> checkForEvent(LivingEntity user, ItemStack stack) {
        return check(user.level().registryAccess(), user, stack);
    }

    // The one resolution order: an explicit component, what a settlement wrote, the definition claiming the stack,
    // and last a spirit herb's own declaration. Read with a Provider because the client draws tooltips from the
    // same order.
    public static Optional<Holder<ItemQuality>> find(Provider access, ItemStack stack) {
        return find(registry(access).orElse(null), stack, ItemBindingService.resolve(access, stack), access);
    }

    private static Optional<Holder<ItemQuality>> find(@Nullable RegistryLookup<ItemQuality> registry, ItemStack stack,
                                                      ResolvedBindings bindings, Provider access) {
        Optional<Holder<ItemQuality>> declared = intrinsic(registry, stack);
        return declared
                .or(() -> definitionDefault(access, stack))
                .or(() -> SpiritHerbService.find(access, stack).map(SpiritHerb::quality));
    }

    // A registry the client has not been sent is not an error here: the stack simply resolves to whatever the
    // remaining slots answer.
    private static Optional<RegistryLookup<ItemQuality>> registry(Provider access) {
        return access.lookup(MxtResourceKeys.ITEM_QUALITY).map(lookup -> (RegistryLookup<ItemQuality>) lookup);
    }

    // What the definition claiming this stack says its own tier is: an artifact, the sigil on a talisman,
    // the technique it teaches, then the furnace specification. Quality is not derived from a tier name.
    private static Optional<Holder<ItemQuality>> definitionDefault(Provider access, ItemStack stack) {
        Optional<Holder<ItemQuality>> artifact = ArtifactService.definition(access, stack)
                .flatMap(holder -> holder.value().quality());
        if (artifact.isPresent()) return artifact;
        Optional<Holder<ItemQuality>> talisman = TalismanService.quality(stack);
        if (talisman.isPresent()) return talisman;
        Optional<Holder<ItemQuality>> technique = ItemBindingService.technique(access, stack)
                .flatMap(binding -> binding.technique().value().quality());
        if (technique.isPresent()) return technique;
        return AlchemyWorkstationService.furnaceDefinition(access, stack)
                .map(holder -> holder.value().quality());
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
        Optional<Holder<ItemQuality>> quality = find(registry.orElse(null), stack, bindings, access);
        if (quality.isPresent() && !quality.orElseThrow().value().condition().test(user, context))
            return Optional.of(Failure.QUALITY_CONDITIONS);
        return bindings.pill().identity().flatMap(holder -> PillService.usageFailure(user, holder))
                .map(ItemQualityService::fromPill);
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
