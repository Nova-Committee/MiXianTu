package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
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
 * True when the stack carries one of the listed spirit roots in its own {@code mxt:spirit_root} component, which is
 * what a spirit root stone states; a stack carrying no component answers no. An entry may be an id or a
 * {@code #tag}.
 */
public record ItemSpiritRootCondition(List<Either<Holder<SpiritRoot>, TagKey<SpiritRoot>>> spiritRoots) implements ItemCondition {
    public static final MapCodec<ItemSpiritRootCondition> CODEC = RecordCodecBuilder.<ItemSpiritRootCondition>mapCodec(i -> i.group(
            RegistryCodecs.holderOrTagList(MxtResourceKeys.SPIRIT_ROOT).fieldOf("spirit_roots").forGetter(ItemSpiritRootCondition::spiritRoots)
    ).apply(i, ItemSpiritRootCondition::new)).validate(ItemSpiritRootCondition::validate);

    // An empty list can never match, so it is a condition that silently never passes: refused at load.
    private static DataResult<ItemSpiritRootCondition> validate(ItemSpiritRootCondition condition) {
        return condition.spiritRoots().isEmpty()
                ? DataResult.error(() -> "mxt:item_spirit_root needs at least one spirit root to ask about")
                : DataResult.success(condition);
    }

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        Holder<SpiritRoot> root = ctx.stack().get(MxtDataComponents.SPIRIT_ROOT.get());
        return root != null && RegistryCodecs.matches(this.spiritRoots, root);
    }

    @Override
    public @NonNull MapCodec<ItemSpiritRootCondition> codec() {
        return CODEC;
    }
}
