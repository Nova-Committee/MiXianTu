package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.attachment.RiftWorldAttachment;
import com.iafenvoy.mxt.attachment.SecretRealmTravelAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.item.SecretRealmTokenComponent;
import com.iafenvoy.mxt.data.rift.*;
import com.iafenvoy.mxt.registry.*;
import com.iafenvoy.mxt.runtime.rift.*;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

public final class SecretRealmTokenItem extends Item {
    public SecretRealmTokenItem(Properties properties) { super(properties); }

    @Override
    public @NonNull InteractionResult use(@NonNull Level level, @NonNull Player player, @NonNull InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
        SecretRealmTokenComponent token = stack.getOrDefault(MxtDataComponents.SECRET_REALM_TOKEN, SecretRealmTokenComponent.EMPTY);
        SecretRealmTravelAttachment travel = player.getExistingData(MxtAttachments.SECRET_REALM_TRAVEL).orElse(null);
        boolean returning = travel != null && travel.active();
        if (!returning && token.realm().isEmpty()) {
            serverPlayer.sendSystemMessage(Component.translatable("item.mxt.secret_realm_token.unbound"), true);
            return InteractionResult.FAIL;
        }
        if (token.summoned().isPresent() && token.summonedDimension().filter(level.dimension().identifier()::equals).isPresent()) {
            RiftRecord old = RiftService.get(serverPlayer.level(), token.summoned().get());
            RiftDestination expected = returning ? new RiftDestination.Return() : new RiftDestination.Realm(token.realm().orElseThrow().unwrapKey().orElseThrow().identifier());
            if (old != null && !old.expired(RiftService.now(serverPlayer.level())) && old.data().destination().equals(expected)) {
                serverPlayer.sendSystemMessage(Component.translatable("item.mxt.secret_realm_token.reused", old.id().toString()), true);
                return InteractionResult.SUCCESS_SERVER;
            }
        }
        long owned = 0;
        for (ServerLevel world : serverPlayer.level().getServer().getAllLevels()) {
            RiftWorldAttachment attachment = world.getExistingData(MxtAttachments.RIFT_WORLD).orElse(null);
            if (attachment != null) owned += RiftService.world(world).locations().stream()
                    .filter(r -> r.owner().filter(player.getUUID()::equals).isPresent() && !r.expired(RiftService.now(world))).count();
        }
        if (owned >= MxtServerConfig.INSTANCE.rifts.maxPerPlayer.getValue()) {
            serverPlayer.sendSystemMessage(Component.translatable("rift.mxt.failure.budget"), true);
            return InteractionResult.FAIL;
        }
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        if (look.lengthSqr() < 0.5) look = new Vec3(0, 0, 1);
        Vec3 origin = player.position().add(look.scale(3));
        RiftPlacement placement = new RiftPlacement(origin, look, new Vec3(look.z, 0, -look.x));
        RiftPreset preset = token.preset().map(Holder::value).orElse(new RiftPreset(RiftShape.DEFAULT, RiftAppearance.DEFAULT));
        RiftGeometry.Mesh mesh = RiftGeometry.mesh(preset.shape(), placement, RiftGeometry.triangulate(preset.shape()).result().orElseThrow());
        AABB bounds = mesh.bounds();
        // Contact thickness below a floor-aligned outline must not count as an obstructing block.
        if (!level.noCollision(null, new AABB(bounds.minX,
                bounds.minY + RiftGeometry.THICKNESS + 1e-6, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ)))
            return InteractionResult.FAIL;
        RiftTemplate data = new RiftTemplate("token", placement, false, preset.shape(), returning ? new RiftDestination.Return()
                : new RiftDestination.Realm(token.realm().orElseThrow().unwrapKey().orElseThrow().identifier()), preset.appearance(), token.durationTicks(), true);
        RiftResult result = RiftService.publish(serverPlayer.level(), null, 0, data, player.getUUID(), true);
        if (result.isFailure()) {
            serverPlayer.sendSystemMessage(Component.translatable("rift.mxt.failure.edit", result.detail()), true); return InteractionResult.FAIL;
        }
        stack.set(MxtDataComponents.SECRET_REALM_TOKEN, new SecretRealmTokenComponent(token.realm(), token.preset(), token.durationTicks(),
                Optional.of(result.record().id()), Optional.of(level.dimension().identifier())));
        player.getCooldowns().addCooldown(stack, 20);
        serverPlayer.sendSystemMessage(Component.translatable("item.mxt.secret_realm_token.summoned"), true);
        return InteractionResult.SUCCESS_SERVER;
    }
}
