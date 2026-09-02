package com.iafenvoy.mxt.compat.kubejs;

import com.iafenvoy.mxt.event.AbilityUseEvent;
import com.iafenvoy.mxt.event.CurseApplyEvent;
import com.iafenvoy.mxt.event.ResourceConsumeEvent;
import com.iafenvoy.mxt.event.AuraZoneEvent;
import net.neoforged.bus.api.Event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Event forwarding hook. The optional KubeJS adapter installs itself at runtime.
 */
public final class MxtKubeJsEvents {
    private static final List<Consumer<AbilityUseEvent>> ABILITY = new CopyOnWriteArrayList<>();
    private static final List<Consumer<CurseApplyEvent>> CURSE = new CopyOnWriteArrayList<>();
    private static final List<Consumer<ResourceConsumeEvent>> RESOURCE = new CopyOnWriteArrayList<>();
    private static final List<Consumer<AuraZoneEvent>> AURA = new CopyOnWriteArrayList<>();
    private static volatile Dispatcher dispatcher;

    private MxtKubeJsEvents() {
    }

    public static void listenAbility(Consumer<AbilityUseEvent> listener) {
        ABILITY.add(listener);
    }

    public static void listenCurse(Consumer<CurseApplyEvent> listener) {
        CURSE.add(listener);
    }

    public static void listenResource(Consumer<ResourceConsumeEvent> listener) {
        RESOURCE.add(listener);
    }

    public static void listenAura(Consumer<AuraZoneEvent> listener) {
        AURA.add(listener);
    }

    public static void installDispatcher(Dispatcher value) {
        dispatcher = value;
    }

    static void postAbility(AbilityUseEvent event) {
        ABILITY.forEach(listener -> listener.accept(event));
        Dispatcher current = dispatcher;
        if (current != null) current.postAbility(event);
    }

    static void postCurse(CurseApplyEvent event) {
        CURSE.forEach(listener -> listener.accept(event));
        Dispatcher current = dispatcher;
        if (current != null) current.postCurse(event);
    }

    static void postResource(ResourceConsumeEvent event) {
        RESOURCE.forEach(listener -> listener.accept(event));
        Dispatcher current = dispatcher;
        if (current != null) current.postResource(event);
    }

    static void postAura(AuraZoneEvent event) {
        AURA.forEach(listener -> listener.accept(event));
        Dispatcher current = dispatcher;
        if (current != null) current.postAura(event);
    }

    /** Forwards a published MXT lifecycle event that has no specialised Java listener API. */
    public static void post(String type, Event event) {
        Dispatcher current = dispatcher;
        if (current != null) current.post(type, event);
    }

    public interface Dispatcher {
        void postAbility(AbilityUseEvent event);

        void postCurse(CurseApplyEvent event);

        void postResource(ResourceConsumeEvent event);

        void postAura(AuraZoneEvent event);

        void post(String type, Event event);
    }
}
