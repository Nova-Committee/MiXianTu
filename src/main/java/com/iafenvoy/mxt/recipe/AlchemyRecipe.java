package com.iafenvoy.mxt.recipe;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.alchemy.MedicinalProperty;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.quality.QualityRequirement;
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
 * Property thresholds, not an item-id list. A guide is one example and never overrides the mixture check. The
 * furnace asks about two things the mixtures cannot say: {@code furnace_quality} is required of the core item and
 * {@code input_quality} of every non-empty input slot. The core's tier position is readable in every formula here
 * as {@code furnace_rank}.
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
                            Optional<QualityRequirement> furnaceQuality, Optional<QualityRequirement> inputQuality,
                            Optional<Guide> guide) implements Recipe<AlchemyRecipeInput> {
    /**
     * The core's own tier position, which a formula of this recipe may read. Injected for one batch the way the
     * drawing's {@code paper_rank} is, so it is not an {@code mxt:formula_variable} entry.
     */
    public static final String FURNACE_RANK = "furnace_rank";
    // A tier required of the furnace item, or of every input. Nested rather than inlined, because a bare quality on
    // a recipe would read as the product's tier; a written but empty one is refused at load.
    private static final MapCodec<Optional<QualityRequirement>> FURNACE_QUALITY_FIELD = requirement("furnace_quality");
    private static final MapCodec<Optional<QualityRequirement>> INPUT_QUALITY_FIELD = requirement("input_quality");

    private static MapCodec<Optional<QualityRequirement>> requirement(String field) {
        return QualityRequirement.CODEC.flatXmap(
                requirement -> requirement.isEmpty()
                        ? DataResult.error(() -> "mxt:alchemy " + field + " needs a quality list or a min_quality")
                        : DataResult.success(requirement),
                DataResult::success
        ).codec().optionalFieldOf(field);
    }

    private static final Codec<Map<Holder<MedicinalProperty>, NumberProvider>> PROPERTIES =
            Codec.unboundedMap(MedicinalProperty.CODEC, NumberProvider.CODEC);
    public static final MapCodec<AlchemyRecipe> CODEC = RecordCodecBuilder.<AlchemyRecipe>mapCodec(i -> i.group(
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
            // Both tier requirements read as one group: the third pair is what keeps the group at fifteen components.
            MiscCodecs.pair(MiscCodecs.pair(FURNACE_QUALITY_FIELD, INPUT_QUALITY_FIELD), Guide.CODEC.optionalFieldOf("guide"))
                    .forGetter(recipe -> Pair.of(Pair.of(recipe.furnaceQuality(), recipe.inputQuality()), recipe.guide()))
    ).apply(i, (texts, main, auxiliary, catalyst, balance, target, tolerance, duration, badTicks, aura,
                success, failure, actions, blocks, furnace) -> new AlchemyRecipe(
            texts.getFirst(), texts.getSecond(), main, auxiliary, catalyst, balance, target, tolerance, duration, badTicks, aura,
            success, failure, actions.getFirst(), actions.getSecond(), blocks.getFirst(), blocks.getSecond(),
            furnace.getFirst().getFirst(), furnace.getFirst().getSecond(), furnace.getSecond())));
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
