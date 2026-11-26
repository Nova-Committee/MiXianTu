package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.AuraResult.SourceKind;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.runtime.world.AuraZonePriorityProbe;
import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Re-checks the aura zone priority selection, the behaviour with no other observable entry point.
 *
 * <p>It was one leg of the {@code /mxt_test verify} chain, which stopped at the first failure; as a game test it
 * stands alone and reports every disagreement it found.
 */
public final class AuraVerifyProbes {
    private AuraVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/aura_priority", AuraVerifyProbes::auraPriority),
                Probe.of("verify/external_api/aura_query_snapshot", AuraVerifyProbes::snapshot));
    }

    private static void snapshot(ProbeContext context) {
        AuraResult resolved = AuraService.getPositionAura(context.level(), context.playerSpot());
        Holder<Aura> aura = MxtDatapackRegistries.holders(context.registries(), MxtResourceKeys.AURA).findFirst().orElseThrow();
        AuraPool original = AuraPool.natural(3.0D, 10.0D, 0.5D);
        AuraResult snapshot = resolved.copy(Map.of(aura, original));
        original.change(2.0D);
        context.expect("snapshot does not read source mutations", snapshot.pool(aura).amount(), 3.0D);
        try {
            snapshot.aura().clear();
            context.fail("snapshot map allowed mutation");
        } catch (UnsupportedOperationException expected) {
            context.expect("snapshot still contains the aura", snapshot.aura().size(), 1);
        }
        try {
            snapshot.pool(aura).change(2.0D);
            context.fail("snapshot pool allowed mutation");
        } catch (UnsupportedOperationException expected) {
            context.expect("shared pool keeps its value", snapshot.pool(aura).amount(), 3.0D);
        }
        snapshot.pool(aura).copy().change(2.0D);
        context.expect("pool copy does not modify the snapshot", snapshot.pool(aura).amount(), 3.0D);
    }

    private static void auraPriority(ProbeContext context) {
        String failure = verifyAuraPriority(context);
        if (failure != null) context.fail(failure);
    }

    // The documented rule: highest priority wins inside a tier, the registry ID breaks ties, and a dimension
    // binding is never outranked by a biome binding.
    private static String verifyAuraPriority(ProbeContext context) {
        Identifier level = context.level().dimension().identifier();
        List<Reference<AuraZone>> biomeZones = new ArrayList<>();
        List<Reference<AuraZone>> dimensionZones = new ArrayList<>();
        for (Reference<AuraZone> holder : MxtDatapackRegistries.holders(context.registries(), MxtResourceKeys.AURA_ZONE).toList()) {
            if (declaresDimension(context, holder.value(), level)) dimensionZones.add(holder);
            if (!holder.value().biomes().isEmpty()) biomeZones.add(holder);
        }
        String tieBreak = verifyPriorityOrdering(biomeZones, dimensionZones);
        if (tieBreak != null) return tieBreak;
        // Both tiers have to be there for the rule to mean anything, and that is not a technicality: a dimension
        // entry that the pack cannot resolve is dropped while it loads, which leaves the tier empty and the probe
        // green on the biome tier alone. Saying so is the only way this leg stays a test of the dimension tier.
        if (dimensionZones.isEmpty())
            return "no aura zone declares this level through dimensions, so the dimension tier cannot be checked";
        if (biomeZones.isEmpty())
            return "no aura zone declares biomes, so the dimension-over-biome precedence cannot be checked";
        AuraResult resolved = AuraService.getPositionAura(context.level(), context.player().blockPosition());
        Identifier expected = AuraZonePriorityProbe.select(dimensionZones).orElseThrow();
        if (resolved.sourceKind() != SourceKind.DIMENSION)
            return "expected a dimension binding but resolved " + resolved.sourceKind();
        return expected.equals(HolderHelper.id(resolved.source())) ? null
                : "dimension winner mismatch: expected " + expected + " but resolved " + resolved.source();
    }

    // Asserts the production ordering helper itself: maximum priority first, ascending registry ID on a tie.
    private static String verifyPriorityOrdering(List<Reference<AuraZone>> biomeZones,
                                                 List<Reference<AuraZone>> dimensionZones) {
        for (List<Reference<AuraZone>> candidates : List.of(biomeZones, dimensionZones)) {
            if (candidates.isEmpty()) continue;
            int highest = candidates.stream().mapToInt(holder -> holder.value().priority()).max().orElseThrow();
            Identifier expected = candidates.stream()
                    .filter(holder -> holder.value().priority() == highest)
                    .map(HolderHelper::id)
                    .min(Comparator.naturalOrder())
                    .orElseThrow();
            Identifier winner = AuraZonePriorityProbe.select(candidates).orElse(null);
            if (!expected.equals(winner))
                return "priority ordering mismatch: expected " + expected + " but resolved " + winner;
        }
        return null;
    }

    private static boolean declaresDimension(ProbeContext context, AuraZone zone, Identifier level) {
        return RegistryCodecs.matchesKeys(zone.dimensions(),
                context.level().registryAccess().lookupOrThrow(Registries.LEVEL_STEM), level);
    }
}
