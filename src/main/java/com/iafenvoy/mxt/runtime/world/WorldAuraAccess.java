package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.api.AuraAccess;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.spirit.AuraTransfers;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Map;

// A view of existing section stock; neither simulation nor a missing target loads or initializes terrain.
public record WorldAuraAccess(Level level, BlockPos pos) implements AuraAccess {
    public WorldAuraAccess {
        pos = pos.immutable();
    }

    private Map<Holder<Aura>, AuraPool> pools() {
        LevelChunk chunk = AuraService.loadedChunk(this.level, this.pos);
        if (chunk == null) return Map.of();
        return chunk.getExistingData(MxtAttachments.AURA_CHUNK)
                .map(attachment -> attachment.pools(SectionPos.blockToSectionCoord(this.pos.getY())))
                .orElse(Map.of());
    }

    @Override
    public Object2DoubleMap<Holder<Aura>> getCapacity(@Nullable LivingEntity entity, @NonNull Provider registries) {
        Object2DoubleMap<Holder<Aura>> capacities = new Object2DoubleOpenHashMap<>();
        this.pools().forEach((aura, pool) -> capacities.put(aura, Math.min(Double.MAX_VALUE, pool.maximum())));
        return capacities;
    }

    @Override
    public double insert(@Nullable LivingEntity entity, @NonNull Provider registries, Holder<Aura> aura, double amount,
                         boolean exact, boolean simulate) {
        AuraAccess.requireNonNegative(amount);
        AuraPool pool = this.pools().get(aura);
        if (pool == null) return amount;
        AuraTransfers.Transfer transfer = AuraTransfers.insert(pool.amount(), Math.min(Double.MAX_VALUE, pool.maximum()), amount, exact);
        if (!simulate && transfer.remainder() != amount) pool.change(transfer.stored() - pool.amount());
        return transfer.remainder();
    }

    @Override
    public double extract(@Nullable LivingEntity entity, @NonNull Provider registries, Holder<Aura> aura, double amount,
                          boolean exact, boolean simulate) {
        AuraAccess.requireNonNegative(amount);
        AuraPool pool = this.pools().get(aura);
        if (pool == null) return amount;
        AuraTransfers.Transfer transfer = AuraTransfers.extract(pool.amount(), amount, exact);
        if (!simulate && transfer.remainder() != amount) pool.change(transfer.stored() - pool.amount());
        return transfer.remainder();
    }
}
