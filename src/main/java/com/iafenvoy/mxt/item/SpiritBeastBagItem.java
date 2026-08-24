package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.registry.MxtDataComponents;

import com.iafenvoy.mxt.attachment.ContractAttachment;
import com.iafenvoy.mxt.data.item.SpiritBeastComponent;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.Level;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Portable storage for one owned, contracted mob. The entity's full persistent data is retained.
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
        if (!(target instanceof Mob mob)) return InteractionResult.PASS;
        SpiritBeastComponent stored = stack.getOrDefault(MxtDataComponents.SPIRIT_BEAST, SpiritBeastComponent.EMPTY);
        if (stored.entity().isPresent()) {
            ItemFeedback.send(player, Component.translatable("item.mxt.spirit_beast_bag.occupied"));
            return InteractionResult.FAIL;
        }
        ContractAttachment contract = mob.getData(MxtAttachments.CONTRACT);
        if (!contract.bound() || contract.owner().filter(player.getUUID()::equals).isEmpty()) {
            ItemFeedback.send(player, Component.translatable("item.mxt.spirit_beast_bag.not_owner"));
            return InteractionResult.FAIL;
        }
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, mob.level().registryAccess());
        mob.saveWithoutId(output);
        CompoundTag entity = output.buildResult();
        entity.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
        stack.set(MxtDataComponents.SPIRIT_BEAST, new SpiritBeastComponent(Optional.of(entity)));
        mob.discard();
        ItemFeedback.send(player, Component.translatable("item.mxt.spirit_beast_bag.stored"));
        return InteractionResult.SUCCESS;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        SpiritBeastComponent stored = stack.getOrDefault(MxtDataComponents.SPIRIT_BEAST, SpiritBeastComponent.EMPTY);
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (stored.entity().isEmpty() || !(level instanceof ServerLevel serverLevel)) return InteractionResult.FAIL;
        Entity restored = EntityType.loadEntityRecursive(stored.entity().orElseThrow(), serverLevel, EntitySpawnReason.BUCKET, entity -> {
            entity.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            return entity;
        });
        if (restored == null || !serverLevel.addFreshEntity(restored)) {
            ItemFeedback.send(player, Component.translatable("item.mxt.spirit_beast_bag.release_failed"));
            return InteractionResult.FAIL;
        }
        stack.set(MxtDataComponents.SPIRIT_BEAST, SpiritBeastComponent.EMPTY);
        ItemFeedback.send(player, Component.translatable("item.mxt.spirit_beast_bag.released"));
        return InteractionResult.SUCCESS_SERVER;
    }
}
