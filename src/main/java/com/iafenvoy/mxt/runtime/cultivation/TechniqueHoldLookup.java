package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which items are technique manuals that must be held down. Vanilla asks an item's use duration and
 * animation from the render loop, every frame, so the answers are cached per item. The bindings are captured
 * on {@link TagsUpdatedEvent} and {@link ServerStartedEvent} rather than by walking every registered item up
 * front, because building an {@code ItemStack} during a pack load reads unbound component maps and throws.
 */
@EventBusSubscriber
public final class TechniqueHoldLookup {
    /**
     * Per-item answers, cleared whenever the bindings are recaptured. Keyed by item because a binding tests the
     * item's identity and never the stack.
     */
    private static final Map<Item, Optional<TechniqueBinding>> RESOLVED = new ConcurrentHashMap<>();
    private static volatile List<TechniqueBinding> holds = List.of();

    private TechniqueHoldLookup() {
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        rebuild(event.getRegistries());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        rebuild(event.getServer().registryAccess());
    }

    /**
     * The hold declared for this stack, or {@code null} when it declares none. Only bindings that ask for
     * a hold are captured, so a result means the item is a manual that must be held down.
     */
    public static @Nullable TechniqueBinding hold(ItemStack stack) {
        if (stack.isEmpty() || holds.isEmpty()) return null;
        Optional<TechniqueBinding> cached = RESOLVED.get(stack.getItem());
        if (cached != null) return cached.orElse(null);
        Optional<TechniqueBinding> found = ItemMatcher.find(holds.stream(), stack);
        RESOLVED.put(stack.getItem(), found);
        return found.orElse(null);
    }

    /**
     * Recaptures the hold bindings from a data pack registry view. Package-visible so the server audit
     * can drive it directly.
     */
    static void rebuild(Provider access) {
        holds = MxtDatapackRegistries.holders(access, MxtResourceKeys.TECHNIQUE_BINDING)
                .map(Reference::value)
                .filter(TechniqueBinding::requiresHold)
                .toList();
        RESOLVED.clear();
    }
}
