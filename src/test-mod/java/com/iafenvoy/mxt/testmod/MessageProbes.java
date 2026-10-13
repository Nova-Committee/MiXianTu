package com.iafenvoy.mxt.testmod;

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
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.runtime.formation.FormationWorldTicker;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.commands.CommandSourceStack;
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

import java.util.Objects;

/**
 * Checks the send_message action on every axis. Each leg prints actual and expected.
 * <p>
 * The wire shape is the part worth pinning: the same codec has to decode a body written as a bare translation key
 * and another written as a full component, and the type id has to resolve through all four action registries and
 * the formation registry. A wrong default would put a message on the wrong line, so the channel default is
 * asserted rather than assumed.
 */
public final class MessageProbes {
    private MessageProbes() {
    }

    public static int run(CommandSourceStack source) {
        boolean ok = true;
        ok &= translationKeyBody(source);
        ok &= componentBody(source);
        ok &= channelDefault(source);
        ok &= channelSelection(source);
        ok &= recipientCondition(source);
        ok &= registeredOnEveryAxis(source);
        ok &= dispatchesOnEveryAxis(source);
        ok &= formationModuleRegistered(source);
        ok &= intervalGate(source);
        final boolean passed = ok;
        source.sendSuccess(() -> Component.literal(passed ? "message probe: all legs passed" : "message probe: FAILURES above"), false);
        return passed ? 1 : 0;
    }

    // A bare string is a translation key, which is what a pack with its own lang writes.
    private static boolean translationKeyBody(CommandSourceStack source) {
        SendMessageAction action = decode(source, "{\"type\":\"mxt:send_message\",\"message\":\"mxt_test.probe.key\"}");
        return check(source, "message probe: a bare string decodes as a translation key",
                action == null ? null : action.message().getString(), "mxt_test.probe.key");
    }

    // An object is a full component, so styling survives.
    private static boolean componentBody(CommandSourceStack source) {
        SendMessageAction action = decode(source, "{\"type\":\"mxt:send_message\",\"message\":{\"text\":\"literal body\"}}");
        return check(source, "message probe: an object decodes as a literal component",
                action == null ? null : action.message().getString(), "literal body");
    }

    // Chat is the default: a pack that never asked for the action bar must not overwrite it.
    private static boolean channelDefault(CommandSourceStack source) {
        SendMessageAction action = decode(source, "{\"type\":\"mxt:send_message\",\"message\":\"x\"}");
        boolean ok = check(source, "message probe: channel defaults to chat",
                action == null ? null : action.channel(), MessageChannel.CHAT);
        // The flag handed to sendSystemMessage has to agree with the enum it came from.
        ok &= check(source, "message probe: chat maps to the chat flag", MessageChannel.CHAT.actionBar(), false);
        return ok;
    }

    private static boolean channelSelection(CommandSourceStack source) {
        SendMessageAction action = decode(source, "{\"type\":\"mxt:send_message\",\"message\":\"x\",\"channel\":\"actionbar\"}");
        boolean ok = check(source, "message probe: actionbar is selectable",
                action == null ? null : action.channel(), MessageChannel.ACTION_BAR);
        ok &= check(source, "message probe: actionbar maps to the action bar flag",
                action != null && action.channel().actionBar(), true);
        return ok;
    }

    // The recipient filter is a real condition tree, and an absent one must not filter anybody out.
    private static boolean recipientCondition(CommandSourceStack source) {
        SendMessageAction plain = decode(source, "{\"type\":\"mxt:send_message\",\"message\":\"x\"}");
        boolean ok = check(source, "message probe: an absent recipient condition passes everyone",
                plain != null && plain.recipients().test(source.getPlayer(), FormulaContext.EMPTY), true);
        // A condition the registry does not know must fail the whole decode rather than silently dropping the field.
        SendMessageAction bogus = decode(source, "{\"type\":\"mxt:send_message\",\"message\":\"x\",\"recipients\":{\"type\":\"mxt:no_such_condition\"}}");
        ok &= check(source, "message probe: an unknown recipient condition type is rejected", bogus == null, true);
        SendMessageAction sneaking = decode(source, "{\"type\":\"mxt:send_message\",\"message\":\"x\",\"recipients\":{\"type\":\"mxt:sneaking\"}}");
        ok &= check(source, "message probe: a known recipient condition type decodes", sneaking != null, true);
        return ok;
    }

