package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueService;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;

/**
 * Asks which techniques the entity has learned, or whether it has learned any when the list is empty. A disabled
 * definition does not count, the way a disabled spirit root is not held; there is no per-technique enable switch.
 */
public record HasTechniqueEntityCondition(List<Either<Holder<Technique>, TagKey<Technique>>> techniques,
                                          Match match) implements EntityCondition {
    public static final MapCodec<HasTechniqueEntityCondition> CODEC = RecordCodecBuilder.<HasTechniqueEntityCondition>mapCodec(i -> i.group(
            RegistryCodecs.holderOrTagList(MxtResourceKeys.TECHNIQUE).optionalFieldOf("techniques", List.of()).forGetter(HasTechniqueEntityCondition::techniques),
            Match.CODEC.optionalFieldOf("match", Match.ANY).forGetter(HasTechniqueEntityCondition::match)
    ).apply(i, HasTechniqueEntityCondition::new)).validate(HasTechniqueEntityCondition::validate);

    // An empty list asks "learned anything", which all would read as a condition that always passes.
    private static DataResult<HasTechniqueEntityCondition> validate(HasTechniqueEntityCondition condition) {
        return condition.match() == Match.ALL && condition.techniques().isEmpty()
                ? DataResult.error(() -> "mxt:technique with match all has to name at least one technique")
                : DataResult.success(condition);
    }

    // Shared with the loot condition of the same name, so both answer the question the same way.
    public static boolean learned(SpiritIdentityAttachment spirit, List<Either<Holder<Technique>, TagKey<Technique>>> techniques, Match match) {
        List<Holder<Technique>> known = TechniqueService.known(spirit);
        if (techniques.isEmpty()) return !known.isEmpty();
        return match == Match.ALL
                ? techniques.stream().allMatch(asked -> holds(known, asked))
                : techniques.stream().anyMatch(asked -> holds(known, asked));
    }

    // Asked one written entry at a time, because a tag stands for several techniques and all is about that list.
    private static boolean holds(List<Holder<Technique>> known, Either<Holder<Technique>, TagKey<Technique>> asked) {
        return asked.map(entry -> known.stream().anyMatch(learned -> learned.value() == entry.value()),
                tag -> known.stream().anyMatch(learned -> learned.is(tag)));
    }

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        SpiritIdentityAttachment spirit = ctx.entity().getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
        return spirit != null && learned(spirit, this.techniques, this.match);
    }

    @Override
    public @NonNull MapCodec<HasTechniqueEntityCondition> codec() {
        return CODEC;
    }

    public enum Match implements StringRepresentable {
        ANY,
        ALL;

        public static final Codec<Match> CODEC = StringRepresentable.fromEnum(Match::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
