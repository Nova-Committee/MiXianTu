package com.iafenvoy.mxt.event.dispatcher;

import com.iafenvoy.mxt.data.item.HoldBinding;
import net.minecraft.core.RegistryAccess;
import net.neoforged.bus.api.Event;

import java.util.Collection;
import java.util.List;

/**
 * Asks every module with hold-to-use items what it declares. The access is the caller's because the caller is the one
 * that holds it, and the whole answer is cached per item afterwards.
 */
public final class CollectHoldBindingsEvent extends Event {
    private final RegistryAccess registries;
    private final List<HoldBinding> bindings;

    public CollectHoldBindingsEvent(RegistryAccess registries, List<HoldBinding> bindings) {
        this.registries = registries;
        this.bindings = bindings;
    }

    public RegistryAccess registries() {
        return this.registries;
    }

    public void add(HoldBinding binding) {
        this.bindings.add(binding);
    }

    public void addAll(Collection<? extends HoldBinding> bindings) {
        this.bindings.addAll(bindings);
    }
}
