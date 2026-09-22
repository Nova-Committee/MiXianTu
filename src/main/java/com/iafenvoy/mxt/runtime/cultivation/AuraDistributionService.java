package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraZone.Distribution;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.*;
import java.util.Map.Entry;

/**
 * Pre-reserves one shared chunk aura pool for every player due to cultivate this tick, before any of them is
 * processed, so entity-tick ordering cannot decide who is served first.
 */
public final class AuraDistributionService {
    private static final Map<UUID, Allocation> ALLOCATIONS = new HashMap<>();

    private AuraDistributionService() {
    }

    public static void prepare(ServerLevel level) {
        long gameTime = level.getGameTime();
        ALLOCATIONS.entrySet().removeIf(entry -> entry.getValue().gameTime() != gameTime);
        Map<Long, List<Claim>> claimsByChunk = new HashMap<>();
        for (ServerPlayer player : level.players()) {
            CultivationAttachment spirit = player.getData(MxtAttachments.CULTIVATION);
            Holder<CultivateAction> action = spirit.cultivateAction().orElse(null);
            if (!spirit.cultivating() || action == null || gameTime < spirit.nextCultivateTick()) continue;
            CultivateAction definition = action.value();
            FormulaContext context = FormulaContexts.forEntity(player);
            AuraResult aura = AuraService.getPositionAura(level, player.blockPosition());
            if (!CultivationActionService.canCultivateInEnvironment(spirit, player, aura, context)
                    || !definition.condition().test(player, context)) continue;
            Map<Holder<Aura>, Double> requested = evaluateCosts(definition, context);
            if (requested == null || requested.isEmpty()) continue;
            double weight = shareWeight(spirit, context);
            claimsByChunk.computeIfAbsent(chunkKey(player), ignored -> new ArrayList<>())
                    .add(new Claim(player, requested, weight, aura));
        }
        for (List<Claim> claims : claimsByChunk.values()) reserve(level, gameTime, claims);
    }

    // Empty when there was no prepass for this tick.
    public static Optional<Map<Holder<Aura>, Double>> take(ServerPlayer player) {
        Allocation allocation = ALLOCATIONS.remove(player.getUUID());
        return allocation == null || allocation.gameTime() != player.level().getGameTime()
                ? Optional.empty() : Optional.of(allocation.amounts());
    }

    private static void reserve(ServerLevel level, long gameTime, List<Claim> claims) {
        claims.sort(Comparator.comparing(claim -> claim.player().getUUID()));
        LevelChunk chunk = level.getChunkAt(claims.getFirst().player().blockPosition());
        AuraChunkAttachment stored = chunk.getData(MxtAttachments.AURA_CHUNK);
        // The pool is chunk-scoped, so it follows the first active claimant's resolved zone policy when
        // overlapping dynamic zones disagree.
        Distribution distribution = claims.getFirst().aura().distribution();
        Map<Integer, Map<Holder<Aura>, Double>> allocations = new HashMap<>();
        for (int index = 0; index < claims.size(); index++) allocations.put(index, new LinkedHashMap<>());
        Set<Holder<Aura>> auras = new LinkedHashSet<>();
        claims.forEach(claim -> auras.addAll(claim.requested().keySet()));
        for (Holder<Aura> aura : auras) {
            double available = stored.auras().getOrDefault(aura, AuraPool.empty()).amount();
            List<Double> allocated = distribute(claims.stream().map(claim -> claim.requested().getOrDefault(aura, 0.0D)).toList(),
                    claims.stream().map(Claim::weight).toList(), available, distribution, level.getRandom());
            for (int index = 0; index < claims.size(); index++) {
                if (allocated.get(index) > 0.0D) allocations.get(index).put(aura, allocated.get(index));
            }
        }
        for (int index = 0; index < claims.size(); index++)
            ALLOCATIONS.put(claims.get(index).player().getUUID(), new Allocation(gameTime, allocations.get(index)));
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
            if (totalWeight <= 0.0D) totalWeight = active.size();
            List<Integer> fulfilled = new ArrayList<>();
            double distributed = 0.0D;
            for (int index : active) {
                double weight = weight(weights, index, weighted);
                if (weight == 0.0D && totalWeight != active.size()) continue;
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
        return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
    }

    private static double weight(List<Double> weights, int index, boolean weighted) {
        if (!weighted) return 1.0D;
        double value = weights.get(index);
        return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
    }

    private static long chunkKey(ServerPlayer player) {
        return ((long) (player.getBlockX() >> 4) & 0xFFFFFFFFL)
                | ((long) (player.getBlockZ() >> 4) & 0xFFFFFFFFL) << 32;
    }

    private static double shareWeight(CultivationAttachment spirit, FormulaContext context) {
        return spirit.realmStages().values().stream()
                .map(stage -> stage.value().auraShareWeight().evaluate(context))
                .filter(value -> Double.isFinite(value) && value > 0.0D)
                .max(Double::compareTo).orElse(1.0D);
    }

    private static Map<Holder<Aura>, Double> evaluateCosts(CultivateAction action, FormulaContext context) {
        Map<Holder<Aura>, Double> result = new LinkedHashMap<>();
        for (Entry<Holder<Aura>, NumberProvider> entry : action.auraCosts().entrySet()) {
            double value = entry.getValue().evaluate(context);
            if (!Double.isFinite(value) || value < 0.0D) return null;
            if (value > 0.0D) result.put(entry.getKey(), value);
        }
        return result;
    }

    private record Claim(ServerPlayer player, Map<Holder<Aura>, Double> requested, double weight, AuraResult aura) {
    }

    private record Allocation(long gameTime, Map<Holder<Aura>, Double> amounts) {
    }
}
