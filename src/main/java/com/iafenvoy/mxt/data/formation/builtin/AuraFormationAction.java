package com.iafenvoy.mxt.data.formation.builtin;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.Map;

/**
 * The aura module: the array replaces the aura resolved at its own position and raises the ceiling of what that
 * resolution provides. It answers none of the four beats, because the aura query reads the declaration itself.
 * {@code aura_zone} is required rather than optional: a bonus is applied to the resources of the zone the override
 * selected, so a module declaring only {@code max_bonus} would be silently worthless.
 */
public record AuraFormationAction(Holder<AuraZone> auraZone,
                                  Map<Holder<Aura>, NumberProvider> maxBonus) implements FormationActionType {
    public static final MapCodec<AuraFormationAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            AuraZone.CODEC.fieldOf("aura_zone").forGetter(AuraFormationAction::auraZone),
            CollectionCodecs.map(Aura.CODEC, NumberProvider.CODEC).optionalFieldOf("max_bonus", Map.of()).forGetter(AuraFormationAction::maxBonus)
    ).apply(i, AuraFormationAction::new));

    @Override
    public MapCodec<AuraFormationAction> codec() {
        return CODEC;
    }
}
