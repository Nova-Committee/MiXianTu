package com.iafenvoy.mxt;

import com.iafenvoy.mxt.integration.MxtKubeJsEvents;
import com.iafenvoy.mxt.registry.MxtRegistrations;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(MiXianTu.MOD_ID)
public final class MiXianTu {
    public static final String MOD_ID = "mxt";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MiXianTu(IEventBus bus) {
        MxtRegistrations.register(bus);
        MxtKubeJsEvents.register();
    }
}
