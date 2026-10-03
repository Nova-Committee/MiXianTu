package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService.Failure;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService.Result;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
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
import org.jspecify.annotations.Nullable;

/**
 * A physical physique: using the stack grants the one its component names. The grant goes through the same
 * authoritative service the command and the action use, so an exclusive tag, a condition or a repeat is refused
 * rather than bypassed.
 */
public final class PhysiqueItem extends Item {
    public PhysiqueItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        Holder<Physique> physique = stack.get(MxtDataComponents.PHYSIQUE);
        if (physique == null) {
            ItemFeedback.send(player, Component.translatable("item.mxt.physique.unbound"));
            return InteractionResult.FAIL;
        }
        Result result = CultivationIdentityService.grantPhysique(serverPlayer, HolderHelper.id(physique),
                physique.value(), FormulaContext.of(serverPlayer));
        if (!result.changed()) {
            ItemFeedback.send(player, failure(result.failure()));
            return InteractionResult.FAIL;
        }
        // Only a physique actually gained is spent, and a creative player keeps it so the picker can be used freely.
        if (!player.hasInfiniteMaterials()) stack.shrink(1);
        ItemFeedback.send(player, Component.translatable("item.mxt.physique.granted", DefinitionText.name(physique, "physique")));
        return InteractionResult.SUCCESS_SERVER;
    }

    private static Component failure(@Nullable Failure failure) {
        if (failure == null) return Component.translatable("item.mxt.physique.failed", "-");
        return switch (failure) {
            case ALREADY_HELD -> Component.translatable("item.mxt.physique.already_held");
            case CONDITIONS -> Component.translatable("item.mxt.physique.conditions");
            case EXCLUSIVE_CONFLICT -> Component.translatable("item.mxt.physique.conflict");
            case DISABLED -> Component.translatable("item.mxt.physique.disabled");
            default -> Component.translatable("item.mxt.physique.failed", failure.name());
        };
    }
}
