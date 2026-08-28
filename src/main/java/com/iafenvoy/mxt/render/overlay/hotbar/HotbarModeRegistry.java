package com.iafenvoy.mxt.render.overlay.hotbar;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.network.payload.SpiritBurstC2SPayload;
import com.iafenvoy.mxt.registry.MxtKeyMappings.KeyMappingHolder;
import com.iafenvoy.mxt.render.overlay.hotbar.AbilityHotbarClient.ResolvedAbility;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.core.Holder.Reference;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Client-only registry of hotbar modes. A mode may optionally own a key
 * mapping; this class registers, ticks, and dispatches those mappings.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class HotbarModeRegistry {
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
        }, ABILITY_KEY);
        register(SPIRIT, player -> {
            List<HotbarEntry> entries = new ArrayList<>();
            for (Reference<Resource> resource : SpiritBurstClient.resources(player))
                entries.add(new SpiritHotbarEntry(HolderHelper.id(resource)));
            return entries;
        }, SPIRIT_KEY, _ -> ClientPacketDistributor.sendToServer(
                new SpiritBurstC2SPayload(false, Optional.empty())));
    }

    public static void register(Identifier id, Function<Player, List<HotbarEntry>> provider) {
        register(id, provider, null, null);
    }

    public static void register(Identifier id, Function<Player, List<HotbarEntry>> provider, @Nullable KeyMappingHolder keyMapping) {
        register(id, provider, keyMapping, null);
    }

    public static void register(Identifier id, Function<Player, List<HotbarEntry>> provider,
                                @Nullable KeyMappingHolder keyMapping, @Nullable Consumer<Player> onClose) {
        if (id == null) throw new IllegalArgumentException("Hotbar mode id cannot be null");
        if (provider == null) throw new IllegalArgumentException("Hotbar mode provider cannot be null");
        MODES.put(id, new ModeEntry(id, provider, keyMapping, onClose));
        if (keyMapping != null)
            keyMapping.onStateChange(pressed -> HotbarController.handleModeKey(id, pressed));
    }

    public static Optional<ModeEntry> get(Identifier id) {
        return Optional.ofNullable(MODES.get(id));
    }

    public static List<HotbarEntry> entries(Identifier id, Player player) {
        if (id == null || player == null) return List.of();
        ModeEntry mode = MODES.get(id);
        return mode == null ? List.of() : mode.provider().apply(player);
    }

    public static Optional<KeyMappingHolder> keyMapping(Identifier id) {
        ModeEntry mode = MODES.get(id);
        return mode == null ? Optional.empty() : Optional.ofNullable(mode.keyMapping());
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
                            KeyMappingHolder keyMapping, Consumer<Player> onClose) {
    }
}
