package com.iafenvoy.mxt.api;

import com.iafenvoy.mxt.data.quality.ItemQuality;
import net.minecraft.core.Holder;

import java.util.Optional;

/**
 * A definition that answers the tier its own items start at. The question is about the stack, not the item: several
 * definitions of one type may share a single built-in item (a furnace specification, a technique manual, a pill),
 * and only the definition a stack carries can tell them apart.
 *
 * <p>Read as a default only. An explicit {@code mxt:quality} component on the stack always wins, and a stack that
 * carries no such definition falls back to the item's entry in the {@code mxt:default_quality} table; see
 * {@code QualityService.find} for the one order. A definition is reached through a registered carrier component
 * (see {@code QualityService.carry}), so implementing this interface without one changes nothing.</p>
 */
public interface QualityProvider {
    /**
     * Empty when the definition declares no tier, which leaves that stack to the next source.
     */
    Optional<Holder<ItemQuality>> defaultQuality();
}
