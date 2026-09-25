package com.iafenvoy.mxt.compat.kubejs.binding;

import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.runtime.cultivation.LifeSpanService;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The lifespan ledger exposed as {@code MxtLifespan}.
 */
public final class MxtKubeJsLifespanBindings {
    @Info("Ticks of life left, or -1 when the entity has no lifespan ledger.")
    public long remaining(Entity entity) {
        return MxtKubeJsApi.lifespanRemaining(entity);
    }

    @Info("The most life the entity was ever granted in this life, or -1 when it has no ledger.")
    public long total(Entity entity) {
        return MxtKubeJsApi.lifespanTotal(entity);
    }

    @Info("Rewrites both numbers of the ledger; a negative value is refused.")
    public LifeSpanService.Result set(LivingEntity entity, long ticks) {
        return MxtKubeJsApi.setLifespan(entity, ticks);
    }

    @Info("Adds to the ledger, extending the life; a negative amount takes life away.")
    public LifeSpanService.Result add(LivingEntity entity, long ticks) {
        return MxtKubeJsApi.addLifespan(entity, ticks);
    }

    @Info("Runs the reincarnation reset on the entity right now; the ledger afterwards is what the next life starts from. A listener may cancel it, which answers with the cancelled failure.")
    public LifeSpanService.Result reincarnate(LivingEntity entity) {
        return MxtKubeJsApi.reincarnateLifespan(entity);
    }
}
