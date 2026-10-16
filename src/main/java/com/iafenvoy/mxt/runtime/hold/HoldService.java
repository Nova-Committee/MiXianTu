package com.iafenvoy.mxt.runtime.hold;

import com.iafenvoy.mxt.data.item.HoldBinding;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Drives a hold end to end for every module that declares one: arms the use cycle, answers what vanilla asks
 * while it runs, plays the sound and hands the item back untouched. A module plugs in by implementing
 * {@link HoldBinding} and registering a {@link HoldSource}; this class knows no module's registries.
 *
 * <p>It writes the vanilla {@code minecraft:consumable} component for a hold - {@code PillUseService} writes one for
 * a bound pill whose item has no use of its own, and both go through {@link TemporaryUseComponent} - and it takes
 * that component back off the moment the cycle has started (after the duration was read), so
 * {@code Item.finishUsingItem} finds nothing to eat. A stack carrying a use component of its own is never written
 * over: that component is what makes the item edible, drinkable or throwable.
 *
 * <p>The component it writes is silent, and this class plays the sound itself: vanilla sounds a component only after
 * 21.875% of its duration has run out, which is days away for a read long enough to be a joke.
 */
@EventBusSubscriber
public final class HoldService {
    // Nothing comes off the component: it is written to answer the duration and the pose, and both copies stay
    // silent because playHoldSound owns the sound.
    private static final Holder<SoundEvent> SILENT_SOUND = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY);

    // Ours rather than vanilla's fraction of the duration, so the cadence does not depend on how long a hold is;
    // the first sound lands one interval in.
    private static final int SOUND_INTERVAL_TICKS = 4;

    // Ours rather than vanilla's: reaching for vanilla's volume and pitch would mean copying the private
    // randomisation inside Consumable.emitParticlesAndSounds. The pitch wanders because the sound repeats.
    private static final float SOUND_VOLUME = 1.0F;
    private static final float SOUND_PITCH_SPREAD = 0.2F;

    private HoldService() {
    }

    // Runs last of all: a module whose own gate refuses the click has cancelled the event by then, so nothing is
    // armed for a click somebody else claimed and no component is left on a stack nothing will come back for.
    // A sneaking click is never a hold - shift asks an item for its *other* behaviour, and a cycle would swallow
    // that click. Both sides write it, because each answers its own copy of the cycle.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemUse(RightClickItem event) {
        LivingEntity entity = event.getEntity();
        if (entity.isShiftKeyDown()) return;
        ItemStack stack = entity.getItemInHand(event.getHand());
        HoldBinding hold = HoldLookup.hold(stack);
        if (hold == null) return;
        Provider registries = entity.level().registryAccess();
        // The holder is part of the question: a declaration may refuse a stack for one reader and take it for
        // another, and a refusal here is what lets the item answer the click itself.
        if (!hold.claims(entity, registries, stack)) return;
        // An item that declares its own use keeps it. Nothing is armed here, which also means nothing is taken
        // off it later: the hold module only ever takes back the component it wrote itself.
        if (stack.has(DataComponents.CONSUMABLE) && !ours(stack, registries, hold)) return;
        arm(registries, entity, stack, hold);
    }

    // Only the server's copy is touched (the client needs its own for the pose), and only the component this module
    // wrote: taking the component off anything else would stop that item from being used up at all.
    @SubscribeEvent
    public static void onUseStart(Start event) {
        if (event.getEntity().level().isClientSide()) return;
        TemporaryUseComponent.takeBack(event.getEntity(), TemporaryUseComponent.Owner.HOLD);
    }

    // Keeps a read alive on the client, plays its sound on the server.
    @SubscribeEvent
    public static void onUseTick(Tick event) {
        LivingEntity entity = event.getEntity();
        Provider registries = entity.level().registryAccess();
        if (entity.level().isClientSide()) {
            // Two stacks can carry the read on this side: the entity's own use stack, and whatever the hand slot
            // holds now - a slot sync can replace the latter mid-read, and the pose is drawn from it.
            keepArmed(registries, entity, event.getItem());
            keepArmed(registries, entity, entity.getItemInHand(entity.getUsedItemHand()));
            // The reader hears their own sound from here, where it lines up with the pose their client is drawing;
            // the server's broadcast below leaves them out so nobody hears it twice. Another player's read is only
            // ever heard from that broadcast, since their stack is re-armed on this client too.
            if (entity instanceof Player player && player.isLocalPlayer()) {
                ItemStack stack = event.getItem();
                HoldBinding hold = HoldLookup.hold(stack);
                if (hold != null && hold.claims(entity, registries, stack))
                    playHoldSound(entity, hold, stack, event.getDuration(), null);
            }
            return;
        }
        ItemStack stack = event.getItem();
        HoldBinding hold = HoldLookup.hold(stack);
        if (hold == null) return;
        // A stack that still carries a use component on this side is one this module did not arm - it was never
        // claimed, or it declares its own use - and it is not a hold of ours to make a sound for.
        if (stack.has(DataComponents.CONSUMABLE)) return;
        playHoldSound(entity, hold, stack, event.getDuration(), entity instanceof Player reader ? reader : null);
    }

    // The vanilla component the use cycle reads: duration and pose both come from it, so a hold needs no Item
    // subclass. It is silent on purpose, and the particle flag is off because the component's own emitter also
    // throws item particles, which none of the poses want.
    public static void arm(Provider registries, LivingEntity holder, ItemStack stack, HoldBinding hold) {
        // The same refusal the click path makes, so the hold module cannot take an item's own use away from it.
        if (stack.has(DataComponents.CONSUMABLE) && !ours(stack, registries, hold)) return;
        TemporaryUseComponent.write(holder, stack,
                new Consumable(consumeSeconds(hold.holdTicks(holder, registries, stack)),
                        hold.holdAnimation(registries, stack), SILENT_SOUND, false, List.of()),
                TemporaryUseComponent.Owner.HOLD);
    }

    // An item's own component - food, a potion - is not ours, and is left exactly as found: reading it is what
    // distinguishes the two, since nothing on the stack records who wrote it. The duration is deliberately not
    // compared: a declaration may size the component from the stack, whose count can change mid-read.
    private static boolean ours(ItemStack stack, Provider registries, HoldBinding hold) {
        Consumable component = stack.get(DataComponents.CONSUMABLE);
        if (component == null) return false;
        return !component.hasConsumeParticles() && component.onConsumeEffects().isEmpty()
                && Objects.equals(component.sound(), SILENT_SOUND)
                && component.animation() == hold.holdAnimation(registries, stack);
    }

    // The component counts in seconds and truncates back to ticks, so a plain division lands a tick short on some
    // durations; half a tick of slack is far more than the float error and still truncates back exactly.
    private static float consumeSeconds(int ticks) {
        return (ticks + 0.5F) / 20.0F;
    }

    // The server takes the component off its own copy as the cycle starts, and that change can be synced back over
    // the client's copy - which is the copy the render loop asks for the duration and the pose. Re-supplying it
    // every client tick keeps the pose alive across that sync.
    public static void keepArmed(Provider registries, LivingEntity holder, ItemStack stack) {
        if (stack.isEmpty() || stack.has(DataComponents.CONSUMABLE)) return;
        HoldBinding hold = HoldLookup.hold(stack);
        if (hold != null && hold.claims(holder, registries, stack)) arm(registries, holder, stack, hold);
    }

    // On an interval of ours rather than vanilla's wait, and played from here rather than off the component, which
    // is silent: the reader is left out on purpose, because their own client plays the same sound in step with the
    // pose it is drawing. Returns whether it played.
    public static boolean playHoldSound(LivingEntity entity, HoldBinding hold, ItemStack stack, int remaining,
                                        @Nullable Player except) {
        Provider registries = entity.level().registryAccess();
        int elapsed = hold.holdTicks(entity, registries, stack) - remaining;
        if (elapsed <= 0 || elapsed % SOUND_INTERVAL_TICKS != 0) return false;
        Holder<SoundEvent> sound = hold.holdSound(registries, stack);
        entity.level().playSound(except, entity.getX(), entity.getY(), entity.getZ(), sound.value(),
                entity.getSoundSource(), SOUND_VOLUME, entity.getRandom().triangle(1.0F, SOUND_PITCH_SPREAD));
        return true;
    }

    // Public because the edges matter: a non-positive duration must not divide by zero, and the ends must not read
    // one percent off. The product is widened because a duration has no ceiling: ticks * 100 overflows an int after
    // about two weeks of holding, and the bar would go backwards there.
    public static int holdPercent(int total, int remaining) {
        if (total <= 0) return 100;
        return (int) Mth.clamp((total - remaining) * 100L / total, 0L, 100L);
    }

    // The tick event fires with one tick still to run, and reporting that honestly would leave a one-percent-short
    // bar on every hold that ran its full course.
    public static int displayPercent(int total, int remaining) {
        return remaining <= 1 ? 100 : holdPercent(total, remaining);
    }
}
