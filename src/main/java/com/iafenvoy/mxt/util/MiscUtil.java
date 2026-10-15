package com.iafenvoy.mxt.util;

public class MiscUtil {
    public static double positive(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }
}
