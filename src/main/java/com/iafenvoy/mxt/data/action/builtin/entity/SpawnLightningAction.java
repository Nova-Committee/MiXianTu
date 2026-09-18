package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.registry.MxtEntityTypes;
import com.iafenvoy.mxt.runtime.lightning.ColoredLightningBolt;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import org.jspecify.annotations.NonNull;

/**
 * Strikes a lightning bolt at the actor, with a colour, glow and thickness of its own. Everything the bolt
 * then does — damage, fire, lightning rods, copper, thunder — is the vanilla bolt's own behaviour.
 */
public record SpawnLightningAction(NumberProvider offsetX, NumberProvider offsetY, NumberProvider offsetZ,
                                   int color, float alpha, float thickness, NumberProvider damage,
                                   boolean visualOnly, boolean cause) implements EntityAction {
    public static final MapCodec<SpawnLightningAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.optionalFieldOf("offset_x", new Constant(0.0D)).forGetter(SpawnLightningAction::offsetX),
            NumberProvider.CODEC.optionalFieldOf("offset_y", new Constant(0.0D)).forGetter(SpawnLightningAction::offsetY),
            NumberProvider.CODEC.optionalFieldOf("offset_z", new Constant(0.0D)).forGetter(SpawnLightningAction::offsetZ),
            MiscCodecs.COLOR_NO_ALPHA.optionalFieldOf("color", ColoredLightningBolt.DEFAULT_COLOR).forGetter(SpawnLightningAction::color),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("alpha", ColoredLightningBolt.DEFAULT_ALPHA).forGetter(SpawnLightningAction::alpha),
            Codec.floatRange(ColoredLightningBolt.MIN_THICKNESS, ColoredLightningBolt.MAX_THICKNESS)
                    .optionalFieldOf("thickness", ColoredLightningBolt.DEFAULT_THICKNESS).forGetter(SpawnLightningAction::thickness),
            NumberProvider.CODEC.optionalFieldOf("damage", new Constant(5.0D)).forGetter(SpawnLightningAction::damage),
            Codec.BOOL.optionalFieldOf("visual_only", false).forGetter(SpawnLightningAction::visualOnly),
            Codec.BOOL.optionalFieldOf("cause", true).forGetter(SpawnLightningAction::cause)
    ).apply(i, SpawnLightningAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        FormulaContext formula = ctx.formula();
        double x = this.offsetX.evaluate(formula), y = this.offsetY.evaluate(formula), z = this.offsetZ.evaluate(formula);
        double damage = this.damage.evaluate(formula);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(damage)) return;
        ColoredLightningBolt bolt = MxtEntityTypes.COLORED_LIGHTNING.get().create(level, EntitySpawnReason.TRIGGERED);
        if (bolt == null) return;
        // Every visual value is set before the bolt enters the level, so the pairing bundle already carries it.
        bolt.setColor(this.color);
        bolt.setAlpha(this.alpha);
        bolt.setThickness(this.thickness);
        bolt.setVisualOnly(this.visualOnly);
        bolt.setDamage((float) Math.max(0.0D, damage));
        if (this.cause && entity instanceof ServerPlayer player) bolt.setCause(player);
        bolt.setPos(ctx.position().add(x, y, z));
        level.addFreshEntity(bolt);
    }

    @Override
    public @NonNull MapCodec<SpawnLightningAction> codec() {
        return CODEC;
    }
}
