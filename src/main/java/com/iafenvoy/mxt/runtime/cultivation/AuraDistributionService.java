package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraZone.Distribution;
import com.iafenvoy.mxt.data.cost.Costs;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigins;
import com.iafenvoy.mxt.data.cultivation.Cultivation;
import com.iafenvoy.mxt.data.formation.Formation;
import com.iafenvoy.mxt.data.formation.FormationIntervals;
import com.iafenvoy.mxt.data.formation.builtin.AuraFormationAction;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.formation.FormationInstance;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.runtime.world.BlockAuraService;
import com.iafenvoy.mxt.util.MiscUtil;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import com.iafenvoy.mxt.util.profiling.ProfilerScope;
import com.iafenvoy.mxt.util.profiling.ServerProfiling;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Pre-reserves the shared aura stock for every consumer due this tick before any of them is processed, so
 * entity-tick ordering cannot decide who is served first. Consumers are cultivators and standing formations, and
 * the stock they split is one sub-chunk: a section is drawn by whoever stands in it and by the formations whose
 * ground it belongs to.
 */
public final class AuraDistributionService {
    private static final Map<UUID, Allocation> PLAYER_ALLOCATIONS = new HashMap<>();
    // Keyed by formation and period both, so this tick's claim never replaces the one the formation is about to collect:
    // which of the two listeners runs first must not decide whether a period is paid.
    private static final Map<CreditKey, Credit> FORMATION_CREDITS = new HashMap<>();
    // What a formation's ground holds this period, per section. Recomputed once per period, not once per tick.
    private static final Map<FormationKey, Ground> GROUND = new HashMap<>();

    private AuraDistributionService() {
    }

