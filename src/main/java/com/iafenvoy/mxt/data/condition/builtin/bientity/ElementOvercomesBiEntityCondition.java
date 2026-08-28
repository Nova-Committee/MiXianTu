package com.iafenvoy.mxt.data.condition.builtin.bientity;

import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;

import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.attachment.SpiritAttachment;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * True when at least one of the actor's spirit-root elements overcomes a target root element.
 */
public enum ElementOvercomesBiEntityCondition implements BiEntityCondition {
    INSTANCE;
    public static final MapCodec<ElementOvercomesBiEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(BiEntityConditionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        Set<Holder<Element>> actorElements = elements(actor.getData(MxtAttachments.SPIRIT_DATA));
        Set<Holder<Element>> targetElements = elements(target.getData(MxtAttachments.SPIRIT_DATA));
        return actorElements.stream().anyMatch(element -> targetElements.stream()
                .anyMatch(targetElement -> RegistryCodecs.matches(element.value().overcomes(), targetElement)));
    }

    private static Set<Holder<Element>> elements(SpiritAttachment spirit) {
        return spirit.spiritRoots().stream().flatMap(root -> MxtDatapackRegistries.get(MxtResourceKeys.SPIRIT_ROOT, root).stream())
                .map(SpiritRoot::element).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public MapCodec<ElementOvercomesBiEntityCondition> codec() {
        return CODEC;
    }
}
