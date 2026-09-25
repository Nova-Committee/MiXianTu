package com.iafenvoy.mxt.runtime.lightning;

import com.iafenvoy.mxt.registry.MxtEntityDataSerializers;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Lightning bolt whose render colour, glow, thickness and gradient are per-instance instead of the vanilla
 * constants; damage, fire, lightning rods, copper weathering, the sky flash and the thunder are inherited
 * unchanged.
 * <p>
 * These four values must be set before the bolt enters the level: the client's copy is built from the non-default
 * synched values at that moment, so a later change costs a tick of the vanilla look.
 */
public final class ColoredLightningBolt extends LightningBolt {
    // The vanilla bolt colour 0.45, 0.45, 0.5, rounded to eight bits per channel.
    public static final int DEFAULT_COLOR = 0x737380;
    // The vanilla vertex alpha, which additive blending turns into glow strength rather than transparency.
    public static final float DEFAULT_ALPHA = 0.3F;
    public static final float DEFAULT_THICKNESS = 1.0F;
    public static final float MIN_THICKNESS = 0.1F, MAX_THICKNESS = 4.0F;
    // The bound exists for the synched data channel, not the renderer: a palette is read once per seam, so its
    // length never costs anything at render time.
    public static final int MAX_PALETTE = 16;

    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(ColoredLightningBolt.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ALPHA = SynchedEntityData.defineId(ColoredLightningBolt.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> THICKNESS = SynchedEntityData.defineId(ColoredLightningBolt.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<List<Integer>> PALETTE = SynchedEntityData.defineId(ColoredLightningBolt.class, MxtEntityDataSerializers.COLOR_LIST.get());

    public ColoredLightningBolt(EntityType<? extends ColoredLightningBolt> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(@NonNull Builder builder) {
        builder.define(COLOR, DEFAULT_COLOR);
        builder.define(ALPHA, DEFAULT_ALPHA);
        builder.define(THICKNESS, DEFAULT_THICKNESS);
        builder.define(PALETTE, List.of());
    }

    public int color() {
        return this.getEntityData().get(COLOR);
    }

    // Rejects anything that is not an RGB value rather than letting it wrap in the renderer.
    public void setColor(int color) {
        if (color < 0 || color > 0xFFFFFF) throw new IllegalArgumentException("Lightning colour must be an RGB value");
        this.getEntityData().set(COLOR, color);
    }

    public float alpha() {
        return this.getEntityData().get(ALPHA);
    }

    public void setAlpha(float alpha) {
        this.getEntityData().set(ALPHA, alpha);
    }

    public float thickness() {
        return this.getEntityData().get(THICKNESS);
    }

    public void setThickness(float thickness) {
        this.getEntityData().set(THICKNESS, thickness);
    }

    /**
     * The gradient of the strand, first entry at the top and last at the ground. Empty means the flat
     * {@link #color()} - that is what every bolt looked like before gradients existed.
     */
    public List<Integer> palette() {
        return this.getEntityData().get(PALETTE);
    }

    // Rejects an oversized or non-RGB palette rather than letting the renderer sample something impossible, and
    // copies the list because synched data keeps the reference it is handed.
    public void setPalette(List<Integer> palette) {
        if (palette.size() > MAX_PALETTE)
            throw new IllegalArgumentException("Lightning palette must hold at most " + MAX_PALETTE + " colours");
        for (int color : palette)
            if (color < 0 || color > 0xFFFFFF)
                throw new IllegalArgumentException("Lightning colours must be RGB values");
        this.getEntityData().set(PALETTE, List.copyOf(palette));
    }
}
