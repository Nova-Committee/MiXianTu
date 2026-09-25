package com.iafenvoy.mxt.network;

import com.iafenvoy.mxt.network.payload.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
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
        PayloadRegistrar registrar = event.registrar("1")
                .playToServer(WheelActionC2SPayload.TYPE, WheelActionC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onWheelAction))
                .playToServer(ForgingActionC2SPayload.TYPE, ForgingActionC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onForgingAction))
                .playToServer(ChequeActionC2SPayload.TYPE, ChequeActionC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onChequeAction))
                .playToServer(StationTradeC2SPayload.TYPE, StationTradeC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onStationTrade))
                .playToServer(PlayerTradeActionC2SPayload.TYPE, PlayerTradeActionC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onPlayerTradeAction))
                .playToServer(BackSlotSwapC2SPayload.TYPE, BackSlotSwapC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onBackSlotSwap))
                .playToServer(CultivationToggleC2SPayload.TYPE, CultivationToggleC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onCultivationToggle))
                .playToServer(WheelLayoutC2SPayload.TYPE, WheelLayoutC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onWheelLayout))
                .playToServer(WheelSelectionC2SPayload.TYPE, WheelSelectionC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onWheelSelection))
                .playToServer(OwnerNameC2SPayload.TYPE, OwnerNameC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onOwnerNameRequest))
                .playToServer(FlightDescendC2SPayload.TYPE, FlightDescendC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onFlightDescend));
        // A dedicated server never runs one of these, so it registers the codec and nothing else.
        if (FMLEnvironment.getDist() != Dist.CLIENT) {
            registrar.playToClient(AuraStateS2CPayload.TYPE, AuraStateS2CPayload.STREAM_CODEC)
                    .playToClient(ItemPickerS2CPayload.TYPE, ItemPickerS2CPayload.STREAM_CODEC)
                    .playToClient(OwnerNameS2CPayload.TYPE, OwnerNameS2CPayload.STREAM_CODEC);
            return;
        }
        registrar.playToClient(AuraStateS2CPayload.TYPE, AuraStateS2CPayload.STREAM_CODEC,
                        new MainThreadPayloadHandler<>(ClientNetworkHandler::onAuraState))
                .playToClient(ItemPickerS2CPayload.TYPE, ItemPickerS2CPayload.STREAM_CODEC,
                        new MainThreadPayloadHandler<>(ClientNetworkHandler::onItemPicker))
                .playToClient(OwnerNameS2CPayload.TYPE, OwnerNameS2CPayload.STREAM_CODEC,
                        new MainThreadPayloadHandler<>(ClientNetworkHandler::onOwnerName));
    }
}
