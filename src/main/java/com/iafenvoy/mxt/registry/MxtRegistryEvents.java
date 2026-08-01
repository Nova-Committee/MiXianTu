package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent.NewRegistry;
import net.neoforged.neoforge.registries.NewRegistryEvent;

/** Mod-bus events that create Java-owned and datapack-owned registries. */
@EventBusSubscriber(modid = MiXianTu.MOD_ID)
public final class MxtRegistryEvents {
    private MxtRegistryEvents() {
    }

    @SubscribeEvent
    public static void createRegistries(NewRegistryEvent event) {
        MxtTypeRegistries.newRegistries(event);
    }

    @SubscribeEvent
    public static void createDatapackRegistries(NewRegistry event) {
        MxtDatapackRegistries.newDatapackRegistries(event);
    }
}
