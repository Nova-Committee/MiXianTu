package com.iafenvoy.mxt.config;

import com.iafenvoy.jupiter.config.container.AutoInitConfigContainer;
import com.iafenvoy.jupiter.config.entry.BooleanEntry;
import com.iafenvoy.jupiter.config.entry.DoubleEntry;
import com.iafenvoy.jupiter.config.entry.EnumEntry;
import com.iafenvoy.jupiter.config.entry.IntegerEntry;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueProgress.Mode;
import com.iafenvoy.mxt.screen.aui.AuiTheme;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Client-only controls for the mod's own overlays, one tab per feature; an entry's name stays short and its
 * tooltip carries the explanation, which also lives in both lang files ({@code config.mxt.client.*}), so
 * adding or renaming one means editing {@code zh_cn.json} and {@code en_us.json} too.
 */
public final class MxtClientConfig extends AutoInitConfigContainer {
    public static final MxtClientConfig INSTANCE = new MxtClientConfig();

    public final Appearance appearance = new Appearance();
    public final Information information = new Information();
    public final Techniques techniques = new Techniques();
    public final Rifts rifts = new Rifts();
    public final Tooltips tooltips = new Tooltips();
    public final Flames flames = new Flames();
    public final Effects effects = new Effects();

    private MxtClientConfig() {
        super(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "client"), "config.mxt.client", "./config/mxt/mxt-client.json");
        this.dataFixer.registerKeyRule("^config\\.mxt\\.client\\.[a-z_]+\\.([a-z_]+)$", key -> key.substring(key.lastIndexOf('.') + 1));
    }

    public static final class Appearance extends AutoInitConfigCategoryBase {
        public final EnumEntry<AuiTheme> theme = EnumEntry.builder("config.mxt.client.appearance.theme", AuiTheme.PARCHMENT)
                .key("theme")
                .tooltip("config.mxt.client.appearance.theme.tooltip")
                .nameProvider(value -> Component.translatable("config.mxt.client.appearance.theme." + value.name().toLowerCase(Locale.ROOT)))
                .build();

        private Appearance() {
            super("appearance", "config.mxt.client.appearance");
        }
    }

    public static final class Information extends AutoInitConfigCategoryBase {
        // Ticks between two rebuilds of the information panel.
        public final IntegerEntry refreshInterval = IntegerEntry.builder("config.mxt.client.information.refresh_interval", 20)
                .key("refresh_interval")
                .tooltip("config.mxt.client.information.refresh_interval.tooltip")
                .range(1, 1200).build();

        private Information() {
            super("information", "config.mxt.client.information");
        }
    }

    public static final class Techniques extends AutoInitConfigCategoryBase {
        public final EnumEntry<Mode> progressMode = EnumEntry.builder("config.mxt.client.techniques.progress_mode", Mode.ABSOLUTE)
                .key("progress_mode")
                .tooltip("config.mxt.client.techniques.progress_mode.tooltip")
                .nameProvider(value -> Component.translatable("config.mxt.client.techniques.progress_mode." + value.name().toLowerCase()))
                .build();

        private Techniques() {
            super("techniques", "config.mxt.client.techniques");
        }
    }

    public static final class Rifts extends AutoInitConfigCategoryBase {
        // Basic rendering keeps the same opaque geometry without the animated material.
        public final BooleanEntry shaders = BooleanEntry.builder("config.mxt.client.rifts.shaders", true)
                .key("shaders")
                .tooltip("config.mxt.client.rifts.shaders.tooltip")
                .build();

        private Rifts() {
            super("rifts", "config.mxt.client.rifts");
        }
    }


    public static final class Flames extends AutoInitConfigCategoryBase {
        public final BooleanEntry enabled = BooleanEntry.builder("config.mxt.client.flames.enabled", true)
                .key("enabled").tooltip("config.mxt.client.flames.enabled.tooltip").build();
        public final DoubleEntry intensity = number("intensity", 1, 0, 4);
        public final DoubleEntry windCoefficient = number("wind_coefficient", 1, 0, 8);
        public final DoubleEntry windX = number("wind_x", 0.3, -12, 12);
        public final DoubleEntry windY = number("wind_y", 0, -12, 12);
        public final DoubleEntry windZ = number("wind_z", 0.1, -12, 12);
        public final DoubleEntry nearDistance = number("near_distance", 16, 1, 256);
        public final DoubleEntry middleDistance = number("middle_distance", 48, 1, 256);
        public final DoubleEntry farDistance = number("far_distance", 128, 1, 256);
        public final IntegerEntry nearParticles = particles("near_particles", 200);
        public final IntegerEntry middleParticles = particles("middle_particles", 50);
        public final IntegerEntry farParticles = particles("far_particles", 10);
        public final DoubleEntry surfaceOpacity = number("surface_opacity", 0.18, 0, 1);
        public final DoubleEntry softDistance = number("soft_distance", 0.12, 0, 1);

        private Flames() {
            super("flames", "config.mxt.client.flames");
        }

        private static DoubleEntry number(String name, double value, double min, double max) {
            String key = "config.mxt.client.flames." + name;
            return DoubleEntry.builder(key, value).key(name).tooltip(key + ".tooltip").range(min, max).build();
        }

        private static IntegerEntry particles(String name, int count) {
            String key = "config.mxt.client.flames." + name;
            return IntegerEntry.builder(key, count).key(name).tooltip(key + ".tooltip").range(1, 256).build();
        }
    }

    public static final class Effects extends AutoInitConfigCategoryBase {
        public final BooleanEntry sceneDepth = BooleanEntry.builder("config.mxt.client.effects.scene_depth", false)
                .key("scene_depth").tooltip("config.mxt.client.effects.scene_depth.tooltip").build();

        private Effects() {
            super("effects", "config.mxt.client.effects");
        }
    }


    /**
     * Item tooltip switches. The name line is the one line no appender can reach, so whether a quality is
     * allowed to tint it is a player's choice rather than part of what the quality draws.
     */
    public static final class Tooltips extends AutoInitConfigCategoryBase {
        public final BooleanEntry tintItemName = BooleanEntry.builder("config.mxt.client.tooltips.tint_item_name", true)
                .key("tint_item_name")
                .tooltip("config.mxt.client.tooltips.tint_item_name.tooltip")
                .build();

        private Tooltips() {
            super("tooltips", "config.mxt.client.tooltips");
        }
    }
}
