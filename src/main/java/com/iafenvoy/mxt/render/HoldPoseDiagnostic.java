package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.runtime.cultivation.TechniqueHoldLookup;
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
 * Reports why the first-person reading pose stops being drawn while the read is still going.
 *
 * <h2>What it is for</h2>
 * The pose is drawn only while all of four things hold at once: the entity is using an item, the
 * remaining duration is above zero, the used hand is the hand being rendered, and the item reports an
 * animation other than {@code NONE}. Anything that breaks one of them makes the arm fall back to the
 * ordinary hold pose while the server - which never asks any of these questions - carries on and grants
 * the technique at the end. The result is a read that visibly "breaks" half way and still works, which
 * says nothing at all about which of the four gave way.
 *
 * <p>So this logs the transition rather than the state: a normal read produces two INFO lines, one when
 * the pose starts and one when it ends. A read that really does break produces a WARN instead, and its
 * line carries the four values so the one that is wrong names the cause.</p>
 *
 * <p>The two are worth separating carefully. The pose stops at the end of every successful read - the
 * arm returns to the ordinary holding pose and the item stays in hand, which is indistinguishable from
 * an interruption on screen - so a diagnostic that calls that a loss reports a problem on every read
 * that works. An earlier version of this class did exactly that.</p>
 *
 * <h2>It also answers a question about releasing</h2>
 * The screen is part of the logged state, because opening any screen makes the client force every key
 * up, which the game then reports as the player letting go. A hold that ends "by itself" while a screen
 * is open is that, and the log says so.</p>
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
        if (TechniqueHoldLookup.hold(useItem) == null) {
            lastState = "";
            return;
        }

        InteractionHand hand = player.getUsedItemHand();
        ItemUseAnimation animation = useItem.getItem().getUseAnimation(useItem);
        int remaining = player.getUseItemRemainingTicks();
        boolean drawn = animation != ItemUseAnimation.NONE && remaining > 0;

        // The remaining ticks change every tick and are not part of what makes the pose appear or not,
        // so they are reported but not compared.
        String state = drawn + "|" + animation + "|" + hand + "|" + (useItem == player.getItemInHand(hand))
                + "|" + minecraft.screen;
        if (state.equals(lastState)) return;
        boolean first = lastState.isEmpty();
        lastState = state;

        if (drawn) {
            LOGGER.info("[mxt] reading pose {}: animation={}, remaining={}",
                    first ? "started" : "resumed", animation, remaining);
        } else if (remaining <= 0) {
            // The read ran its full course and the pose stopped with it. That is the end, and it looks
            // exactly like a break would: the arm simply returns to the ordinary holding pose. Reported
            // at INFO rather than as a problem, because calling this a loss is what made an earlier
            // version of this diagnostic cry wolf on every successful read.
            LOGGER.info("[mxt] reading pose ended after the full read: animation={}", animation);
        } else {
            // Still reading, so the pose should be drawn, but the item reports no animation at all.
            // This is the only signature of a real mid-read break.
            LOGGER.warn("[mxt] reading pose lost with {} of the read left: animation={}, hand={}, "
                            + "useItemIsHandStack={}, screen={}",
                    remaining, animation, hand, useItem == player.getItemInHand(hand), minecraft.screen);
        }
    }
}
