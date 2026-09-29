package com.iafenvoy.mxt.compat.geckolib;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

/**
 * The animatable cache for a mount model. GeckoLib's own singleton cache never drops an entry, and a mount is
 * summoned and discarded per flight with an ever-increasing entity id, so an unbounded map would hold one manager
 * per flight for the whole session. Oldest-unused entries are dropped instead; an evicted vehicle restarts its
 * animation the next time it is drawn, which nobody can see.
 */
final class MountAnimatableCache extends AnimatableInstanceCache {
    private static final int MAX_MANAGERS = 128;
    private final Long2ObjectLinkedOpenHashMap<AnimatableManager<?>> managers = new Long2ObjectLinkedOpenHashMap<>();

    MountAnimatableCache(GeoAnimatable animatable) {
        super(animatable);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends GeoAnimatable> AnimatableManager<T> getManagerForId(long uniqueId) {
        AnimatableManager<?> manager = this.managers.getAndMoveToFirst(uniqueId);
        if (manager == null) {
            manager = new AnimatableManager<>(this.animatable);
            this.managers.putAndMoveToFirst(uniqueId, manager);
            while (this.managers.size() > MAX_MANAGERS) this.managers.removeLast();
        }
        return (AnimatableManager<T>) manager;
    }
}
