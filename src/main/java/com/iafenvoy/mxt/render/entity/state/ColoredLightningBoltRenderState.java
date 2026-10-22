package com.iafenvoy.mxt.render.entity.state;

import com.iafenvoy.mxt.entity.ColoredLightningBolt;
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;

import java.util.List;

/**
 * The vanilla bolt render state plus the values that decide its look. All of them are synched, so a renderer
 * never has to reach back into the entity.
 */
public class ColoredLightningBoltRenderState extends LightningBoltRenderState {
    public int color = ColoredLightningBolt.DEFAULT_COLOR;
    public float alpha = ColoredLightningBolt.DEFAULT_ALPHA;
    public float thickness = ColoredLightningBolt.DEFAULT_THICKNESS;
    // Empty for a flat color; otherwise the gradient, first entry at the top of the strand.
    public List<Integer> palette = List.of();
}
