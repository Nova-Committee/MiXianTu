package com.iafenvoy.mxt.runtime.hold;

import com.iafenvoy.mxt.data.item.HoldBinding;
import net.minecraft.core.HolderLookup.Provider;

import java.util.List;

/**
 * Where hold declarations come from. Every module with hold-to-use items registers one, and that is the whole of
 * what the hold module needs from that module: it never reads another module's registries, and knows of no
 * module in particular.
 * <p>
 * Registering is a one-liner at construction - {@code HoldLookup.register(registries -> ...)} - so a module can
 * keep its declarations in whatever registry it already has.
 */
@FunctionalInterface
public interface HoldSource {
    /**
     * Every hold this source declares for one snapshot of the registries. Called on tag updates and on server
     * start rather than per use, because the answers are cached per item afterwards.
     */
    List<HoldBinding> holds(Provider registries);
}
