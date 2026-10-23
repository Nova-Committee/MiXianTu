package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.data.item.HoldBinding;
import net.minecraft.core.RegistryAccess;
import net.neoforged.bus.api.Event;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Asks every module with hold-to-use items what it declares. The access is the caller's because the caller is the one
 * that holds it, and the whole answer is cached per item afterwards.
 */
public final class CollectHoldBindingsEvent extends Event {
    private final RegistryAccess registries;
    private final List<HoldBinding> bindings = new LinkedList<>();

    public CollectHoldBindingsEvent(RegistryAccess registries) {
        this.registries = registries;
    }

    public RegistryAccess getRegistries() {
        return this.registries;
    }

    public List<HoldBinding> getBindings() {
        return List.copyOf(this.bindings);
    }

    public void add(HoldBinding binding) {
        this.bindings.add(binding);
    }

    public void addAll(Collection<? extends HoldBinding> bindings) {
        this.bindings.addAll(bindings);
    }
}
