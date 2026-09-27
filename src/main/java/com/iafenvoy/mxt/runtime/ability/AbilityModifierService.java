package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.ability.Abilities;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.type.ModifierAbilityType;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves passive modifier abilities on demand, without mutating attributes itself. A passive answers to its own
 * {@code condition}, re-evaluated on the same per-tick pass the attribute service runs on, so an ability grants
 * its modifiers only while the holder's state says so.
 *
 * <p>Every granted ability is read from the grant ledger by id, so an artifact's passives and a book's count the
 * same.
 */
public final class AbilityModifierService {
    private AbilityModifierService() {
    }

    public static List<ResolvedModifier> resolve(LivingEntity entity, AbilityAttachment holder) {
        List<ResolvedModifier> result = new ArrayList<>();
        FormulaContext context = FormulaContext.of(entity);
        for (Identifier id : holder.sources().keys()) {
            Holder<Ability> ability = Abilities.resolve(entity.level().registryAccess(), id).orElse(null);
            if (ability == null) continue;
            if (!(ability.value().type() instanceof ModifierAbilityType(List<AttributeEntry> modifiers))) continue;
            if (!ability.value().condition().test(entity, context)) continue;
            for (AttributeEntry entry : modifiers)
                result.add(new ResolvedModifier(id, entry));
        }
        return result;
    }

    public record ResolvedModifier(Identifier ability, AttributeEntry modifier) {
    }
}
