package com.iafenvoy.mxt.recipe;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.alchemy.MedicinalProperty;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.registry.MxtRecipeSerializers;
import com.iafenvoy.mxt.registry.MxtRecipeTypes;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyResolver;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Property thresholds, not an item-id list. A guide is one example and never overrides the mixture check.
 */
public record AlchemyRecipe(Component name, Component description,
                            Map<Holder<MedicinalProperty>, NumberProvider> mainRequirements,
                            Map<Holder<MedicinalProperty>, NumberProvider> auxiliaryRequirements,
                            NumberProvider catalystRequirement, NumberProvider balanceTolerance,
                            NumberProvider targetTemperature, NumberProvider temperatureTolerance,
                            NumberProvider duration, int maxBadTicks,
                            Map<Holder<Aura>, NumberProvider> minimumAura,
                            List<ItemStackTemplate> successOutputs, List<ItemStackTemplate> failureOutputs,
                            EntityAction successAction, EntityAction failureAction,
                            BlockAction successBlockAction, BlockAction failureBlockAction,
                            Optional<Guide> guide) implements Recipe<AlchemyRecipeInput> {
    private static final Codec<Map<Holder<MedicinalProperty>, NumberProvider>> PROPERTIES =
            Codec.unboundedMap(MedicinalProperty.CODEC, NumberProvider.CODEC);
    public static final MapCodec<AlchemyRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            MiscCodecs.pair(
                            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("name", Component.empty()),
                            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("description", Component.empty()))
                    .forGetter(recipe -> Pair.of(recipe.name(), recipe.description())),
            PROPERTIES.validate(AlchemyRecipe::requirePositive).fieldOf("main_requirements").forGetter(AlchemyRecipe::mainRequirements),
            PROPERTIES.validate(AlchemyRecipe::optionalPositive).optionalFieldOf("auxiliary_requirements", Map.of()).forGetter(AlchemyRecipe::auxiliaryRequirements),
            NumberProvider.CODEC.validate(AlchemyRecipe::positiveProvider).fieldOf("catalyst_requirement").forGetter(AlchemyRecipe::catalystRequirement),
            NumberProvider.CODEC.validate(AlchemyRecipe::unitInterval).optionalFieldOf("balance_tolerance", new Constant(0.0D)).forGetter(AlchemyRecipe::balanceTolerance),
            NumberProvider.CODEC.validate(AlchemyRecipe::nonNegativeProvider).fieldOf("target_temperature").forGetter(AlchemyRecipe::targetTemperature),
            NumberProvider.CODEC.validate(AlchemyRecipe::nonNegativeProvider).optionalFieldOf("temperature_tolerance", new Constant(0.0D)).forGetter(AlchemyRecipe::temperatureTolerance),
            NumberProvider.CODEC.validate(AlchemyRecipe::positiveProvider).fieldOf("duration").forGetter(AlchemyRecipe::duration),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("max_bad_ticks", 0).forGetter(AlchemyRecipe::maxBadTicks),
            Codec.unboundedMap(Aura.CODEC, NumberProvider.CODEC).optionalFieldOf("minimum_aura", Map.of()).forGetter(AlchemyRecipe::minimumAura),
            ItemStackTemplate.CODEC.listOf(1, 4).validate(AlchemyRecipe::fitting).fieldOf("success_outputs").forGetter(AlchemyRecipe::successOutputs),
            ItemStackTemplate.CODEC.listOf(0, 4).validate(AlchemyRecipe::fitting).optionalFieldOf("failure_outputs", List.of()).forGetter(AlchemyRecipe::failureOutputs),
            MiscCodecs.pair(EntityAction.optionalCodec("success_action"), EntityAction.optionalCodec("failure_action"))
                    .forGetter(recipe -> Pair.of(recipe.successAction(), recipe.failureAction())),
            MiscCodecs.pair(BlockAction.optionalCodec("success_block_action"), BlockAction.optionalCodec("failure_block_action"))
                    .forGetter(recipe -> Pair.of(recipe.successBlockAction(), recipe.failureBlockAction())),
            Guide.CODEC.optionalFieldOf("guide").forGetter(AlchemyRecipe::guide)
    ).apply(i, (texts, main, auxiliary, catalyst, balance, target, tolerance, duration, badTicks, aura,
                success, failure, actions, blocks, guide) -> new AlchemyRecipe(
            texts.getFirst(), texts.getSecond(), main, auxiliary, catalyst, balance, target, tolerance, duration, badTicks, aura,
            success, failure, actions.getFirst(), actions.getSecond(), blocks.getFirst(), blocks.getSecond(), guide)));
    public static final StreamCodec<RegistryFriendlyByteBuf, AlchemyRecipe> PACKET_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    @Override
    public boolean matches(AlchemyRecipeInput input, @NonNull Level level) {
        return AlchemyResolver.matches(this, input, level);
    }

    @Override
    public @NonNull ItemStack assemble(AlchemyRecipeInput input) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    // Made in the furnace rather than laid out in a grid, so it is never listed by a recipe book. Without this the
    // recipe manager warns on every load that a non-special recipe with impossible placement info was ignored.
    @Override
    public boolean isSpecial() {
        return true;
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

    public enum Role {
        MAIN, AUXILIARY, CATALYST
    }

    public record Guide(List<ItemStackTemplate> main, List<ItemStackTemplate> auxiliary,
                        List<ItemStackTemplate> catalyst) {
        public static final Codec<Guide> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStackTemplate.CODEC.listOf(0, 2).optionalFieldOf("main", List.of()).forGetter(Guide::main),
                ItemStackTemplate.CODEC.listOf(0, 2).optionalFieldOf("auxiliary", List.of()).forGetter(Guide::auxiliary),
                ItemStackTemplate.CODEC.listOf(0, 1).optionalFieldOf("catalyst", List.of()).forGetter(Guide::catalyst)
        ).apply(i, Guide::new));
    }

    private static DataResult<Map<Holder<MedicinalProperty>, NumberProvider>> requirePositive(Map<Holder<MedicinalProperty>, NumberProvider> values) {
        if (values.isEmpty()) return DataResult.error(() -> "main_requirements must not be empty");
        return optionalPositive(values);
    }

    private static DataResult<Map<Holder<MedicinalProperty>, NumberProvider>> optionalPositive(Map<Holder<MedicinalProperty>, NumberProvider> values) {
        for (NumberProvider provider : values.values()) {
            DataResult<NumberProvider> checked = positiveProvider(provider);
            if (checked instanceof DataResult.Error<NumberProvider> error) return DataResult.error(error::message);
        }
        return DataResult.success(values);
    }

    private static DataResult<NumberProvider> positiveProvider(NumberProvider provider) {
        if (provider instanceof Constant(double value) && value <= 0.0D)
            return DataResult.error(() -> "Alchemy threshold must be positive");
        return DataResult.success(provider);
    }

    private static DataResult<NumberProvider> nonNegativeProvider(NumberProvider provider) {
        if (provider instanceof Constant(double value) && value < 0.0D)
            return DataResult.error(() -> "Alchemy temperature must be non-negative");
        return DataResult.success(provider);
    }

    private static DataResult<NumberProvider> unitInterval(NumberProvider provider) {
        if (provider instanceof Constant(double value) && (value < 0.0D || value > 1.0D))
            return DataResult.error(() -> "balance_tolerance must be in [0, 1]");
        return DataResult.success(provider);
    }

    private static DataResult<List<ItemStackTemplate>> fitting(List<ItemStackTemplate> stacks) {
        for (ItemStackTemplate stack : stacks) {
            if (stack.count() > 64)
                return DataResult.error(() -> "Alchemy output " + stack.item().value() + " does not fit in one slot");
        }
        return DataResult.success(stacks);
    }
}
