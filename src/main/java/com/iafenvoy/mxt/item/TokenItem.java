package com.iafenvoy.mxt.item;

import net.minecraft.world.item.Item;

/**
 * Appearance-specific token shell; all identity and permission state uses the shared token component.
 */
public final class TokenItem extends Item {
    public TokenItem(Properties properties) {
        super(properties);
    }
}
