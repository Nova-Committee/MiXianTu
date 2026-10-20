package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.profiling.Profiler;
import com.iafenvoy.mxt.util.profiling.ProfilerFrame;
import com.iafenvoy.mxt.util.profiling.ProfilerSource;
import com.iafenvoy.mxt.util.profiling.ProfilerWindowSnapshot;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ProfilerSnapshotS2CPayload(UUID token, ProfilerFrame frame) implements CustomPacketPayload {
    private static final int MAX_SOURCES = 64;
    private static final int MAX_PRODUCERS = 4;
    private static final int MAX_CELLS = 16_384;
    public static final Type<ProfilerSnapshotS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "profiler_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProfilerSnapshotS2CPayload> STREAM_CODEC = StreamCodec.of(
            ProfilerSnapshotS2CPayload::encode, ProfilerSnapshotS2CPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, ProfilerSnapshotS2CPayload value) {
        ProfilerWindowSnapshot window = value.frame.window();
        if (window.sources().size() > MAX_SOURCES || window.producers().size() > MAX_PRODUCERS
                || window.producers().stream().mapToInt(p -> p.sampleCount() * window.sources().size()).sum() > MAX_CELLS)
            throw new EncoderException("Profiler snapshot exceeds bounds");
        buffer.writeUUID(value.token);
        buffer.writeVarLong(value.frame.sequence());
        buffer.writeBoolean(value.frame.enabled());
        buffer.writeVarLong(window.generation());
        buffer.writeVarLong(window.version());
        buffer.writeVarLong(window.droppedBatches());
        buffer.writeVarInt(window.sources().size());
        for (ProfilerSource source : window.sources()) buffer.writeUtf(source.id(), 128);
        buffer.writeVarInt(window.producers().size());
        for (ProfilerWindowSnapshot.Producer producer : window.producers()) {
            buffer.writeVarLong(producer.id());
            buffer.writeUtf(producer.name(), 128);
            buffer.writeVarLong(producer.firstTick());
            buffer.writeVarLong(producer.discardedSpans());
            buffer.writeVarInt(producer.sampleCount());
            for (int tick = 0; tick < producer.sampleCount(); tick++) {
                buffer.writeBoolean(producer.complete(tick));
                for (ProfilerSource source : window.sources()) {
                    buffer.writeVarLong(producer.nanos(tick, source));
                    buffer.writeVarLong(producer.hits(tick, source));
                }
            }
        }
    }

    private static ProfilerSnapshotS2CPayload decode(RegistryFriendlyByteBuf buffer) {
        UUID token = buffer.readUUID();
        long sequence = nonnegative(buffer.readVarLong());
        boolean enabled = buffer.readBoolean();
        long generation = nonnegative(buffer.readVarLong());
        long version = nonnegative(buffer.readVarLong());
        long dropped = nonnegative(buffer.readVarLong());
        int sourceCount = count(buffer, MAX_SOURCES);
        List<String> sources = new ArrayList<>(sourceCount);
        for (int index = 0; index < sourceCount; index++) sources.add(buffer.readUtf(128));
        int producerCount = count(buffer, MAX_PRODUCERS);
        List<ProfilerWindowSnapshot.ProducerData> producers = new ArrayList<>(producerCount);
        int cells = 0;
        for (int index = 0; index < producerCount; index++) {
            long id = nonnegative(buffer.readVarLong());
            String name = buffer.readUtf(128);
            long first = nonnegative(buffer.readVarLong());
            long discarded = nonnegative(buffer.readVarLong());
            int ticks = count(buffer, Profiler.WINDOW_TICKS);
            cells += ticks * sourceCount;
            if (cells > MAX_CELLS) throw new DecoderException("Profiler sample budget exceeded");
            long[][] samples = new long[ticks][sourceCount * 2];
            boolean[] complete = new boolean[ticks];
            for (int tick = 0; tick < ticks; tick++) {
                complete[tick] = buffer.readBoolean();
                for (int cell = 0; cell < sourceCount * 2; cell++)
                    samples[tick][cell] = nonnegative(buffer.readVarLong());
            }
            producers.add(new ProfilerWindowSnapshot.ProducerData(id, name, first, samples, complete, discarded));
        }
        try {
            return new ProfilerSnapshotS2CPayload(token, new ProfilerFrame(sequence, enabled,
                    ProfilerWindowSnapshot.restore(generation, version, dropped, sources, producers)));
        } catch (IllegalArgumentException exception) {
            throw new DecoderException("Invalid profiler snapshot", exception);
        }
    }

    private static int count(RegistryFriendlyByteBuf buffer, int max) {
        int count = buffer.readVarInt();
        if (count < 0 || count > max) throw new DecoderException("Invalid profiler collection size");
        return count;
    }

    private static long nonnegative(long value) {
        if (value < 0L) throw new DecoderException("Negative profiler value");
        return value;
    }

    @Override
    public @NonNull Type<ProfilerSnapshotS2CPayload> type() {
        return TYPE;
    }
}
