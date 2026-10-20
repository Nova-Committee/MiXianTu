package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.formation.Formation;
import com.iafenvoy.mxt.data.trigger.Trigger;
import com.iafenvoy.mxt.data.trigger.TriggerContext;
import com.iafenvoy.mxt.data.trigger.TriggerSignal;
import com.iafenvoy.mxt.data.trigger.TriggerSignals;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.Sources;
import com.iafenvoy.mxt.runtime.formation.FormationWorldService;
import com.iafenvoy.mxt.runtime.formation.FormationWorldTicker;
import com.iafenvoy.mxt.runtime.trigger.TriggerDispatcher;
import com.iafenvoy.mxt.runtime.trigger.TriggerSubscription;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * The two scoping bugs the audit found in the shared-runtime modules: a grant whose identity is shared by every
 * instance of a definition, and a re-entrancy guard whose key was shared by every owner.
 *
 * <p>Both are about a key that was too coarse, so both legs run two entities or two instances through the real
 * dispatcher and read which one actually kept what.
 */
public final class ScopeVerifyProbes {
    private ScopeVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/formation_shared_source", ScopeVerifyProbes::sharedSourceHolds),
                Probe.of("verify/trigger_reentry_owner", ScopeVerifyProbes::reentryIsPerOwner)
        );
    }

    // One source per definition, so walking out of one instance must not take away what an overlapping sibling still
    // owes. The two arrays of one definition overlap, the player stands in both, then steps into one of them only.
    private static void sharedSourceHolds(ProbeContext context) {
        ServerLevel level = context.level();
        ResourceHolderAttachment resources = context.player().getData(MxtAttachments.RESOURCE_HOLDER);
        FormulaContext formula = FormulaContext.of(context.player());
        UUID owner = context.player().getUUID();
        // The fixture declares no structure, so both arrays can be raised on the arena floor.
        Formation definition;
        try {
            definition = context.require(MxtResourceKeys.FORMATION, ProbeDefinitions.id("scope_grant_probe")).value();
        } catch (RuntimeException exception) {
            context.fail("the grant fixture does not resolve: " + exception.getMessage());
            return;
        }
        // Ten blocks apart with a radius of eight: a band in the middle is inside both, and each centre is inside one.
        BlockPos left = context.playerSpot();
        BlockPos right = left.offset(10, 0, 0);
        if (!FormationWorldService.activate(level, left, ProbeDefinitions.id("scope_grant_probe"), definition, resources,
                formula, owner).isSuccess()) {
            context.fail("the left array did not go up");
            return;
        }
        if (!FormationWorldService.activate(level, right, ProbeDefinitions.id("scope_grant_probe"), definition, resources,
                formula, owner).isSuccess()) {
            FormationWorldService.deactivate(level, left);
            context.fail("the right array did not go up");
            return;
        }
        ServerPlayer player = context.player();
        Identifier source = Sources.formation(ProbeDefinitions.id("scope_grant_probe"));
        try {
            // Inside both, so both arrays track the player; the source is one, so it is granted once either way.
            player.setPos(left.getX() + 5.5D, left.getY(), left.getZ() + 0.5D);
            FormationWorldTicker.dispatch(level, 20L);
            context.check("both arrays together granted the shared source",
                    player.getData(MxtAttachments.ABILITY_HOLDER).sources().heldBy(source));
            // Now inside the right one only: the left array sees the player leave while the right still covers the spot.
            // This is the leg - the source is keyed by definition, so the release must ask whether anything of that
            // definition still reaches the player before taking the grant away.
            player.setPos(right.getX() + 0.5D, right.getY(), right.getZ() + 0.5D);
            FormationWorldTicker.dispatch(level, 30L);
            context.check("walking out of one instance keeps the grant the sibling of the same definition still owes",
                    player.getData(MxtAttachments.ABILITY_HOLDER).sources().heldBy(source));
            // Out of both: nothing covers the player any more, so the shared source goes.
            player.setPos(right.getX() + 40.5D, right.getY(), right.getZ() + 0.5D);
            FormationWorldTicker.dispatch(level, 40L);
            context.check("leaving every instance of the definition releases the grant",
                    !player.getData(MxtAttachments.ABILITY_HOLDER).sources().heldBy(source));
        } finally {
            FormationWorldService.deactivate(level, left);
            FormationWorldService.deactivate(level, right);
        }
    }

    // A re-entry guard keyed by module and identity alone would treat a second owner's identical subscription as the
    // first one re-entering itself. The listener below publishes to the other owner from inside its own dispatch.
    private static void reentryIsPerOwner(ProbeContext context) {
        ServerPlayer first = context.player();
        ServerPlayer second = context.joinBeside(first, 2.0D, 0.0D);
        Trigger trigger = trigger(context);
        if (trigger == null) {
            context.fail("the tick trigger does not resolve");
            return;
        }
        List<String> fired = new ArrayList<>();
        UUID firstId = first.getUUID(), secondId = second.getUUID();
        try {
            // Both owners hold the same module and identity, which is what makes the keys collide.
            TriggerDispatcher.register(new TriggerSubscription(firstId, "scope_probe", "same", trigger, signal -> true,
                    signal -> {
                        fired.add("first");
                        // A nested publish out of the first owner's dispatch, addressed at the second owner.
                        TriggerDispatcher.publishTo(secondId, signal);
                    }, false));
            TriggerDispatcher.register(new TriggerSubscription(secondId, "scope_probe", "same", trigger, signal -> true,
                    signal -> fired.add("second"), false));
            TriggerDispatcher.publishTo(firstId, new TriggerSignal(TriggerSignals.TICK, context(second, first), null, 0L));
            context.expect("the second owner's identical subscription runs while the first one is dispatching",
                    fired, List.of("first", "second"));
        } finally {
            TriggerDispatcher.clearOwner(firstId);
            TriggerDispatcher.clearOwner(secondId);
        }
        // The guard still stops an owner from re-entering itself, which is what it is for.
        List<String> reentered = new ArrayList<>();
        try {
            TriggerDispatcher.register(new TriggerSubscription(firstId, "scope_probe", "loop", trigger, signal -> true,
                    signal -> {
                        reentered.add("once");
                        TriggerDispatcher.publishTo(firstId, signal);
                    }, false));
            TriggerDispatcher.publishTo(firstId, new TriggerSignal(TriggerSignals.TICK, context(first, second), null, 0L));
            context.expect("an owner that publishes its own signal again runs once", reentered, List.of("once"));
        } finally {
            TriggerDispatcher.clearOwner(firstId);
        }
    }

    // The ticker reads the presence set from the previous beat, so a first beat only ever finds everything newly
    // arrived. Each call is a whole period so the tick-beat modules run.
    private static void tick(ServerLevel level, long from) {
        for (long time = from; time <= from + 20L; time += 10L) FormationWorldTicker.dispatch(level, time);
    }

    private static Trigger trigger(ProbeContext context) {
        MapCodec<? extends Trigger> codec = MxtRegistries.TRIGGER_TYPE.getValue(TriggerSignals.TICK);
        if (codec == null) return null;
        return codec.decoder().parse(RegistryOps.create(JsonOps.INSTANCE, context.registries()),
                JsonOps.INSTANCE.createMap(new LinkedHashMap<>())).result().orElse(null);
    }

    private static TriggerContext context(ServerPlayer actor, ServerPlayer target) {
        return new TriggerContext().level(actor.level()).actor(actor).target(target)
                .formula(FormulaContext.of(actor));
    }
}
