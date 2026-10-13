package com.iafenvoy.mxt.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

// Reads the ids named by the argument types that may name something the current pack no longer provides. A
// ResourceKeyArgument node is deliberately never resolved to a registry entry; only its registry is checked.
//FIXME::Remove
public final class RegistryReferences {
    private static final DynamicCommandExceptionType ERROR_INVALID_ID = new DynamicCommandExceptionType(
            value -> Component.translatable("command.mxt.message.invalid_id", value));

    private RegistryReferences() {
    }

    public static <T> ResourceKey<T> key(CommandContext<CommandSourceStack> ctx, String name, ResourceKey<Registry<T>> registry) throws CommandSyntaxException {
        return ResourceKeyArgument.getRegistryKey(ctx, name, registry, ERROR_INVALID_ID);
    }

    // Read as an id, not through DimensionArgument.getDimension: a secret realm's level exists only while it is
    // open, so asking the server for it would reject the dormant realm an operator is inspecting or destroying.
    public static Identifier dimension(CommandContext<CommandSourceStack> ctx, String name) {
        return ctx.getArgument(name, Identifier.class);
    }
}
