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
 * Registers every payload of this mod. The server-bound ones are handled on the server, so they are the same on
 * both sides.
 *
 * <p>The client-bound ones are not: a dedicated server never handles one, but it is still the side that encodes
 * them, so their <em>types</em> have to be registered there too. Only a client may name the handlers that run
 * them, because those handlers touch screen classes that a dedicated server's class loader refuses to load at
 * all — naming one is enough to make the server refuse to start.</p>
 */
@EventBusSubscriber
public final class NetworkManager {
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1")
                .playToServer(AbilityActionC2SPayload.TYPE, AbilityActionC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onAbilityAction))
                .playToServer(ForgingActionC2SPayload.TYPE, ForgingActionC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onForgingAction))
                .playToServer(FlightToggleC2SPayload.TYPE, FlightToggleC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onFlightToggle))
                .playToServer(ChequeActionC2SPayload.TYPE, ChequeActionC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onChequeAction))
                .playToServer(StationTradeC2SPayload.TYPE, StationTradeC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onStationTrade))
                .playToServer(PlayerTradeActionC2SPayload.TYPE, PlayerTradeActionC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onPlayerTradeAction))
                .playToServer(BackSlotSwapC2SPayload.TYPE, BackSlotSwapC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onBackSlotSwap))
                .playToServer(CultivationToggleC2SPayload.TYPE, CultivationToggleC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onCultivationToggle))
                .playToServer(SpiritBurstC2SPayload.TYPE, SpiritBurstC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onSpiritBurst))
                .playToServer(HotbarLayoutC2SPayload.TYPE, HotbarLayoutC2SPayload.STREAM_CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::onHotbarLayout));
        // A dedicated server never runs one of these, so it registers the codec and nothing else.
        if (FMLEnvironment.getDist() != Dist.CLIENT) {
            registrar.playToClient(AuraStateS2CPayload.TYPE, AuraStateS2CPayload.STREAM_CODEC)
                    .playToClient(ItemPickerS2CPayload.TYPE, ItemPickerS2CPayload.STREAM_CODEC)
                    .playToClient(HotbarConfigurationS2CPayload.TYPE, HotbarConfigurationS2CPayload.STREAM_CODEC);
            return;
        }
        registrar.playToClient(AuraStateS2CPayload.TYPE, AuraStateS2CPayload.STREAM_CODEC,
                        new MainThreadPayloadHandler<>(ClientNetworkHandler::onAuraState))
                .playToClient(ItemPickerS2CPayload.TYPE, ItemPickerS2CPayload.STREAM_CODEC,
                        new MainThreadPayloadHandler<>(ClientNetworkHandler::onItemPicker))
                .playToClient(HotbarConfigurationS2CPayload.TYPE, HotbarConfigurationS2CPayload.STREAM_CODEC,
                        new MainThreadPayloadHandler<>(ClientNetworkHandler::onHotbarConfiguration));
    }
}
