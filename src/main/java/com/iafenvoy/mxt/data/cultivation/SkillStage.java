package com.iafenvoy.mxt.data.cultivation;

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
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * One level of a skill mastery chain, written like {@link RealmStage}. {@code skill} is the chain identity, not
 * one owner, so techniques share a chain and each enters at the stage it declares as default. {@code next_stage}
 * is a holder reference, so a broken chain is only detectable at runtime. {@code damage_multiplier} belongs to the
 * abilities the chain grants at that level, not to everything the holder does.
 */
public record SkillStage(Component name, Component description, Identifier skill,
                         Optional<Holder<SkillStage>> nextStage, NumberProvider mastery,
                         double damageMultiplier) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.SKILL_STAGE.identifier());
    public static final Codec<Holder<SkillStage>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.SKILL_STAGE);
    public static final Codec<SkillStage> DIRECT_CODEC = RecordCodecBuilder.<SkillStage>create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(SkillStage::name),
            ContextNameCodec.description(CATEGORY).forGetter(SkillStage::description),
            Identifier.CODEC.fieldOf("skill").forGetter(SkillStage::skill),
            RegistryFixedCodec.create(MxtResourceKeys.SKILL_STAGE).optionalFieldOf("next_stage").forGetter(SkillStage::nextStage),
            NumberProvider.CODEC.optionalFieldOf("mastery", new Constant(0.0D)).forGetter(SkillStage::mastery),
            Codec.DOUBLE.optionalFieldOf("damage_multiplier", 1.0D).forGetter(SkillStage::damageMultiplier)
    ).apply(i, SkillStage::new)).validate(SkillStage::validate);

    private static DataResult<SkillStage> validate(SkillStage stage) {
        if (!Double.isFinite(stage.damageMultiplier) || stage.damageMultiplier < 0.0D)
            return DataResult.error(() -> "Skill damage_multiplier must be finite and non-negative: " + stage.damageMultiplier);
        return DataResult.success(stage);
    }

    // The next-stage link is a holder reference, so diagnostic output must remain shallow.
    @Override
    public @NonNull String toString() {
        return "SkillStage[skill=" + this.skill + ", hasNextStage=" + this.nextStage.isPresent()
                + ", damageMultiplier=" + this.damageMultiplier + "]";
    }
}
