package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.type.ModifierAbilityType;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves passive modifier abilities on demand without duplicate attribute mutation.
 * <p>
 * A passive answers to its own {@code condition}, re-evaluated here on the same per-tick pass the rest of the
 * attribute service runs on, so an ability can grant its modifiers only while the holder's state says so - the
 * behaviour an {@code aura} already had.
 */
public final class AbilityModifierService {
    private AbilityModifierService() {
    }

    public static List<ResolvedModifier> resolve(LivingEntity entity, AbilityAttachment holder) {
        List<ResolvedModifier> result = new ArrayList<>();
        FormulaContext context = FormulaContext.of(entity);
        for (Holder<Ability> ability : holder.sources().keys()) {
            Ability definition = ability.value();
            if (!(definition.type() instanceof ModifierAbilityType)) continue;
            if (!definition.condition().test(entity, context)) continue;
            for (AttributeEntry modifier : definition.modifiers())
                result.add(new ResolvedModifier(HolderHelper.id(ability), modifier));
        }
        return result;
    }

    public record ResolvedModifier(Identifier ability, AttributeEntry modifier) {
    }
}
