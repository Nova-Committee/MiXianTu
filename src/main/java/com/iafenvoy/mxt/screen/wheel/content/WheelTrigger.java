package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.network.payload.WheelActionC2SPayload;
import com.iafenvoy.mxt.api.WheelSource;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Tells the server what was picked and off which page; one place because both entry kinds share the channel.
 */
final class WheelTrigger {
    private WheelTrigger() {
    }

    static void send(WheelMenuEntry entry, WheelSource source) {
        ClientPacketDistributor.sendToServer(WheelActionC2SPayload.press(source.id(), entry.kind().id(), entry.id()));
    }
}