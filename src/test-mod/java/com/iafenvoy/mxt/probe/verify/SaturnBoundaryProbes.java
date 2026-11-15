package com.iafenvoy.mxt.probe.verify;

import com.google.common.collect.LinkedHashMultimap;
import com.google.gson.JsonElement;
import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.data.storage.runtime.AuraPulse;
import com.iafenvoy.mxt.data.trigger.MxtTriggerSignals;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.runtime.MxtProfilerSources;
import com.iafenvoy.mxt.runtime.MxtSources;
import com.iafenvoy.mxt.runtime.tribulation.TribulationService;
import com.iafenvoy.saturn.api.AbilityHooks;
import com.iafenvoy.saturn.attachment.AbilityAttachment;
import com.iafenvoy.saturn.data.ability.Ability;
import com.iafenvoy.saturn.data.action.NoOpAction;
import com.iafenvoy.saturn.data.condition.AlwaysCondition;
import com.iafenvoy.saturn.data.storage.runtime.IdleCountdown;
import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.saturn.data.timeline.TimelineContext;
import com.iafenvoy.saturn.data.timeline.TimelineJump;
import com.iafenvoy.saturn.data.timeline.TimelineState;
import com.iafenvoy.saturn.data.timeline.builtin.IdleEntry;
import com.iafenvoy.saturn.event.CollectAbilityGrantsEvent;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.iafenvoy.saturn.runtime.Sources;
import com.iafenvoy.saturn.runtime.ability.AbilityGrantService;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.saturn.util.formula.number.Constant;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Set;

