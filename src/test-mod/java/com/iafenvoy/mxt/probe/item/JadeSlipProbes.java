package com.iafenvoy.mxt.probe.item;

import com.iafenvoy.mxt.data.item.HoldBinding;
import com.iafenvoy.mxt.item.JadeSlipHold;
import com.iafenvoy.mxt.item.JadeSlipItem;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.runtime.hold.HoldLookup;
import com.iafenvoy.mxt.runtime.hold.HoldService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Reading a jade slip: it is a hold like every other reading in the mod, and its length is the stack's own component
 * rather than the item's. Both halves matter - the declaration says the slip is read by holding it, the component says
 * for how long - and the use cycle is driven for real, because the one thing that can silently break is a read that
 * never starts.
 */
public final class JadeSlipProbes {
    private JadeSlipProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("item/jade_slip/default_read", JadeSlipProbes::defaultRead),
                Probe.of("item/jade_slip/read_cycle", JadeSlipProbes::readCycle));
    }

    private static void defaultRead(ProbeContext context) {
        ServerPlayer player = context.player();
        ItemStack slip = MxtItems.JADE_SLIP.toStack();
        ItemStack instantSlip = slip.copy();
        instantSlip.set(MxtDataComponents.JADE_SLIP_READING, 0);
        HoldBinding hold = HoldLookup.hold(slip);
        // A zero is the slip read by a click, which is the other branch of JadeSlipItem.use.
        context.check("jade slip probe: a zero reading is not claimed by the hold",
                hold != null && !hold.claims(player, context.registries(), instantSlip));
    }

    // Run the runtime gesture through vanilla's actual use lifecycle.
    private static void readCycle(ProbeContext context) {
        ServerPlayer player = context.player();
        ItemStack slip = JadeSlipItem.create(Component.literal("probe slip"));
        player.setItemInHand(InteractionHand.MAIN_HAND, slip);
        HoldService.arm(context.registries(), player, slip, JadeSlipHold.INSTANCE);
        int duration = slip.getUseDuration(player);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        boolean began = player.isUsingItem();
        int ran = 0;
        for (; ran < duration + 5 && player.isUsingItem(); ran++) player.doTick();
        context.check("jade slip probe: a read runs for the component's length and leaves the slip in hand",
                began && duration == JadeSlipItem.DEFAULT_READ_TIME && !player.isUsingItem()
                        && player.getMainHandItem().is(MxtItems.JADE_SLIP.get()));
    }
}
