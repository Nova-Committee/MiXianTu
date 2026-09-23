package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

// Completion for the arguments that stay IdentifierArgument because their job is naming a reference the current
// data pack may no longer provide; ResourceArgument refuses such an id while parsing and cannot complete it.
public final class Suggestions {
    private Suggestions() {
    }

    public static <T> SuggestionProvider<CommandSourceStack> enabledIds(ResourceKey<Registry<T>> key) {
        return (ctx, builder) -> SharedSuggestionProvider.suggest(MxtDatapackRegistries
                .holders(ctx.getSource().getServer().registryAccess(), key)
                .map(HolderHelper::id).map(Identifier::toString).sorted().toList(), builder);
    }
}
