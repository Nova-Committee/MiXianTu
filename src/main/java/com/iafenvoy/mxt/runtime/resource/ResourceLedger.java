package com.iafenvoy.mxt.runtime.resource;

import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Server-authoritative mutable resource balances with atomic multi-cost payment.
 */
public final class ResourceLedger {
    private final Map<Identifier, Double> balances = new LinkedHashMap<>();

    public synchronized double get(Identifier id) {
        return this.balances.getOrDefault(id, 0.0D);
    }

    public synchronized void set(@NotNull Identifier id, double value) {
        this.requireFinite(value, "Resource value");
        this.balances.put(id, value);
    }

    public synchronized void add(Identifier id, double value) {
        this.set(id, this.get(id) + value);
    }

    public synchronized TransactionResult tryConsume(List<ResourceCost> costs, FormulaContext context) {
        Evaluation evaluated = ResourceTransactions.evaluate(costs, context);
        for (Entry<Identifier, Double> entry : evaluated.amounts().entrySet()) {
            if (this.get(entry.getKey()) < entry.getValue()) {
                return TransactionResult.rejected(entry.getKey(), evaluated.amounts());
            }
        }
        evaluated.amounts().forEach((id, amount) -> this.balances.put(id, this.get(id) - amount));
        return TransactionResult.committed(evaluated.amounts());
    }

    public synchronized Map<Identifier, Double> snapshot() {
        return Map.copyOf(this.balances);
    }

    private void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }

    public record TransactionResult(boolean committed, Identifier failedResource, Map<Identifier, Double> amounts) {
        static TransactionResult committed(Map<Identifier, Double> amounts) {
            return new TransactionResult(true, null, amounts);
        }

        static TransactionResult rejected(Identifier failedResource, Map<Identifier, Double> amounts) {
            return new TransactionResult(false, failedResource, amounts);
        }
    }
}
