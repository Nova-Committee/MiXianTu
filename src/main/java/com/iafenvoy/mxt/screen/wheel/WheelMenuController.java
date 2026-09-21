package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.config.MxtClientConfig;
import com.iafenvoy.mxt.registry.MxtKeyMappings;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Opens and closes the wheel and spends what it holds: the wheel key only chooses, the use key spends the
 * pointed sector (or the remembered one while the wheel is down). An empty wheel does not open.
 *
 * <p>Both keys are polled raw ({@code InputConstants}/GLFW), because {@code setScreen} releases every mapping
 * and {@code grabMouse}'s {@code setAll()} would re-press the use key and double-fire; edges are once a tick.</p>
 */
@EventBusSubscriber(Dist.CLIENT)
public final class WheelMenuController {
    private static @Nullable WheelMenuScreen open;
    private static boolean lastDown;
    private static boolean lastUseDown;

    private WheelMenuController() {
    }

    static void usePointed(WheelSelection.Method method) {
        WheelMenuScreen screen = open;
        if (screen == null) return;
        // Null for an empty sector: nothing is spent and the wheel stays up.
        WheelSelection selection = screen.selection(method);
        if (selection != null) selection.entry().onSelected(selection);
    }

    /** Called when something else takes the screen down; the controller must not touch the new screen. */
    static void screenRemoved(WheelMenuScreen screen) {
        if (open == screen) open = null;
    }

    private static void open(Minecraft minecraft) {
        if (minecraft.screen != null || WheelMenuContent.isEmpty(minecraft.player)) return;
        WheelMenuScreen screen = new WheelMenuScreen();
        open = screen;
        minecraft.setScreen(screen);
    }

    private static void close() {
        if (open == null) return;
        // Forgotten before the screen is taken down, so the removal callback is a no-op.
        open = null;
        Minecraft.getInstance().setScreen(null);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        // Closed from the outside (escape, another screen): drop the reference without closing anything.
        if (open != null && minecraft.screen != open) open = null;
        // No world to show the wheel over - a disconnect while it was open.
        if (open != null && minecraft.level == null) {
            close();
            return;
        }
        // Recorded before this tick's edges: letting go must keep whatever the pointer was last on.
        if (open != null) WheelSelectionState.select(open.pointedSector());
        // Before this tick's edges act on it, and before the HUD cell draws it.
        WheelSelectionState.refresh(minecraft.player);

        // The use key first, so a tick that presses one key and releases the other spends what was aimed at.
        boolean useDown = keyDown(minecraft, MxtKeyMappings.WHEEL_USE);
        if (useDown != lastUseDown) {
            lastUseDown = useDown;
            if (useDown) use(minecraft);
        }
        boolean down = keyDown(minecraft, MxtKeyMappings.WHEEL);
        if (down == lastDown) return;
        lastDown = down;
        if (down) {
            if (open == null) open(minecraft);
                // The second press in toggle mode takes the wheel down, as letting go does in hold mode.
            else if (!hold()) close();
        } else if (open != null && hold()) {
            close();
        }
    }

    /** The use key went down: spend the pointed sector if the wheel is up, the remembered one if it is not. */
    private static void use(Minecraft minecraft) {
        if (open != null) {
            usePointed(WheelSelection.Method.KEY);
            return;
        }
        // Another screen owns the key: typing a "v" in chat must not cast anything.
        if (minecraft.screen != null) return;
        WheelSelection selection = remembered();
        if (selection != null) selection.entry().onSelected(selection);
    }

    /**
     * The remembered sector as a selection, or {@code null} when there is nothing to spend. Read from the
     * selection this tick resolved, so the use key and the HUD cell that announces it agree by construction.
     */
    private static @Nullable WheelSelection remembered() {
        WheelMenuEntry entry = WheelSelectionState.selected();
        return entry == null ? null
                : new WheelSelection(WheelSelectionState.sector(), entry, WheelSelection.Method.KEY);
    }

    /** A bound key's state straight from the input device; an unbound key is never down. */
    private static boolean keyDown(Minecraft minecraft, MxtKeyMappings.KeyMappingHolder holder) {
        InputConstants.Key bound = holder.get().getKey();
        int value = bound.getValue();
        if (value == InputConstants.UNKNOWN.getValue()) return false;
        if (bound.getType() == InputConstants.Type.MOUSE)
            return GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), value) == GLFW.GLFW_PRESS;
        return InputConstants.isKeyDown(minecraft.getWindow(), value);
    }

    private static boolean hold() {
        return MxtClientConfig.INSTANCE.wheel.mode.getValue() == MxtClientConfig.WheelMode.HOLD;
    }
}
