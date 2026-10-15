package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.runtime.sword.SwordAuraEntity;
import com.iafenvoy.mxt.util.MiscUtil;
import com.iafenvoy.mxt.util.OptionalBoolean;
import com.iafenvoy.mxt.util.OptionalFloat;
import com.iafenvoy.mxt.util.codec.OptionalCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.OptionalInt;

public record SpawnSwordAuraAction(NumberProvider speed, OptionalInt bladeColor, OptionalInt auraColor,
                                   OptionalBoolean radialFlame, OptionalFloat length, OptionalFloat bladeWidth,
                                   OptionalFloat thickness, OptionalFloat handleLength, OptionalFloat guardWidth,
                                   OptionalFloat scale, OptionalInt lifetime,
                                   EntityAction collideAction) implements EntityAction {
    public static final MapCodec<SpawnSwordAuraAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.optionalFieldOf("speed", new Constant(1.0D)).forGetter(SpawnSwordAuraAction::speed),
            OptionalCodecs.rgbColorCodec("blade_color").forGetter(SpawnSwordAuraAction::bladeColor),
            OptionalCodecs.rgbColorCodec("aura_color").forGetter(SpawnSwordAuraAction::auraColor),
            OptionalCodecs.boolCodec("radial_flame").forGetter(SpawnSwordAuraAction::radialFlame),
            OptionalCodecs.floatCodec(0.01F, 32.0F, "length").forGetter(SpawnSwordAuraAction::length),
            OptionalCodecs.floatCodec(0.01F, 32.0F, "blade_width").forGetter(SpawnSwordAuraAction::bladeWidth),
            OptionalCodecs.floatCodec(0.01F, 32.0F, "thickness").forGetter(SpawnSwordAuraAction::thickness),
            OptionalCodecs.floatCodec(0.01F, 32.0F, "handle_length").forGetter(SpawnSwordAuraAction::handleLength),
            OptionalCodecs.floatCodec(0.01F, 32.0F, "guard_width").forGetter(SpawnSwordAuraAction::guardWidth),
            OptionalCodecs.floatCodec(0.01F, 32.0F, "scale").forGetter(SpawnSwordAuraAction::scale),
            OptionalCodecs.intCodec(1, 72000, "lifetime").forGetter(SpawnSwordAuraAction::lifetime),
            EntityAction.optionalCodec("collide_action").forGetter(SpawnSwordAuraAction::collideAction)
    ).apply(i, SpawnSwordAuraAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity source = ctx.entity();
        if (source.level().isClientSide()) return;

        FormulaContext formula = ctx.formula();
        double speed = MiscUtil.positive(this.speed.evaluate(formula));
        if (speed <= 0.0D) return;

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
        if (source instanceof LivingEntity living) aura.setCollideAction(living, this.collideAction);
        source.level().addFreshEntity(aura);
    }

    @Override
    public @NonNull MapCodec<SpawnSwordAuraAction> codec() {
        return CODEC;
    }
}
