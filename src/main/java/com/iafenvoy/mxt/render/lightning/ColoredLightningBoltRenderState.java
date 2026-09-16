package com.iafenvoy.mxt.render.lightning;

import com.iafenvoy.mxt.runtime.lightning.ColoredLightningBolt;
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;

/**
 * The vanilla bolt render state plus the three values that decide its look. All three are synched, so a
 * renderer never has to reach back into the entity.
 */
public class ColoredLightningBoltRenderState extends LightningBoltRenderState {
    public int color = ColoredLightningBolt.DEFAULT_COLOR;
    public float alpha = ColoredLightningBolt.DEFAULT_ALPHA;
    public float thickness = ColoredLightningBolt.DEFAULT_THICKNESS;
}
