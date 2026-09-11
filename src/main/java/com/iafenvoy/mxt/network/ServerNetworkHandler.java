package com.iafenvoy.mxt.network;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.HotbarLayoutAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.network.payload.*;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.artifact.FlightService;
import com.iafenvoy.mxt.runtime.artifact.FlightService.Failure;
import com.iafenvoy.mxt.runtime.cultivation.CultivationActionService.Result;
import com.iafenvoy.mxt.runtime.cultivation.CultivationModeService;
import com.iafenvoy.mxt.runtime.economy.PlayerTradeService;
import com.iafenvoy.mxt.item.block.entity.ForgingTableBlockEntity;
import com.iafenvoy.mxt.runtime.forging.ForgingWorkstationService;
import com.iafenvoy.mxt.runtime.spirit.SpiritBurstService;
import com.iafenvoy.mxt.screen.menu.ChequeTableMenu;
import com.iafenvoy.mxt.screen.menu.ForgingMenu;
import com.iafenvoy.mxt.screen.menu.StationMenu;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;


public final class ServerNetworkHandler {
    public static final Logger MXT_DEBUG = LogUtils.getLogger();
    static void onAbilityAction(AbilityActionC2SPayload payload, IPayloadContext context) {
        Player player = context.player();
        AbilityAttachment abilities = player.getData(MxtAttachments.ABILITY_HOLDER);
        if (payload.cancel()) {
            MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, payload.ability()).ifPresent(ability -> {
                // Releasing the input cancels either a pending cast or an active channel. Both
                // states are independent, so both are cleared before returning.
                AbilityService.cancelCast(ability, abilities, player.level().getGameTime());
                if (abilities.channelledAbility().filter(ability::equals).isPresent())
                    AbilityService.stopChannel(abilities);
            });
            return;
        }
        MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, payload.ability()).ifPresent(ability -> {
            ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
            AbilityService.use(ability, ability.value(), player, abilities, resources, player.level().getGameTime(), FormulaContext.of(player));
        });
    }

    static void onForgingAction(ForgingActionC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        // The table comes from the menu the player has open, not from the packet. The client cannot
        // reach the block at all - its ContainerLevelAccess is NULL - so a position in the request
        // would have to be published to it first and trusted coming back. Resolving here also scopes a
        // request to the table this player is actually standing at, which is what makes the distance
        // check below the only remaining thing to verify.
        if (!(player.containerMenu instanceof ForgingMenu menu)) return;
        if (!(menu.table() instanceof ForgingTableBlockEntity table)) return;
        Identifier definition = payload.definition().orElse(null);
        // The outcomes carry the reason a request was refused, and every branch below used to throw it
        // away - which makes "nothing happens" the only symptom a player can report.
        //
        // `active` is logged after the call for the two actions that can settle a session by themselves:
        // a strike that completes the piece ends it, so "the session is gone afterwards" is the success
        // path rather than something to go looking for.
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
        MxtDatapackRegistries.holder(MxtResourceKeys.ITEM_ARCHETYPE, payload.archetype()).ifPresent(archetype ->
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

    static void onSpiritBurst(SpiritBurstC2SPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player)
            SpiritBurstService.setFiring(player, payload.resource(), payload.firing());
    }

    static void onHotbarLayout(HotbarLayoutC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || payload.mode() == null) return;
        HotbarLayoutAttachment attachment = player.getData(MxtAttachments.HOTBAR_LAYOUT);
        if (payload.slots().size() > 9) return;
        attachment.setSlots(payload.mode(), payload.slots());
    }
}
