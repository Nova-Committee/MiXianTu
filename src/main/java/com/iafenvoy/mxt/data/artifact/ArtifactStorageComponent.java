package com.iafenvoy.mxt.data.artifact;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Persistent server-owned contents of a storage artifact; slot limits come from its archetype, never from this
 * payload. The list is kept at the declared capacity, so an empty stack is a real value here - it is how a hole in
 * the middle of the contents is spelled - and the codec has to be {@code ItemStack.OPTIONAL_CODEC}: the plain one
 * refuses an empty stack at both ends, and that takes down the whole {@code container_set_slot} packet.
 */
public record ArtifactStorageComponent(List<ItemStack> contents) {
    public static final Codec<ArtifactStorageComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("contents", List.of()).forGetter(ArtifactStorageComponent::contents)
    ).apply(i, ArtifactStorageComponent::new));

    public ArtifactStorageComponent {
        contents = new LinkedList<>(contents.stream().map(ItemStack::copy).toList());
    }

    public ItemStack get(int slot) {
        return slot < 0 || slot >= this.contents.size() ? ItemStack.EMPTY : this.contents.get(slot);
    }

    public ArtifactStorageComponent with(int slot, ItemStack value, int capacity) {
        if (slot < 0 || slot >= capacity) throw new IllegalArgumentException("Storage slot outside artifact capacity");
        ArrayList<ItemStack> values = new ArrayList<>(Math.min(capacity, Math.max(this.contents.size(), slot + 1)));
        for (int index = 0; index < capacity; index++)
            values.add(index < this.contents.size() ? this.contents.get(index).copy() : ItemStack.EMPTY);
        values.set(slot, value.copy());
        return new ArtifactStorageComponent(values);
    }

    // What a screen writes back after a click: one component update instead of one per slot, at the full capacity.
    public static ArtifactStorageComponent of(int capacity, List<ItemStack> contents) {
        if (capacity < 0) throw new IllegalArgumentException("Artifact storage capacity cannot be negative");
        ArrayList<ItemStack> values = new ArrayList<>(capacity);
        for (int index = 0; index < capacity; index++)
            values.add(index < contents.size() ? contents.get(index).copy() : ItemStack.EMPTY);
        return new ArtifactStorageComponent(values);
    }
}
