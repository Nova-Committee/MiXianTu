package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.realm.RealmInstance;
import com.iafenvoy.mxt.data.realm.RealmInstance.Border;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;

/**
 * Writes a realm definition's border onto an instance dimension. The center is read as {@code [x, z]}: a
 * {@code Vec2} stores the second component in its {@code y} field.
 */
public final class RealmBorderService {
    private RealmBorderService() {
    }

    // forceDefault: an omitted border still applies the vanilla default, because a runtime dimension built on
    // derived level data would inherit the overworld border. An existing realm passes false; its border is not ours.
    public static void apply(ServerLevel level, RealmInstance definition, boolean forceDefault) {
        if (!forceDefault && definition.border().isEmpty()) return;
        Border settings = definition.effectiveBorder();
        WorldBorder border = level.getWorldBorder();
        border.setCenter(settings.center().x, settings.center().y);
        border.setSize(settings.size());
        border.setWarningBlocks(settings.warningBlocks());
        border.setWarningTime(settings.warningTime());
        border.setDamagePerBlock(settings.damagePerBlock());
        border.setSafeZone(settings.safeZone());
        for (ServerPlayer player : level.players())
            player.connection.send(new ClientboundInitializeBorderPacket(border));
    }
}
