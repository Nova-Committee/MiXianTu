package com.iafenvoy.mxt.attachment;

import com.iafenvoy.saturn.data.storage.builtin.ContainerDataStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class SharedStorageAttachment {
    public static final MapCodec<SharedStorageAttachment> CODEC = Codec.unboundedMap(Identifier.CODEC, ContainerDataStorage.CODEC.codec())
            .optionalFieldOf("inventories", Map.of()).xmap(SharedStorageAttachment::new, data -> data.inventories);

    private final Map<Identifier, ContainerDataStorage> inventories;

    public SharedStorageAttachment() {
        this(Map.of());
    }

    private SharedStorageAttachment(Map<Identifier, ContainerDataStorage> inventories) {
        this.inventories = new HashMap<>(inventories);
    }

    public ContainerDataStorage get(Identifier ability) {
        return this.inventories.getOrDefault(ability, ContainerDataStorage.INSTANCE);
    }

    public void set(Identifier ability, ContainerDataStorage storage) {
        if (storage.contents().stream().allMatch(ItemStack::isEmpty)) this.inventories.remove(ability);
        else this.inventories.put(ability, storage);
    }
}
