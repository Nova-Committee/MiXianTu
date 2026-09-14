package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueService.Result;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.runtime.item.ItemQualityService.Failure;
import com.iafenvoy.mxt.util.DefinitionText;
import com.mojang.logging.LogUtils;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Stop;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;
import java.util.Optional;

/**
 * Server-side use path for books, jade slips, and other technique items.
 *
 * <p>A matching binding always claims the interaction, but a refusal is reported rather than
 * swallowed: the item gate answers first, then the learning transaction, and whichever one refuses
 * says why on the action bar. That message is the only feedback a player gets, because a rejected
 * learning attempt changes nothing they could observe.</p>
 *
 * <p>On the event path the gate has usually refused already: {@link ItemQualityService} watches the
 * same interaction at a higher priority and cancels it, so the gate check here speaks for direct
 * callers of {@link #use}.</p>
 *
 * <h2>The two ways a manual can teach</h2>
 * <ul>
 *   <li><b>On a click.</b> A binding without {@code learn_time} teaches immediately, and the click is
 *       claimed so nothing else reacts to it. That is all this class has to do.</li>
 *   <li><b>On a hold.</b> A binding with {@code learn_time} runs the vanilla use cycle instead, so the
 *       progress bar, the pose and the cancel-on-release all come from the game rather than from a timer
 *       of our own. Starting that cycle, and the duration and pose it uses, belong to {@code ItemMixin};
 *       learning is read off {@link Finish}, which vanilla fires on the server when the duration runs
 *       out. A release part-way never reaches here, which is what a hold means.</li>
 * </ul>
 *
 * <p>Nothing is written onto the stack for either path, so there is no state for the two sides to
 * disagree about - which is what the previous designs kept getting wrong.</p>
 */
@EventBusSubscriber
public final class TechniqueItemService {
    /**
     * How the last reading gesture ended for each player, for {@code /mxt technique diagnose}.
     *
     * <p>A hold has exactly two endings and they are indistinguishable from the outside: {@link Finish}
     * when the duration runs out, and a release part-way that never reaches this class at all. The second
     * one carries the only number that separates "the player let go early" from "the cycle was cut short
     * by something else" - the ticks that were still left - so it is recorded rather than guessed at.</p>
     */
    private static final Map<UUID, String> LAST_ENDING = new ConcurrentHashMap<>();

    /**
     * The last percentage shown to each player, so the action bar is only written when the number moves.
     *
     * <p>A hold lasts seconds and the message would otherwise be rebuilt and resent twenty times a
     * second for no visible difference.</p>
     */
    private static final Map<UUID, Integer> LAST_PROGRESS = new ConcurrentHashMap<>();

    /**
     * Wall-clock and tick counts for a read in progress.
     *
     * <p>A read is measured in ticks, but the player experiences it in seconds, and the two only agree
     * while the server is keeping up: a server below twenty ticks a second takes longer than its own
     * duration says, while the client's copy of the same timer runs on its own frame rate and finishes
     * on time. That divergence is invisible in the tick numbers and only shows up as a pose that ends
     * before the progress bar does, so the wall clock is measured rather than assumed.</p>
     */
    private static final Map<UUID, HoldTiming> HOLD_TIMING = new ConcurrentHashMap<>();

    private static final Logger LOGGER = LogUtils.getLogger();

    private TechniqueItemService() {
    }

    /**
     * How long a read has been running, in ticks and in nanoseconds.
     */
    private record HoldTiming(long startNanos, int ticks, int firstRemaining, int lastRemaining) {
        static HoldTiming started(int remaining) {
            return new HoldTiming(System.nanoTime(), 1, remaining, remaining);
        }

