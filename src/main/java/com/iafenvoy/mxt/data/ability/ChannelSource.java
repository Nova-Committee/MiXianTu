package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.util.formula.NumberProvider;

import java.util.List;

/**
 * A type a player holds on to: while the channel is up it pulses every {@code channel_interval} and each pulse pays
 * the upkeep. The runtime asks through this interface instead of through the class, so what a channel is stays one
 * question with one answer.
 */
public interface ChannelSource {
    NumberProvider channelInterval();

    List<Cost> upkeepCosts();
}
