package com.iafenvoy.mxt.screen.overlay.hotbar;

import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.render.IconRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * One selectable entry rendered by the shared client hotbar.
 */
public interface HotbarEntry {
    int SLOT_SIZE = 22;
    int SLOT_GAP = 2;

    Component name();

    /**
     * Stable option ID for configurable hotbar layouts; null means not selectable there.
     */
    default Identifier id() {
        return null;
    }

    default Optional<IconReference> icon() {
        return Optional.empty();
    }

    default int accentColor() {
        return 0xFF7E8799;
    }

    /**
     * Remaining cooldown fraction, matching vanilla item cooldown rendering: {@code 0} is ready and
     * {@code 1} means the cooldown has just started.
     */
    default float cooldown(Player player) {
        return 0.0F;
    }

    /**
     * Prevents a cooldown-bound entry from becoming visually pressed or sending a use request.
     */
    default boolean canPress(Player player) {
        return this.cooldown(player) <= 0.0F;
    }

    default void onPress(Player player) {
    }

    default void onPressTick(Player player) {
    }

    default void onRelease(Player player) {
    }

    /**
     * Renders this entry: background, key label, icon/name and cooldown. Override to change the visual
     * without touching the shared overlay.
     */
    default void render(GuiGraphicsExtractor graphics, Font font, Player player,
                        int x, int y, int index, boolean selected) {
        int background = selected ? 0xEE26364A : 0xCC10131D;
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, background);
        graphics.fill(x, y, x + SLOT_SIZE, y + 1, this.accentColor());
        graphics.fill(x, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF303747);
        String key = Integer.toString(index + 1);
        graphics.text(font, key, x + (SLOT_SIZE - font.width(key)) / 2, y - 9, 0xFFFFFFFF, true);
        IconRenderer.renderOrName(graphics, font, this.icon(), this.name(), x, y, SLOT_SIZE);
        float cooldown = Math.max(0.0F, Math.min(1.0F, this.cooldown(player)));
        int height = (int) Math.ceil(cooldown * SLOT_SIZE);
        if (height > 0)
            graphics.fill(x, y + SLOT_SIZE - height, x + SLOT_SIZE, y + SLOT_SIZE, 0x99000000);
    }
}
