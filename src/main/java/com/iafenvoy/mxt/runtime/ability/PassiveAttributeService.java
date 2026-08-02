package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.common.AttributeModifierDefinition;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.ability.AbilityModifierService.ResolvedModifier;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies all datapack-defined passive modifiers as removable transient vanilla attributes.
 */
public final class PassiveAttributeService {
    private static final String PREFIX = "passive/";

    private PassiveAttributeService() {
    }

    /**
     * Reconciles the full generated modifier set. Call only on the server thread.
     */
    public static void reconcile(LivingEntity entity) {
        removePreviouslyGenerated(entity);
        List<Entry> entries = new ArrayList<>();
        Map<Identifier, Integer> abilityIndices = new HashMap<>();
        for (ResolvedModifier value : AbilityModifierService.resolve(entity.getData(MxtAttachments.ABILITY_HOLDER), id ->
                MxtDatapackRegistries.get(MxtDatapackRegistries.ABILITY, id).orElse(null))) {
            int index = abilityIndices.merge(value.ability(), 1, Integer::sum) - 1;
            entries.add(new Entry("ability", value.ability(), index, value.modifier()));
        }

        SpiritData spirit = entity.getData(MxtAttachments.SPIRIT_DATA);
        spirit.realmStage().flatMap(id -> MxtDatapackRegistries.get(MxtDatapackRegistries.REALM_STAGE, id))
                .ifPresent(value -> addAll(entries, "realm", spirit.realmStage().orElseThrow(), value.passiveModifiers()));
        spirit.activeTechnique().flatMap(id -> MxtDatapackRegistries.get(MxtDatapackRegistries.CULTIVATION_TECHNIQUE, id))
                .ifPresent(value -> addAll(entries, "technique", spirit.activeTechnique().orElseThrow(), value.passiveModifiers()));
        for (Identifier id : spirit.physiques())
            MxtDatapackRegistries.get(MxtDatapackRegistries.PHYSIQUE, id)
                    .ifPresent(value -> addAll(entries, "physique", id, value.attributeModifiers()));
        for (Identifier id : spirit.titles())
            MxtDatapackRegistries.get(MxtDatapackRegistries.TITLE, id)
                    .ifPresent(value -> addAll(entries, "title", id, value.passiveModifiers()));

        FormulaContext context = FormulaContexts.forEntity(entity);
        for (Entry entry : entries) apply(entity, entry, context);
    }

    private static void removePreviouslyGenerated(LivingEntity entity) {
        for (AttributeInstance instance : entity.getAttributes().getSyncableAttributes()) {
            instance.getModifiers().stream().filter(modifier -> modifier.id().getNamespace().equals("mxt")
                            && modifier.id().getPath().startsWith(PREFIX)).map(AttributeModifier::id).toList()
                    .forEach(instance::removeModifier);
        }
    }

    private static void addAll(List<Entry> target, String kind, Identifier source, List<AttributeModifierDefinition> values) {
        for (int index = 0; index < values.size(); index++)
            target.add(new Entry(kind, source, index, values.get(index)));
    }

    private static void apply(LivingEntity entity, Entry entry, FormulaContext context) {
        AttributeModifierDefinition definition = entry.definition();
        Holder<Attribute> attribute = definition.attribute();
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;
        final double amount;
        try {
            amount = definition.value().evaluate(context);
        } catch (RuntimeException ignored) {
            return;
        }
        if (!Double.isFinite(amount)) return;
        Identifier id = Identifier.fromNamespaceAndPath("mxt", PREFIX + entry.kind() + "/" + entry.source().getNamespace() + "/" + entry.source().getPath() + "/" + entry.index());
        instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation(definition.operation())));
    }

    private static Operation operation(AttributeModifierDefinition.Operation value) {
        return switch (value) {
            case ADD_VALUE -> Operation.ADD_VALUE;
            case ADD_MULTIPLIED_BASE -> Operation.ADD_MULTIPLIED_BASE;
            case ADD_MULTIPLIED_TOTAL -> Operation.ADD_MULTIPLIED_TOTAL;
        };
    }

    private record Entry(String kind, Identifier source, int index, AttributeModifierDefinition definition) {
    }
}
