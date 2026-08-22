package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.resources.Identifier;

/**
 * Client-side copy of the server-resolved aura at the local player's position.
 */
public final class AuraClientState {
    private static final Identifier EMPTY = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "empty");
    private static volatile Snapshot current = new Snapshot(EMPTY, 0.0D, 0.0D);

    private AuraClientState() {
    }

    public static Snapshot current() {
        return current;
    }

    public static void update(Identifier source, double concentration, double maximum) {
        current = new Snapshot(source, Double.isFinite(concentration) ? Math.max(0.0D, concentration) : 0.0D,
                Double.isNaN(maximum) || maximum < 0.0D ? 0.0D : maximum);
    }

    public record Snapshot(Identifier source, double concentration, double maximum) {
    }
}
