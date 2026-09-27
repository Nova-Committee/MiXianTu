package com.iafenvoy.mxt.runtime.alchemy;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum AlchemyFailure implements StringRepresentable {
    FURNACE_QUALITY,
    QUALITY_CONDITIONS,
    BINDING_CONDITIONS,
    UNBOUND,
    MAX_USES,
    COOLDOWN,
    NO_FURNACE,
    STRUCTURE,
    ACTIVE,
    CAPACITY,
    SLOTS,
    ZERO_POWER,
    NOT_HERB,
    INSUFFICIENT,
    CONFLICT,
    IMBALANCE,
    AMBIGUOUS,
    TEMPERATURE,
    ENVIRONMENT,
    INVALID_FORMULA,
    OUTPUT_CAPACITY,
    CANCELLED,
    DISABLED;

    public static final Codec<AlchemyFailure> CODEC = StringRepresentable.fromEnum(AlchemyFailure::values);

    @Override
    public String getSerializedName() {
        return this.name();
    }
}
