package com.iafenvoy.mxt.render.overlay.hotbar;

import com.iafenvoy.mxt.data.HotbarIcon;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * One selectable entry rendered by the shared client hotbar.
 */
public interface HotbarEntry {
    int SLOT_SIZE = 22;
    int SLOT_GAP = 2;

    Component name();

    default Optional<HotbarIcon> icon() {
        return Optional.empty();
    }

    default int accentColor() {
        return 0xFF7E8799;
    }

    /**
     * Returns the remaining cooldown fraction, matching vanilla item cooldown
     * rendering semantics. A value of {@code 0} means ready and {@code 1}
     * means the cooldown has just started.
     */
    default float cooldown(Player player) {
        return 0.0F;
    }

    default void onPress(Player player) {
    }

    default void onPressTick(Player player) {
    }

    default void onRelease(Player player) {
    }

    /**
     * Renders this entry, including its background, key label, icon/name and
     * server-authoritative cooldown. Implementations may override this method
     * to provide a different visual without changing the shared overlay.
     */
    default void render(GuiGraphicsExtractor graphics, Font font, Player player,
                        int x, int y, int index, boolean selected) {
        int background = selected ? 0xEE26364A : 0xCC10131D;
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, background);
        graphics.fill(x, y, x + SLOT_SIZE, y + 1, this.accentColor());
        graphics.fill(x, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF303747);
        String key = Integer.toString(index + 1);
        graphics.text(font, key, x + (SLOT_SIZE - font.width(key)) / 2, y - 9, 0xFFFFFFFF, false);
        this.icon().ifPresentOrElse(icon -> icon.item().ifPresentOrElse(
                item -> graphics.item(item.create(), x + 3, y + 3),
                () -> icon.texture().ifPresent(texture -> graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                        x + 3, y + 3, 0.0F, 0.0F, 16, 16, 16, 16))
        ), () -> {
            String name = this.name().getString();
            if (name.length() > 3) name = name.substring(0, 3);
            graphics.text(font, name, x + (SLOT_SIZE - font.width(name)) / 2, y + 10, 0xFFE0E5EF, false);
        });
        float cooldown = Math.max(0.0F, Math.min(1.0F, this.cooldown(player)));
        int height = (int) Math.ceil(cooldown * SLOT_SIZE);
        if (height > 0)
            graphics.fill(x, y + SLOT_SIZE - height, x + SLOT_SIZE, y + SLOT_SIZE, 0x99000000);
    }
}
