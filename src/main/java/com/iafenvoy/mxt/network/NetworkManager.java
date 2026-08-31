package com.iafenvoy.mxt.network;

import com.iafenvoy.mxt.network.payload.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.MainThreadPayloadHandler;

@EventBusSubscriber
public final class NetworkManager {
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(AbilityActionC2SPayload.TYPE, AbilityActionC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onAbilityAction))
                .playToServer(ForgingActionC2SPayload.TYPE, ForgingActionC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onForgingAction))
                .playToServer(FlightToggleC2SPayload.TYPE, FlightToggleC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onFlightToggle))
                .playToServer(ChequeActionC2SPayload.TYPE, ChequeActionC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onChequeAction))
                .playToServer(StationTradeC2SPayload.TYPE, StationTradeC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onStationTrade))
                .playToServer(PlayerTradeActionC2SPayload.TYPE, PlayerTradeActionC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onPlayerTradeAction))
                .playToServer(BackSlotSwapC2SPayload.TYPE, BackSlotSwapC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onBackSlotSwap))
                .playToServer(CultivationToggleC2SPayload.TYPE, CultivationToggleC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onCultivationToggle))
                .playToServer(SpiritBurstC2SPayload.TYPE, SpiritBurstC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onSpiritBurst))
                .playToServer(HotbarLayoutC2SPayload.TYPE, HotbarLayoutC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onHotbarLayout))
                .playToClient(AuraStateS2CPayload.TYPE, AuraStateS2CPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ClientNetworkHandler::onAuraState))
                .playToClient(HotbarConfigurationS2CPayload.TYPE, HotbarConfigurationS2CPayload.STREAM_CODEC,
                        new MainThreadPayloadHandler<>(ClientNetworkHandler::onHotbarConfiguration));
    }
}
