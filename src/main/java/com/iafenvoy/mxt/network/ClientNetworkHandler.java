package com.iafenvoy.mxt.network;

import com.iafenvoy.mxt.network.payload.*;
import com.iafenvoy.mxt.runtime.world.AuraClientState;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceMenu;
import com.iafenvoy.mxt.screen.menu.TalismanWorkstationMenu;
import com.iafenvoy.mxt.screen.multiblock.MultiblockStructure;
import com.iafenvoy.mxt.screen.multiblock.MultiblockStructureScreen;
import com.iafenvoy.mxt.screen.profiling.ProfilerScreen;
import com.iafenvoy.mxt.util.ClientPlayerNames;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

public final class ClientNetworkHandler {
    static void onProfilerGui(ProfilerGuiS2CPayload payload, IPayloadContext context) {
        ProfilerScreen.control(payload);
    }

    static void onProfilerSnapshot(ProfilerSnapshotS2CPayload payload, IPayloadContext context) {
        ProfilerScreen.receive(payload);
    }

    static void onAuraState(AuraStateS2CPayload payload, IPayloadContext context) {
        AuraClientState.update(payload.source(), payload.actual(), payload.environment());
    }

    static void onAlchemyState(AlchemyStateS2CPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof AlchemyFurnaceMenu menu && menu.containerId == payload.containerId())
            menu.acceptView(payload.view());
    }

    // Every drawing message lands on the menu the player has open; one whose container id does not match its own
    // is a late packet from a screen that is already gone, and is dropped.
    static void onTalismanList(TalismanDrawingListS2CPayload payload, IPayloadContext context) {
        TalismanWorkstationMenu menu = talismanMenu(context, payload.containerId());
        if (menu != null) menu.acceptList(payload.rows());
    }

    static void onTalismanStart(TalismanDrawingStartS2CPayload payload, IPayloadContext context) {
        TalismanWorkstationMenu menu = talismanMenu(context, payload.containerId());
        if (menu != null) menu.acceptStart(payload);
    }

    static void onTalismanStrokeAck(TalismanStrokeAckS2CPayload payload, IPayloadContext context) {
        TalismanWorkstationMenu menu = talismanMenu(context, payload.containerId());
        if (menu != null) menu.acceptAck(payload);
    }

    static void onTalismanResult(TalismanResultS2CPayload payload, IPayloadContext context) {
        TalismanWorkstationMenu menu = talismanMenu(context, payload.containerId());
        if (menu != null) menu.acceptResult(payload);
    }

    private static @Nullable TalismanWorkstationMenu talismanMenu(IPayloadContext context, int containerId) {
        if (!(context.player().containerMenu instanceof TalismanWorkstationMenu menu)) return null;
        return menu.containerId == containerId ? menu : null;
    }

    // The structure travels in the payload, so the screen is built from what the server resolved rather than from
    // anything this side would have to look up.
    static void onFormationStructure(FormationStructureS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MultiblockStructureScreen.open(new MultiblockStructure(payload.title(),
                payload.structure().stream()
                        .map(block -> MultiblockStructure.BlockEntry.of(block.offset().getX(), block.offset().getY(),
                                block.offset().getZ(), block.state()))
                        .toList())));
    }

    // Nothing is redrawn here: a tooltip is rebuilt every frame it is shown, so the name appears next time it is read.
    static void onOwnerName(OwnerNameS2CPayload payload, IPayloadContext context) {
        ClientPlayerNames.remember(payload.owner(), payload.name());
    }
}
