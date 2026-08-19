package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Server-side use path for books, jade slips, and other technique items. */
public final class TechniqueItemService {
    private TechniqueItemService() {
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
        SpiritData spirit = entity.getData(MxtAttachments.SPIRIT_DATA);
        TechniqueService.Result result = TechniqueService.learn(entity, spirit, HolderHelper.id(value.technique()),
                value.technique().value(), ignored -> Optional.empty(), FormulaContext.of(entity));
        if (result.learned() && value.setActive()) spirit.setActiveTechnique(value.technique());
        return true;
    }
}
