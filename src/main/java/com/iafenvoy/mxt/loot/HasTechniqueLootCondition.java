package com.iafenvoy.mxt.loot;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.condition.builtin.entity.HasTechniqueEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.HasTechniqueEntityCondition.Match;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContext.EntityTarget;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.NonNull;

import java.util.List;

// Same shape as the entity condition, plus the target the loot table asks about.
public record HasTechniqueLootCondition(EntityTarget target,
                                        List<Either<Holder<Technique>, TagKey<Technique>>> techniques,
                                        Match match) implements LootItemCondition {
    public static final MapCodec<HasTechniqueLootCondition> CODEC = RecordCodecBuilder.<HasTechniqueLootCondition>mapCodec(i -> i.group(
            EntityTarget.CODEC.optionalFieldOf("entity", EntityTarget.THIS).forGetter(HasTechniqueLootCondition::target),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.TECHNIQUE).optionalFieldOf("techniques", List.of()).forGetter(HasTechniqueLootCondition::techniques),
            Match.CODEC.optionalFieldOf("match", Match.ANY).forGetter(HasTechniqueLootCondition::match)
    ).apply(i, HasTechniqueLootCondition::new)).validate(HasTechniqueLootCondition::validate);

    private static DataResult<HasTechniqueLootCondition> validate(HasTechniqueLootCondition condition) {
        return condition.match() == Match.ALL && condition.techniques().isEmpty()
                ? DataResult.error(() -> "mxt:technique with match all has to name at least one technique")
                : DataResult.success(condition);
    }

    @Override
    public boolean test(LootContext context) {
        Entity entity = this.target.get(context);
        SpiritIdentityAttachment spirit = entity == null ? null
                : entity.getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
        return spirit != null && HasTechniqueEntityCondition.learned(spirit, this.techniques, this.match);
    }

    @Override
    public @NonNull MapCodec<HasTechniqueLootCondition> codec() {
        return CODEC;
    }
}
