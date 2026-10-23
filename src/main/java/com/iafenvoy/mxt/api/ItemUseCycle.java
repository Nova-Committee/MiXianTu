package com.iafenvoy.mxt.api;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * An immutable description of one runtime item-use gesture, resolved independently on each logical side.
 * Implementations must not retain the holder or live stack; all mutable context is passed to callbacks.
 * Register a provider through {@code ResolveItemUseCycleEvent} on the NeoForge game event bus.
 * Native use components and item overrides keep their own behavior; base Item methods run before the fallback.
 * A gesture starts only after an unchanged PASS result and completes only after an unchanged native result.
 */
public interface ItemUseCycle {
    /** @return an extensible purpose identifier, identical on the client and server */
    Identifier id();

    /** @return a positive, fixed duration for this gesture, in ticks */
    int durationTicks();

    /** @return the pose displayed while this gesture is active */
    ItemUseAnimation animation();

    /** @return whether this gesture may start for the holder's current stack, without mutating state */
    default boolean canStart(LivingEntity holder, ItemStack stack) {
        return true;
    }

    /**
     * Whether successful completion counts as consumption for pill bindings, including creative-mode consumption.
     * Override together with {@link #finish}; returning true alone does not remove an item.
     */
    default boolean isConsumption() {
        return false;
    }

    /** Called on both logical sides while the gesture runs; gameplay changes must be server-only. */
    default void tick(LivingEntity holder, ItemStack stack, int remainingTicks) {
    }

    /**
     * Completes the gesture once on the server and returns the replacement hand stack.
     * The default preserves the stack. Vanilla remainder and cooldown handling still wraps this callback.
     * Native completion that replaces or changes the input stack takes precedence over this callback.
     */
    default ItemStack finish(Level level, LivingEntity holder, ItemStack stack) {
        return stack;
    }
}
