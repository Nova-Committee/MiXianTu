package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.data.item.RiftComponent;
import com.iafenvoy.mxt.data.rift.RiftDestination;
import com.iafenvoy.mxt.data.rift.RiftPlacement;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.rift.RiftEditorService;
import com.iafenvoy.mxt.runtime.rift.RiftGeometry;
import com.iafenvoy.mxt.runtime.rift.RiftRecord;
import com.iafenvoy.mxt.runtime.rift.RiftService;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.Result;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

public class RiftAnchorItem extends Item {
    public RiftAnchorItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NonNull InteractionResult use(@NonNull Level level, @NonNull Player player, @NonNull InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        if (!RiftEditorService.mayEdit(serverPlayer)) return InteractionResult.FAIL;
        ItemStack stack = player.getItemInHand(hand);
        RiftComponent component = stack.getOrDefault(MxtDataComponents.RIFT, RiftComponent.EMPTY);
        if (player.isShiftKeyDown()) {
            RiftDestination.Position destination = new RiftDestination.Position(Optional.of(level.dimension().identifier()), player.position(),
                    Optional.of(player.getYRot()), Optional.of(player.getXRot()), false);
            stack.set(MxtDataComponents.RIFT, new RiftComponent(component.blueprint(), Optional.of(destination)));
            return InteractionResult.SUCCESS_SERVER;
        }
        RiftRecord selected = pick(serverPlayer);
        if (selected != null) RiftEditorService.open(serverPlayer, selected.id(), null);
        else RiftEditorService.openItem(serverPlayer, hand);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public @NonNull InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        if (!RiftEditorService.mayEdit(player)) return InteractionResult.FAIL;
        if (player.isShiftKeyDown()) return this.use(context.getLevel(), player, context.getHand());
        RiftRecord selected = pick(player);
        if (selected != null) {
            RiftEditorService.open(player, selected.id(), null);
            return InteractionResult.SUCCESS_SERVER;
        }
        Direction face = context.getClickedFace();
        Vec3 n = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        Vec3 tangent = face.getAxis().isVertical() ? new Vec3(1, 0, 0) : new Vec3(n.z, 0, -n.x);
        Result<RiftRecord> result = RiftEditorService.place(player, context.getHand(), new RiftPlacement(context.getClickLocation(), n, tangent));
        if (result instanceof Result.FailureResult<RiftRecord>(Failure failure1)) {
            player.sendSystemMessage(Component.translatable("rift.mxt.failure.edit", failure1.message()), true);
            return InteractionResult.FAIL;
        }
        player.sendSystemMessage(Component.translatable(result.getData().orElseThrow().data().enabled() ? "rift.mxt.placed" : "rift.mxt.placed_disabled"), true);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static @Nullable RiftRecord pick(ServerPlayer player) {
        Vec3 from = player.getEyePosition(), ray = player.getLookAngle();
        BlockHitResult block = player.level().clip(new ClipContext(from, from.add(ray.scale(16)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        // Include a portal flush with the hit face, but not one behind the block.
        double nearest = block.getType() == HitResult.Type.MISS ? 16 : Math.min(16, from.distanceTo(block.getLocation()) + 1e-6);
        long now = RiftService.now(player.level());
        RiftRecord selected = null;
        for (RiftRecord record : RiftService.world(player.level()).records()) {
            if (record.expired(now)) continue;
            RiftPlacement placement = record.data().placement();
            double denominator = ray.dot(placement.normal());
            if (Math.abs(denominator) < 1e-8) continue;
            double at = placement.origin().subtract(from).dot(placement.normal()) / denominator;
            if (at >= 0 && at <= nearest && RiftGeometry.contains(record.data().shape(), placement.local(from.add(ray.scale(at))))) {
                nearest = at;
                selected = record;
            }
        }
        return selected;
    }
}
