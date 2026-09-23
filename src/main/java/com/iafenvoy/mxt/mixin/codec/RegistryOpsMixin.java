package com.iafenvoy.mxt.mixin.codec;

import com.iafenvoy.mxt.accessor.ResourceLoadingOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(RegistryOps.class)
public class RegistryOpsMixin implements ResourceLoadingOps {
    @Unique
    private final ThreadLocal<ResourceKey<?>> mxt$key = new ThreadLocal<>();

    @Override
    public void mxt$setKey(@Nullable ResourceKey<?> key) {
        this.mxt$key.set(key);
    }

    @Override
    public @Nullable ResourceKey<?> mxt$getKey() {
        return this.mxt$key.get();
    }
}