public final class SaturnBoundaryProbes {
    private SaturnBoundaryProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/saturn/grants", SaturnBoundaryProbes::grants),
                Probe.of("verify/saturn/defaults", SaturnBoundaryProbes::defaults),
                Probe.of("verify/saturn/hooks", SaturnBoundaryProbes::hooks),
                Probe.of("verify/saturn/timeline", SaturnBoundaryProbes::timeline),
                Probe.of("verify/saturn/ownership", SaturnBoundaryProbes::ownership)
        );
    }

    private static Holder<Ability> ability(ProbeContext context) {
        return context.require(SaturnResourceKeys.ABILITY, ProbeDefinitions.id("firebolt"));
    }

    private static void grants(ProbeContext context) {
        Holder<Ability> ability = ability(context);
        Identifier first = Identifier.parse("first:profession");
        Identifier second = Identifier.parse("second:quest");
        Identifier manual = Identifier.parse("third:item");
        AbilityAttachment state = new AbilityAttachment();
        CollectAbilityGrantsEvent event = new CollectAbilityGrantsEvent(context.player());
        event.want(first, ability);
        AbilityGrantService.Result result = AbilityGrantService.reconcile(state, event.getDesired());
        leg(context, "external source granted", result.granted() == 1 && state.managedSources().contains(first));
        state.storage().set(ability, new IdleCountdown(17), 0);
        LinkedHashMultimap<Identifier, Holder<Ability>> desired = LinkedHashMultimap.<Identifier, Holder<Ability>>create();
        desired.put(second, ability);
        result = AbilityGrantService.reconcile(state, desired);
        leg(context, "source handoff", result.granted() == 1 && result.revoked() == 1
                && state.storage().get(ability, IdleCountdown.class).orElseThrow().remaining() == 17);
        state.grant(ability, manual);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, context.registries());
        state = AbilityAttachment.CODEC.codec().parse(ops, AbilityAttachment.CODEC.codec().encodeStart(ops, state).getOrThrow()).getOrThrow();
        leg(context, "ownership saved", state.managedSources().equals(Set.of(second)));
        leg(context, "ownership copied", state.copy().managedSources().equals(Set.of(second)));
        desired.clear();
        result = AbilityGrantService.reconcile(state, desired);
        leg(context, "withdrawal after reload", result.revoked() == 1 && state.sources().keysHeldBy(second).isEmpty());
        leg(context, "manual source retained", state.has(ability) && state.sources().keysHeldBy(manual).contains(ability)
                && state.storage().get(ability, IdleCountdown.class).isPresent());
        leg(context, "idempotent", AbilityGrantService.reconcile(state, desired).revoked() == 0);
        state.revoke(ability, manual);
        Identifier legacy = Sources.granted(new Sources.Grant("legacy"), ProbeDefinitions.id("definition"));
        state.grant(ability, legacy);
        leg(context, "legacy withdrawal", AbilityGrantService.reconcile(state, desired).revoked() == 1 && !state.has(ability));
    }

    private static void defaults(ProbeContext context) {
        int count = 0;
        for (Registry<?> registry : SaturnRegistries.all()) {
            if (!(registry instanceof DefaultedRegistry<?> defaults)) continue;
            count++;
            Identifier key = defaults.getDefaultKey();
            Object value = defaults.getOptional(key).orElseThrow();
            leg(context, "default " + registry.key().identifier(), key.getNamespace().equals("saturn")
                    && defaults.getValue(Identifier.parse("mxt_test:absent_default_probe")) == value
                    && defaults.byId(Integer.MAX_VALUE) == value);
        }
        leg(context, "all defaulted registries", count == 24);
    }

    private static void hooks(ProbeContext context) {
        Identifier first = ProbeDefinitions.id("hooks_first");
        Identifier second = ProbeDefinitions.id("hooks_second");
        ItemStack shared = new ItemStack(Items.STICK);
        ItemStack separate = new ItemStack(Items.STICK);
        Holder<Ability> ability = ability(context);
        try {
            AbilityHooks.register(first, new AbilityHooks() {
                @Override
                public List<Holder<Ability>> itemAbilities(LivingEntity entity, ItemStack stack) { return List.of(ability); }

                @Override
                public List<ItemStack> equippedItems(LivingEntity entity) { return List.of(shared, separate); }
            });
            AbilityHooks.register(second, new AbilityHooks() {
                @Override
                public List<Holder<Ability>> itemAbilities(LivingEntity entity, ItemStack stack) { return List.of(ability); }

                @Override
                public List<ItemStack> equippedItems(LivingEntity entity) { return List.of(shared); }
            });
            leg(context, "ability union", AbilityHooks.get().itemAbilities(context.player(), shared).stream().filter(ability::equals).count() == 1);
            List<ItemStack> equipment = AbilityHooks.get().equippedItems(context.player());
            leg(context, "equipment identity", equipment.stream().filter(stack -> stack == shared).count() == 1
                    && equipment.stream().filter(stack -> stack == separate).count() == 1);
            AbilityHooks.register(first, null);
            leg(context, "independent provider removal", AbilityHooks.get().equippedItems(context.player()).stream().anyMatch(stack -> stack == shared));
        } finally {
            AbilityHooks.register(first, null);
            AbilityHooks.register(second, null);
        }
    }

    private static void timeline(ProbeContext context) {
        FormulaContext formula = FormulaContext.of(context.player()).with("aura_tribulation_modifier", 0.5);
        Tribulation definition = new Tribulation(AlwaysCondition.INSTANCE, List.of(new IdleEntry(new Constant(20))),
                new Constant(2), new Constant(10), false, NoOpAction.INSTANCE, NoOpAction.INSTANCE);
        double scale = TribulationService.effectiveScale(definition, formula);
        leg(context, "tribulation effective scale", Math.abs(scale - 3) < 1.0E-9);
        TimelineContext host = new TimelineContext(context.player(), formula, 0, scale, 1, new TimelineState(), new TimelineJump());
        leg(context, "host applies multiplier once", host.ticks(new Constant(20)) == 60 && host.ticks(definition.windup()) == 30);
        leg(context, "deadline unscaled", host.unscaledTicks(new Constant(20)) == 20);
        TimelineContext generic = new TimelineContext(context.player(), formula, 0, 1, 1, new TimelineState(), new TimelineJump());
        leg(context, "generic ignores host variable", generic.ticks(new Constant(20)) == 20);
    }

    private static void ownership(ProbeContext context) {
        Identifier content = ProbeDefinitions.id("content");
        leg(context, "legacy grant id", Sources.granted(MxtSources.Grant.SPIRIT_ROOT, content).equals(Identifier.parse("saturn:grant/spirit_root/mxt_test/content")));
        leg(context, "legacy formation id", MxtSources.formation(content).equals(Identifier.parse("saturn:formation/mxt_test/content")));
        leg(context, "legacy signal ids", MxtTriggerSignals.BREAKTHROUGH.equals(Identifier.parse("saturn:breakthrough"))
                && MxtTriggerSignals.PROGRESSION_LEVEL.equals(Identifier.parse("saturn:progression_level")));
        leg(context, "profiler ownership", MxtProfilerSources.ITEM.key().equals(Identifier.parse("mxt:item"))
                && MxtProfilerSources.ENTITY.key().equals(Identifier.parse("mxt:entity"))
                && !SaturnRegistries.PROFILER_SOURCE.containsKey(Identifier.parse("saturn:item"))
                && !SaturnRegistries.PROFILER_SOURCE.containsKey(Identifier.parse("saturn:entity")));
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, context.registries());
        JsonElement saved = DataStorage.CODEC.encodeStart(ops, new AuraPulse(23)).getOrThrow();
        DataStorage restored = DataStorage.CODEC.parse(ops, saved).getOrThrow();
        leg(context, "legacy aura storage", restored instanceof AuraPulse pulse && pulse.nextTick() == 23
                && saved.getAsJsonObject().get("type").getAsString().equals("mxt:aura_pulse"));
    }

    private static void leg(ProbeContext context, String name, boolean success) {
        context.check(name, success);
        context.note("saturn " + name + ": " + (success ? "OK" : "MISMATCH"));
    }
}
