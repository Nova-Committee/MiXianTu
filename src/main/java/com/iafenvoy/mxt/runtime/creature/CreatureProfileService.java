package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.data.creature.CreatureProfile;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Selects and persists a tagged creature profile after evaluating its entity condition, and reads the persisted
 * profile back for other modules that need it, such as contract eligibility.
 */
public final class CreatureProfileService {
    private CreatureProfileService() {
    }

    public static Optional<Identifier> select(Mob creature) {
        return MxtDatapackRegistries.holders(MxtResourceKeys.CREATURE_PROFILE).map(holder -> holder.key().identifier())
                .filter(id -> MxtDatapackRegistries.get(MxtResourceKeys.CREATURE_PROFILE, id).filter(value -> matchesType(creature, value)).isPresent())
                .min(Comparator.naturalOrder());
    }

    public static boolean apply(Mob creature, Identifier id, CreatureProfile definition, FormulaContext context) {
        if (!definition.condition().test(creature, context))
            return false;
        AuraResult aura = AuraService.getPositionAura(creature.level(), creature.blockPosition());
        if (!definition.minimumAura().entrySet().stream().allMatch(entry -> {
            double minimum = entry.getValue().evaluate(context);
            return Double.isFinite(minimum) && minimum >= 0.0D && aura.pool(entry.getKey()).amount() >= minimum;
        })
                || (!definition.preferredAuraElements().isEmpty() && aura.aura().entrySet().stream().noneMatch(element -> element.getValue().amount() > 0.0D
                && element.getKey().value().auraType()
                .filter(type -> Elements.matches(definition.preferredAuraElements(), type)).isPresent())))
            return false;
        final double intelligence;
        try {
            intelligence = definition.intelligence().evaluate(context);
        } catch (RuntimeException exception) {
            return false;
        }
        if (!Double.isFinite(intelligence) || intelligence < 0.0D) return false;
        MxtDatapackRegistries.holder(MxtResourceKeys.CREATURE_PROFILE, id)
                .ifPresent(profile -> creature.getData(MxtAttachments.CREATURE_SPIRIT)
                        .apply(profile, intelligence, definition.innerCore(), definition.lootTable()));
        return true;
    }

    public static boolean matchesType(Mob creature, CreatureProfile definition) {
        Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(creature.getType());
        return RegistryCodecs.matches(definition.entityTypeTags(), BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE, type);
    }

    // contract_tags entries are read inside the mxt:contract_type namespace, so each names the type's own id or a
    // native tag it declares. Client-safe: a client answers "no match" rather than throwing.
    public static boolean declaresContract(Entity creature, Holder<ContractType> type) {
        List<Identifier> declared = creature.getData(MxtAttachments.CREATURE_SPIRIT).profile()
                .map(Holder::value)
                .map(CreatureProfile::contractTags)
                .orElse(List.of());
        if (declared.isEmpty()) return false;
        Identifier contractId = HolderHelper.idOrNull(type);
        return declared.stream().anyMatch(entry -> entry.equals(contractId)
                || type.is(TagKey.create(MxtResourceKeys.CONTRACT_TYPE, entry)));
    }

    public static boolean applySelected(Mob creature) {
        if (creature.getData(MxtAttachments.CREATURE_SPIRIT).profile().isPresent()) return false;
        Identifier id = select(creature).orElse(null);
        return id != null && MxtDatapackRegistries.get(MxtResourceKeys.CREATURE_PROFILE, id).map(definition -> apply(creature, id, definition, FormulaContext.of(creature))).orElse(false);
    }
}
