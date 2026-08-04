package com.iafenvoy.mxt.data.action.builtin.bientity;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Space;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.function.BiFunction;

public record AddVelocityAction(float x, float y, float z, Reference reference, boolean client, boolean server,
                                boolean set) implements BiEntityAction {
    public static final MapCodec<AddVelocityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("x", 0.0F).forGetter(AddVelocityAction::x),
            Codec.FLOAT.optionalFieldOf("y", 0.0F).forGetter(AddVelocityAction::y),
            Codec.FLOAT.optionalFieldOf("z", 0.0F).forGetter(AddVelocityAction::z),
            Reference.CODEC.optionalFieldOf("reference", Reference.POSITION).forGetter(AddVelocityAction::reference),
            Codec.BOOL.optionalFieldOf("client", true).forGetter(AddVelocityAction::client),
            Codec.BOOL.optionalFieldOf("server", true).forGetter(AddVelocityAction::server),
            Codec.BOOL.optionalFieldOf("set", false).forGetter(AddVelocityAction::set)
    ).apply(instance, AddVelocityAction::new));

    @Override
    public void execute(Entity actor, Entity target, FormulaContext context) {
        if ((target.level().isClientSide() && !this.client) || (!target.level().isClientSide() && !this.server)) return;
        Vector3f value = new Vector3f(this.x, this.y, this.z);
        Space.transformVectorToBase(this.reference.apply(actor, target), value, actor.getYRot(), true);
        if (this.set) target.setDeltaMovement(new Vec3(value));
        else target.addDeltaMovement(new Vec3(value));
        target.hurtMarked = true;
    }

    @Override
    public MapCodec<AddVelocityAction> codec() {
        return CODEC;
    }

    public enum Reference implements StringRepresentable {
        POSITION((actor, target) -> target.position().subtract(actor.position())),
        ROTATION((actor, target) -> {
            float pitch = actor.getXRot() * Mth.DEG_TO_RAD;
            float yaw = actor.getYRot() * Mth.DEG_TO_RAD;
            return new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
        });

        public static final Codec<Reference> CODEC = StringRepresentable.fromEnum(Reference::values);
        private final BiFunction<Entity, Entity, Vec3> function;

        Reference(BiFunction<Entity, Entity, Vec3> function) {
            this.function = function;
        }

        public Vec3 apply(Entity actor, Entity target) {
            return this.function.apply(actor, target);
        }

        @Override
        public @NotNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
