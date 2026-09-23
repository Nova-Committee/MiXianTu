package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.data.ability.type.FlightDisplay;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/**
 * The mount's look: the item model the vehicle carries, the yaw and pitch it is drawn at, and the declared display.
 */
public class FlyingSwordRenderState extends EntityRenderState {
    public final ItemStackRenderState item = new ItemStackRenderState();
    public float yRot;
    public float xRot;
    public FlightDisplay display = FlightDisplay.DEFAULT;
}
