package com.iafenvoy.mxt.runtime.wheel;

import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.artifact.ArtifactCapability;
import com.iafenvoy.mxt.util.codec.MiscStreamCodecs;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * What kind of thing one wheel sector holds; the kind names the registry an id has to resolve in, which is
 * what lets one twelve-cell wheel mix abilities and auras.
 */
public enum WheelEntryKind implements StringRepresentable {
    /** An empty sector: part of this enum so a layout is always twelve entries, never a null kind. */
    EMPTY,
    /** An entry of the {@code mxt:ability} registry. */
    ABILITY,
    /** An entry of the {@code mxt:aura} registry. */
    AURA,
    /**
     * An entry of the {@code mxt:artifact} registry that declares a {@code ToggableArtifactAbility}: the cell is
     * something the player presses for - a switch or a one-shot - named by the artifact's own id with the
     * capability's key appended.
     */
    ARTIFACT;

    public static final Codec<WheelEntryKind> CODEC = StringRepresentable.fromEnum(WheelEntryKind::values);
    public static final StreamCodec<ByteBuf, WheelEntryKind> STREAM_CODEC = MiscStreamCodecs.enumCodec(WheelEntryKind.class);

    @Override
    public @NonNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public boolean holdsEntry() {
        return this != EMPTY;
    }

    /** Whether {@code id} resolves in this kind's registry; each side passes its own registry access. */
    public boolean exists(RegistryAccess access, Identifier id) {
        if (id == null) return false;
        return switch (this) {
            case ABILITY -> MxtDatapackRegistries.holder(access, MxtResourceKeys.ABILITY, id).isPresent();
            case AURA -> MxtDatapackRegistries.holder(access, MxtResourceKeys.AURA, id).isPresent();
            // A capability is named by the artifact and its own key together (`ns:path/key`), and only counts when
            // that artifact really declares a capability under that key - so a stored cell always names something
            // the wheel could draw and press.
            case ARTIFACT -> ArtifactCapability.parse(id).map(capability ->
                    MxtDatapackRegistries.holder(access, MxtResourceKeys.ARTIFACT, capability.artifact())
                            .map(artifact -> artifact.value().toggables().stream()
                                    .anyMatch(ability -> ability.key().equals(capability.key())))
                            .orElse(false)).orElse(false);
            case EMPTY -> false;
        };
    }

    public Component displayName() {
        return Component.translatable("wheel.mxt.kind." + this.getSerializedName());
    }
}
