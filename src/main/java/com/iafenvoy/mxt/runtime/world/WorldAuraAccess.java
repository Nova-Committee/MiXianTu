package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.api.AuraHandler;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.context.AuraSection;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.spirit.AuraHandlerUtil;
import com.iafenvoy.mxt.runtime.spirit.AuraTransfers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

// A view of existing section stock; neither simulation nor a missing target loads or initializes terrain.
public record WorldAuraAccess(Level level, BlockPos pos) implements AuraHandler {
    public WorldAuraAccess {
        pos = pos.immutable();
    }

    private Map<Holder<Aura>, AuraPool> pools() {
        return this.pools(AuraService.loadedChunk(this.level, this.pos));
    }

    private Map<Holder<Aura>, AuraPool> pools(@Nullable LevelChunk chunk) {
        if (chunk == null) return Map.of();
        return chunk.getExistingData(MxtAttachments.AURA_CHUNK)
                .map(attachment -> attachment.pools(SectionPos.blockToSectionCoord(this.pos.getY())))
                .orElse(Map.of());
    }

    @Override
    public Set<Holder<Aura>> getAuras() {
        return Set.copyOf(this.pools().keySet());
    }

    @Override
    public double getAmount(Holder<Aura> aura) {
        AuraPool pool = this.pools().get(aura);
        return pool == null ? 0.0D : pool.amount();
    }

    @Override
    public double getCapacity(Holder<Aura> aura) {
        AuraPool pool = this.pools().get(aura);
        return pool == null ? 0.0D : pool.maximum();
    }

    @Override
    public Object storageIdentity(Holder<Aura> aura) {
        return new StorageIdentity(this.level, AuraSection.of(this.level.dimension(), this.pos));
    }

    @Override
    public double insert(Holder<Aura> aura, double amount, TransactionContext transaction) {
        return this.change(aura, amount, transaction, true);
    }

    @Override
    public double extract(Holder<Aura> aura, double amount, TransactionContext transaction) {
        return this.change(aura, amount, transaction, false);
    }

    private double change(Holder<Aura> aura, double amount, TransactionContext transaction, boolean inserting) {
        AuraHandlerUtil.requireNonNegative(amount);
        if (this.level.isClientSide()) return 0.0D;
        LevelChunk chunk = AuraService.loadedChunk(this.level, this.pos);
        if (chunk == null) return 0.0D;
        AuraPool pool = this.pools(chunk).get(aura);
        if (pool == null) return 0.0D;
        double before = pool.amount();
        double after = inserting
                ? AuraTransfers.insert(before, Math.min(Double.MAX_VALUE, pool.maximum()), amount, false).stored()
                : AuraTransfers.extract(before, amount, false).stored();
        pool.setAmount(after, chunk, transaction);
        return inserting ? after - before : before - after;
    }

    private record StorageIdentity(Level level, AuraSection section) {
    }
}
