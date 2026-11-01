package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.item.HoldBinding;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.saturn.runtime.hold.ItemUseCycles;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The pose a reading is drawn with. A hold answers {@code Item.getUseAnimation} through the library's cycle, and the
 * answer has to survive the whole gesture: an answer that is right on the first tick and empty afterwards draws an
 * open book in the plain first-person pose.
 */
public final class HoldAnimationVerifyProbes {
    private static final int SAMPLED_TICKS = 6;

    private HoldAnimationVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/hold_animation", HoldAnimationVerifyProbes::poseSurvives));
    }

    private static void poseSurvives(ProbeContext context) {
        Holder<Technique> technique = context.require(MxtResourceKeys.TECHNIQUE, "qingxiao_breathing_manual");
        ItemStack book = MxtItems.TECHNIQUE_BOOK.toStack();
        book.set(MxtDataComponents.TECHNIQUE, technique);
        context.player().setItemInHand(InteractionHand.MAIN_HAND, book);
        ItemStack held = context.player().getItemInHand(InteractionHand.MAIN_HAND);
        context.check("the reading cycle is prepared", ItemUseCycles.prepare(context.player(), held));
        InteractionResult result = held.use(context.level(), context.player(), InteractionHand.MAIN_HAND);
        context.check("the click starts the reading", context.player().isUsingItem());

        List<ItemUseAnimation> poses = new ArrayList<>();
        for (int tick = 0; tick < SAMPLED_TICKS && context.player().isUsingItem(); tick++) {
            context.player().doTick();
            held = context.player().getItemInHand(InteractionHand.MAIN_HAND);
            poses.add(held.getUseAnimation());
        }
        context.note("poses after each tick = " + poses + ", click = " + result);
        context.expect("the pose holds for the whole reading", poses,
                List.copyOf(new ArrayList<>(Collections.nCopies(poses.size(), HoldBinding.DEFAULT_HOLD_ANIMATION))));
    }
}
