package com.iafenvoy.mxt.command;

import com.iafenvoy.jupiter.config.entry.BooleanEntry;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.config.MxtServerConfig.Commands;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.util.Util;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Map;
import java.util.Map.Entry;

import static net.minecraft.commands.Commands.literal;

@EventBusSubscriber
public final class CommandManager {
    private static final Map<LiteralArgumentBuilder<CommandSourceStack>, BooleanEntry> NODES = Util.make(() -> {
        Commands config = MxtServerConfig.INSTANCE.commands;
        return Map.of(
                AbilityCommand.ROOT, config.ability,
                AuraCommand.ROOT, config.aura,
                DisplayCommand.ROOT, config.display,
                FormationCommand.ROOT, config.formation,
                FriendCommand.ROOT, config.friend,
                LightningCommand.ROOT, config.lightning,
                TechniqueCommand.ROOT, config.technique,
                TradeCommand.ROOT, config.trade
        );
    });

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> root = literal(MiXianTu.MOD_ID);
        for (Entry<LiteralArgumentBuilder<CommandSourceStack>, BooleanEntry> entry : NODES.entrySet())
            root.then(entry.getKey());
        MxtCommand.attach(root);
        dispatcher.register(root);

        NODES.forEach((node, config) -> {
            if (config.getValue()) dispatcher.register(node);
        });
    }
}
