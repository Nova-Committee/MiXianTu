package com.iafenvoy.mxt.runtime.rift;

import com.iafenvoy.mxt.item.block.entity.RiftBlockEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Colours of a rift. A rift without an explicit override is coloured by its target dimension, derived from the
 * dimension id alone, which keeps every client in agreement without any packet and without the server storing a
 * colour per block. Opacity is deliberately not part of this: a rift is drawn fully opaque.
 */
public final class RiftColors {
    // Marks "no override": the colour follows the target dimension.
    public static final int AUTO = -1;

    private RiftColors() {
    }

    // The same id always yields the same colour, on every client and across restarts.
    public static int forDimension(Identifier dimension) {
        int hashed = fnv(dimension.toString());
        float hue = Math.floorMod(hashed, 3600) / 3600.0F;
        return 0xFF000000 | hsv(hue, 0.72F, 1.0F);
    }

    public static int resolve(RiftBlockEntity rift) {
        return rift.color() == AUTO ? forDimension(rift.target()) : rift.color();
    }

    // Used for the point at a rift's centre, so a node stands out against the links and fills that meet at it.
    public static int rim(int argb) {
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        return 0xFF000000
                | (Math.min(255, red + (255 - red) / 2) << 16)
                | (Math.min(255, green + (255 - green) / 2) << 8)
                | Math.min(255, blue + (255 - blue) / 2);
    }

    // AUTO covers both "auto" and anything unparsable, so callers tell "reset" from "bad input" by checking the
    // input themselves.
    public static int parse(String text) {
        String value = text.startsWith("#") ? text.substring(1) : text;
        if (value.length() != 6) return AUTO;
        try {
            return 0xFF000000 | Integer.parseInt(value, 16);
        } catch (NumberFormatException exception) {
            return AUTO;
        }
    }

    public static String format(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }

    // Mixing the characters keeps neighbouring ids, which often differ in one letter, far apart in hue.
    private static int fnv(String text) {
        int hash = 0x811C9DC5;
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= 0x01000193;
        }
        return hash & 0x7FFFFFFF;
    }

    private static int hsv(float hue, float saturation, float value) {
        int sector = (int) (hue * 6.0F) % 6;
        float fraction = hue * 6.0F - (float) Math.floor(hue * 6.0F);
        float p = value * (1.0F - saturation);
        float q = value * (1.0F - saturation * fraction);
        float t = value * (1.0F - saturation * (1.0F - fraction));
        float red;
        float green;
        float blue;
        switch (sector) {
            case 0 -> {
                red = value;
                green = t;
                blue = p;
            }
            case 1 -> {
                red = q;
                green = value;
                blue = p;
            }
            case 2 -> {
                red = p;
                green = value;
                blue = t;
            }
            case 3 -> {
                red = p;
                green = q;
                blue = value;
            }
            case 4 -> {
                red = t;
                green = p;
                blue = value;
            }
            default -> {
                red = value;
                green = p;
                blue = q;
            }
        }
        return (Mth.clamp((int) (red * 255.0F), 0, 255) << 16)
                | (Mth.clamp((int) (green * 255.0F), 0, 255) << 8)
                | Mth.clamp((int) (blue * 255.0F), 0, 255);
    }
}
