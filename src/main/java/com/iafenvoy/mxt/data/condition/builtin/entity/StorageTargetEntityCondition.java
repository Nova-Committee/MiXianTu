package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.storage.TargetLockDataStorage;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads the {@code target} of the {@code mxt:target_lock} of a named host, optionally requiring that entity to
 * still be in the actor's level within {@code max_distance}; a host that declares no such kind is false.
 */
public record StorageTargetEntityCondition(Identifier family, Identifier id, boolean locked,
                                           Optional<NumberProvider> maxDistance) implements EntityCondition {
    public static final MapCodec<StorageTargetEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("family").forGetter(StorageTargetEntityCondition::family),
            Identifier.CODEC.fieldOf("id").forGetter(StorageTargetEntityCondition::id),
            Codec.BOOL.optionalFieldOf("locked", true).forGetter(StorageTargetEntityCondition::locked),
            NumberProvider.CODEC.optionalFieldOf("max_distance").forGetter(StorageTargetEntityCondition::maxDistance)
    ).apply(i, StorageTargetEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        StorageConditionReading reading = StorageConditionReading.of(ctx.entity(), this.family, this.id).orElse(null);
        if (reading == null) return false;
        if (reading.declared(TargetLockDataStorage.class).isEmpty()) return false;
        Optional<String> target = reading.stored(TargetLockDataStorage.class).flatMap(TargetLockDataStorage::target);
        if (target.isPresent() != this.locked) return false;
        if (this.maxDistance.isEmpty()) return true;
        // A distance can only be asked of a target that is stored and still in the level.
        UUID targetId = parse(target.orElse(null));
        if (targetId == null) return false;
        Entity lockedEntity = ctx.entity().level().getEntity(targetId);
        if (lockedEntity == null) return false;
        double distance = this.maxDistance.get().evaluate(ctx.formula());
        return Double.isFinite(distance) && distance >= 0.0D && ctx.entity().distanceTo(lockedEntity) <= distance;
    }

    // A stored uuid is data-pack content, so a malformed one names no entity instead of failing the test.
    private static @Nullable UUID parse(@Nullable String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @Override
    public @NonNull MapCodec<StorageTargetEntityCondition> codec() {
        return CODEC;
    }
}
