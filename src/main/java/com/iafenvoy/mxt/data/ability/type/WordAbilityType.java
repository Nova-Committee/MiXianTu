package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;

/**
 * High-tier, code-whitelisted word effects. Datapacks cannot supply an arbitrary command string.
 */
public record WordAbilityType(WordEffect effect, boolean requiresOperator,
                              NumberProvider amount) implements AbilityType {
    public static final MapCodec<WordAbilityType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            WordEffect.CODEC.fieldOf("effect").forGetter(WordAbilityType::effect),
            Codec.BOOL.optionalFieldOf("requires_operator", true).forGetter(WordAbilityType::requiresOperator),
            NumberProvider.CODEC.optionalFieldOf("amount", new Constant(0.0D)).forGetter(WordAbilityType::amount)
    ).apply(i, WordAbilityType::new));

    @Override
    public MapCodec<WordAbilityType> codec() {
        return CODEC;
    }

    public enum WordEffect {
        SELF_HEAL,
        PURGE_SELF_CURSES;

        public static final Codec<WordEffect> CODEC = Codec.STRING.comapFlatMap(value -> {
            try {
                return DataResult.success(valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(() -> "Unknown word effect " + value);
            }
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }
}
