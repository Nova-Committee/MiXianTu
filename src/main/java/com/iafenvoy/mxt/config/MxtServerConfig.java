package com.iafenvoy.mxt.config;

import com.iafenvoy.jupiter.config.container.AutoInitConfigContainer;
import com.iafenvoy.jupiter.config.entry.EnumEntry;
import com.iafenvoy.jupiter.config.entry.BooleanEntry;
import com.iafenvoy.jupiter.config.entry.IntegerEntry;
import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Jupiter server configuration shared by MiXianTu gameplay integrations.
 */
public final class MxtServerConfig extends AutoInitConfigContainer {
    public static final MxtServerConfig INSTANCE = new MxtServerConfig();

    public final Curios curios = new Curios();
    public final Cultivation cultivation = new Cultivation();
    public final Aura aura = new Aura();

    private MxtServerConfig() {
        super(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "server"), "config.mxt.server", "./config/mxt-server.json");
    }

    public static BackMode backMode() {
        return INSTANCE.curios.backMode.getValue();
    }

    public static BeltMode beltMode() {
        return INSTANCE.curios.beltMode.getValue();
    }

    public static boolean forceRenderSlots() {
        return INSTANCE.curios.forceRenderSlots.getValue();
    }

    public static boolean forbidCultivationWithoutEligibleAura() {
        return INSTANCE.cultivation.forbidWithoutEligibleAura.getValue();
    }

    public static boolean allowCultivationMovement() {
        return INSTANCE.cultivation.allowMovement.getValue();
    }

    public static int blockAuraTickInterval() {
        return INSTANCE.aura.blockAuraTickInterval.getValue();
    }

    public static final class Curios extends AutoInitConfigCategoryBase {
        public final EnumEntry<BackMode> backMode = EnumEntry.builder("config.mxt.server.curios.back_mode", BackMode.MANUAL).nameProvider(value -> Component.translatable("config.mxt.server.mode." + value.name().toLowerCase())).build();
        public final EnumEntry<BeltMode> beltMode = EnumEntry.builder("config.mxt.server.curios.belt_mode", BeltMode.MANUAL).nameProvider(value -> Component.translatable("config.mxt.server.mode." + value.name().toLowerCase())).build();
        public final BooleanEntry forceRenderSlots = BooleanEntry.builder("config.mxt.server.curios.force_render_slots", false).build();

        private Curios() {
            super("curios", "config.mxt.server.curios");
        }
    }

    public static final class Cultivation extends AutoInitConfigCategoryBase {
        public final BooleanEntry forbidWithoutEligibleAura = BooleanEntry.builder(
                "config.mxt.server.cultivation.forbid_without_eligible_aura", false).build();
        public final BooleanEntry allowMovement = BooleanEntry.builder(
                "config.mxt.server.cultivation.allow_movement", false).build();

        private Cultivation() {
            super("cultivation", "config.mxt.server.cultivation");
        }
    }

    public static final class Aura extends AutoInitConfigCategoryBase {
        public final IntegerEntry blockAuraTickInterval = IntegerEntry.builder(
                "config.mxt.server.aura.block_aura_tick_interval", 10).range(1, 1200).build();

        private Aura() {
            super("aura", "config.mxt.server.aura");
        }
    }

    public enum BackMode {
        MANUAL, WEAPONS, ALL
    }

    public enum BeltMode {
        MANUAL, WEAPONS_ARTIFACTS, ALL
    }
}
