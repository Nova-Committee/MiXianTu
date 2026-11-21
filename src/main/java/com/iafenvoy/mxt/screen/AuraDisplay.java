package com.iafenvoy.mxt.screen;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.saturn.util.DefinitionText;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record AuraDisplay(Component name, Optional<IconReference> icon, int color, List<Component> description) {
    public static AuraDisplay of(Holder<Aura> aura) {
        return new AuraDisplay(DefinitionText.nameOrId(aura), aura.value().resource().value().icon(),
                aura.value().resource().value().particleColor(),
                DefinitionText.resolved(DefinitionText.description(aura)).stream().<Component>map(text -> text).toList());
    }

    public List<Component> tooltip(Component amount) {
        List<Component> lines = new ArrayList<>();
        lines.add(this.name);
        lines.add(amount);
        lines.addAll(this.description);
        return lines;
    }
}
