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
 * Which items are technique manuals that must be held down.
 *
 * <h2>Why this exists at all</h2>
 * Vanilla asks an item two questions while a use cycle runs - {@code getUseDuration} and
 * {@code getUseAnimation} - and it asks them <em>from the render loop</em>, every frame. Answering them
 * by scanning the data pack registries each time would be a per-frame search, so the answers are
 * resolved once and remembered per item.
 *
 * <h2>What is remembered, and why not the obvious thing</h2>
 * The list of hold bindings is captured from the data pack, and the per-item answer is worked out from
 * that list on first use of the item. An earlier version instead walked every registered item up front
 * and built the whole table in one go; that fails outright, because building an {@code ItemStack} reads
 * the item holder's component map, and during a data pack load the item holders are not bound yet -
 * it throws "Components not bound yet" and takes the whole load down with it.
 *
 * <p>Matching a stack against the captured bindings needs no registry access, so nothing here has to
 * reach for a registry at the moment a player uses an item. That matters more than it looks: the
 * obvious shortcut for a lookup with no context is to read the registries through the current
 * {@code MinecraftServer}, which works in singleplayer and returns nothing on a multiplayer client,
 * leaving the client and the server describing different holds. Capturing the bindings keeps the
 * per-frame path context-free.</p>
 *
 * <h2>When the list is captured</h2>
 * On {@link TagsUpdatedEvent}, which is fired on the server <em>and</em> on the client, and on
 * {@link ServerStartedEvent} so the server's capture happens after the data pack has fully loaded.
 * Capturing is a read of the binding values only - no item stacks are built here.
 */
@EventBusSubscriber
public final class TechniqueHoldLookup {
    /**
     * Per-item answers, cleared whenever the bindings are recaptured.
     *
     * <p>Keyed by item because matching a binding is a test of the item's identity - the id, a tag, a
     * wildcard or a pattern - and never of what is on the stack, so one answer per item is the whole
     * answer.</p>
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
     * The hold declared for this stack, or {@code null} when it declares none.
     *
     * <p>Only bindings that actually ask for a hold are captured, so a caller that gets a result back
     * knows the item is a manual that must be held down.</p>
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
     * Recaptures the hold bindings from a data pack registry view.
     *
     * <p>Package-visible so the server audit can drive it directly.</p>
     */
    static void rebuild(Provider access) {
        holds = MxtDatapackRegistries.holders(access, MxtResourceKeys.TECHNIQUE_BINDING)
                .map(Reference::value)
                .filter(TechniqueBinding::requiresHold)
                .toList();
        RESOLVED.clear();
    }
}
