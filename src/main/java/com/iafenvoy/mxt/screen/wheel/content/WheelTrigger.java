package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.network.payload.WheelActionC2SPayload;
import com.iafenvoy.mxt.screen.wheel.WheelMenuEntry;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Tells the server what was picked; one place because both entry kinds share the payload channel. */
final class WheelTrigger {
    private WheelTrigger() {
    }

    static void send(WheelMenuEntry entry) {
        ClientPacketDistributor.sendToServer(new WheelActionC2SPayload(entry.kind(), entry.id()));
    }
}
