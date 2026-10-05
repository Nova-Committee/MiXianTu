package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.quality.QualityIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The NeoForge ingredient types this mod adds. One entry here is what lets every recipe in the game write
 * {@code {"neoforge:ingredient_type": "mxt:quality", ...}} as one of its ingredients.
 */
@SuppressWarnings("unused")
public final class MxtIngredientTypes {
    public static final DeferredRegister<IngredientType<?>> REGISTRY =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, MiXianTu.MOD_ID);

    public static final DeferredHolder<IngredientType<?>, IngredientType<QualityIngredient>> QUALITY =
            REGISTRY.register("quality", () -> QualityIngredient.TYPE);
}
