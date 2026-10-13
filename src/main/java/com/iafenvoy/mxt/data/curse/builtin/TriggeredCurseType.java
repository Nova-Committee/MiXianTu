package com.iafenvoy.mxt.data.curse.builtin;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.curse.CurseType;
import com.iafenvoy.mxt.data.trigger.Trigger;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.OptionalLong;

// Periodic behaviour comes from the trigger system instead of a tick interval: while the curse is held, every
// signal one of its triggers matches runs on_tick once. Lasts like mxt:timed with a duration, else permanent.
public record TriggeredCurseType(List<Trigger> triggers) implements CurseType {
    public static final MapCodec<TriggeredCurseType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Trigger.CODEC.listOf().optionalFieldOf("triggers", List.of()).forGetter(TriggeredCurseType::triggers)
    ).apply(i, TriggeredCurseType::new));

    public TriggeredCurseType {
        triggers = List.copyOf(triggers);
    }

    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "triggered");
    }

    @Override
    public MapCodec<TriggeredCurseType> codec() {
        return CODEC;
    }

    @Override
    public OptionalLong expiry(long duration, long gameTime) {
        return duration <= 0L ? OptionalLong.of(-1L) : OptionalLong.of(Math.addExact(gameTime, duration));
    }
}
