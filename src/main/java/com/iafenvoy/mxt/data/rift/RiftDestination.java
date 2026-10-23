package com.iafenvoy.mxt.data.rift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public sealed interface RiftDestination {
    MapCodec<Position> POSITION = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.optionalFieldOf("dimension").forGetter(Position::dimension),
            Vec3.CODEC.fieldOf("pos").forGetter(Position::pos),
            Codec.FLOAT.optionalFieldOf("yaw").forGetter(Position::yaw),
            Codec.FLOAT.optionalFieldOf("pitch").forGetter(Position::pitch),
            Codec.BOOL.optionalFieldOf("nearby", false).forGetter(Position::nearby)
    ).apply(i, Position::new));
    MapCodec<Realm> REALM = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("realm").forGetter(Realm::realm)
    ).apply(i, Realm::new));
    MapCodec<Return> RETURN = MapCodec.unit(Return::new);
    Codec<RiftDestination> CODEC = Codec.STRING.<RiftDestination>partialDispatch("type", value -> DataResult.success(value.type()),
            type -> switch (type) {
                case "position" -> DataResult.success(POSITION);
                case "secret_realm" -> DataResult.success(REALM);
                case "return" -> DataResult.success(RETURN);
                default -> DataResult.error(() -> "Unknown rift destination: " + type);
            }).validate(value -> value instanceof Position p && (!RiftPlacement.finite(p.pos())
            || Math.abs(p.pos().x) > 30_000_000 || Math.abs(p.pos().y) > 30_000_000 || Math.abs(p.pos().z) > 30_000_000
            || p.yaw().filter(v -> !Float.isFinite(v)).isPresent()
            || p.pitch().filter(v -> !Float.isFinite(v) || Math.abs(v) > 90).isPresent())
            ? DataResult.error(() -> "Invalid rift destination") : DataResult.success(value));

    String type();

    default RiftDestination resolve(Identifier dimension) {
        return this instanceof Position(
                Optional<Identifier> dimension1, Vec3 pos, Optional<Float> yaw, Optional<Float> pitch, boolean nearby
        ) && dimension1.isEmpty()
                ? new Position(Optional.of(dimension), pos, yaw, pitch, nearby) : this;
    }

    record Position(Optional<Identifier> dimension, Vec3 pos, Optional<Float> yaw, Optional<Float> pitch,
                    boolean nearby) implements RiftDestination {
        @Override
        public String type() {
            return "position";
        }
    }

    record Realm(Identifier realm) implements RiftDestination {
        @Override
        public String type() {
            return "secret_realm";
        }
    }

    record Return() implements RiftDestination {
        @Override
        public String type() {
            return "return";
        }
    }
}
