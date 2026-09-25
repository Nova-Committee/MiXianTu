package com.iafenvoy.mxt.data.cost.context;


/**
 * The channels a cost entry can be paid through. A context offers a subset of them; an entry that needs a channel
 * the context does not offer simply cannot be paid there.
 */
public enum CostChannel {
    /**
     * The context's resource account: a payer's attachment, or one named directly (a formation with no owner).
     */
    RESOURCE_ACCOUNT,
    /**
     * The payer's inventory, which only a player has.
     */
    PLAYER_INVENTORY,
    /**
     * The shared aura pool at the context position.
     */
    WORLD_AURA,
    /**
     * A block entity's own aura store.
     */
    AURA_BANK,
    /**
     * A server script callback.
     */
    SCRIPT
}
