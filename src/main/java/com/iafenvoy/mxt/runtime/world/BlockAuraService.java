package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraValue;
import com.iafenvoy.mxt.data.aura.BlockAura;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.*;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds the bounded, per-chunk cache for datapack-defined aura-emitting blocks. Formations never enter this
 * cache: they take their share of the same stock through {@code AuraDistributionService}, like a cultivator.
 */
public final class BlockAuraService {
    // Tags can change without a new registry instance, so the block index is dropped by the datapack hook
    // rather than by a registry-keyed lookup.
    private static volatile Index index;

    private BlockAuraService() {
    }

    public static void clear() {
        index = null;
    }

    public static void reload(RegistryAccess access) {
        index = build(access);
    }

    private static Index index(RegistryAccess access) {
        Index cached = index;
        if (cached != null) return cached;
        Index built = build(access);
        index = built;
        return built;
    }

    // Block ids and tags are expanded once into a per-block index, so the inner loop does one lookup per block.
    private static Index build(RegistryAccess access) {
        Registry<Block> blocks = access.lookupOrThrow(Registries.BLOCK);
        List<BlockAura> definitions = MxtDatapackRegistries.holders(access, MxtResourceKeys.BLOCK_AURA)
                .map(Reference::value).toList();
        Reference2ObjectMap<Block, List<BlockAura>> byBlock = new Reference2ObjectOpenHashMap<>();
        for (BlockAura definition : definitions) {
            for (Holder<Block> holder : RegistryCodecs.resolve(definition.blocks(), blocks).toList())
                byBlock.computeIfAbsent(holder.value(), ignored -> new ArrayList<>()).add(definition);
        }
        return new Index(definitions, byBlock);
    }

    /**
     * One section only, for a sub-chunk that went stale.
     */
    public static void rebuild(ServerLevel level, LevelChunk chunk, int sectionY) {
        rebuild(chunk, chunk.getData(MxtAttachments.AURA_CHUNK), index(level.registryAccess()), sectionY);
    }

    public static void rebuild(ServerLevel level, LevelChunk chunk) {
        AuraChunkAttachment attachment = chunk.getData(MxtAttachments.AURA_CHUNK);
        Index index = index(level.registryAccess());
        int minSectionY = chunk.getMinSectionY();
        // The cached sections join the filled range, so a section that went back to air - now above the filled
        // range - and one left behind by a changed world height are both revisited and cleared.
        IntSet sections = new IntOpenHashSet(attachment.blockAuraSections().keySet());
        for (int i = 0, highest = chunk.getHighestFilledSectionIndex(); i <= highest; i++)
            sections.add(minSectionY + i);
        sections.forEach(sectionY -> rebuild(chunk, attachment, index, sectionY));
    }

    private static void rebuild(LevelChunk chunk, AuraChunkAttachment attachment, Index index, int sectionY) {
        int sectionIndex = sectionY - chunk.getMinSectionY();
        Map<Holder<Aura>, AuraValue> aura = index.definitions().isEmpty() || sectionIndex < 0
                || sectionIndex >= chunk.getSectionsCount()
                ? Map.of() : sectionAura(chunk.getSection(sectionIndex), index);
        attachment.setSectionAura(sectionY, aura);
    }

    /**
     * One block changed: only that block's own contribution is re-evaluated, so nothing walks the section.
     */
    public static void updateBlock(ServerLevel level, LevelChunk chunk, BlockPos pos, BlockState previous, BlockState current) {
        if (previous.equals(current)) return;
        Index index = index(level.registryAccess());
        if (index.definitions().isEmpty()) return;
        Map<Holder<Aura>, AuraValue> departed = blockAura(index, previous);
        Map<Holder<Aura>, AuraValue> arrived = blockAura(index, current);
        if (departed.isEmpty() && arrived.isEmpty()) return;
        chunk.getData(MxtAttachments.AURA_CHUNK).changeBlock(SectionPos.blockToSectionCoord(pos.getY()), departed, arrived);
    }

