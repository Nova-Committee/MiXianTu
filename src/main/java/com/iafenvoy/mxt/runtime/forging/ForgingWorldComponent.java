package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.attachment.ForgingSessionComponent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Level-persistent work sessions keyed by a validated forging workstation position.
 */
public final class ForgingWorldComponent {
    public static final MapCodec<ForgingWorldComponent> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.LONG, StationSession.CODEC).optionalFieldOf("sessions", Map.of()).forGetter(ForgingWorldComponent::encoded)
    ).apply(i, ForgingWorldComponent::new));
    public static final Codec<ForgingWorldComponent> CODEC = MAP_CODEC.codec();
    private final Map<Long, StationSession> sessions;

    public ForgingWorldComponent() {
        this(Map.of());
    }

    private ForgingWorldComponent(Map<Long, StationSession> sessions) {
        this.sessions = new LinkedHashMap<>(sessions);
    }

    public Optional<StationSession> get(BlockPos position) {
        return Optional.ofNullable(this.sessions.get(position.asLong()));
    }

    public boolean put(BlockPos position, UUID owner, ForgingSessionComponent data) {
        long key = position.asLong();
        if (this.sessions.containsKey(key)) return false;
        this.sessions.put(key, new StationSession(owner, data));
        return true;
    }

    public Optional<StationSession> remove(BlockPos position) {
        return Optional.ofNullable(this.sessions.remove(position.asLong()));
    }

    public Map<BlockPos, StationSession> sessions() {
        Map<BlockPos, StationSession> result = new LinkedHashMap<>();
        this.sessions.forEach((key, value) -> result.put(BlockPos.of(key), value));
        return result;
    }

    private Map<Long, StationSession> encoded() {
        return this.sessions;
    }

    public record StationSession(UUID owner, ForgingSessionComponent session) {
        public static final Codec<StationSession> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(StationSession::owner),
                ForgingSessionComponent.CODEC.codec().fieldOf("session").forGetter(StationSession::session)
        ).apply(i, StationSession::new));
    }
}
