package com.iafenvoy.mxt.integration.kubejs;

import com.iafenvoy.mxt.event.AbilityUseEvent;
import com.iafenvoy.mxt.event.AbilityUseEvent.Post;
import com.iafenvoy.mxt.event.CurseApplyEvent;
import com.iafenvoy.mxt.event.ResourceConsumeEvent;
import com.iafenvoy.mxt.event.ResourceConsumeEvent.Pre;
import com.iafenvoy.mxt.integration.MxtKubeJsEvents;
import com.iafenvoy.mxt.integration.MxtKubeJsEvents.Dispatcher;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Converts authoritative MXT events into KubeJS server events.
 */
final class MxtKubeJsEventDispatcher implements Dispatcher {
    static final MxtKubeJsEventDispatcher INSTANCE = new MxtKubeJsEventDispatcher();
    static final EventGroup EVENTS = EventGroup.of("MxtEvents");
    private static final EventHandler ABILITY_USE = EVENTS.server("abilityUse", () -> AbilityUseKubeEvent.class);
    private static final EventHandler CURSE_APPLY = EVENTS.server("curseApply", () -> CurseApplyKubeEvent.class);
    private static final EventHandler RESOURCE_CONSUME = EVENTS.server("resourceConsume", () -> ResourceConsumeKubeEvent.class);

    private MxtKubeJsEventDispatcher() {
    }

    @Override
    public void postAbility(AbilityUseEvent event) {
        EventResult result = ABILITY_USE.post(new AbilityUseKubeEvent(event));
        if (event instanceof ICancellableEvent cancellable) result.applyCancel(cancellable);
    }

    @Override
    public void postCurse(CurseApplyEvent event) {
        EventResult result = CURSE_APPLY.post(new CurseApplyKubeEvent(event));
        if (event instanceof ICancellableEvent cancellable) result.applyCancel(cancellable);
    }

    @Override
    public void postResource(ResourceConsumeEvent event) {
        EventResult result = RESOURCE_CONSUME.post(new ResourceConsumeKubeEvent(event));
        if (event instanceof ICancellableEvent cancellable) result.applyCancel(cancellable);
    }

    /**
     * KubeJS view of an ability use; cancellation is honored for pre events.
     */
    public static final class AbilityUseKubeEvent implements KubeEvent {
        private final AbilityUseEvent event;

        AbilityUseKubeEvent(AbilityUseEvent event) {
            this.event = event;
        }

        public Entity getEntity() {
            return this.event.getEntity();
        }

        public String getAbility() {
            return this.event.ability().toString();
        }

        public boolean isPre() {
            return this.event instanceof AbilityUseEvent.Pre;
        }

        public Map<String, Double> getPaidCosts() {
            if (!(this.event instanceof Post post)) return Map.of();
            return post.paidCosts().entrySet().stream().collect(Collectors.toUnmodifiableMap(entry -> entry.getKey().toString(), Entry::getValue));
        }
    }

    /**
     * KubeJS view of curse application; pre listeners can change stacks and source.
     */
    public static final class CurseApplyKubeEvent implements KubeEvent {
        private final CurseApplyEvent event;

        CurseApplyKubeEvent(CurseApplyEvent event) {
            this.event = event;
        }

        public String getCurse() {
            return this.event.curse().toString();
        }

        public boolean isPre() {
            return this.event instanceof CurseApplyEvent.Pre;
        }

        public int getStacks() {
            return this.pre().stacks();
        }

        public void setStacks(int stacks) {
            this.pre().setStacks(stacks);
        }

        public String getSource() {
            return this.pre().source();
        }

        public void setSource(String source) {
            this.pre().setSource(source);
        }

        private CurseApplyEvent.Pre pre() {
            if (this.event instanceof CurseApplyEvent.Pre pre) return pre;
            throw new IllegalStateException("Only curseApply pre events can change curse state");
        }
    }

    /**
     * KubeJS view of a common-resource transaction; only pre listeners may adjust costs.
     */
    public static final class ResourceConsumeKubeEvent implements KubeEvent {
        private final ResourceConsumeEvent event;

        ResourceConsumeKubeEvent(ResourceConsumeEvent event) {
            this.event = event;
        }

        public boolean isPre() {
            return this.event instanceof Pre;
        }

        public Map<String, Double> getAmounts() {
            return this.event.amounts().entrySet().stream().collect(Collectors.toUnmodifiableMap(entry -> entry.getKey().toString(), Entry::getValue));
        }

        public void setAmount(String resource, double amount) {
            Identifier id = Identifier.tryParse(resource);
            if (id == null) throw new IllegalArgumentException("Invalid resource ID: " + resource);
            this.pre().setAmount(id, amount);
        }

        private Pre pre() {
            if (this.event instanceof Pre pre) return pre;
            throw new IllegalStateException("Only resourceConsume pre events can change costs");
        }
    }
}
