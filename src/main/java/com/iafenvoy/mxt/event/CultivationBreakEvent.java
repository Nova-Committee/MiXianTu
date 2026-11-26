package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.saturn.attachment.ResourceHolderAttachment;
import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

/**
 * Authoritative transaction event emitted around a realm breakthrough.
 */
public abstract class CultivationBreakEvent extends Event {
    private final CultivationAttachment spirit;
    private final ResourceHolderAttachment resources;
    private final Holder<RealmStage> target;
    private final FormulaContext context;
    private final double threshold;

    protected CultivationBreakEvent(@NotNull CultivationAttachment spirit, @NotNull ResourceHolderAttachment resources, @NotNull Holder<RealmStage> target, @NotNull FormulaContext context, double threshold) {
        this.spirit = spirit.copy();
        this.resources = resources.copy();
        this.target = target;
        this.context = context;
        this.threshold = threshold;
    }

    /** Returns a detached snapshot of the cultivation state. */
    public CultivationAttachment getSpirit() {
        return this.spirit.copy();
    }

    /** Returns a detached snapshot of the resource account. */
    public ResourceHolderAttachment getResource() {
        return this.resources.copy();
    }

    public Map<Holder<Aura>, Double> getProgress() {
        return Map.copyOf(this.spirit.cultivationProgresses());
    }

    public Map<Holder<Aura>, Holder<RealmStage>> getRealms() {
        return Map.copyOf(this.spirit.realmStages());
    }

    public Map<Holder<Resource>, Double> getResources() {
        return Map.copyOf(this.resources.values());
    }

    public Holder<RealmStage> getTarget() {
        return this.target;
    }

    public FormulaContext getContext() {
        return this.context;
    }

    public double getThreshold() {
        return this.threshold;
    }

    public static final class Pre extends CultivationBreakEvent implements ICancellableEvent {
        private final Map<Identifier, Double> originalCosts;
        private final Map<Identifier, Double> costs;

        public Pre(CultivationAttachment spirit, ResourceHolderAttachment resources, Holder<RealmStage> target, FormulaContext context, double threshold, Map<Identifier, Double> costs) {
            super(spirit, resources, target, context, threshold);
            this.originalCosts = Collections.unmodifiableMap(new LinkedHashMap<>(costs));
            this.costs = new LinkedHashMap<>(costs);
        }

        public Map<Identifier, Double> getOriginalCosts() {
            return this.originalCosts;
        }

        public Map<Identifier, Double> getCosts() {
            return Collections.unmodifiableMap(this.costs);
        }

        public void setCost(Identifier resource, double amount) {
            if (resource == null || !Double.isFinite(amount) || amount < 0.0D)
                throw new IllegalArgumentException("Breakthrough costs must be finite and non-negative");
            if (amount == 0.0D) this.costs.remove(resource);
            else this.costs.put(resource, amount);
        }
    }

    public static final class Post extends CultivationBreakEvent {
        private final Map<Identifier, Double> paidCosts;

        public Post(CultivationAttachment spirit, ResourceHolderAttachment resources, Holder<RealmStage> target, FormulaContext context, double threshold, Map<Identifier, Double> paidCosts) {
            super(spirit, resources, target, context, threshold);
            this.paidCosts = Map.copyOf(paidCosts);
        }

        public Map<Identifier, Double> getPaidCosts() {
            return this.paidCosts;
        }
    }
}
