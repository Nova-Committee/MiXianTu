package com.iafenvoy.mxt.runtime.wheel;

import com.iafenvoy.mxt.data.ability.Abilities;
import com.iafenvoy.mxt.data.creature.ContractBehaviors;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.MiscStreamCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * What kind of thing one wheel sector holds; the kind names the registry an id has to resolve in, which is what
 * lets one twelve-cell wheel mix abilities and auras.
 *
 * <p>A layout saved before the merge still says {@code artifact} for a cell that names an ability, and is read as
 * one, which is what that cell named all along.
 */
public enum WheelEntryKind implements StringRepresentable {
    // Part of this enum so a layout is always twelve entries, never a null kind.
    EMPTY,
    ABILITY,
    AURA,
    // An order for a bound creature. The id is a {@code ContractBehavior} id rather than a registry entry, and the
    // page it appears on is read from the taming bell's tuned beast.
    BEHAVIOR;

    public static final Codec<WheelEntryKind> CODEC = Codec.STRING.comapFlatMap(WheelEntryKind::parse, WheelEntryKind::getSerializedName);
    public static final StreamCodec<ByteBuf, WheelEntryKind> STREAM_CODEC = MiscStreamCodecs.enumCodec(WheelEntryKind.class);

    private static DataResult<WheelEntryKind> parse(String value) {
        if ("artifact".equals(value)) return DataResult.success(ABILITY);
        try {
            return DataResult.success(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> "Unknown wheel entry kind " + value);
        }
    }

    @Override
    public @NonNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public boolean holdsEntry() {
        return this != EMPTY;
    }

    // Each side passes its own registry access. An id that no longer resolves is a cell the layout refuses to keep,
    // which is how deleting a definition takes its cells off every wheel.
    public boolean exists(RegistryAccess access, Identifier id) {
        if (id == null) return false;
        return switch (this) {
            case ABILITY -> Abilities.resolve(access, id).isPresent();
            case AURA -> MxtDatapackRegistries.holder(access, MxtResourceKeys.AURA, id).isPresent();
            // Orders live in code, not in a registry, so the registry access is not what answers here.
            case BEHAVIOR -> ContractBehaviors.isKnown(id);
            case EMPTY -> false;
        };
    }

    public Component displayName() {
        return Component.translatable("wheel.mxt.kind." + this.getSerializedName());
    }
}
