package com.iafenvoy.mxt.render.talisman;

import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;

/**
 * The vanilla thrown-item state - the item model to draw - plus the spin this throw was given, so the renderer turns
 * the item from what extraction handed it instead of reaching back into the entity.
 */
public class TalismanProjectileRenderState extends ThrownItemRenderState {
    public float spinSpeed;
    public float spinPhase;

    // Radians, accumulated from the interpolated age: continuous by construction, so no frame can jump.
    public float spin() {
        return this.ageInTicks * this.spinSpeed + this.spinPhase;
    }
}
