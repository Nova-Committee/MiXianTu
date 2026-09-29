package com.iafenvoy.mxt.compat.geckolib;

import com.iafenvoy.mxt.data.ability.render.builtin.GeckoLibMountRender;
import com.iafenvoy.mxt.render.mount.MountRenderers;

/**
 * The GeckoLib half of the mount renderers, reached only from inside {@link GeckoLibCompat}'s mod-list guard. The
 * definition type itself is registered on the common side whether or not the mod is there, so a pack that asks for
 * a GeckoLib model still loads on a machine that cannot draw it.
 */
public final class GeckoLibMountRenderers {
    private GeckoLibMountRenderers() {
    }

    public static void register() {
        MountRenderers.register(GeckoLibMountRender.CODEC, new GeckoLibMountRenderer());
    }
}
