package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.math.Space;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public record AddVelocityAction(float x, float y, float z, Space space, boolean set) implements EntityAction {
    public static final MapCodec<AddVelocityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("x", 0.0F).forGetter(AddVelocityAction::x),
            Codec.FLOAT.optionalFieldOf("y", 0.0F).forGetter(AddVelocityAction::y),
            Codec.FLOAT.optionalFieldOf("z", 0.0F).forGetter(AddVelocityAction::z),
            Space.CODEC.optionalFieldOf("space", Space.WORLD).forGetter(AddVelocityAction::space),
            Codec.BOOL.optionalFieldOf("set", false).forGetter(AddVelocityAction::set)
    ).apply(instance, AddVelocityAction::new));

    @Override
    public void execute(Entity entity) {
        Vector3f velocity = new Vector3f(this.x, this.y, this.z);
        this.space.toGlobal(velocity, entity);
        Vec3 value = new Vec3(velocity);
        if (this.set) entity.setDeltaMovement(value);
        else entity.addDeltaMovement(value);
        entity.hurtMarked = true;
    }

    @Override
    public MapCodec<AddVelocityAction> codec() {
        return CODEC;
    }
}
