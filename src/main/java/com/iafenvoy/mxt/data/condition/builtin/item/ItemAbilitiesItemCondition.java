package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Asks which abilities the stack grants: what its artifact definition declares plus what the
 * {@code mxt:item_abilities} component lists, which is the union the ability runtime reads.
 */
public record ItemAbilitiesItemCondition(List<Either<Holder<Ability>, TagKey<Ability>>> abilities) implements ItemCondition {
    public static final MapCodec<ItemAbilitiesItemCondition> CODEC = RecordCodecBuilder.<ItemAbilitiesItemCondition>mapCodec(i -> i.group(
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).fieldOf("abilities").forGetter(ItemAbilitiesItemCondition::abilities)
    ).apply(i, ItemAbilitiesItemCondition::new)).validate(ItemAbilitiesItemCondition::validate);

    // An empty list can never match, so it is a condition that silently never passes: refused at load.
    private static DataResult<ItemAbilitiesItemCondition> validate(ItemAbilitiesItemCondition condition) {
        return condition.abilities().isEmpty()
                ? DataResult.error(() -> "mxt:item_abilities needs at least one ability to ask about")
                : DataResult.success(condition);
    }

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        return ArtifactService.abilities(ctx.holder().level().registryAccess(), ctx.stack()).stream()
                .anyMatch(ability -> RegistryCodecs.matches(this.abilities, ability));
    }

    @Override
    public @NonNull MapCodec<ItemAbilitiesItemCondition> codec() {
        return CODEC;
    }
}
