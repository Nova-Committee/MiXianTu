package com.iafenvoy.mxt.render.mount;

import com.iafenvoy.mxt.api.MountRenderContext;
import com.iafenvoy.mxt.data.ability.render.MountPose;
import com.iafenvoy.mxt.data.ability.type.FlightDisplay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

// One frame's worth of context, built by the dispatcher and read by whichever renderer the definition names.
public record MountContext(ItemStack visual, Entity vehicle, Optional<FlightDisplay> display, float yRot, float xRot,
                           float partialTick, int lightCoords, MountPose motion, MountPose crew, int riders)
        implements MountRenderContext {
}
