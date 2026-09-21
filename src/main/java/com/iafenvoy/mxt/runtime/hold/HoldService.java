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
 * Drives a hold from end to end for every module that declares one: it makes the item start a use cycle, answers
 * the two questions vanilla asks while the cycle runs, plays the sound, and hands the item back untouched. It is
 * the only place in the mod that writes the vanilla {@code minecraft:consumable} component, and it knows no
 * module's registries - a module plugs in by implementing {@link HoldBinding} and registering a
 * {@link HoldSource}.
 * <p>
 * Nothing here consumes an item. The component is taken back off the moment the cycle has started, which is
 * after the duration was read, so {@code Item.finishUsingItem} finds nothing to eat. A module that wants to know
 * how a hold ended subscribes to {@code LivingEntityUseItemEvent.Finish} and {@code Stop} itself and asks
 * {@link HoldLookup} whether the item is one of its own.
 * <p>
 * Every question is asked through the declaration, including "is this stack one of yours" - see
 * {@link HoldBinding#claims(Provider, ItemStack)} - because a declaration may drive only some of the stacks that
 * match it. Nothing is written for a stack the declaration does not claim, and a stack that carries a use
 * component of its own is never written over: that component is what makes the item edible, drinkable or
 * throwable, and replacing it would silently cancel the item's own use.
 */
@EventBusSubscriber
public final class HoldService {
    /**
     * Silence, for the server's copy of the component. Both copies would otherwise make the same sound and the
     * reader would hear it twice.
     */
    private static final Holder<SoundEvent> SILENT_SOUND = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY);

    /**
     * How loud the sound is for the players around the reader, and how far its pitch wanders. Ours rather than
     * vanilla's: reaching for vanilla's own volume and pitch would mean copying the private randomisation inside
     * {@code Consumable.emitParticlesAndSounds}, and the shape it draws the pitch from is reproducible with
     * {@link net.minecraft.util.RandomSource#triangle}. A wandering pitch matters because the sound repeats.
     */
    private static final float SOUND_VOLUME = 1.0F;
    private static final float SOUND_PITCH_SPREAD = 0.2F;

    private HoldService() {
    }

    /**
     * Arms the stack a click is about to start a cycle on. Runs last of all: a module whose own gate refuses the
     * click cancels the event before this, and a cancelled event does not reach a handler that did not ask for
     * cancelled ones - so nothing is armed for a click somebody else claimed, and no component is left on a stack
     * nothing will ever come back for.
     * <p>
     * A sneaking click is never a hold: shift is how a player asks an item for its <em>other</em> behaviour, and a
     * hold that armed itself here would swallow that click - the item's own {@code use} is not reached while a
     * cycle is running, so the only way to let it answer is not to start one. Sneaking is therefore decided here
     * rather than in the hold's own {@code claims}: it belongs to the click, and a declaration that answered it
     * would have to know about a verb it does not own.
     * <p>
     * Both sides write it, because each answers its own copy of the cycle; writing it on the server alone is what
     * broke the first attempt at this, where the client ran a zero-length, animation-less cycle. Both sides ask
     * the same questions of the same registry view, so a stack one of them declines is declined by the other.
     */
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

    /**
     * Takes the component back off once the cycle has started. The duration was read a line earlier, so the count
     * is already right, and from here the component could only let {@code Consumable.onConsume} eat the item,
     * award the item-used stat and fire the eat game event.
     * <p>
     * Only the server's copy is touched - the client needs its own for the pose - and only what this module put
     * there. The component is the one every vanilla consumable uses, so taking it off anything else would stop
     * that item from being used up at all: food would never feed anybody again.
     */
    @SubscribeEvent
    public static void onUseStart(Start event) {
        if (event.getEntity().level().isClientSide()) return;
        ItemStack stack = event.getItem();
        HoldBinding hold = HoldLookup.hold(stack);
        if (hold == null || !ours(stack, hold)) return;
        stack.remove(DataComponents.CONSUMABLE);
    }

    /**
     * Keeps a read alive on the client, and plays its sound on the server.
     */
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

    /**
     * Writes the vanilla component the use cycle reads: duration and pose both come from it, so a hold needs no
     * subclass and {@code Item} needs no patch. This is the only thing ever written onto the stack, and the two
     * sides want different sounds in it - see {@link #armQuietly}.
     * <p>
     * This is the reader's copy, so it carries the declaration's own sound: vanilla's own emitter plays a
     * consumable's sound off the stack during the hold, and the reader's client is the one place that can be
     * relied on to hear it.
     */
    public static void arm(Provider registries, LivingEntity holder, ItemStack stack, HoldBinding hold) {
        arm(registries, holder, stack, hold, hold.holdSound());
    }

    /**
     * The server's copy of the same component, which only ever has to answer the duration and the pose: it is
     * taken off again the moment the cycle starts, and the sound for everyone else is played by the server itself
     * (see {@link #playHoldSound}) rather than from a stack's component.
     */
    public static void armQuietly(Provider registries, LivingEntity holder, ItemStack stack, HoldBinding hold) {
        arm(registries, holder, stack, hold, SILENT_SOUND);
    }

    private static void arm(Provider registries, LivingEntity holder, ItemStack stack, HoldBinding hold, Holder<SoundEvent> sound) {
        // The same refusal the click path makes, kept here as well so the one writer in this class cannot be used
        // to take an item's own use away from it: see {@link #ours}.
        if (stack.has(DataComponents.CONSUMABLE) && !ours(stack, hold)) return;
        stack.set(DataComponents.CONSUMABLE, consumable(hold, sound, hold.holdTicks(holder, registries, stack)));
    }

    private static Consumable consumable(HoldBinding hold, Holder<SoundEvent> sound, int ticks) {
        // The particle flag is off because the component's own emitter also throws item particles, which none of
        // the poses want.
        return new Consumable(consumeSeconds(ticks), hold.holdAnimation(), sound, false, List.of());
    }

    /**
     * Whether the use component on this stack is the one this module writes for this declaration. An item's own
     * component - food, a potion - is not, and is left exactly as it was found: reading is what distinguishes
     * the two, since nothing about the stack records who wrote it.
     * <p>
     * The duration is deliberately not part of the comparison: a declaration may size the component from the
     * stack, and a stack whose count changes mid-read would otherwise stop looking like ours.
     */
    private static boolean ours(ItemStack stack, HoldBinding hold) {
        Consumable component = stack.get(DataComponents.CONSUMABLE);
        if (component == null) return false;
        return !component.hasConsumeParticles() && component.onConsumeEffects().isEmpty()
                && component.animation() == hold.holdAnimation()
                && (component.sound().value() == SILENT_SOUND.value() || component.sound().value() == hold.holdSound().value());
    }

    /**
     * The component counts in seconds and truncates back to ticks, so a plain division lands a tick short on some
     * durations. Half a tick of slack is orders of magnitude more than the float error and still truncates back
     * to the exact tick count, so the duration reported is the declaration's, to the tick.
     */
    private static float consumeSeconds(int ticks) {
        return (ticks + 0.5F) / 20.0F;
    }

    /**
     * Puts the component back on a stack that is mid-read. The server takes it off its own copy as soon as the
     * cycle starts, and that change can be synchronised back over the client's copy - which is the copy the render
     * loop asks for the duration and the pose. Re-supplying it on every client tick is what keeps the pose alive
     * across that sync; a stack that still has it, or that is no hold, is left alone.
     */
    public static void keepArmed(Provider registries, LivingEntity holder, ItemStack stack) {
        if (stack.isEmpty() || stack.has(DataComponents.CONSUMABLE)) return;
        HoldBinding hold = HoldLookup.hold(stack);
        if (hold != null && hold.claims(holder, registries, stack)) arm(registries, holder, stack, hold);
    }

    /**
     * Plays the hold's sound for the players around the reader, on the ticks vanilla would play it on. Vanilla
     * would play it from the stack's own component, but the server's copy never has one - it is taken off as the
     * cycle starts - so vanilla's own predicate is asked and the play is made here instead.
     * <p>
     * The reader is left out on purpose: their own client plays the same sound off their own copy of the
     * component, and hearing both would double it. Returns whether it played, which is the only way to see the
     * cadence from outside.
     */
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

    /**
     * How far a hold has come, as a whole percentage of a total duration. Public because the edges matter: a
     * non-positive duration must not divide by zero, and the ends must not read one percent off.
     */
    public static int holdPercent(int total, int remaining) {
        if (total <= 0) return 100;
        return Mth.clamp((total - remaining) * 100 / total, 0, 100);
    }

    /**
     * The elapsed fraction, except on the last tick: the tick event fires with one tick still to run, and
     * reporting that honestly would leave a one-percent-short bar on every hold that ran its full course.
     */
    public static int displayPercent(int total, int remaining) {
        return remaining <= 1 ? 100 : holdPercent(total, remaining);
    }
}
