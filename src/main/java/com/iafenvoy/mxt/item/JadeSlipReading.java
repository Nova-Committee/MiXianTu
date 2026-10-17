package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.event.dispatcher.CollectHoldBindingsEvent;
import com.iafenvoy.mxt.registry.MxtItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish;

/**
 * The reading half of a jade slip: it contributes the hold declaration and reports the text when that hold runs out.
 * The gesture's own length, pose and sound are the hold module's to run, and the text is the stack's to carry.
 */
@EventBusSubscriber
public final class JadeSlipReading {
    private JadeSlipReading() {
    }

    @SubscribeEvent
    public static void onCollectHolds(CollectHoldBindingsEvent event) {
        event.add(JadeSlipHold.INSTANCE);
    }

    // A completed read is a read the reader spent the hold on, so the text lands there and not on the click.
    @SubscribeEvent
    public static void onUseFinish(Finish event) {
        if (event.getEntity().level().isClientSide()) return;
        ItemStack stack = event.getItem();
        if (!stack.is(MxtItems.JADE_SLIP.get())) return;
        if (event.getEntity() instanceof ServerPlayer player) JadeSlipItem.read(player, stack);
    }
}
