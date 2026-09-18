package com.iafenvoy.mxt.command;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
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
        Builder<LiteralArgumentBuilder<CommandSourceStack>, BooleanEntry> builder = ImmutableMap.builder();
        builder.put(AbilityCommand.ROOT, config.ability);
        builder.put(AuraCommand.ROOT, config.aura);
        builder.put(DisplayCommand.ROOT, config.display);
        builder.put(FormationCommand.ROOT, config.formation);
        builder.put(FriendCommand.ROOT, config.friend);
        builder.put(LightningCommand.ROOT, config.lightning);
        builder.put(PickerCommand.ROOT, config.picker);
        builder.put(TalismanCommand.ROOT, config.talisman);
        builder.put(TechniqueCommand.ROOT, config.technique);
        builder.put(TradeCommand.ROOT, config.trade);
        builder.put(TribulationCommand.ROOT, config.tribulation);
        return builder.build();
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
