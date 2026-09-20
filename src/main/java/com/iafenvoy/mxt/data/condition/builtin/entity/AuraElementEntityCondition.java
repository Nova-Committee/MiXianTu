package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraRequirement;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Map.Entry;

/**
 * Tests the ambient aura at an entity's position by element rather than by aura.
 *
 * <p>{@code mxt:aura_range} asks about named auras, which is the wrong question for a pack that groups its
 * auras by element: "is this a fire-aligned place" would otherwise have to list every aura id that happens to
 * carry that element, and stay in step with them. Here the requirement is put on the element and every live
 * aura carrying it is summed, so adding another fire aura to a zone is enough on its own. The requirement is
 * the same lower/upper bound shape used everywhere else, and every entry has to pass.</p>
 */
public record AuraElementEntityCondition(Map<Holder<Element>, AuraRequirement> elements) implements EntityCondition {
    public static final MapCodec<AuraElementEntityCondition> CODEC = CollectionCodecs.map(Element.CODEC, AuraRequirement.CODEC)
            .fieldOf("elements").xmap(AuraElementEntityCondition::new, AuraElementEntityCondition::elements);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        AuraResult resolved = AuraService.getPositionAura(entity.level(), entity.blockPosition());
        return this.elements.entrySet().stream()
                .allMatch(entry -> entry.getValue().test(concentration(resolved, entry.getKey()), context));
    }

    /**
     * Every live aura of one element at this position, added up: the element is the key a pack asks by, and a
     * place is fire-aligned to the degree that all of its fire auras are present.
     */
    private static double concentration(AuraResult aura, Holder<Element> element) {
        double total = 0.0D;
        for (Entry<Holder<Aura>, AuraPool> entry : aura.aura().entrySet()) {
            if (!Elements.enabled(entry.getKey().value().auraType())) continue;
            if (entry.getKey().value().auraType().filter(element::equals).isEmpty()) continue;
            total += entry.getValue().amount();
        }
        return total;
    }

    @Override
    public @NonNull MapCodec<AuraElementEntityCondition> codec() {
        return CODEC;
    }
}
