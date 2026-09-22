package com.iafenvoy.mxt.runtime.hold;

import com.iafenvoy.mxt.data.item.HoldBinding;
import net.minecraft.core.HolderLookup.Provider;

import java.util.List;

/**
 * Where hold declarations come from. Every module with hold-to-use items registers one - a one-liner at
 * construction, {@code HoldLookup.register(registries -> ...)} - so a module keeps its declarations in whatever
 * registry it already has and the hold module reads no other module's registries.
 */
@FunctionalInterface
public interface HoldSource {
    // Called on tag updates and on server start rather than per use, because the answers are cached per item
    // afterwards.
    List<HoldBinding> holds(Provider registries);
}
