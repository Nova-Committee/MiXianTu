package com.iafenvoy.mxt.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * The vanilla in-game backdrop every page host draws before submitting its page: the flat semi-transparent grey
 * the vanilla container screens put over the world. It is called directly rather than through
 * {@code super.extractBackground}, because the hosts that extend plain {@link Screen} answer false to
 * {@code isInGameUi()} and the super call would take the blurred menu-background branch instead - which blurs
 * whatever is already on screen, the HUD included. A container host answers true there, but one spelling for all
 * of them is easier to keep honest.
 */
public final class AuiBackdrop {
    private AuiBackdrop() {
    }

    public static void extract(Screen screen, GuiGraphicsExtractor graphics) {
        screen.extractTransparentBackground(graphics);
    }
}
