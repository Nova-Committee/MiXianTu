package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.runtime.sword.SwordAuraEntity;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

public record SpawnSwordAuraAction(NumberProvider speed, Optional<Integer> bladeColor, Optional<Integer> auraColor,
                                   Optional<Boolean> radialFlame, Optional<Float> length, Optional<Float> bladeWidth,
                                   Optional<Float> thickness, Optional<Float> handleLength, Optional<Float> guardWidth,
                                   Optional<Float> scale, Optional<Integer> lifetime,
                                   EntityAction collideAction) implements EntityAction {
    public static final MapCodec<SpawnSwordAuraAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.optionalFieldOf("speed", new Constant(1.0D)).forGetter(SpawnSwordAuraAction::speed),
            ExtraCodecs.STRING_ARGB_COLOR.optionalFieldOf("blade_color").forGetter(SpawnSwordAuraAction::bladeColor),
            ExtraCodecs.STRING_ARGB_COLOR.optionalFieldOf("aura_color").forGetter(SpawnSwordAuraAction::auraColor),
            Codec.BOOL.optionalFieldOf("radial_flame").forGetter(SpawnSwordAuraAction::radialFlame),
            Codec.floatRange(0.01F, 32.0F).optionalFieldOf("length").forGetter(SpawnSwordAuraAction::length),
            Codec.floatRange(0.01F, 32.0F).optionalFieldOf("blade_width").forGetter(SpawnSwordAuraAction::bladeWidth),
            Codec.floatRange(0.01F, 32.0F).optionalFieldOf("thickness").forGetter(SpawnSwordAuraAction::thickness),
            Codec.floatRange(0.01F, 32.0F).optionalFieldOf("handle_length").forGetter(SpawnSwordAuraAction::handleLength),
            Codec.floatRange(0.01F, 32.0F).optionalFieldOf("guard_width").forGetter(SpawnSwordAuraAction::guardWidth),
            Codec.floatRange(0.01F, 32.0F).optionalFieldOf("scale").forGetter(SpawnSwordAuraAction::scale),
            Codec.intRange(1, 72000).optionalFieldOf("lifetime").forGetter(SpawnSwordAuraAction::lifetime),
            EntityAction.optionalCodec("collide_action").forGetter(SpawnSwordAuraAction::collideAction)
    ).apply(i, SpawnSwordAuraAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity source = ctx.entity();
        if (source.level().isClientSide()) return;

        FormulaContext formula = ctx.formula();
        double speed = this.speed.evaluate(formula);
        if (!Double.isFinite(speed) || speed <= 0.0D) return;

        Vec3 velocity = source.getLookAngle().scale(speed);
        if (!Double.isFinite(velocity.x) || !Double.isFinite(velocity.y) || !Double.isFinite(velocity.z) || velocity.lengthSqr() <= 1.0E-8D)
            return;

        SwordAuraEntity aura = new SwordAuraEntity(source.level(), ctx.launchPosition(), velocity);
        this.bladeColor.ifPresent(aura::setBladeColor);
        this.auraColor.ifPresent(aura::setAuraColor);
        this.radialFlame.ifPresent(aura::setRadialFlame);
        this.length.ifPresent(aura::setLength);
        this.bladeWidth.ifPresent(aura::setBladeWidth);
        this.thickness.ifPresent(aura::setThickness);
        this.handleLength.ifPresent(aura::setHandleLength);
        this.guardWidth.ifPresent(aura::setGuardWidth);
        this.scale.ifPresent(aura::setScale);
        this.lifetime.ifPresent(aura::setLifetime);
        aura.setCollideAction(source, this.collideAction);
        source.level().addFreshEntity(aura);
    }

    @Override
    public @NonNull MapCodec<SpawnSwordAuraAction> codec() {
        return CODEC;
    }
}
