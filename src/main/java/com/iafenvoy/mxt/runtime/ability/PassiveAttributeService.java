package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.AttributeModifier;
import com.iafenvoy.mxt.data.Title;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ability.AbilityModifierService.ResolvedModifier;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
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
        for (ResolvedModifier value : AbilityModifierService.resolve(entity.getData(MxtAttachments.ABILITY_HOLDER))) {
            int index = abilityIndices.merge(value.ability(), 1, Integer::sum) - 1;
            entries.add(new Entry("ability", value.ability(), index, value.modifier()));
        }

        SpiritData spirit = entity.getData(MxtAttachments.SPIRIT_DATA);
        spirit.realmStage().ifPresent(realm -> addAll(entries, "realm", HolderHelper.id(realm), realm.value().passiveModifiers()));
        spirit.activeTechnique().ifPresent(technique -> addAll(entries, "technique", HolderHelper.id(technique), technique.value().passiveModifiers()));
        for (Holder<Physique> physique : spirit.physiques())
            addAll(entries, "physique", HolderHelper.id(physique), physique.value().attributeModifiers());
        for (Holder<Title> title : spirit.titles())
            addAll(entries, "title", HolderHelper.id(title), title.value().passiveModifiers());

        FormulaContext context = FormulaContexts.forEntity(entity);
        for (Entry entry : entries) apply(entity, entry, context);
    }

    private static void removePreviouslyGenerated(LivingEntity entity) {
        for (AttributeInstance instance : entity.getAttributes().getSyncableAttributes()) {
            instance.getModifiers().stream().filter(modifier -> modifier.id().getNamespace().equals(MiXianTu.MOD_ID)
                            && modifier.id().getPath().startsWith(PREFIX)).map(net.minecraft.world.entity.ai.attributes.AttributeModifier::id).toList()
                    .forEach(instance::removeModifier);
        }
    }

    private static void addAll(List<Entry> target, String kind, Identifier source, List<AttributeModifier> values) {
        for (int index = 0; index < values.size(); index++)
            target.add(new Entry(kind, source, index, values.get(index)));
    }

    private static void apply(LivingEntity entity, Entry entry, FormulaContext context) {
        AttributeModifier definition = entry.definition();
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
        Identifier id = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, PREFIX + entry.kind() + "/" + entry.source().getNamespace() + "/" + entry.source().getPath() + "/" + entry.index());
        instance.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, amount, operation(definition.operation())));
    }

    private static Operation operation(AttributeModifier.Operation value) {
        return switch (value) {
            case ADD_VALUE -> Operation.ADD_VALUE;
            case ADD_MULTIPLIED_BASE -> Operation.ADD_MULTIPLIED_BASE;
            case ADD_MULTIPLIED_TOTAL -> Operation.ADD_MULTIPLIED_TOTAL;
        };
    }

    private record Entry(String kind, Identifier source, int index, AttributeModifier definition) {
    }
}
