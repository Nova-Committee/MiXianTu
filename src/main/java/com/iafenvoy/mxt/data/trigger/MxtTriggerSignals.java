package com.iafenvoy.mxt.data.trigger;

import com.iafenvoy.saturn.Saturn;
import net.minecraft.resources.Identifier;

public final class MxtTriggerSignals {
    // Existing scripts subscribe to these ids; their namespace is retained for compatibility.
    public static final Identifier BREAKTHROUGH = Identifier.fromNamespaceAndPath(Saturn.MOD_ID, "breakthrough");
    public static final Identifier PROGRESSION_LEVEL = Identifier.fromNamespaceAndPath(Saturn.MOD_ID, "progression_level");

    private MxtTriggerSignals() {
    }
}
