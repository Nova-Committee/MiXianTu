package com.iafenvoy.mxt.runtime.wheel.builtin;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.api.WheelEntryKind;
import com.iafenvoy.mxt.api.WheelSource;
import com.iafenvoy.mxt.data.creature.ContractBehaviors;
import com.iafenvoy.mxt.runtime.wheel.WheelService;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * An order for a bound creature. The id is a {@code ContractBehavior} id rather than a registry entry, and the page
 * it appears on is read from the taming bell's tuned beast.
 */
public final class BehaviorKind implements WheelEntryKind {
    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "behavior");
    }

    @Override
    public Component displayName() {
        return Component.translatable("wheel.mxt.kind.behavior");
    }

    @Override
    public boolean holdsEntry() {
        return true;
    }

    @Override
    public boolean exists(RegistryAccess access, Identifier id) {
        // Orders live in code, not in a registry, so the registry access is not what answers here.
        return ContractBehaviors.isKnown(id);
    }

    @Override
    public boolean trigger(ServerPlayer player, WheelSource source, Identifier id) {
        return WheelService.order(player, id);
    }
}
