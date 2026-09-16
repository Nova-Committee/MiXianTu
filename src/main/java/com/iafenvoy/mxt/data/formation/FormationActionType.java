package com.iafenvoy.mxt.data.formation;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * One functional module of a formation, selected by a datapack {@code type}. A formation's framework —
 * structure, radius, cost — is declared once on {@link com.iafenvoy.mxt.data.Formation}; what it does is a list
 * of these, so one array can hurt strangers and heal the owner's friends at once. Adding a module costs one
 * record and one registry line; concrete behaviour lives in the runtime, which switches on the record type.
 */
public interface FormationActionType {
    /// A codec rather than a map codec, because a formation holds a <em>list</em> of these and only a
    /// codec can be listed. The dispatch itself is unchanged: one {@code type} field picks the record.
    Codec<FormationActionType> CODEC = MxtRegistries.FORMATION_ACTION_TYPE.byNameCodec()
            .dispatch("type", FormationActionType::codec, Function.identity());

    MapCodec<? extends FormationActionType> codec();
}
