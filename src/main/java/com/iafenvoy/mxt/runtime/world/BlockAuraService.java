package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraValue;
import com.iafenvoy.mxt.data.aura.BlockAura;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.registry.cache.RegistryCaches;
import com.iafenvoy.mxt.util.profiling.ProfilerScope;
import com.iafenvoy.mxt.util.profiling.ServerProfiling;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds the bounded, per-chunk cache for datapack-defined aura-emitting blocks. Formations never enter this
 * cache: they take their share of the same stock through {@code AuraDistributionService}, like a cultivator.
 */
public final class BlockAuraService {
    /**
     * One section only, for a sub-chunk that went stale.
     */
    public static void rebuild(LevelChunk chunk, int sectionY) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.AURA_BLOCK_CACHE)) {
            rebuild(chunk, chunk.getData(MxtAttachments.AURA_CHUNK), sectionY);
        }
    }

    public static void rebuild(LevelChunk chunk) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.AURA_BLOCK_CACHE)) {
            AuraChunkAttachment attachment = chunk.getData(MxtAttachments.AURA_CHUNK);
            int minSectionY = chunk.getMinSectionY();
            // The cached sections join the filled range, so a section that went back to air - now above the filled
            // range - and one left behind by a changed world height are both revisited and cleared.
            IntSet sections = new IntOpenHashSet(attachment.blockAuraSections().keySet());
            for (int i = 0, highest = chunk.getHighestFilledSectionIndex(); i <= highest; i++)
                sections.add(minSectionY + i);
            sections.forEach(sectionY -> rebuild(chunk, attachment, sectionY));
        }
    }

    private static void rebuild(LevelChunk chunk, AuraChunkAttachment attachment, int sectionY) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.AURA_BLOCK_CACHE)) {
            int sectionIndex = sectionY - chunk.getMinSectionY();
            Map<Holder<Aura>, AuraValue> aura = registry(chunk.getLevel().registryAccess()).size() == 0 || sectionIndex < 0
                    || sectionIndex >= chunk.getSectionsCount()
                    ? Map.of() : sectionAura(chunk.getSection(sectionIndex));
            attachment.setSectionAura(sectionY, aura);
        }
    }

    /**
     * One block changed: only that block's own contribution is re-evaluated, so nothing walks the section.
     */
    public static void updateBlock(ServerLevel level, LevelChunk chunk, BlockPos pos, BlockState previous, BlockState current) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.AURA_BLOCK_CACHE)) {
            if (previous.equals(current)) return;
            if (registry(level.registryAccess()).size() == 0) return;
            Map<Holder<Aura>, AuraValue> departed = blockAura(previous), arrived = blockAura(current);
            if (departed.isEmpty() && arrived.isEmpty()) return;
            chunk.getData(MxtAttachments.AURA_CHUNK).changeBlock(SectionPos.blockToSectionCoord(pos.getY()), departed, arrived);
        }
    }

    // The palette rejects a section before any cell is read, and counts make the aggregate exact without
    // positions: merging the same value n times is the same as scaling it once.
    private static Map<Holder<Aura>, AuraValue> sectionAura(LevelChunkSection section) {
        if (section.hasOnlyAir() || !section.maybeHas(state -> RegistryCaches.BLOCK_AURA.containsKey(state.getBlock())))
            return Map.of();
        Map<Holder<Aura>, AuraValue> total = new LinkedHashMap<>();
        section.getStates().count((state, count) -> {
            for (Holder<BlockAura> definition : definitions(state))
                definition.value().aura().forEach((aura, value) -> total.merge(aura, value.scale(count), AuraValue::merge));
        });
        return total.isEmpty() ? Map.of() : total;
    }

    @FunctionalInterface
    public interface SourceVisitor {
        void accept(int x, int y, int z, List<Holder<BlockAura>> definitions);
    }

    /**
     * The positions the exact contribution still needs. They are read from the live section instead of the save,
     * so they cannot go stale; they are visited rather than collected, so no position object is allocated.
     */
    public static void visitSources(Level level, LevelChunk chunk, int sectionY, SourceVisitor visitor) {
        int sectionIndex = sectionY - chunk.getMinSectionY();
        if (registry(level.registryAccess()).size() == 0 || sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount())
            return;
        LevelChunkSection section = chunk.getSection(sectionIndex);
        if (section.hasOnlyAir() || !section.maybeHas(state -> RegistryCaches.BLOCK_AURA.containsKey(state.getBlock())))
            return;
        // A one-value section answers every cell with the same definitions, so only the positions are walked.
        List<Holder<BlockAura>> uniform = section.getStates().bitsPerEntry() == 0 ? definitions(section.getBlockState(0, 0, 0)) : List.of();
        int baseX = chunk.getPos().getMinBlockX();
        int baseY = SectionPos.sectionToBlockCoord(sectionY);
        int baseZ = chunk.getPos().getMinBlockZ();
        // y outermost and x innermost matches the palette index (y << 8) | (z << 4) | x, so storage is read in
        // order instead of with a stride of a whole y layer.
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    List<Holder<BlockAura>> found = uniform.isEmpty() ? definitions(section.getBlockState(x, y, z)) : uniform;
                    if (!found.isEmpty()) visitor.accept(baseX + x, baseY + y, baseZ + z, found);
                }
            }
        }
    }

    /**
     * What emitters inside a formation's sphere supply from one chunk, summed per aura and kept per section. A
     * section the sphere covers or misses completely is answered by its palette counts; only the sections the
     * surface crosses are walked cell by cell, so the cost follows the surface instead of the volume.
     */
    public static Int2ObjectMap<Map<Holder<Aura>, Double>> groundInSphere(ServerLevel level, LevelChunk chunk, BlockPos center, double radiusSquared) {
        Int2ObjectMap<Map<Holder<Aura>, Double>> sections = new Int2ObjectOpenHashMap<>();
        if (registry(level.registryAccess()).size() == 0) return sections;
        int minSectionY = chunk.getMinSectionY();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int i = 0, highest = chunk.getHighestFilledSectionIndex(); i <= highest; i++) {
            LevelChunkSection section = chunk.getSection(i);
            if (section.hasOnlyAir() || !section.maybeHas(state -> RegistryCaches.BLOCK_AURA.containsKey(state.getBlock())))
                continue;
            int baseY = SectionPos.sectionToBlockCoord(minSectionY + i);
            if (!sphereReaches(center, baseX, baseY, baseZ, radiusSquared)) continue;
            int sectionY = minSectionY + i;
            Map<Holder<Aura>, Double> totals = sections.computeIfAbsent(sectionY, ignored -> new LinkedHashMap<>());
            if (sphereCovers(center, baseX, baseY, baseZ, radiusSquared)) {
                sectionAura(section).forEach((aura, value) -> totals.merge(aura, value.amount(), Double::sum));
                continue;
            }
            List<Holder<BlockAura>> uniform = section.getStates().bitsPerEntry() == 0 ? definitions(section.getBlockState(0, 0, 0)) : List.of();
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (center.distToCenterSqr(baseX + x + 0.5D, baseY + y + 0.5D, baseZ + z + 0.5D) > radiusSquared)
                            continue;
                        List<Holder<BlockAura>> found = uniform.isEmpty() ? definitions(section.getBlockState(x, y, z)) : uniform;
                        for (Holder<BlockAura> definition : found)
                            definition.value().aura().forEach((aura, value) -> totals.merge(aura, value.amount(), Double::sum));
                    }
                }
            }
            if (totals.isEmpty()) sections.remove(sectionY);
        }
        return sections;
    }

    // The sphere reaches a section when its nearest block centre is inside, and covers one when the farthest is.
    private static boolean sphereReaches(BlockPos center, int baseX, int baseY, int baseZ, double radiusSquared) {
        return center.distToCenterSqr(Math.clamp(center.getX(), baseX + 0.5D, baseX + 15.5D),
                Math.clamp(center.getY(), baseY + 0.5D, baseY + 15.5D),
                Math.clamp(center.getZ(), baseZ + 0.5D, baseZ + 15.5D)) <= radiusSquared;
    }

    private static boolean sphereCovers(BlockPos center, int baseX, int baseY, int baseZ, double radiusSquared) {
        return center.distToCenterSqr(farthest(center.getX(), baseX), farthest(center.getY(), baseY),
                farthest(center.getZ(), baseZ)) <= radiusSquared;
    }

    private static double farthest(int value, int base) {
        return Math.abs(value - (base + 0.5D)) >= Math.abs(value - (base + 15.5D)) ? base + 0.5D : base + 15.5D;
    }

    private static Map<Holder<Aura>, AuraValue> blockAura(BlockState state) {
        List<Holder<BlockAura>> found = definitions(state);
        if (found.isEmpty()) return Map.of();
        Map<Holder<Aura>, AuraValue> total = new LinkedHashMap<>();
        for (Holder<BlockAura> definition : found)
            definition.value().aura().forEach((aura, value) -> total.merge(aura, value.scale(1), AuraValue::merge));
        return total;
    }

    private static List<Holder<BlockAura>> definitions(BlockState state) {
        return RegistryCaches.BLOCK_AURA.get(state.getBlock());
    }

    private static Registry<BlockAura> registry(RegistryAccess access) {
        return access.lookupOrThrow(MxtResourceKeys.BLOCK_AURA);
    }
}
