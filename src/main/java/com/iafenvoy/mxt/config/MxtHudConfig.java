package com.iafenvoy.mxt.config;

import com.iafenvoy.jupiter.config.container.AutoInitConfigContainer;
import com.iafenvoy.jupiter.config.entry.MapStringEntry;
import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Where every movable HUD element sits, in a file of its own so a broken layout cannot take the mod's own
 * settings with it. Values are {@code x,y,visible}, the position a ratio of the window naming the top-left
 * corner; a missing key means "never placed". The versioned key is deliberate: an old one is never read.
 */
public final class MxtHudConfig extends AutoInitConfigContainer {
    public static final MxtHudConfig INSTANCE = new MxtHudConfig();

    public final Hud hud = new Hud();

    private MxtHudConfig() {
        super(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "hud_layout"), "config.mxt.hud", "./config/mxt/mxt-hud.json");
    }

    public static final class Hud extends AutoInitConfigCategoryBase {
        /**
         * The whole layout table: one entry, because the set of elements is not known here - a module that
         * registers an element only picks a layout key. Must stay public: a category's entries are collected by
         * reflection over public fields, so a private one is silently dropped and the file comes out empty.
         */
        public final MapStringEntry layout = hudLayoutEntry();

        private Hud() {
            super("hud", "config.mxt.hud");
        }

        public String stored(String layoutKey) {
            return this.layout.getValue().get(layoutKey);
        }

        // Written on every drag step: there is no close-time flush that a crash or a kill would spare.
        public void store(String layoutKey, String value) {
            Map<String, String> values = new HashMap<>(this.layout.getValue());
            values.put(layoutKey, value);
            this.layout.setValue(values);
            INSTANCE.save();
        }

        // The file must be saved too: a value left in it comes back on the next launch, which makes a reset
        // look like it had not been saved.
        public void remove(String layoutKey) {
            Map<String, String> values = new HashMap<>(this.layout.getValue());
            if (values.remove(layoutKey) == null) return;
            this.layout.setValue(values);
            INSTANCE.save();
        }

        private static MapStringEntry hudLayoutEntry() {
            // Held in a local first: the tooltip call makes the builder chain lose its type argument, and this
            // way the inferred type stays concrete instead of a raw type warning.
            MapStringEntry.Builder builder = MapStringEntry.builder("config.mxt.hud.layout_v2", Map.of());
            builder.key("layout_v2").tooltip("config.mxt.hud.layout.tooltip");
            return builder.build();
        }
    }
}
