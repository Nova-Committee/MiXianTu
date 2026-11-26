package com.iafenvoy.mxt.compat.kubejs;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.compat.kubejs.binding.*;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsCallbacks;
import com.iafenvoy.mxt.event.*;
import com.iafenvoy.mxt.event.AuraZoneEvent.Enter;
import com.iafenvoy.mxt.event.AuraZoneEvent.Leave;
import com.iafenvoy.mxt.event.AuraZoneEvent.Tick;
import com.iafenvoy.mxt.event.FormationEvent.Activate;
import com.iafenvoy.mxt.event.FormationEvent.Deactivate;
import com.iafenvoy.mxt.event.FormationEvent.TickEffects;
import com.iafenvoy.mxt.event.FormationEvent.UpkeepFailed;
import com.iafenvoy.mxt.event.FriendEvent.Relation;
import com.iafenvoy.mxt.event.SecretRealmEvent.*;
import com.iafenvoy.mxt.event.SoulEvent.ReclaimPost;
import com.iafenvoy.mxt.event.SoulEvent.ReclaimPre;
import com.iafenvoy.mxt.event.SoulEvent.TransferPost;
import com.iafenvoy.mxt.event.SoulEvent.TransferPre;
import com.iafenvoy.mxt.event.TribulationEvent.*;
import com.iafenvoy.saturn.compat.kubejs.binding.*;
import com.iafenvoy.saturn.event.AbilityTriggeredEvent;
import com.iafenvoy.saturn.event.AbilityUseEvent;
import com.iafenvoy.saturn.event.ResourceConsumeEvent.Post;
import com.iafenvoy.saturn.event.ResourceConsumeEvent.Pre;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/**
 * KubeJS entry point, discovered through {@code kubejs.plugins.txt}.
 */
public final class MxtKubeJsPlugin implements KubeJSPlugin {
    @Override
    public void init() {
        MxtKubeJsEvents.installDispatcher(MxtKubeJsEventDispatcher.INSTANCE);
        registerEventForwarders();
        MiXianTu.LOGGER.info("MXT KubeJS bridge initialized");
    }

