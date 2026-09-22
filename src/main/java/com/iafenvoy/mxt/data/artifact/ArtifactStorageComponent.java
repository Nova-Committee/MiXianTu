package com.iafenvoy.mxt.data.artifact;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Persistent server-owned contents of a storage artifact. Slot limits come from its archetype, never from this
 * payload.
 *
 * <p>The list is kept at the capacity the definition declares, so the size a screen reads and the size the
 * artifact accepts are the same number. An empty stack is therefore a real value here - it is how a hole in the
 * middle of the contents is spelled - and the list codec has to be the optional one: {@code ItemStack.CODEC}
 * refuses an empty stack at both ends (count must be 1..99, item must not be air), and the stack it is part of is
 * what has to be written to disk and to the client. A non-optional codec here takes down the whole
 * {@code container_set_slot} packet the moment the artifact with a hole in its storage is synced.</p>
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

    /**
     * The whole contents at once, cut or padded to {@code capacity}. What a screen writes back after a click: one
     * component update instead of one per slot, and the size stays the capacity even when the last slots are empty.
     */
    public static ArtifactStorageComponent of(int capacity, List<ItemStack> contents) {
        if (capacity < 0) throw new IllegalArgumentException("Artifact storage capacity cannot be negative");
        ArrayList<ItemStack> values = new ArrayList<>(capacity);
        for (int index = 0; index < capacity; index++)
            values.add(index < contents.size() ? contents.get(index).copy() : ItemStack.EMPTY);
        return new ArtifactStorageComponent(values);
    }
}
