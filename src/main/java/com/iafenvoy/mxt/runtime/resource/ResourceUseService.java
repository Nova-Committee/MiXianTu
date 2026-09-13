package com.iafenvoy.mxt.runtime.resource;

import com.iafenvoy.mxt.data.cultivation.CultivationProfile;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.runtime.cultivation.CultivationProfiles;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves the optional resource use gate for entity-driven interactions.
 */
public final class ResourceUseService {
    private ResourceUseService() {
    }

    /**
     * A value without a cultivation profile has no gate and is always usable; a profiled value asks
     * its profile, which is what keeps the gate attached to the cultivation definition.
     */
    public static boolean canUse(@NotNull LivingEntity entity, @NotNull Holder<Resource> resource) {
        CultivationProfile profile = CultivationProfiles.find(entity, resource).orElse(null);
        if (profile == null) return true;
        FormulaContext context = ResourceService.formulaContext(entity, resource, FormulaContext.of(entity));
        return profile.useCondition().test(entity, context);
    }
}
