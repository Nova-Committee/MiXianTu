package com.iafenvoy.mxt.config;

import com.iafenvoy.jupiter.config.container.AutoInitConfigContainer;
import com.iafenvoy.jupiter.config.entry.BooleanEntry;
import com.iafenvoy.jupiter.config.entry.EnumEntry;
import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Client-only controls for the shared ability and spirit-burst hotbars.
 */
public final class MxtClientConfig extends AutoInitConfigContainer {
    public static final MxtClientConfig INSTANCE = new MxtClientConfig();

    public final Hotbar hotbar = new Hotbar();
    public final ResourceBars resourceBars = new ResourceBars();

    private MxtClientConfig() {
        super(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "client"), "config.mxt.client", "./config/mxt-client.json");
    }

    public static boolean allowVanillaHotbarSelection() {
        return INSTANCE.hotbar.allowVanillaHotbarSelection.getValue();
    }

    public static HotbarMode hotbarMode() {
        return INSTANCE.hotbar.mode.getValue();
    }

    public static ResourceBarIconLayout resourceBarIconLayout() {
        return INSTANCE.resourceBars.iconLayout.getValue();
    }

    public static boolean showResourceBarNames() {
        return INSTANCE.resourceBars.showNames.getValue();
    }

    public static final class Hotbar extends AutoInitConfigCategoryBase {
        public final BooleanEntry allowVanillaHotbarSelection = BooleanEntry.builder("config.mxt.client.hotbar.allow_vanilla_selection", false).build();
        public final EnumEntry<HotbarMode> mode = EnumEntry.builder("config.mxt.client.hotbar.mode", HotbarMode.HOLD).nameProvider(value -> Component.translatable("config.mxt.client.hotbar.mode." + value.name().toLowerCase())).build();

        private Hotbar() {
            super("hotbar", "config.mxt.client.hotbar");
        }
    }

    public enum HotbarMode {
        HOLD, TOGGLE
    }

    public static final class ResourceBars extends AutoInitConfigCategoryBase {
        public final BooleanEntry showNames = BooleanEntry.builder("config.mxt.client.resource_bars.show_names", false).build();
        public final EnumEntry<ResourceBarIconLayout> iconLayout = EnumEntry.builder("config.mxt.client.resource_bars.icon_layout", ResourceBarIconLayout.SIDES)
                .nameProvider(value -> Component.translatable("config.mxt.client.resource_bars.icon_layout." + value.name().toLowerCase()))
                .build();

        private ResourceBars() {
            super("resource_bars", "config.mxt.client.resource_bars");
        }
    }

    public enum ResourceBarIconLayout {
        SIDES, CENTER
    }
}
