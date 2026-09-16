package com.iafenvoy.mxt.runtime.hold;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import org.slf4j.Logger;

/**
 * Reports why the first-person hold pose stops being drawn while the hold is still going. The pose needs four
 * things at once - the entity using an item, remaining duration above zero, the used hand being the rendered one,
 * and an animation other than {@code NONE} - but the server asks none of them and grants the hold's outcome
 * anyway, so this logs transitions rather than state.
 * <p>
 * The count the pose reads is the client's own while the read itself is timed by the server, so a server that is
 * not keeping up can end the pose before the read ends. That used to be patched over and is not any more, which
 * makes a {@code hold pose lost} line here the signature of it happening.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class HoldPoseDiagnostic {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The last logged state, minus the tick counter, so only real transitions are written. */
    private static String lastState = "";

    private HoldPoseDiagnostic() {
    }

    @SubscribeEvent
    public static void onClientTick(Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !player.isUsingItem()) {
            lastState = "";
            return;
        }
        ItemStack useItem = player.getUseItem();
        if (HoldLookup.hold(useItem) == null) {
            lastState = "";
            return;
        }

        InteractionHand hand = player.getUsedItemHand();
        ItemUseAnimation animation = useItem.getItem().getUseAnimation(useItem);
        int remaining = player.getUseItemRemainingTicks();
        boolean drawn = animation != ItemUseAnimation.NONE && remaining > 0;

        // Reported but not compared: the remaining ticks change every tick without moving the pose.
        String state = drawn + "|" + animation + "|" + hand + "|" + (useItem == player.getItemInHand(hand))
                + "|" + minecraft.screen;
        if (state.equals(lastState)) return;
        boolean first = lastState.isEmpty();
        lastState = state;

        if (drawn) {
            LOGGER.info("[mxt] hold pose {}: animation={}, remaining={}",
                    first ? "started" : "resumed", animation, remaining);
        } else if (remaining <= 0) {
            // The hold ran its full course and the pose stopped with it, which looks exactly like a break.
            // INFO, not WARN: reporting this as a loss would fire on every successful hold.
            LOGGER.info("[mxt] hold pose ended after the full hold: animation={}", animation);
        } else {
            // Still holding with no animation at all: the only signature of a real mid-hold break.
            LOGGER.warn("[mxt] hold pose lost with {} of the hold left: animation={}, hand={}, "
                            + "useItemIsHandStack={}, screen={}",
                    remaining, animation, hand, useItem == player.getItemInHand(hand), minecraft.screen);
        }
    }
}