    private static void registerEventForwarders() {
        // NeoForge does not permit listeners for abstract event parents.
        NeoForge.EVENT_BUS.addListener(AbilityUseEvent.Pre.class, MxtKubeJsEvents::postAbility);
        NeoForge.EVENT_BUS.addListener(AbilityUseEvent.Post.class, MxtKubeJsEvents::postAbility);
        NeoForge.EVENT_BUS.addListener(CurseApplyEvent.Pre.class, MxtKubeJsEvents::postCurse);
        NeoForge.EVENT_BUS.addListener(CurseApplyEvent.Post.class, MxtKubeJsEvents::postCurse);
        NeoForge.EVENT_BUS.addListener(Pre.class, MxtKubeJsEvents::postResource);
        NeoForge.EVENT_BUS.addListener(Post.class, MxtKubeJsEvents::postResource);
        NeoForge.EVENT_BUS.addListener(Enter.class, MxtKubeJsEvents::postAura);
        NeoForge.EVENT_BUS.addListener(Leave.class, MxtKubeJsEvents::postAura);
        NeoForge.EVENT_BUS.addListener(Tick.class, MxtKubeJsEvents::postAura);
        NeoForge.EVENT_BUS.addListener(AuraZoneEvent.Override.class, MxtKubeJsEvents::postAura);
        // A judgement rather than a lifecycle notification, so it goes through its own entry point: see
        // MxtKubeJsEventDispatcher#postFriendRelation for why it is not on the generic forwarder.
        NeoForge.EVENT_BUS.addListener(Relation.class, MxtKubeJsEvents::postFriendRelation);
        forward(AbilityTriggeredEvent.Pre.class, "abilityTriggered", "Pre");
        forward(AbilityTriggeredEvent.Post.class, "abilityTriggered", "Post");
        forward(CurseRemoveEvent.Pre.class, "curseRemove", "Pre");
        forward(CurseRemoveEvent.Post.class, "curseRemove", "Post");
        forward(CultivationBreakEvent.Pre.class, "cultivationBreak", "Pre");
        forward(CultivationBreakEvent.Post.class, "cultivationBreak", "Post");
        forward(TechniqueLearnEvent.Pre.class, "techniqueLearn", "Pre");
        forward(TechniqueLearnEvent.Post.class, "techniqueLearn", "Post");
        forward(ArtifactRefineEvent.Pre.class, "artifactRefine", "Pre");
        forward(ArtifactRefineEvent.Post.class, "artifactRefine", "Post");
        forward(Activate.class, "formation", "Activate");
        forward(Deactivate.class, "formation", "Deactivate");
        // All three share one script group and are told apart by isCancellable()/getPhase(): Tick is the
        // settled observer, while TickEffects and UpkeepFailed are the cancellable two.
        forward(FormationEvent.Tick.class, "formation", "Tick");
        forward(TickEffects.class, "formation", "TickEffects");
        forward(UpkeepFailed.class, "formation", "UpkeepFailed");
        forward(EnterPre.class, "secretRealm", "EnterPre");
        forward(EnterPost.class, "secretRealm", "EnterPost");
        forward(Exit.class, "secretRealm", "Exit");
        forward(Create.class, "secretRealm", "Create");
        forward(Destroy.class, "secretRealm", "Destroy");
        forward(TransferPre.class, "soul", "TransferPre");
        forward(TransferPost.class, "soul", "TransferPost");
        forward(ReclaimPre.class, "soul", "ReclaimPre");
        forward(ReclaimPost.class, "soul", "ReclaimPost");
        forward(SpiritContractEvent.Pre.class, "spiritContract", "Pre");
        forward(SpiritContractEvent.Post.class, "spiritContract", "Post");
        forward(StartPre.class, "tribulation", "StartPre");
        forward(StartPost.class, "tribulation", "StartPost");
        forward(EntryPre.class, "tribulation", "EntryPre");
        forward(EntryPost.class, "tribulation", "EntryPost");
        forward(Complete.class, "tribulation", "Complete");
    }

    private static <T extends Event> void forward(Class<T> type, String eventType, String phase) {
        NeoForge.EVENT_BUS.addListener(type, event -> MxtKubeJsEvents.post(eventType, phase, event));
    }

    @Override
    public void registerBindings(BindingRegistry registry) {
        registry.add("MxtActions", new SaturnKubeJsActionBindings());
        registry.add("MxtConditions", new SaturnKubeJsConditionBindings());
        registry.add("MxtValues", new SaturnKubeJsValueBindings());
        registry.add("MxtCosts", new SaturnKubeJsCostBindings());
        registry.add("MxtAbilities", new MxtKubeJsAbilityBindings());
        registry.add("MxtCultivation", new MxtKubeJsCultivationBindings());
        registry.add("MxtCurses", new MxtKubeJsCurseBindings());
        registry.add("MxtResources", new SaturnKubeJsResourceBindings());
        registry.add("MxtAura", new MxtKubeJsAuraBindings(registry.type().isClient()));
        registry.add("MxtElements", new MxtKubeJsElementBindings());
        registry.add("MxtSpiritRoots", new MxtKubeJsSpiritRootBindings());
        registry.add("MxtPhysiques", new MxtKubeJsPhysiqueBindings());
        registry.add("MxtTechniques", new MxtKubeJsTechniqueBindings());
        registry.add("MxtProgression", new MxtKubeJsProgressionBindings());
        registry.add("MxtQuality", new MxtKubeJsQualityBindings());
        registry.add("MxtSouls", new MxtKubeJsSoulBindings());
        registry.add("MxtTriggers", new MxtKubeJsTriggerBindings());
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(MxtKubeJsEventDispatcher.EVENTS);
    }

    @Override
    public void beforeScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType == ScriptType.SERVER) MxtJsCallbacks.clear();
    }
}
