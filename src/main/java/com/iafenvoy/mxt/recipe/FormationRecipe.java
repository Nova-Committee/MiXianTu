package com.iafenvoy.mxt.recipe;

import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.registry.MxtRecipeSerializers;
import com.iafenvoy.mxt.registry.MxtRecipeTypes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

/**
 * Native recipe entry containing the complete formation payload.
 */
public record FormationRecipe(Formation definition) implements Recipe<SingleRecipeInput> {
    public static final MapCodec<FormationRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Formation.DIRECT_CODEC.fieldOf("definition").forGetter(FormationRecipe::definition)
    ).apply(i, FormationRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, FormationRecipe> PACKET_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    @Override
    public boolean matches(SingleRecipeInput input, @NonNull Level level) {
        return !input.isEmpty();
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
        return "mxt.formation";
    }

    @Override
    public @NonNull RecipeSerializer<? extends Recipe<SingleRecipeInput>> getSerializer() {
        return MxtRecipeSerializers.FORMATION.get();
    }

    @Override
    public @NonNull RecipeType<? extends Recipe<SingleRecipeInput>> getType() {
        return MxtRecipeTypes.FORMATION.get();
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
