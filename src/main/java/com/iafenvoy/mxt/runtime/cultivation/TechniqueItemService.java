package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueService.Result;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;

import java.util.Locale;
import java.util.Optional;

/**
 * Server-side use path for books, jade slips, and other technique items.
 *
 * <p>A matching binding always claims the interaction, but a refusal is reported rather than
 * swallowed: the item gate answers first, then the learning transaction, and whichever one refuses
 * says why on the action bar. That message is the only feedback a player gets, because a rejected
 * learning attempt changes nothing they could observe.</p>
 *
 * <p>On the event path the gate has usually refused already: {@link ItemQualityService} watches the
 * same interaction at a higher priority and cancels it, so the gate check here speaks for direct
 * callers of {@link #use}.</p>
 */
@EventBusSubscriber
public final class TechniqueItemService {
    private TechniqueItemService() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemUse(RightClickItem event) {
        if (use(event.getEntity(), event.getEntity().getItemInHand(event.getHand()))) event.setCanceled(true);
    }

    /**
     * Attempts to learn a matching technique. A matching binding claims the
     * interaction even when learning is rejected by its normal conditions.
     */
    public static boolean use(LivingEntity entity, ItemStack stack) {
        if (entity.level().isClientSide()) return false;
        Optional<TechniqueBinding> binding = ItemBindingService.technique(stack);
        if (binding.isEmpty()) return false;
        TechniqueBinding value = binding.orElseThrow();
        Optional<ItemQualityService.Failure> refused = ItemQualityService.check(entity, stack);
        if (refused.isPresent()) {
            ItemQualityService.notifyCannotUse(entity, refused.orElseThrow());
            return true;
        }
        SpiritIdentityAttachment spirit = entity.getData(MxtAttachments.SPIRIT_IDENTITY);
        Result result = TechniqueService.learn(entity, spirit, value.technique(), FormulaContext.of(entity));
        if (!result.learned()) notifyLearnFailure(entity, value.technique(), result);
        return true;
    }

    /**
     * Sends the user-facing reason a learning attempt was rejected.
     */
    private static void notifyLearnFailure(LivingEntity entity, Holder<CultivationTechnique> technique, Result result) {
        if (!(entity instanceof ServerPlayer player) || result.failure() == null) return;
        player.sendSystemMessage(Component.translatable("actionbar.mxt.technique.failed",
                        DefinitionText.name(technique, "cultivation_technique"),
                        Component.translatable("actionbar.mxt.technique.failure." + result.failure().name().toLowerCase(Locale.ROOT)))
                .withStyle(ChatFormatting.RED), true);
    }
}
