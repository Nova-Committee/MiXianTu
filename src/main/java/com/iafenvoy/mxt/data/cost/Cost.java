package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.function.Function;

/**
 * A server-side cost that can be checked and then consumed from a player.
 */
public interface Cost {
    Codec<Cost> TYPED_CODEC = MxtRegistries.COST_TYPE.byNameCodec().dispatch("type", Cost::codec, Function.identity());
    /**
     * Accepts the pre-Cost resource shorthand while new entries use a type discriminator.
     */
    Codec<Cost> CODEC = Codec.either(TYPED_CODEC, RecordCodecBuilder.<ResourceCost>create(i -> i.group(
            Resource.CODEC.fieldOf("id").forGetter(ResourceCost::resource),
            NumberProvider.CODEC.fieldOf("amount").forGetter(ResourceCost::amount)
    ).apply(i, ResourceCost::new))).xmap(
            value -> value.map(Function.identity(), Function.identity()),
            value -> value instanceof ResourceCost resource
                    ? Either.right(resource) : Either.left(value)
    );
    Codec<List<Cost>> LIST_CODEC = CODEC.listOf();

    boolean check(Player player);

    void consume(Player player);

    MapCodec<? extends Cost> codec();
}
