package com.iafenvoy.mxt.recipe;

import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtRecipeSerializers;
import com.iafenvoy.mxt.registry.MxtRecipeTypes;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

/**
 * A shaped recipe whose completion also consumes stored spirit power.
 */
public record SpiritShapedRecipe(List<String> pattern, Map<String, Ingredient> key, ItemStackTemplate result,
                                 Map<Holder<Resource>, NumberProvider> aura) implements SpiritRecipe {
    public static final MapCodec<SpiritShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.listOf(1, 3).fieldOf("pattern").forGetter(SpiritShapedRecipe::pattern),
            Codec.unboundedMap(Codec.STRING, Ingredient.CODEC).fieldOf("key").forGetter(SpiritShapedRecipe::key),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(SpiritShapedRecipe::result),
            CollectionCodecs.map(Resource.CODEC, NumberProvider.CODEC).fieldOf("aura").forGetter(SpiritShapedRecipe::aura)
    ).apply(i, SpiritShapedRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpiritShapedRecipe> PACKET_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    public SpiritShapedRecipe {
        if (pattern.isEmpty() || pattern.stream().anyMatch(row -> row.isEmpty() || row.length() > 3))
            throw new IllegalArgumentException("Spirit shaped recipe pattern must be 1x1 to 3x3");
        if (key.keySet().stream().anyMatch(value -> value.length() != 1 || value.charAt(0) == ' '))
            throw new IllegalArgumentException("Spirit shaped recipe keys must be single non-space characters");
        if (aura.isEmpty()) throw new IllegalArgumentException("Spirit crafting recipes must require aura");
    }

    @Override
    public boolean matches(SpiritCraftingInput input, @NonNull Level level) {
        int height = this.pattern.size(), width = this.pattern.stream().mapToInt(String::length).max().orElse(0);
        for (int offsetY = 0; offsetY <= 3 - height; offsetY++)
            for (int offsetX = 0; offsetX <= 3 - width; offsetX++) {
                boolean matches = true;
                for (int y = 0; y < 3 && matches; y++)
                    for (int x = 0; x < 3; x++) {
                        char symbol = y >= offsetY && y < offsetY + height && x >= offsetX && x < this.pattern.get(y - offsetY).length()
                                ? this.pattern.get(y - offsetY).charAt(x - offsetX) : ' ';
                        Ingredient ingredient = symbol == ' ' ? null : this.key.get(String.valueOf(symbol));
                        if (ingredient == null ? !input.getItem(x + y * 3).isEmpty() : !ingredient.test(input.getItem(x + y * 3))) {
                            matches = false;
                            break;
                        }
                    }
                if (matches) return true;
            }
        return false;
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
