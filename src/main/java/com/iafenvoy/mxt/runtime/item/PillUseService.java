package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.api.ItemUseCycle;
import com.iafenvoy.mxt.event.ResolveItemUseCycleEvent;
import com.iafenvoy.mxt.item.PillItem;
import com.iafenvoy.mxt.runtime.hold.ItemUseCycles;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber
public final class PillUseService {
    private static final ItemUseCycle CYCLE = new PillCycle();

    private PillUseService() {
    }

    @SubscribeEvent
    public static void onResolve(ResolveItemUseCycleEvent event) {
        if (event.getCycle() == null && ItemBindingService.resolvePill(
                event.getHolder().level().registryAccess(), event.getStack()).present()) event.claim(CYCLE);
    }

    public static void arm(LivingEntity entity, ItemStack stack) {
        ItemUseCycles.prepare(entity, stack);
    }

    private record PillCycle() implements ItemUseCycle {
        private static final Identifier PURPOSE = Identifier.fromNamespaceAndPath("mxt", "pill");

        @Override
        public Identifier id() {
            return PURPOSE;
        }

        @Override
        public int durationTicks() {
            return PillItem.CONSUMABLE.consumeTicks();
        }

        @Override
        public ItemUseAnimation animation() {
            return PillItem.CONSUMABLE.animation();
        }

        @Override
        public boolean canStart(LivingEntity holder, ItemStack stack) {
            return PillItem.CONSUMABLE.canConsume(holder, stack);
        }

        @Override
        public boolean isConsumption() {
            return true;
        }

        @Override
        public void tick(LivingEntity holder, ItemStack stack, int remainingTicks) {
            if (PillItem.CONSUMABLE.shouldEmitParticlesAndSounds(remainingTicks))
                PillItem.CONSUMABLE.emitParticlesAndSounds(holder.getRandom(), holder, stack, 5);
        }

        @Override
        public ItemStack finish(Level level, LivingEntity holder, ItemStack stack) {
            return PillItem.CONSUMABLE.onConsume(level, holder, stack);
        }
    }
}
