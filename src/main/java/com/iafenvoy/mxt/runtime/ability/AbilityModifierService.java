package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.attachment.AbilityHolderData;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.type.ModifierAbilityType;
import com.iafenvoy.mxt.data.AttributeModifier;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves passive modifier abilities on demand without duplicate attribute mutation.
 */
public final class AbilityModifierService {
    private AbilityModifierService() {
    }

    public static List<ResolvedModifier> resolve(AbilityHolderData holder) {
        List<ResolvedModifier> result = new ArrayList<>();
        for (Holder<Ability> ability : holder.sources().keySet()) {
            Ability definition = ability.value();
            if (!(definition.type() instanceof ModifierAbilityType))
                continue;
            for (AttributeModifier modifier : definition.modifiers())
                result.add(new ResolvedModifier(HolderHelper.id(ability), modifier));
        }
        return result;
    }

    public record ResolvedModifier(Identifier ability, AttributeModifier modifier) {
    }
}
