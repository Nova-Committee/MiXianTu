package com.iafenvoy.mxt.data.formation;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.resource.Resource;
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
 * The benefit module: what the array hands to the entities it covers, and what it does to the ground. No
 * field for attribute modifiers, because a granted {@link Ability} already carries its own {@code modifiers}.
 * {@code aura_zone} and {@code max_bonus} are benefits, so they live here rather than on every formation.
 * Under {@link TargetMode#ALLIES} an unidentifiable entity is not given the benefit.
 */
public record BuffFormationAction(List<Holder<Ability>> abilities, TargetMode target,
                                  Optional<Holder<AuraZone>> auraZone,
                                  Map<Holder<Resource>, NumberProvider> maxBonus) implements FormationActionType {
    public static final MapCodec<BuffFormationAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ability.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(BuffFormationAction::abilities),
            TargetMode.CODEC.optionalFieldOf("target", TargetMode.ALL).forGetter(BuffFormationAction::target),
            AuraZone.CODEC.optionalFieldOf("aura_zone").forGetter(BuffFormationAction::auraZone),
            CollectionCodecs.map(Resource.CODEC, NumberProvider.CODEC).optionalFieldOf("max_bonus", Map.of()).forGetter(BuffFormationAction::maxBonus)
    ).apply(i, BuffFormationAction::new));

    @Override
    public MapCodec<BuffFormationAction> codec() {
        return CODEC;
    }

    /**
     * Who receives the benefit.
     */
    public enum TargetMode implements StringRepresentable {
        /// Everyone the array covers, friends and strangers alike.
        ALL,
        /// The owner and whoever the owner's friend sources recognise. An unidentifiable entity gets nothing.
        ALLIES,
        /// Only the owner.
        OWNER;

        public static final Codec<TargetMode> CODEC = StringRepresentable.fromEnum(TargetMode::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
