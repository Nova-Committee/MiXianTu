package com.iafenvoy.mxt.data.condition.builtin;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

public record RealmEntityCondition(Identifier realm) implements EntityCondition {
    public static final MapCodec<RealmEntityCondition> CODEC = Identifier.CODEC.fieldOf("realm").xmap(RealmEntityCondition::new, RealmEntityCondition::realm);

    @Override
    public boolean test(Entity entity) {
        return entity.getData(MxtAttachments.SPIRIT_DATA).realmStage().filter(this.realm::equals).isPresent();
    }

    @Override
    public MapCodec<RealmEntityCondition> codec() {
        return CODEC;
    }
}
