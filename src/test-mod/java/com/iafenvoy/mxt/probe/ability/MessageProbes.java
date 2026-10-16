package com.iafenvoy.mxt.probe.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.MessageChannel;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.builtin.SendMessageAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.iafenvoy.mxt.data.context.action.BlockActionContext;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.context.action.ItemActionContext;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.formation.FormationIntervals;
import com.iafenvoy.mxt.data.formation.builtin.SendMessageFormationAction;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.runtime.formation.FormationWorldTicker;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
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

/**
 * The send_message action on every axis: body shape, channel default, recipients, the registries it resolves through,
 * and the formation module with its interval gate. A failing leg reports the actual and the expected value.
 */
public final class MessageProbes {
    private MessageProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("ability/message/translation_key", MessageProbes::translationKeyBody),
                Probe.of("ability/message/component", MessageProbes::componentBody),
                Probe.of("ability/message/channel_default", MessageProbes::channelDefault),
                Probe.of("ability/message/channel_selection", MessageProbes::channelSelection),
                Probe.of("ability/message/recipient_condition", MessageProbes::recipientCondition),
                Probe.of("ability/message/registered_axes", MessageProbes::registeredOnEveryAxis),
                Probe.of("ability/message/dispatch_axes", MessageProbes::dispatchesOnEveryAxis),
                Probe.of("ability/message/formation_module", MessageProbes::formationModuleRegistered),
                Probe.of("ability/message/interval_gate", MessageProbes::intervalGate));
    }

    // A bare string is a translation key, which is what a pack with its own lang writes.
    private static void translationKeyBody(ProbeContext context) {
        SendMessageAction action = decode(context, "{\"type\":\"mxt:send_message\",\"message\":\"mxt_test.probe.key\"}");
        context.expect("message probe: a bare string decodes as a translation key",
                action == null ? null : action.message().getString(), "mxt_test.probe.key");
    }

    // An object is a full component, so styling survives.
    private static void componentBody(ProbeContext context) {
        SendMessageAction action = decode(context, "{\"type\":\"mxt:send_message\",\"message\":{\"text\":\"literal body\"}}");
        context.expect("message probe: an object decodes as a literal component",
                action == null ? null : action.message().getString(), "literal body");
    }

    // Chat is the default: a pack that never asked for the action bar must not overwrite it.
    private static void channelDefault(ProbeContext context) {
        SendMessageAction action = decode(context, "{\"type\":\"mxt:send_message\",\"message\":\"x\"}");
        context.expect("message probe: channel defaults to chat",
                action == null ? null : action.channel(), MessageChannel.CHAT);
        // The flag handed to sendSystemMessage has to agree with the enum it came from.
        context.check("message probe: chat maps to the chat flag", !MessageChannel.CHAT.actionBar());
    }

    private static void channelSelection(ProbeContext context) {
        // MessageChannel is StringRepresentable over its own lowercased name, so a pack writes `action_bar`; the
        // command probe spelled it `actionbar` and was never run, so it always decoded to null.
        SendMessageAction action = decode(context, "{\"type\":\"mxt:send_message\",\"message\":\"x\",\"channel\":\"action_bar\"}");
        context.expect("message probe: actionbar is selectable",
                action == null ? null : action.channel(), MessageChannel.ACTION_BAR);
        context.check("message probe: actionbar maps to the action bar flag",
                action != null && action.channel().actionBar());
    }

    // The recipient filter is a real condition tree, and an absent one must not filter anybody out.
    private static void recipientCondition(ProbeContext context) {
        SendMessageAction plain = decode(context, "{\"type\":\"mxt:send_message\",\"message\":\"x\"}");
        context.check("message probe: an absent recipient condition passes everyone",
                plain != null && plain.recipients().test(context.player(), FormulaContext.EMPTY));
        // A condition the registry does not know must fail the whole decode rather than silently dropping the field.
        SendMessageAction bogus = decode(context, "{\"type\":\"mxt:send_message\",\"message\":\"x\",\"recipients\":{\"type\":\"mxt:no_such_condition\"}}");
        context.check("message probe: an unknown recipient condition type is rejected", bogus == null);
        SendMessageAction sneaking = decode(context, "{\"type\":\"mxt:send_message\",\"message\":\"x\",\"recipients\":{\"type\":\"mxt:sneaking\"}}");
        context.check("message probe: a known recipient condition type decodes", sneaking != null);
    }

    // One class is registered under all four action ids, so every axis can write the same type id.
    private static void registeredOnEveryAxis(ProbeContext context) {
        context.check("message probe: entity axis has send_message", MxtRegistries.ENTITY_ACTION_TYPE.containsKey(id()));
        context.check("message probe: block axis has send_message", MxtRegistries.BLOCK_ACTION_TYPE.containsKey(id()));
        context.check("message probe: item axis has send_message", MxtRegistries.ITEM_ACTION_TYPE.containsKey(id()));
        context.check("message probe: bientity axis has send_message", MxtRegistries.BI_ENTITY_ACTION_TYPE.containsKey(id()));
    }

    // Dispatching is where the axes actually differ. The sub-case worth pinning is the one that is easy to get
    // wrong: a named addressee that is not a player must resolve to nobody rather than to the whole level.
    private static void dispatchesOnEveryAxis(ProbeContext context) {
        ServerLevel level = context.level();
        ServerPlayer player = context.player();
        Cow cow = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
        Entity nonPlayer = cow == null ? new ItemEntity(level, 0.0D, 0.0D, 0.0D, new ItemStack(Items.STONE)) : cow;
        // SELF and OTHER name an entity, so a non-player addressee resolves to nobody: the send must do nothing.
        context.check("message probe: SELF on a non-player actor resolves to nobody",
                SendMessageAction.MessageTarget.SELF.player(nonPlayer, null).isEmpty());
        context.check("message probe: OTHER on a non-player addressee resolves to nobody",
                SendMessageAction.MessageTarget.OTHER.player(player, nonPlayer).isEmpty());
        context.expect("message probe: SELF on a player resolves to that player",
                SendMessageAction.MessageTarget.SELF.player(player, null).orElse(null), player);
        context.expect("message probe: OTHER on a player resolves to that player",
                SendMessageAction.MessageTarget.OTHER.player(nonPlayer, player).orElse(null), player);
        // ALL never names an entity, which is exactly what lets a block action (which has no entity) use it.
        context.check("message probe: ALL names nobody",
                SendMessageAction.MessageTarget.ALL.player(player, player).isEmpty());
        // Every axis must at least run its dispatch without throwing. Building the action first keeps a decode
        // failure from being reported as a dispatch failure.
        SendMessageAction entity = decode(context, "{\"type\":\"mxt:send_message\",\"message\":\"x\"}");
        SendMessageAction all = decode(context, "{\"type\":\"mxt:send_message\",\"message\":\"x\",\"target\":\"all\"}");
        SendMessageAction other = decode(context, "{\"type\":\"mxt:send_message\",\"message\":\"x\",\"target\":\"other\"}");
        context.check("message probe: the dispatch bodies decode", entity != null && all != null && other != null);
        if (entity == null || all == null || other == null) return;
        context.check("message probe: entity axis dispatches without throwing",
                runs(() -> entity.execute(new EntityActionContext(player, FormulaContext.EMPTY))));
        context.check("message probe: block axis dispatches without throwing",
                runs(() -> all.execute(new BlockActionContext(level, player.blockPosition(), FormulaContext.EMPTY))));
        context.check("message probe: item axis dispatches without throwing",
                runs(() -> entity.execute(new ItemActionContext(player, player.getMainHandItem(), FormulaContext.EMPTY))));
        context.check("message probe: bientity axis dispatches without throwing",
                runs(() -> other.execute(new BiEntityActionContext(player, nonPlayer, FormulaContext.EMPTY))));
        if (cow != null) cow.discard();
    }

    private static boolean runs(Runnable body) {
        try {
            body.run();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void formationModuleRegistered(ProbeContext context) {
        context.check("message probe: formation axis has send_message",
                MxtRegistries.FORMATION_ACTION_TYPE.containsKey(id()));
        // The module is only useful if the runner reaches it, so the registry entry has to decode to the record:
        // message is required, so the probe supplies one rather than letting an empty object fail the decode.
        MapCodec<? extends FormationActionType> codec = MxtRegistries.FORMATION_ACTION_TYPE.getValue(id());
        JsonObject body = new JsonObject();
        body.addProperty("message", "probe");
        FormationActionType module = decodeModule(context, codec, body);
        context.check("message probe: the formation module decodes to the message record",
                module instanceof SendMessageFormationAction);
        if (module instanceof SendMessageFormationAction(
                Component message1, MessageChannel channel, EntityCondition recipients,
                int intervalTicks
        )) {
            context.expect("message probe: the formation module read its channel",
                    channel, MessageChannel.CHAT);
            context.expect("message probe: the formation module read its message",
                    message1.getString(), "probe");
            context.expect("message probe: the formation module defaults to one upkeep period",
                    intervalTicks, FormationIntervals.PERIOD);
            // An absent recipients condition must not filter anyone out.
            context.check("message probe: the module's absent recipient condition passes everyone",
                    recipients.test(context.player(), FormulaContext.EMPTY));
        }
        // A definition that forgot its message must be refused rather than decoding to something silent.
        context.check("message probe: the formation module requires its message",
                decodeModule(context, codec, new JsonObject()) == null);
    }

    // The throttle is the one part of the interval field that is pure logic, so it is pinned directly: the phase
    // comes from the level clock, which makes two modules share a beat and a module stateless across a reload.
    private static void intervalGate(ProbeContext context) {
        context.check("message probe: an interval of one beat is due on every beat",
                due(10, 0L) && due(10, 10L) && due(10, 20L));
        context.check("message probe: a 30-tick interval is due every third beat",
                due(30, 0L) && !due(30, 10L) && !due(30, 20L) && due(30, 30L));
        // Two definitions with the same interval must agree on the phase rather than drift apart: the gate reads
        // the clock and nothing else, so the same beat gives the same answer however often it is asked.
        context.check("message probe: the phase is shared rather than counted per module",
                due(30, 60L) && !due(30, 70L));
        // The gate is a modulo on the world clock, so a late clock agrees with an early one at the same phase.
        context.check("message probe: the gate agrees with itself on a late clock",
                due(30, 600L) && !due(30, 610L));
        // A tick count that is not a whole number of beats can never be honoured by a pass that only runs on the beat
        // grid, so it is refused rather than silently rounded onto it.
        context.check("message probe: an interval off the beat grid is refused",
                !FormationIntervals.beatDue(15L));
        // The field has to be reachable from JSON, or the assertions above would hold for a field nothing sets.
        MapCodec<? extends FormationActionType> codec = MxtRegistries.FORMATION_ACTION_TYPE.getValue(id());
        JsonObject body = new JsonObject();
        body.addProperty("message", "probe");
        body.addProperty("interval_ticks", 50);
        FormationActionType module = decodeModule(context, codec, body);
        context.expect("message probe: a module reads interval_ticks from JSON",
                module instanceof SendMessageFormationAction message ? message.intervalTicks() : -1, 50);
        // A zero or negative interval would divide by zero in the gate, so the range must refuse it at decode; so must
        // a value below the beat, which the pass could never reach.
        JsonObject zero = new JsonObject();
        zero.addProperty("message", "probe");
        zero.addProperty("interval_ticks", 0);
        context.check("message probe: interval_ticks of zero is refused",
                decodeModule(context, codec, zero) == null);
        JsonObject offBeat = new JsonObject();
        offBeat.addProperty("message", "probe");
        offBeat.addProperty("interval_ticks", 15);
        context.check("message probe: interval_ticks off the beat grid is refused",
                decodeModule(context, codec, offBeat) == null);
    }

    private static boolean due(int intervalTicks, long gameTime) {
        return FormationIntervals.due(intervalTicks, gameTime);
    }

    private static FormationActionType decodeModule(ProbeContext context,
                                                    MapCodec<? extends FormationActionType> codec, JsonElement body) {
        if (codec == null) return null;
        return codec.decoder().parse(RegistryOps.create(JsonOps.INSTANCE, context.registries()), body)
                .result().orElse(null);
    }

    // The type id the one class is registered under on all four axes and in the formation registry.
    private static Identifier id() {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "send_message");
    }

    private static SendMessageAction decode(ProbeContext context, String json) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, context.registries());
        EntityAction action = EntityAction.SINGLE_CODEC.parse(ops, JsonParser.parseString(json)).result().orElse(null);
        return action instanceof SendMessageAction message ? message : null;
    }
}
