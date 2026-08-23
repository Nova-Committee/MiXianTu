package com.iafenvoy.mxt.recipe;

import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtRecipeSerializers;
import com.iafenvoy.mxt.registry.MxtRecipeTypes;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A shapeless recipe whose completion also consumes stored spirit power.
 */
public record SpiritShapelessRecipe(List<Ingredient> ingredients, ItemStackTemplate result,
                                    Map<Holder<Element>, NumberProvider> aura) implements SpiritRecipe {
    public static final MapCodec<SpiritShapelessRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.listOf(1, 9).fieldOf("ingredients").forGetter(SpiritShapelessRecipe::ingredients),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(SpiritShapelessRecipe::result),
            CollectionCodecs.map(Element.CODEC, NumberProvider.CODEC).fieldOf("aura").forGetter(SpiritShapelessRecipe::aura)
    ).apply(i, SpiritShapelessRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpiritShapelessRecipe> PACKET_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    public SpiritShapelessRecipe {
        if (aura.isEmpty()) throw new IllegalArgumentException("Spirit crafting recipes must require aura");
    }

    @Override
    public boolean matches(SpiritCraftingInput input, @NonNull Level level) {
        List<ItemStack> remaining = new ArrayList<>();
        for (int index = 0; index < input.size(); index++)
            if (!input.getItem(index).isEmpty()) remaining.add(input.getItem(index));
        if (remaining.size() != this.ingredients.size()) return false;
        boolean[] used = new boolean[remaining.size()];
        return this.match(0, remaining, used);
    }

    private boolean match(int ingredient, List<ItemStack> stacks, boolean[] used) {
        if (ingredient == this.ingredients.size()) return true;
        for (int index = 0; index < stacks.size(); index++)
            if (!used[index] && this.ingredients.get(ingredient).test(stacks.get(index))) {
                used[index] = true;
                if (this.match(ingredient + 1, stacks, used)) return true;
                used[index] = false;
            }
        return false;
    }

    @Override
    public @NonNull RecipeSerializer<? extends Recipe<SpiritCraftingInput>> getSerializer() {
        return MxtRecipeSerializers.SPIRIT_SHAPELESS.get();
    }

    @Override
    public @NonNull RecipeType<? extends Recipe<SpiritCraftingInput>> getType() {
        return MxtRecipeTypes.SPIRIT_SHAPELESS.get();
    }
}
