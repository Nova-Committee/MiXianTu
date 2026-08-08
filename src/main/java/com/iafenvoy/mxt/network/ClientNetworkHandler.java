package com.iafenvoy.mxt.network;

import com.iafenvoy.mxt.network.payload.AuraStateS2CPayload;
import com.iafenvoy.mxt.runtime.world.AuraClientState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Handles common client-bound state payloads without loading client-only rendering classes on a server.
 */
public final class ClientNetworkHandler {
    private ClientNetworkHandler() {
    }

    static void onAuraState(AuraStateS2CPayload payload, IPayloadContext context) {
        AuraClientState.update(payload.source(), payload.concentration());
    }
}
