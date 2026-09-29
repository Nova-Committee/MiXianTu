package com.iafenvoy.mxt.api;

import com.iafenvoy.mxt.data.ability.render.MountPose;
import com.iafenvoy.mxt.data.ability.type.FlightDisplay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * What a mount renderer reads to draw one frame, all of it captured at extraction time. Implemented by the
 * framework; a renderer only reads it.
 *
 * <p>Client-side only, and valid until the frame's submission is done.
 */
public interface MountRenderContext {
    // The artifact the vehicle gave up for the duration, which is also what its definition was read from.
    ItemStack visual();

    // The vehicle itself, for what the framework does not summarise here (its id, its passengers, its level).
    Entity vehicle();

    // Empty when the definition wrote none: the renderer then supplies its own default pose.
    Optional<FlightDisplay> display();

    float yRot();

    float xRot();

    float partialTick();

    int lightCoords();

    // The movement group: how the vehicle moved during this tick.
    MountPose motion();

    // The crew group: nobody, the driver alone, or the driver with passengers.
    MountPose crew();

    int riders();
}
