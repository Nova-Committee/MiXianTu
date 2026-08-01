package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.data.creature.CreatureProfileDefinition;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;

import java.util.Comparator;
import java.util.Optional;

/**
 * Selects and persists a tagged creature profile. Spawn conditions remain a caller-provided policy hook.
 */
public final class CreatureProfileService {
    private CreatureProfileService() {
    }

    public static Optional<Identifier> select(Mob creature) {
        return MxtDatapackRegistries.holders(MxtDatapackRegistries.CREATURE_PROFILE).map(holder -> holder.key().identifier())
                .filter(id -> MxtDatapackRegistries.get(MxtDatapackRegistries.CREATURE_PROFILE, id).filter(value -> matchesType(creature, value)).isPresent())
                .min(Comparator.naturalOrder());
    }

    public static boolean apply(Mob creature, Identifier id, CreatureProfileDefinition definition, FormulaContext context) {
        if (!definition.spawnConditions().stream().allMatch(condition -> MxtTypeRegistries.CREATURE_SPAWN_CONDITION.get(condition)
                .map(reference -> reference.value().test(creature, context)).orElse(false))) return false;
        final double intelligence;
        try {
            intelligence = definition.intelligence().evaluate(context);
        } catch (RuntimeException exception) {
            return false;
        }
        if (!Double.isFinite(intelligence) || intelligence < 0.0D) return false;
        creature.getData(MxtAttachments.CREATURE_SPIRIT).apply(id, intelligence, definition.innerCore(), definition.lootTable());
        return true;
    }

    public static boolean matchesType(Mob creature, CreatureProfileDefinition definition) {
        return definition.entityTypeTags().stream().map(tag -> TagKey.create(Registries.ENTITY_TYPE, tag))
                .anyMatch(tag -> creature.getType().builtInRegistryHolder().is(tag));
    }

    public static boolean applySelected(Mob creature) {
        if (creature.getData(MxtAttachments.CREATURE_SPIRIT).profile().isPresent()) return false;
        Identifier id = select(creature).orElse(null);
        return id != null && MxtDatapackRegistries.get(MxtDatapackRegistries.CREATURE_PROFILE, id).map(definition -> apply(creature, id, definition, FormulaContext.EMPTY)).orElse(false);
    }
}
