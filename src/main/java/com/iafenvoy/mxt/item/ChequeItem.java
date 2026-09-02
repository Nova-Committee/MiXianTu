package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.data.economy.ChequeComponent;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A physical claim for a data-driven currency value.
 */
public final class ChequeItem extends Item {
    public ChequeItem(Properties properties) {
        super(properties.component(MxtDataComponents.CHEQUE, ChequeComponent.EMPTY));
    }

    public static ItemStack create(long value, String issuer) {
        if (value <= 0L) throw new IllegalArgumentException("Cheque value must be positive");
        ItemStack stack = new ItemStack(MxtItems.CHEQUE.get());
        stack.set(MxtDataComponents.CHEQUE.get(), new ChequeComponent(value, issuer));
        return stack;
    }
}
