package com.iafenvoy.mxt.data.formation;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * One functional module of a formation, selected by a datapack {@code type}. A formation's framework (structure,
 * radius, cost) is declared once on {@link com.iafenvoy.mxt.data.Formation}; what it does is a list of these.
 * Concrete behaviour lives in the runtime, which switches on the record type.
 */
public interface FormationActionType {
    // A codec rather than a map codec, because a formation holds a list of these and only a codec can be listed.
    Codec<FormationActionType> CODEC = MxtRegistries.FORMATION_ACTION_TYPE.byNameCodec()
            .dispatch("type", FormationActionType::codec, Function.identity());

    MapCodec<? extends FormationActionType> codec();
}
