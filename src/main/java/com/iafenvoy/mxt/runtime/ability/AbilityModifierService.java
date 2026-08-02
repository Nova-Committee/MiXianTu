package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.attachment.AbilityHolderData;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.AbilityType.Modifier;
import com.iafenvoy.mxt.data.AttributeModifier;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Resolves passive modifier abilities on demand without duplicate attribute mutation.
 */
public final class AbilityModifierService {
    private AbilityModifierService() {
    }

    public static List<ResolvedModifier> resolve(AbilityHolderData holder, Function<Identifier, Ability> definitions) {
        List<ResolvedModifier> result = new ArrayList<>();
        for (Identifier id : holder.sources().keySet()) {
            Ability definition = definitions.apply(id);
            if (definition == null || !(definition.type() instanceof Modifier))
                continue;
            for (AttributeModifier modifier : definition.modifiers())
                result.add(new ResolvedModifier(id, modifier));
        }
        return List.copyOf(result);
    }

    public record ResolvedModifier(Identifier ability, AttributeModifier modifier) {
    }
}
