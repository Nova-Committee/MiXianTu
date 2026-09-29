package com.iafenvoy.mxt.compat.geckolib;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.ability.render.MountPose;
import com.iafenvoy.mxt.data.ability.render.builtin.GeckoLibMountRender;
import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.cache.GeckoLibResources;
import com.geckolib.cache.animation.BakedAnimations;
import com.geckolib.constant.dataticket.DataTicket;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The animatable behind every mount drawn from one definition: one controller per pose group, both reading the
 * pose the renderer put into the render state for this frame.
 *
 * <p>Animations that are not in the file are skipped instead of played, and remembered per baked file so a typo
 * costs one warning rather than one error per frame. A resource reload replaces the baked animations, which is what
 * makes the memo fall away then.
 */
final class MountAnimatable implements GeoAnimatable {
    static final DataTicket<MountPose> MOTION = DataTicket.create("mxt_mount_motion", MountPose.class);
    static final DataTicket<MountPose> CREW = DataTicket.create("mxt_mount_crew", MountPose.class);
    private final GeckoLibMountRender definition;
    private final AnimatableInstanceCache cache = new MountAnimatableCache(this);
    private final AnimationController<MountAnimatable> motionController;
    private final AnimationController<MountAnimatable> crewController;
    private final Map<String, Boolean> present = new HashMap<>();
    private @Nullable BakedAnimations baked;

    MountAnimatable(GeckoLibMountRender definition) {
        this.definition = definition;
        this.motionController = new AnimationController<>("mxt_motion", definition.transitionTicks(), test -> this.play(test, MOTION));
        this.crewController = new AnimationController<>("mxt_crew", definition.transitionTicks(), test -> this.play(test, CREW));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // A definition with no animation file is a still model, and a controller would only make GeckoLib look one up.
        if (this.definition.animations().isEmpty()) return;
        controllers.add(this.motionController);
        controllers.add(this.crewController);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    private PlayState play(AnimationTest<MountAnimatable> test, DataTicket<MountPose> ticket) {
        MountPose pose = test.getData(ticket);
        if (pose == null) return PlayState.STOP;
        String name = this.definition.animationFor(pose);
        return this.present(name) ? test.setAndContinue(RawAnimation.begin().thenLoop(name)) : PlayState.STOP;
    }

    private boolean present(String name) {
        BakedAnimations current = GeckoLibResources.getBakedAnimations().cache().get(this.animationFile());
        if (current != this.baked) {
            this.baked = current;
            this.present.clear();
        }
        Boolean known = this.present.get(name);
        if (known != null) return known;
        boolean found = current != null && current.getAnimation(name) != null;
        if (!found)
            MiXianTu.LOGGER.warn("Mount animation {} is not in {}; the model keeps its rest pose", name, this.animationFile());
        this.present.put(name, found);
        return found;
    }

    private Identifier animationFile() {
        return this.definition.animations().orElseThrow();
    }
}
