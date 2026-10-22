package com.iafenvoy.mxt.probe.ability;

import com.iafenvoy.mxt.data.action.builtin.SendMessageAction;
import com.iafenvoy.mxt.data.formation.FormationIntervals;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

import static com.iafenvoy.mxt.data.formation.FormationIntervals.due;

/**
 * The send_message action where it is dispatched and throttled: the recipients it resolves to on every axis, the
 * formation module's interval gate, and the condition tree a pack writes for the recipients.
 */
public final class MessageProbes {
    private MessageProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("ability/message/dispatch_axes", MessageProbes::dispatchesOnEveryAxis),
                Probe.of("ability/message/interval_gate", MessageProbes::intervalGate));
    }

    // Dispatching is where the axes actually differ. The sub-case worth pinning is the one that is easy to get
    // wrong: a named addressee that is not a player must resolve to nobody rather than to the whole level.
    private static void dispatchesOnEveryAxis(ProbeContext context) {
        ServerLevel level = context.level();
        ServerPlayer player = context.player();
        Cow cow = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
        Entity nonPlayer = cow == null ? new ItemEntity(level, 0.0D, 0.0D, 0.0D, new ItemStack(Items.STONE)) : cow;
        // SELF and OTHER name an entity, so a non-player addressee resolves to nobody: the send must do nothing.
        context.check("message probe: SELF on a non-player actor resolves to nobody", SendMessageAction.MessageTarget.SELF.player(nonPlayer, null).isEmpty());
        context.check("message probe: OTHER on a non-player addressee resolves to nobody", SendMessageAction.MessageTarget.OTHER.player(player, nonPlayer).isEmpty());
        context.expect("message probe: SELF on a player resolves to that player", SendMessageAction.MessageTarget.SELF.player(player, null).orElse(null), player);
        context.expect("message probe: OTHER on a player resolves to that player", SendMessageAction.MessageTarget.OTHER.player(nonPlayer, player).orElse(null), player);
        // ALL never names an entity, which is exactly what lets a block action (which has no entity) use it.
        context.check("message probe: ALL names nobody", SendMessageAction.MessageTarget.ALL.player(player, player).isEmpty());
        if (cow != null) cow.discard();
    }

    // The throttle is the one part of the interval field that is pure logic, so it is pinned directly: the phase
    // comes from the level clock, which makes two modules share a beat and a module stateless across a reload.
    private static void intervalGate(ProbeContext context) {
        context.check("message probe: an interval of one beat is due on every beat", due(10, 0L) && due(10, 10L) && due(10, 20L));
        context.check("message probe: a 30-tick interval is due every third beat", due(30, 0L) && !due(30, 10L) && !due(30, 20L) && due(30, 30L));
        // Two definitions with the same interval must agree on the phase rather than drift apart: the gate reads
        // the clock and nothing else, so the same beat gives the same answer however often it is asked.
        context.check("message probe: the phase is shared rather than counted per module", due(30, 60L) && !due(30, 70L));
        // The gate is a modulo on the world clock, so a late clock agrees with an early one at the same phase.
        context.check("message probe: the gate agrees with itself on a late clock", due(30, 600L) && !due(30, 610L));
        // A tick count that is not a whole number of beats can never be honoured by a pass that only runs on the beat
        // grid, so it is refused rather than silently rounded onto it.
        context.check("message probe: an interval off the beat grid is refused", !FormationIntervals.beatDue(15L));
    }
}
