package com.iafenvoy.mxt.registry.cache;

import com.iafenvoy.mxt.data.ability.AbilityAffinity;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.saturn.data.ability.Ability;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.iafenvoy.saturn.registry.cache.RegistryCache;
import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The element affinities the pack declares, in definition order, so a damage multiplier or a tooltip does not walk the
 * datapack registry per hit. The first entry naming an ability is the one that answers.
 */
public final class AbilityAffinityCache extends RegistryCache<AbilityAffinityCache> {
    private volatile List<AbilityAffinity> entries = List.of();

    public static AbilityAffinityCache of() {
        return new AbilityAffinityCache();
    }

    @Override
    protected void build(RegistryAccess access) {
        this.entries = MxtDatapackRegistries.holders(access, MxtResourceKeys.ABILITY_AFFINITY)
                .map(Holder.Reference::value).toList();
    }

    @Override
    protected void clear() {
        this.entries = List.of();
    }

    @Override
    public AbilityAffinityCache get() {
        return this;
    }

    public @Nullable AbilityAffinity of(Holder<Ability> ability) {
        for (AbilityAffinity entry : this.entries)
            if (entry.matches(ability)) return entry;
        return null;
    }

    /**
     * The same answer for a caller that only has the definition: a tag entry is resolved through the registry, so a
     * pack can cover a family without naming every ability.
     */
    public @Nullable AbilityAffinity of(Ability ability, RegistryAccess access) {
        for (AbilityAffinity entry : this.entries)
            if (RegistryCodecs.resolve(entry.abilities(), access, SaturnResourceKeys.ABILITY)
                    .anyMatch(holder -> holder.value().equals(ability))) return entry;
        return null;
    }
}
