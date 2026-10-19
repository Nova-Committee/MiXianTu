package com.iafenvoy.mxt.runtime.resource;

import com.iafenvoy.mxt.runtime.resulting.Failures;
import com.iafenvoy.mxt.runtime.resulting.ResultWithData;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Server-authoritative mutable resource balances with atomic multi-cost payment. Amounts arrive evaluated, so a
 * caller that holds a costs array loads it with {@code CostPayment} and hands the result here.
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

    public synchronized ResultWithData<Map<Identifier, Double>> tryConsume(Map<Identifier, Double> amounts) {
        for (Entry<Identifier, Double> entry : amounts.entrySet()) {
            if (this.get(entry.getKey()) < entry.getValue()) {
                return ResultWithData.failure(new LinkedHashMap<>(amounts), Failures.UNKNOWN, entry.getKey());
            }
        }
        amounts.forEach((id, amount) -> this.balances.put(id, this.get(id) - amount));
        return ResultWithData.success(new LinkedHashMap<>(amounts));
    }

    public synchronized Map<Identifier, Double> snapshot() {
        return this.balances;
    }

    private void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }
}
