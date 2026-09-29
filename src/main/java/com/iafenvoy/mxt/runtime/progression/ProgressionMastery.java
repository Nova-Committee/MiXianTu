package com.iafenvoy.mxt.runtime.progression;

import net.minecraft.resources.Identifier;

/**
 * How far a holder stands from its next level: the value measured, what that level asks for, and which resource
 * measures it. Handed to scripts, which have no other way to ask "how much is left".
 */
public record ProgressionMastery(double have, double required, Identifier resource) {
}
