package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.storage.builtin.CooldownDataStorage;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.literal;

/**
 * Administration and diagnostics that exist only under {@code /mxt}: these nodes take no top-level alias, so
 * operator tooling never claims short generic spellings such as {@code /registries}.
 */
public final class MxtCommand {
    public static void attach(CommandBuildContext context, LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(literal("registries").executes(ctx -> listRegistries(ctx.getSource())))
                .then(literal("attachment").then(literal("status").executes(ctx -> attachmentStatus(ctx.getSource()))));
    }

    private static int listRegistries(CommandSourceStack source) {
        RegistryAccess access = source.registryAccess();
        String text = MxtDatapackRegistries.registries().stream()
                .map(key -> key.identifier() + "=" + access.lookupOrThrow(key).size())
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.translatable("command.mxt.registries.list", text), false);
        return MxtDatapackRegistries.registries().size();
    }

    private static int attachmentStatus(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AbilityAttachment abilities = player.getData(MxtAttachments.ABILITY_HOLDER);
        CurseHolderAttachment curses = player.getData(MxtAttachments.CURSE_HOLDER);
        ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
        SpiritIdentityAttachment identity = player.getData(MxtAttachments.SPIRIT_IDENTITY);
        source.sendSuccess(() -> Component.translatable("command.mxt.attachment.status", resources.values().size(), abilities.sources().size(),
                abilities.storage().count(CooldownDataStorage.class), curses.instances().size(), identity.spiritRoots().size(), identity.physiques().size()), false);
        return 1;
    }
}
