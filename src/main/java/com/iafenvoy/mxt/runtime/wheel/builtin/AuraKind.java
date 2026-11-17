package com.iafenvoy.mxt.runtime.wheel.builtin;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.wheel.MxtWheelActions;
import com.iafenvoy.saturn.api.wheel.WheelEntryKind;
import com.iafenvoy.saturn.api.wheel.WheelSource;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * A spirit-power burst from an aura's own value.
 */
public final class AuraKind implements WheelEntryKind {
    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "aura");
    }

    @Override
    public Component displayName() {
        return Component.translatable("wheel.mxt.kind.aura");
    }

    @Override
    public boolean holdsEntry() {
        return true;
    }

    @Override
    public boolean exists(RegistryAccess access, Identifier id) {
        return MxtDatapackRegistries.holder(access, MxtResourceKeys.AURA, id).isPresent();
    }

    @Override
    public boolean trigger(ServerPlayer player, WheelSource source, Identifier id) {
        return MxtWheelActions.burst(player, id);
    }
}
