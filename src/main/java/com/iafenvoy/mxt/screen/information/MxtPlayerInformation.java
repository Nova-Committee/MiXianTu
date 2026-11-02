package com.iafenvoy.mxt.screen.information;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.saturn.api.playerview.PlayerInformationRowProvider;
import com.iafenvoy.saturn.view.PlayerInformation;
import com.iafenvoy.saturn.view.model.InfomationGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Registers MiXianTu's information rows with the shared panel. The vanilla-facing rows are Saturn's own, so only the
 * definition-driven ones are added here, under a group of their own.
 */
public final class MxtPlayerInformation {
    public static final Identifier GROUP_ID = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "cultivation");
    public static final String LABEL_PREFIX = "info.mxt.";
    public static final String REALM = "realm";
    public static final String LIFESPAN = "lifespan";
    public static final String PROGRESS = "cultivation_progress";
    public static final String SPIRIT_ROOTS = "spirit_roots";
    public static final String PHYSIQUES = "physiques";
    public static final String TECHNIQUES = "techniques";
    public static final String CURSES = "curses";
    private static InfomationGroup group;

    private MxtPlayerInformation() {
    }

    public static void register() {
        MxtPlayerView.register();
        group = PlayerInformation.registerGroup(GROUP_ID, Component.translatable("info.mxt.cultivation"), 100);
        row(group, REALM, 0);
        row(group, LIFESPAN, 10);
        row(group, PROGRESS, 20);
        row(group, SPIRIT_ROOTS, 30);
        row(group, PHYSIQUES, 40);
        row(group, TECHNIQUES, 50);
        row(group, CURSES, 60);
    }

    // Empty until register() ran, which every reader may assume: registration happens at mod setup.
    public static InfomationGroup group() {
        if (group == null) throw new IllegalStateException("MiXianTu information rows are not registered yet");
        return group;
    }

    private static void row(InfomationGroup group, String path, int order) {
        PlayerInformationRowProvider provider = (view, _) -> MxtPlayerView.row(view, path, LABEL_PREFIX + path);
        PlayerInformation.register(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path), group, order, provider);
    }
}
