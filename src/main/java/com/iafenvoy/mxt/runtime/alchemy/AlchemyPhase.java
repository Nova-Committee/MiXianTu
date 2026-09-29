package com.iafenvoy.mxt.runtime.alchemy;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

public enum AlchemyPhase implements StringRepresentable {
    IDLE, WARMING, RUNNING, READY;

    public static final Codec<AlchemyPhase> CODEC = StringRepresentable.fromEnum(AlchemyPhase::values);

    @Override
    public @NonNull String getSerializedName() {
        return this.name();
    }
}
