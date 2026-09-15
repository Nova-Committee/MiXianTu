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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The benefit module: what the array hands to the entities it covers, and what it does to the ground.
 *
 * <p>There is no field here for attribute modifiers on purpose. A granted {@link Ability} already
 * carries its own {@code modifiers}, and the ability pipeline applies and removes them, so a second
 * entry point would be a second set of rules for the same thing — including the parts that are easy to
 * get wrong, such as a modifier outliving the array that granted it.</p>
 *
 * <p>{@code aura_zone} and {@code max_bonus} moved in here from the formation's own fields. They are
 * benefits and nothing else: a zone override raising the ceiling of the ground the array stands on is
 * something a cultivating array does, and neither an attack module nor a terrain ward has any use for
 * them. Keeping them at the top level made every definition carry two fields that only one kind of
 * array ever read.</p>
 *
 * <p>{@link TargetMode#ALLIES} asks the same friend system a hostile array asks, and answers the other
 * way: an entity nobody can identify is <em>not</em> given the benefit. Guessing "friend" would hand
 * the owner's cultivation bonus to a stranger, and the cost of guessing "stranger" is that a friend
 * outside every source's knowledge simply gets nothing.</p>
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
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
