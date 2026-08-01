package com.iafenvoy.mxt.data.world;

import com.iafenvoy.mxt.registry.BehaviorReferences;
import com.iafenvoy.mxt.registry.BehaviorReferences.Reference;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Datapack policy for a temporary secret-realm instance.
 */
public record RealmInstanceDefinition(Optional<Identifier> dimension,
                                      long durationTicks, int maxMembers, Optional<Identifier> enterBehavior,
                                      Optional<Identifier> exitBehavior) {
    public static final Codec<RealmInstanceDefinition> CODEC = RecordCodecBuilder.<RealmInstanceDefinition>create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("dimension").forGetter(RealmInstanceDefinition::dimension), Codec.LONG.optionalFieldOf("duration_ticks", 0L).forGetter(RealmInstanceDefinition::durationTicks),
            Codec.intRange(1, 100_000).optionalFieldOf("max_members", 1).forGetter(RealmInstanceDefinition::maxMembers), Identifier.CODEC.optionalFieldOf("enter_behavior").forGetter(RealmInstanceDefinition::enterBehavior),
            Identifier.CODEC.optionalFieldOf("exit_behavior").forGetter(RealmInstanceDefinition::exitBehavior)
    ).apply(instance, RealmInstanceDefinition::new)).validate(value -> {
        if (value.durationTicks < 0L)
            return DataResult.error(() -> "duration_ticks cannot be negative");
        return BehaviorReferences.validate(value, MxtTypeRegistries.REALM_LIFECYCLE_BEHAVIOR,
                new Reference("enter_behavior", value.enterBehavior),
                new Reference("exit_behavior", value.exitBehavior));
    });
}
