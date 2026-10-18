package com.iafenvoy.mxt.registry.cache;

import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.slf4j.Logger;

import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

@EventBusSubscriber
public abstract class RegistryCache<T> implements Supplier<T> {
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final List<RegistryCache<?>> CACHES = new LinkedList<>();
    private final List<BiConsumer<RegistryAccess, T>> callbacks = new LinkedList<>();

    public RegistryCache() {
        CACHES.add(this);
    }

    protected void rebuild(RegistryAccess access) {
        this.clear();
        this.build(access);
        this.callbacks.forEach(x -> x.accept(access, this.get()));
    }

    protected abstract void build(RegistryAccess access);

    protected abstract void clear();

    public RegistryCache<T> callback(BiConsumer<RegistryAccess, T> callback) {
        this.callbacks.add(callback);
        return this;
    }

    protected static <T, R> void validateRegistry(HolderLookup<T> registry, Function<T, R> mapper, BiPredicate<R, R> predicate) {
        validateRegistry(registry, (a, b) -> predicate.test(mapper.apply(a), mapper.apply(b)));
    }

    protected static <T> void validateRegistry(HolderLookup<T> registry, BiPredicate<T, T> predicate) {
        List<Holder.Reference<T>> list = registry.listElements().toList();
        for (int i = 0; i < list.size(); i++)
            for (int j = i + 1; j < list.size(); j++)
                if (!predicate.test(list.get(i).value(), list.get(j).value()))
                    LOGGER.error("Validation failed between {} and {}", HolderHelper.id(list.get(i)), HolderHelper.id(list.get(j)));
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (!event.shouldUpdateStaticData()) return;
        RegistryAccess access = event.getRegistries();
        CACHES.forEach(x -> x.rebuild(access));
    }

    @EventBusSubscriber(Dist.CLIENT)
    public static final class ClientDatapackCacheEvents {
        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            CACHES.forEach(RegistryCache::clear);
        }
    }
}
