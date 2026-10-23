package com.iafenvoy.mxt.runtime.hold;

import com.iafenvoy.mxt.api.ItemUseCycle;
import com.iafenvoy.mxt.event.ResolveItemUseCycleEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Supplier;

@EventBusSubscriber
public final class ItemUseCycles {
    // Entity instances separate logical sides and reconnects; values never strongly retain their weak keys.
    private static final Map<LivingEntity, Session> SESSIONS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ItemStack, Session> STACKS = Collections.synchronizedMap(new WeakHashMap<>());

    private ItemUseCycles() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemUse(RightClickItem event) {
        prepare(event.getEntity(), event.getEntity().getItemInHand(event.getHand()));
    }

    public static boolean prepare(LivingEntity holder, ItemStack stack) {
        if (holder.isUsingItem()) return false;
        close(holder);
        if (!available(holder, stack)) return false;
        ResolveItemUseCycleEvent event = NeoForge.EVENT_BUS.post(new ResolveItemUseCycleEvent(holder, stack));
        return event.getCycle() != null && prepare(holder, stack, event.getCycle());
    }

    public static boolean prepare(LivingEntity holder, ItemStack stack, ItemUseCycle cycle) {
        if (holder.isUsingItem()) return false;
        close(holder);
        InteractionHand hand = handOf(holder, stack);
        if (hand == null || !available(holder, stack) || cycle.durationTicks() <= 0 || !cycle.canStart(holder, stack))
            return false;
        Session session = new Session(holder, hand, cycle);
        SESSIONS.put(holder, session);
        session.attach(stack);
        return true;
    }

    private static boolean available(LivingEntity holder, ItemStack stack) {
        return !stack.isEmpty() && !holder.isShiftKeyDown() && !hasNativeUseComponent(stack)
                && stack.getUseDuration(holder) == 0;
    }

    private static boolean hasNativeUseComponent(ItemStack stack) {
        if (stack.has(DataComponents.CONSUMABLE) || stack.has(DataComponents.BLOCKS_ATTACKS)
                || stack.has(DataComponents.KINETIC_WEAPON)) return true;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.swappable();
    }

    @Nullable
    private static InteractionHand handOf(LivingEntity holder, ItemStack stack) {
        for (InteractionHand hand : InteractionHand.values())
            if (holder.getItemInHand(hand) == stack) return hand;
        return null;
    }

    @Nullable
    public static ItemUseCycle current(LivingEntity holder) {
        Session session = SESSIONS.get(holder);
        return session == null ? null : session.cycle;
    }

    public static InteractionResult use(Player holder, InteractionHand hand, Supplier<InteractionResult> nativeUse) {
        Session session = SESSIONS.get(holder);
        if (session == null || session.started) return nativeUse.get();
        ItemStack before = holder.getItemInHand(hand).copy();
        try {
            InteractionResult result = nativeUse.get();
            if (result != InteractionResult.PASS || holder.isUsingItem() || SESSIONS.get(holder) != session
                    || !ItemStack.matches(before, holder.getItemInHand(hand))) {
                close(holder, session);
                return result;
            }
            return Objects.requireNonNullElse(start(holder, hand), result);
        } catch (RuntimeException | Error failure) {
            close(holder, session);
            throw failure;
        }
    }

    @Nullable
    public static InteractionResult start(Player holder, InteractionHand hand) {
        Session session = matching(holder, holder.getItemInHand(hand));
        if (session == null || session.hand != hand || session.started) return null;
        if (!session.cycle.canStart(holder, holder.getItemInHand(hand))) {
            close(holder);
            return InteractionResult.FAIL;
        }
        holder.startUsingItem(hand);
        // LocalPlayer may set its predicted use flag even when a later Start listener cancelled the gesture.
        if (!holder.isUsingItem() || holder.getUseItemRemainingTicks() <= 0
                || holder.getUseItem() != holder.getItemInHand(hand) || SESSIONS.get(holder) != session) {
            holder.stopUsingItem();
            close(holder);
            return InteractionResult.FAIL;
        }
        return InteractionResult.CONSUME;
    }

    public static int duration(LivingEntity holder, ItemStack stack, int nativeDuration) {
        if (nativeDuration != 0) {
            Session session = SESSIONS.get(holder);
            if (session != null && holder.getItemInHand(session.hand) == stack) close(holder, session);
            return nativeDuration;
        }
        Session session = matching(holder, stack);
        // Remote clients learn about a gesture through vanilla's synchronized use flag, without a click event.
        if (session == null && holder.level().isClientSide() && holder.isUsingItem()
                && holder.getItemInHand(holder.getUsedItemHand()) == stack) {
            session = discover(holder, stack);
        }
        return session == null ? nativeDuration : session.cycle.durationTicks();
    }

    @Nullable
    private static Session discover(LivingEntity holder, ItemStack stack) {
        if (stack.isEmpty() || hasNativeUseComponent(stack)) return null;
        ResolveItemUseCycleEvent event = NeoForge.EVENT_BUS.post(new ResolveItemUseCycleEvent(holder, stack));
        ItemUseCycle cycle = event.getCycle();
        if (cycle == null || cycle.durationTicks() <= 0) return null;
        close(holder);
        Session session = new Session(holder, holder.getUsedItemHand(), cycle);
        session.started = true;
        SESSIONS.put(holder, session);
        session.attach(stack);
        return session;
    }

    public static ItemUseAnimation animation(ItemStack stack, ItemUseAnimation nativeAnimation) {
        if (nativeAnimation != ItemUseAnimation.NONE) return nativeAnimation;
        Session session = STACKS.get(stack);
        LivingEntity holder = session == null ? null : session.holder.get();
        return holder != null && matching(holder, stack) == session ? session.cycle.animation() : nativeAnimation;
    }

