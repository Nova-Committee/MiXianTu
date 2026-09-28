package com.iafenvoy.mxt.screen.multiblock;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.NonNull;

/**
 * A screen of its own rather than a page inside another one, so the scene can own the whole frame. The title comes
 * from the structure, which is why nothing here has a translation key of its own.
 */
public final class MultiblockStructureScreen extends Screen {
    private static final int BACKGROUND_COLOR = 0xF00C1014;
    private final MultiblockStructure structure;
    private final MultiblockStructureView view = new MultiblockStructureView();

    private MultiblockStructureScreen(MultiblockStructure structure) {
        super(structure.title());
        this.structure = structure;
        this.view.open();
    }

    public static void open(MultiblockStructure structure) {
        Minecraft.getInstance().setScreen(new MultiblockStructureScreen(structure));
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, this.width, this.height, BACKGROUND_COLOR);
        this.view.render(graphics, this.font, mouseX, mouseY, 0, 0, this.width, this.height, this.structure);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;
        return switch (this.view.mouseClicked(this.font, this.structure, event.x(), event.y(),
                0, 0, this.width, this.height)) {
            case BACK -> {
                this.onClose();
                yield true;
            }
            case HANDLED -> true;
            case NONE -> false;
        };
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        return this.view.mouseReleased(event.button());
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double deltaX, double deltaY) {
        return this.view.mouseDragged(deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Any scroll over the scene counts as a drag: the wheel has no zoom of its own here.
        return scrollY != 0.0D && this.view.mouseScrolled(mouseX, mouseY, 0, 0, this.width, this.height);
    }

    // Arrow keys are the buttons on the bottom bar; the space bar is the play/pause button.
    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_LEFT) return this.view.step(-1, this.structure);
        if (key == InputConstants.KEY_RIGHT) return this.view.step(1, this.structure);
        if (key == InputConstants.KEY_SPACE) return this.view.toggle(this.structure);
        return super.keyPressed(event);
    }

    // No background of its own: the view fills the frame it was given, and the default layer would blur behind it.
    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}
