package com.iafenvoy.mxt.screen.information;

import com.iafenvoy.mxt.attachment.SpiritAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.DefinitionText;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Optional;

public final class InformationHelper {
    public static SpiritAttachment spirit(Player player) {
        return player.getData(MxtAttachments.SPIRIT_DATA);
    }

    public static Optional<InformationEntry> line(String key, String value) {
        return line(key, Component.literal(value));
    }

    public static Optional<InformationEntry> line(String key, Component value) {
        return Optional.of(new InformationEntry(Component.translatable(key), value));
    }

    public static Optional<InformationEntry> lineWithDefinitions(String key, List<? extends Holder<?>> values, String category) {
        return values.isEmpty() ? Optional.empty() : line(key, Component.literal(joinDefinitions(values, category)));
    }

    public static String joinDefinitions(List<? extends Holder<?>> values, String category) {
        return values.stream().map(holder -> DefinitionText.name(holder, category).getString()).reduce((a, b) -> a + ", " + b).orElse("-");
    }
}
