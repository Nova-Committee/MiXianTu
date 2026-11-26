package com.iafenvoy.mxt.probe.artifact;

import com.iafenvoy.mxt.data.ability.render.MountPose;
import com.iafenvoy.mxt.data.ability.render.builtin.ItemMountRender;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.render.mount.MountContext;
import com.iafenvoy.mxt.render.mount.MountRenderers;
import com.iafenvoy.mxt.testmod.ProbeMountRenders.ProbeMountRender;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public final class ArtifactMountClientProbes {
    private ArtifactMountClientProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("artifact/mount/renderer_fallback", context -> {
            MountRenderers.Resolved<?> fallback = MountRenderers.resolve(new ProbeMountRender("no client renderer"));
            context.expect("item renderer receives its own definition", fallback.definition(), ItemMountRender.INSTANCE);
            MountContext frame = new MountContext(ItemStack.EMPTY, context.player(), Optional.empty(),
                    0, 0, 0, 0, MountPose.motion(0, 0), MountPose.crew(0), 0);
            context.check("fallback extraction completes", fallback.extract(frame) != null);
        }));
    }
}
