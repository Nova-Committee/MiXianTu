package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.network.payload.BackSlotSwapC2SPayload;
import com.iafenvoy.mxt.network.payload.CultivationToggleC2SPayload;
import com.iafenvoy.mxt.screen.information.InformationPanelScreen;
import com.iafenvoy.mxt.screen.information.TechniquePanelScreen;
import com.iafenvoy.mxt.screen.hud.HudManager;
import com.iafenvoy.mxt.screen.wheel.WheelGeometry;
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
    private static final Category WHEEL_SLOT_CATEGORY = new Category(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "wheel_slot"));

    public static final KeyMappingHolder SWAP_BACK = new KeyMappingHolder("key.mxt.swap_back", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMappingHolder CULTIVATE = new KeyMappingHolder("key.mxt.cultivate", Type.KEYSYM, InputConstants.KEY_C, CATEGORY);
    public static final KeyMappingHolder INFORMATION_PANEL = new KeyMappingHolder("key.mxt.information_panel", Type.KEYSYM, InputConstants.KEY_Z, CATEGORY);
    public static final KeyMappingHolder TECHNIQUE_PANEL = new KeyMappingHolder("key.mxt.technique_panel", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMappingHolder HUD_LAYOUT = new KeyMappingHolder("key.mxt.hud_layout", Type.KEYSYM, InputConstants.KEY_RSHIFT, CATEGORY);
    public static final KeyMappingHolder WHEEL = new KeyMappingHolder("key.mxt.wheel", Type.KEYSYM, InputConstants.KEY_R, CATEGORY);
    public static final KeyMappingHolder WHEEL_USE = new KeyMappingHolder("key.mxt.wheel_use", Type.KEYSYM, InputConstants.KEY_V, CATEGORY);
    public static final KeyMappingHolder WHEEL_CONFIGURATION = new KeyMappingHolder("key.mxt.wheel_configuration", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    /** The numpad's own left/right keys, so switching wheels never competes with the arrow keys. */
    public static final KeyMappingHolder WHEEL_PREVIOUS = new KeyMappingHolder("key.mxt.wheel_previous", Type.KEYSYM, InputConstants.KEY_LEFT, CATEGORY);
    public static final KeyMappingHolder WHEEL_NEXT = new KeyMappingHolder("key.mxt.wheel_next", Type.KEYSYM, InputConstants.KEY_RIGHT, CATEGORY);

    public static final List<KeyMappingHolder> WHEEL_SLOTS = new ArrayList<>(WheelGeometry.SECTORS);

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

        // The sector is the key's own sort order inside the category: KeyMapping#compareTo compares the order
        // before the translated name, so the twelve are listed 1..12 in every language without padding them.
        for (int sector = 0; sector < WheelGeometry.SECTORS; sector++)
            WHEEL_SLOTS.add(new KeyMappingHolder("key.mxt.wheel_slot." + (sector + 1), Type.KEYSYM, InputConstants.UNKNOWN.getValue(), WHEEL_SLOT_CATEGORY, sector));
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.registerCategory(WHEEL_SLOT_CATEGORY);
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

        /**
         * Same, plus the position this key takes inside its category. Without one, a category is sorted by the
         * translated name ({@code KeyMapping#compareTo}), which puts "Slot 10" before "Slot 2".
         */
        public KeyMappingHolder(String name, Type type, int value, Category category, int order) {
            this(new KeyMapping(name, type, value, category, order));
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
