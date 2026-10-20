package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.runtime.economy.CurrencyPaymentService;
import com.iafenvoy.mxt.runtime.economy.CurrencyValueService;
import com.iafenvoy.mxt.runtime.economy.PlayerTradeAction;
import com.iafenvoy.mxt.runtime.economy.PlayerTradeService;
import com.iafenvoy.mxt.runtime.economy.PlayerTradeService.RequestResult;
import com.iafenvoy.mxt.screen.menu.PlayerTradeMenu;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The two counters that move items on a player's behalf: the trade that swaps two offers, and the cheque table that
 * turns items into one item and back.
 *
 * <p>Both legs are about what a player keeps, so each one is driven through the real service or the real planner and
 * then read back as inventory plus drops.
 */
public final class EconomyVerifyProbes {
    private EconomyVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/economy/change_plan", EconomyVerifyProbes::changePlan),
                Probe.of("verify/economy/trade_offer_change", EconomyVerifyProbes::tradeOfferChange),
                Probe.of("verify/economy/trade_full_cancel", EconomyVerifyProbes::tradeFullCancel)
        );
    }

    // A plan that cannot fit is refused while counting, and a set the greedy answer cannot solve is named at load
    // time. The denominations are the test pack's, so the numbers are the fixture's own.
    private static void changePlan(ProbeContext context) {
        List<Long> shipped = List.of(1L, 10L, 100L, 1000L, 10000L, 100000L);
        context.check("a set of powers of ten is greedy solvable",
                CurrencyPaymentService.greedyFailure(shipped, CurrencyPaymentService.checkBound(shipped)).isEmpty());
        // 4 and 3: greedy takes 4 and cannot pay the remaining 2, although 3 + 3 can.
        OptionalLong broken = CurrencyPaymentService.greedyFailure(List.of(4L, 3L),
                CurrencyPaymentService.checkBound(List.of(4L, 3L)));
        if (broken.isEmpty()) context.fail("the 4/3 set passed the greedy check");
        else context.expect("the 4/3 set fails first at the value only combinations can pay", broken.getAsLong(), 6L);
        // Nothing to pay with, and no value to pay: both are answers, not errors.
        context.check("a set with no denominations reports nothing to check",
                CurrencyPaymentService.greedyFailure(List.of(), CurrencyPaymentService.checkBound(List.of())).isEmpty());
        // The bound is the sum of the two largest: past it no new gap can open.
        context.expect("the check bound is the sum of the two largest values",
                CurrencyPaymentService.checkBound(shipped), 110_000L);

        // The plan is bounded by the slot count it is asked for. The value is counted in whole stacks of the pack's
        // largest denomination, so the exact number of stacks is read off the plan rather than assumed.
        RegistryAccess provider = context.registries();
        long value = 1_600_000L;
        Optional<List<ItemStack>> planned = CurrencyPaymentService.makeChange(provider, value, Integer.MAX_VALUE);
        if (planned.isEmpty()) {
            context.fail("a value the pack can pay was refused");
            return;
        }
        int stacks = planned.get().size();
        context.check("a value past the slot count is refused while planning",
                CurrencyPaymentService.makeChange(provider, value, stacks - 1).isEmpty());
        context.check("the same value is accepted at exactly its own slot count",
                CurrencyPaymentService.makeChange(provider, value, stacks).isPresent());
        long total = planned.get().stream()
                .mapToLong(stack -> CurrencyValueService.unitValue(provider, stack).orElse(0L) * stack.getCount()).sum();
        context.expect("the planned stacks add back up to the value", total, value);
    }

    // A confirmation covers both offers: swapping what the partner gives after this side confirmed makes the trade a
    // different one, so it must not complete on the partner's own press alone.
    private static void tradeOfferChange(ProbeContext context) {
        ServerPlayer first = context.player();
        ServerPlayer second = context.joinBeside(first, 2.0D, 0.0D);
        context.expect("a trade request between two nearby players is sent",
                PlayerTradeService.request(first, second), RequestResult.SENT);
        context.expect("the answer to that request starts the trade",
                PlayerTradeService.request(second, first), RequestResult.STARTED);
        if (!(first.containerMenu instanceof PlayerTradeMenu firstMenu)) {
            context.fail("the first side of the trade did not open a menu");
            return;
        }
        if (!(second.containerMenu instanceof PlayerTradeMenu secondMenu)) {
            context.fail("the second side of the trade did not open a menu");
            return;
        }
        // The first side confirms an empty pair of offers, then the second side adds what it is giving. That addition
        // is a new trade, so the first side's press no longer counts and the second side's own press cannot settle it.
        PlayerTradeService.handleAction(first, PlayerTradeAction.ACCEPT);
        secondMenu.getSlot(0).set(new ItemStack(Items.DIAMOND, 3));
        context.check("a partner's offer change clears the confirmation this side already gave",
                !firstMenu.ownAccepted());
        PlayerTradeService.handleAction(second, PlayerTradeAction.ACCEPT);
        context.check("the second side's press alone does not settle a trade whose other confirmation was cleared",
                first.containerMenu == firstMenu && second.containerMenu == secondMenu);
        PlayerTradeService.handleAction(first, PlayerTradeAction.CLOSE);
    }

    // A cancel with a full inventory keeps every offered item: it either returns to the inventory or is dropped next
    // to the player. Nothing stays inside the offer container.
    private static void tradeFullCancel(ProbeContext context) {
        ServerPlayer first = context.player();
        ServerPlayer second = context.joinBeside(first, 2.0D, 0.0D);
        // Fill the first player's inventory so nothing can be returned into it.
        for (int slot = 0; slot < first.getInventory().getContainerSize(); slot++)
            first.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        context.expect("a trade request with a full inventory is still sent",
                PlayerTradeService.request(first, second), RequestResult.SENT);
        context.expect("the answer starts it",
                PlayerTradeService.request(second, first), RequestResult.STARTED);
        if (!(first.containerMenu instanceof PlayerTradeMenu menu)) {
            context.fail("the trade did not open a menu");
            return;
        }
        ItemStack offered = new ItemStack(MxtItems.CHEQUE.get(), 1);
        menu.getSlot(0).set(offered);
        PlayerTradeService.handleAction(first, PlayerTradeAction.CLOSE);
        // The offer grid is gone with the session, so the item has to be somewhere a player can reach: the inventory
        // is full, which leaves the drop. Either way it must not be inside the grid any more.
        context.check("a cancelled offer no longer sits in the grid", menu.getSlot(0).getItem().isEmpty());
        long kept = 0L;
        for (int slot = 0; slot < first.getInventory().getContainerSize(); slot++) {
            ItemStack stack = first.getInventory().getItem(slot);
            if (stack.is(MxtItems.CHEQUE.get())) kept += stack.getCount();
        }
        for (ItemEntity entity : context.level().getEntitiesOfClass(ItemEntity.class,
                first.getBoundingBox().inflate(8.0D))) {
            if (entity.getItem().is(MxtItems.CHEQUE.get())) kept += entity.getItem().getCount();
        }
        context.expect("a cancelled offer of a full inventory is kept as one item", kept, 1L);
    }
}
