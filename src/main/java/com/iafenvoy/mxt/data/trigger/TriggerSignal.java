package com.iafenvoy.mxt.data.trigger;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable runtime notification sent to the trigger dispatcher.
 */
public record TriggerSignal(Identifier type, TriggerContext context, @Nullable Identifier source, long gameTime) {
}
