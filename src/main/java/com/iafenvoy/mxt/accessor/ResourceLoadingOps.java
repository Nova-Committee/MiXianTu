package com.iafenvoy.mxt.accessor;

import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

/**
 * The data pack entry a decode is running under, carried by that decode's {@code RegistryOps} so a codec can
 * derive a default from the id it is being decoded as. Set only while one registry element loads and cleared
 * right after, so a codec that reads it outside data pack loading sees null.
 */
public interface ResourceLoadingOps {
    void mxt$setKey(@Nullable ResourceKey<?> key);

    @Nullable
    ResourceKey<?> mxt$getKey();
}
