package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.item.HoldBinding;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueService.Result;
import com.iafenvoy.mxt.runtime.hold.HoldLookup;
import com.iafenvoy.mxt.runtime.hold.HoldService;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.runtime.item.ItemQualityService.Failure;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side use path for books, jade slips and other technique items: a matching binding claims the
 * interaction, and a refusal is reported on the action bar since it changes nothing else. The hold gesture
 * itself belongs to the hold module, which drives it without knowing what a technique is; this class only
 * declares how long a read lasts and answers what only this module can - which of its own holds this item is,
 * how far through it is, what to teach and what to report afterwards. {@link ItemQualityService} watches the
 * same interaction at a higher priority, so the gate check in {@link #use} speaks for direct callers.
 */
@EventBusSubscriber
public final class TechniqueItemService {
    // How the last reading gesture ended, for `/mxt technique diagnose`: a hold has two endings that look
    // identical from outside, and only the ticks still left tell them apart.
    private static final Map<UUID, String> LAST_ENDING = new ConcurrentHashMap<>();

    // The last percentage shown to each player, so the action bar is rewritten only when the number moves.
    private static final Map<UUID, Integer> LAST_PROGRESS = new ConcurrentHashMap<>();

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

    // The whole wiring between the two modules, done once at construction: the hold module drives the gesture
    // and never learns what a technique is, and this module never touches the use cycle. A declaration that claims
    // items is registered as a second hold over them, so those items are read without carrying any component.
    public static void initialize() {
        HoldLookup.register(registries -> {
            List<HoldBinding> holds = new ArrayList<>();
            holds.add(TechniqueHold.INSTANCE);
            MxtDatapackRegistries.holders(registries, MxtResourceKeys.TECHNIQUE_BINDING)
                    .map(Reference::value)
                    .filter(binding -> !binding.entries().isEmpty())
                    .forEach(binding -> holds.add(new TechniqueHold(binding.entries())));
            return List.copyOf(holds);
        });
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
        int percent = HoldService.displayPercent(learnTime(event.getItem()), remaining);
        if (LAST_PROGRESS.getOrDefault(player.getUUID(), -1) == percent) return;
        LAST_PROGRESS.put(player.getUUID(), percent);
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
        // The declaration's own duration rather than the stack's use state: the component carrying the cycle was
        // taken off when the read started, so asking the stack here would report a total of zero.
        record(entity, "Released early with " + entity.getUseItemRemainingTicks() + " of "
                + learnTime(event.getItem()) + " ticks left");
    }

    // How long a read of this stack lasts, which the progress bar and the diagnostic both report.
    private static int learnTime(ItemStack stack) {
        return ItemBindingService.technique(stack).map(TechniqueBinding::learnTime).orElse(0);
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
        LOGGER.info("[mxt] read {}: {} server ticks in {} ms ({} ms per tick; 50 ms per tick is 20 tps), "
                        + "first tick reported {} remaining, last reported {}",
                ending, timing.ticks(), millis, timing.ticks() == 0 ? 0 : millis / timing.ticks(),
                timing.firstRemaining(), timing.lastRemaining());
    }

    // A matching binding claims the interaction even when learning is rejected by its conditions; a binding
    // that asks for a hold answers false so the click falls through to the hold module.
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
        // Every completed read pays, not only the ones that teach: a refusal is still a read the player
        // spent the hold on. Charged before the outcome is looked at so the two cases cannot drift.
        applyCooldown(entity, stack);
        if (!result.learned()) {
            notifyLearnFailure(entity, binding.technique(), result);
            return;
        }
        notifyLearned(entity, binding.technique());
    }

    // Success uses the action bar as the refusals do, because it is transient state.
    private static void notifyLearned(LivingEntity entity, Holder<Technique> technique) {
        if (!(entity instanceof ServerPlayer player)) return;
        player.sendSystemMessage(Component.translatable("actionbar.mxt.technique.learned",
                DefinitionText.name(technique, "technique")).withStyle(ChatFormatting.GREEN), true);
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
                        DefinitionText.name(technique, "technique"),
                        Component.translatable("actionbar.mxt.technique.failure." + result.failure().name().toLowerCase(Locale.ROOT)))
                .withStyle(ChatFormatting.RED), true);
    }
}
