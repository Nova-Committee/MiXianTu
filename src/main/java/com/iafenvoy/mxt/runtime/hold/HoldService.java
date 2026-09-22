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

import java.util.List;

/**
 * Drives a hold end to end for every module that declares one: arms the use cycle, answers what vanilla asks
 * while it runs, plays the sound and hands the item back untouched. A module plugs in by implementing
 * {@link HoldBinding} and registering a {@link HoldSource}; this class knows no module's registries.
 *
 * <p>It is the only place in the mod that writes the vanilla {@code minecraft:consumable} component, and it takes
 * that component back off the moment the cycle has started (after the duration was read), so
 * {@code Item.finishUsingItem} finds nothing to eat. A stack carrying a use component of its own is never written
 * over: that component is what makes the item edible, drinkable or throwable.
 */
@EventBusSubscriber
public final class HoldService {
    // The server's copy only answers "how long" and "which pose" before it is taken off again, so it stays quiet:
    // both copies making the sound would be heard twice.
    private static final Holder<SoundEvent> SILENT_SOUND = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY);

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
        if (stack.has(DataComponents.CONSUMABLE) && !ours(stack, hold)) return;
        // The reader's copy is the one that makes the sound, so it carries the declaration's own; the server's
        // copy only has to answer "how long" and "which pose" before it is taken off again, so it stays quiet.
        if (entity.level().isClientSide()) arm(registries, entity, stack, hold);
        else armQuietly(registries, entity, stack, hold);
    }

    // Only the server's copy is touched (the client needs its own for the pose), and only what this module put
    // there: the component is the one every vanilla consumable uses, so taking it off anything else would stop
    // that item from being used up at all.
    @SubscribeEvent
    public static void onUseStart(Start event) {
        if (event.getEntity().level().isClientSide()) return;
        ItemStack stack = event.getItem();
        HoldBinding hold = HoldLookup.hold(stack);
        if (hold == null || !ours(stack, hold)) return;
        stack.remove(DataComponents.CONSUMABLE);
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
            return;
        }
        ItemStack stack = event.getItem();
        HoldBinding hold = HoldLookup.hold(stack);
        if (hold == null) return;
        // A stack that still carries a use component on this side is one this module did not arm - it was never
        // claimed, or it declares its own use - and it is not a hold of ours to make a sound for.
        if (stack.has(DataComponents.CONSUMABLE)) return;
        // The sound belongs to the read rather than to the reader: played from here so the server plays it for
        // the players around the reader, since the server's own stack carries no component to play it from.
        playHoldSound(entity, hold, stack, event.getDuration());
    }

    // The vanilla component the use cycle reads: duration and pose both come from it, so a hold needs no Item
    // subclass. This is the reader's copy, so it carries the declaration's own sound - the reader's client is the
    // one place that can be relied on to hear it.
    public static void arm(Provider registries, LivingEntity holder, ItemStack stack, HoldBinding hold) {
        arm(registries, holder, stack, hold, hold.holdSound());
    }

    // The server's copy only has to answer the duration and the pose; the sound for everyone else is played by the
    // server itself (playHoldSound) rather than from a stack's component.
    public static void armQuietly(Provider registries, LivingEntity holder, ItemStack stack, HoldBinding hold) {
        arm(registries, holder, stack, hold, SILENT_SOUND);
    }

    private static void arm(Provider registries, LivingEntity holder, ItemStack stack, HoldBinding hold, Holder<SoundEvent> sound) {
        // The same refusal the click path makes, so the one writer in this class cannot take an item's own use
        // away from it.
        if (stack.has(DataComponents.CONSUMABLE) && !ours(stack, hold)) return;
        stack.set(DataComponents.CONSUMABLE, consumable(hold, sound, hold.holdTicks(holder, registries, stack)));
    }

    private static Consumable consumable(HoldBinding hold, Holder<SoundEvent> sound, int ticks) {
        // The particle flag is off because the component's own emitter also throws item particles, which none of
        // the poses want.
        return new Consumable(consumeSeconds(ticks), hold.holdAnimation(), sound, false, List.of());
    }

    // An item's own component - food, a potion - is not ours, and is left exactly as found: reading it is what
    // distinguishes the two, since nothing on the stack records who wrote it. The duration is deliberately not
    // compared: a declaration may size the component from the stack, whose count can change mid-read.
    private static boolean ours(ItemStack stack, HoldBinding hold) {
        Consumable component = stack.get(DataComponents.CONSUMABLE);
        if (component == null) return false;
        return !component.hasConsumeParticles() && component.onConsumeEffects().isEmpty()
                && component.animation() == hold.holdAnimation()
                && (component.sound().value() == SILENT_SOUND.value() || component.sound().value() == hold.holdSound().value());
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

    // On the ticks vanilla would play it. Vanilla would play it from the stack's own component, but the server's
    // copy never has one, so vanilla's own predicate is asked and the play is made here. The reader is left out on
    // purpose: their client plays the same sound off their own copy. Returns whether it played.
    public static boolean playHoldSound(LivingEntity entity, HoldBinding hold, ItemStack stack, int remaining) {
        Holder<SoundEvent> sound = hold.holdSound();
        int ticks = hold.holdTicks(entity, entity.level().registryAccess(), stack);
        Consumable emitter = consumable(hold, sound, ticks);
        if (!emitter.shouldEmitParticlesAndSounds(remaining)) return false;
        entity.level().playSound(entity instanceof Player reader ? reader : null,
                entity.getX(), entity.getY(), entity.getZ(), sound.value(), entity.getSoundSource(),
                SOUND_VOLUME, entity.getRandom().triangle(1.0F, SOUND_PITCH_SPREAD));
        return true;
    }

    // Public because the edges matter: a non-positive duration must not divide by zero, and the ends must not read
    // one percent off.
    public static int holdPercent(int total, int remaining) {
        if (total <= 0) return 100;
        return Mth.clamp((total - remaining) * 100 / total, 0, 100);
    }

    // The tick event fires with one tick still to run, and reporting that honestly would leave a one-percent-short
    // bar on every hold that ran its full course.
    public static int displayPercent(int total, int remaining) {
        return remaining <= 1 ? 100 : holdPercent(total, remaining);
    }
}
