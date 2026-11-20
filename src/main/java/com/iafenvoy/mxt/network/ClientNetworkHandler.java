package com.iafenvoy.mxt.network;

import com.iafenvoy.mxt.network.payload.*;
import com.iafenvoy.mxt.render.rift.RiftClientState;
import com.iafenvoy.mxt.runtime.world.AuraClientState;
import com.iafenvoy.mxt.screen.menu.SpiritCraftingMenu;
import com.iafenvoy.mxt.screen.ponder.FormationPonder;
import com.iafenvoy.mxt.screen.profiling.ProfilerScreen;
import com.iafenvoy.mxt.screen.rift.RiftEditorScreen;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientNetworkHandler {
    static void onRiftSync(RiftSyncS2CPayload payload, IPayloadContext context) {
        RiftClientState.accept(payload);
    }

    static void onRiftEditor(RiftEditorS2CPayload payload, IPayloadContext context) {
        RiftEditorScreen.receive(payload);
    }

    static void onSpiritCraftingState(SpiritCraftingStateS2CPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof SpiritCraftingMenu menu && menu.containerId == payload.containerId())
            menu.acceptProgress(payload.rows());
    }

    static void onProfilerSnapshot(ProfilerSnapshotS2CPayload payload, IPayloadContext context) {
        ProfilerScreen.receive(payload);
    }

    static void onAuraState(AuraStateS2CPayload payload, IPayloadContext context) {
        AuraClientState.update(payload.source(), payload.actual(), payload.environment());
    }


    // Every drawing message lands on the menu the player has open; one whose container id does not match its own
    // is a late packet from a screen that is already gone, and is dropped.


    // The structure travels in the payload, so the screen is built from what the server resolved rather than from
    // anything this side would have to look up.
    static void onFormationStructure(FormationStructureS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> FormationPonder.open(payload.title(), payload.structure()));
    }
}
