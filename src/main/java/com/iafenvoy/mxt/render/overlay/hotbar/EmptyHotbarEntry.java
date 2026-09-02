package com.iafenvoy.mxt.render.overlay.hotbar;

import net.minecraft.network.chat.Component;

/**
 * Empty slot used to preserve holes in a saved nine-slot layout.
 */
public final class EmptyHotbarEntry implements HotbarEntry {
    public static final EmptyHotbarEntry INSTANCE = new EmptyHotbarEntry();

    private EmptyHotbarEntry() {
    }

    @Override
    public Component name() {
        return Component.empty();
    }

    @Override
    public int accentColor() {
        return 0xFF303747;
    }
}
