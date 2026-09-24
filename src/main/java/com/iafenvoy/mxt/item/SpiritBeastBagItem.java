package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.attachment.ContractAttachment;
import com.iafenvoy.mxt.data.item.SpiritBeastComponent;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.creature.ContractFeedback;
import com.iafenvoy.mxt.runtime.creature.ContractService;
import com.iafenvoy.mxt.runtime.creature.Contracts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import org.jetbrains.annotations.NotNull;

/**
 * Portable storage for one owned, contracted mob. The entity's full persistent data is retained, written into the
 * bag's own component together with what a bag has to say about it without loading it.
 *
 * <p>What this bag may hold is the bag's own rule - your own contracted beast, and one at a time - not a
 * property of the creature: the framework puts no gate on capturing, so another item is free to decide
 * differently. The creature is only told that it happened, through {@code CaptureListener}.</p>
 */
public final class SpiritBeastBagItem extends Item {
    public SpiritBeastBagItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack, @NotNull Player player,
                                                           @NotNull LivingEntity target,
                                                           @NotNull InteractionHand hand) {
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer owner) || !(target instanceof Mob mob)) {
            ItemFeedback.send(player, ContractFeedback.of(ContractService.Failure.NOT_CONTRACTABLE));
            return InteractionResult.FAIL;
        }
        if (stack.getOrDefault(MxtDataComponents.SPIRIT_BEAST, SpiritBeastComponent.EMPTY).stored()) {
            ItemFeedback.send(player, Component.translatable("item.mxt.spirit_beast_bag.occupied"));
            return InteractionResult.FAIL;
        }
        ContractAttachment contract = mob.getData(MxtAttachments.CONTRACT);
        if (!contract.bound() || Contracts.ownerOf(mob).filter(player.getUUID()::equals).isEmpty()) {
            ItemFeedback.send(player, ContractFeedback.of(ContractService.Failure.NOT_BOUND));
            return InteractionResult.FAIL;
        }
        // Told before the entity leaves the world, so anything it wants to shut down on the way out still has a
        // world to do it in.
        Contracts.captureListener(mob).ifPresent(listener -> listener.onCaptured(owner));
        stack.set(MxtDataComponents.SPIRIT_BEAST, SpiritBeastComponent.capture(mob, contract.contractType().orElseThrow(), owner));
        mob.discard();
        ItemFeedback.send(player, Component.translatable("item.mxt.spirit_beast_bag.stored"));
        return InteractionResult.SUCCESS;
    }

    // Releasing is aimed at a block - the creature comes back on top of it - because that is what lets the player
    // choose where it lands instead of always getting it at their own feet.
    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        SpiritBeastComponent stored = stack.getOrDefault(MxtDataComponents.SPIRIT_BEAST, SpiritBeastComponent.EMPTY);
        if (!stored.stored()) return InteractionResult.PASS;
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel) || player == null) return InteractionResult.FAIL;
        // The type is stored beside the data, so the entity is built from it and the saved data is read into it:
        // the creature's own data never has to carry an id of its own.
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(stored.entityType().orElseThrow()).orElse(null);
        Entity restored = type == null ? null : type.create(serverLevel, EntitySpawnReason.BUCKET);
        if (restored == null) return failed(player);
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, serverLevel.registryAccess(), stored.entity().orElseThrow().copy()));
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        restored.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYRot(), 0.0F);
        if (!serverLevel.addFreshEntity(restored)) return failed(player);
        if (restored instanceof Mob mob) Contracts.captureListener(mob).ifPresent(listener -> listener.onReleased(player));
        stack.set(MxtDataComponents.SPIRIT_BEAST, SpiritBeastComponent.EMPTY);
        ItemFeedback.send(player, Component.translatable("item.mxt.spirit_beast_bag.released"));
        return InteractionResult.SUCCESS_SERVER;
    }

    // An air click cannot say where the creature should come out, so it says how rather than doing nothing.
    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        SpiritBeastComponent stored = player.getItemInHand(hand).getOrDefault(MxtDataComponents.SPIRIT_BEAST, SpiritBeastComponent.EMPTY);
        if (level.isClientSide() || !stored.stored()) return InteractionResult.PASS;
        ItemFeedback.send(player, Component.translatable("item.mxt.spirit_beast_bag.release_hint"));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult failed(Player player) {
        ItemFeedback.send(player, Component.translatable("item.mxt.spirit_beast_bag.release_failed"));
        return InteractionResult.FAIL;
    }
}
