package com.iafenvoy.mxt.compat.geckolib;

import net.neoforged.fml.ModList;

/**
 * Whether GeckoLib is installed, kept behind a mod-list check. This class declares nothing from GeckoLib and is
 * reachable unconditionally; the classes that do reference it are reached only from inside the guard, since one
 * GeckoLib type here would stop every client without the mod from starting.
 */
public final class GeckoLibCompat {
    private static final String GECKOLIB = "geckolib";

    private GeckoLibCompat() {
    }

    public static boolean loaded() {
        return ModList.get().isLoaded(GECKOLIB);
    }
}
