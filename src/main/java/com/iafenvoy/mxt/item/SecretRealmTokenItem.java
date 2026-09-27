package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.attachment.SecretRealmTravelAttachment;
import com.iafenvoy.mxt.data.item.SecretRealmTokenComponent;
import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.world.SecretRealmService;
import com.iafenvoy.mxt.runtime.world.SecretRealmService.Result;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Enters a bound secret realm, or returns the traveller to their saved origin. A refusal carries the
 * definition's own message when there is one, so a secret realm can explain itself instead of showing a failure code.
 */
public final class SecretRealmTokenItem extends Item {
    public SecretRealmTokenItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        SecretRealmTravelAttachment travel = serverPlayer.getData(MxtAttachments.SECRET_REALM_TRAVEL);
        if (travel.active()) {
            Result result = SecretRealmService.exit(serverPlayer);
            ItemFeedback.send(player, result.changed()
                    ? Component.translatable("item.mxt.secret_realm_token.exited")
                    : result.message().orElseGet(() -> Component.translatable("item.mxt.secret_realm_token.exit_failed")));
            return result.changed() ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
        }
        SecretRealmTokenComponent token = stack.getOrDefault(MxtDataComponents.SECRET_REALM_TOKEN, SecretRealmTokenComponent.EMPTY);
        if (token.realm().isEmpty()) {
            ItemFeedback.send(player, Component.translatable("item.mxt.secret_realm_token.unbound"));
            return InteractionResult.FAIL;
        }
        Holder<SecretRealm> realm = token.realm().orElseThrow();
        Result result = SecretRealmService.enter(serverPlayer, serverPlayer.level().getServer(), realm);
        ItemFeedback.send(player, result.changed()
                ? Component.translatable("item.mxt.secret_realm_token.entered")
                : result.message().orElseGet(() -> Component.translatable("item.mxt.secret_realm_token.enter_failed", result.failure().name())));
        return result.changed() ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
    }
}