    // The palette rejects a section before any cell is read, and counts make the aggregate exact without
    // positions: merging the same value n times is the same as scaling it once.
    private static Map<Holder<Aura>, AuraValue> sectionAura(LevelChunkSection section, Index index) {
        if (section.hasOnlyAir() || !section.maybeHas(state -> index.byBlock().containsKey(state.getBlock())))
            return Map.of();
        Map<Holder<Aura>, AuraValue> total = new LinkedHashMap<>();
        section.getStates().count((state, count) -> {
            for (BlockAura definition : definitions(index, state))
                definition.aura().forEach((aura, value) -> total.merge(aura, value.scale(count), AuraValue::merge));
        });
        return total.isEmpty() ? Map.of() : total;
    }

    @FunctionalInterface
    public interface SourceVisitor {
        void accept(int x, int y, int z, List<BlockAura> definitions);
    }

    /**
     * The positions the exact contribution still needs. They are read from the live section instead of the save,
     * so they cannot go stale; they are visited rather than collected, so no position object is allocated.
     */
    public static void visitSources(Level level, LevelChunk chunk, int sectionY, SourceVisitor visitor) {
        Index index = index(level.registryAccess());
        int sectionIndex = sectionY - chunk.getMinSectionY();
        if (index.definitions().isEmpty() || sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount()) return;
        LevelChunkSection section = chunk.getSection(sectionIndex);
        if (section.hasOnlyAir() || !section.maybeHas(state -> index.byBlock().containsKey(state.getBlock())))
            return;
        // A one-value section answers every cell with the same definitions, so only the positions are walked.
        List<BlockAura> uniform = section.getStates().bitsPerEntry() == 0
                ? definitions(index, section.getBlockState(0, 0, 0)) : List.of();
        int baseX = chunk.getPos().getMinBlockX();
        int baseY = SectionPos.sectionToBlockCoord(sectionY);
        int baseZ = chunk.getPos().getMinBlockZ();
        // y outermost and x innermost matches the palette index (y << 8) | (z << 4) | x, so storage is read in
        // order instead of with a stride of a whole y layer.
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    List<BlockAura> found = uniform.isEmpty() ? definitions(index, section.getBlockState(x, y, z)) : uniform;
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
        Index index = index(level.registryAccess());
        Int2ObjectMap<Map<Holder<Aura>, Double>> sections = new Int2ObjectOpenHashMap<>();
        if (index.definitions().isEmpty()) return sections;
        int minSectionY = chunk.getMinSectionY();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int i = 0, highest = chunk.getHighestFilledSectionIndex(); i <= highest; i++) {
            LevelChunkSection section = chunk.getSection(i);
            if (section.hasOnlyAir() || !section.maybeHas(state -> index.byBlock().containsKey(state.getBlock())))
                continue;
            int baseY = SectionPos.sectionToBlockCoord(minSectionY + i);
            if (!sphereReaches(center, baseX, baseY, baseZ, radiusSquared)) continue;
            int sectionY = minSectionY + i;
            Map<Holder<Aura>, Double> totals = sections.computeIfAbsent(sectionY, ignored -> new LinkedHashMap<>());
            if (sphereCovers(center, baseX, baseY, baseZ, radiusSquared)) {
                sectionAura(section, index).forEach((aura, value) -> totals.merge(aura, value.amount(), Double::sum));
                continue;
            }
            List<BlockAura> uniform = section.getStates().bitsPerEntry() == 0
                    ? definitions(index, section.getBlockState(0, 0, 0)) : List.of();
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (center.distToCenterSqr(baseX + x + 0.5D, baseY + y + 0.5D, baseZ + z + 0.5D) > radiusSquared)
                            continue;
                        List<BlockAura> found = uniform.isEmpty() ? definitions(index, section.getBlockState(x, y, z)) : uniform;
                        for (BlockAura definition : found)
                            definition.aura().forEach((aura, value) -> totals.merge(aura, value.amount(), Double::sum));
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

    private static Map<Holder<Aura>, AuraValue> blockAura(Index index, BlockState state) {
        List<BlockAura> found = definitions(index, state);
        if (found.isEmpty()) return Map.of();
        Map<Holder<Aura>, AuraValue> total = new LinkedHashMap<>();
        for (BlockAura definition : found)
            definition.aura().forEach((aura, value) -> total.merge(aura, value.scale(1), AuraValue::merge));
        return total;
    }

    private static List<BlockAura> definitions(Index index, BlockState state) {
        List<BlockAura> found = index.byBlock().get(state.getBlock());
        return found == null ? List.of() : found;
    }

    private record Index(List<BlockAura> definitions, Reference2ObjectMap<Block, List<BlockAura>> byBlock) {
    }
}
