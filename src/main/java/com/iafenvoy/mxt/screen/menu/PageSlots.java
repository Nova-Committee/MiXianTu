package com.iafenvoy.mxt.screen.menu;

import com.sighs.apricityui.container.SlotLayout;
import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.datasource.ContainerDataSource;
import com.sighs.apricityui.container.filter.FilterUtil;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The page containers of one menu: the layout ApricityUI binds the page's slots with, and one data source per
 * container that opens its slots.
 * <p>
 * ApricityUI maps a page slot to a menu slot through the layout - container id plus local index becomes
 * {@code baseIndex + localIndex} - so the menu's slot order is the order the containers are declared here, and a
 * page container that is not declared is a display slot no menu slot backs.
 * <p>
 * The layout travels to the client with the open packet, and the client half rebuilds it the same way from the same
 * constructor arguments, so both halves must declare the same containers in the same order.
 */
public final class PageSlots {
    /**
     * Opens one slot of a container; the page decides where that slot is drawn, so the x/y ApricityUI passes are
     * always 0.
     */
    @FunctionalInterface
    public interface SlotFactory {
        Slot create(Container container, int index, int x, int y);
    }

    /**
     * A built layout and the sources its slots are created from; a menu hands both to
     * {@code ApricityContainerMenu}'s constructor.
     */
    public record Layout(SlotLayout layout, Map<String, ContainerDataSource> sources) {
    }

    /**
     * The player inventory: main inventory 9..35 first, hotbar 0..8 last, which is also the order the pages write
     * with their vanilla {@code slot-index}.
     */
    private static final int PLAYER_SLOTS = 36;

    private final String pagePath;
    private final Map<String, Declared> declared = new LinkedHashMap<>();
    private String playerId;

    private PageSlots(String pagePath) {
        this.pagePath = pagePath;
    }

    public static PageSlots of(String pagePath) {
        return new PageSlots(pagePath);
    }

    /**
     * Declares a container; the first one declared is the page's primary container, which is what ApricityUI's own
     * shift-click aims at (a menu that needs another order overrides {@code quickMoveStack} anyway).
     */
    public PageSlots container(String id, Container container, SlotFactory slots) {
        this.declared.put(id, new Declared(container, slots));
        return this;
    }

    /**
     * Declares the player inventory container, under the id the page spells it with ({@code inventory} on most
     * pages, {@code player_inventory} on the furnace's).
     */
    public PageSlots player(String id) {
        this.playerId = id;
        return this;
    }

    public Layout build() {
        if (this.declared.isEmpty()) throw new IllegalStateException("A page menu needs at least one container");
        ArrayList<SlotLayout.ContainerEntry> entries = new ArrayList<>(this.declared.size() + 1);
        LinkedHashMap<String, ContainerDataSource> sources = new LinkedHashMap<>();
        int base = 0;
        boolean primary = true;
        for (Map.Entry<String, Declared> entry : this.declared.entrySet()) {
            Container container = entry.getValue().container();
            int capacity = container.getContainerSize();
            entries.add(new SlotLayout.ContainerEntry(entry.getKey(), ContainerBindType.BLOCK_ENTITY, base, capacity, primary));
            sources.put(entry.getKey(), new Source(container, entry.getValue().slots()));
            base += capacity;
            primary = false;
        }
        if (this.playerId != null) {
            entries.add(new SlotLayout.ContainerEntry(this.playerId, ContainerBindType.PLAYER, base, PLAYER_SLOTS, false));
        }
        return new Layout(new SlotLayout(this.pagePath, entries), Map.copyOf(sources));
    }

    private record Declared(Container container, SlotFactory slots) {
    }

    /**
     * One container's source. ApricityUI only tells PLAYER apart from everything else here, and our containers are
     * already resolved before the menu exists, so the bind type only has to be something other than PLAYER.
     */
    private record Source(Container container, SlotFactory slots) implements ContainerDataSource {
        @Override
        public ContainerBindType bindType() {
            return ContainerBindType.BLOCK_ENTITY;
        }

        @Override
        public int capacity() {
            return this.container.getContainerSize();
        }

        @Override
        public Slot createSlot(int slotIndex, int x, int y, FilterUtil filter) {
            return this.slots.create(this.container, slotIndex, x, y);
        }
    }
}
