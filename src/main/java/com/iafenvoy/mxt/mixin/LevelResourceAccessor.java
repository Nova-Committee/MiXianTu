package com.iafenvoy.mxt.mixin;

import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@code LevelResource} has no public constructor, and secret realms need a world-relative path for the
 * per-dimension folders they create and later discard.
 */
@Mixin(LevelResource.class)
public interface LevelResourceAccessor {
    @Invoker("<init>")
    static LevelResource mxt$newInstance(String relativePath) {
        throw new AssertionError("This method should be replaced by Mixin.");
    }
}
