package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.saturn.data.cost.CostTarget;
import com.iafenvoy.saturn.util.HolderHelper;
import net.minecraft.core.Holder;
import org.jetbrains.annotations.Nullable;

/**
 * One aura of one store: a section of a world pool, or a block entity's own bank. Saturn's own targets cover accounts,
 * items and scripts, so the aura channel names its store here and its label is the only thing the payment reads.
 */
public record AuraCostTarget(@Nullable Object store, Holder<Aura> aura) implements CostTarget {
    @Override
    public String label() {
        return HolderHelper.id(this.aura).toString();
    }
}
