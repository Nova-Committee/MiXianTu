package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.data.creature.CreatureProfile;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
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

    public static boolean apply(Mob creature, Identifier id, CreatureProfile definition, FormulaContext context) {
        if (!definition.spawnConditions().stream().allMatch(condition -> condition.test(creature, context)))
            return false;
        AuraResult aura = AuraService.getPositionAura(creature.level(), creature.blockPosition());
        if (!Double.isFinite(definition.minimumAura()) || aura.concentration() < definition.minimumAura()
                || (!definition.preferredAuraElements().isEmpty() && aura.elementAura().entrySet().stream().noneMatch(element -> element.getValue() > 0.0D
                && RegistryCodecs.matches(definition.preferredAuraElements(), element.getKey()))))
            return false;
        final double intelligence;
        try {
            intelligence = definition.intelligence().evaluate(context);
        } catch (RuntimeException exception) {
            return false;
        }
        if (!Double.isFinite(intelligence) || intelligence < 0.0D) return false;
        MxtDatapackRegistries.holder(MxtDatapackRegistries.CREATURE_PROFILE, id)
                .ifPresent(profile -> creature.getData(MxtAttachments.CREATURE_SPIRIT).apply(profile, intelligence, definition.innerCore(), definition.lootTable()));
        return true;
    }

    public static boolean matchesType(Mob creature, CreatureProfile definition) {
        Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(creature.getType());
        return RegistryCodecs.matches(definition.entityTypeTags(), BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE, type);
    }

    public static boolean applySelected(Mob creature) {
        if (creature.getData(MxtAttachments.CREATURE_SPIRIT).profile().isPresent()) return false;
        Identifier id = select(creature).orElse(null);
        return id != null && MxtDatapackRegistries.get(MxtDatapackRegistries.CREATURE_PROFILE, id).map(definition -> apply(creature, id, definition, FormulaContext.of(creature))).orElse(false);
    }
}
