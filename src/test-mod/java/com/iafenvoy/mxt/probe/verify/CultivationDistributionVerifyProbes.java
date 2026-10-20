package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.data.aura.AuraZone.Distribution;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.runtime.cultivation.AuraDistributionService;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * The shared-aura split, driven through its one public entry point: what a consumer is granted, and what the sum of the
 * grants is allowed to be.
 *
 * <p>The three allocation rules are pure functions of requests, weights and one finite stock, so this is where a wrong
 * answer is invisible in play and cheap to pin down.
 */
public final class CultivationDistributionVerifyProbes {
    private CultivationDistributionVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/cultivation/distribution_total", CultivationDistributionVerifyProbes::withinStock),
                Probe.of("verify/cultivation/distribution_weight", CultivationDistributionVerifyProbes::weightFallback)
        );
    }

    // No rule may hand out more than it was given: every grant is bounded by its own request, and their sum by the
    // stock. A zero request is not served at all, whichever rule is in force.
    private static void withinStock(ProbeContext context) {
        RandomSource random = RandomSource.create(20260101L);
        List<List<Double>> requests = List.of(List.of(10.0D, 10.0D), List.of(3.0D, 100.0D), List.of(0.0D, 7.0D));
        List<List<Double>> weights = List.of(List.of(0.0D, 2.0D), List.of(2.0D, 2.0D), List.of(1.0D, 1.0D));
        for (Distribution distribution : Distribution.values()) {
            for (int index = 0; index < requests.size(); index++) {
                List<Double> granted = AuraDistributionService.distribute(requests.get(index), weights.get(index), 10.0D,
                        distribution, random);
                String where = distribution + " " + requests.get(index) + " x " + weights.get(index);
                double total = granted.stream().mapToDouble(Double::doubleValue).sum();
                if (total > 10.0D + 1.0E-6D) context.fail(where + " handed out " + total + " of a stock of 10");
                for (int claim = 0; claim < granted.size(); claim++) {
                    if (granted.get(claim) > requests.get(index).get(claim) + 1.0E-6D)
                        context.fail(where + " granted " + granted.get(claim) + " of a request of " + requests.get(index).get(claim));
                    if (granted.get(claim) < -1.0E-6D) context.fail(where + " granted a negative share");
                }
            }
        }
    }

    // Weighted splitting decides "nobody carries weight" once, instead of re-deriving it from a total that happens to
    // equal the head count: two claimants weighted 0 and 2 of a stock of 10 must not become 5 and 10.
    private static void weightFallback(ProbeContext context) {
        RandomSource random = RandomSource.create(20260101L);
        List<Double> granted = AuraDistributionService.distribute(List.of(10.0D, 10.0D), List.of(0.0D, 2.0D), 10.0D,
                Distribution.REALM_WEIGHTED, random);
        context.expect("a zero-weight claimant of a weighted split is served nothing", granted.get(0), 0.0D);
        context.expect("the only claimant with weight takes the stock", granted.get(1), 10.0D);
        // All-zero weights are the documented fallback: an equal split, still within the stock.
        List<Double> even = AuraDistributionService.distribute(List.of(10.0D, 10.0D), List.of(0.0D, 0.0D), 10.0D,
                Distribution.REALM_WEIGHTED, random);
        context.expect("all-zero weights fall back to one claimant's share", even.get(0), 5.0D);
        context.expect("all-zero weights fall back to the other claimant's share", even.get(1), 5.0D);
        // Weights that are real and happen to sum to the head count are not the fallback: 1 and 1 of a stock of 10 is
        // still an even split, while 1 and 3 is not.
        List<Double> real = AuraDistributionService.distribute(List.of(10.0D, 10.0D), List.of(1.0D, 3.0D), 10.0D,
                Distribution.REALM_WEIGHTED, random);
        context.expect("a lighter claimant of a real weighted split gets the lighter share", real.get(0), 2.5D);
        context.expect("a heavier claimant of a real weighted split gets the heavier share", real.get(1), 7.5D);
    }
}
