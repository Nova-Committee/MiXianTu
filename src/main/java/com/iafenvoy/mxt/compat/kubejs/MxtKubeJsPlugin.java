package com.iafenvoy.mxt.compat.kubejs;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.compat.kubejs.binding.*;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsCallbacks;
import com.iafenvoy.mxt.event.*;
import com.iafenvoy.mxt.event.AuraZoneEvent.Enter;
import com.iafenvoy.mxt.event.AuraZoneEvent.Leave;
import com.iafenvoy.mxt.event.AuraZoneEvent.Tick;
import com.iafenvoy.mxt.event.ForgingEvent.*;
import com.iafenvoy.mxt.event.FormationEvent.Activate;
import com.iafenvoy.mxt.event.FormationEvent.Deactivate;
import com.iafenvoy.mxt.event.RealmInstanceEvent.EnterPost;
import com.iafenvoy.mxt.event.RealmInstanceEvent.EnterPre;
import com.iafenvoy.mxt.event.RealmInstanceEvent.Exit;
import com.iafenvoy.mxt.event.ResourceConsumeEvent.Post;
import com.iafenvoy.mxt.event.ResourceConsumeEvent.Pre;
import com.iafenvoy.mxt.event.SectEvent.*;
import com.iafenvoy.mxt.event.SoulEvent.ReclaimPost;
import com.iafenvoy.mxt.event.SoulEvent.ReclaimPre;
import com.iafenvoy.mxt.event.SoulEvent.TransferPost;
import com.iafenvoy.mxt.event.SoulEvent.TransferPre;
import com.iafenvoy.mxt.event.TribulationEvent.*;
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

    /**
     * Installs the concrete event listeners while the optional KubeJS plugin is
     * initialized.  Keeping this here makes the lifecycle explicit and avoids
     * registering listeners lazily from script-facing API calls.
     */
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
        forward(AbilityTriggeredEvent.Pre.class, "abilityTriggered");
        forward(AbilityTriggeredEvent.Post.class, "abilityTriggered");
        forward(CurseRemoveEvent.Pre.class, "curseRemove");
        forward(CurseRemoveEvent.Post.class, "curseRemove");
        forward(CultivationBreakEvent.Pre.class, "cultivationBreak");
        forward(CultivationBreakEvent.Post.class, "cultivationBreak");
        forward(TechniqueLearnEvent.Pre.class, "techniqueLearn");
        forward(TechniqueLearnEvent.Post.class, "techniqueLearn");
        forward(AlchemyCraftEvent.Pre.class, "alchemyCraft");
        forward(AlchemyCraftEvent.Post.class, "alchemyCraft");
        forward(ArtifactRefineEvent.Pre.class, "artifactRefine");
        forward(ArtifactRefineEvent.Post.class, "artifactRefine");
        forward(Start.class, "forging");
        forward(Started.class, "forging");
        forward(StrikePre.class, "forging");
        forward(StrikePost.class, "forging");
        forward(CompletePre.class, "forging");
        forward(CompletePost.class, "forging");
        forward(Cancel.class, "forging");
        forward(Activate.class, "formation");
        forward(Deactivate.class, "formation");
        forward(FormationEvent.Tick.class, "formation");
        forward(LifeSpanEndEvent.Pre.class, "lifespanEnd");
        forward(LifeSpanEndEvent.Post.class, "lifespanEnd");
        forward(EnterPre.class, "realmInstance");
        forward(EnterPost.class, "realmInstance");
        forward(Exit.class, "realmInstance");
        forward(JoinPre.class, "sect");
        forward(JoinPost.class, "sect");
        forward(LeavePre.class, "sect");
        forward(LeavePost.class, "sect");
        forward(PromotePre.class, "sect");
        forward(PromotePost.class, "sect");
        forward(TransferPre.class, "soul");
        forward(TransferPost.class, "soul");
        forward(ReclaimPre.class, "soul");
        forward(ReclaimPost.class, "soul");
        forward(SpiritContractEvent.Pre.class, "spiritContract");
        forward(SpiritContractEvent.Post.class, "spiritContract");
        forward(StartPre.class, "tribulation");
        forward(StartPost.class, "tribulation");
        forward(PhasePre.class, "tribulation");
        forward(PhasePost.class, "tribulation");
        forward(Complete.class, "tribulation");
    }

    private static <T extends Event> void forward(Class<T> type, String eventType) {
        NeoForge.EVENT_BUS.addListener(type, event -> MxtKubeJsEvents.post(eventType, event));
    }

    @Override
    public void registerBindings(BindingRegistry registry) {
        registry.add("MxtActions", new MxtKubeJsActionBindings());
        registry.add("MxtConditions", new MxtKubeJsConditionBindings());
        registry.add("MxtValues", new MxtKubeJsValueBindings());
        registry.add("MxtCosts", new MxtKubeJsCostBindings());
        registry.add("MxtAbilities", new MxtKubeJsAbilityBindings());
        registry.add("MxtCultivation", new MxtKubeJsCultivationBindings());
        registry.add("MxtCurses", new MxtKubeJsCurseBindings());
        registry.add("MxtResources", new MxtKubeJsResourceBindings());
        registry.add("MxtAura", new MxtKubeJsAuraBindings());
        registry.add("MxtSouls", new MxtKubeJsSoulBindings());
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
