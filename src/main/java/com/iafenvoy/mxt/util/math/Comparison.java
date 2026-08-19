package com.iafenvoy.mxt.util.math;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Origins-compatible comparison used by generic datapack conditions.
 */
public record Comparison(Operation operation, double compareTo) {
    public static final MapCodec<Comparison> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Operation.CODEC.fieldOf("comparison").forGetter(Comparison::operation),
            Codec.DOUBLE.fieldOf("compare_to").forGetter(Comparison::compareTo)
    ).apply(i, Comparison::new));

    public boolean compare(double value) {
        return this.operation.compare(value, this.compareTo);
    }

    public boolean compare(int value) {
        return this.compare((double) value);
    }

    public enum Operation implements StringRepresentable {
        LESS_THAN("<"), LESS_THAN_OR_EQUAL("<="), GREATER_THAN(">"),
        GREATER_THAN_OR_EQUAL(">="), EQUAL("=="), NOT_EQUAL("!=");

        public static final Codec<Operation> CODEC = StringRepresentable.fromEnum(Operation::values);
        private final String serializedName;

        Operation(String serializedName) {
            this.serializedName = serializedName;
        }

        public boolean compare(double value, double compareTo) {
            return switch (this) {
                case LESS_THAN -> value < compareTo;
                case LESS_THAN_OR_EQUAL -> value <= compareTo;
                case GREATER_THAN -> value > compareTo;
                case GREATER_THAN_OR_EQUAL -> value >= compareTo;
                case EQUAL -> Objects.equals(value, compareTo);
                case NOT_EQUAL -> !Objects.equals(value, compareTo);
            };
        }

        @Override
        public @NotNull String getSerializedName() {
            return this.serializedName;
        }
    }
}
