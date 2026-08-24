package com.iafenvoy.mxt.network;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.network.payload.*;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.artifact.FlightService;
import com.iafenvoy.mxt.runtime.artifact.FlightService.Failure;
import com.iafenvoy.mxt.runtime.economy.PlayerTradeService;
import com.iafenvoy.mxt.runtime.forging.ForgingWorkstationService;
import com.iafenvoy.mxt.runtime.spirit.SpiritBurstService;
import com.iafenvoy.mxt.screen.menu.ChequeTableMenu;
import com.iafenvoy.mxt.screen.menu.StationMenu;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerNetworkHandler {
    static void onAbilityAction(AbilityActionC2SPayload payload, IPayloadContext context) {
        Player player = context.player();
        AbilityAttachment abilities = player.getData(MxtAttachments.ABILITY_HOLDER);
        if (payload.cancel()) {
            MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, payload.ability()).ifPresent(ability ->
                    AbilityService.cancelCast(ability, abilities, player.level().getGameTime()));
            return;
        }
        MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, payload.ability()).ifPresent(ability -> {
            ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
            AbilityService.use(ability, ability.value(), player, abilities, resources, player.level().getGameTime(), FormulaContext.of(player));
        });
    }

    static void onForgingAction(ForgingActionC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Identifier definition = payload.definition().orElse(null);
        switch (payload.action()) {
            case START ->
                    MxtDatapackRegistries.get(MxtResourceKeys.FORGING_BLUEPRINT, definition).ifPresent(blueprint ->
                            ForgingWorkstationService.start(player, payload.position(), definition, blueprint));
            case STRIKE -> MxtDatapackRegistries.get(MxtResourceKeys.FORGING_METHOD, definition).ifPresent(method ->
                    ForgingWorkstationService.strike(player, payload.position(), definition, method));
            case FINISH -> ForgingWorkstationService.finish(player, payload.position(), definition);
            case CANCEL -> ForgingWorkstationService.cancel(player, payload.position());
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

    static void onSpiritBurst(SpiritBurstC2SPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player)
            SpiritBurstService.setFiring(player, payload.resource(), payload.firing());
    }
}
