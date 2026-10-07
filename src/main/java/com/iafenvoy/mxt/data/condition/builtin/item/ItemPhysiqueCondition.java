package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
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
 * True when the stack carries one of the listed physiques in its own {@code mxt:physique} component, which is what a
 * physique stone states and the only place a stack states one - a stack carrying no component answers no. An entry
 * may be an id or a {@code #tag}, as everywhere else a list is asked about a definition.
 */
public record ItemPhysiqueCondition(List<Either<Holder<Physique>, TagKey<Physique>>> physiques) implements ItemCondition {
    public static final MapCodec<ItemPhysiqueCondition> CODEC = RecordCodecBuilder.<ItemPhysiqueCondition>mapCodec(i -> i.group(
            RegistryCodecs.holderOrTagList(MxtResourceKeys.PHYSIQUE).fieldOf("physiques").forGetter(ItemPhysiqueCondition::physiques)
    ).apply(i, ItemPhysiqueCondition::new)).validate(ItemPhysiqueCondition::validate);

    // An empty list can never match, so it is a condition that silently never passes: refused at load.
    private static DataResult<ItemPhysiqueCondition> validate(ItemPhysiqueCondition condition) {
        return condition.physiques().isEmpty()
                ? DataResult.error(() -> "mxt:item_physique needs at least one physique to ask about")
                : DataResult.success(condition);
    }

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        Holder<Physique> physique = ctx.stack().get(MxtDataComponents.PHYSIQUE.get());
        return physique != null && RegistryCodecs.matches(this.physiques, physique);
    }

    @Override
    public @NonNull MapCodec<ItemPhysiqueCondition> codec() {
        return CODEC;
    }
}
