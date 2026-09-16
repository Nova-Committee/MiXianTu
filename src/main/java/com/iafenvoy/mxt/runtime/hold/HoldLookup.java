package com.iafenvoy.mxt.runtime.hold;

import com.iafenvoy.mxt.data.item.HoldBinding;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Which items are used by holding them down, and what each one's hold looks like. The question is asked from
 * paths that run every tick - the client's keep-alive and the click path - so vanilla's answers are cached per
 * item. The declarations come from every registered {@link HoldSource}, and this class knows none of them.
 * <p>
 * The cache is captured on {@link TagsUpdatedEvent} and {@link ServerStartedEvent} rather than by walking every
 * registered item up front, because building an {@code ItemStack} during a pack load reads unbound component
 * maps and throws.
 */
@EventBusSubscriber
public final class HoldLookup {
    /**
     * Per-item answers, cleared whenever the holds are recaptured. Keyed by item because a declaration tests the
     * item's identity and never the stack.
     */
    private static final Map<Item, Optional<HoldBinding>> RESOLVED = new ConcurrentHashMap<>();
    private static final List<HoldSource> SOURCES = new CopyOnWriteArrayList<>();
    private static volatile List<HoldBinding> holds = List.of();

    private HoldLookup() {
    }

    /**
     * Adds one module's holds. Called once per module, at construction, long before a world can load.
     */
    public static void register(HoldSource source) {
        SOURCES.add(source);
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
     * The hold declared for this stack, or {@code null} when nothing holds this item down. Only declarations
     * that ask for a hold are captured, so any answer means the item is used by holding it.
     */
    public static @Nullable HoldBinding hold(ItemStack stack) {
        if (stack.isEmpty() || holds.isEmpty()) return null;
        Optional<HoldBinding> cached = RESOLVED.get(stack.getItem());
        if (cached != null) return cached.orElse(null);
        Optional<HoldBinding> found = ItemMatcher.find(holds.stream(), stack);
        RESOLVED.put(stack.getItem(), found);
        return found.orElse(null);
    }

    /**
     * Recaptures every registered module's holds from one registry view. Public so the server audit can drive it
     * directly.
     */
    public static void rebuild(Provider access) {
        holds = SOURCES.stream().flatMap(source -> source.holds(access).stream())
                .filter(HoldBinding::requiresHold)
                .toList();
        RESOLVED.clear();
    }
}
