package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.storage.builtin.ToggleDataStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Reads the {@code mxt:toggle} state of a named host: the stored {@code state}, or the declaration's
 * {@code default} while nothing was written. A host that declares no such kind is false.
 */
public record StorageToggleEntityCondition(Identifier family, Identifier id,
                                           boolean expected) implements EntityCondition {
    public static final MapCodec<StorageToggleEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("family").forGetter(StorageToggleEntityCondition::family),
            Identifier.CODEC.fieldOf("id").forGetter(StorageToggleEntityCondition::id),
            Codec.BOOL.optionalFieldOf("expected", true).forGetter(StorageToggleEntityCondition::expected)
    ).apply(i, StorageToggleEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        StorageConditionReading reading = StorageConditionReading.of(ctx.entity(), this.family, this.id).orElse(null);
        if (reading == null) return false;
        Optional<ToggleDataStorage> declared = reading.declared(ToggleDataStorage.class);
        if (declared.isEmpty()) return false;
        boolean state = reading.stored(ToggleDataStorage.class).flatMap(ToggleDataStorage::state)
                .orElse(declared.get().defaultValue());
        return state == this.expected;
    }

    @Override
    public @NonNull MapCodec<StorageToggleEntityCondition> codec() {
        return CODEC;
    }
}
