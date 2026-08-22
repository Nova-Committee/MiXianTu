package com.iafenvoy.mxt.compat.kubejs.callback;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

/**
 * Callback storage for direct-ID gameplay registries.
 */
public final class MxtJsGameplayCallbacks {
    private static volatile BiPredicate<Mob, FormulaContext> creatureSpawn;
    private static volatile BiPredicate<LivingEntity, FormulaContext> cultivation;

    private MxtJsGameplayCallbacks() {
    }

    public static void registerCreatureSpawn(BiPredicate<Mob, FormulaContext> callback) {
        creatureSpawn = callback;
    }

    public static void registerCultivation(BiPredicate<LivingEntity, FormulaContext> callback) {
        cultivation = callback;
    }

    public static boolean testCreatureSpawn(Mob mob, FormulaContext context) {
        BiPredicate<Mob, FormulaContext> callback = creatureSpawn;
        return callback == null ? unknown("creature spawn condition") : test("creature spawn condition", () -> callback.test(mob, context));
    }

    public static boolean testCultivation(LivingEntity entity, FormulaContext context) {
        BiPredicate<LivingEntity, FormulaContext> callback = cultivation;
        return callback == null ? unknown("cultivation condition") : test("cultivation condition", () -> callback.test(entity, context));
    }

    public static void clear() {
        creatureSpawn = null;
        cultivation = null;
    }

    private static boolean unknown(String type) {
        MiXianTu.LOGGER.warn("Unknown KubeJS {}", type);
        return false;
    }

    private static boolean test(String type, BooleanSupplier callback) {
        try {
            return callback.getAsBoolean();
        } catch (Exception exception) {
            MiXianTu.LOGGER.error("KubeJS {} failed", type, exception);
            return false;
        }
    }
}
