package com.iafenvoy.mxt.compat.ponder;

import net.createmod.ponder.api.client.scene.PonderScene;
import net.createmod.ponder.impl.client.gui.PonderUI;

// Ponder 1.0.52 passes GUI coordinates to its pixel-based scene transform when identifying blocks.
// Remove this bridge once upstream uses the same coordinate scale for rendering and picking.
public final class PonderPickingCompatibility {
    private PonderPickingCompatibility() {
    }

    public static void update(PonderUI screen) {
        PonderScene scene = screen.getActiveScene();
        withGuiCoordinates(scene, () -> screen.updateIdentifiedItem(scene));
    }

    // Only our single-scene preview uses this scope; its slide offset is always zero.
    public static void withGuiCoordinates(PonderScene scene, Runnable picking) {
        PonderScene.SceneTransform transform = scene.getTransform();
        int originalGuiScale = transform.guiScale();
        int originalWidth = transform.width() / originalGuiScale;
        int originalHeight = transform.height() / originalGuiScale;
        try {
            transform.updateScreenParams(originalWidth, originalHeight, 0, 1);
            picking.run();
        } finally {
            transform.updateScreenParams(originalWidth, originalHeight, 0, originalGuiScale);
        }
    }
}
