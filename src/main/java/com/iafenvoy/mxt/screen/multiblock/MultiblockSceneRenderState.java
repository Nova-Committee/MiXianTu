package com.iafenvoy.mxt.screen.multiblock;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One frame of the scene: the camera, the area to draw into, and the cells already revealed. {@code state} is null
 * when a cell is drawn with its item model, and {@code index} seeds that model and names the cell.
 * {@code hovered} is the index of the cell under the mouse, or -1 for none.
 */
record MultiblockSceneRenderState(List<SceneBlock> blocks, MultiblockSceneCamera camera,
                                  int x0, int y0, int x1, int y1, int hovered,
                                  @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
        implements PictureInPictureRenderState {
    MultiblockSceneRenderState(List<SceneBlock> blocks, MultiblockSceneCamera camera,
                               int x0, int y0, int x1, int y1, int hovered, @Nullable ScreenRectangle scissorArea) {
        this(List.copyOf(blocks), camera, x0, y0, x1, y1, hovered, scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }

    @Override
    public float scale() {
        return this.camera.scale();
    }

    @Nullable
    SceneBlock hoveredBlock() {
        if (this.hovered < 0) return null;
        for (SceneBlock block : this.blocks) if (block.index() == this.hovered) return block;
        return null;
    }

    record SceneBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        float centreX() {
            return (this.minX + this.maxX + 1.0F) * 0.5F;
        }

        float centreY() {
            return (this.minY + this.maxY + 1.0F) * 0.5F;
        }

        float centreZ() {
            return (this.minZ + this.maxZ + 1.0F) * 0.5F;
        }
    }

    record SceneBlock(@Nullable BlockState state, ItemStack stack, int x, int y, int z, int index) {
        SceneBlock {
            stack = stack.copy();
        }
    }
}
