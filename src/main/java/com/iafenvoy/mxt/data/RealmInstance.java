package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Datapack policy for a temporary secret-realm instance.
 */
public record RealmInstance(Optional<Identifier> dimension, long durationTicks, int maxMembers,
                            EntityAction enterAction, EntityAction exitAction) {
    public static final Codec<RealmInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.optionalFieldOf("dimension").forGetter(RealmInstance::dimension),
            MiscCodecs.longRange(0, Long.MAX_VALUE).optionalFieldOf("duration_ticks", 0L).forGetter(RealmInstance::durationTicks),
            Codec.intRange(1, 100_000).optionalFieldOf("max_members", 1).forGetter(RealmInstance::maxMembers),
            EntityAction.optionalCodec("enter_action").forGetter(RealmInstance::enterAction),
            EntityAction.optionalCodec("exit_action").forGetter(RealmInstance::exitAction)
    ).apply(i, RealmInstance::new));
}
