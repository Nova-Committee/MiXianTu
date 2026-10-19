package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.TechniqueAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.data.item.TechniqueReading;
import com.iafenvoy.mxt.event.dispatcher.CollectHoldBindingsEvent;
import com.iafenvoy.mxt.item.ItemReading;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueService.Result;
import com.iafenvoy.mxt.runtime.hold.HoldLookup;
import com.iafenvoy.mxt.runtime.hold.HoldService;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.item.QualityService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Stop;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.iafenvoy.mxt.runtime.failure.Failure;

/**
 * Server-side use path for books, jade slips and other technique items: a matching binding claims the
 * interaction, and a refusal is reported on the action bar since it changes nothing else. The hold gesture
 * itself belongs to the hold module, which drives it without knowing what a technique is; this class is the only
 * one that answers what only this module can - which of its own holds this item is, how far through it is, what to
 * teach and what to report afterwards. {@link QualityService} watches the same interaction at a higher priority,
 * so the gate check in {@link #use} speaks for direct callers.
 */
@EventBusSubscriber
public final class TechniqueItemService {
    // How the last reading gesture ended, for `/mxt technique diagnose`: a hold has two endings that look
    // identical from outside, and only the ticks still left tell them apart.
    private static final Map<UUID, String> LAST_ENDING = new ConcurrentHashMap<>();

    // What each player's action bar last showed, and when: the number moves rarely on a long read, but the client
    // drops an overlay message 60 ticks after it arrives, so the same text is sent again before that happens.
    private static final Map<UUID, ShownProgress> LAST_PROGRESS = new ConcurrentHashMap<>();

    // Comfortably inside the client's 60-tick overlay lifetime.
    private static final int PROGRESS_KEEP_ALIVE_TICKS = 40;

    // Wall-clock and tick counts for a read in progress: a read is measured in ticks but experienced in
    // seconds, and the two agree only while the server keeps up.
    private static final Map<UUID, HoldTiming> HOLD_TIMING = new ConcurrentHashMap<>();

    private static final Logger LOGGER = LogUtils.getLogger();

    private TechniqueItemService() {
    }

    private record HoldTiming(long startNanos, int ticks, int firstRemaining, int lastRemaining) {
        static HoldTiming started(int remaining) {
            return new HoldTiming(System.nanoTime(), 1, remaining, remaining);
        }

        HoldTiming ticked(int remaining) {
            return new HoldTiming(this.startNanos, this.ticks + 1, this.firstRemaining, remaining);
        }
    }

    private record ShownProgress(int percent, long tick) {
    }

    // The hold module drives the gesture and never learns what a technique is, and this module never touches the use
    // cycle. A declaration that claims items is contributed as a second hold over them, so those items are read
    // without carrying any component.
    @SubscribeEvent
    public static void onCollectHolds(CollectHoldBindingsEvent event) {
        event.add(TechniqueHold.INSTANCE);
        MxtDatapackRegistries.holders(event.getRegistries(), MxtResourceKeys.TECHNIQUE_BINDING)
                .map(Reference::value)
                .filter(binding -> !binding.entries().isEmpty())
                .forEach(binding -> event.add(new TechniqueHold(binding.entries())));
    }

