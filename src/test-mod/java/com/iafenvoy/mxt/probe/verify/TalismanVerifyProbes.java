package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.api.ItemAuraAccess;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.data.item.TalismanComponent;
import com.iafenvoy.mxt.data.item.TalismanComponent.TriggerMode;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.talisman.Talisman;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.spirit.SpiritSource;
import com.iafenvoy.mxt.runtime.talisman.TalismanService;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * The talisman carrier's own half of the pour: what a carrier wears away by, what it costs the holder, the
 * inscription's gate, the aura store it fires out of, and what it hands back when it is spent.
 */
public final class TalismanVerifyProbes {
    private TalismanVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/talisman_wear", TalismanVerifyProbes::talismanWear),
                Probe.of("verify/talisman_ledger", TalismanVerifyProbes::talismanLedger),
                Probe.of("verify/talisman_gate", TalismanVerifyProbes::talismanGate),
                Probe.of("verify/talisman_buffer", TalismanVerifyProbes::talismanBuffer),
                Probe.of("verify/talisman_refund", TalismanVerifyProbes::talismanRefund));
    }

    private static void talismanWear(ProbeContext context) {
        String failure = verifyTalismanWear(context);
        if (failure != null) context.fail(failure);
    }

    private static void talismanLedger(ProbeContext context) {
        String failure = verifyTalismanLedger(context);
        if (failure != null) context.fail(failure);
    }

    private static void talismanGate(ProbeContext context) {
        String failure = verifyTalismanGate(context);
        if (failure != null) context.fail(failure);
    }

    private static void talismanBuffer(ProbeContext context) {
        String failure = verifyTalismanBuffer(context);
        if (failure != null) context.fail(failure);
    }

    private static void talismanRefund(ProbeContext context) {
        String failure = verifyTalismanRefund(context);
        if (failure != null) context.fail(failure);
    }

    // What a carrier wears away by, through the real invocation entry point: the cap comes off the inscriptions and
    // lands on the stack as the vanilla component, each invocation takes its own cost off, and the invocation that
    // would pass the cap destroys the carrier instead. A refused invocation would leave the stack untouched, so the
    // resource the fixture ability adds is the proof that the wear was spent on a firing.
    private static String verifyTalismanWear(ProbeContext context) {
        Holder<Talisman> durable = context.require(MxtResourceKeys.TALISMAN, ProbeDefinitions.id("durable_sigil"));
        ItemStack stack = carrier(durable);
        if (TalismanService.durability(stack) != 3 || TalismanService.durabilityCost(stack) != 1)
            return "a carrier written with a three-use talisman read cap " + TalismanService.durability(stack)
                    + " and cost " + TalismanService.durabilityCost(stack) + " instead of 3 and 1";
        TalismanService.applyDurability(stack);
        if (!stack.isDamageableItem() || stack.getMaxDamage() != 3)
            return "the declared durability did not land on the stack as max_damage";
        // A number a pack patched onto the stack is the one that counts, not the one the definition declares.
        ItemStack patched = stack.copy();
        patched.set(DataComponents.MAX_DAMAGE, 3);
        if (TalismanService.durability(patched) != 3)
            return "a stack carrying its own max_damage was read off its inscriptions instead";

        ResourceHolderAttachment resources = context.player().getData(MxtAttachments.RESOURCE_HOLDER);
        Holder<Resource> common = context.require(MxtResourceKeys.RESOURCE, mxt("common"));
        double before = resources.get(common);
        try {
            SpiritSource placed = SpiritSource.placed(context.level(), context.player().position(), context.player());
            if (!TalismanService.invokeOnUse(placed, stack))
                return "the first invocation of a worn carrier did not fire";
            if (stack.getDamageValue() != 1)
                return "the first invocation left the carrier at " + stack.getDamageValue() + " wear instead of 1";
            if (!TalismanService.invokeOnUse(placed, stack))
                return "the second invocation of a worn carrier did not fire";
            if (stack.getDamageValue() != 2)
                return "the second invocation left the carrier at " + stack.getDamageValue() + " wear instead of 2";
            if (!TalismanService.invokeOnUse(placed, stack))
                return "the invocation that wears the carrier past its cap did not fire";
            if (!stack.isEmpty())
                return "a carrier worn past its cap survived at " + stack.getDamageValue() + " wear";
            if (!close(resources.get(common) - before, 9.0D))
                return "three invocations added " + (resources.get(common) - before) + " instead of the 9 the ability owes";

            // No declared wear anywhere means the old currency: one whole carrier per invocation.
            ItemStack plain = MxtItems.TALISMAN.toStack(2);
            plain.set(MxtDataComponents.TALISMAN, TalismanComponent.of(
                    context.require(MxtResourceKeys.TALISMAN, ProbeDefinitions.id("free_sigil")), TriggerMode.FIRE));
            if (TalismanService.durability(plain) != 0 || TalismanService.durabilityCost(plain) != 0)
                return "a carrier whose talisman declares no durability read a cap of " + TalismanService.durability(plain);
            if (!TalismanService.invokeOnUse(placed, plain) || plain.getCount() != 1)
                return "an invocation did not spend one whole carrier, leaving " + plain.getCount();
        } finally {
            resources.set(common, before);
        }
        return null;
    }

    // The carrier's price, driven the same way: a price the actor cannot pay refuses the invocation before anything
    // happens, and the same price is taken when the invocation does happen. The tier is no longer checked here: a
    // talisman definition declares none, and the drawing probes cover the grade an inscription stamps on the carrier.
    private static String verifyTalismanLedger(ProbeContext context) {
        ItemStack stack = carrier(context.require(MxtResourceKeys.TALISMAN, ProbeDefinitions.id("graded_sigil")));

        ResourceHolderAttachment resources = context.player().getData(MxtAttachments.RESOURCE_HOLDER);
        Holder<Resource> probe = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.id("trigger_probe"));
        Holder<Resource> common = context.require(MxtResourceKeys.RESOURCE, mxt("common"));
        double probeBefore = resources.get(probe);
        double commonBefore = resources.get(common);
        try {
            // Nothing to pay with: the invocation never happens, so neither the ability nor the carrier moved.
            resources.set(probe, 0.0D, 0.0D, 100_000.0D, -1L, "closure");
            SpiritSource placed = SpiritSource.placed(context.level(), context.player().position(), context.player());
            if (TalismanService.invokeOnUse(placed, stack))
                return "an invocation whose price could not be paid still happened";
            if (!close(resources.get(common), commonBefore))
                return "an invocation that could not pay its price still ran its ability";
            if (stack.getCount() != 1 || stack.getDamageValue() != 0)
                return "an invocation that could not pay its price still spent the carrier";
            // Enough to pay: the price is taken and the ability runs.
            resources.set(probe, 5.0D, 0.0D, 100_000.0D, -1L, "closure");
            if (!TalismanService.invokeOnUse(placed, stack))
                return "an invocation whose price could be paid did not happen";
            if (!close(resources.get(probe), 0.0D))
                return "the invocation left " + resources.get(probe) + " of its price unpaid";
            if (!close(resources.get(common) - commonBefore, 3.0D))
                return "the ability behind a paid invocation added " + (resources.get(common) - commonBefore) + " instead of 3";
        } finally {
            resources.set(probe, probeBefore);
            resources.set(common, commonBefore);
        }
        return null;
    }

    // The inscription's own condition, which the carrier asks before it prices anything: the fixture wants ten of
    // the very resource its price takes five of, so a holder with five is refused while its account stays whole,
    // and the same stack fires once the condition is met. One refused inscription refuses the carrier, so a free
    // one written next to a blocked one does not run either.
    private static String verifyTalismanGate(ProbeContext context) {
        ItemStack stack = carrier(context.require(MxtResourceKeys.TALISMAN, ProbeDefinitions.id("gated_sigil")));
        ResourceHolderAttachment resources = context.player().getData(MxtAttachments.RESOURCE_HOLDER);
        Holder<Resource> probe = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.id("trigger_probe"));
        Holder<Resource> common = context.require(MxtResourceKeys.RESOURCE, mxt("common"));
        double probeBefore = resources.get(probe);
        double commonBefore = resources.get(common);
        try {
            SpiritSource placed = SpiritSource.placed(context.level(), context.player().position(), context.player());
            // Enough to pay the price, not enough for the condition: the refusal must come first, so the price the
            // holder could have paid is still there and the ability never ran.
            resources.set(probe, 5.0D, 0.0D, 100_000.0D, -1L, "closure");
            if (TalismanService.invokeOnUse(placed, stack))
                return "an invocation its inscription's condition refused still happened";
            if (!close(resources.get(probe), 5.0D))
                return "a refused condition was checked after the price was taken, leaving " + resources.get(probe);
            if (!close(resources.get(common), commonBefore))
                return "an invocation refused by its condition still ran the ability behind it";
            if (stack.getCount() != 1 || stack.getDamageValue() != 0)
                return "an invocation refused by its condition still spent the carrier";
            // The condition is the only thing that changed, and the invocation is an ordinary one.
            resources.set(probe, 10.0D, 0.0D, 100_000.0D, -1L, "closure");
            if (!TalismanService.invokeOnUse(placed, stack))
                return "an invocation whose condition was met did not happen";
            if (!close(resources.get(probe), 5.0D))
                return "a met condition left " + resources.get(probe) + " instead of paying five of the ten";
            if (!close(resources.get(common) - commonBefore, 3.0D))
                return "the ability behind a met condition added " + (resources.get(common) - commonBefore);
            // A carrier carries one inscription, so a blocked one is simply a carrier that does not fire; the control
            // below is the same inscription written on its own.
            Holder<Talisman> blocked = context.require(MxtResourceKeys.TALISMAN, ProbeDefinitions.id("blocked_sigil"));
            ItemStack onlyBlocked = carrier(blocked);
            if (TalismanService.invokeOnUse(placed, onlyBlocked))
                return "a carrier whose inscription was blocked fired anyway";
            if (onlyBlocked.getCount() != 1)
                return "a carrier blocked by its inscription was still spent";
            if (!close(resources.get(common) - commonBefore, 3.0D))
                return "a blocked carrier still ran the ability its inscription carries";
            // The control: the free inscription on its own fires, so the leg above is about the block.
            Holder<Talisman> free = context.require(MxtResourceKeys.TALISMAN, ProbeDefinitions.id("free_sigil"));
            if (!TalismanService.invokeOnUse(placed, carrier(free)))
                return "an ungated carrier of the same inscription did not fire";
        } finally {
            resources.set(probe, probeBefore);
            resources.set(common, commonBefore);
        }
        return null;
    }

    // The capacity is a multiplier of what one invocation costs, so a carrier written with room for more than one
    // invocation fires again without being poured - and what the carrier can still spend caps it, so the fixture
    // writes five while its wear leaves three and is poured for three. Every invocation takes its own share off the
    // store rather than the whole pour. An empty carrier is not a firing one, which is the gate a click runs into.
    private static String verifyTalismanBuffer(ProbeContext context) {
        ItemStack stack = carrier(context.require(MxtResourceKeys.TALISMAN, ProbeDefinitions.id("buffer_sigil")));
        if (!(stack.getItem() instanceof ItemAuraAccess access)) return "a talisman carrier no longer stores aura";
        Holder<Aura> aura = context.require(MxtResourceKeys.AURA, ProbeDefinitions.SPIRIT_POWER);
        Holder<Resource> resource = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.SPIRIT_POWER);
        ResourceHolderAttachment resources = context.player().getData(MxtAttachments.RESOURCE_HOLDER);
        double before = resources.get(resource);
        try {
            resources.set(resource, 0.0D, 0.0D, 100_000.0D, -1L, "closure");
            Map<Holder<Aura>, Double> capacity = TalismanService.capacity(stack);
            if (capacity.getOrDefault(aura, 0.0D) != 9)
                return "a carrier written for five invocations over three of wear read " + capacity + " instead of 9";
            if (TalismanService.ready(stack)) return "an empty carrier was ready to fire";
            access.insert(context.player(), context.registries(), stack, aura, 9, false, false);
            if (!TalismanService.ready(stack)) return "a filled carrier was not ready to fire";
            SpiritSource placed = SpiritSource.placed(context.level(), context.player().position(), context.player());
            for (int shot = 1; shot <= 3; shot++) {
                if (!TalismanService.invokeOnUse(placed, stack))
                    return "invocation " + shot + " of a carrier holding three did not fire";
                double left = stored(stack, aura);
                if (!close(left, 9.0D - 3.0D * shot))
                    return "invocation " + shot + " left " + left + " units in the carrier instead of " + (9 - 3 * shot);
                if (!close(resources.get(resource), 0.0D))
                    return "an invocation out of a poured carrier still charged its holder " + resources.get(resource);
            }
            if (!stack.isEmpty()) return "a carrier worn out by three invocations survived";
        } finally {
            resources.set(resource, before);
        }
        return null;
    }

    // What a carrier was still holding when it was spent goes back to whoever set that invocation off: the pour
    // charged one unit of the aura's own resource per unit, so that is what returns. The fixture holds four
    // invocations of wear and is poured full; damaging it twice drops what it can still spend to two, so the two
    // invocations it fires leave two invocations of aura behind, and the wear that destroys it hands them back.
    private static String verifyTalismanRefund(ProbeContext context) {
        ItemStack stack = carrier(context.require(MxtResourceKeys.TALISMAN, ProbeDefinitions.id("refund_sigil")));
        if (!(stack.getItem() instanceof ItemAuraAccess access)) return "a talisman carrier no longer stores aura";
        Holder<Aura> aura = context.require(MxtResourceKeys.AURA, ProbeDefinitions.SOUL_POWER);
        Holder<Resource> resource = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.SOUL_POWER);
        ResourceHolderAttachment resources = context.player().getData(MxtAttachments.RESOURCE_HOLDER);
        double before = resources.get(resource);
        try {
            resources.set(resource, 0.0D, 0.0D, 100_000.0D, -1L, "closure");
            SpiritSource placed = SpiritSource.placed(context.level(), context.player().position(), context.player());
            access.insert(context.player(), context.registries(), stack, aura, 8, false, false);
            if (!close(stored(stack, aura), 8.0D)) return "a full carrier of four invocations did not hold eight units";
            // Wear the carrier down without firing it: what it can still spend decides what it may hold, so being
            // damaged past the point the store was sized for is what leaves aura behind when it finally breaks.
            TalismanService.applyDurability(stack);
            stack.setDamageValue(2);
            if (TalismanService.capacity(stack).getOrDefault(aura, 0.0D) != 4)
                return "a carrier with two invocations of wear left read a capacity of "
                        + TalismanService.capacity(stack) + " instead of 4";
            // One invocation of the two points of wear the fixture declares: the carrier survives, so what it spent
            // is not handed back, while what it has not spent stays in the store for the invocation after it.
            if (!TalismanService.invokeOnUse(placed, stack))
                return "the first invocation of a refundable carrier did not fire";
            if (stack.getCount() != 1 || stack.getDamageValue() != 3)
                return "a carrier with wear left was not left standing at three points of wear";
            if (!close(resources.get(resource), 0.0D))
                return "a carrier that survived its invocation handed its charge back instead of spending it";
            if (!close(stored(stack, aura), 6.0D))
                return "a carrier that survived its invocation kept " + stored(stack, aura) + " units instead of 6";
            // The second invocation is the one the wear destroys, and what it never spent comes back with the paper.
            if (!TalismanService.invokeOnUse(placed, stack))
                return "the invocation that burns the carrier out did not fire";
            if (!stack.isEmpty()) return "a carrier worn past its cap survived";
            if (!close(resources.get(resource), 4.0D))
                return "a carrier burned out by wear returned " + resources.get(resource) + " instead of the 4 it held";
        } finally {
            resources.set(resource, before);
        }
        return null;
    }

    private static double stored(ItemStack stack, Holder<Aura> aura) {
        return stack.getOrDefault(MxtDataComponents.SPIRIT_STORAGE, SpiritStorageComponent.EMPTY).get(aura);
    }

    private static ItemStack carrier(Holder<Talisman> inscribed) {
        ItemStack stack = MxtItems.TALISMAN.toStack();
        stack.set(MxtDataComponents.TALISMAN, TalismanComponent.of(inscribed, TriggerMode.FIRE));
        return stack;
    }

    private static Identifier mxt(String path) {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path);
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }
}
