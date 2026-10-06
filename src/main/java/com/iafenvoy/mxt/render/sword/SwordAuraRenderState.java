package com.iafenvoy.mxt.render.sword;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;

public final class SwordAuraRenderState extends EntityRenderState {
    public Vec3 velocity = Vec3.ZERO;
    public float animationTime;
    public int entityId;
    public float centerOffsetY;
    public int bladeColor;
    public int auraColor;
    public boolean radialFlame;
    public float length;
    public float bladeWidth;
    public float thickness;
    public float handleLength;
    public float guardWidth;
    public float scale;
}
