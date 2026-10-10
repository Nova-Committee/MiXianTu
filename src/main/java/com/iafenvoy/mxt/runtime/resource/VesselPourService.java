package com.iafenvoy.mxt.runtime.resource;

import com.iafenvoy.mxt.item.ItemFeedback;
import com.iafenvoy.mxt.item.SpiritVesselItem;
import com.iafenvoy.mxt.runtime.ModuleHooks;
import com.iafenvoy.mxt.runtime.hold.HoldLookup;
import com.iafenvoy.mxt.runtime.hold.HoldSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick;

import java.util.List;

/**
 * Pours a vessel out while it is held down: one tick of the hold moves one unit of each resource inside into the
 * holder's own account. The hold module drives the gesture, so this only answers a tick of it - there is no click
 * path here, and no mode to read.
 */
@EventBusSubscriber
public final class VesselPourService {
    private VesselPourService() {
    }

    public static void register() {
        ModuleHooks.register(HoldSource.class, registries -> List.of(VesselPourHold.INSTANCE));
    }

    @SubscribeEvent
    public static void onUseTick(Tick event) {
        LivingEntity holder = event.getEntity();
        if (holder.level().isClientSide()) return;
        ItemStack stack = event.getItem();
        if (!(HoldLookup.hold(stack) instanceof VesselPourHold hold)) return;
        if (!SpiritVesselItem.pourTick(holder, stack)) return;
        // Said once, on the first tick that really moved something: a hold that poured nothing stays quiet, and the
        // pour itself is silent apart from the gesture's own sound.
        if (holder instanceof ServerPlayer player && event.getDuration() == hold.holdTicks(holder.level().registryAccess(), stack))
            ItemFeedback.send(player, Component.translatable("item.mxt.spirit_vessel.released"));
    }
}
