package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.network.payload.BackSlotSwapC2SPayload;
import com.iafenvoy.mxt.network.payload.CultivationToggleC2SPayload;
import com.iafenvoy.mxt.screen.information.InformationPanelScreen;
import com.iafenvoy.mxt.screen.information.TechniquePanelScreen;
import com.iafenvoy.mxt.screen.overlay.hud.HudManager;
import com.iafenvoy.mxt.screen.wheel.content.WheelConfigurationScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

@EventBusSubscriber(Dist.CLIENT)
public final class MxtKeyMappings {
    private static final Category CATEGORY = new Category(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "general"));

    public static final KeyMappingHolder SWAP_BACK = new KeyMappingHolder("key.mxt.swap_back", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMappingHolder CULTIVATE = new KeyMappingHolder("key.mxt.cultivate", Type.KEYSYM, InputConstants.KEY_C, CATEGORY);
    public static final KeyMappingHolder INFORMATION_PANEL = new KeyMappingHolder("key.mxt.information_panel", Type.KEYSYM, InputConstants.KEY_Z, CATEGORY);
    /**
     * Left unbound on purpose: the technique panel is also reachable from the character information
     * panel, so a default key would compete with other mods for a slot nobody asked for.
     */
    public static final KeyMappingHolder TECHNIQUE_PANEL = new KeyMappingHolder("key.mxt.technique_panel", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    /**
     * Bound by default, unlike the technique panel: placing HUD elements is done by looking at the HUD, so
     * the key that opens the editor has to be reachable while playing. Right shift is what other client HUD
     * editors use for the same job, which makes it the key a player is most likely to try first.
     */
    public static final KeyMappingHolder HUD_LAYOUT = new KeyMappingHolder("key.mxt.hud_layout", Type.KEYSYM, InputConstants.KEY_RSHIFT, CATEGORY);
    /**
     * The wheel key, bound by default: choosing a sector is not something to go looking for a key for. It only
     * chooses - spending is {@link #WHEEL_USE} - and it has no state callback because {@code WheelMenuController}
     * polls the raw key (opening a screen releases every mapping); registering it still puts it in the controls
     * screen.
     */
    public static final KeyMappingHolder WHEEL = new KeyMappingHolder("key.mxt.wheel", Type.KEYSYM, InputConstants.KEY_R, CATEGORY);
    /**
     * The use key, bound by default: spends the sector the wheel key chose, with the wheel open or closed.
     * Like {@link #WHEEL} it has no state callback, since the controller polls both keys raw.
     */
    public static final KeyMappingHolder WHEEL_USE = new KeyMappingHolder("key.mxt.wheel_use", Type.KEYSYM, InputConstants.KEY_V, CATEGORY);
    /**
     * Unbound on purpose, like the technique panel: opening the editor is not a mid-fight action, and {@code
     * /wheel} does the same. Opening it touches no server - it reads the synced attachment and registries.
     */
    public static final KeyMappingHolder WHEEL_CONFIGURATION = new KeyMappingHolder("key.mxt.wheel_configuration", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

    static {
        SWAP_BACK.onStateChange(pressed -> {
            if (pressed) ClientPacketDistributor.sendToServer(BackSlotSwapC2SPayload.INSTANCE);
        });
        CULTIVATE.onStateChange(pressed -> {
            if (pressed) ClientPacketDistributor.sendToServer(CultivationToggleC2SPayload.INSTANCE);
        });
        INFORMATION_PANEL.onStateChange(pressed -> {
            if (pressed && Minecraft.getInstance().screen == null)
                Minecraft.getInstance().setScreen(new InformationPanelScreen());
        });
        TECHNIQUE_PANEL.onStateChange(pressed -> {
            if (pressed && Minecraft.getInstance().screen == null)
                Minecraft.getInstance().setScreen(new TechniquePanelScreen());
        });
        HUD_LAYOUT.onStateChange(pressed -> {
            if (pressed && Minecraft.getInstance().screen == null) HudManager.openEditor();
        });
        WHEEL_CONFIGURATION.onStateChange(pressed -> {
            if (pressed && Minecraft.getInstance().screen == null) WheelConfigurationScreen.open();
        });
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        KeyMappingHolder.HOLDERS.stream().map(KeyMappingHolder::get).forEach(event::register);
    }

    @SubscribeEvent
    public static void tick(Post event) {
        KeyMappingHolder.HOLDERS.forEach(KeyMappingHolder::tick);
    }

    public static final class KeyMappingHolder {
        private static final List<KeyMappingHolder> HOLDERS = new LinkedList<>();
        private final KeyMapping keyBinding;
        private final List<Consumer<Boolean>> callbacks = new ArrayList<>();
        private boolean pressed;

        public KeyMappingHolder(String name, Type type, int value, Category category) {
            this(new KeyMapping(name, type, value, category));
        }

        public KeyMappingHolder(KeyMapping keyBinding) {
            this(keyBinding, true);
        }

        private KeyMappingHolder(KeyMapping keyBinding, boolean register) {
            this.keyBinding = keyBinding;
            if (register) HOLDERS.add(this);
        }

        public static KeyMappingHolder standalone(KeyMapping keyBinding) {
            return new KeyMappingHolder(keyBinding, false);
        }

        public KeyMapping get() {
            return this.keyBinding;
        }

        public boolean isDown() {
            return this.keyBinding.isDown();
        }

        public boolean consumeClick() {
            return this.keyBinding.consumeClick();
        }

        public void onStateChange(Consumer<Boolean> callback) {
            this.callbacks.add(callback);
        }

        public void tick() {
            boolean current = this.keyBinding.isDown();
            if (current != this.pressed) {
                this.callbacks.forEach(callback -> callback.accept(current));
                this.pressed = current;
            }
        }
    }
}
