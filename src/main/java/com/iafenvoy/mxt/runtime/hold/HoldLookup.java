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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Which items are used by holding them down, and what each one's hold looks like. The question is asked from paths
 * that run while a gesture is live, so declarations answering from the item's identity are cached per item; one
 * that reads the stack - a manual, whose technique is its own component - is asked about every stack. The
 * declarations come from every registered {@link HoldSource}, and this class knows none of them.
 *
 * <p>The cache is captured on {@link TagsUpdatedEvent} and {@link ServerStartedEvent} rather than by walking every
 * registered item up front, because building an {@code ItemStack} during a pack load reads unbound component maps
 * and throws.
 */
@EventBusSubscriber
public final class HoldLookup {
    // Keyed by item, and only ever holding the declarations that match on the item's identity.
    private static final Map<Item, List<HoldBinding>> ITEM_MATCHED = new ConcurrentHashMap<>();
    private static final List<HoldSource> SOURCES = new CopyOnWriteArrayList<>();
    // Sorted by the priority each declaration carries, then by the order the sources registered in, which is who
    // drives a stack two of them claim.
    private static volatile List<HoldBinding> holds = List.of();

    private HoldLookup() {
    }

    // Called once per module, at construction, long before a world can load.
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

    // Only declarations that ask for a hold are captured, so any answer means the item is used by holding it.
    public static @Nullable HoldBinding hold(ItemStack stack) {
        if (stack.isEmpty() || holds.isEmpty()) return null;
        List<HoldBinding> byItem = ITEM_MATCHED.computeIfAbsent(stack.getItem(), item -> holds.stream()
                .filter(hold -> hold.entries().stream().anyMatch(entry -> entry.itemLevel() && entry.matches(stack)))
                .toList());
        for (HoldBinding hold : holds)
            if (byItem.contains(hold) || matchesStack(hold, stack)) return hold;
        return null;
    }

    private static boolean matchesStack(HoldBinding hold, ItemStack stack) {
        return hold.entries().stream().anyMatch(entry -> !entry.itemLevel() && entry.matches(stack));
    }

    // Public so the server audit can drive it directly.
    public static void rebuild(Provider access) {
        holds = SOURCES.stream().flatMap(source -> source.holds(access).stream())
                .filter(HoldBinding::requiresHold)
                .sorted(ItemMatcher.ORDER)
                .toList();
        ITEM_MATCHED.clear();
    }
}
