package com.iafenvoy.mxt.data.formation;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The benefit module: what the array hands to the entities it covers, and what it does to the ground. No field for
 * attribute modifiers, because a granted {@link Ability} already carries its own {@code modifiers}. Under
 * {@link TargetMode#ALLIES} an unidentifiable entity is not given the benefit.
 */
public record BuffFormationAction(List<Holder<Ability>> abilities, TargetMode target,
                                  Optional<Holder<AuraZone>> auraZone,
                                  Map<Holder<Aura>, NumberProvider> maxBonus) implements FormationActionType {
    public static final MapCodec<BuffFormationAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ability.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(BuffFormationAction::abilities),
            TargetMode.CODEC.optionalFieldOf("target", TargetMode.ALL).forGetter(BuffFormationAction::target),
            AuraZone.CODEC.optionalFieldOf("aura_zone").forGetter(BuffFormationAction::auraZone),
            CollectionCodecs.map(Aura.CODEC, NumberProvider.CODEC).optionalFieldOf("max_bonus", Map.of()).forGetter(BuffFormationAction::maxBonus)
    ).apply(i, BuffFormationAction::new));

    @Override
    public MapCodec<BuffFormationAction> codec() {
        return CODEC;
    }

    public enum TargetMode implements StringRepresentable {
        ALL,
        ALLIES,
        OWNER;

        public static final Codec<TargetMode> CODEC = StringRepresentable.fromEnum(TargetMode::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
