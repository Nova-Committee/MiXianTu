package com.iafenvoy.mxt.data.formation;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * One functional module of a formation, selected by a datapack {@code type}.
 *
 * <p>A formation's framework — its structure, its radius, what it costs to raise and to keep — is
 * declared once on {@link com.iafenvoy.mxt.data.Formation}. What the formation <em>does</em> is a list
 * of these, so a single array can build an array that both hurts strangers and heals the owner's
 * friends, and each module carries only the fields its own job needs.</p>
 *
 * <p>The split is deliberate rather than cosmetic: a flat record with a field per feature would grow a
 * field for every future array, and every one of those fields would be meaningless for every other
 * array. Adding a module here costs one record and one registry line, and existing definitions are not
 * touched — unknown fields were never read in the first place.</p>
 *
 * <p>Concrete behaviour lives in the runtime, which switches on the module's record type. That keeps
 * this package free of world access, exactly as {@code AbilityType} does for abilities: the type says
 * which algorithm, and the runtime owns what that algorithm does to a level.</p>
 */
public interface FormationActionType {
    /// A codec rather than a map codec, because a formation holds a <em>list</em> of these and only a
    /// codec can be listed. The dispatch itself is unchanged: one {@code type} field picks the record.
    Codec<FormationActionType> CODEC = MxtRegistries.FORMATION_ACTION_TYPE.byNameCodec()
            .dispatch("type", FormationActionType::codec, Function.identity());

    MapCodec<? extends FormationActionType> codec();
}
