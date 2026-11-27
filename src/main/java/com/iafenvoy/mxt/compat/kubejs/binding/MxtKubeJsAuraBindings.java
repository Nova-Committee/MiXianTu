package com.iafenvoy.mxt.compat.kubejs.binding;

import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.runtime.world.AuraClientState;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * World aura operations exposed as {@code MxtAura}.
 */
public final class MxtKubeJsAuraBindings {
    private final boolean clientScript;

    public MxtKubeJsAuraBindings(boolean clientScript) {
        this.clientScript = clientScript;
    }

    @Info("Gets the resolved aura at a server world position, or null on the client.")
    public @Nullable AuraResult get(Level level, BlockPos position) {
        return MxtKubeJsApi.aura(level, position);
    }

    @Info("Gets the latest server aura sample for the local client player, or null if unavailable.")
    public @Nullable AuraClientState.Snapshot getLocal() {
        return this.clientScript ? AuraClientState.latest().orElse(null) : null;
    }

    @Info("Adds a persistent server-side rectangular aura area and returns its generated ID.")
    public String addBox(Level level, String zone, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int priority) {
        return MxtKubeJsApi.addAuraBox(level, id(zone), minX, minY, minZ, maxX, maxY, maxZ, priority);
    }

    @Info("Removes a persistent aura area by its generated ID.")
    public boolean remove(Level level, String area) {
        return MxtKubeJsApi.removeAuraArea(level, area);
    }

    private static Identifier id(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid MXT identifier: " + raw);
        return id;
    }
}
