package com.iafenvoy.mxt.network;

import com.iafenvoy.mxt.network.payload.AuraStateS2CPayload;
import com.iafenvoy.mxt.network.payload.ItemPickerS2CPayload;
import com.iafenvoy.mxt.network.payload.OwnerNameS2CPayload;
import com.iafenvoy.mxt.runtime.world.AuraClientState;
import com.iafenvoy.mxt.screen.picker.ItemPickerScreen;
import com.iafenvoy.mxt.util.ClientPlayerNames;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientNetworkHandler {
    static void onAuraState(AuraStateS2CPayload payload, IPayloadContext context) {
        AuraClientState.update(payload.source(), payload.actual(), payload.environment());
    }

    // The grid is built on this side from the synced registries, and taking an item out of it is the vanilla
    // creative gesture, so the only thing that reaches the server is the slot it ends up in: this only opens the screen.
    static void onItemPicker(ItemPickerS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ItemPickerScreen screen = ItemPickerScreen.opening(payload.title(), payload.categories());
            if (screen != null) Minecraft.getInstance().setScreen(screen);
        });
    }

    // Nothing is redrawn here: a tooltip is rebuilt every frame it is shown, so the name appears next time it is read.
    static void onOwnerName(OwnerNameS2CPayload payload, IPayloadContext context) {
        ClientPlayerNames.remember(payload.owner(), payload.name());
    }
}
