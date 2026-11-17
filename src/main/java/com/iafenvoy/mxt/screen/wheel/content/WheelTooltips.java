package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.saturn.util.DefinitionText;
import com.iafenvoy.saturn.util.TooltipText;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class WheelTooltips {
    private WheelTooltips() {
    }

    static Component number(double value) {
        if (!Double.isFinite(value)) return Component.literal("0");
        return Component.literal(value == Math.rint(value) && Math.abs(value) < 1.0E15 ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value));
    }

    static Component elements(List<Either<Holder<Element>, TagKey<Element>>> affinity) {
        List<Component> parts = new ArrayList<>();
        for (Either<Holder<Element>, TagKey<Element>> entry : affinity)
            parts.add(entry.map(DefinitionText::nameOrId, tag -> Component.literal("#" + tag.location())));
        return TooltipText.join(parts);
    }
}
