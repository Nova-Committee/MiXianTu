package com.iafenvoy.mxt.command;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.iafenvoy.jupiter.config.entry.BooleanEntry;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.command.server.AbilityCommand;
import com.iafenvoy.mxt.command.server.AuraCommand;
import com.iafenvoy.mxt.command.server.CurseCommand;
import com.iafenvoy.mxt.command.server.ContractCommand;
import com.iafenvoy.mxt.command.server.DisplayCommand;
import com.iafenvoy.mxt.command.server.FlightCommand;
import com.iafenvoy.mxt.command.server.FormationCommand;
import com.iafenvoy.mxt.command.server.FriendCommand;
import com.iafenvoy.mxt.command.server.LightningCommand;
import com.iafenvoy.mxt.command.server.MxtCommand;
import com.iafenvoy.mxt.command.server.PhysiqueCommand;
import com.iafenvoy.mxt.command.server.PickerCommand;
import com.iafenvoy.mxt.command.server.QualityCommand;
import com.iafenvoy.mxt.command.server.RealmCommand;
import com.iafenvoy.mxt.command.server.SpiritRootCommand;
import com.iafenvoy.mxt.command.server.TalismanCommand;
import com.iafenvoy.mxt.command.server.TechniqueCommand;
import com.iafenvoy.mxt.command.server.TradeCommand;
import com.iafenvoy.mxt.command.server.TribulationCommand;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.config.MxtServerConfig.Commands;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Util;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import static net.minecraft.commands.Commands.literal;

@EventBusSubscriber
public final class ServerCommandManager {
    private static final Map<Function<CommandBuildContext, LiteralArgumentBuilder<CommandSourceStack>>, BooleanEntry> NODES = Util.make(() -> {
        Commands config = MxtServerConfig.INSTANCE.commands;
        Builder<Function<CommandBuildContext, LiteralArgumentBuilder<CommandSourceStack>>, BooleanEntry> builder = ImmutableMap.builder();
        builder.put(AbilityCommand::build, config.ability);
        builder.put(AuraCommand::build, config.aura);
        builder.put(ContractCommand::build, config.contract);
        builder.put(CurseCommand::build, config.curse);
        builder.put(_ -> DisplayCommand.build(), config.display);
        builder.put(_ -> FlightCommand.build(), config.flight);
        builder.put(FormationCommand::build, config.formation);
        builder.put(_ -> FriendCommand.build(), config.friend);
        builder.put(_ -> LightningCommand.build(), config.lightning);
        builder.put(PhysiqueCommand::build, config.physique);
        builder.put(_ -> PickerCommand.build(), config.picker);
        builder.put(QualityCommand::build, config.quality);
        builder.put(RealmCommand::build, config.realm);
        builder.put(SpiritRootCommand::build, config.spirit_root);
        builder.put(TalismanCommand::build, config.talisman);
        builder.put(_ -> TechniqueCommand.build(), config.technique);
        builder.put(_ -> TradeCommand.build(), config.trade);
        builder.put(TribulationCommand::build, config.tribulation);
        return builder.build();
    });

    // The single answer to "may this source change somebody else's state", so no node can drift from the others
    // by spelling its own permission check.
    public static boolean mayChange(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandBuildContext context = event.getBuildContext();

        LiteralArgumentBuilder<CommandSourceStack> root = literal(MiXianTu.MOD_ID);
        for (Entry<Function<CommandBuildContext, LiteralArgumentBuilder<CommandSourceStack>>, BooleanEntry> entry : NODES.entrySet()) {
            LiteralArgumentBuilder<CommandSourceStack> node = entry.getKey().apply(context);
            root.then(node);
            if (entry.getValue().getValue()) dispatcher.register(node);
        }
        MxtCommand.attach(context, root);
        dispatcher.register(root);
    }
}
