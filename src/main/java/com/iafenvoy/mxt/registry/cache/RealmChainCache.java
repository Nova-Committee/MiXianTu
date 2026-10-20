package com.iafenvoy.mxt.registry.cache;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.chain.Chain;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

public class RealmChainCache extends ChainCache<RealmStage> {
    private final Map<Holder<Aura>, Chain<RealmStage>> realmChainByAura = new LinkedHashMap<>();
    private final Map<Holder<RealmStage>, Holder<Aura>> auraByRealm = new LinkedHashMap<>();

    public RealmChainCache() {
        super(MxtResourceKeys.REALM_STAGE);
    }

    @Override
    public void build(RegistryAccess access) {
        super.build(access);
        validateRegistry(access.lookupOrThrow(MxtResourceKeys.AURA), Aura::resource, (a, b) -> !a.equals(b));
        for (Holder<Aura> aura : access.lookupOrThrow(MxtResourceKeys.AURA).listElements().toList()) {
            Optional<Chain<RealmStage>> optional = aura.value().firstRealm().flatMap(x -> this.get().chainOf(x));
            if (optional.isEmpty()) continue;
            Chain<RealmStage> chain = optional.get();
            this.realmChainByAura.put(aura, chain);
            chain.nodes().forEach(x -> this.auraByRealm.put(x, aura));
        }
    }

    @Override
    protected void clear() {
        super.clear();
        this.realmChainByAura.clear();
        this.auraByRealm.clear();
    }

    @Override
    public <R> RealmChainCache validate(Function<RealmStage, R> mapper, BiPredicate<R, R> predicate) {
        return (RealmChainCache) super.validate(mapper, predicate);
    }

    @Override
    public RealmChainCache require(Predicate<Chain<RealmStage>> rule) {
        return (RealmChainCache) super.require(rule);
    }

    public Optional<Chain<RealmStage>> getChainByAura(Holder<Aura> holder) {
        return Optional.ofNullable(this.realmChainByAura.get(holder));
    }

    public Optional<Holder<Aura>> getAuraByRealm(Holder<RealmStage> holder) {
        return Optional.ofNullable(this.auraByRealm.get(holder));
    }
}
