package com.iafenvoy.mxt.recipe;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtRecipeSerializers;
import com.iafenvoy.mxt.registry.MxtRecipeTypes;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

/**
 * Native RecipeManager entry for an alchemy recipe.
 */
public record AlchemyRecipe(List<Identifier> inputs, NumberProvider targetTemperature,
                            NumberProvider temperatureTolerance, int minimumFurnaceTier, NumberProvider duration,
                            List<Identifier> auraKinds, Map<Holder<Resource>, NumberProvider> minimumAura,
                            List<Identifier> successOutputs, List<Identifier> failureOutputs,
                            EntityAction successAction, EntityAction failureAction,
                            BlockAction successBlockAction, BlockAction failureBlockAction)
        implements Recipe<AlchemyRecipeInput> {
    public static final MapCodec<AlchemyRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.listOf(1, Integer.MAX_VALUE).fieldOf("inputs").forGetter(AlchemyRecipe::inputs),
            NumberProvider.CODEC.fieldOf("target_temperature").forGetter(AlchemyRecipe::targetTemperature),
            NumberProvider.CODEC.optionalFieldOf("temperature_tolerance", new Constant(0.0D)).forGetter(AlchemyRecipe::temperatureTolerance),
            Codec.INT.optionalFieldOf("minimum_furnace_tier", 0).forGetter(AlchemyRecipe::minimumFurnaceTier),
            NumberProvider.CODEC.fieldOf("duration").forGetter(AlchemyRecipe::duration),
            Identifier.CODEC.listOf().optionalFieldOf("aura_kinds", List.of()).forGetter(AlchemyRecipe::auraKinds),
            CollectionCodecs.map(Resource.CODEC, NumberProvider.CODEC).optionalFieldOf("minimum_aura", Map.of()).forGetter(AlchemyRecipe::minimumAura),
            Identifier.CODEC.listOf(1, Integer.MAX_VALUE).fieldOf("success_outputs").forGetter(AlchemyRecipe::successOutputs),
            Identifier.CODEC.listOf().optionalFieldOf("failure_outputs", List.of()).forGetter(AlchemyRecipe::failureOutputs),
            EntityAction.optionalCodec("success_action").forGetter(AlchemyRecipe::successAction),
            EntityAction.optionalCodec("failure_action").forGetter(AlchemyRecipe::failureAction),
            BlockAction.optionalCodec("success_block_action").forGetter(AlchemyRecipe::successBlockAction),
            BlockAction.optionalCodec("failure_block_action").forGetter(AlchemyRecipe::failureBlockAction)
    ).apply(i, AlchemyRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, AlchemyRecipe> PACKET_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    public com.iafenvoy.mxt.data.alchemy.AlchemyRecipe definition() {
        return new com.iafenvoy.mxt.data.alchemy.AlchemyRecipe(this.inputs, this.targetTemperature, this.temperatureTolerance,
                this.minimumFurnaceTier, this.duration, this.auraKinds, this.minimumAura, this.successOutputs, this.failureOutputs,
                this.successAction, this.failureAction, this.successBlockAction, this.failureBlockAction);
    }

    @Override
    public boolean matches(AlchemyRecipeInput input, @NonNull Level level) {
        return input != null && !input.isEmpty() && input.matches(this.inputs);
    }

    @Override
    public @NonNull ItemStack assemble(AlchemyRecipeInput input) {
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
    public @NonNull RecipeSerializer<? extends Recipe<AlchemyRecipeInput>> getSerializer() {
        return MxtRecipeSerializers.ALCHEMY.get();
    }

    @Override
    public @NonNull RecipeType<? extends Recipe<AlchemyRecipeInput>> getType() {
        return MxtRecipeTypes.ALCHEMY.get();
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
