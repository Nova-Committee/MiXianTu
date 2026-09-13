package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * One level of a skill mastery chain, written like {@link RealmStage}: the stage names the chain it
 * belongs to, may point at the next stage, and carries what that level is worth.
 *
 * <p>{@code skill} is the chain identity rather than a reference to one owner, so several
 * techniques - or a technique and some other system - may share a single chain, and each definition
 * enters that chain through the stage it declares as its default.</p>
 *
 * <p>{@code next_stage} is a holder reference, exactly like {@code next_realm}: the chain is only
 * walked at runtime and never while an entry is being decoded, which is also where a broken chain
 * (a stage that points at another {@code skill}, a cycle, or a missing stage) has to be detected.</p>
 */
public record SkillStage(Identifier skill, Optional<Holder<SkillStage>> nextStage, double damageMultiplier) {
    public static final Codec<Holder<SkillStage>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.SKILL_STAGE);
    public static final Codec<SkillStage> DIRECT_CODEC = RecordCodecBuilder.<SkillStage>create(i -> i.group(
            Identifier.CODEC.fieldOf("skill").forGetter(SkillStage::skill),
            RegistryFixedCodec.create(MxtResourceKeys.SKILL_STAGE).optionalFieldOf("next_stage").forGetter(SkillStage::nextStage),
            Codec.DOUBLE.optionalFieldOf("damage_multiplier", 1.0D).forGetter(SkillStage::damageMultiplier)
    ).apply(i, SkillStage::new)).validate(SkillStage::validate);

    private static DataResult<SkillStage> validate(SkillStage stage) {
        if (!Double.isFinite(stage.damageMultiplier) || stage.damageMultiplier < 0.0D)
            return DataResult.error(() -> "Skill damage_multiplier must be finite and non-negative: " + stage.damageMultiplier);
        return DataResult.success(stage);
    }

    /**
     * The next-stage link is a holder reference, so diagnostic output must remain shallow.
     */
    @Override
    public @NonNull String toString() {
        return "SkillStage[skill=" + this.skill + ", hasNextStage=" + this.nextStage.isPresent()
                + ", damageMultiplier=" + this.damageMultiplier + "]";
    }
}
