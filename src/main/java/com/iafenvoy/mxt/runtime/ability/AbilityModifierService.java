package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.attachment.AbilityHolderData;
import com.iafenvoy.mxt.data.ability.AbilityDefinition;
import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.AbilityType.Modifier;
import com.iafenvoy.mxt.data.common.AttributeModifierDefinition;
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

    public static List<ResolvedModifier> resolve(AbilityHolderData holder, Function<Identifier, AbilityDefinition> definitions) {
        List<ResolvedModifier> result = new ArrayList<>();
        for (Identifier id : holder.sources().keySet()) {
            AbilityDefinition definition = definitions.apply(id);
            if (definition == null || !(definition.typedType() instanceof Modifier))
                continue;
            for (AttributeModifierDefinition modifier : definition.modifiers())
                result.add(new ResolvedModifier(id, modifier));
        }
        return List.copyOf(result);
    }

    public record ResolvedModifier(Identifier ability, AttributeModifierDefinition modifier) {
    }
}
