package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.type.ModifierAbilityType;
import com.iafenvoy.mxt.data.AttributeEntry;
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

    public static List<ResolvedModifier> resolve(AbilityAttachment holder) {
        List<ResolvedModifier> result = new ArrayList<>();
        for (Holder<Ability> ability : holder.sources().keySet()) {
            Ability definition = ability.value();
            if (!(definition.type() instanceof ModifierAbilityType))
                continue;
            for (AttributeEntry modifier : definition.modifiers())
                result.add(new ResolvedModifier(HolderHelper.id(ability), modifier));
        }
        return result;
    }

    public record ResolvedModifier(Identifier ability, AttributeEntry modifier) {
    }
}
