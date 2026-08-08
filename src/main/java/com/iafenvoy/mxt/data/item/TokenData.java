package com.iafenvoy.mxt.data.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Generic identity and permission payload for sect, realm and trading tokens.
 */
public record TokenData(Optional<String> kind, Optional<String> value, Optional<String> owner) {
    public static final TokenData EMPTY = new TokenData(Optional.empty(), Optional.empty(), Optional.empty());
    public static final Codec<TokenData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("kind").forGetter(TokenData::kind),
            Codec.STRING.optionalFieldOf("value").forGetter(TokenData::value),
            Codec.STRING.optionalFieldOf("owner").forGetter(TokenData::owner)
    ).apply(instance, TokenData::new));
}
