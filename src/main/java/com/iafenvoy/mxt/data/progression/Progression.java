package com.iafenvoy.mxt.data.progression;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * One level of a progression chain, written like a realm stage: the chain is the {@code next_level} line itself,
 * so the level nobody points at starts it and an owner enters it wherever its entry level lands. A level says
 * nothing about who climbs it - that belongs to the owning definition. {@code next_level} is a holder reference,
 * so a broken chain is only detectable at runtime, and {@code damage_multiplier} belongs to the powers the chain
 * grants at that level rather than to everything the holder does.
 */
public record Progression(Component name, Component description,
                          Optional<Holder<Progression>> nextLevel, NumberProvider mastery,
                          double damageMultiplier) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.PROGRESSION.identifier());
    public static final Codec<Holder<Progression>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.PROGRESSION);
    public static final Codec<Progression> DIRECT_CODEC = RecordCodecBuilder.<Progression>create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(Progression::name),
            ContextNameCodec.description(CATEGORY).forGetter(Progression::description),
            RegistryFixedCodec.create(MxtResourceKeys.PROGRESSION).optionalFieldOf("next_level").forGetter(Progression::nextLevel),
            NumberProvider.CODEC.optionalFieldOf("mastery", new Constant(0.0D)).forGetter(Progression::mastery),
            Codec.DOUBLE.optionalFieldOf("damage_multiplier", 1.0D).forGetter(Progression::damageMultiplier)
    ).apply(i, Progression::new)).validate(Progression::validate);

    private static DataResult<Progression> validate(Progression level) {
        if (!Double.isFinite(level.damageMultiplier) || level.damageMultiplier < 0.0D)
            return DataResult.error(() -> "Progression damage_multiplier must be finite and non-negative: " + level.damageMultiplier);
        return DataResult.success(level);
    }

    // The next-level link is a holder reference, so diagnostic output must remain shallow.
    @Override
    public @NonNull String toString() {
        return "Progression[hasNextLevel=" + this.nextLevel.isPresent()
                + ", damageMultiplier=" + this.damageMultiplier + "]";
    }
}
