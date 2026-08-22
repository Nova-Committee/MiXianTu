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
 * Recipe-manager bridge for refining one item into a declared artifact archetype.
 */
public record RefiningRecipeAdapter(Identifier input, Identifier archetype) implements Recipe<SingleRecipeInput> {
    public static final MapCodec<RefiningRecipeAdapter> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("input").forGetter(RefiningRecipeAdapter::input),
            Identifier.CODEC.fieldOf("archetype").forGetter(RefiningRecipeAdapter::archetype)
    ).apply(i, RefiningRecipeAdapter::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, RefiningRecipeAdapter> PACKET_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    @Override
    public boolean matches(SingleRecipeInput input, @NonNull Level level) {
        return !input.isEmpty() && MxtDatapackRegistries.get(MxtResourceKeys.ITEM_ARCHETYPE, this.archetype).isPresent()
                && this.input.equals(BuiltInRegistries.ITEM.getKey(input.item().getItem()));
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
