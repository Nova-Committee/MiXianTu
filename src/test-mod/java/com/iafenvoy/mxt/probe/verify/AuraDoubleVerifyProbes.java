package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.api.ItemAuraAccess;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostPayment;
import com.iafenvoy.mxt.data.cost.CostTarget;
import com.iafenvoy.mxt.data.cost.builtin.AuraCost;
import com.iafenvoy.mxt.data.cost.builtin.ResourceCost;
import com.iafenvoy.mxt.data.cost.context.AuraCostSources;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigins;
import com.iafenvoy.mxt.data.item.TalismanComponent;
import com.iafenvoy.mxt.data.talisman.Talisman;
import com.iafenvoy.mxt.item.block.entity.SpiritCraftingTableBlockEntity;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.*;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.resulting.failures.CostFailure;
import com.iafenvoy.mxt.runtime.spirit.AuraTransfers;
import com.iafenvoy.mxt.runtime.spirit.SpiritChargeService;
import com.iafenvoy.mxt.runtime.talisman.TalismanService;
import com.iafenvoy.mxt.util.TooltipText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.number.Constant;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

import java.util.List;
import java.util.Map;

public final class AuraDoubleVerifyProbes {
    private AuraDoubleVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/aura_double_arithmetic", AuraDoubleVerifyProbes::arithmetic),
                Probe.of("verify/aura_double_items", AuraDoubleVerifyProbes::items),
                Probe.of("verify/aura_double_payment", AuraDoubleVerifyProbes::payment),
                Probe.of("verify/aura_double_sources", AuraDoubleVerifyProbes::sources),
                Probe.of("verify/aura_double_capacity", AuraDoubleVerifyProbes::capacity));
    }

    private static void arithmetic(ProbeContext context) {
        double stored = 10.0D;
        for (int i = 0; i < 100; i++) {
            AuraTransfers.Transfer transfer = AuraTransfers.extract(stored, 0.1D, true);
            if (transfer.remainder() != 0.0D) context.fail("decimal withdrawal refused at " + i);
            stored = transfer.stored();
        }
        close(context, "one hundred tenths empty ten units", stored, 0.0D);
        for (int i = 0; i < 100; i++) {
            AuraTransfers.Transfer transfer = AuraTransfers.insert(stored, 10.0D, 0.1D, true);
            if (transfer.remainder() != 0.0D) context.fail("decimal deposit refused at " + i);
            stored = transfer.stored();
        }
        close(context, "one hundred tenths refill ten units", stored, 10.0D);
        context.check("tiny costs are not waived", AuraTransfers.extract(0.0D, 1.0E-14D, true).remainder() == 1.0E-14D);
        context.check("subnormal shortage is not waived", AuraTransfers.extract(Double.MIN_VALUE, 2.0D * Double.MIN_VALUE, true).remainder() == 2.0D * Double.MIN_VALUE);
        context.check("subnormal stock can be spent", AuraTransfers.extract(Double.MIN_VALUE, Double.MIN_VALUE, true).remainder() == 0.0D);
        context.check("unrepresentable withdrawal refuses", AuraTransfers.extract(1.0E30D, 0.25D, true).remainder() == 0.25D);
        context.check("unrepresentable deposit refuses", AuraTransfers.insert(1.0E30D, 2.0E30D, 0.25D, true).remainder() == 0.25D);
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.25D}) {
            boolean refused = false;
            try {
                AuraTransfers.extract(1.0D, invalid, true);
            } catch (IllegalArgumentException expected) {
                refused = true;
            }
            context.check("invalid amount refused: " + invalid, refused);
        }
    }

    private static void items(ProbeContext context) {
        Holder<Aura> qi = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        Holder<Aura> spirit = context.require(MxtResourceKeys.AURA, ProbeDefinitions.SPIRIT_POWER);
        ItemStack talisman = MxtItems.TALISMAN.toStack();
        talisman.set(MxtDataComponents.TALISMAN, TalismanComponent.of(
                context.require(MxtResourceKeys.TALISMAN, ProbeDefinitions.id("buffer_sigil")), TalismanComponent.TriggerMode.FIRE));
        ItemStack stone = MxtItems.SPIRIT_STONE.toStack();
        for (ItemStack stack : List.of(stone, talisman)) {
            Holder<Aura> aura = stack == stone ? qi : spirit;
            ItemAuraAccess access = (ItemAuraAccess) stack.getItem();
            SpiritStorageComponent original = SpiritStorageComponent.EMPTY.with(aura, 1.75D);
            stack.set(MxtDataComponents.SPIRIT_STORAGE, original);
            close(context, "exact shortage returns the entire request", access.extract(context.player(), context.registries(), stack, aura, 2.0D, true, false), 2.0D);
            context.check("exact shortage does not rewrite the component", stack.get(MxtDataComponents.SPIRIT_STORAGE) == original);
            close(context, "simulation accepts a fractional withdrawal", access.extract(context.player(), context.registries(), stack, aura, 0.25D, true, true), 0.0D);
            context.check("simulation does not rewrite the component", stack.get(MxtDataComponents.SPIRIT_STORAGE) == original);
            close(context, "one unit is withdrawn", access.extract(context.player(), context.registries(), stack, aura, 1.0D, true, false), 0.0D);
            close(context, "fractional remainder survives", stack.get(MxtDataComponents.SPIRIT_STORAGE).get(aura), 0.75D);
        }
    }

    private static SpiritCraftingTableBlockEntity bank(ProbeContext context, Holder<Aura> aura) {
        SpiritCraftingTableBlockEntity bank = new SpiritCraftingTableBlockEntity(context.at(2, 1, 2), MxtBlocks.SPIRIT_CRAFTING_TABLE.get().defaultBlockState());
        bank.configureAuraCosts(Map.of(aura, 0.25D));
        return bank;
    }

    private static void payment(ProbeContext context) {
        Holder<Aura> aura = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        SpiritCraftingTableBlockEntity bank = bank(context, aura);
        close(context, "a fractional recipe grants a fractional buffer", bank.getCapacity(null, context.registries(), aura), 24.75D);
        bank.insert(null, context.registries(), aura, 1.75D, true, false);
        CostContext costs = CostContext.bank(bank, context.player(), FormulaContext.of(context.player()), CostOrigins.SCRIPT);
        CostPayment.PaymentResult paid = CostPayment.pay(List.of(new AuraCost(aura, new Constant(0.25D))), costs);
        context.check("fractional bank payment succeeds", paid.isSuccess());
        close(context, "receipt retains the original fractional price", paid.auras().getOrDefault(aura, 0.0D), 0.25D);
        close(context, "bank loses precisely the quoted amount", bank.aura(aura), 1.5D);
        bank.extract(null, context.registries(), aura, 1.5D, true, false);
        bank.insert(null, context.registries(), aura, 24.5D, true, false);
        close(context, "partial fill accepts the last quarter", bank.insert(null, context.registries(), aura, 1.0D, false, false), 0.75D);
        close(context, "partial fill reaches the fractional maximum", bank.aura(aura), 24.75D);

        Holder<Aura> spirit = context.require(MxtResourceKeys.AURA, ProbeDefinitions.SPIRIT_POWER);
        ItemStack talisman = MxtItems.TALISMAN.toStack();
        talisman.set(MxtDataComponents.TALISMAN, TalismanComponent.of(
                context.require(MxtResourceKeys.TALISMAN, ProbeDefinitions.id("buffer_sigil")), TalismanComponent.TriggerMode.STORE));
        double capacity = TalismanService.capacity(talisman).get(spirit);
        talisman.set(MxtDataComponents.SPIRIT_STORAGE, SpiritStorageComponent.EMPTY.with(spirit, capacity - 0.25D));
        ResourceHolderAttachment account = context.player().getData(MxtAttachments.RESOURCE_HOLDER);
        account.set(spirit.value().resource(), 1.0D, 0.0D, 100_000.0D, -1L, "probe");
        context.player().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, talisman);
        com.iafenvoy.mxt.runtime.hold.ItemUseCycles.prepare(context.player(), talisman);
        context.player().startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        SpiritChargeService.onUseTick(new LivingEntityUseItemEvent.Tick(context.player(), talisman, 200));
        context.player().releaseUsingItem();
        close(context, "held pouring fills the final quarter", talisman.get(MxtDataComponents.SPIRIT_STORAGE).get(spirit), capacity);
        close(context, "held pouring charges only the final quarter", account.get(spirit.value().resource()), 0.75D);
    }

    private static void sources(ProbeContext context) {
        Holder<Aura> aura = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        AuraCost fee = new AuraCost(aura, new Constant(0.25D));
        FormulaContext formula = FormulaContext.of(context.player());
        SpiritCraftingTableBlockEntity bank = bank(context, aura);
        CostContext first = CostContext.bank(bank, null, formula, CostOrigins.SCRIPT);
        CostContext second = CostContext.bank(bank, context.player(), formula, CostOrigins.SCRIPT);
        context.expect("wrapping one bank retains its physical identity", fee.prepare(first).getData().target(), fee.prepare(second).getData().target());
        ResourceHolderAttachment account = new ResourceHolderAttachment();
        CostContext accountContext = CostContext.account(account, null, formula, CostOrigins.SCRIPT);
        Cost resourceFee = new ResourceCost(aura.value().resource(), new Constant(0.25D));
        context.expect("aura and resource share one physical account target", fee.prepare(accountContext).getData().target(), resourceFee.prepare(accountContext).getData().target());
        CostContext changed = CostContext.of(null, formula, CostOrigins.SCRIPT).withPayer(context.player());
        CostTarget.Account target = (CostTarget.Account) fee.prepare(changed).getData().target();
        context.check("account source follows the current payer", target.account() == changed.resourceTarget());
        CostContext quote = first.withAuraSource(AuraCostSources.QUOTE);
        Cost.Transaction transaction = fee.prepare(quote).getData();
        close(context, "quote has no inventory dependency", transaction.amount(), 0.25D);
        context.expect("quote cannot execute a payment", transaction.execute(true).failure(), CostFailure.NO_CHANNEL);
        context.expect("same-section positions retain identity", fee.prepare(CostContext.pool(null, context.level(), context.at(2, 1, 2), formula, CostOrigins.SCRIPT)).getData().target(),
                fee.prepare(CostContext.pool(null, context.level(), context.at(2, 1, 2).offset(0, (context.at(2, 1, 2).getY() & 15) == 15 ? -1 : 1, 0), formula, CostOrigins.SCRIPT)).getData().target());
    }

    private static void capacity(ProbeContext context) {
        Holder<Aura> aura = context.require(MxtResourceKeys.AURA, ProbeDefinitions.SPIRIT_POWER);
        Talisman base = context.require(MxtResourceKeys.TALISMAN, ProbeDefinitions.id("buffer_sigil")).value();
        Holder<Talisman> fractional = Holder.direct(new Talisman(base.type(), 1.5D, base.condition(), List.of(new AuraCost(aura, new Constant(0.25D)))));
        ItemStack stack = MxtItems.TALISMAN.toStack();
        stack.set(MxtDataComponents.TALISMAN, TalismanComponent.of(fractional, TalismanComponent.TriggerMode.FIRE));
        close(context, "talisman capacity keeps its fraction", TalismanService.capacity(stack).get(aura), 0.375D);
        close(context, "artifact capacity keeps its fraction", ArtifactService.capacity(context.registries(), new ItemStack(Items.STICK), aura, 0.375D, FormulaContext.of(context.player())), 0.375D);
        context.expect("integral values retain two decimal places", TooltipText.number(2.0D), "2.00");
        context.expect("fractional values round only for display", TooltipText.number(1.756D), "1.76");
        context.expect("negative zero is hidden", TooltipText.number(-0.001D), "0.00");
    }

    private static void close(ProbeContext context, String leg, double actual, double expected) {
        context.check(leg + ": " + actual + " != " + expected,
                Double.isFinite(actual) && Math.abs(actual - expected) <= 1.0E-9D);
    }
}
