package com.iafenvoy.mxt.runtime.perch;

import com.iafenvoy.mxt.attachment.PerchAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Lets go the moment the vehicle stops being somewhere to sit: the vanilla parrot's own list of reasons, plus the
 * vehicle sneaking, which is how a rider asks for its pet back. Read from the passenger side, so one lookup per
 * entity per tick covers every pair.
 */
@EventBusSubscriber
public final class PerchEventBridge {
    // The distance vanilla uses before a shoulder is no longer somewhere to hold on to.
    private static final double FALL_DISTANCE = 0.5D;

    private PerchEventBridge() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity passenger = event.getEntity();
        if (passenger.level().isClientSide()) return;
        PerchAttachment perch = passenger.getExistingData(MxtAttachments.PERCH).orElse(null);
        if (perch == null || !perch.perched()) return;
        Entity vehicle = passenger.getVehicle();
        // A vehicle that is gone is not a policy question: the record would otherwise outlive the ride and place
        // the creature at a stale offset the moment it rode anything else.
        if (vehicle == null || !vehicle.isAlive()) {
            perch.clear();
            return;
        }
        if (shouldRelease(vehicle)) PerchService.release(passenger);
    }

    private static boolean shouldRelease(Entity vehicle) {
        MxtServerConfig.Perch config = MxtServerConfig.INSTANCE.perch;
        if (config.dropWhenSneaking.getValue() && vehicle.isShiftKeyDown()) return true;
        if (config.dropOnFall.getValue() && vehicle.fallDistance > FALL_DISTANCE) return true;
        if (config.dropInWater.getValue() && vehicle.isInWater()) return true;
        if (config.dropInPowderSnow.getValue() && vehicle.isInPowderSnow) return true;
        if (config.dropWhenFlying.getValue() && isFlying(vehicle)) return true;
        return config.dropWhenSleeping.getValue() && vehicle instanceof LivingEntity living && living.isSleeping();
    }

    private static boolean isFlying(Entity vehicle) {
        if (vehicle instanceof LivingEntity living && living.isFallFlying()) return true;
        return vehicle instanceof Player player && player.getAbilities().flying;
    }
}
