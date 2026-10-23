package com.iafenvoy.mxt.event;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

import java.util.List;

/**
 * Asks every granting system what the body's definitions grant. The rebuild starts from the whole answer, so whatever a
 * system stops naming is released.
 */
public final class CollectAbilityGrantsEvent extends LivingEvent {
    private final SetMultimap<Identifier, Holder<Ability>> desired = LinkedHashMultimap.create();

    public CollectAbilityGrantsEvent(LivingEntity entity) {
        super(entity);
    }

    public SetMultimap<Identifier, Holder<Ability>> getDesired() {
        return Multimaps.unmodifiableSetMultimap(this.desired);
    }

    public void want(Identifier source, Holder<Ability> ability) {
        this.desired.put(source, ability);
    }

    // A definition's own list, which may name ids or tags. The access is the caller's because the caller is the
    // one that holds it: a grant is rebuilt from inside the listener, which already has the body being asked about.
    public void want(Identifier source, List<Either<Holder<Ability>, TagKey<Ability>>> values) {
        RegistryCodecs.resolve(values, this.getEntity().registryAccess(), MxtResourceKeys.ABILITY).forEach(ability -> this.want(source, ability));
    }

    // A list already resolved by the owner, such as the cumulative unlocks of a reached level.
    public void wantResolved(Identifier source, List<Holder<Ability>> abilities) {
        abilities.forEach(ability -> this.want(source, ability));
    }
}
