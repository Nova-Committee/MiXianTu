package com.iafenvoy.mxt.mixin.codec;

import com.google.gson.JsonElement;
import com.iafenvoy.mxt.accessor.ResourceLoadingOps;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Decoder;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Publishes the element being decoded to its own {@code RegistryOps} for the length of that decode. Both entry
 * points are covered because a definition is decoded from the pack on the server and again from the network
 * payload on a client.
 */
@Mixin(targets = "net.minecraft.resources.RegistryLoadTask$PendingRegistration")
public class RegistryDataLoaderMixin {
    @Inject(method = "loadFromResource", at = @At("HEAD"))
    private static <T> void mxt$beforeLoadFromResource(Decoder<T> elementDecoder, RegistryOps<JsonElement> ops, ResourceKey<T> elementKey, Resource thunk, CallbackInfoReturnable<Either<T, Exception>> cir) {
        ((ResourceLoadingOps) ops).mxt$setKey(elementKey);
    }

    @Inject(method = "loadFromResource", at = @At("RETURN"))
    private static <T> void mxt$afterLoadFromResource(Decoder<T> elementDecoder, RegistryOps<JsonElement> ops, ResourceKey<T> elementKey, Resource thunk, CallbackInfoReturnable<Either<T, Exception>> ci) {
        ((ResourceLoadingOps) ops).mxt$setKey(null);
    }

    @Inject(method = "loadFromNetwork", at = @At("HEAD"))
    private static <T> void mxt$beforeLoadFromNetwork(Decoder<T> elementDecoder, RegistryOps<Tag> ops, ResourceKey<T> elementKey, Tag contents, CallbackInfoReturnable<Either<T, Exception>> ci) {
        ((ResourceLoadingOps) ops).mxt$setKey(elementKey);
    }

    @Inject(method = "loadFromNetwork", at = @At("RETURN"))
    private static <T> void mxt$afterLoadFromNetwork(Decoder<T> elementDecoder, RegistryOps<Tag> ops, ResourceKey<T> elementKey, Tag contents, CallbackInfoReturnable<Either<T, Exception>> ci) {
        ((ResourceLoadingOps) ops).mxt$setKey(null);
    }
}