    public static void prepare(ServerLevel level) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.CULTIVATION_DISTRIBUTION)) {
            long gameTime = level.getGameTime();
            long period = currentPeriod(gameTime);
            PLAYER_ALLOCATIONS.entrySet().removeIf(entry -> entry.getValue().gameTime() != gameTime);
            // The period that just ended is still collectable, so it is not swept: anything older than it is a period nobody
            // collected, and dropping it here is what keeps the map from growing over a session.
            FORMATION_CREDITS.keySet().removeIf(key -> key.period() < settledPeriod(gameTime));
            Map<Slot, List<Claim>> claimsBySlot = new HashMap<>();
            collectCultivators(level, gameTime, claimsBySlot);
            collectFormations(level, period, claimsBySlot);
            for (Map.Entry<Slot, List<Claim>> claims : claimsBySlot.entrySet())
                reserve(level, gameTime, period, claims.getKey(), claims.getValue());
        }
    }

    // The period a tick belongs to, and the one a formation settles here: the first tick of a new period is where the
    // period that just ended is paid for, which is why the two are named apart.
    private static long currentPeriod(long gameTime) {
        return gameTime / FormationIntervals.PERIOD;
    }

    private static long settledPeriod(long gameTime) {
        return currentPeriod(gameTime) - 1L;
    }

    // Empty when there was no prepass for this tick.
    public static Optional<Map<Holder<Aura>, Double>> take(ServerPlayer player) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.CULTIVATION_DISTRIBUTION)) {
            Allocation allocation = PLAYER_ALLOCATIONS.remove(player.getUUID());
            return allocation == null || allocation.gameTime() != player.level().getGameTime()
                    ? Optional.empty() : Optional.of(allocation.amounts());
        }
    }

    /**
     * What this formation was granted over the period that just ended, taken out of the sections it was granted from.
     * Emptied here, so a period pays once.
     * <p>
     * The withdrawal is this step, not the reservation: an allocation is a claim on a stock, and a claim that is never
     * collected must cost nothing. A section that has been drained since - by the players standing in it, by
     * regeneration that never happened - pays what it still holds, so nothing is invented.
     */
    public static Map<Holder<Aura>, Double> takeUpkeep(ServerLevel level, BlockPos controller) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.CULTIVATION_DISTRIBUTION)) {
            FormationKey formation = new FormationKey(level.dimension(), controller);
            Credit credit = FORMATION_CREDITS.remove(new CreditKey(formation, settledPeriod(level.getGameTime())));
            if (credit == null) return Map.of();
            Map<Holder<Aura>, Double> taken = new LinkedHashMap<>();
            credit.sources().forEach((slot, amounts) -> withdraw(level, slot, amounts, taken));
            return taken;
        }
    }

    // Takes what one section still holds of what was claimed, and reports only that.
    private static void withdraw(ServerLevel level, Slot slot, Map<Holder<Aura>, Double> claimed,
                                 Map<Holder<Aura>, Double> taken) {
        int chunkX = (int) slot.chunk();
        int chunkZ = (int) (slot.chunk() >> 32);
        if (!(level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) instanceof LevelChunk chunk))
            return;
        Map<Holder<Aura>, AuraPool> stock = chunk.getData(MxtAttachments.AURA_CHUNK).pools(slot.sectionY());
        claimed.forEach((aura, claimedAmount) -> {
            AuraPool pool = stock.get(aura);
            if (pool == null || !Double.isFinite(claimedAmount)) return;
            double amount = Math.min(Math.max(0.0D, claimedAmount), pool.amount());
            if (amount <= 0.0D) return;
            pool.change(-amount);
            taken.merge(aura, amount, Double::sum);
        });
    }

    /**
     * The ground a formation stands on this period, before it competes with anyone: what its upkeep report shows
     * and what its per-tick claims are cut from.
     */
    public static Map<Holder<Aura>, Double> projectedSupply(ServerLevel level, BlockPos controller, double radius) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.CULTIVATION_DISTRIBUTION)) {
            Map<Holder<Aura>, Double> totals = new LinkedHashMap<>();
            ground(level, controller, radius).slots().values()
                    .forEach(section -> section.forEach((aura, amount) -> totals.merge(aura, amount, Double::sum)));
            return totals;
        }
    }

    private static void collectCultivators(ServerLevel level, long gameTime, Map<Slot, List<Claim>> claimsBySlot) {
        for (ServerPlayer player : level.players()) {
            CultivationAttachment spirit = player.getData(MxtAttachments.CULTIVATION);
            Holder<Cultivation> action = spirit.cultivation().orElse(null);
            if (!spirit.cultivating() || action == null || gameTime < spirit.nextCultivateTick()) continue;
            Cultivation definition = action.value();
            FormulaContext context = FormulaContexts.forEntity(player);
            AuraResult aura = AuraService.getPositionAura(level, player.blockPosition());
            if (!CultivationMethodService.canCultivateInEnvironment(spirit, player, aura, context)
                    || !definition.tickCondition().test(player, context)) continue;
            Map<Holder<Aura>, Double> requested = evaluateCosts(definition, context);
            if (requested == null || requested.isEmpty()) continue;
            claimsBySlot.computeIfAbsent(slot(player.blockPosition()), ignored -> new ArrayList<>())
                    .add(new Claim(player, null, requested, shareWeight(spirit, context), aura.distribution()));
        }
    }

    // One claim per covered section, each a period's worth divided by the period: over the period the formation is
    // offered exactly what its ground holds, but only as much of it as the split leaves it.
    private static void collectFormations(ServerLevel level, long period, Map<Slot, List<Claim>> claimsBySlot) {
        Map<BlockPos, FormationInstance> formations = level.getData(MxtAttachments.FORMATION_WORLD).formations();
        Set<FormationKey> active = new HashSet<>();
        for (Map.Entry<BlockPos, FormationInstance> entry : formations.entrySet()) {
            FormationKey key = new FormationKey(level.dimension(), entry.getKey());
            active.add(key);
            Ground ground = GROUND.get(key);
            if (ground == null || ground.period() != period) {
                ground = ground(level, entry.getKey(), entry.getValue().radius());
                GROUND.put(key, ground);
            }
            double weight = upkeepWeight(level, entry.getValue());
            for (Map.Entry<Slot, Map<Holder<Aura>, Double>> slot : ground.slots().entrySet()) {
                Map<Holder<Aura>, Double> slice = new LinkedHashMap<>();
                slot.getValue().forEach((aura, amount) -> {
                    double share = amount / FormationIntervals.PERIOD;
                    if (share > 0.0D) slice.put(aura, share);
                });
                if (slice.isEmpty()) continue;
                claimsBySlot.computeIfAbsent(slot.getKey(), ignored -> new ArrayList<>())
                        .add(new Claim(null, key, slice, weight, ground.policy()));
            }
        }
        GROUND.keySet().removeIf(key -> key.dimension().equals(level.dimension()) && !active.contains(key));
    }

    // One section's worth of emitters inside the sphere, per covered section, plus the ambient aura of the
    // section the controller stands in when the server option lets a formation spend it. Taken once per period.
    private static Ground ground(ServerLevel level, BlockPos controller, double radius) {
        double radiusSquared = radius * radius;
        Map<Slot, Map<Holder<Aura>, Double>> slots = new LinkedHashMap<>();
        int minChunkX = (int) Math.floor((controller.getX() - radius) / 16.0D);
        int maxChunkX = (int) Math.floor((controller.getX() + radius) / 16.0D);
        int minChunkZ = (int) Math.floor((controller.getZ() - radius) / 16.0D);
        int maxChunkZ = (int) Math.floor((controller.getZ() + radius) / 16.0D);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) continue;
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                long key = chunkKey(chunkX, chunkZ);
                Int2ObjectMap<Map<Holder<Aura>, Double>> sections = BlockAuraService.groundInSphere(level, chunk, controller, radiusSquared);
                for (Int2ObjectMap.Entry<Map<Holder<Aura>, Double>> section : sections.int2ObjectEntrySet())
                    slots.put(new Slot(key, section.getIntKey()), section.getValue());
            }
        }
        long period = level.getGameTime() / FormationIntervals.PERIOD;
        Distribution policy = AuraService.getPositionAura(level, controller).distribution();
        if (MxtServerConfig.INSTANCE.formations.drawsEnvironment.getValue()) {
            Map<Holder<Aura>, Double> ambient = ambientSupply(level, controller);
            if (!ambient.isEmpty())
                mergeInto(slots.computeIfAbsent(slot(controller), ignored -> new LinkedHashMap<>()), ambient);
        }
        return new Ground(period, slots, policy);
    }

    // The ambient part of the resolved aura at the controller: what the pool holds beyond its own block emitters.
    private static Map<Holder<Aura>, Double> ambientSupply(ServerLevel level, BlockPos controller) {
        Map<Holder<Aura>, Double> supply = new LinkedHashMap<>();
        AuraService.getPositionAura(level, controller).aura().forEach((resource, pool) -> {
            double available = pool.amount() - pool.supplied();
            if (available > 0.0D) supply.put(resource, available);
        });
        return supply;
    }

    private static void mergeInto(Map<Holder<Aura>, Double> target, Map<Holder<Aura>, Double> values) {
        values.forEach((aura, amount) -> target.merge(aura, amount, Double::sum));
    }

    private static double upkeepWeight(ServerLevel level, FormationInstance instance) {
        Formation definition = MxtDatapackRegistries.get(level.registryAccess(), MxtResourceKeys.FORMATION, instance.formation()).orElse(null);
        if (definition == null) return 1.0D;
        FormulaContext context = FormulaContext.of(level);
        return definition.actions().stream()
                .filter(AuraFormationAction.class::isInstance)
                .map(AuraFormationAction.class::cast)
                .mapToDouble(action -> MiscUtil.positive(action.upkeepWeight().evaluate(context)))
                .filter(weight -> weight > 0.0D)
                .max().orElse(1.0D);
    }

    private static void reserve(ServerLevel level, long gameTime, long period, Slot slot, List<Claim> claims) {
        // A chunk that unloaded after its ground was measured is skipped rather than loaded again: a claim is
        // worth nothing for a chunk nobody is standing in.
        if (!(level.getChunkSource().getChunk((int) slot.chunk(), (int) (slot.chunk() >> 32), ChunkStatus.FULL, false) instanceof LevelChunk chunk))
            return;
        claims.sort(Comparator.comparing(Claim::sortKey));
        Map<Holder<Aura>, AuraPool> stock = chunk.getData(MxtAttachments.AURA_CHUNK).pools(slot.sectionY());
        // The stock is section-scoped, so it follows the resolved zone policy of the first claimant.
        Distribution distribution = claims.stream().map(Claim::policy).filter(Objects::nonNull)
                .findFirst().orElse(Distribution.EQUAL);
        // One map per claimant: a shared map would make the last write win, so every consumer would read somebody
        // else's share - and a zero share would still put a neighbour's amount in front of it.
        List<Map<Holder<Aura>, Double>> allocations = new ArrayList<>(claims.size());
        for (int index = 0; index < claims.size(); index++) allocations.add(new LinkedHashMap<>());
        Set<Holder<Aura>> auras = new LinkedHashSet<>();
        claims.forEach(claim -> auras.addAll(claim.requested().keySet()));
        for (Holder<Aura> aura : auras) {
            AuraPool pool = stock.get(aura);
            double available = pool == null ? 0.0D : pool.amount();
            List<Double> allocated = distribute(claims.stream().map(claim -> claim.requested().getOrDefault(aura, 0.0D)).toList(),
                    claims.stream().map(Claim::weight).toList(), available, distribution, level.getRandom());
            for (int index = 0; index < claims.size(); index++) {
                if (allocated.get(index) > 0.0D) allocations.get(index).put(aura, allocated.get(index));
            }
        }
        for (int index = 0; index < claims.size(); index++) {
            Claim claim = claims.get(index);
            if (claim.player() != null)
                PLAYER_ALLOCATIONS.put(claim.player().getUUID(), new Allocation(gameTime, allocations.get(index)));
            else addCredit(claim.formation(), period, slot, allocations.get(index));
        }
    }

    // A formation's claim keeps the section it was cut from: what it is paid is drawn from there, so the source of a
    // credit is part of the credit rather than something to reconstruct when the bill arrives.
    private static void addCredit(FormationKey formation, long period, Slot slot, Map<Holder<Aura>, Double> amounts) {
        if (amounts.isEmpty()) return;
        Credit credit = FORMATION_CREDITS.computeIfAbsent(new CreditKey(formation, period),
                ignored -> new Credit(new LinkedHashMap<>()));
        mergeInto(credit.sources().computeIfAbsent(slot, ignored -> new LinkedHashMap<>()), amounts);
    }

    // Allocates one finite shared pool without mutating world state. Public so integrations can preview the
    // three allocation rules without creating fake players or chunks.
    public static List<Double> distribute(List<Double> requests, List<Double> weights, double available,
                                          Distribution distribution, RandomSource random) {
        if (requests.size() != weights.size())
            throw new IllegalArgumentException("Requests and weights must have equal sizes");
        List<Double> result = new ArrayList<>(Collections.nCopies(requests.size(), 0.0D));
        double remaining = Double.isFinite(available) ? Math.max(0.0D, available) : Double.MAX_VALUE;
        List<Integer> active = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            if (request(requests, index) > 0.0D) active.add(index);
        }
        if (distribution == Distribution.RANDOM) {
            for (int index = active.size() - 1; index > 0; index--) {
                int swap = random.nextInt(index + 1);
                int value = active.get(index);
                active.set(index, active.get(swap));
                active.set(swap, value);
            }
            for (int index : active) {
                double amount = Math.min(request(requests, index), remaining);
                result.set(index, amount);
                remaining -= amount;
                if (remaining <= 0.0D) break;
            }
            return result;
        }
        boolean weighted = distribution == Distribution.REALM_WEIGHTED;
        while (remaining > 1.0E-9D && !active.isEmpty()) {
            double totalWeight = active.stream().mapToDouble(index -> weight(weights, index, weighted)).sum();
            // Whether nobody carries weight is a state, not a number: a real total that happens to equal the head count
            // is not the fallback, so it is decided once here instead of being re-derived from equality below.
            boolean fallback = totalWeight <= 0.0D;
            if (fallback) totalWeight = active.size();
            List<Integer> fulfilled = new ArrayList<>();
            double distributed = 0.0D;
            for (int index : active) {
                double weight = weight(weights, index, weighted);
                if (weight == 0.0D && !fallback) continue;
                if (weight == 0.0D) weight = 1.0D;
                double share = remaining * weight / totalWeight;
                double missing = request(requests, index) - result.get(index);
                double amount = Math.min(Math.max(0.0D, missing), share);
                result.set(index, result.get(index) + amount);
                distributed += amount;
                if (amount + 1.0E-9D >= missing) fulfilled.add(index);
            }
            remaining -= distributed;
            if (fulfilled.isEmpty()) break;
            active.removeAll(fulfilled);
        }
        return result;
    }

    private static double request(List<Double> requests, int index) {
        double value = requests.get(index);
        return MiscUtil.positive(value);
    }

    private static double weight(List<Double> weights, int index, boolean weighted) {
        if (!weighted) return 1.0D;
        double value = weights.get(index);
        return MiscUtil.positive(value);
    }

    private static Slot slot(BlockPos pos) {
        return new Slot(chunkKey(pos.getX() >> 4, pos.getZ() >> 4), SectionPos.blockToSectionCoord(pos.getY()));
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | (chunkZ & 0xFFFFFFFFL) << 32;
    }

    private static double shareWeight(CultivationAttachment spirit, FormulaContext context) {
        return spirit.realmStages().values().stream()
                .map(stage -> stage.value().auraShareWeight().evaluate(context))
                .filter(value -> MiscUtil.positive(value) > 0.0D)
                .max(Double::compareTo).orElse(1.0D);
    }

    private static Map<Holder<Aura>, Double> evaluateCosts(Cultivation action, FormulaContext context) {
        return Costs.auras(action.costs(), CostContext.of(null, context, CostOrigins.CULTIVATION));
    }

    private record Slot(long chunk, int sectionY) {
    }

    private record FormationKey(ResourceKey<Level> dimension, BlockPos controller) {
    }

    private record CreditKey(FormationKey formation, long period) {
    }

    private record Ground(long period, Map<Slot, Map<Holder<Aura>, Double>> slots, Distribution policy) {
    }

    private record Claim(@Nullable ServerPlayer player, @Nullable FormationKey formation,
                         Map<Holder<Aura>, Double> requested, double weight, @Nullable Distribution policy) {
        private String sortKey() {
            return this.player != null ? this.player.getUUID().toString() : "formation:" + this.formation;
        }
    }

    private record Allocation(long gameTime, Map<Holder<Aura>, Double> amounts) {
    }

    private record Credit(Map<Slot, Map<Holder<Aura>, Double>> sources) {
    }
}
