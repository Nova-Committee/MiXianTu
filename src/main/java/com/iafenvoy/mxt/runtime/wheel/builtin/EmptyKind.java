package com.iafenvoy.mxt.runtime.wheel.builtin;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.api.WheelEntryKind;
import com.iafenvoy.mxt.api.WheelSource;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * No entry at all. A saved layout is twelve cells, so this is a kind like any other rather than a null.
 */
public final class EmptyKind implements WheelEntryKind {
    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "empty");
    }

    @Override
    public Component displayName() {
        return Component.translatable("wheel.mxt.kind.empty");
    }

    @Override
    public boolean holdsEntry() {
        return false;
    }

    @Override
    public boolean exists(RegistryAccess access, Identifier id) {
        return false;
    }

    @Override
    public boolean trigger(ServerPlayer player, WheelSource source, Identifier id) {
        return false;
    }
}
