package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.registry.MxtDataComponents;

import com.iafenvoy.mxt.attachment.RealmInstanceAttachment;
import com.iafenvoy.mxt.attachment.RealmTravelAttachment;
import com.iafenvoy.mxt.data.RealmInstance;
import com.iafenvoy.mxt.data.item.RealmTokenComponent;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.world.RealmInstanceService;
import com.iafenvoy.mxt.runtime.world.RealmInstanceService.Result;
import com.iafenvoy.mxt.util.HolderHelper;
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
import org.jetbrains.annotations.NotNull;

/**
 * Enters a bound realm instance, or returns the traveller to their saved origin.
 */
public final class RealmTokenItem extends Item {
    public RealmTokenItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        RealmTravelAttachment travel = serverPlayer.getData(MxtAttachments.REALM_TRAVEL);
        if (travel.active()) {
            RealmInstanceAttachment instance = hostInstance(serverPlayer, travel);
            if (instance == null || !RealmInstanceService.exit(serverPlayer, instance).changed()) {
                ItemFeedback.send(player, Component.translatable("item.mxt.realm_token.exit_failed"));
                return InteractionResult.FAIL;
            }
            ItemFeedback.send(player, Component.translatable("item.mxt.realm_token.exited"));
            return InteractionResult.SUCCESS_SERVER;
        }
        RealmTokenComponent token = stack.getOrDefault(MxtDataComponents.REALM_TOKEN, RealmTokenComponent.EMPTY);
        if (token.realm().isEmpty()) {
            ItemFeedback.send(player, Component.translatable("item.mxt.realm_token.unbound"));
            return InteractionResult.FAIL;
        }
        Holder<RealmInstance> realm = token.realm().orElseThrow();
        Result result = RealmInstanceService.enter(serverPlayer,
                level.getData(MxtAttachments.REALM_INSTANCE), HolderHelper.id(realm), realm.value());
        if (!result.changed()) {
            ItemFeedback.send(player, Component.translatable("item.mxt.realm_token.enter_failed", result.failure().name()));
            return InteractionResult.FAIL;
        }
        ItemFeedback.send(player, Component.translatable("item.mxt.realm_token.entered"));
        return InteractionResult.SUCCESS_SERVER;
    }

    private static RealmInstanceAttachment hostInstance(ServerPlayer player, RealmTravelAttachment travel) {
        Holder<RealmInstance> realm = travel.realm().orElse(null);
        if (realm == null) return null;
        for (ServerLevel candidate : player.level().getServer().getAllLevels()) {
            RealmInstanceAttachment data = candidate.getData(MxtAttachments.REALM_INSTANCE);
            if (data.definition().filter(realm::equals).isPresent() && data.members().contains(player.getUUID()))
                return data;
        }
        return null;
    }
}
