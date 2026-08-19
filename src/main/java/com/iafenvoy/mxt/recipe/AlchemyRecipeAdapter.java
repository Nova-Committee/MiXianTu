package com.iafenvoy.mxt.recipe;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtRecipeSerializers;
import com.iafenvoy.mxt.registry.MxtRecipeTypes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

/**
 * Recipe-manager reference; AlchemySession owns temperature and output transactions.
 */
public record AlchemyRecipeAdapter(Identifier definition) implements Recipe<SingleRecipeInput> {
    public static final MapCodec<AlchemyRecipeAdapter> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("definition").forGetter(AlchemyRecipeAdapter::definition)
    ).apply(i, AlchemyRecipeAdapter::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, AlchemyRecipeAdapter> PACKET_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    @Override
    public boolean matches(SingleRecipeInput input, @NonNull Level level) {
        if (input.isEmpty()) return false;
        return MxtDatapackRegistries.get(MxtResourceKeys.ALCHEMY_RECIPE, this.definition)
                .filter(value -> value.inputs().size() == 1)
                .map(value -> value.inputs().getFirst().equals(BuiltInRegistries.ITEM.getKey(input.item().getItem())))
                .orElse(false);
    }

    @Override
    public @NonNull ItemStack assemble(SingleRecipeInput input) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public @NonNull String group() {
        return "mxt.alchemy";
    }

    @Override
    public @NonNull RecipeSerializer<? extends Recipe<SingleRecipeInput>> getSerializer() {
        return MxtRecipeSerializers.ALCHEMY.get();
    }

    @Override
    public @NonNull RecipeType<? extends Recipe<SingleRecipeInput>> getType() {
        return MxtRecipeTypes.ALCHEMY;
    }

    @Override
    public @NonNull PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public @NonNull RecipeBookCategory recipeBookCategory() {
        return new RecipeBookCategory();
    }
}
