package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.formation.FormationInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Decides which block aura emitters belong to a formation instead of the environment: an emitter inside a
 * formation's radius supplies that formation. The decision is made when a chunk's block aura is rebuilt, not
 * when aura is queried, because the shared stock subtracts the whole chunk aggregate and filtering at query
 * time would let the same aura be spent twice.
 */
public final class FormationAbsorption {
    private FormationAbsorption() {
    }

    /**
     * Snapshot of the formations able to absorb anything in one chunk, taken once per rebuild so the
     * per-block loop only does distance comparisons.
     */
    public record Sources(List<Shape> shapes) {
        public static Sources of(ServerLevel level, int minX, int minZ, int maxX, int maxZ) {
            List<Shape> shapes = new ArrayList<>();
            for (Entry<BlockPos, FormationInstance> entry : level.getData(MxtAttachments.FORMATION_WORLD).formations().entrySet()) {
                BlockPos center = entry.getKey();
                double radius = entry.getValue().radius();
                // A formation can only reach this chunk when its centre is within its radius plus the chunk
                // bounds.
                if (center.getX() < minX - radius || center.getX() > maxX + radius) continue;
                if (center.getZ() < minZ - radius || center.getZ() > maxZ + radius) continue;
                shapes.add(new Shape(center, radius * radius));
            }
            return new Sources(List.copyOf(shapes));
        }

        public boolean absorbed(BlockPos pos) {
            for (Shape shape : this.shapes) {
                if (shape.center().distToCenterSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= shape.radiusSquared())
                    return true;
            }
            return false;
        }

        /**
         * Whether any formation could absorb anything at all, which lets the rebuild skip the distance test on
         * a level with no formations.
         */
        public boolean empty() {
            return this.shapes.isEmpty();
        }
    }

    public record Shape(BlockPos center, double radiusSquared) {
    }

    /**
     * The aura the emitters inside one formation's radius are supplying it, read from the chunks the radius
     * overlaps. Summed without distance weighting, so a block inside the formation gives it everything.
     */
    public static Map<Holder<Aura>, Double> absorbedFor(ServerLevel level, BlockPos controller, double radius) {
        Map<Holder<Aura>, Double> totals = new LinkedHashMap<>();
        int minChunkX = (int) Math.floor((controller.getX() - radius) / 16.0D);
        int maxChunkX = (int) Math.floor((controller.getX() + radius) / 16.0D);
        int minChunkZ = (int) Math.floor((controller.getZ() - radius) / 16.0D);
        int maxChunkZ = (int) Math.floor((controller.getZ() + radius) / 16.0D);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) continue;
                AuraChunkAttachment aura = level.getChunk(chunkX, chunkZ).getData(MxtAttachments.AURA_CHUNK);
                aura.absorbedAura().forEach((resource, value) ->
                        totals.merge(resource, value.amount(), Double::sum));
            }
        }
        return totals;
    }

    /**
     * The ambient aura of the ground a formation stands on, as a supply it can also spend. Read from the
     * resolved aura at the controller, minus the part the formation's own emitters contribute
     * ({@link AuraPool#supplied()}).
     */
    public static Map<Holder<Aura>, Double> environmentSupply(ServerLevel level, BlockPos controller) {
        Map<Holder<Aura>, Double> supply = new LinkedHashMap<>();
        AuraService.getPositionAura(level, controller).aura().forEach((resource, pool) -> {
            double available = pool.amount() - pool.supplied();
            if (available > 0.0D) supply.put(resource, available);
        });
        return supply;
    }
}
