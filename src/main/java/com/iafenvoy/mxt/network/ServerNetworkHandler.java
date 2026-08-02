package com.iafenvoy.mxt.network;

import com.iafenvoy.mxt.network.payload.*;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.artifact.FlightService;
import com.iafenvoy.mxt.runtime.artifact.FlightService.Failure;
import com.iafenvoy.mxt.runtime.economy.PlayerTradeService;
import com.iafenvoy.mxt.runtime.forging.ForgingWorkstationService;
import com.iafenvoy.mxt.screen.menu.ChequeTableMenu;
import com.iafenvoy.mxt.screen.menu.StationMenu;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Applies C2S requests after they have reached the server main thread.
 */
public final class ServerNetworkHandler {
    private ServerNetworkHandler() {
    }

    static void onAbilityAction(AbilityActionC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (payload.cancel()) {
            AbilityService.cancelCast(payload.ability(), player.getData(MxtAttachments.ABILITY_HOLDER), player.level().getGameTime());
            return;
        }
        MxtDatapackRegistries.get(MxtDatapackRegistries.ABILITY, payload.ability()).ifPresent(definition -> AbilityService.use(payload.ability(), definition, player,
                player.getData(MxtAttachments.ABILITY_HOLDER), player.getData(MxtAttachments.RESOURCE_HOLDER), player.level().getGameTime(), FormulaContext.EMPTY));
    }

    static void onForgingAction(ForgingActionC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Identifier definition = payload.definition().orElse(null);
        switch (payload.action()) {
            case START ->
                    MxtDatapackRegistries.get(MxtDatapackRegistries.FORGING_BLUEPRINT, definition).ifPresent(blueprint ->
                            ForgingWorkstationService.start(player, payload.position(), definition, blueprint));
            case STRIKE ->
                    MxtDatapackRegistries.get(MxtDatapackRegistries.FORGING_METHOD, definition).ifPresent(method ->
                            ForgingWorkstationService.strike(player, payload.position(), definition, method));
            case FINISH -> ForgingWorkstationService.finish(player, payload.position(), definition);
            case CANCEL -> ForgingWorkstationService.cancel(player, payload.position());
        }
    }

    static void onFlightToggle(FlightToggleC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!payload.enabled()) {
            if (player.getData(MxtAttachments.FLIGHT).archetype().filter(payload.archetype()::equals).isPresent()) {
                FlightService.dismount(player, Failure.STOPPED);
            }
            return;
        }
        MxtDatapackRegistries.get(MxtDatapackRegistries.ITEM_ARCHETYPE, payload.archetype()).ifPresent(definition ->
                FlightService.mount(player, player.getMainHandItem(), payload.archetype(), definition, FormulaContext.EMPTY));
    }

    static void onChequeAction(ChequeActionC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof ChequeTableMenu menu))
            return;
        if (payload.checkIn()) menu.checkIn(player);
        else menu.checkOut();
    }

    static void onStationTrade(StationTradeC2SPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof StationMenu menu && menu.isCustomer()) {
            menu.trade(player);
        }
    }

    static void onPlayerTradeAction(PlayerTradeActionC2SPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) PlayerTradeService.handleAction(player, payload.action());
    }
}
