package com.iafenvoy.mxt.render.overlay.hotbar;

/**
 * Shared compact dimensions used by the ability and spirit-burst hotbars.
 * Entry rendering is polymorphic and lives in {@link HotbarEntry#render}.
 */
public final class HotbarOverlayRenderer {
    public static final int SLOT_SIZE = HotbarEntry.SLOT_SIZE;
    public static final int SLOT_GAP = HotbarEntry.SLOT_GAP;

    private HotbarOverlayRenderer() {
    }

    public static int width(int count) {
        return count <= 0 ? 0 : count * SLOT_SIZE + (count - 1) * SLOT_GAP;
    }

}
