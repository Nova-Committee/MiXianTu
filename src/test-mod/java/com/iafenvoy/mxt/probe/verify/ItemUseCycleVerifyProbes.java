package com.iafenvoy.mxt.probe.verify;

import com.google.gson.JsonParser;
import com.iafenvoy.mxt.api.ItemUseCycle;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.item.PillComponent;
import com.iafenvoy.mxt.event.ResolveItemUseCycleEvent;
import com.iafenvoy.mxt.item.JadeSlipItem;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.hold.ItemUseCycles;
import com.iafenvoy.mxt.runtime.item.PillService;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class ItemUseCycleVerifyProbes {
    private ItemUseCycleVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/item_use_cycle/hold_pill", ItemUseCycleVerifyProbes::holdPill),
                Probe.of("verify/item_use_cycle/plain_pill", ItemUseCycleVerifyProbes::plainPill),
                Probe.of("verify/item_use_cycle/native_creative", ItemUseCycleVerifyProbes::nativeCreative),
                Probe.of("verify/item_use_cycle/native_components", ItemUseCycleVerifyProbes::nativeComponents),
                Probe.of("verify/item_use_cycle/copies_save", ItemUseCycleVerifyProbes::copiesSave),
                Probe.of("verify/item_use_cycle/drop_transfer", ItemUseCycleVerifyProbes::dropTransfer),
                Probe.of("verify/item_use_cycle/lifecycle", ItemUseCycleVerifyProbes::lifecycle),
                Probe.of("verify/item_use_cycle/rejected_start", ItemUseCycleVerifyProbes::rejectedStart),
                Probe.of("verify/item_use_cycle/replacement", ItemUseCycleVerifyProbes::replacement),
                Probe.of("verify/item_use_cycle/slot_sync", ItemUseCycleVerifyProbes::slotSync),
                Probe.of("verify/item_use_cycle/extension", ItemUseCycleVerifyProbes::extension),
                Probe.of("verify/item_use_cycle/finish_failure", ItemUseCycleVerifyProbes::finishFailure));
    }

    private static ItemStack dose(ProbeContext context, ItemStack stack) {
        stack.set(MxtDataComponents.PILL, PillComponent.CODEC.parse(
                context.registries().createSerializationContext(JsonOps.INSTANCE),
                JsonParser.parseString("{\"toxicity_gain\":7}")).getOrThrow());
        return stack;
    }

    private static ItemStack plain(ProbeContext context) {
        ItemStack stack = dose(context, new ItemStack(Items.STICK, 2));
        context.player().setItemInHand(InteractionHand.MAIN_HAND, stack);
        return stack;
    }

    private static void run(ProbeContext context, ItemStack stack) {
        ServerPlayer player = context.player();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        ItemUseCycles.prepare(player, stack);
        stack.use(player.level(), player, InteractionHand.MAIN_HAND);
        expect(context, "real Item.use starts", player.isUsingItem(), true);
        if (stack.has(DataComponents.USE_COOLDOWN))
            expect(context, "timed use does not apply cooldown at start", player.getCooldowns().isOnCooldown(stack), false);
        int duration = player.getUseItemRemainingTicks();
        int ticks = 0;
        double decay = MxtServerConfig.INSTANCE.alchemy.toxicityDecayPerSecond.getValue();
        try {
            MxtServerConfig.INSTANCE.alchemy.toxicityDecayPerSecond.setValue(0.0D);
            while (player.isUsingItem() && ticks++ <= duration + 1) player.doTick();
        } finally {
            MxtServerConfig.INSTANCE.alchemy.toxicityDecayPerSecond.setValue(decay);
        }
        expect(context, "gesture completes", player.isUsingItem(), false);
        expect(context, "completed gesture closes session", ItemUseCycles.current(player), null);
    }

    private static void holdPill(ProbeContext context) {
        ItemStack slip = dose(context, JadeSlipItem.create(Component.literal("mixed gesture")));
        run(context, slip);
        expect(context, "reading preserves carrier", slip.getCount(), 1);
        close(context, "reading grants no pill toxicity", PillService.toxicity(context.player()), 0.0D);
        expect(context, "reading never adds consumable", slip.has(DataComponents.CONSUMABLE), false);
    }

    private static void plainPill(ProbeContext context) {
        ItemStack stack = plain(context);
        run(context, stack);
        expect(context, "plain pill consumes exactly one", stack.getCount(), 1);
        close(context, "plain pill settles once", PillService.toxicity(context.player()), 7.0D);
        expect(context, "plain pill preserves component patch", stack.has(DataComponents.CONSUMABLE), false);

        ItemStack last = plain(context);
        last.setCount(1);
        last.set(DataComponents.USE_REMAINDER, new UseRemainder(new ItemStackTemplate(Items.BOWL)));
        last.set(DataComponents.USE_COOLDOWN, new UseCooldown(5.0F));
        ItemStack cooldownKey = last.copy();
        int bowls = context.player().getInventory().countItem(Items.BOWL);
        run(context, last);
        expect(context, "last dose installs native remainder", context.player().getMainHandItem().is(Items.BOWL), true);
        expect(context, "last dose creates exactly one remainder", context.player().getInventory().countItem(Items.BOWL) - bowls, 1);
        expect(context, "native cooldown applies after completion", context.player().getCooldowns().isOnCooldown(cooldownKey), true);
        close(context, "last dose settles once", PillService.toxicity(context.player()), 14.0D);
    }

    private static void nativeCreative(ProbeContext context) {
        ServerPlayer player = context.player();
        player.getFoodData().setFoodLevel(1);
        ItemStack apple = dose(context, new ItemStack(Items.APPLE, 2));
        Consumable nativeUse = apple.get(DataComponents.CONSUMABLE);
        run(context, apple);
        expect(context, "native food consumes one", apple.getCount(), 1);
        expect(context, "native component stays identical", apple.get(DataComponents.CONSUMABLE) == nativeUse, true);
        close(context, "native food settles pill overlay", PillService.toxicity(player), 7.0D);
        player.getAbilities().instabuild = true;
        try {
            ItemStack creative = plain(context);
            run(context, creative);
            expect(context, "creative keeps the stack", creative.getCount(), 2);
            close(context, "creative still settles consumption", PillService.toxicity(player), 14.0D);
        } finally {
            player.getAbilities().instabuild = false;
        }
    }

    private static void nativeComponents(ProbeContext context) {
        ItemStack slip = JadeSlipItem.create(Component.literal("native consumable"));
        Consumable sameShape = new Consumable(0.25F, ItemUseAnimation.BOW,
                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY), false, List.of());
        slip.set(DataComponents.CONSUMABLE, sameShape);
        context.player().setItemInHand(InteractionHand.MAIN_HAND, slip);
        expect(context, "same-shaped native use is refused", ItemUseCycles.prepare(context.player(), slip), false);
        expect(context, "same-shaped native use stays identical", slip.get(DataComponents.CONSUMABLE) == sameShape, true);
        expect(context, "native duration preserved", slip.getUseDuration(context.player()), 5);
        for (ItemStack nativeStack : List.of(new ItemStack(Items.SHIELD), new ItemStack(Items.BOW), new ItemStack(Items.IRON_HELMET))) {
            dose(context, nativeStack);
            context.player().setItemInHand(InteractionHand.MAIN_HAND, nativeStack);
            expect(context, "native use preserved for " + nativeStack.getItem(), ItemUseCycles.prepare(context.player(), nativeStack), false);
        }

        ServerPlayer player = context.player();
        AtomicInteger finishes = new AtomicInteger();
        ItemStack blocking = plain(context);
        ItemUseCycles.prepare(player, blocking, new CustomCycle(finishes, false));
        blocking.set(DataComponents.BLOCKS_ATTACKS, new ItemStack(Items.SHIELD).get(DataComponents.BLOCKS_ATTACKS));
        expect(context, "late native blocking keeps duration", blocking.getUseDuration(player), 72000);
        expect(context, "late native blocking keeps animation", blocking.getUseAnimation(), ItemUseAnimation.BLOCK);
        expect(context, "late native blocking starts normally", blocking.use(player.level(), player, InteractionHand.MAIN_HAND), InteractionResult.CONSUME);
        player.doTick();
        expect(context, "native blocking is not interrupted by stale session", player.isUsingItem(), true);
        expect(context, "native blocking abandons fallback session", ItemUseCycles.current(player), null);
        player.releaseUsingItem();

        ItemStack equipment = plain(context);
        equipment.setCount(1);
        ItemUseCycles.prepare(player, equipment, new CustomCycle(finishes, false));
        equipment.set(DataComponents.EQUIPPABLE, new ItemStack(Items.IRON_HELMET).get(DataComponents.EQUIPPABLE));
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        InteractionResult result = equipment.use(player.level(), player, InteractionHand.MAIN_HAND);
        expect(context, "late native equipment keeps success", result instanceof InteractionResult.Success, true);
        expect(context, "late native equipment keeps replacement", result instanceof InteractionResult.Success success
                && success.heldItemTransformedTo() != null && success.heldItemTransformedTo().isEmpty(), true);
        expect(context, "late native equipment occupies its slot", player.getItemBySlot(EquipmentSlot.HEAD).is(Items.STICK), true);
        expect(context, "native equipment starts no fallback", player.isUsingItem(), false);
        expect(context, "native equipment closes preparation", ItemUseCycles.current(player), null);
        expect(context, "native operations run no custom completion", finishes.get(), 0);
        close(context, "native non-consumption grants no pill", PillService.toxicity(player), 0.0D);

        ItemStack eating = plain(context);
        ItemUseCycles.prepare(player, eating, new CustomCycle(finishes, false));
        eating.use(player.level(), player, InteractionHand.MAIN_HAND);
        eating.set(DataComponents.CONSUMABLE, sameShape);
        double decay = MxtServerConfig.INSTANCE.alchemy.toxicityDecayPerSecond.getValue();
        try {
            MxtServerConfig.INSTANCE.alchemy.toxicityDecayPerSecond.setValue(0.0D);
            for (int tick = 0; tick < 4 && player.isUsingItem(); tick++) player.doTick();
        } finally {
            MxtServerConfig.INSTANCE.alchemy.toxicityDecayPerSecond.setValue(decay);
        }
        expect(context, "native consumption introduced mid-use completes", player.isUsingItem(), false);
        expect(context, "native consumption introduced mid-use consumes once", eating.getCount(), 1);
        expect(context, "native consumption abandons custom completion", finishes.get(), 0);
        close(context, "native consumption introduced mid-use settles once", PillService.toxicity(player), 7.0D);
    }

    private static void copiesSave(ProbeContext context) {
        ItemStack stack = plain(context);
        ItemUseCycles.prepare(context.player(), stack);
        var ops = context.registries().createSerializationContext(JsonOps.INSTANCE);
        var before = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
        ItemStack copy = stack.copy();
        ItemStack split = stack.split(1);
        ItemStack decoded = ItemStack.CODEC.parse(ops, before).getOrThrow();
        for (ItemStack isolated : List.of(copy, split, decoded)) {
            expect(context, "copy/split/save has no consumable", isolated.has(DataComponents.CONSUMABLE), false);
            expect(context, "copy/split/save has no pose", isolated.getUseAnimation(), ItemUseAnimation.NONE);
            expect(context, "copy/split/save has no duration", isolated.getUseDuration(context.player()), 0);
        }
        expect(context, "armed stack has no persistent runtime patch", stack.has(DataComponents.CONSUMABLE), false);
        ItemUseCycles.close(context.player());
    }

    private static void dropTransfer(ProbeContext context) {
        ServerPlayer player = context.player();
        ItemStack stack = plain(context);
        ItemUseCycles.prepare(player, stack);
        stack.use(player.level(), player, InteractionHand.MAIN_HAND);
        var drop = player.drop(stack.split(1), false, false);
        expect(context, "actual drop exists", drop != null, true);
        if (drop != null) {
            expect(context, "drop has no temporary component", drop.getItem().has(DataComponents.CONSUMABLE), false);
            expect(context, "drop has no temporary pose", drop.getItem().getUseAnimation(), ItemUseAnimation.NONE);
            drop.discard();
        }
        ServerPlayer other = context.joinBeside(player, 2, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        other.setItemInHand(InteractionHand.MAIN_HAND, stack);
        expect(context, "transferred stack has no duration", stack.getUseDuration(other), 0);
        expect(context, "transferred stack has no pose", stack.getUseAnimation(), ItemUseAnimation.NONE);
        player.doTick();
        ItemUseCycles.onEntityTick(new EntityTickEvent.Post(player));
        expect(context, "departed carrier closes session", ItemUseCycles.current(player), null);
        close(context, "interrupted use grants no pill", PillService.toxicity(player), 0.0D);
    }

    private static void lifecycle(ProbeContext context) {
        ServerPlayer player = context.player();
        ItemStack stack = plain(context);
        ItemUseCycles.prepare(player, stack);
        ItemUseCycles.onEntityTick(new EntityTickEvent.Post(player));
        expect(context, "unused click closes at idle", ItemUseCycles.current(player), null);
        ItemUseCycles.prepare(player, stack);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        player.releaseUsingItem();
        expect(context, "Stop closes session", ItemUseCycles.current(player), null);
        ItemUseCycles.prepare(player, stack);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        player.stopUsingItem();
        ItemUseCycles.onEntityTick(new EntityTickEvent.Post(player));
        expect(context, "silent stop closes at idle", ItemUseCycles.current(player), null);
        Consumer<LivingEntityUseItemEvent.Tick> rejectTick = event -> {
            if (event.getEntity() == player) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, rejectTick);
        try {
            ItemUseCycles.prepare(player, stack);
            player.startUsingItem(InteractionHand.MAIN_HAND);
            player.doTick();
            expect(context, "late Tick cancellation stops use", player.isUsingItem(), false);
            expect(context, "late Tick cancellation closes session", ItemUseCycles.current(player), null);
            close(context, "cancelled Tick grants no pill", PillService.toxicity(player), 0.0D);
        } finally {
            NeoForge.EVENT_BUS.unregister(rejectTick);
        }
        ItemUseCycles.prepare(player, stack);
        NeoForge.EVENT_BUS.post(new EntityLeaveLevelEvent(player, player.level()));
        expect(context, "entity removal closes session", ItemUseCycles.current(player), null);
        ItemUseCycles.prepare(player, stack);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
        expect(context, "logout closes before save", ItemUseCycles.current(player), null);
        expect(context, "every exit preserves stack patch", stack.has(DataComponents.CONSUMABLE), false);
    }

    private static void rejectedStart(ProbeContext context) {
        ServerPlayer player = context.player();
        Consumer<LivingEntityUseItemEvent.Start> reject = event -> {
            if (event.getEntity() == player) event.setCanceled(true);
        };
        for (EventPriority priority : List.of(EventPriority.HIGH, EventPriority.LOWEST)) {
            NeoForge.EVENT_BUS.addListener(priority, reject);
            try {
                ItemStack stack = plain(context);
                ItemUseCycles.prepare(player, stack);
                stack.use(player.level(), player, InteractionHand.MAIN_HAND);
                expect(context, priority + " rejected Start does not use", player.isUsingItem(), false);
                expect(context, priority + " rejected Start closes immediately", ItemUseCycles.current(player), null);
                expect(context, priority + " rejected Start leaves no component", stack.has(DataComponents.CONSUMABLE), false);
                expect(context, priority + " rejected Start leaves no pose", stack.getUseAnimation(), ItemUseAnimation.NONE);
            } finally {
                NeoForge.EVENT_BUS.unregister(reject);
            }
        }
        Consumer<LivingEntityUseItemEvent.Start> zeroDuration = event -> {
            if (event.getEntity() == player) event.setDuration(0);
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, zeroDuration);
        try {
            ItemStack stack = plain(context);
            ItemUseCycles.prepare(player, stack);
            stack.use(player.level(), player, InteractionHand.MAIN_HAND);
            expect(context, "zero-duration Start does not use", player.isUsingItem(), false);
            expect(context, "zero-duration Start closes session", ItemUseCycles.current(player), null);
        } finally {
            NeoForge.EVENT_BUS.unregister(zeroDuration);
        }
        Consumer<LivingEntityUseItemEvent.Start> failingStart = event -> {
            if (event.getEntity() == player) throw new IllegalStateException("start failure");
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, failingStart);
        try {
            ItemStack stack = plain(context);
            ItemUseCycles.prepare(player, stack);
            boolean failed = false;
            try {
                stack.use(player.level(), player, InteractionHand.MAIN_HAND);
            } catch (IllegalStateException expected) {
                failed = expected.getMessage().equals("start failure");
            }
            expect(context, "Start listener failure propagates", failed, true);
            expect(context, "Start listener failure closes preparation", ItemUseCycles.current(player), null);
            expect(context, "Start listener failure leaves no pose", stack.getUseAnimation(), ItemUseAnimation.NONE);
        } finally {
            NeoForge.EVENT_BUS.unregister(failingStart);
        }
    }

    private static void replacement(ProbeContext context) {
        ServerPlayer player = context.player();
        ItemStack first = plain(context);
        ItemUseCycles.prepare(player, first);
        ItemStack second = dose(context, new ItemStack(Items.STICK, 2));
        player.setItemInHand(InteractionHand.OFF_HAND, second);
        ItemUseCycles.prepare(player, second);
        expect(context, "new preparation releases old target", first.getUseDuration(player), 0);
        expect(context, "new target owns duration", second.getUseDuration(player), 32);
        ItemUseCycles.close(player);
        expect(context, "closing releases the new target", second.getUseDuration(player), 0);
        expect(context, "old target remains clean", first.has(DataComponents.CONSUMABLE), false);
    }

    private static void slotSync(ProbeContext context) {
        ServerPlayer player = context.player();
        ItemStack original = plain(context);
        ItemUseCycles.prepare(player, original);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        ItemStack replacement = original.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, replacement);
        expect(context, "equal slot replacement inherits duration", replacement.getUseDuration(player), 32);
        expect(context, "old alias is detached", original.getUseAnimation(), ItemUseAnimation.NONE);
        expect(context, "live alias keeps pose", replacement.getUseAnimation(), ItemUseAnimation.EAT);
        player.releaseUsingItem();
        expect(context, "release clears live alias", replacement.getUseAnimation(), ItemUseAnimation.NONE);
        expect(context, "neither alias has a runtime component", original.has(DataComponents.CONSUMABLE) || replacement.has(DataComponents.CONSUMABLE), false);
    }

    private static void extension(ProbeContext context) {
        ServerPlayer player = context.player();
        AtomicInteger finishes = new AtomicInteger();
        Consumer<ResolveItemUseCycleEvent> provider = event -> {
            if (event.getHolder() == player) event.claim(new CustomCycle(finishes, false));
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, provider);
        try {
            ItemStack stack = plain(context);
            run(context, stack);
            expect(context, "external provider wins and completes once", finishes.get(), 1);
            expect(context, "external non-consumption preserves stack", stack.getCount(), 2);
            close(context, "external non-consumption grants no pill", PillService.toxicity(player), 0.0D);
        } finally {
            NeoForge.EVENT_BUS.unregister(provider);
        }
        nativeFinishResults(context);
    }

    private static void nativeFinishResults(ProbeContext context) {
        ServerPlayer player = context.player();
        AtomicInteger finishes = new AtomicInteger();
        AtomicInteger nativeCalls = new AtomicInteger();
        for (boolean replacement : List.of(false, true)) {
            ItemStack stack = plain(context);
            ItemUseCycles.prepare(player, stack, new CustomCycle(finishes, false));
            player.startUsingItem(InteractionHand.MAIN_HAND);
            ItemStack expected = replacement ? new ItemStack(Items.BOWL) : stack;
            ItemStack result = ItemUseCycles.finish(player.level(), player, stack, () -> {
                nativeCalls.incrementAndGet();
                if (!replacement) stack.shrink(1);
                return expected;
            });
            expect(context, "native finish preserves " + (replacement ? "replacement" : "mutation"), result == expected, true);
            expect(context, "native finish closes session", ItemUseCycles.current(player), null);
            expect(context, "native finish leaves no fallback pose", stack.getUseAnimation(), ItemUseAnimation.NONE);
            player.stopUsingItem();
        }
        expect(context, "native completions each execute once", nativeCalls.get(), 2);
        expect(context, "native completions skip custom completion", finishes.get(), 0);

        ItemStack emptied = plain(context);
        ItemUseCycles.prepare(player, emptied, new CustomCycle(finishes, false));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        ItemUseCycles.finish(player.level(), player, emptied, () -> {
            emptied.setCount(0);
            return ItemUseCycles.finish(player.level(), player, emptied, () -> {
                nativeCalls.incrementAndGet();
                return emptied;
            });
        });
        expect(context, "emptied stack cannot reenter native completion", nativeCalls.get(), 2);
        expect(context, "emptied native result runs no custom completion", finishes.get(), 0);
        expect(context, "emptied native result closes session", ItemUseCycles.current(player), null);
        player.stopUsingItem();
    }

    private static void finishFailure(ProbeContext context) {
        ServerPlayer player = context.player();
        ItemStack stack = plain(context);
        AtomicInteger finishes = new AtomicInteger();
        ItemUseCycles.prepare(player, stack, new CustomCycle(finishes, true));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        boolean failed = false;
        try {
            stack.finishUsingItem(player.level(), player);
        } catch (IllegalStateException expected) {
            failed = expected.getMessage().equals("probe failure");
        } finally {
            player.stopUsingItem();
        }
        expect(context, "finish failure propagated", failed, true);
        expect(context, "finish failure closes session", ItemUseCycles.current(player), null);
        expect(context, "reentrant finish invokes callback once", finishes.get(), 1);
        expect(context, "finish failure leaves no pose", stack.getUseAnimation(), ItemUseAnimation.NONE);

        stack = plain(context);
        ItemUseCycles.prepare(player, stack, new CustomCycle(finishes, false));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        failed = false;
        try {
            ItemUseCycles.finish(player.level(), player, stack, () -> {
                throw new IllegalStateException("native failure");
            });
        } catch (IllegalStateException expected) {
            failed = expected.getMessage().equals("native failure");
        } finally {
            player.stopUsingItem();
        }
        expect(context, "native finish failure propagates", failed, true);
        expect(context, "native finish failure closes session", ItemUseCycles.current(player), null);
        expect(context, "native finish failure skips custom callback", finishes.get(), 1);
    }

    private record CustomCycle(AtomicInteger finishes, boolean fail) implements ItemUseCycle {
        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath("mxt_test", "custom_gesture");
        }

        @Override
        public int durationTicks() {
            return 3;
        }

        @Override
        public ItemUseAnimation animation() {
            return ItemUseAnimation.BRUSH;
        }

        @Override
        public ItemStack finish(Level level, LivingEntity holder, ItemStack stack) {
            this.finishes.incrementAndGet();
            stack.finishUsingItem(level, holder);
            if (this.fail) throw new IllegalStateException("probe failure");
            return stack;
        }
    }

    private static void expect(ProbeContext context, String leg, Object actual, Object expected) {
        boolean matched = Objects.equals(actual, expected);
        context.note("use_cycle " + leg + ": " + (matched ? "OK" : "MISMATCH") + " actual=" + actual + " expected=" + expected);
        context.expect(leg, actual, expected);
    }

    private static void close(ProbeContext context, String leg, double actual, double expected) {
        boolean matched = Math.abs(actual - expected) < 1.0E-6D;
        context.note("use_cycle " + leg + ": " + (matched ? "OK" : "MISMATCH") + " actual=" + actual + " expected=" + expected);
        context.check(leg + ": " + actual + " != " + expected, matched);
    }
}
