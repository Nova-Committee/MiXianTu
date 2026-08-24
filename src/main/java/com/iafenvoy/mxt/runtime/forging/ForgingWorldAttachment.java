package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.attachment.ForgingSessionAttachment;
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
public final class ForgingWorldAttachment {
    public static final MapCodec<ForgingWorldAttachment> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.LONG, StationSession.CODEC).optionalFieldOf("sessions", Map.of()).forGetter(ForgingWorldAttachment::encoded)
    ).apply(i, ForgingWorldAttachment::new));
    public static final Codec<ForgingWorldAttachment> CODEC = MAP_CODEC.codec();
    private final Map<Long, StationSession> sessions;

    public ForgingWorldAttachment() {
        this(Map.of());
    }

    private ForgingWorldAttachment(Map<Long, StationSession> sessions) {
        this.sessions = new LinkedHashMap<>(sessions);
    }

    public Optional<StationSession> get(BlockPos position) {
        return Optional.ofNullable(this.sessions.get(position.asLong()));
    }

    public boolean put(BlockPos position, UUID owner, ForgingSessionAttachment data) {
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

    public record StationSession(UUID owner, ForgingSessionAttachment session) {
        public static final Codec<StationSession> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(StationSession::owner),
                ForgingSessionAttachment.CODEC.codec().fieldOf("session").forGetter(StationSession::session)
        ).apply(i, StationSession::new));
    }
}
