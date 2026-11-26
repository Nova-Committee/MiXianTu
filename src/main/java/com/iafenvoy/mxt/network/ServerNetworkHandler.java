package com.iafenvoy.mxt.network;

import com.iafenvoy.mxt.network.payload.*;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.cultivation.CultivationMethodService.CultivationStep;
import com.iafenvoy.mxt.runtime.cultivation.CultivationModeService;
import com.iafenvoy.mxt.runtime.economy.PlayerTradeService;
import com.iafenvoy.mxt.screen.menu.ChequeTableMenu;
import com.iafenvoy.mxt.screen.menu.StationMenu;
import com.iafenvoy.mxt.util.ProfilerSubscriptions;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;


public final class ServerNetworkHandler {
    static void onProfilerSubscription(ProfilerSubscriptionC2SPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player)
            ProfilerSubscriptions.update(player, payload.token(), payload.open());
    }

    public static final Logger LOGGER = LogUtils.getLogger();
    // One line per action the client sends, so a held key would repeat it: the trace is development-only.


    // Every drawing request is routed through the menu the player has open, so nothing here trusts a position, a
    // session or a recipe the client named: the menu either has that session or it refuses.


    // The menu is resolved from the open container and checked against the id the client sent, so a stale or
    // forged id can only ever be ignored.


    static void onChequeAction(ChequeActionC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof ChequeTableMenu menu)
                || menu.containerId != payload.containerId()) return;
        if (payload.checkIn()) menu.checkIn(player);
        else menu.checkOut(player);
    }

    static void onStationTrade(StationTradeC2SPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player.containerMenu instanceof StationMenu menu && menu.isCustomer())
            menu.trade(player, payload.revision());
    }

    static void onPlayerTradeAction(PlayerTradeActionC2SPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player)
            PlayerTradeService.handleAction(player, payload.action(), payload.revision());
    }

    static void onBackSlotSwap(BackSlotSwapC2SPayload payload, IPayloadContext context) {
        Player player = context.player();
        ItemStack hand = player.getMainHandItem();
        CuriosApi.getCuriosInventory(player).flatMap(handler -> handler.getStacksHandler("back_weapon")).ifPresent(back -> {
            IDynamicStackHandler stacks = back.getStacks();
            if (stacks.getSlots() <= 0) return;
            ItemStack target = stacks.getStackInSlot(0);
            SlotContext slot = new SlotContext("back_weapon", player, 0, false, back.getRenders().getFirst());
            if (!hand.isEmpty() && !CuriosApi.isStackValid(slot, hand)) return;
            stacks.setStackInSlot(0, hand.copy());
            player.getInventory().setSelectedItem(target);
        });
    }

    static void onCultivationToggle(CultivationToggleC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Result<CultivationStep> result = CultivationModeService.toggle(player);
        boolean stepped = result instanceof Result.SuccessResult<CultivationStep> ok && (ok.data().started() || ok.data().stopped());
        if (!stepped) CultivationModeService.notifyFailure(player, result);
    }

    // The key is a held state, so it is written where the flight reads it and nowhere else: a player who is not
    // flying has no attachment to write it to, and the key does nothing until a flight is up.
    static void onFlightDescend(FlightDescendC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        player.getExistingData(MxtAttachments.FLIGHT).ifPresent(data -> data.setDescend(payload.pressed()));
    }
}