    // One class is registered under all four action ids, so every axis can write the same type id.
    private static boolean registeredOnEveryAxis(CommandSourceStack source) {
        boolean ok = true;
        ok &= check(source, "message probe: entity axis has send_message", MxtRegistries.ENTITY_ACTION_TYPE.containsKey(id()), true);
        ok &= check(source, "message probe: block axis has send_message", MxtRegistries.BLOCK_ACTION_TYPE.containsKey(id()), true);
        ok &= check(source, "message probe: item axis has send_message", MxtRegistries.ITEM_ACTION_TYPE.containsKey(id()), true);
        ok &= check(source, "message probe: bientity axis has send_message", MxtRegistries.BI_ENTITY_ACTION_TYPE.containsKey(id()), true);
        return ok;
    }

    // Dispatching is where the axes actually differ, and the sub-case worth pinning is the one that is easy to get
    // wrong: a named addressee that is not a player must resolve to nobody rather than to the whole level.
    private static boolean dispatchesOnEveryAxis(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayer();
        if (player == null) return check(source, "message probe: a player is needed to dispatch", false, true);
        Cow cow = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
        Entity nonPlayer = cow == null ? new ItemEntity(level, 0.0D, 0.0D, 0.0D, new ItemStack(Items.STONE)) : cow;
        boolean ok = true;
        // SELF and OTHER name an entity, so a non-player addressee resolves to nobody: the send must do nothing.
        ok &= check(source, "message probe: SELF on a non-player actor resolves to nobody",
                SendMessageAction.MessageTarget.SELF.player(nonPlayer, null).isEmpty(), true);
        ok &= check(source, "message probe: OTHER on a non-player addressee resolves to nobody",
                SendMessageAction.MessageTarget.OTHER.player(player, nonPlayer).isEmpty(), true);
        ok &= check(source, "message probe: SELF on a player resolves to that player",
                SendMessageAction.MessageTarget.SELF.player(player, null).orElse(null), player);
        ok &= check(source, "message probe: OTHER on a player resolves to that player",
                SendMessageAction.MessageTarget.OTHER.player(nonPlayer, player).orElse(null), player);
        // ALL never names an entity, which is exactly what lets a block action (which has no entity) use it.
        ok &= check(source, "message probe: ALL names nobody", SendMessageAction.MessageTarget.ALL.player(player, player).isEmpty(), true);
        // Every axis must at least run its dispatch without throwing. Building the action first keeps a decode
        // failure from being reported as a dispatch failure.
        SendMessageAction entity = decode(source, "{\"type\":\"mxt:send_message\",\"message\":\"x\"}");
        SendMessageAction all = decode(source, "{\"type\":\"mxt:send_message\",\"message\":\"x\",\"target\":\"all\"}");
        SendMessageAction other = decode(source, "{\"type\":\"mxt:send_message\",\"message\":\"x\",\"target\":\"other\"}");
        ok &= check(source, "message probe: the dispatch bodies decode",
                entity != null && all != null && other != null, true);
        if (entity == null || all == null || other == null) return ok;
        ok &= check(source, "message probe: entity axis dispatches without throwing", runs(() ->
                entity.execute(new EntityActionContext(player, FormulaContext.EMPTY))), true);
        ok &= check(source, "message probe: block axis dispatches without throwing", runs(() ->
                all.execute(new BlockActionContext(level, player.blockPosition(), FormulaContext.EMPTY))), true);
        ok &= check(source, "message probe: item axis dispatches without throwing", runs(() ->
                entity.execute(new ItemActionContext(player, player.getMainHandItem(), FormulaContext.EMPTY))), true);
        ok &= check(source, "message probe: bientity axis dispatches without throwing", runs(() ->
                other.execute(new BiEntityActionContext(player, nonPlayer, FormulaContext.EMPTY))), true);
        if (cow != null) cow.discard();
        return ok;
    }

