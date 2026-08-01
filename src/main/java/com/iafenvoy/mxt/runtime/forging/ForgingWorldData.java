package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.attachment.ForgingSessionData;
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
public final class ForgingWorldData {
    public static final MapCodec<ForgingWorldData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Codec.LONG, StationSession.CODEC).optionalFieldOf("sessions", Map.of()).forGetter(ForgingWorldData::encoded)
    ).apply(instance, ForgingWorldData::decode));
    public static final Codec<ForgingWorldData> CODEC = MAP_CODEC.codec();
    private final Map<Long, StationSession> sessions;

    public ForgingWorldData() {
        this(Map.of());
    }

    private ForgingWorldData(Map<Long, StationSession> sessions) {
        this.sessions = new LinkedHashMap<>(sessions);
    }

    private static ForgingWorldData decode(Map<Long, StationSession> sessions) {
        return new ForgingWorldData(sessions);
    }

    public Optional<StationSession> get(BlockPos position) {
        return Optional.ofNullable(this.sessions.get(position.asLong()));
    }

    public boolean put(BlockPos position, UUID owner, ForgingSessionData data) {
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
        return Map.copyOf(result);
    }

    private Map<Long, StationSession> encoded() {
        return Map.copyOf(this.sessions);
    }

    public record StationSession(UUID owner, ForgingSessionData session) {
        public static final Codec<StationSession> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(StationSession::owner), ForgingSessionData.CODEC.fieldOf("session").forGetter(StationSession::session)
        ).apply(instance, StationSession::new));
    }
}
