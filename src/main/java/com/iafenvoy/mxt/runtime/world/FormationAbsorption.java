package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.formation.FormationInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides which block aura emitters belong to a formation instead of the environment.
 *
 * <p>An emitter inside a formation's radius supplies that formation. The decision is made when a
 * chunk's block aura is rebuilt, not when aura is queried, because the shared stock subtracts the whole
 * chunk aggregate from the pool: filtering only at query time would leave the absorbed aura in the
 * aggregate and hand it back to every query, letting the same aura be spent twice.</p>
 *
 * <p>The radius is the sphere the formation already claims for its entity actions, so "inside the
 * formation" needs no second definition. A block inside several formations is absorbed once, and the
 * totals are per chunk rather than per formation: the aura is gone from the environment either way, and
 * which formation spends it is decided by whichever one is charging upkeep.</p>
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
            for (Map.Entry<BlockPos, FormationInstance> entry : level.getData(MxtAttachments.FORMATION_WORLD).formations().entrySet()) {
                BlockPos center = entry.getKey();
                double radius = entry.getValue().radius();
                // A formation can only reach this chunk when its centre is within its radius plus the chunk's
                // own extent of the chunk bounds.
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
         * Whether any formation could absorb anything at all. Lets the rebuild skip the distance test
         * entirely on a level with no formations.
         */
        public boolean empty() {
            return this.shapes.isEmpty();
        }
    }

    public record Shape(BlockPos center, double radiusSquared) {
    }

    /**
     * The aura the emitters inside one formation's radius are supplying it.
     *
     * <p>Read from the chunks the radius overlaps, which is why the totals live on the chunk rather than
     * on the formation: the formation only has to ask the level. Summed without distance weighting, so a
     * block inside the formation gives it everything.</p>
     */
    public static Map<Holder<Resource>, Double> absorbedFor(ServerLevel level, BlockPos controller, double radius) {
        Map<Holder<Resource>, Double> totals = new LinkedHashMap<>();
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
     * The ambient aura of the ground a formation stands on, as a supply it can also spend.
     *
     * <p>Read from the resolved aura at the controller, which is the aura a player standing there would
     * see, minus the part the formation's own emitters contribute ({@link AuraPool#supplied()}). Those are
     * either absorbed — and so not in the pool at all — or field aura the formation does not own, and
     * handing either back to it would be counting the same aura twice.</p>
     */
    public static Map<Holder<Resource>, Double> environmentSupply(ServerLevel level, BlockPos controller) {
        Map<Holder<Resource>, Double> supply = new LinkedHashMap<>();
        AuraService.getPositionAura(level, controller).aura().forEach((resource, pool) -> {
            double available = pool.amount() - pool.supplied();
            if (available > 0.0D) supply.put(resource, available);
        });
        return supply;
    }
}
