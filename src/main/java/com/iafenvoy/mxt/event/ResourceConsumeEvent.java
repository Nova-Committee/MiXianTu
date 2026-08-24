package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-side notification around one all-or-nothing common-resource payment.
 */
public abstract class ResourceConsumeEvent extends Event {
    private final ResourceHolderAttachment holder;
    private final Map<Identifier, Double> amounts;

    protected ResourceConsumeEvent(ResourceHolderAttachment holder, Map<Identifier, Double> amounts) {
        this.holder = holder;
        this.amounts = new LinkedHashMap<>(amounts);
        this.amounts.forEach(ResourceConsumeEvent::validate);
    }

    public ResourceHolderAttachment holder() {
        return this.holder;
    }

    public Map<Identifier, Double> amounts() {
        return this.amounts;
    }

    protected void setAmount(Identifier id, double amount) {
        validate(id, amount);
        this.amounts.put(id, amount);
    }

    private static void validate(Identifier id, double amount) {
        if (id == null || !Double.isFinite(amount) || amount <= 0.0D)
            throw new IllegalArgumentException("Resource event costs must be finite and positive");
    }

    public static final class Pre extends ResourceConsumeEvent implements ICancellableEvent {
        public Pre(ResourceHolderAttachment holder, Map<Identifier, Double> amounts) {
            super(holder, amounts);
        }

        @Override
        public void setAmount(Identifier id, double amount) {
            super.setAmount(id, amount);
        }
    }

    public static final class Post extends ResourceConsumeEvent {
        public Post(ResourceHolderAttachment holder, Map<Identifier, Double> amounts) {
            super(holder, amounts);
        }
    }
}
