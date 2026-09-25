package com.iafenvoy.mxt.runtime.wheel;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.api.WheelEntryKind;
import com.iafenvoy.mxt.api.WheelSource;
import com.iafenvoy.mxt.compat.CuriosIntegration;
import com.iafenvoy.mxt.runtime.ability.AbilitySources;
import com.iafenvoy.mxt.runtime.creature.ContractBells;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The wheel pages the framework defines, and the one place a page is looked up by id.
 *
 * <p>There is no enum to extend: a content mod makes a {@link WheelSource} of its own and registers it here (from its
 * mod setup), and its page then takes part in the numbering and the page turning like a built-in one. Page order is
 * registration order, and the first registration of an id wins, so both sides walk the same pages without agreeing on
 * an order up front.
 */
public final class WheelSourceTypes {
    /**
     * The player's own twelve saved cells: the only page whose contents are stored rather than read from what they
     * carry.
     */
    public static final WheelSource CONFIGURED = new ConfiguredSource();
    /**
     * What the main hand grants and declares right now.
     */
    public static final WheelSource MAIN_HAND = new EquipmentSource("main_hand", EquipmentSlot.MAINHAND);
    /**
     * What the off hand grants and declares right now.
     */
    public static final WheelSource OFF_HAND = new EquipmentSource("off_hand", EquipmentSlot.OFFHAND);
    /**
     * What every equipped Curios slot grants and declares right now.
     */
    public static final WheelSource CURIOS = new CuriosSource();
    /**
     * The orders of the beast the held taming bell is tuned to: a derived page like the equipment ones, empty until
     * a bell names a creature, and not part of the saved layout.
     */
    public static final WheelSource CONTRACT = new ContractSource();
    public static final List<WheelSource> BUILT_IN = List.of(CONFIGURED, MAIN_HAND, OFF_HAND, CURIOS, CONTRACT);

    // Registered during mod setup and only read from the game thread afterwards, so it needs no lock.
    private static final Map<Identifier, WheelSource> REGISTERED = new LinkedHashMap<>();

    static {
        for (WheelSource source : BUILT_IN) REGISTERED.put(source.id(), source);
    }

    private WheelSourceTypes() {
    }

    public static void register(WheelSource source) {
        REGISTERED.putIfAbsent(source.id(), source);
    }

    public static Optional<WheelSource> byId(Identifier id) {
        return Optional.ofNullable(REGISTERED.get(id));
    }

    // Every page, in the order the switch keys walk them and the order the page numbers count.
    public static List<WheelSource> pages() {
        return List.copyOf(REGISTERED.values());
    }

    // The page the wheel opens on, which is the first registered one. The built-ins are registered first, so this is
    // the player's own page unless a content mod registers into an empty registry.
    public static WheelSource first() {
        List<WheelSource> pages = pages();
        return pages.isEmpty() ? CONFIGURED : pages.getFirst();
    }

    // Wrapping at both ends, so the switch keys never dead-end. A page that is no longer registered steps from the
    // first one, which is the same answer as opening again.
    public static WheelSource step(WheelSource from, int delta) {
        List<WheelSource> pages = pages();
        int index = pages.indexOf(from);
        return pages.get(Math.floorMod(Math.max(index, 0) + delta, pages.size()));
    }

    // 1-based, which is what the player sees and never the index; 0 for a page nothing registered any more.
    public static int pageNumber(WheelSource source) {
        return pages().indexOf(source) + 1;
    }

    private static Identifier key(String path) {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path);
    }

    private abstract static class NamedSource implements WheelSource {
        private final Identifier id;
        private final String nameKey;

        private NamedSource(String path) {
            this.id = key(path);
            this.nameKey = "wheel.mxt.source." + path;
        }

        @Override
        public Identifier id() {
            return this.id;
        }

        @Override
        public Component displayName() {
            return Component.translatable(this.nameKey);
        }
    }

    private static final class ConfiguredSource extends NamedSource {
        private ConfiguredSource() {
            super("configured");
        }

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public List<Identifier> grantSources(LivingEntity entity) {
            return List.of();
        }

        @Override
        public List<ItemStack> equipment(LivingEntity entity) {
            List<ItemStack> carried = new ArrayList<>();
            carried.add(entity.getMainHandItem());
            carried.add(entity.getOffhandItem());
            carried.addAll(CuriosIntegration.equipped(entity));
            return List.copyOf(carried);
        }

        @Override
        public boolean offers(LivingEntity entity, WheelEntryKind kind, Identifier id) {
            return WheelSources.inLayout(entity, kind, id);
        }
    }

    private static final class EquipmentSource extends NamedSource {
        private final EquipmentSlot slot;

        private EquipmentSource(String path, EquipmentSlot slot) {
            super(path);
            this.slot = slot;
        }

        @Override
        public boolean configured() {
            return false;
        }

        @Override
        public List<Identifier> grantSources(LivingEntity entity) {
            return List.of(AbilitySources.equipment(this.slot, entity.getItemBySlot(this.slot)));
        }

        @Override
        public List<ItemStack> equipment(LivingEntity entity) {
            return List.of(entity.getItemBySlot(this.slot));
        }

        @Override
        public boolean offers(LivingEntity entity, WheelEntryKind kind, Identifier id) {
            return WheelSources.granted(entity, this, kind, id);
        }
    }

    private static final class CuriosSource extends NamedSource {
        private CuriosSource() {
            super("curios");
        }

        @Override
        public boolean configured() {
            return false;
        }

        @Override
        public List<Identifier> grantSources(LivingEntity entity) {
            return List.of(AbilitySources.CURIOS);
        }

        @Override
        public List<ItemStack> equipment(LivingEntity entity) {
            return CuriosIntegration.equipped(entity);
        }

        @Override
        public boolean offers(LivingEntity entity, WheelEntryKind kind, Identifier id) {
            return WheelSources.granted(entity, this, kind, id);
        }
    }

    private static final class ContractSource extends NamedSource {
        private ContractSource() {
            super("contract");
        }

        @Override
        public boolean configured() {
            return false;
        }

        @Override
        public List<Identifier> grantSources(LivingEntity entity) {
            // Orders are not granted abilities: the page holds what the tuned creature answers with, not what an
            // item grants.
            return List.of();
        }

        @Override
        public List<ItemStack> equipment(LivingEntity entity) {
            // The bell itself, which is what this page is read from; it grants no abilities.
            return List.of(entity.getMainHandItem(), entity.getOffhandItem());
        }

        @Override
        public boolean offers(LivingEntity entity, WheelEntryKind kind, Identifier id) {
            return kind == WheelEntryKinds.BEHAVIOR
                    && ContractBells.selection(entity).map(selection -> selection.offers(id)).orElse(false);
        }
    }
}
