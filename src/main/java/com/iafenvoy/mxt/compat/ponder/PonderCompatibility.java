package com.iafenvoy.mxt.compat.ponder;

import com.iafenvoy.mxt.MiXianTu;
import com.mojang.brigadier.arguments.ArgumentType;
import net.createmod.catnip.impl.command.argument.ConfigIdArgument;
import net.createmod.catnip.impl.command.argument.ConfigPathArgument;
import net.createmod.catnip.impl.command.argument.ModIdArgument;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.function.Supplier;

@EventBusSubscriber
public final class PonderCompatibility {
    private PonderCompatibility() {
    }

    @SubscribeEvent
    public static void registerArguments(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.COMMAND_ARGUMENT_TYPE)) return;
        // Catnip 1.0.52 registers these command nodes but omits their network serializers.
        register(event, "catnip_mod_id", ModIdArgument.class, ModIdArgument::new);
        register(event, "catnip_config_id", ConfigIdArgument.class, ConfigIdArgument::new);
        register(event, "catnip_config_path", ConfigPathArgument.class, ConfigPathArgument::new);
    }

    private static <A extends ArgumentType<?>> void register(RegisterEvent event, String name, Class<A> type, Supplier<A> factory) {
        if (ArgumentTypeInfos.isClassRecognized(type)) return;
        event.register(Registries.COMMAND_ARGUMENT_TYPE, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, name),
                () -> ArgumentTypeInfos.registerByClass(type, SingletonArgumentInfo.contextFree(factory)));
    }
}
