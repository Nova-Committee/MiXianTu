package com.iafenvoy.mxt.data.quality;

import com.iafenvoy.mxt.runtime.item.QualityRequirements;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.HolderSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import org.jspecify.annotations.NonNull;

import java.util.stream.Stream;

/**
 * An ingredient that accepts a listed item set only when the stack's tier satisfies a requirement. The item set is
 * required and enumerable: an ingredient returning no item counts as empty and takes the whole recipe with it, and
 * viewers enumerate that set. Written as {@code mxt:quality} under {@code neoforge:ingredient_type}.
 *
 * <p>The component is called {@code values} because {@link ICustomIngredient#items()} already means "what this
 * accepts, enumerated"; the JSON key is still {@code items}.
 */
public record QualityIngredient(HolderSet<Item> values, QualityRequirement requirement) implements ICustomIngredient {
    public static final MapCodec<QualityIngredient> CODEC = RecordCodecBuilder.<QualityIngredient>mapCodec(i -> i.group(
                    Ingredient.NON_AIR_HOLDER_SET_CODEC.fieldOf("items").forGetter(QualityIngredient::values),
                    QualityRequirement.QUALITIES_FIELD.forGetter(ingredient -> ingredient.requirement().qualities()),
                    QualityRequirement.MIN_QUALITY_FIELD.forGetter(ingredient -> ingredient.requirement().minQuality())
            ).apply(i, (values, qualities, minimum) -> new QualityIngredient(values, new QualityRequirement(qualities, minimum))))
            .validate(QualityIngredient::validate);
    public static final IngredientType<QualityIngredient> TYPE = new IngredientType<>(CODEC);

    private static DataResult<QualityIngredient> validate(QualityIngredient ingredient) {
        // A tag's contents are not bound while a pack is being read, so counting is only possible for a list written
        // out in the file; a tag is taken as non-empty here and answers with whatever it holds once it is bound.
        boolean listed = ingredient.values().unwrap().right().map(values -> !values.isEmpty()).orElse(true);
        if (!listed)
            return DataResult.error(() -> "the mxt:quality ingredient needs at least one item");
        return ingredient.requirement().isEmpty()
                ? DataResult.error(() -> "the mxt:quality ingredient needs a quality list or a min_quality")
                : DataResult.success(ingredient);
    }

    @Override
    public boolean test(@NonNull ItemStack stack) {
        // An ingredient only ever sees the stack, so it has to look the registries up itself; with no level loaded
        // there is no tier to compare and the ingredient answers no.
        Provider access = QualityRequirements.access();
        return access != null && stack.is(this.values)
                && QualityRequirements.test(access, stack, this.requirement);
    }

    @Override
    public @NonNull Stream<Holder<Item>> items() {
        return this.values.stream();
    }

    // A requirement is always present (see the codec) and a tier is read per stack, so this is never simple.
    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public @NonNull IngredientType<?> getType() {
        return TYPE;
    }
}
