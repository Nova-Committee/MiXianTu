package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.api.ItemUseCycle;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

/** Resolves one gesture per click; the first claim wins, so event priority decides provider precedence. */
public final class ResolveItemUseCycleEvent extends Event {
    private final LivingEntity holder;
    private final ItemStack stack;
    @Nullable
    private ItemUseCycle cycle;

    public ResolveItemUseCycleEvent(LivingEntity holder, ItemStack stack) {
        this.holder = holder;
        this.stack = stack;
    }

    public LivingEntity getHolder() {
        return this.holder;
    }

    public ItemStack getStack() {
        return this.stack;
    }

    @Nullable
    public ItemUseCycle getCycle() {
        return this.cycle;
    }

    public boolean claim(ItemUseCycle cycle) {
        if (this.cycle != null) return false;
        this.cycle = java.util.Objects.requireNonNull(cycle);
        return true;
    }
}
