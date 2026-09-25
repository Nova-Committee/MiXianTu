package com.iafenvoy.mxt.runtime.wheel;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.api.WheelEntryKind;
import com.iafenvoy.mxt.api.WheelSource;
import com.iafenvoy.mxt.data.ability.Abilities;
import com.iafenvoy.mxt.data.creature.ContractBehaviors;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.mojang.serialization.Codec;
import net.minecraft.core.RegistryAccess;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The kinds the framework defines, and the one place a kind is looked up by id.
 *
 * <p>There is no enum to extend: a content mod makes a {@link WheelEntryKind} of its own and registers it here (from
 * its mod setup). Registration is what lets a stored layout be read back - a cell keeps the kind's id, and both
 * sides resolve it here - and what lets the configuration screen offer a pool of its own.
 */
public final class WheelEntryKinds {
    /**
     * No entry at all. A saved layout is twelve cells, so this is a kind like any other rather than a null.
     */
    public static final WheelEntryKind EMPTY = new EmptyKind();
    /**
     * An ability, and the only kind with a state a directed request can name.
     */
    public static final WheelEntryKind ABILITY = new AbilityKind();
    /**
     * A spirit-power burst from an aura's own value.
     */
    public static final WheelEntryKind AURA = new AuraKind();
    /**
     * An order for a bound creature. The id is a {@code ContractBehavior} id rather than a registry entry, and the
     * page it appears on is read from the taming bell's tuned beast.
     */
    public static final WheelEntryKind BEHAVIOR = new BehaviorKind();
    public static final List<WheelEntryKind> BUILT_IN = List.of(EMPTY, ABILITY, AURA, BEHAVIOR);

    // Registered during mod setup and only read from the game thread afterwards, so it needs no lock; the first
    // registration of an id wins, which keeps what an id means stable across a reload.
    private static final Map<Identifier, WheelEntryKind> REGISTERED = new LinkedHashMap<>();

    static {
        for (WheelEntryKind kind : BUILT_IN) REGISTERED.put(kind.id(), kind);
    }

    private WheelEntryKinds() {
    }

    public static void register(WheelEntryKind kind) {
        REGISTERED.putIfAbsent(kind.id(), kind);
    }

    public static Optional<WheelEntryKind> byId(Identifier id) {
        return Optional.ofNullable(REGISTERED.get(id));
    }

    public static List<WheelEntryKind> known() {
        return List.copyOf(REGISTERED.values());
    }

    // A stored cell keeps the kind's id, so this is what reads one back. Nothing throws: an id nobody knows, and a
    // layout written before the ability merge, both read as EMPTY - which is the cell the server would drop anyway.
    public static final Codec<WheelEntryKind> CODEC = Codec.STRING.xmap(WheelEntryKinds::parse, kind -> kind.id().toString());
    public static final StreamCodec<ByteBuf, WheelEntryKind> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(WheelEntryKinds::parse, kind -> kind.id().toString());

    public static WheelEntryKind parse(String value) {
        if ("artifact".equals(value)) return ABILITY;
        Identifier id = value.indexOf(':') >= 0 ? Identifier.tryParse(value)
                : Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, value);
        return id == null ? EMPTY : byId(id).orElse(EMPTY);
    }

    private static Identifier key(String path) {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path);
    }

    private static final class EmptyKind implements WheelEntryKind {
        @Override
        public Identifier id() {
            return key("empty");
        }

        @Override
        public Component displayName() {
            return Component.translatable("wheel.mxt.kind.empty");
        }

        @Override
        public boolean holdsEntry() {
            return false;
        }

        @Override
        public boolean exists(RegistryAccess access, Identifier id) {
            return false;
        }

        @Override
        public boolean trigger(ServerPlayer player, WheelSource source, Identifier id) {
            return false;
        }
    }

    private static final class AbilityKind implements WheelEntryKind {
        @Override
        public Identifier id() {
            return key("ability");
        }

        @Override
        public Component displayName() {
            return Component.translatable("wheel.mxt.kind.ability");
        }

        @Override
        public boolean holdsEntry() {
            return true;
        }

        @Override
        public boolean exists(RegistryAccess access, Identifier id) {
            return Abilities.resolve(access, id).isPresent();
        }

        @Override
        public boolean trigger(ServerPlayer player, WheelSource source, Identifier id) {
            return WheelService.press(player, source, id);
        }

        @Override
        public boolean directed(ServerPlayer player, WheelSource source, Identifier id, boolean wanted) {
            return WheelService.directed(player, source, id, wanted);
        }
    }

    private static final class AuraKind implements WheelEntryKind {
        @Override
        public Identifier id() {
            return key("aura");
        }

        @Override
        public Component displayName() {
            return Component.translatable("wheel.mxt.kind.aura");
        }

        @Override
        public boolean holdsEntry() {
            return true;
        }

        @Override
        public boolean exists(RegistryAccess access, Identifier id) {
            return MxtDatapackRegistries.holder(access, MxtResourceKeys.AURA, id).isPresent();
        }

        @Override
        public boolean trigger(ServerPlayer player, WheelSource source, Identifier id) {
            return WheelService.burst(player, id);
        }
    }

    private static final class BehaviorKind implements WheelEntryKind {
        @Override
        public Identifier id() {
            return key("behavior");
        }

        @Override
        public Component displayName() {
            return Component.translatable("wheel.mxt.kind.behavior");
        }

        @Override
        public boolean holdsEntry() {
            return true;
        }

        @Override
        public boolean exists(RegistryAccess access, Identifier id) {
            // Orders live in code, not in a registry, so the registry access is not what answers here.
            return ContractBehaviors.isKnown(id);
        }

        @Override
        public boolean trigger(ServerPlayer player, WheelSource source, Identifier id) {
            return WheelService.order(player, id);
        }
    }
}
