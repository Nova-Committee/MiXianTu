package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.cache.RegistryCaches;
import com.iafenvoy.mxt.util.codec.OptionalCodecs;
import com.iafenvoy.mxt.util.math.CompareOperation;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.OptionalInt;

/**
 * Asks what realm the entity stands in, and optionally how far it has got inside that realm. Only the chain the
 * named realm belongs to is consulted, and a body that never entered that chain stands on its entry stage. The
 * minor stage is read from the body's own record rather than from live progress, so a layer that was reached
 * stays reached - that is what makes a threshold on it usable as a gate.
 */
public record RealmEntityCondition(Holder<RealmStage> realm, CompareOperation comparison,
                                   OptionalInt minMinorStage) implements EntityCondition {
    public static final MapCodec<RealmEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RealmStage.CODEC.fieldOf("realm").forGetter(RealmEntityCondition::realm),
            CompareOperation.CODEC.optionalFieldOf("comparison", CompareOperation.EQUAL).forGetter(RealmEntityCondition::comparison),
            OptionalCodecs.intCodec("min_minor_stage").forGetter(RealmEntityCondition::minMinorStage)
    ).apply(i, RealmEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        CultivationAttachment cultivation = entity.getData(MxtAttachments.CULTIVATION);
        Holder<Aura> aura = this.realm.value().aura();
        Holder<RealmStage> current = cultivation.realmStage(aura);
        // A body with no record on this chain stands on its entry stage, which is what makes a mortal an entry.
        if (current == null) current = aura.value().firstRealm().orElse(null);
        // Both sides are ranks inside that one walked chain, so a stage of another aura satisfies nothing.
        boolean inRealm = current != null && RegistryCaches.REALM_CHAIN.compare(this.comparison, current, this.realm);
        if (!inRealm || this.minMinorStage.isEmpty()) return inRealm;
        // -1 for a realm the body never entered, so a threshold is never met by a realm it has not stood in.
        return entity.getExistingData(MxtAttachments.MINOR_STAGES)
                .map(stages -> stages.minorStageRecord(this.realm) >= this.minMinorStage.getAsInt())
                .orElse(false);
    }

    @Override
    public @NonNull MapCodec<RealmEntityCondition> codec() {
        return CODEC;
    }
}
