package com.iafenvoy.mxt.compat.kubejs.callback;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Callback storage for the JavaScript cost type.
 *
 * <p>The {@code Cost} interface is checked and consumed with a player only, so a cost callback sees
 * the payer's context rather than the event payload an ability was evaluated with.</p>
 */
public final class MxtJsCostCallbacks {
    private static final Map<String, TriPredicate<Player, JsonObject, FormulaContext>> CHECK = new ConcurrentHashMap<>();
    private static final Map<String, TriConsumer<Player, JsonObject, FormulaContext>> CONSUME = new ConcurrentHashMap<>();

    private MxtJsCostCallbacks() {
    }

    public static void register(String id, TriPredicate<Player, JsonObject, FormulaContext> check,
                                TriConsumer<Player, JsonObject, FormulaContext> consume) {
        CHECK.put(id, check);
        CONSUME.put(id, consume);
    }

    public static boolean check(String id, Player player, JsonObject params) {
        TriPredicate<Player, JsonObject, FormulaContext> callback = CHECK.get(id);
        if (callback == null) return unknown(id);
        try {
            return callback.test(player, params, FormulaContext.of(player));
        } catch (Exception exception) {
            return failed(id, exception);
        }
    }

    public static void consume(String id, Player player, JsonObject params) {
        TriConsumer<Player, JsonObject, FormulaContext> callback = CONSUME.get(id);
        if (callback == null) {
            unknown(id);
            return;
        }
        try {
            callback.accept(player, params, FormulaContext.of(player));
        } catch (Exception exception) {
            failed(id, exception);
        }
    }

    public static void clear() {
        CHECK.clear();
        CONSUME.clear();
    }

    private static boolean unknown(String id) {
        MiXianTu.LOGGER.warn("Unknown KubeJS cost '{}'; the cost cannot be paid", id);
        return false;
    }

    private static boolean failed(String id, Exception exception) {
        MiXianTu.LOGGER.error("KubeJS cost '{}' failed; the cost cannot be paid", id, exception);
        return false;
    }
}