        HoldTiming ticked(int remaining) {
            return new HoldTiming(this.startNanos, this.ticks + 1, this.firstRemaining, remaining);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemUse(RightClickItem event) {
        if (use(event.getEntity(), event.getEntity().getItemInHand(event.getHand()))) event.setCanceled(true);
    }

    /**
     * Shows how far a hold has come, because nothing else does.
     *
     * <p>The pose an item plays while being used is not a progress readout: the animations this mod
     * allows are fixed motions that repeat, so a player holding a manual can see that <em>something</em>
     * is happening but not whether they are a tenth of the way through or one tick from the end. A
     * duration long enough to read is also long enough to let go early by mistake, and letting go early
     * is a silent cancel by design, so without this the gesture is guesswork.</p>
     */
    @SubscribeEvent
    public static void onUseTick(Tick event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        TechniqueBinding binding = TechniqueHoldLookup.hold(event.getItem());
        if (binding == null) return;
        // Recorded for every entity, not just players: the audit drives a hold on a pig, and this is the
        // measurement that says whether the duration the server actually ran matches the binding.
        int remaining = event.getDuration();
        HOLD_TIMING.merge(entity.getUUID(), HoldTiming.started(remaining),
                (existing, added) -> existing.ticked(remaining));
        if (!(entity instanceof ServerPlayer player)) return;
        int percent = displayPercent(binding.learnTime(), remaining);
        if (LAST_PROGRESS.getOrDefault(player.getUUID(), -1) == percent) return;
        LAST_PROGRESS.put(player.getUUID(), percent);
        player.sendSystemMessage(Component.translatable("actionbar.mxt.technique.holding", bar(percent), percent)
                .withStyle(ChatFormatting.AQUA), true);
    }

    /**
     * How far a hold has come, as a whole percentage of a total duration.
     *
     * <p>Public because it is the one piece of arithmetic here worth pinning down: a zero or negative
     * duration must not divide by zero, and the ends must not read as one percent short or over.</p>
     */
    public static int holdPercent(int total, int remaining) {
        if (total <= 0) return 100;
        return Mth.clamp((total - remaining) * 100 / total, 0, 100);
    }

    /**
     * The percentage to actually show, which is the elapsed fraction except on the last tick.
     *
     * <p>The tick event fires with the duration still to run, before that tick is taken off, so the last
     * one a read ever sees arrives with a single tick left - and no further event follows, because the
     * tick after it completes the read. Reporting that tick honestly gives 98%, which leaves the bar
     * visibly short of the end on every successful read and reads as a read that never finishes. One
     * tick from the end is the end of the bar.</p>
     */
    public static int displayPercent(int total, int remaining) {
        return remaining <= 1 ? 100 : holdPercent(total, remaining);
    }

    /**
     * The period of the repeating use poses. Vanilla's brush sweep loops over this many ticks.
     */
    private static final int POSE_LOOP_TICKS = 10;

    /**
     * A run-out use counter, folded back into the positive range so a repeating pose keeps repeating.
     *
     * <p>Once the client's own count reaches zero the pose would stop, but the read is not over - the
     * server is still counting. Reporting a fixed positive number keeps the pose on screen but freezes
     * it, and a frozen repeating motion looks as broken as a missing one. Reporting an unrelated cycling
     * number is worse: it restarts the motion out of phase, which shows up as a stutter every loop.</p>
     *
     * <p>What is wanted is the count the client would have reached had it kept counting, folded into the
     * range the pose code expects. That is the modulo of the count itself, which lands exactly where the
     * pose left off, so the motion carries on without a seam - and it is never zero, so the condition the
     * pose is drawn under stays satisfied.</p>
     */
    public static int loopingUseRemaining(int remaining) {
        int phase = Math.floorMod(remaining, POSE_LOOP_TICKS);
        return phase == 0 ? POSE_LOOP_TICKS : phase;
    }

    /**
     * A ten-segment bar, kept to plain ASCII so it renders in the default font.
     */
    private static String bar(int percent) {
        int filled = Mth.clamp(percent / 10, 0, 10);
        return "#".repeat(filled) + "-".repeat(10 - filled);
    }

    /**
     * Teaches the technique when a hold runs its full course.
     *
     * <p>The hand stack is the live one; the event's own stack is a copy vanilla makes and discards.</p>
     */
    @SubscribeEvent
    public static void onUseFinish(Finish event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        LAST_PROGRESS.remove(entity.getUUID());
        reportTiming(entity, "finished");
        ItemStack stack = entity.getItemInHand(entity.getUsedItemHand());
        if (stack.isEmpty()) stack = event.getItem();
        Optional<TechniqueBinding> binding = ItemBindingService.technique(stack);
        if (binding.isEmpty()) {
            record(entity, "Finish, but no technique binding resolves for " + stack.getItem());
            return;
        }
        TechniqueBinding value = binding.orElseThrow();
        if (!value.requiresHold()) {
            record(entity, "Finish, but the binding declares no hold");
            return;
        }
        record(entity, "Finish and taught");
        learn(entity, stack, value);
    }

    /**
     * Records the other ending: a hold that was let go before its duration ran out.
     *
     * <p>Only manuals are recorded, so ordinary items releasing does not drown the diagnostic.</p>
     */
    @SubscribeEvent
    public static void onUseStop(Stop event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        LAST_PROGRESS.remove(entity.getUUID());
        reportTiming(entity, "released early");
        if (TechniqueHoldLookup.hold(event.getItem()) == null) return;
        // The bar is left where it stopped otherwise, and an overlay message lingers for seconds after its
        // last update, so a cancelled read would keep showing a half-filled bar as if it were still going.
        if (entity instanceof ServerPlayer player) player.sendSystemMessage(Component.empty(), true);
        record(entity, "Released early with " + entity.getUseItemRemainingTicks() + " of "
                + event.getItem().getUseDuration(entity) + " ticks left");
    }

    /**
     * How the last reading gesture ended for this player, or empty when none has been seen.
     */
    public static Optional<String> lastEnding(Player player) {
        return Optional.ofNullable(LAST_ENDING.get(player.getUUID()));
    }

    private static void record(LivingEntity entity, String ending) {
        LAST_ENDING.put(entity.getUUID(), ending);
    }

    /**
     * Reports how long a read actually took on the server, in ticks and in milliseconds.
     *
     * <p>Milliseconds per tick is the number that matters: fifty is a server keeping up, a hundred is
     * one running at half speed, and half speed is what makes a read that says sixty ticks take six
     * seconds while the client's copy of the same timer finishes in three.</p>
     */
    private static void reportTiming(LivingEntity entity, String ending) {
        HoldTiming timing = HOLD_TIMING.remove(entity.getUUID());
        if (timing == null) return;
        long millis = (System.nanoTime() - timing.startNanos()) / 1_000_000L;
        LOGGER.info("[mxt] read {}: {} server ticks in {} ms ({} ms per tick; 50 ms per tick is 20 tps), "
                        + "first tick reported {} remaining, last reported {}",
                ending, timing.ticks(), millis, timing.ticks() == 0 ? 0 : millis / timing.ticks(),
                timing.firstRemaining(), timing.lastRemaining());
    }

    /**
     * Attempts to learn a matching technique. A matching binding claims the
     * interaction even when learning is rejected by its normal conditions.
     *
     * <p>A binding that asks for a hold is the one exception: it answers {@code false} so the caller
     * leaves the click alone, because the click is what lets {@code ItemMixin} start the use cycle the
     * hold is measured by. Cancelling there would consume the interaction and no hold would ever
     * begin.</p>
     */
    public static boolean use(LivingEntity entity, ItemStack stack) {
        if (entity.level().isClientSide()) return false;
        Optional<TechniqueBinding> binding = ItemBindingService.technique(stack);
        if (binding.isEmpty()) return false;
        TechniqueBinding value = binding.orElseThrow();
        Optional<Failure> refused = ItemQualityService.check(entity, stack);
        if (refused.isPresent()) {
            ItemQualityService.notifyCannotUse(entity, refused.orElseThrow());
            return true;
        }
        // A binding that asks for a hold must not also teach on the click that starts it, or the hold
        // would be decoration on top of an instant learn.
        if (value.requiresHold()) return false;
        learn(entity, stack, value);
        return true;
    }

    private static void learn(LivingEntity entity, ItemStack stack, TechniqueBinding binding) {
        SpiritIdentityAttachment spirit = entity.getData(MxtAttachments.SPIRIT_IDENTITY);
        Result result = TechniqueService.learn(entity, spirit, binding.technique(), FormulaContext.of(entity));
        // Every completed read pays, not only the ones that teach. A refused read is still a read: the
        // player spent the hold on it, and charging nothing for a refusal makes a manual that cannot be
        // learned yet free to spam. Applied before the outcome is looked at, so the two cases cannot
        // drift apart again.
        applyCooldown(entity, stack);
        if (!result.learned()) {
            notifyLearnFailure(entity, binding.technique(), result);
            return;
        }
        notifyLearned(entity, binding.technique());
    }

    /**
     * Adds the success half of the learning feedback.
     *
     * <p>Without this the only message a player could ever see was a refusal, so a technique that was
     * actually learned looked exactly like nothing happening. Failures are also sent to the action bar,
     * so success uses the same channel for the same reason: it is transient state, not a record.</p>
     */
    private static void notifyLearned(LivingEntity entity, Holder<CultivationTechnique> technique) {
        if (!(entity instanceof ServerPlayer player)) return;
        player.sendSystemMessage(Component.translatable("actionbar.mxt.technique.learned",
                DefinitionText.name(technique, "cultivation_technique")).withStyle(ChatFormatting.GREEN), true);
    }

    /**
     * Puts the item on cooldown after a completed read, whatever that read produced.
     *
     * <p>The cooldown is not a penalty for failing, it is the price of the attempt: what it exists to
     * stop is a manual being read over and over, and a refusal that costs nothing leaves exactly that
     * loop open for any technique the holder cannot learn yet. So it is charged on the attempt rather
     * than on the result.</p>
     *
     * <p>Vanilla's own tracker is used rather than a component, which means the grey sweep on the hotbar
     * and the {@code mxt:on_cooldown} item condition both see it for free.</p>
     */
    private static void applyCooldown(LivingEntity entity, ItemStack stack) {
        int ticks = MxtServerConfig.techniqueLearnCooldown();
        if (ticks <= 0 || stack.isEmpty() || !(entity instanceof Player player)) return;
        player.getCooldowns().addCooldown(stack, ticks);
    }

    /**
     * Sends the user-facing reason a learning attempt was rejected.
     */
    private static void notifyLearnFailure(LivingEntity entity, Holder<CultivationTechnique> technique, Result result) {
        if (!(entity instanceof ServerPlayer player) || result.failure() == null) return;
        player.sendSystemMessage(Component.translatable("actionbar.mxt.technique.failed",
                        DefinitionText.name(technique, "cultivation_technique"),
                        Component.translatable("actionbar.mxt.technique.failure." + result.failure().name().toLowerCase(Locale.ROOT)))
                .withStyle(ChatFormatting.RED), true);
    }
}
