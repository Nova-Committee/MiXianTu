package com.iafenvoy.mxt.network;

import com.iafenvoy.mxt.network.payload.*;
import com.iafenvoy.mxt.runtime.rift.RiftEditorService;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.MainThreadPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers every payload of this mod. The server-bound ones are handled on the server, so they are the same on both
 * sides; every handler is a {@code MainThreadPayloadHandler}, so it runs on the main thread and may touch world state.
 *
 * <p>The client-bound ones differ per side: a dedicated server never handles one, but it is still the side that
 * encodes them, so their <em>types</em> have to be registered there too. Only a client may name the handlers that run
 * them, since those touch screen classes a dedicated server's class loader refuses to load at all - naming one is
 * enough to make the server refuse to start.
 */
@EventBusSubscriber
public final class NetworkManager {
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("3")
                .playToServer(RiftEditC2SPayload.TYPE, RiftEditC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(RiftEditorService::handle))
                .playToServer(ProfilerSubscriptionC2SPayload.TYPE, ProfilerSubscriptionC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onProfilerSubscription))
                .playToServer(ChequeActionC2SPayload.TYPE, ChequeActionC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onChequeAction))
                .playToServer(StationTradeC2SPayload.TYPE, StationTradeC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onStationTrade))
                .playToServer(PlayerTradeActionC2SPayload.TYPE, PlayerTradeActionC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onPlayerTradeAction))
                .playToServer(BackSlotSwapC2SPayload.TYPE, BackSlotSwapC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onBackSlotSwap))
                .playToServer(CultivationToggleC2SPayload.TYPE, CultivationToggleC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onCultivationToggle))
                .playToServer(FlightDescendC2SPayload.TYPE, FlightDescendC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onFlightDescend));

        registrar.playToClient(RiftSyncS2CPayload.TYPE, RiftSyncS2CPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ClientNetworkHandler::onRiftSync))
                .playToClient(RiftEditorS2CPayload.TYPE, RiftEditorS2CPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ClientNetworkHandler::onRiftEditor));
        registrar.playToClient(ProfilerSnapshotS2CPayload.TYPE, ProfilerSnapshotS2CPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ClientNetworkHandler::onProfilerSnapshot))
                .playToClient(AuraStateS2CPayload.TYPE, AuraStateS2CPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ClientNetworkHandler::onAuraState))
                .playToClient(SpiritCraftingStateS2CPayload.TYPE, SpiritCraftingStateS2CPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ClientNetworkHandler::onSpiritCraftingState))
                .playToClient(FormationStructureS2CPayload.TYPE, FormationStructureS2CPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ClientNetworkHandler::onFormationStructure));
    }
}
