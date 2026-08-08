package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.RealmInstance;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.Optional;

/**
 * Datapack-selected realm instance carried by an access token.
 */
public record RealmTokenData(Optional<Holder<RealmInstance>> realm) {
    public static final RealmTokenData EMPTY = new RealmTokenData(Optional.empty());
    public static final Codec<RealmTokenData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RegistryFixedCodec.create(MxtDatapackRegistries.REALM_INSTANCE).optionalFieldOf("realm").forGetter(RealmTokenData::realm)
    ).apply(instance, RealmTokenData::new));
}
