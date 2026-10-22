package com.iafenvoy.mxt.render.entity.flame;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

public abstract class FlameRenderState extends EntityRenderState {
    public Vec3 velocity = Vec3.ZERO;
    public float animationTime;
    public int entityId;
    public float centerOffsetY;
    public int auraColor;
    public float scale;
    public boolean meteor;
    // Sword: blade length/width/thickness/handle length. Meteor: radius/tail length/unused/unused.
    public final Vector4f flameShape = new Vector4f();
    // Sword: guard width/radial flow. Seed and LOD are assigned by the manager.
    public float flameSurfaceWidth;
    public boolean radialFlame;
}
