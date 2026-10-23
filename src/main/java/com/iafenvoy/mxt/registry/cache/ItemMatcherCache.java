package com.iafenvoy.mxt.registry.cache;

import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether one binding table could claim a stack at all, answered without scanning the table.
 *
 * <p>Declarations whose entries decide on the item alone are indexed per item, built as the first stack of that
 * item asks; the ones that read stack data (a quality, a technique component) are asked about every stack. A no
 * answer is final - no declaration in the table matches - which is what lets a per-tick caller skip the lookup.
 *
 * <p>Nothing is cached before the first rebuild: an unbuilt table answers yes so a caller falls back to the lookup
 * rather than losing the binding for a session.
 */
public class ItemMatcherCache<T extends ItemMatcher> extends RegistryCache<Map<Item, List<T>>> {
    private final ResourceKey<Registry<T>> registry;
    private final Map<Item, List<T>> itemLevel = new ConcurrentHashMap<>();
    private volatile List<T> declarations;
    private volatile List<T> stackLevel = List.of();

    public ItemMatcherCache(ResourceKey<Registry<T>> registry) {
        this.registry = registry;
    }

    @Override
    protected void build(RegistryAccess access) {
        List<T> all = new ArrayList<>();
        List<T> stack = new ArrayList<>();
        access.lookupOrThrow(this.registry).listElements().forEach(holder -> {
            T matcher = holder.value();
            all.add(matcher);
            if (readsStack(matcher)) stack.add(matcher);
        });
        this.stackLevel = List.copyOf(stack);
        this.declarations = List.copyOf(all);
    }

    @Override
    protected void clear() {
        this.itemLevel.clear();
        this.stackLevel = List.of();
        this.declarations = null;
    }

    @Override
    public Map<Item, List<T>> get() {
        return this.itemLevel;
    }

    public boolean mayMatch(ItemStack stack) {
        if (stack.isEmpty()) return false;
        List<T> all = this.declarations;
        if (all == null) return true;
        if (all.isEmpty()) return false;
        if (!this.byItem(stack).isEmpty()) return true;
        for (T matcher : this.stackLevel) if (ItemMatcher.matches(matcher, stack)) return true;
        return false;
    }

    private List<T> byItem(ItemStack stack) {
        List<T> cached = this.itemLevel.get(stack.getItem());
        if (cached != null) return cached;
        List<T> matched = new ArrayList<>();
        for (T matcher : this.declarations)
            for (ItemMatcher.Entry entry : matcher.entries())
                if (entry.itemLevel() && entry.matches(stack)) {
                    matched.add(matcher);
                    break;
                }
        List<T> result = List.copyOf(matched);
        this.itemLevel.put(stack.getItem(), result);
        return result;
    }

    // A declaration with one entry that reads the stack cannot be cached per item at all.
    private static boolean readsStack(ItemMatcher matcher) {
        for (ItemMatcher.Entry entry : matcher.entries()) if (!entry.itemLevel()) return true;
        return false;
    }
}
