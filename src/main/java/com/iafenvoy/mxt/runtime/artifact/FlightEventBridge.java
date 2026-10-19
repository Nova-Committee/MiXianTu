package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.attachment.FlightAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.type.FlightControlAbilityType;
import com.iafenvoy.mxt.data.ability.type.MountAbilityType;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.resulting.failures.FlightFailure;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;

/**
 * Per-tick server driver for an active flight: it re-reads the skill the flight was started with and the vehicle that
 * skill picked up, and ends the flight when either is gone.
 */
@EventBusSubscriber
public final class FlightEventBridge {
    private FlightEventBridge() {
    }

    @SubscribeEvent
    public static void onEntityTick(Post event) {
        // getExistingData: every living entity ticks here, and getData would put an empty flight record on each of
        // them the moment they spawn.
        if (!(event.getEntity() instanceof LivingEntity holder) || holder.level().isClientSide()) return;
        FlightAttachment data = holder.getExistingData(MxtAttachments.FLIGHT).orElse(null);
        if (data == null || !data.active()) return;
        // Both halves are the holders the flight was started with, and a stored holder keeps the definition it was
        // written with, so these are type checks rather than a way to notice a definition leaving the pack.
        Holder<Ability> skill = data.archetype().filter(archetype -> archetype.value().type() instanceof FlightControlAbilityType).orElse(null);
        if (skill == null) {
            FlightService.dismount(holder, FlightFailure.NOT_FLYABLE);
            return;
        }
        Holder<Ability> vehicle = data.vehicle()
                .filter(mount -> mount.value().type() instanceof MountAbilityType)
                .orElse(null);
        if (vehicle == null) {
            FlightService.dismount(holder, FlightFailure.NOT_FLYABLE);
            return;
        }
        FlightService.tick(holder, skill, vehicle, FormulaContext.of(holder));
    }
}
