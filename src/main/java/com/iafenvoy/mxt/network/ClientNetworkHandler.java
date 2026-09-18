package com.iafenvoy.mxt.network;

import com.iafenvoy.mxt.network.payload.AuraStateS2CPayload;
import com.iafenvoy.mxt.network.payload.HotbarConfigurationS2CPayload;
import com.iafenvoy.mxt.network.payload.ItemPickerS2CPayload;
import com.iafenvoy.mxt.screen.picker.ItemPickerScreen;
import com.iafenvoy.mxt.screen.overlay.hotbar.HotbarModeRegistry;
import com.iafenvoy.mxt.runtime.world.AuraClientState;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientNetworkHandler {
    static void onAuraState(AuraStateS2CPayload payload, IPayloadContext context) {
        AuraClientState.update(payload.source(), payload.actual(), payload.environment());
    }

    static void onHotbarConfiguration(HotbarConfigurationS2CPayload payload, IPayloadContext context) {
        HotbarModeRegistry.openConfiguration(payload.mode());
    }

    /**
     * Shows the categories the server named. The grid is built on this side from the synced registries, and
     * taking an item out of it is the vanilla creative gesture: the client puts it in the carried stack, and
     * the only thing that ever reaches the server is the slot it ends up in, sent as the vanilla creative slot
     * packet - so this handler only has to open the screen.
     */
    static void onItemPicker(ItemPickerS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ItemPickerScreen screen = ItemPickerScreen.opening(payload.title(), payload.categories());
            if (screen != null) Minecraft.getInstance().setScreen(screen);
        });
    }
}