    private static boolean runs(Runnable body) {
        try {
            body.run();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean formationModuleRegistered(CommandSourceStack source) {
        boolean ok = check(source, "message probe: formation axis has send_message",
                MxtRegistries.FORMATION_ACTION_TYPE.containsKey(id()), true);
        // The module is only useful if the runner reaches it, so the registry entry has to decode to the record.
        // message is required, so the probe has to supply one: an empty object would fail the decode and this leg
        // would report a failure that says nothing about the module.
        MapCodec<? extends FormationActionType> codec = MxtRegistries.FORMATION_ACTION_TYPE.getValue(id());
        JsonObject body = new JsonObject();
        body.addProperty("message", "probe");
        FormationActionType module = decodeModule(source, codec, body);
        ok &= check(source, "message probe: the formation module decodes to the message record",
                module instanceof SendMessageFormationAction, true);
        if (module instanceof SendMessageFormationAction(
                Component message1, MessageChannel channel, EntityCondition recipients,
                int intervalPeriods
        )) {
            ok &= check(source, "message probe: the formation module read its channel",
                    channel, MessageChannel.CHAT);
            ok &= check(source, "message probe: the formation module read its message",
                    message1.getString(), "probe");
            ok &= check(source, "message probe: the formation module defaults to every period",
                    intervalPeriods, 1);
            // An absent recipients condition must not filter anyone out.
            ok &= check(source, "message probe: the module's absent recipient condition passes everyone",
                    recipients.test(source.getPlayer(), FormulaContext.EMPTY), true);
        }
        // A definition that forgot its message must be refused rather than decoding to something silent.
        ok &= check(source, "message probe: the formation module requires its message",
                decodeModule(source, codec, new JsonObject()) == null, true);
        return ok;
    }

    // The throttle is the one part of the interval field that is pure logic, so it is pinned directly: the phase
    // comes from the level clock, which makes two modules share a period and a module stateless across a reload.
    private static boolean intervalGate(CommandSourceStack source) {
        boolean ok = check(source, "message probe: an interval of one is due on every period",
                due(1, 0L) && due(1, 20L) && due(1, 40L), true);
        ok &= check(source, "message probe: an interval of three is due every third period",
                due(3, 0L) && !due(3, 20L) && !due(3, 40L) && due(3, 60L), true);
        // Two definitions with the same interval must agree on the phase rather than drift apart: the gate reads
        // the clock and nothing else, so the same period gives the same answer however often it is asked.
        ok &= check(source, "message probe: the phase is shared rather than counted per module",
                due(3, 60L) && !due(3, 80L), true);
        // Floor division rather than a modulo on a positive clock: 600 is a multiple of the 20-tick period and 3
        // divides it, so the gate must open there and stay shut one period later.
        ok &= check(source, "message probe: the gate uses floor division on a late clock",
                due(3, 600L) && !due(3, 620L), true);
        // The field has to be reachable from JSON, or the assertions above would hold for a field nothing sets.
        MapCodec<? extends FormationActionType> codec = MxtRegistries.FORMATION_ACTION_TYPE.getValue(id());
        JsonObject body = new JsonObject();
        body.addProperty("message", "probe");
        body.addProperty("interval_periods", 5);
        FormationActionType module = decodeModule(source, codec, body);
        ok &= check(source, "message probe: a module reads interval_periods from JSON",
                module instanceof SendMessageFormationAction message ? message.intervalPeriods() : -1, 5);
        // A zero or negative interval would divide by zero in the gate, so the range must refuse it at decode.
        JsonObject zero = new JsonObject();
        zero.addProperty("message", "probe");
        zero.addProperty("interval_periods", 0);
        ok &= check(source, "message probe: interval_periods of zero is refused",
                decodeModule(source, codec, zero) == null, true);
        return ok;
    }

    private static boolean due(int intervalPeriods, long gameTime) {
        return FormationIntervals.due(intervalPeriods, gameTime, FormationWorldTicker.PERIOD);
    }

    private static FormationActionType decodeModule(CommandSourceStack source,
                                                    MapCodec<? extends FormationActionType> codec, JsonElement body) {
        if (codec == null) return null;
        return codec.decoder().parse(RegistryOps.create(JsonOps.INSTANCE, source.getLevel().registryAccess()), body)
                .result().orElse(null);
    }

    private static Identifier id() {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "send_message");
    }

    private static SendMessageAction decode(CommandSourceStack source, String json) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, source.getLevel().registryAccess());
        EntityAction action = EntityAction.SINGLE_CODEC.parse(ops, JsonParser.parseString(json)).result().orElse(null);
        return action instanceof SendMessageAction message ? message : null;
    }

    private static boolean check(CommandSourceStack source, String label, Object actual, Object expected) {
        boolean ok = Objects.equals(actual, expected);
        source.sendSuccess(() -> Component.literal(label + ": " + (ok ? "ok" : "expected " + expected + " but got " + actual)), false);
        return ok;
    }
}
