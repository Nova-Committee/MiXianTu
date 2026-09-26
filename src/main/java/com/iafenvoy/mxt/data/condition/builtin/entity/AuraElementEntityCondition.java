package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraRequirement;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Map.Entry;

/**
 * Tests the ambient aura at an entity's position by element rather than by aura id: every live aura carrying that
 * element is summed, and every listed entry has to pass.
 */
public record AuraElementEntityCondition(Map<Holder<Element>, AuraRequirement> elements) implements EntityCondition {
    public static final MapCodec<AuraElementEntityCondition> CODEC = CollectionCodecs.map(Element.CODEC, AuraRequirement.CODEC)
            .fieldOf("elements").xmap(AuraElementEntityCondition::new, AuraElementEntityCondition::elements)
            .validate(AuraElementEntityCondition::validate);

    // An empty table would be a condition that passes everywhere, a gate nobody can see is missing: refused at load.
    private static DataResult<AuraElementEntityCondition> validate(AuraElementEntityCondition condition) {
        return condition.elements().isEmpty()
                ? DataResult.error(() -> "mxt:aura_element needs at least one element to ask about")
                : DataResult.success(condition);
    }

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        AuraResult resolved = AuraService.getPositionAura(entity.level(), entity.blockPosition());
        return this.elements.entrySet().stream().allMatch(entry -> entry.getValue().test(concentration(resolved, entry.getKey()), ctx.formula()));
    }

    // All live auras of one element at this position, summed.
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
