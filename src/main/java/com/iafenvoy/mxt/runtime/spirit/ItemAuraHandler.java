package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.api.AuraHandler;
import com.iafenvoy.mxt.api.ItemAuraAccess;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.item.SpiritStoneItem;
import com.iafenvoy.mxt.item.SpiritVesselItem;
import com.iafenvoy.mxt.registry.MxtCapabilities.ItemContext;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.iafenvoy.mxt.runtime.spirit.AuraHandlerUtil.requireNonNegative;

public final class ItemAuraHandler implements AuraHandler {
    private final Item item;
    private final ItemAuraAccess storage;
    private final ItemContext context;

    public ItemAuraHandler(ItemStack stack, ItemContext context) {
        this.item = stack.getItem();
        this.storage = (ItemAuraAccess) this.item;
        this.context = context;
    }

    private static ItemStack stack(ItemContext context) {
        ItemResource resource = context.access().getResource();
        return resource.isEmpty() ? ItemStack.EMPTY : resource.toStack(context.access().getAmount());
    }

    @Override
    public Set<Holder<Aura>> getAuras() {
        ItemStack stack = this.currentStack();
        if (stack.isEmpty()) return Set.of();
        Set<Holder<Aura>> auras = new LinkedHashSet<>();
        if (this.item instanceof SpiritVesselItem) {
            this.context.registries().lookupOrThrow(MxtResourceKeys.AURA).listElements()
                    .filter(aura -> SpiritVesselItem.auraCapacity(stack, aura) > 0.0D || SpiritVesselItem.storedAura(stack, aura) > 0.0D)
                    .forEach(auras::add);
        } else {
            auras.addAll(this.storage.getCapacity(this.context.actor(), this.context.registries(), stack).keySet());
            if (!(this.item instanceof SpiritStoneItem))
                auras.addAll(stack.getOrDefault(MxtDataComponents.SPIRIT_STORAGE, SpiritStorageComponent.EMPTY).amounts().keySet());
        }
        return Set.copyOf(auras);
    }

    @Override
    public double getAmount(Holder<Aura> aura) {
        return this.amount(this.currentStack(), aura);
    }

    @Override
    public double getCapacity(Holder<Aura> aura) {
        ItemStack stack = this.currentStack();
        if (stack.isEmpty()) return 0.0D;
        if (this.item instanceof SpiritVesselItem) return SpiritVesselItem.auraCapacity(stack, aura);
        return this.storage.getCapacity(this.context.actor(), this.context.registries(), stack, aura);
    }

    @Override
    public Object storageIdentity(Holder<Aura> aura) {
        return this.context.storageIdentity();
    }

    @Override
    public double insert(Holder<Aura> aura, double amount, TransactionContext transaction) {
        return this.change(aura, amount, transaction, true);
    }

    @Override
    public double extract(Holder<Aura> aura, double amount, TransactionContext transaction) {
        return this.change(aura, amount, transaction, false);
    }

    private ItemStack currentStack() {
        ItemStack stack = stack(this.context);
        return stack.is(this.item) ? stack : ItemStack.EMPTY;
    }

    private double amount(ItemStack stack, Holder<Aura> aura) {
        if (stack.isEmpty()) return 0.0D;
        if (this.item instanceof SpiritVesselItem) return SpiritVesselItem.storedAura(stack, aura);
        if (this.item instanceof SpiritStoneItem stone)
            return stone.storedAura(this.context.registries(), stack, aura);
        return stack.getOrDefault(MxtDataComponents.SPIRIT_STORAGE, SpiritStorageComponent.EMPTY).get(aura);
    }

    private double change(Holder<Aura> aura, double amount, TransactionContext parent, boolean inserting) {
        requireNonNegative(amount);
        ItemStack stack = this.currentStack();
        if (stack.isEmpty() || amount == 0.0D || this.context.level().isClientSide())
            return 0.0D;
        double before = this.amount(stack, aura);
        // The built-in item methods edit the detached stack. NeoForge journals the actual slot exchange.
        if (inserting)
            this.storage.insert(this.context.actor(), this.context.registries(), stack, aura, amount, false, false);
        else this.storage.extract(this.context.actor(), this.context.registries(), stack, aura, amount, false, false);
        double after = this.amount(stack, aura);
        double moved = inserting ? after - before : before - after;
        if (!Double.isFinite(moved) || moved <= 0.0D) return 0.0D;
        ItemResource updated = ItemResource.of(stack);
        try (Transaction transaction = Transaction.open(parent)) {
            if (this.context.access().exchange(updated, stack.getCount(), transaction) != stack.getCount()
                    || this.context.access().getAmount() != stack.getCount()
                    || !updated.equals(this.context.access().getResource())) return 0.0D;
            transaction.commit();
            return moved;
        }
    }
}
