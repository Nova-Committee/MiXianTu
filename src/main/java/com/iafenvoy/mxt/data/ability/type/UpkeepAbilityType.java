package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.Togglable;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The periodic price of carrying the ability's carrier: the ability's own {@code costs}, charged once every
 * {@code interval} ticks of world time while the carrier is on the holder. Paying is all or nothing, and an
 * unpayable price runs {@code on_fail} against the holder and the stack, which is where a backlash belongs.
 *
 * <p>Deliberately not a {@link Togglable}: nothing is pressed, the price falls on
 * whoever carries the thing.
 */
public record UpkeepAbilityType(NumberProvider interval, ItemAction onFail, boolean ownerOnly) implements AbilityType {
    // One second.
    public static final double DEFAULT_INTERVAL = 20.0D;
    public static final MapCodec<UpkeepAbilityType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.optionalFieldOf("interval", new Constant(DEFAULT_INTERVAL)).forGetter(UpkeepAbilityType::interval),
            ItemAction.optionalCodec("on_fail").forGetter(UpkeepAbilityType::onFail),
            Codec.BOOL.optionalFieldOf("owner_only", true).forGetter(UpkeepAbilityType::ownerOnly)
    ).apply(i, UpkeepAbilityType::new));

    @Override
    public MapCodec<UpkeepAbilityType> codec() {
        return CODEC;
    }
}
