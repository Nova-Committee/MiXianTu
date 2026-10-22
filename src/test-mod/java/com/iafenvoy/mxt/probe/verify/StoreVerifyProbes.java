package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.api.ItemAuraAccess;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.data.item.TalismanComponent;
import com.iafenvoy.mxt.data.item.TalismanComponent.TriggerMode;
import com.iafenvoy.mxt.data.talisman.Talisman;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The store protocol's two questions. A fee asks through {@code exact}, where a store either moves the whole amount or
 * nothing, because a part taken by a refusal is a part lost; a fill asks without it, where moving what fits is the
 * point. Both directions have to answer both ways, otherwise a carrier one unit short of a price would be drained and
 * the price would still be refused.
 */
public final class StoreVerifyProbes {
    private StoreVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/aura_store_exact", StoreVerifyProbes::exactIsAllOrNothing));
    }

    private static void exactIsAllOrNothing(ProbeContext context) {
        Holder<Aura> aura = context.require(MxtResourceKeys.AURA, ProbeDefinitions.SPIRIT_POWER);
        Holder<Talisman> sigil = context.require(MxtResourceKeys.TALISMAN, ProbeDefinitions.id("buffer_sigil"));
        ItemStack stack = MxtItems.TALISMAN.toStack();
        stack.set(MxtDataComponents.TALISMAN, TalismanComponent.of(sigil, TriggerMode.FIRE));
        if (!(stack.getItem() instanceof ItemAuraAccess store)) {
            context.fail("a talisman carrier no longer stores aura");
            return;
        }
        double capacity = store.getCapacity(context.player(), context.registries(), stack, aura);
        if (!context.check("the fixture has room to fill", capacity > 0)) return;
        double over = capacity + 1;
        // A fill past the room is what a partial store does; asked exactly, it must move nothing.
        context.expect("an exact fill past the room takes nothing",
                store.insert(context.player(), context.registries(), stack, aura, over, true, false), over);
        context.expect("and writes nothing", stored(stack, aura), 0.0D);
        // The same ask without exact is logistics: fill what fits, report the rest.
        context.expect("a partial fill reports the leftover",
                store.insert(context.player(), context.registries(), stack, aura, over, false, false), 1.0D);
        context.expect("and filled the room it had", stored(stack, aura), capacity);
        // Draining is the same question from the other side: one unit short of the whole amount moves nothing.
        context.expect("an exact drain past the stock takes nothing",
                store.extract(context.player(), context.registries(), stack, aura, over, true, false), over);
        context.expect("and leaves the stock alone", stored(stack, aura), capacity);
        context.expect("a partial drain reports the leftover",
                store.extract(context.player(), context.registries(), stack, aura, over, false, false), 1.0D);
        context.expect("and emptied what was there", stored(stack, aura), 0.0D);
        // An amount that does fit is moved whole either way.
        context.expect("an exact fill that fits moves all of it",
                store.insert(context.player(), context.registries(), stack, aura, capacity, true, false), 0.0D);
        // Simulating the exact form is how a fee asks without spending anything.
        context.expect("a simulated exact drain answers yes",
                store.extract(context.player(), context.registries(), stack, aura, capacity, true, true), 0.0D);
        context.expect("and simulated nothing away", stored(stack, aura), capacity);
        context.expect("an exact drain that fits moves all of it",
                store.extract(context.player(), context.registries(), stack, aura, capacity, true, false), 0.0D);
        context.expect("and emptied the store", stored(stack, aura), 0.0D);
    }

    private static double stored(ItemStack stack, Holder<Aura> aura) {
        return stack.getOrDefault(MxtDataComponents.SPIRIT_STORAGE, SpiritStorageComponent.EMPTY).get(aura);
    }
}
