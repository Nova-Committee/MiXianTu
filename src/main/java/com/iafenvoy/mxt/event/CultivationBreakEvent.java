package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.SpiritAttachment;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authoritative transaction event emitted around a realm breakthrough.
 */
public abstract class CultivationBreakEvent extends Event {
    private final SpiritAttachment spirit;
    private final ResourceHolderAttachment resources;
    private final Identifier target;
    private final RealmStage definition;
    private final FormulaContext context;
    private final double threshold;

    protected CultivationBreakEvent(@NotNull SpiritAttachment spirit, @NotNull ResourceHolderAttachment resources, @NotNull Identifier target, @NotNull RealmStage definition, @NotNull FormulaContext context, double threshold) {
        this.spirit = spirit;
        this.resources = resources;
        this.target = target;
        this.definition = definition;
        this.context = context;
        this.threshold = threshold;
    }

    public SpiritAttachment spirit() {
        return this.spirit;
    }

    public ResourceHolderAttachment resources() {
        return this.resources;
    }

    public Identifier target() {
        return this.target;
    }

    public RealmStage definition() {
        return this.definition;
    }

    public FormulaContext context() {
        return this.context;
    }

    public double threshold() {
        return this.threshold;
    }

    public static final class Pre extends CultivationBreakEvent implements ICancellableEvent {
        private final Map<Identifier, Double> originalCosts;
        private final Map<Identifier, Double> costs;

        public Pre(SpiritAttachment spirit, ResourceHolderAttachment resources, Identifier target, RealmStage definition, FormulaContext context, double threshold, Map<Identifier, Double> costs) {
            super(spirit, resources, target, definition, context, threshold);
            this.originalCosts = new LinkedHashMap<>(costs);
            this.costs = new LinkedHashMap<>(costs);
        }

        public Map<Identifier, Double> originalCosts() {
            return this.originalCosts;
        }

        public Map<Identifier, Double> costs() {
            return this.costs;
        }

        public void setCost(Identifier resource, double amount) {
            if (resource == null || !Double.isFinite(amount) || amount <= 0.0D)
                throw new IllegalArgumentException("Breakthrough costs must be finite and positive");
            this.costs.put(resource, amount);
        }
    }

    public static final class Post extends CultivationBreakEvent {
        private final Map<Identifier, Double> paidCosts;

        public Post(SpiritAttachment spirit, ResourceHolderAttachment resources, Identifier target, RealmStage definition, FormulaContext context, double threshold, Map<Identifier, Double> paidCosts) {
            super(spirit, resources, target, definition, context, threshold);
            this.paidCosts = new LinkedHashMap<>(paidCosts);
        }

        public Map<Identifier, Double> paidCosts() {
            return this.paidCosts;
        }
    }
}
