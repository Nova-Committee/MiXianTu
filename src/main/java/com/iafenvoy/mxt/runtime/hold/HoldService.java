package com.iafenvoy.mxt.runtime.hold;

import com.iafenvoy.mxt.api.ItemUseCycle;
import com.iafenvoy.mxt.data.item.HoldBinding;
import com.iafenvoy.mxt.event.ResolveItemUseCycleEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber
public final class HoldService {
    private static final Identifier PURPOSE = Identifier.fromNamespaceAndPath("mxt", "hold");
    private static final int SOUND_INTERVAL_TICKS = 4;
    private static final float SOUND_VOLUME = 1.0F;
    private static final float SOUND_PITCH_SPREAD = 0.2F;

    private HoldService() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onResolve(ResolveItemUseCycleEvent event) {
        if (event.getCycle() != null) return;
        LivingEntity holder = event.getHolder();
        HoldBinding hold = HoldLookup.hold(event.getStack());
        if (hold != null && hold.claims(holder, holder.level().registryAccess(), event.getStack()))
            event.claim(cycle(holder.level().registryAccess(), holder, event.getStack(), hold));
    }

    public static void arm(Provider registries, LivingEntity holder, ItemStack stack, HoldBinding hold) {
        if (hold.claims(holder, registries, stack))
            ItemUseCycles.prepare(holder, stack, cycle(registries, holder, stack, hold));
    }

    private static HoldCycle cycle(Provider registries, LivingEntity holder, ItemStack stack, HoldBinding hold) {
        return new HoldCycle(hold, hold.holdTicks(holder, registries, stack), hold.holdAnimation(registries, stack));
    }

    @Nullable
    public static HoldBinding activeBinding(LivingEntity holder) {
        return holder.isUsingItem() && ItemUseCycles.current(holder) instanceof HoldCycle cycle ? cycle.binding() : null;
    }

    private record HoldCycle(HoldBinding binding, int durationTicks,
                             ItemUseAnimation animation) implements ItemUseCycle {
        @Override
        public Identifier id() {
            return PURPOSE;
        }

        @Override
        public void tick(LivingEntity holder, ItemStack stack, int remainingTicks) {
            if (holder.level().isClientSide()) {
                if (holder instanceof Player player && player.isLocalPlayer())
                    emitHoldSound(holder, this.binding, stack, this.durationTicks - remainingTicks, null);
            } else emitHoldSound(holder, this.binding, stack, this.durationTicks - remainingTicks,
                    holder instanceof Player player ? player : null);
        }
    }

    // The elapsed duration is fixed at Start, even when a formula or stack count changes during the gesture.
    private static boolean emitHoldSound(LivingEntity entity, HoldBinding hold, ItemStack stack, int elapsed,
                                         @Nullable Player except) {
        if (elapsed <= 0 || elapsed % SOUND_INTERVAL_TICKS != 0) return false;
        Holder<SoundEvent> sound = hold.holdSound(entity.level().registryAccess(), stack);
        entity.level().playSound(except, entity.getX(), entity.getY(), entity.getZ(), sound.value(),
                entity.getSoundSource(), SOUND_VOLUME, entity.getRandom().triangle(1.0F, SOUND_PITCH_SPREAD));
        return true;
    }

    public static boolean playHoldSound(LivingEntity entity, HoldBinding hold, ItemStack stack, int remaining,
                                        @Nullable Player except) {
        int elapsed = hold.holdTicks(entity, entity.level().registryAccess(), stack) - remaining;
        return emitHoldSound(entity, hold, stack, elapsed, except);
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
