package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.network.payload.BackSlotSwapC2SPayload;
import com.iafenvoy.mxt.network.payload.CultivationToggleC2SPayload;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.*;
import java.util.function.Consumer;

/**
 * Registers key mappings that are not owned by a client overlay.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class MxtKeyMappings {
    private static final Category CATEGORY = new Category(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "general"));

    public static final KeyMappingHolder SWAP_BACK = new KeyMappingHolder("key.mxt.swap_back", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMappingHolder CULTIVATE = new KeyMappingHolder("key.mxt.cultivate", Type.KEYSYM, InputConstants.KEY_C, CATEGORY);

    static {
        SWAP_BACK.onStateChange(pressed -> {
            if (pressed) ClientPacketDistributor.sendToServer(BackSlotSwapC2SPayload.INSTANCE);
        });
        CULTIVATE.onStateChange(pressed -> {
            if (pressed) ClientPacketDistributor.sendToServer(CultivationToggleC2SPayload.INSTANCE);
        });
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        KeyMappingHolder.HOLDERS.stream().map(KeyMappingHolder::get).forEach(event::register);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        KeyMappingHolder.HOLDERS.forEach(KeyMappingHolder::tick);
    }

    public static final class KeyMappingHolder {
        private static final List<KeyMappingHolder> HOLDERS = new LinkedList<>();
        private final KeyMapping keyBinding;
        private final List<Consumer<Boolean>> callbacks = new ArrayList<>();
        private boolean pressed;

        public KeyMappingHolder(String name, InputConstants.Type type, int value, Category category) {
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
