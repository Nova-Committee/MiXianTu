package com.iafenvoy.mxt.screen.information;

import net.minecraft.network.chat.Component;

public record InformationEntry(Component name, Component value) {
    public boolean fulfilled() {
        return !this.name.getString().isEmpty() || !this.value.getString().isEmpty();
    }
}
