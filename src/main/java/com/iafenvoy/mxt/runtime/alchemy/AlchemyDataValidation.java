package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.recipe.AlchemyRecipe;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.DefaultDataComponentsBoundEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import java.util.List;

@EventBusSubscriber
public final class AlchemyDataValidation {
    // Both events run in updateComponentsAndStaticRegistryTags; item templates are usable only in the second.
    private static final ThreadLocal<TagsUpdatedEvent.ServerDataLoad> PENDING = new ThreadLocal<>();

    private AlchemyDataValidation() {
    }

    @SubscribeEvent
    public static void onServerDataLoad(TagsUpdatedEvent.ServerDataLoad event) {
        PENDING.set(event);
    }

    @SubscribeEvent
    public static void onComponentsBound(DefaultDataComponentsBoundEvent event) {
        if (event.getUpdateCause() != DefaultDataComponentsBoundEvent.UpdateCause.SERVER_DATA_LOAD) return;
        TagsUpdatedEvent.ServerDataLoad reload = PENDING.get();
        PENDING.remove();
        if (reload == null) return;
        SpiritHerbService.validateExclusiveClaims(reload.getRegistries());
        for (RecipeHolder<?> holder : reload.getServerResources().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof AlchemyRecipe recipe)) continue;
            validateOutputs(holder.id(), "success_outputs", recipe.successOutputs());
            validateOutputs(holder.id(), "failure_outputs", recipe.failureOutputs());
        }
    }

    private static void validateOutputs(ResourceKey<Recipe<?>> recipe, String field, List<ItemStackTemplate> outputs) {
        for (int index = 0; index < outputs.size(); index++) {
            ItemStackTemplate stack = outputs.get(index);
            int limit = stack.getMaxStackSize();
            if (stack.count() > limit)
                throw new IllegalStateException(recipe.identifier() + " " + field + "[" + index + "] count "
                        + stack.count() + " exceeds the component-aware stack limit " + limit);
        }
    }
}
