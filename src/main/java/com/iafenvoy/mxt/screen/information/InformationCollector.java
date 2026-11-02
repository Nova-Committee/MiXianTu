package com.iafenvoy.mxt.screen.information;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class InformationCollector {
    private final Player player;
    private final List<Entry> entries = new LinkedList<>();

    public InformationCollector(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return this.player;
    }

    public <T> T getData(Supplier<AttachmentType<T>> type) {
        return this.player.getData(type);
    }

    public List<Entry> getEntries() {
        return List.copyOf(this.entries);
    }

    public InformationCollector add(Entry entry) {
        if (entry.fulfilled()) this.entries.add(entry);
        return this;
    }

    public InformationCollector add(@Nullable Component name, Component value) {
        return this.add(new Entry(name, value));
    }

    public InformationCollector add(@Nullable Component name, Component value, int color, @Nullable Component tooltip) {
        return this.add(new Entry(name, value, color, Optional.ofNullable(tooltip)));
    }

    public InformationCollector add(String key, Component value) {
        return this.add(Component.translatable(key), value);
    }

    public InformationCollector add(String key, String value) {
        return this.add(key, Component.literal(value));
    }

    public InformationCollector add(Component value) {
        return this.add((Component) null, value);
    }

    public InformationCollector addAll(List<Entry> entries) {
        entries.stream().filter(Entry::fulfilled).forEach(this.entries::add);
        return this;
    }

    public record Entry(@Nullable Component name, Component value, int color, Optional<Component> tooltip) {
        public Entry(@Nullable Component name, Component value) {
            this(name, value, 0xFFE0E4EC, Optional.empty());
        }

        public boolean fulfilled() {
            return this.name != null && !this.name.getString().isEmpty() || !this.value.getString().isEmpty();
        }
    }
}
