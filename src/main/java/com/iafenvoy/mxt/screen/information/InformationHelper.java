package com.iafenvoy.mxt.screen.information;

import com.iafenvoy.mxt.util.DefinitionText;
import net.minecraft.core.Holder;

import java.util.Collection;

public final class InformationHelper {
    public static void lineWithDefinitions(InformationCollector collector, String key, Collection<? extends Holder<?>> values, String category) {
        if (!values.isEmpty()) collector.add(key, joinDefinitions(values, category));
    }

    public static String joinDefinitions(Collection<? extends Holder<?>> values, String category) {
        return values.stream().map(holder -> DefinitionText.name(holder, category).getString()).reduce((a, b) -> a + ", " + b).orElse("-");
    }
}
