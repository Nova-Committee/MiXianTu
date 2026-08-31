package com.iafenvoy.mxt.network;

import com.iafenvoy.mxt.network.payload.AuraStateS2CPayload;
import com.iafenvoy.mxt.network.payload.HotbarConfigurationS2CPayload;
import com.iafenvoy.mxt.render.overlay.hotbar.HotbarModeRegistry;
import com.iafenvoy.mxt.runtime.world.AuraClientState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientNetworkHandler {
    static void onAuraState(AuraStateS2CPayload payload, IPayloadContext context) {
        AuraClientState.update(payload.source(), payload.actual(), payload.environment());
    }

    static void onHotbarConfiguration(HotbarConfigurationS2CPayload payload, IPayloadContext context) {
        HotbarModeRegistry.openConfiguration(payload.mode());
    }
}
