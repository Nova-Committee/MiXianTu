package com.iafenvoy.mxt.mixin;

import com.iafenvoy.mxt.runtime.world.SecretRealmRegistry;
import com.iafenvoy.mxt.runtime.world.SecretRealmSeedBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalLong;

/**
 * Gives a secret realm dimension its own seed instead of the world seed.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSeedMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldOptions;seed()J"))
    private long mxt$secretRealmSeed(WorldOptions options) {
        OptionalLong pending = SecretRealmSeedBridge.pending();
        return pending.isPresent() ? pending.getAsLong() : options.seed();
    }

    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void mxt$secretRealmSeedLookup(CallbackInfoReturnable<Long> cir) {
        OptionalLong known = SecretRealmRegistry.seedFor(((ServerLevel) (Object) this).dimension());
        if (known.isPresent()) cir.setReturnValue(known.getAsLong());
    }
}
