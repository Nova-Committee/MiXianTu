package com.iafenvoy.mxt.probe.verify;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.api.AuraAccess;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostPayment;
import com.iafenvoy.mxt.data.cost.CostTarget;
import com.iafenvoy.mxt.data.cost.Debit;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.ResultWithData;
import com.iafenvoy.mxt.runtime.resulting.failures.CostFailure;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What one bound fee is worth once it is bound: the store it names, the receipt it hands back, the amount it refuses,
 * and the answer it keeps after it stopped.
 *
 * <p>Each leg drives a real transaction or a real bill; the store is a fixture, because the parts under test are the
 * payment's own decisions and not the ones a block entity makes.
 */
public final class CostStoreVerifyProbes {
    private CostStoreVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/cost_store_identity", CostStoreVerifyProbes::storeIdentity),
                Probe.of("verify/cost_receipt", CostStoreVerifyProbes::receipt),
                Probe.of("verify/cost_bank_exact", CostStoreVerifyProbes::bankExact),
                Probe.of("verify/cost_amount_bound", CostStoreVerifyProbes::amountBound),
                Probe.of("verify/cost_edit_scope", CostStoreVerifyProbes::editScope),
                Probe.of("verify/cost_scale_per_source", CostStoreVerifyProbes::scalePerSource)
        );
    }

    // One section of one chunk is one stock: two positions inside it are one store, so two fees written at them are one
    // charge, while the same position in another section is another store.
    private static void storeIdentity(ProbeContext context) {
        RegistryAccess registries = context.registries();
        ResourceKey<Level> dimension = context.level().dimension();
        // Positions are read, not visited, so the sibling may sit outside the arena: what is under test is the identity
        // of a section, and the arena itself spans more than one chunk.
        BlockPos base = context.at(1, 1, 1);
        int chunkX = SectionPos.blockToSectionCoord(base.getX());
        int chunkZ = SectionPos.blockToSectionCoord(base.getZ());
        BlockPos sibling = new BlockPos(SectionPos.sectionToBlockCoord(chunkX, 15), base.getY(),
                SectionPos.sectionToBlockCoord(chunkZ, 15));
        BlockPos above = base.above(16);
        CostTarget.Section near = CostTarget.Section.of(dimension, base);
        CostTarget.Section far = CostTarget.Section.of(dimension, sibling);
        context.expect("two positions of one section are one store", near, far);
        if (near.equals(CostTarget.Section.of(dimension, above)))
            context.fail("two sections of one chunk read as one store");

        // Two aura entries at two positions inside one section add up and are checked once against that one stock.
        Holder<Aura> qi = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        CostContext here = CostContext.pool(context.player(), context.level(), base,
                FormulaContext.of(context.player()), CostOrigin.SCRIPT);
        CostContext there = CostContext.pool(context.player(), context.level(), sibling,
                FormulaContext.of(context.player()), CostOrigin.SCRIPT);
        CostPayment bill = CostPayment.of(here);
        String entry = "[{\"type\": \"mxt:aura\", \"aura\": \"mxt_test:qi\", \"amount\": 1}]";
        if (bill.price(decodeCosts(registries, entry), here).isPresent()
                || bill.price(decodeCosts(registries, entry), there).isPresent()) {
            context.fail("two pool fees in one section could not be priced: " + bill.failure());
            return;
        }
        Map<Holder<Aura>, Double> auraPrice = bill.auras();
        context.expect("two fees of one section are priced as one", auraPrice.getOrDefault(qi, 0.0D), 2.0D);
    }

    // What left a container is named by what was there before the write: a full stack that emptied still reports its
    // item and its count instead of the empty stack the shrink left behind.
    private static void receipt(ProbeContext context) {
        RegistryAccess registries = context.registries();
        SimpleContainer container = new SimpleContainer(1);
        container.setItem(0, new ItemStack(Items.EMERALD, 2));
        CostContext costs = CostContext.of(context.player(), CostOrigin.SCRIPT).withItemTarget(container);
        CostPayment.PaymentResult paid = CostPayment.pay(decodeCosts(registries,
                "[{\"type\": \"mxt:item\", \"items\": [\"minecraft:emerald\"], \"amount\": 2}]"), costs);
        if (!paid.isSuccess()) {
            context.fail("a payable item cost was refused: " + paid.failure());
            return;
        }
        if (container.getItem(0).getCount() != 0) context.fail("a 2-item cost left " + container.getItem(0).getCount());
        List<Debit> debits = paid.debits();
        if (debits.size() != 1) {
            context.fail("a one-fee payment reported " + debits.size() + " receipts");
            return;
        }
        List<ItemStack> items = debits.getFirst().items();
        if (items.size() != 1) {
            context.fail("the emptiable stack was reported as " + items.size() + " stacks");
            return;
        }
        context.expect("the receipt keeps the item that left", items.getFirst().getItem(), Items.EMERALD);
        context.expect("the receipt keeps the count that left", items.getFirst().getCount(), 2);
    }

    // A bank that cannot promise the whole price does not pay part of it: the fee is refused, the part that did leave is
    // reported instead of dropped, and the amount a bank cannot express is refused rather than clamped.
    private static void bankExact(ProbeContext context) {
        RegistryAccess registries = context.registries();
        Holder<Aura> qi = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        // Answers "all of it" when asked, then hands over 3 of the 5 it was asked for.
        ShortBank bank = new ShortBank(3);
        CostContext costs = CostContext.bank(bank, context.player(), FormulaContext.of(context.player()),
                CostOrigin.SCRIPT);
        CostPayment.PaymentResult paid = CostPayment.pay(decodeCosts(registries,
                "[{\"type\": \"mxt:aura\", \"aura\": \"mxt_test:qi\", \"amount\": 5}]"), costs);
        if (paid.isSuccess()) {
            context.fail("a bank that handed over 3 of 5 was reported as paid");
            return;
        }
        context.expect("a short bank refuses with the aura reason", paid.failure(), CostFailure.INSUFFICIENT_AURA);
        context.expect("the part the bank did hand over is reported", paid.auras().getOrDefault(qi, 0.0D), 3.0D);
        context.expect("the bank really lost the part it reported", bank.removed, 3);

        // The amount a bank cannot express is a refusal, not a silently smaller charge.
        ShortBank whole = new ShortBank(Integer.MAX_VALUE);
        CostContext huge = CostContext.bank(whole, context.player(), FormulaContext.of(context.player()),
                CostOrigin.SCRIPT);
        CostPayment.PaymentResult refused = CostPayment.pay(decodeCosts(registries,
                "[{\"type\": \"mxt:aura\", \"aura\": \"mxt_test:qi\", \"amount\": 3000000000}]"), huge);
        if (refused.isSuccess() || refused.failure() != CostFailure.INVALID_AMOUNT)
            context.fail("an amount past the integer range read as " + refused.failure());
        context.expect("an amount past the integer range took nothing", whole.removed, 0);

        // An item demand past the integer range is the same refusal, and takes nothing either.
        SimpleContainer container = new SimpleContainer(1);
        container.setItem(0, new ItemStack(Items.EMERALD, 3));
        CostContext items = CostContext.of(context.player(), CostOrigin.SCRIPT).withItemTarget(container);
        CostPayment.PaymentResult tooMany = CostPayment.pay(decodeCosts(registries,
                "[{\"type\": \"mxt:item\", \"items\": [\"minecraft:emerald\"], \"amount\": 3000000000}]"), items);
        if (tooMany.isSuccess() || tooMany.failure() != CostFailure.INVALID_AMOUNT)
            context.fail("an item count past the integer range read as " + tooMany.failure());
        context.expect("an item count past the integer range took nothing", container.getItem(0).getCount(), 3);
    }

    // A check takes nothing and reports nothing: the simulated pass of a resource fee answers with no receipt.
    private static void amountBound(ProbeContext context) {
        RegistryAccess registries = context.registries();
        Holder<Resource> probe = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.id("trigger_probe"));
        ResourceHolderAttachment account = new ResourceHolderAttachment();
        account.set(probe, 10.0D, 0.0D, 10_000.0D, -1L, "probe");
        CostContext costs = CostContext.account(account, null, FormulaContext.of(context.player()), CostOrigin.SCRIPT);
        Cost fee = decodeCosts(registries, "[{\"id\": \"mxt_test:trigger_probe\", \"amount\": 4}]").getFirst();
        ResultWithData<Cost.Transaction> prepared = fee.prepare(costs);
        if (prepared.isFailure()) {
            context.fail("a plain resource fee could not be prepared: " + prepared.failure());
            return;
        }
        ResultWithData<List<Debit>> simulated = prepared.getData().execute(true);
        if (simulated.isFailure()) {
            context.fail("a payable resource fee was refused when asked: " + simulated.failure());
            return;
        }
        context.expect("a check writes nothing", simulated.getData().size(), 0);
        context.expect("a check takes nothing", account.get(probe), 10.0D);
    }

    // A table of amounts names one owner: a bill charging two accounts cannot say whose balance an id means, so the
    // edit is refused instead of picking one payer for all of it.
    private static void editScope(ProbeContext context) {
        RegistryAccess registries = context.registries();
        Holder<Resource> probe = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.id("trigger_probe"));
        ResourceHolderAttachment first = new ResourceHolderAttachment();
        first.set(probe, 10.0D, 0.0D, 10_000.0D, -1L, "probe");
        ResourceHolderAttachment second = new ResourceHolderAttachment();
        second.set(probe, 10.0D, 0.0D, 10_000.0D, -1L, "probe");
        FormulaContext formula = FormulaContext.of(context.player());
        CostContext one = CostContext.account(first, null, formula, CostOrigin.SCRIPT);
        CostContext two = CostContext.account(second, null, formula, CostOrigin.SCRIPT);
        String entry = "[{\"id\": \"mxt_test:trigger_probe\", \"amount\": 2}]";
        CostPayment mixed = CostPayment.of(one);
        if (mixed.price(decodeCosts(registries, entry), one).isPresent()
                || mixed.price(decodeCosts(registries, entry), two).isPresent()) {
            context.fail("a two-account bill could not be priced");
            return;
        }
        if (mixed.samePayer()) context.fail("a bill charging two accounts read as one payer");
        Optional<Failure> edited = mixed.replaceResourceAmounts(Map.of(ProbeDefinitions.id("trigger_probe"), 1.0D));
        if (edited.isEmpty() || edited.get() != CostFailure.AMBIGUOUS_PAYER)
            context.fail("editing a two-account bill read as " + edited);

        // Paying it untouched still charges each account its own share: the refusal is about the edit, not the bill.
        CostPayment.PaymentResult paid = mixed.pay();
        if (!paid.isSuccess()) {
            context.fail("an unedited two-account bill was refused: " + paid.failure());
            return;
        }
        context.expect("the first account paid its own share", first.get(probe), 8.0D);
        context.expect("the second account paid its own share", second.get(probe), 8.0D);
    }

    // A bill scaled per source charges each source by its own grant: a source nobody was granted leaves the bill
    // instead of being charged at another source's ratio.
    private static void scalePerSource(ProbeContext context) {
        RegistryAccess registries = context.registries();
        Holder<Aura> qi = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        Holder<Aura> spirit = context.require(MxtResourceKeys.AURA, ProbeDefinitions.SPIRIT_POWER);
        CostContext here = CostContext.pool(context.player(), context.level(), context.at(1, 1, 1),
                FormulaContext.of(context.player()), CostOrigin.SCRIPT);
        CostPayment bill = CostPayment.of(here);
        String entries = "[{\"type\": \"mxt:aura\", \"aura\": \"mxt_test:qi\", \"amount\": 10},"
                + "{\"type\": \"mxt:aura\", \"aura\": \"mxt_test:spirit_power\", \"amount\": 10}]";
        if (bill.price(decodeCosts(registries, entries), here).isPresent()) {
            context.fail("a two-aura bill could not be priced: " + bill.failure());
            return;
        }
        // Grant: all of the first, none of the second. The averaged ratio would charge 5 and 5.
        Map<Holder<Aura>, Double> granted = Map.of(qi, 10.0D, spirit, 0.0D);
        Optional<Failure> scaled = bill.scale(transaction -> transaction.target() instanceof CostTarget.AuraStore,
                transaction -> transaction.target() instanceof CostTarget.AuraStore store
                        ? granted.getOrDefault(store.aura(), 0.0D) / transaction.amount() : 1.0D);
        if (scaled.isPresent()) {
            context.fail("scaling a bill per source failed: " + scaled.get());
            return;
        }
        Map<Holder<Aura>, Double> price = bill.auras();
        context.expect("a granted source keeps its whole charge", price.getOrDefault(qi, 0.0D), 10.0D);
        if (price.containsKey(spirit))
            context.fail("a source nobody was granted is still charged " + price.get(spirit));
    }

    private static List<Cost> decodeCosts(RegistryAccess registries, String json) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        return Cost.LIST_CODEC.parse(ops, JsonParser.parseString(json))
                .getOrThrow(error -> new IllegalArgumentException("Invalid cost fixture: " + error));
    }

    // A store that says it can pay and then parts with less: the one shape a bank's answer cannot rule out.
    private static final class ShortBank implements AuraAccess {
        private final int willing;
        private int removed;

        private ShortBank(int willing) {
            this.willing = willing;
        }

        @Override
        public Object2IntMap<Holder<Aura>> getCapacity(@Nullable LivingEntity entity,
                                                       @NotNull HolderLookup.Provider registries) {
            return Object2IntMaps.emptyMap();
        }

        @Override
        public int insert(@Nullable LivingEntity entity,
                          @NotNull HolderLookup.Provider registries, Holder<Aura> aura, int amount, boolean exact, boolean simulate) {
            return amount;
        }

        @Override
        public int extract(@Nullable LivingEntity entity,
                           @NotNull HolderLookup.Provider registries, Holder<Aura> aura, int amount, boolean exact, boolean simulate) {
            // Asked exactly, it promises the whole amount: this is the store that empties between the two passes.
            if (simulate) return 0;
            int taken = Math.min(this.willing, amount);
            this.removed += taken;
            return amount - taken;
        }
    }
}
