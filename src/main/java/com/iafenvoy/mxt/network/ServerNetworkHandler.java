package com.iafenvoy.mxt.network;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.WheelLayoutAttachment;
import com.iafenvoy.mxt.item.block.entity.ForgingTableBlockEntity;
import com.iafenvoy.mxt.network.payload.*;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.artifact.FlightService;
import com.iafenvoy.mxt.runtime.artifact.FlightService.Failure;
import com.iafenvoy.mxt.runtime.cultivation.CultivationActionService.Result;
import com.iafenvoy.mxt.runtime.cultivation.CultivationModeService;
import com.iafenvoy.mxt.runtime.economy.PlayerTradeService;
import com.iafenvoy.mxt.runtime.forging.ForgingWorkstationService;
import com.iafenvoy.mxt.runtime.wheel.WheelService;
import com.iafenvoy.mxt.screen.menu.ChequeTableMenu;
import com.iafenvoy.mxt.screen.menu.ForgingMenu;
import com.iafenvoy.mxt.screen.menu.StationMenu;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.PlayerNames;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.Optional;


public final class ServerNetworkHandler {
    public static final Logger MXT_DEBUG = LogUtils.getLogger();

    // The payload carries what was chosen and off which page, and nothing else: whether the player may use it is
    // decided here and by the pipeline behind it, never by the screen that sent this.
    static void onWheelAction(WheelActionC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        WheelService.trigger(player, payload.source(), payload.kind(), payload.id());
    }

    // Forced to twelve sectors with every id resolved, so it can only contain things this server could trigger.
    static void onWheelLayout(WheelLayoutC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        WheelLayoutAttachment attachment = player.getData(MxtAttachments.WHEEL_LAYOUT);
        attachment.setLayout(WheelService.sanitize(player, payload.layout()));
    }

    // A number outside the numbering is stored as "nothing armed" and logged, since only a client bug produces one.
    static void onWheelSelection(WheelSelectionC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Integer submitted = payload.armed().orElse(null);
        Optional<Integer> armed = WheelService.sanitizeArmed(submitted);
        if (submitted != null && armed.isEmpty())
            MiXianTu.LOGGER.warn("Discarding the wheel cell {} sent by {}: not a cell number",
                    submitted, player.getGameProfile().name());
        player.getData(MxtAttachments.WHEEL_LAYOUT).setArmed(armed);
    }

    static void onForgingAction(ForgingActionC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        // The table comes from the menu the player has open, not from the packet: the client cannot reach the block
        // at all (its ContainerLevelAccess is NULL), so a position in the request would have to be trusted coming
        // back. Resolving here is what leaves the distance check as the only remaining thing to verify.
        if (!(player.containerMenu instanceof ForgingMenu menu)) return;
        if (!(menu.table() instanceof ForgingTableBlockEntity table)) return;
        Identifier definition = payload.definition().orElse(null);
        // `active` is logged after the call for the two actions that can settle a session by themselves: a strike
        // that completes the piece ends it, so "the session is gone afterwards" is the success path.
        switch (payload.action()) {
            case SELECT -> MXT_DEBUG.info("forging SELECT definition={} selectable={} methods={} outcome={} active={}",
                    definition, table.selectableBlueprintIds(), table.availableMethodIds(),
                    ForgingWorkstationService.start(player, table, definition),
                    table.forgingState().active());
            case STRIKE -> MXT_DEBUG.info("forging STRIKE definition={} available={} outcome={} active={}",
                    definition, table.availableMethodIds(),
                    ForgingWorkstationService.strike(player, table, definition),
                    table.forgingState().active());
            case FINISH -> MXT_DEBUG.info("forging FINISH outcome={}", ForgingWorkstationService.finish(player, table));
            case CANCEL -> MXT_DEBUG.info("forging CANCEL outcome={}", ForgingWorkstationService.cancel(player, table));
        }
    }

    static void onFlightToggle(FlightToggleC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!payload.enabled()) {
            if (player.getData(MxtAttachments.FLIGHT).archetype().map(HolderHelper::id).filter(payload.archetype()::equals).isPresent()) {
                FlightService.dismount(player, Failure.STOPPED);
            }
            return;
        }
        MxtDatapackRegistries.holder(MxtResourceKeys.ARTIFACT, payload.archetype()).ifPresent(archetype ->
                FlightService.mount(player, player.getMainHandItem(), archetype, FormulaContext.of(player)));
    }

    static void onChequeAction(ChequeActionC2SPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!(player.containerMenu instanceof ChequeTableMenu menu))
            return;
        if (payload.checkIn()) menu.checkIn(player);
        else menu.checkOut();
    }

    static void onStationTrade(StationTradeC2SPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player.containerMenu instanceof StationMenu menu && menu.isCustomer())
            menu.trade(player);
    }

    static void onPlayerTradeAction(PlayerTradeActionC2SPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) PlayerTradeService.handleAction(player, payload.action());
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
        Result result = CultivationModeService.toggle(player);
        if (!result.started() && !result.stopped()) CultivationModeService.notifyFailure(player, result);
    }

    // Answered out of what the server already knows - the online player, or the name its profile cache kept from a
    // previous login. Nothing is fetched (see PlayerNames#knownToServer), so an unknown id answers with nothing.
    static void onOwnerNameRequest(OwnerNameC2SPayload payload, IPayloadContext context) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        Optional<String> name = server == null ? Optional.empty() : PlayerNames.knownToServer(server, payload.owner());
        context.reply(new OwnerNameS2CPayload(payload.owner(), name));
    }
}