    public static ItemStack finish(Level level, LivingEntity holder, ItemStack stack, Supplier<ItemStack> nativeFinish) {
        Session active = SESSIONS.get(holder);
        // Reentrant callbacks may have emptied the live target before the normal match can recognize it.
        if (active != null && active.stack.get() == stack && (active.finishing || active.completed)) return stack;
        Session session = matching(holder, stack);
        if (session == null || !session.started) return nativeFinish.get();
        if (session.finishing || session.completed) return stack;
        session.finishing = true;
        session.finishedStack = stack.copy();
        try {
            ItemStack nativeResult = Objects.requireNonNull(nativeFinish.get());
            // An existing completion owns its replacement or in-place changes; do not settle a second use.
            if (nativeResult != stack || !ItemStack.matches(session.finishedStack, stack)
                    || matching(holder, stack) != session) {
                close(holder, session);
                return nativeResult;
            }
            ItemStack result = level.isClientSide() ? stack : Objects.requireNonNull(session.cycle.finish(level, holder, stack));
            session.completed = true;
            return result;
        } catch (RuntimeException | Error failure) {
            close(holder, session);
            throw failure;
        } finally {
            session.finishing = false;
        }
    }

    public static boolean isConsumption(LivingEntity holder, ItemStack original) {
        Session session = SESSIONS.get(holder);
        if (session != null && session.started)
            return session.completed && session.cycle.isConsumption()
                    && ItemStack.isSameItemSameComponents(original, session.finishedStack);
        return original.has(DataComponents.CONSUMABLE);
    }

    @Nullable
    private static Session matching(LivingEntity holder, ItemStack stack) {
        Session session = SESSIONS.get(holder);
        if (session == null || stack.isEmpty() || holder.isRemoved()) return null;
        ItemStack held = holder.getItemInHand(session.hand);
        if (held != stack) return null;
        if (hasNativeUseComponent(held)) {
            close(holder, session);
            return null;
        }
        ItemStack previous = session.stack.get();
        if (previous != stack && (previous == null || !(holder.level().isClientSide()
                ? ItemStack.isSameItem(previous, stack) : ItemStack.isSameItemSameComponents(previous, stack)))) return null;
        if (previous != stack) session.attach(stack);
        return session;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onStart(LivingEntityUseItemEvent.Start event) {
        Session session = matching(event.getEntity(), event.getItem());
        if (session == null) {
            Session prepared = SESSIONS.get(event.getEntity());
            if (prepared != null && !prepared.started) close(event.getEntity(), prepared);
            return;
        }
        if (event.isCanceled() || event.getDuration() <= 0 || session.hand != event.getHand()) {
            event.setCanceled(true);
            close(event.getEntity());
        } else session.started = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH, receiveCanceled = true)
    public static void onTick(LivingEntityUseItemEvent.Tick event) {
        LivingEntity holder = event.getEntity();
        Session session = matching(holder, event.getItem());
        if (session == null) {
            boolean interrupted = SESSIONS.containsKey(holder);
            if (interrupted) {
                event.setCanceled(true);
                interrupt(holder);
            }
            return;
        }
        if (event.isCanceled() || event.getDuration() <= 0) {
            interrupt(holder);
            return;
        }
        try {
            session.cycle.tick(holder, event.getItem(), event.getDuration());
        } catch (RuntimeException | Error failure) {
            close(holder);
            throw failure;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onTickCancelled(LivingEntityUseItemEvent.Tick event) {
        if ((event.isCanceled() || event.getDuration() <= 0) && SESSIONS.containsKey(event.getEntity())) {
            interrupt(event.getEntity());
        }
    }

    private static void interrupt(LivingEntity holder) {
        try {
            holder.releaseUsingItem();
        } finally {
            close(holder);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFinish(LivingEntityUseItemEvent.Finish event) {
        close(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onStop(LivingEntityUseItemEvent.Stop event) {
        close(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity holder && !holder.isUsingItem()) close(holder);
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity holder) close(holder);
    }

    @SubscribeEvent
    public static void onLogout(PlayerLoggedOutEvent event) {
        close(event.getEntity());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        clear(false);
    }

    public static void close(LivingEntity holder) {
        Session session = SESSIONS.remove(holder);
        if (session != null) session.detach();
    }

    private static void close(LivingEntity holder, Session session) {
        if (SESSIONS.remove(holder, session)) session.detach();
    }

    public static void clear(boolean clientSide) {
        ArrayList<LivingEntity> holders;
        synchronized (SESSIONS) {
            holders = new ArrayList<>(SESSIONS.keySet());
        }
        for (LivingEntity holder : holders)
            if (holder.level().isClientSide() == clientSide) close(holder);
    }

    private static final class Session {
        final WeakReference<LivingEntity> holder;
        final InteractionHand hand;
        final ItemUseCycle cycle;
        WeakReference<ItemStack> stack = new WeakReference<>(null);
        ItemStack finishedStack = ItemStack.EMPTY;
        boolean started;
        boolean finishing;
        boolean completed;

        Session(LivingEntity holder, InteractionHand hand, ItemUseCycle cycle) {
            this.holder = new WeakReference<>(holder);
            this.hand = hand;
            this.cycle = cycle;
        }

        void attach(ItemStack next) {
            this.detach();
            this.stack = new WeakReference<>(next);
            STACKS.put(next, this);
        }

        void detach() {
            ItemStack previous = this.stack.get();
            if (previous != null) STACKS.remove(previous, this);
        }
    }
}
