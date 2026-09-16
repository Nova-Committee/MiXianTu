package com.iafenvoy.mxt.runtime.lightning;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

/**
 * Lightning bolt whose render colour, glow and thickness are per-instance instead of the vanilla constants.
 * Damage, fire, lightning rods, copper weathering, the sky flash and the thunder are inherited unchanged.
 * <p>
 * These three values must be set before the bolt enters the level: the client's copy is built from the
 * non-default synched values at that moment, so a later change costs a tick of the vanilla look.
 */
public final class ColoredLightningBolt extends LightningBolt {
    /** The vanilla bolt colour {@code 0.45, 0.45, 0.5}, rounded to eight bits per channel. */
    public static final int DEFAULT_COLOR = 0x737380;
    /** The vanilla vertex alpha, which additive blending turns into glow strength rather than transparency. */
    public static final float DEFAULT_ALPHA = 0.3F;
    public static final float DEFAULT_THICKNESS = 1.0F;
    public static final float MIN_THICKNESS = 0.1F, MAX_THICKNESS = 4.0F;

    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(ColoredLightningBolt.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ALPHA = SynchedEntityData.defineId(ColoredLightningBolt.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> THICKNESS = SynchedEntityData.defineId(ColoredLightningBolt.class, EntityDataSerializers.FLOAT);

    public ColoredLightningBolt(EntityType<? extends ColoredLightningBolt> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(@NonNull Builder builder) {
        builder.define(COLOR, DEFAULT_COLOR);
        builder.define(ALPHA, DEFAULT_ALPHA);
        builder.define(THICKNESS, DEFAULT_THICKNESS);
    }

    public int color() {
        return this.getEntityData().get(COLOR);
    }

    /**
     * Rejects anything that is not an RGB value rather than letting it wrap in the renderer.
     */
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
}
