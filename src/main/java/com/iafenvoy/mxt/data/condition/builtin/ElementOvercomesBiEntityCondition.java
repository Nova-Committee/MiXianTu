package com.iafenvoy.mxt.data.condition.builtin;

import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.cultivation.SpiritRootDefinition;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.Set;
import java.util.stream.Collectors;

/** True when at least one of the actor's spirit-root elements overcomes a target root element. */
public enum ElementOvercomesBiEntityCondition implements BiEntityCondition {
    INSTANCE;
    public static final MapCodec<ElementOvercomesBiEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override public boolean test(Entity actor, Entity target, FormulaContext context) {
        Set<Identifier> actorElements = elements(actor.getData(MxtAttachments.SPIRIT_DATA));
        Set<Identifier> targetElements = elements(target.getData(MxtAttachments.SPIRIT_DATA));
        return actorElements.stream().anyMatch(element -> MxtDatapackRegistries.get(MxtDatapackRegistries.ELEMENT, element)
                .map(definition -> definition.overcomes().stream().anyMatch(targetElements::contains)).orElse(false));
    }

    private static Set<Identifier> elements(SpiritData spirit) {
        return spirit.spiritRoots().stream().flatMap(root -> MxtDatapackRegistries.get(MxtDatapackRegistries.SPIRIT_ROOT, root).stream())
                .map(SpiritRootDefinition::element).collect(Collectors.toUnmodifiableSet());
    }

    @Override public MapCodec<ElementOvercomesBiEntityCondition> codec() { return CODEC; }
}
