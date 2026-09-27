package com.iafenvoy.mxt.runtime.alchemy;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum AlchemyPhase implements StringRepresentable {
    IDLE, WARMING, RUNNING, READY;

    public static final Codec<AlchemyPhase> CODEC = StringRepresentable.fromEnum(AlchemyPhase::values);

    @Override
    public String getSerializedName() {
        return this.name();
    }
}
