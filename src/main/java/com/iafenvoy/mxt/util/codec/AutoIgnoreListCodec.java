package com.iafenvoy.mxt.util.codec;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.*;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public record AutoIgnoreListCodec<E>(Codec<E> elementCodec) implements Codec<List<E>> {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static <E> AutoIgnoreListCodec<E> create(Codec<E> elementCodec) {
        return new AutoIgnoreListCodec<>(elementCodec);
    }

    @Override
    public <T> DataResult<Pair<List<E>, T>> decode(DynamicOps<T> ops, T input) {
        return ops.getList(input).setLifecycle(Lifecycle.stable()).flatMap(stream -> {
            final DecoderState<T> decoder = new DecoderState<>(ops);
            stream.accept(decoder::accept);
            return decoder.build();
        });
    }

    @Override
    public <T> DataResult<T> encode(List<E> input, DynamicOps<T> ops, T prefix) {
        final ListBuilder<T> builder = ops.listBuilder();
        for (final E element : input) {
            DataResult<T> result = this.elementCodec.encodeStart(ops, element);
            if (result.isSuccess()) builder.add(result);
            else LOGGER.warn("Failed to encode element: {}, error: {}", element, result.error().orElseThrow());
        }
        return builder.build(prefix);
    }

    private class DecoderState<T> {
        private final DynamicOps<T> ops;
        private final List<E> elements = new ArrayList<>();

        private DecoderState(final DynamicOps<T> ops) {
            this.ops = ops;
        }

        public void accept(final T value) {
            final DataResult<Pair<E, T>> elementResult = AutoIgnoreListCodec.this.elementCodec.decode(this.ops, value);
            elementResult.result().ifPresentOrElse(pair -> this.elements.add(pair.getFirst()),
                    () -> LOGGER.warn("Ignoring invalid list element: {}", elementResult.error().orElseThrow().message()));
        }

        public DataResult<Pair<List<E>, T>> build() {
            final Pair<List<E>, T> pair = Pair.of(List.copyOf(this.elements), this.ops.empty());
            return DataResult.success(pair, Lifecycle.stable());
        }
    }
}
