package com.iafenvoy.mxt.data.talisman.type;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.talisman.TalismanType;
import com.iafenvoy.mxt.data.talisman.TalismanUse;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * The user is the only target, so the bi-entity half runs with the user on both sides. A use that lands on nobody
 * never happens, so it is always runnable.
 */
public record SelfTalismanType(int maxUse, EntityAction entityAction,
                               BiEntityAction biEntityAction) implements TalismanType {
    public static final MapCodec<SelfTalismanType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("max_use", 0).forGetter(SelfTalismanType::maxUse),
            EntityAction.optionalCodec("entity_action").forGetter(SelfTalismanType::entityAction),
            BiEntityAction.optionalCodec("bi_entity_action").forGetter(SelfTalismanType::biEntityAction)
    ).apply(i, SelfTalismanType::new));

    @Override
    public Plan plan(TalismanUse use) {
        return Plan.of(List.of(use.user()));
    }

    @Override
    public void apply(TalismanUse use, Plan plan) {
        TalismanType.runActions(this.entityAction, this.biEntityAction, use, plan.targets());
    }

    @Override
    public MapCodec<SelfTalismanType> codec() {
        return CODEC;
    }
}
