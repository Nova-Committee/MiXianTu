package com.iafenvoy.mxt.recipe;

import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.registry.MxtRecipeSerializers;
import com.iafenvoy.mxt.registry.MxtRecipeTypes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * A shaped recipe for the spirit crafting table, matched by vanilla's {@link ShapedRecipePattern}: the {@code key}
 * and {@code pattern} fields are vanilla's own, so a pattern is shrunk to the cells it uses and compared against
 * the rectangle the grid's items occupy. A layout therefore matches wherever it sits (and mirrored), and empty
 * rows or columns in the JSON are ignored. Completion also consumes the table's stored aura.
 */
public record SpiritShapedRecipe(ShapedRecipePattern pattern, ItemStackTemplate result,
                                 List<Cost> aura) implements SpiritRecipe {
    public static final MapCodec<SpiritShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ShapedRecipePattern.MAP_CODEC.forGetter(SpiritShapedRecipe::pattern),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(SpiritShapedRecipe::result),
            AURA_CODEC.fieldOf("aura").forGetter(SpiritShapedRecipe::aura)
    ).apply(i, SpiritShapedRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpiritShapedRecipe> PACKET_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    public SpiritShapedRecipe {
        if (aura.isEmpty()) throw new IllegalArgumentException("Spirit crafting recipes must require aura");
    }

    @Override
    public boolean matches(SpiritCraftingInput input, @NonNull Level level) {
        return this.pattern.matches(input.craftingInput());
    }

    @Override
    public @NonNull PlacementInfo placementInfo() {
        return PlacementInfo.createFromOptionals(this.pattern.ingredients());
    }

    @Override
    public @NonNull RecipeSerializer<? extends Recipe<SpiritCraftingInput>> getSerializer() {
        return MxtRecipeSerializers.SPIRIT_SHAPED.get();
    }

    @Override
    public @NonNull RecipeType<? extends Recipe<SpiritCraftingInput>> getType() {
        return MxtRecipeTypes.SPIRIT_SHAPED.get();
    }
}
