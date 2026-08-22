package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.network.payload.BackSlotSwapC2SPayload;
import com.iafenvoy.mxt.network.payload.SpiritBurstC2SPayload;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.resources.Identifier;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

@EventBusSubscriber(Dist.CLIENT)
public final class MxtKeyMappings {
    private static final Category CATEGORY = new Category(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "general"));

    private static final KeyMappingHolder SWAP_BACK = new KeyMappingHolder(new KeyMapping("key.mxt.swap_back", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
    private static final KeyMappingHolder SPIRIT_BURST = new KeyMappingHolder(new KeyMapping("key.mxt.spirit_burst", Type.KEYSYM, InputConstants.KEY_V, CATEGORY));

    static {
        SWAP_BACK.registerPressCallback(b -> {
            if (b) ClientPacketDistributor.sendToServer(BackSlotSwapC2SPayload.INSTANCE);
        });
        SPIRIT_BURST.registerPressCallback(firing -> ClientPacketDistributor.sendToServer(new SpiritBurstC2SPayload(firing)));
    }

    @SubscribeEvent
    public static void onClientTick(Post event) {
        KeyMappingHolder.HOLDERS.forEach(KeyMappingHolder::tick);
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        KeyMappingHolder.HOLDERS.stream().map(KeyMappingHolder::getKeyMapping).forEach(event::register);
    }

    public static class KeyMappingHolder {
        private static final List<KeyMappingHolder> HOLDERS = new LinkedList<>();
        private final Supplier<KeyMapping> keyMapping;
        private final List<BooleanConsumer> callback = new LinkedList<>();
        private boolean pressed;

        public KeyMappingHolder(KeyMapping keyMapping) {
            this(() -> keyMapping);
        }

        public KeyMappingHolder(Supplier<KeyMapping> keyMapping) {
            this.keyMapping = keyMapping;
            HOLDERS.add(this);
        }

        public KeyMapping getKeyMapping() {
            return this.keyMapping.get();
        }

        public boolean isPressed() {
            return this.pressed;
        }

        public void registerPressCallback(BooleanConsumer consumer) {
            this.callback.add(consumer);
        }

        public void tick() {
            KeyMapping k = this.keyMapping.get();
            if (k == null) return;
            boolean curr = k.isDown();
            if (!this.pressed && curr) this.callback.forEach(x -> x.accept(true));
            if (this.pressed && !curr) this.callback.forEach(x -> x.accept(false));
            this.pressed = curr;
        }
    }
}
