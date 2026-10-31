package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.testmod.MxtTestMod;
import com.iafenvoy.saturn.data.SpriteIcon;
import com.iafenvoy.saturn.data.resource.ResourceBar.Anchor;
import com.iafenvoy.saturn.data.resource.ResourceBar.ValueDisplay;
import com.iafenvoy.saturn.data.resourcebar.builtin.context.SelfHudContext;
import com.iafenvoy.saturn.data.resourcebar.builtin.renderdata.OriginsRenderData;
import com.iafenvoy.saturn.screen.resourcebar.ResourceBarRenderState;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * How much of a bar the renderer fills. A range with no width has no fill at all, which is what keeps a value pinned at
 * a zero or stale cap from being drawn as a full bar.
 */
public final class ResourceBarVerifyProbes {
    private ResourceBarVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/resource_bar_fill", ResourceBarVerifyProbes::fill));
    }

    private static void fill(ProbeContext context) {
        context.expect("a zero-width range draws nothing", state(0.0D, 0.0D, 0.0D).percentage(), 0.0D);
        context.expect("a zero-width range draws nothing whatever it holds", state(5.0D, 5.0D, 5.0D).percentage(), 0.0D);
        context.expect("the middle of a range is half full", state(50.0D, 0.0D, 100.0D).percentage(), 0.5D);
        context.expect("below the range is empty", state(-10.0D, 0.0D, 100.0D).percentage(), 0.0D);
        context.expect("above the range is full", state(150.0D, 0.0D, 100.0D).percentage(), 1.0D);
        context.expect("a non-finite value draws nothing", state(Double.NaN, 0.0D, 100.0D).percentage(), 0.0D);
        context.expect("a value inside a non-zero minimum keeps its offset", state(15.0D, 10.0D, 20.0D).percentage(), 0.5D);
    }

    private static ResourceBarRenderState state(double current, double minimum, double maximum) {
        return new ResourceBarRenderState(SelfHudContext.INSTANCE, Anchor.LEFT, 0,
                Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, "probe"), 0, current, minimum, maximum,
                new OriginsRenderData(SpriteIcon.of(OriginsRenderData.DEFAULT_TEXTURE, false), 0, OptionalInt.empty(), false),
                0, 0, Optional.empty(), ValueDisplay.NONE);
    }
}
