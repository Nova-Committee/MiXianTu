package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.registry.BehaviorReferences;
import com.iafenvoy.mxt.registry.BehaviorReferences.Reference;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Datapack policy for a temporary secret-realm instance.
 */
public record RealmInstance(Optional<Identifier> dimension, long durationTicks, int maxMembers,
                            Optional<Identifier> enterBehavior, Optional<Identifier> exitBehavior) {
    public static final Codec<RealmInstance> CODEC = RecordCodecBuilder.<RealmInstance>create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("dimension").forGetter(RealmInstance::dimension),
            MiscCodecs.longRange(0, Long.MAX_VALUE).optionalFieldOf("duration_ticks", 0L).forGetter(RealmInstance::durationTicks),
            Codec.intRange(1, 100_000).optionalFieldOf("max_members", 1).forGetter(RealmInstance::maxMembers),
            Identifier.CODEC.optionalFieldOf("enter_behavior").forGetter(RealmInstance::enterBehavior),
            Identifier.CODEC.optionalFieldOf("exit_behavior").forGetter(RealmInstance::exitBehavior)
    ).apply(instance, RealmInstance::new)).validate(value -> BehaviorReferences.validate(value, MxtTypeRegistries.REALM_LIFECYCLE_BEHAVIOR,
            new Reference("enter_behavior", value.enterBehavior),
            new Reference("exit_behavior", value.exitBehavior)));
}
