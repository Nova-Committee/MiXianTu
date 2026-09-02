package com.iafenvoy.mxt.recipe;

import com.iafenvoy.mxt.data.artifact.ItemArchetype;
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
 * Native recipe entry containing the complete artifact archetype payload.
 */
public record RefiningRecipe(Identifier input, ItemArchetype archetype) implements Recipe<SingleRecipeInput> {
    public static final MapCodec<RefiningRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("input").forGetter(RefiningRecipe::input),
            ItemArchetype.DIRECT_CODEC.fieldOf("archetype").forGetter(RefiningRecipe::archetype)
    ).apply(i, RefiningRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, RefiningRecipe> PACKET_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    @Override
    public boolean matches(SingleRecipeInput value, @NonNull Level level) {
        return !value.isEmpty() && this.input.equals(BuiltInRegistries.ITEM.getKey(value.item().getItem()));
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
        return "mxt.refining";
    }

    @Override
    public @NonNull RecipeSerializer<? extends Recipe<SingleRecipeInput>> getSerializer() {
        return MxtRecipeSerializers.REFINING.get();
    }

    @Override
    public @NonNull RecipeType<? extends Recipe<SingleRecipeInput>> getType() {
        return MxtRecipeTypes.REFINING.get();
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
