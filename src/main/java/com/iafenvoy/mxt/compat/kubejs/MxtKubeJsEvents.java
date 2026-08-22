package com.iafenvoy.mxt.compat.kubejs;

import com.iafenvoy.mxt.event.AbilityUseEvent;
import com.iafenvoy.mxt.event.AuraZoneEvent.Enter;
import com.iafenvoy.mxt.event.AuraZoneEvent.Leave;
import com.iafenvoy.mxt.event.AuraZoneEvent.Tick;
import com.iafenvoy.mxt.event.CurseApplyEvent;
import com.iafenvoy.mxt.event.ResourceConsumeEvent;
import com.iafenvoy.mxt.event.AuraZoneEvent;
import com.iafenvoy.mxt.event.ResourceConsumeEvent.Post;
import com.iafenvoy.mxt.event.ResourceConsumeEvent.Pre;
import net.neoforged.neoforge.common.NeoForge;

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
    private static volatile boolean registered;
    private static volatile Dispatcher dispatcher;

    private MxtKubeJsEvents() {
    }

    public static void listenAbility(Consumer<AbilityUseEvent> listener) {
        register();
        ABILITY.add(listener);
    }

    public static void listenCurse(Consumer<CurseApplyEvent> listener) {
        register();
        CURSE.add(listener);
    }

    public static void listenResource(Consumer<ResourceConsumeEvent> listener) {
        register();
        RESOURCE.add(listener);
    }

    public static void listenAura(Consumer<AuraZoneEvent> listener) {
        register();
        AURA.add(listener);
    }

    public static void installDispatcher(Dispatcher value) {
        dispatcher = value;
    }

    public static void register() {
        if (registered) return;
        synchronized (MxtKubeJsEvents.class) {
            if (registered) return;
            // NeoForge does not permit listeners for abstract event parents.
            NeoForge.EVENT_BUS.addListener(AbilityUseEvent.Pre.class, MxtKubeJsEvents::postAbility);
            NeoForge.EVENT_BUS.addListener(AbilityUseEvent.Post.class, MxtKubeJsEvents::postAbility);
            NeoForge.EVENT_BUS.addListener(CurseApplyEvent.Pre.class, MxtKubeJsEvents::postCurse);
            NeoForge.EVENT_BUS.addListener(CurseApplyEvent.Post.class, MxtKubeJsEvents::postCurse);
            NeoForge.EVENT_BUS.addListener(ResourceConsumeEvent.Pre.class, MxtKubeJsEvents::postResource);
            NeoForge.EVENT_BUS.addListener(ResourceConsumeEvent.Post.class, MxtKubeJsEvents::postResource);
            NeoForge.EVENT_BUS.addListener(AuraZoneEvent.Enter.class, MxtKubeJsEvents::postAura);
            NeoForge.EVENT_BUS.addListener(AuraZoneEvent.Leave.class, MxtKubeJsEvents::postAura);
            NeoForge.EVENT_BUS.addListener(AuraZoneEvent.Tick.class, MxtKubeJsEvents::postAura);
            NeoForge.EVENT_BUS.addListener(AuraZoneEvent.Override.class, MxtKubeJsEvents::postAura);
            registered = true;
        }
    }

    private static void postAbility(AbilityUseEvent event) {
        ABILITY.forEach(listener -> listener.accept(event));
        Dispatcher current = dispatcher;
        if (current != null) current.postAbility(event);
    }

    private static void postCurse(CurseApplyEvent event) {
        CURSE.forEach(listener -> listener.accept(event));
        Dispatcher current = dispatcher;
        if (current != null) current.postCurse(event);
    }

    private static void postResource(ResourceConsumeEvent event) {
        RESOURCE.forEach(listener -> listener.accept(event));
        Dispatcher current = dispatcher;
        if (current != null) current.postResource(event);
    }

    private static void postAura(AuraZoneEvent event) {
        AURA.forEach(listener -> listener.accept(event));
        Dispatcher current = dispatcher;
        if (current != null) current.postAura(event);
    }

    public interface Dispatcher {
        void postAbility(AbilityUseEvent event);

        void postCurse(CurseApplyEvent event);

        void postResource(ResourceConsumeEvent event);

        void postAura(AuraZoneEvent event);
    }
}
