package com.iafenvoy.mxt.render.overlay.hotbar;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.HotbarLayoutAttachment;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.network.payload.HotbarLayoutC2SPayload;
import com.iafenvoy.mxt.network.payload.SpiritBurstC2SPayload;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtKeyMappings.KeyMappingHolder;
import com.iafenvoy.mxt.render.overlay.hotbar.AbilityHotbarClient.ResolvedAbility;
import com.iafenvoy.mxt.screen.hotbar.HotbarConfigurationScreen;
import com.iafenvoy.mxt.screen.hotbar.HotbarConfigurationScreen.HotbarAccess;
import com.iafenvoy.mxt.screen.hotbar.HotbarConfigurationScreen.Option;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Client-only registry of hotbar modes. A mode may optionally own a key
 * mapping; this class registers, ticks, and dispatches those mappings.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class HotbarModeRegistry {
    public static final int MAX_SLOTS = 9;
    public static final Identifier ABILITY = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "ability");
    public static final Identifier SPIRIT = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "spirit");

    private static final Category CATEGORY = new Category(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "hotbar"));
    private static final Map<Identifier, ModeEntry> MODES = new LinkedHashMap<>();
    private static final KeyMappingHolder ABILITY_KEY = KeyMappingHolder.standalone(new KeyMapping("key.mxt.ability_menu", Type.KEYSYM, InputConstants.KEY_LALT, CATEGORY));
    private static final KeyMappingHolder SPIRIT_KEY = KeyMappingHolder.standalone(new KeyMapping("key.mxt.spirit_burst", Type.KEYSYM, InputConstants.KEY_V, CATEGORY));

    static {
        register(ABILITY, player -> {
            List<HotbarEntry> entries = new ArrayList<>();
            for (ResolvedAbility ability : AbilityHotbarClient.all(player))
                entries.add(new AbilityHotbarEntry(ability.id(), ability.definition()));
            return entries;
        }, ABILITY_KEY, null, player -> {
            List<HotbarEntry> entries = new ArrayList<>();
            for (ResolvedAbility ability : AbilityHotbarClient.allAvailable(player))
                entries.add(new AbilityHotbarEntry(ability.id(), ability.definition()));
            return entries;
        });
        register(SPIRIT, player -> {
            List<HotbarEntry> entries = new ArrayList<>();
            for (Reference<Resource> resource : SpiritBurstClient.resources(player))
                entries.add(new SpiritHotbarEntry(HolderHelper.id(resource)));
            return entries;
        }, SPIRIT_KEY, _ -> ClientPacketDistributor.sendToServer(
                new SpiritBurstC2SPayload(false, Optional.empty())), player -> {
            List<HotbarEntry> entries = new ArrayList<>();
            for (Reference<Resource> resource : SpiritBurstClient.resourcesAvailable(player))
                entries.add(new SpiritHotbarEntry(HolderHelper.id(resource)));
            return entries;
        });
    }

    public static void register(Identifier id, Function<Player, List<HotbarEntry>> provider) {
        register(id, provider, null, null);
    }

    public static void register(Identifier id, Function<Player, List<HotbarEntry>> provider, @Nullable KeyMappingHolder keyMapping) {
        register(id, provider, keyMapping, null);
    }

    public static void register(Identifier id, Function<Player, List<HotbarEntry>> provider,
                                @Nullable KeyMappingHolder keyMapping, @Nullable Consumer<Player> onClose) {
        register(id, provider, keyMapping, onClose, null);
    }

    public static void register(Identifier id, Function<Player, List<HotbarEntry>> provider,
                                @Nullable KeyMappingHolder keyMapping, @Nullable Consumer<Player> onClose,
                                @Nullable Function<Player, List<HotbarEntry>> configurationProvider) {
        if (id == null) throw new IllegalArgumentException("Hotbar mode id cannot be null");
        if (provider == null) throw new IllegalArgumentException("Hotbar mode provider cannot be null");
        MODES.put(id, new ModeEntry(id, provider, keyMapping, onClose, configurationProvider));
        if (keyMapping != null)
            keyMapping.onStateChange(pressed -> HotbarController.handleModeKey(id, pressed));
    }

    public static Optional<ModeEntry> get(Identifier id) {
        return Optional.ofNullable(MODES.get(id));
    }

    public static List<HotbarEntry> entries(Identifier id, Player player) {
        if (id == null || player == null) return List.of();
        ModeEntry mode = MODES.get(id);
        if (mode == null) return List.of();
        // A configuration provider only supplies selectable candidates for the
        // editor. The live hotbar must always be resolved from its runtime provider.
        List<HotbarEntry> available = mode.provider().apply(player);
        List<Identifier> saved = player.getData(MxtAttachments.HOTBAR_LAYOUT).slots(id);
        if (saved.isEmpty()) return available.stream().limit(MAX_SLOTS).toList();
        Map<Identifier, HotbarEntry> byId = new LinkedHashMap<>();
        available.forEach(entry -> {
            if (entry.id() != null) byId.putIfAbsent(entry.id(), entry);
        });
        List<HotbarEntry> result = new ArrayList<>(9);
        for (Identifier slot : saved) {
            if (HotbarLayoutAttachment.EMPTY_SLOT.equals(slot)) result.add(EmptyHotbarEntry.INSTANCE);
            else {
                HotbarEntry entry = byId.remove(slot);
                // An unavailable saved ID is stale datapack state, not an explicit
                // empty slot. Keep explicit empty markers intact, then backfill this
                // position from the current runtime entries below.
                result.add(entry);
            }
        }
        Iterator<HotbarEntry> remaining = byId.values().iterator();
        for (int index = 0; index < result.size() && remaining.hasNext(); index++) {
            if (result.get(index) == null) {
                result.set(index, remaining.next());
                remaining.remove();
            }
        }
        result.replaceAll(entry -> entry == null ? EmptyHotbarEntry.INSTANCE : entry);
        if (result.size() < 9) byId.values().stream().limit(9 - result.size()).forEach(result::add);
        return result;
    }

    public static Optional<KeyMappingHolder> keyMapping(Identifier id) {
        ModeEntry mode = MODES.get(id);
        return mode == null ? Optional.empty() : Optional.ofNullable(mode.keyMapping());
    }

    /**
     * Opens the generic editor for one registered mode using its current entries.
     */
    public static boolean openConfiguration(Identifier id) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        ModeEntry mode = MODES.get(id);
        if (player == null || mode == null) return false;
        Function<Player, List<HotbarEntry>> source = mode.configurationProvider() == null ? mode.provider() : mode.configurationProvider();
        List<HotbarEntry> available = source.apply(player);
        List<Option> options = available.stream()
                .filter(entry -> entry.id() != null)
                .map(Option::of).toList();
        minecraft.setScreen(new HotbarConfigurationScreen(
                Component.translatable("screen.mxt.hotbar_configuration"), options,
                new HotbarAccess() {
                    @Override
                    public List<Identifier> read() {
                        List<Identifier> saved = player.getData(MxtAttachments.HOTBAR_LAYOUT).slots(id);
                        if (saved.isEmpty()) {
                            return mode.provider().apply(player).stream()
                                    .map(HotbarEntry::id)
                                    .filter(Objects::nonNull)
                                    .limit(MAX_SLOTS)
                                    .toList();
                        }
                        List<Identifier> result = new ArrayList<>();
                        saved.forEach(slot ->
                                result.add(HotbarLayoutAttachment.EMPTY_SLOT.equals(slot) ? null : slot));
                        return result;
                    }

                    @Override
                    public void write(List<@Nullable Identifier> slots) {
                        List<Identifier> encoded = new ArrayList<>(MAX_SLOTS);
                        slots.stream().limit(MAX_SLOTS).map(slot -> slot == null ? HotbarLayoutAttachment.EMPTY_SLOT : slot)
                                .forEach(encoded::add);
                        ClientPacketDistributor.sendToServer(new HotbarLayoutC2SPayload(id, encoded));
                    }
                }));
        return true;
    }

    public static void close(Identifier id, @Nullable Player player) {
        ModeEntry mode = MODES.get(id);
        if (mode != null && mode.onClose() != null) mode.onClose().accept(player);
    }

    public static Map<Identifier, ModeEntry> modes() {
        return Collections.unmodifiableMap(MODES);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        MODES.values().stream().map(ModeEntry::keyMapping).filter(Objects::nonNull)
                .map(KeyMappingHolder::get).forEach(event::register);
    }

    @SubscribeEvent
    public static void tickKeyMappings(Post event) {
        MODES.values().stream().map(ModeEntry::keyMapping).filter(Objects::nonNull)
                .forEach(KeyMappingHolder::tick);
    }

    public static boolean isAbility(Identifier id) {
        return ABILITY.equals(id);
    }

    public static boolean isSpirit(Identifier id) {
        return SPIRIT.equals(id);
    }

    public record ModeEntry(Identifier id, Function<Player, List<HotbarEntry>> provider,
                            KeyMappingHolder keyMapping, Consumer<Player> onClose,
                            Function<Player, List<HotbarEntry>> configurationProvider) {
    }
}
