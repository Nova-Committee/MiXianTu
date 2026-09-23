package com.iafenvoy.mxt.compat.kubejs;

import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsEvents.Dispatcher;
import com.iafenvoy.mxt.event.*;
import com.iafenvoy.mxt.event.AbilityUseEvent.Post;
import com.iafenvoy.mxt.event.FriendEvent.Relation;
import com.iafenvoy.mxt.event.ResourceConsumeEvent.Pre;
import com.iafenvoy.mxt.util.HolderHelper;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.Locale;
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
    private static final EventHandler AURA_ZONE = EVENTS.server("auraZone", () -> AuraZoneKubeEvent.class);
    private static final EventHandler FRIEND_RELATION = EVENTS.server("friendRelation", () -> FriendRelationKubeEvent.class);
    private static final EventHandler ABILITY_TRIGGERED = EVENTS.server("abilityTriggered", () -> GenericKubeEvent.class);
    private static final EventHandler CURSE_REMOVE = EVENTS.server("curseRemove", () -> GenericKubeEvent.class);
    private static final EventHandler CULTIVATION_BREAK = EVENTS.server("cultivationBreak", () -> GenericKubeEvent.class);
    private static final EventHandler TECHNIQUE_LEARN = EVENTS.server("techniqueLearn", () -> GenericKubeEvent.class);
    private static final EventHandler ALCHEMY_CRAFT = EVENTS.server("alchemyCraft", () -> GenericKubeEvent.class);
    private static final EventHandler ARTIFACT_REFINE = EVENTS.server("artifactRefine", () -> GenericKubeEvent.class);
    private static final EventHandler FORGING = EVENTS.server("forging", () -> GenericKubeEvent.class);
    private static final EventHandler FORMATION = EVENTS.server("formation", () -> GenericKubeEvent.class);
    private static final EventHandler LIFESPAN_END = EVENTS.server("lifespanEnd", () -> GenericKubeEvent.class);
    private static final EventHandler SECRET_REALM = EVENTS.server("secretRealm", () -> GenericKubeEvent.class);
    private static final EventHandler SOUL = EVENTS.server("soul", () -> GenericKubeEvent.class);
    private static final EventHandler SPIRIT_CONTRACT = EVENTS.server("spiritContract", () -> GenericKubeEvent.class);
    private static final EventHandler TRIBULATION = EVENTS.server("tribulation", () -> GenericKubeEvent.class);

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

    @Override
    public void postAura(AuraZoneEvent event) {
        EventResult result = AURA_ZONE.post(new AuraZoneKubeEvent(event));
        if (event instanceof ICancellableEvent cancellable) result.applyCancel(cancellable);
    }

    // A friend judgement is asked far more often than a lifecycle event, so this check replaces a wrapper and
    // an empty handler walk on every query.
    @Override
    public void postFriendRelation(Relation event) {
        if (FRIEND_RELATION.hasListeners()) FRIEND_RELATION.post(new FriendRelationKubeEvent(event));
    }

    @Override
    public void post(String type, Event event) {
        EventHandler handler = switch (type) {
            case "abilityTriggered" -> ABILITY_TRIGGERED;
            case "curseRemove" -> CURSE_REMOVE;
            case "cultivationBreak" -> CULTIVATION_BREAK;
            case "techniqueLearn" -> TECHNIQUE_LEARN;
            case "alchemyCraft" -> ALCHEMY_CRAFT;
            case "artifactRefine" -> ARTIFACT_REFINE;
            case "forging" -> FORGING;
            case "formation" -> FORMATION;
            case "lifespanEnd" -> LIFESPAN_END;
            case "secretRealm" -> SECRET_REALM;
            case "soul" -> SOUL;
            case "spiritContract" -> SPIRIT_CONTRACT;
            case "tribulation" -> TRIBULATION;
            default -> null;
        };
        if (handler == null) return;
        EventResult result = handler.post(new GenericKubeEvent(type, event));
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
            return HolderHelper.id(this.event.ability()).toString();
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
            return HolderHelper.id(this.event.curse()).toString();
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

        // A string so a script can compare and replace it directly.
        public String getSource() {
            return this.pre().source().toString();
        }

        public void setSource(String source) {
            Identifier id = source == null ? null : Identifier.tryParse(source);
            if (id == null) throw new IllegalArgumentException("Invalid curse source identifier: " + source);
            this.pre().setSource(id);
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

    /**
     * Unified view for auraZone enter, leave, tick, and overridable formation coverage.
     */
    public static final class AuraZoneKubeEvent implements KubeEvent {
        private final AuraZoneEvent event;

        AuraZoneKubeEvent(AuraZoneEvent event) {
            this.event = event;
        }

        public String getKind() {
            return this.event.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        }

        public String getSource() {
            return this.event.result().source().toString();
        }

        public double getConcentration() {
            return this.event.result().concentration();
        }

        public boolean isCultivationSuppressed() {
            return this.event.result().suppressCultivate();
        }

        public String getOverrideZone() {
            return this.event instanceof AuraZoneEvent.Override override ? HolderHelper.id(override.zone()).toString() : "";
        }
    }

    /**
     * KubeJS view of a friend judgement. Leaving the event alone leaves the question to the player's own friend
     * list, which is what {@code "default"} reports; the judge is an id first because it is missing while that
     * player is offline.
     */
    public static final class FriendRelationKubeEvent implements KubeEvent {
        private final Relation event;

        FriendRelationKubeEvent(Relation event) {
            this.event = event;
        }

        public String getJudgeId() {
            return this.event.judgeId().toString();
        }

        /**
         * The judge entity, or {@code null} while that player is offline.
         */
        public Entity getJudge() {
            return this.event.judge().orElse(null);
        }

        public boolean hasJudge() {
            return this.event.judge().isPresent();
        }

        public Entity getCandidate() {
            return this.event.candidate();
        }

        /**
         * {@code "true"}, {@code "false"} or {@code "default"}, matching the Java {@link TriState}.
         */
        public String getResult() {
            return this.event.result().getSerializedName();
        }

        public boolean isAnswered() {
            return this.event.answered();
        }

        public void setFriend(boolean friend) {
            this.event.setResult(TriState.from(friend));
        }

        public void abstain() {
            this.event.setResult(TriState.DEFAULT);
        }
    }

    /**
     * Stable common surface for every remaining lifecycle event; the original event stays available for its
     * domain-specific getters and mutable pre-event fields.
     */
    public static final class GenericKubeEvent implements KubeEvent {
        private final String type;
        private final Event event;

        GenericKubeEvent(String type, Event event) {
            this.type = type;
            this.event = event;
        }

        public String getType() {
            return this.type;
        }

        public String getPhase() {
            return this.event.getClass().getSimpleName();
        }

        public boolean isCancellable() {
            return this.event instanceof ICancellableEvent;
        }

        public Event getEvent() {
            return this.event;
        }

        public String getSignalType() {
            return this.event instanceof AbilityTriggeredEvent trigger
                    ? trigger.signalType().toString() : "";
        }

        public String getAbility() {
            return this.event instanceof AbilityTriggeredEvent trigger
                    ? HolderHelper.id(trigger.ability()).toString() : "";
        }

        public Object getContext() {
            return this.event instanceof AbilityTriggeredEvent trigger ? trigger.context() : null;
        }
    }
}
