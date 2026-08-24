package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritAttachment;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueService.Result;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;

import java.util.Optional;

/**
 * Server-side use path for books, jade slips, and other technique items.
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
        if (!ItemQualityService.canUse(entity, stack)) return true;
        TechniqueBinding value = binding.orElseThrow();
        SpiritAttachment spirit = entity.getData(MxtAttachments.SPIRIT_DATA);
        Result result = TechniqueService.learn(entity, spirit, HolderHelper.id(value.technique()),
                value.technique().value(), ignored -> Optional.empty(), FormulaContext.of(entity));
        if (result.learned() && value.setActive()) spirit.setActiveTechnique(value.technique());
        return true;
    }
}
