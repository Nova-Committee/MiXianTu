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
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Strikes a lightning bolt at the actor with a colour, glow, thickness and optional gradient. Everything the
 * bolt then does — damage, fire, rods, copper, thunder — stays the vanilla bolt's own behaviour.
 */
public record SpawnLightningAction(NumberProvider offsetX, NumberProvider offsetY, NumberProvider offsetZ,
                                   int color, float alpha, float thickness, List<Integer> palette, NumberProvider damage,
                                   boolean visualOnly, boolean cause) implements EntityAction {
    public static final MapCodec<SpawnLightningAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.optionalFieldOf("offset_x", new Constant(0.0D)).forGetter(SpawnLightningAction::offsetX),
            NumberProvider.CODEC.optionalFieldOf("offset_y", new Constant(0.0D)).forGetter(SpawnLightningAction::offsetY),
            NumberProvider.CODEC.optionalFieldOf("offset_z", new Constant(0.0D)).forGetter(SpawnLightningAction::offsetZ),
            MiscCodecs.COLOR_NO_ALPHA.optionalFieldOf("color", ColoredLightningBolt.DEFAULT_COLOR).forGetter(SpawnLightningAction::color),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("alpha", ColoredLightningBolt.DEFAULT_ALPHA).forGetter(SpawnLightningAction::alpha),
            Codec.floatRange(ColoredLightningBolt.MIN_THICKNESS, ColoredLightningBolt.MAX_THICKNESS)
                    .optionalFieldOf("thickness", ColoredLightningBolt.DEFAULT_THICKNESS).forGetter(SpawnLightningAction::thickness),
            // Strict rather than tolerant: a mistyped colour in a gradient is a typo the pack author wants to
            // see, and the synched data channel cannot carry more entries than the bolt accepts anyway.
            Codec.list(MiscCodecs.COLOR_NO_ALPHA).validate(SpawnLightningAction::validatePalette)
                    .optionalFieldOf("palette", List.of()).forGetter(SpawnLightningAction::palette),
            NumberProvider.CODEC.optionalFieldOf("damage", new Constant(5.0D)).forGetter(SpawnLightningAction::damage),
            Codec.BOOL.optionalFieldOf("visual_only", false).forGetter(SpawnLightningAction::visualOnly),
            Codec.BOOL.optionalFieldOf("cause", true).forGetter(SpawnLightningAction::cause)
    ).apply(i, SpawnLightningAction::new));

    public SpawnLightningAction {
        palette = List.copyOf(palette);
    }

    private static DataResult<List<Integer>> validatePalette(List<Integer> palette) {
        return palette.size() <= ColoredLightningBolt.MAX_PALETTE
                ? DataResult.success(palette)
                : DataResult.error(() -> "A lightning palette holds at most " + ColoredLightningBolt.MAX_PALETTE + " colours, got " + palette.size());
    }

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
        bolt.setPalette(this.palette);
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