    // A binding that asks for a hold answers false, so the click falls through to the hold module, which arms
    // the stack for it.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemUse(RightClickItem event) {
        if (use(event.getEntity(), event.getEntity().getItemInHand(event.getHand()))) event.setCanceled(true);
    }

    // Only this module's own holds get the bar: another module's hold is not a technique being read, and its
    // percentage would be a lie here.
    @SubscribeEvent
    public static void onUseTick(Tick event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(HoldLookup.hold(event.getItem()) instanceof TechniqueHold)) return;
        // Recorded for every entity, not just players: the audit drives a hold on a pig.
        int remaining = event.getDuration();
        HOLD_TIMING.merge(entity.getUUID(), HoldTiming.started(remaining),
                (existing, added) -> existing.ticked(remaining));
        if (!(entity instanceof ServerPlayer player)) return;
        int percent = HoldService.displayPercent(learnTime(entity, event.getItem()), remaining);
        long now = entity.level().getGameTime();
        ShownProgress shown = LAST_PROGRESS.get(player.getUUID());
        if (shown != null && shown.percent() == percent && now - shown.tick() < PROGRESS_KEEP_ALIVE_TICKS) return;
        LAST_PROGRESS.put(player.getUUID(), new ShownProgress(percent, now));
        player.sendSystemMessage(Component.translatable("actionbar.mxt.technique.holding", bar(percent), percent)
                .withStyle(ChatFormatting.AQUA), true);
    }

    private static String bar(int percent) {
        int filled = Mth.clamp(percent / 10, 0, 10);
        return "#".repeat(filled) + "-".repeat(10 - filled);
    }

    // The hand stack is the live one; the event's own stack is a copy vanilla makes and discards.
    @SubscribeEvent
    public static void onUseFinish(Finish event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        LAST_PROGRESS.remove(entity.getUUID());
        reportTiming(entity, "finished");
        ItemStack stack = entity.getItemInHand(entity.getUsedItemHand());
        if (stack.isEmpty()) stack = event.getItem();
        Optional<TechniqueBinding> binding = ItemBindingService.technique(entity.level().registryAccess(), stack);
        if (binding.isEmpty()) {
            record(entity, "Finish, but no technique binding resolves for " + stack.getItem());
            return;
        }
        TechniqueBinding value = binding.orElseThrow();
        if (!requiresHold(entity, stack)) {
            record(entity, "Finish, but the technique declares no hold");
            return;
        }
        record(entity, "Finish and taught");
        learn(entity, stack, value);
    }

    // Records the other ending. Only manuals are recorded, so ordinary items releasing do not drown the
    // diagnostic.
    @SubscribeEvent
    public static void onUseStop(Stop event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        LAST_PROGRESS.remove(entity.getUUID());
        reportTiming(entity, "released early");
        if (!(HoldLookup.hold(event.getItem()) instanceof TechniqueHold)) return;
        // The bar is left where it stopped otherwise, and an overlay message lingers for seconds after its
        // last update, so a cancelled read would keep showing a half-filled bar as if it were still going.
        if (entity instanceof ServerPlayer player) player.sendSystemMessage(Component.empty(), true);
        // The technique's own duration rather than the stack's use state: the component carrying the cycle was
        // taken off when the read started, so asking the stack here would report a total of zero.
        record(entity, "Released early with " + entity.getUseItemRemainingTicks() + " of "
                + learnTime(entity, event.getItem()) + " ticks left");
    }

    // How long a read of this stack lasts, which the progress bar and the diagnostic both report. The length is a
    // formula over the reader, so it is asked again every tick.
    private static int learnTime(LivingEntity entity, ItemStack stack) {
        return reading(entity, stack).map(reading -> reading.holdTicks(FormulaContext.of(entity))).orElse(0);
    }

    // Whether reading this stack is a hold: the technique declares it, and the stack's component may override it.
    private static boolean requiresHold(LivingEntity entity, ItemStack stack) {
        return reading(entity, stack).map(reading -> reading.requiresHold(FormulaContext.of(entity))).orElse(false);
    }

    private static Optional<TechniqueReading> reading(LivingEntity entity, ItemStack stack) {
        return ItemBindingService.reading(entity.level().registryAccess(), stack);
    }

    // Wider than a player because {@link #record} keys any entity that reads, and the audit drives a hold on
    // a pig.
    public static Optional<String> lastEnding(LivingEntity entity) {
        return Optional.ofNullable(LAST_ENDING.get(entity.getUUID()));
    }

    private static void record(LivingEntity entity, String ending) {
        LAST_ENDING.put(entity.getUUID(), ending);
    }

    // Milliseconds per tick is the number that matters: 50 is a server keeping up, 100 is one at half speed.
    private static void reportTiming(LivingEntity entity, String ending) {
        HoldTiming timing = HOLD_TIMING.remove(entity.getUUID());
        if (timing == null) return;
        long millis = (System.nanoTime() - timing.startNanos()) / 1_000_000L;
        LOGGER.debug("[mxt] read {}: {} server ticks in {} ms ({} ms per tick; 50 ms per tick is 20 tps), first tick reported {} remaining, last reported {}",
                ending, timing.ticks(), millis, timing.ticks() == 0 ? 0 : millis / timing.ticks(), timing.firstRemaining(), timing.lastRemaining());
    }

    // A matching binding claims the interaction even when learning is rejected by its conditions; a binding
    // that asks for a hold answers false so the click falls through to the hold module.
    public static boolean use(LivingEntity entity, ItemStack stack) {
        if (entity.level().isClientSide()) return false;
        Optional<TechniqueBinding> binding = ItemBindingService.technique(entity.level().registryAccess(), stack);
        if (binding.isEmpty()) return false;
        TechniqueBinding value = binding.orElseThrow();
        Optional<Failure> refused = QualityService.check(entity, stack);
        if (refused.isPresent()) {
            QualityService.notifyCannotUse(entity, refused.orElseThrow());
            return true;
        }
        // A hold manual must not also teach on the click that starts it, but it still says what it is on that click.
        readOut(entity, stack, value.technique());
        if (requiresHold(entity, stack)) return false;
        learn(entity, stack, value);
        return true;
    }

    // What reading a manual prints: the text written on it, then the technique it names and, when the pack
    // translated one, that technique's description. The chat log, not the action bar: this is the text itself.
    private static void readOut(LivingEntity entity, ItemStack stack, Holder<Technique> technique) {
        if (!(entity instanceof ServerPlayer player)) return;
        ItemReading.readInscription(player, stack);
        player.sendSystemMessage(Component.translatable("message.mxt.technique.read",
                DefinitionText.nameOrId(technique)).withStyle(ChatFormatting.GREEN));
        // What the pack wrote for this manual, drawn only when it translated the generated key: the raw key would
        // otherwise land in the chat log.
        DefinitionText.resolved(DefinitionText.description(technique))
                .ifPresent(text -> player.sendSystemMessage(text.withStyle(ChatFormatting.GRAY)));
    }

    private static void learn(LivingEntity entity, ItemStack stack, TechniqueBinding binding) {
        TechniqueAttachment spirit = entity.getData(MxtAttachments.TECHNIQUE);
        Result result = TechniqueService.learn(entity, spirit, binding.technique(), FormulaContext.of(entity));
        // Every completed read pays, not only the ones that teach: a refusal is still a read the player
        // spent the hold on. Charged before the outcome is looked at so the two cases cannot drift.
        applyCooldown(entity, stack);
        if (!result.changed()) {
            notifyLearnFailure(entity, binding.technique(), result);
            return;
        }
        notifyLearned(entity, binding.technique());
    }

    // Success uses the action bar as the refusals do, because it is transient state.
    private static void notifyLearned(LivingEntity entity, Holder<Technique> technique) {
        if (!(entity instanceof ServerPlayer player)) return;
        player.sendSystemMessage(Component.translatable("actionbar.mxt.technique.learned",
                DefinitionText.nameOrId(technique)).withStyle(ChatFormatting.GREEN), true);
    }

    /**
     * Vanilla's own tracker is used rather than a component, so the hotbar sweep and the
     * {@code mxt:on_cooldown} item condition both see it for free.
     */
    private static void applyCooldown(LivingEntity entity, ItemStack stack) {
        int ticks = MxtServerConfig.INSTANCE.cultivation.techniqueLearnCooldown.getValue();
        if (ticks <= 0 || stack.isEmpty() || !(entity instanceof Player player)) return;
        player.getCooldowns().addCooldown(stack, ticks);
    }

    private static void notifyLearnFailure(LivingEntity entity, Holder<Technique> technique, Result result) {
        if (!(entity instanceof ServerPlayer player) || result.failure() == null) return;
        player.sendSystemMessage(Component.translatable("actionbar.mxt.technique.failed",
                        DefinitionText.nameOrId(technique),
                        result.failure().message())
                .withStyle(ChatFormatting.RED), true);
    }
}
