package com.iafenvoy.mxt.runtime.wheel;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.api.WheelEntryKind;
import com.iafenvoy.mxt.runtime.wheel.builtin.AbilityKind;
import com.iafenvoy.mxt.runtime.wheel.builtin.AuraKind;
import com.iafenvoy.mxt.runtime.wheel.builtin.BehaviorKind;
import com.iafenvoy.mxt.runtime.wheel.builtin.EmptyKind;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

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
    public static final WheelEntryKind EMPTY = new EmptyKind();
    public static final WheelEntryKind ABILITY = new AbilityKind();
    public static final WheelEntryKind AURA = new AuraKind();
    public static final WheelEntryKind BEHAVIOR = new BehaviorKind();
    public static final List<WheelEntryKind> BUILT_IN = List.of(EMPTY, ABILITY, AURA, BEHAVIOR);
    public static final Codec<WheelEntryKind> CODEC = Codec.STRING.xmap(WheelEntryKinds::parse, kind -> kind.id().toString());
    public static final StreamCodec<ByteBuf, WheelEntryKind> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(WheelEntryKinds::parse, kind -> kind.id().toString());
    private static final Map<Identifier, WheelEntryKind> REGISTERED = new LinkedHashMap<>();

    static {
        for (WheelEntryKind kind : BUILT_IN) register(kind);
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

    public static WheelEntryKind parse(String value) {
        if ("artifact".equals(value)) return ABILITY;
        Identifier id = value.indexOf(':') >= 0 ? Identifier.tryParse(value)
                : Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, value);
        return id == null ? EMPTY : byId(id).orElse(EMPTY);
    }
}
