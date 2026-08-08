package com.iafenvoy.mxt.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.Executor;

/**
 * Narrow access to the two constructor dependencies needed by runtime levels.
 */
@Mixin(MinecraftServer.class)
public interface MinecraftServerRuntimeAccessor {
    @Accessor("executor")
    Executor mxt$executor();

    @Accessor("storageSource")
    LevelStorageAccess mxt$storageSource();
}
