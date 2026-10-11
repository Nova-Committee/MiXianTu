package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.data.alchemy.AlchemyFurnaceDefinition;
import com.iafenvoy.mxt.recipe.AlchemyRole;

public final class AlchemySlots {
    public static final int MAIN_START = 0;
    public static final int AUX_START = 2;
    public static final int CATALYST = 4;
    public static final int OUTPUT_START = 5;
    public static final int OUTPUT_COUNT = 4;
    public static final int[] OUTPUTS = {5, 6, 7, 8};
    public static final int TOTAL = 9;
    public static final int[] NONE = {};

    private AlchemySlots() {
    }

    public static AlchemyRole role(int index) {
        return switch (index) {
            case 0, 1 -> AlchemyRole.MAIN;
            case 2, 3 -> AlchemyRole.AUXILIARY;
            case 4 -> AlchemyRole.CATALYST;
            default -> null;
        };
    }

    public static boolean output(int index) {
        return index >= OUTPUT_START && index < TOTAL;
    }

    public static boolean enabled(int index, AlchemyFurnaceDefinition spec) {
        AlchemyRole role = role(index);
        if (role == null) return false;
        return switch (role) {
            case MAIN -> index - MAIN_START < spec.mainSlots();
            case AUXILIARY -> index - AUX_START < spec.auxiliarySlots();
            case CATALYST -> true;
        };
    }
}
