package com.iafenvoy.mxt.runtime.wheel.builtin;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.api.WheelEntryKind;
import com.iafenvoy.mxt.api.WheelSource;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.wheel.WheelService;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * An ability, and the only kind with a state a directed request can name.
 */
public final class AbilityKind implements WheelEntryKind {
    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "ability");
    }

    @Override
    public Component displayName() {
        return Component.translatable("wheel.mxt.kind.ability");
    }

    @Override
    public boolean holdsEntry() {
        return true;
    }

    @Override
    public boolean exists(RegistryAccess access, Identifier id) {
        return MxtDatapackRegistries.holder(access, MxtResourceKeys.ABILITY, id).isPresent();
    }

    @Override
    public boolean trigger(ServerPlayer player, WheelSource source, Identifier id) {
        return WheelService.press(player, source, id);
    }

    @Override
    public boolean directed(ServerPlayer player, WheelSource source, Identifier id, boolean wanted) {
        return WheelService.directed(player, source, id, wanted);
    }
}
