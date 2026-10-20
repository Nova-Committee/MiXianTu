package com.iafenvoy.mxt.util.profiling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Instances own their arrays; accessors expose scalar values and immutable metadata only.
public record ProfilerWindowSnapshot(long generation, long version, List<ProfilerSource> sources,
                                     List<Producer> producers, long droppedBatches) {
    public static final ProfilerWindowSnapshot EMPTY = new ProfilerWindowSnapshot(0L, 0L, List.of(), List.of(), 0L);

    public ProfilerWindowSnapshot(long generation, long version, List<ProfilerSource> sources, List<Producer> producers, long droppedBatches) {
        this.generation = generation;
        this.version = version;
        this.sources = List.copyOf(sources);
        this.producers = List.copyOf(producers);
        this.droppedBatches = droppedBatches;
    }

    // Copy external arrays at the boundary; decoded snapshots obey the same ownership as consumer snapshots.
    public static ProfilerWindowSnapshot restore(long generation, long version, long droppedBatches, List<String> ids, List<ProducerData> data) {
        if (generation < 0 || version < 0 || droppedBatches < 0 || ids.stream().distinct().count() != ids.size())
            throw new IllegalArgumentException("Invalid profiler metadata");
        ProfilerSources registry = new ProfilerSources();
        List<ProfilerSource> sources = ids.stream().map(registry::register).toList();
        List<Producer> producers = new ArrayList<>();
        for (ProducerData item : data) {
            if (item.firstTick < 0 || item.discardedSpans < 0 || item.samples.length > Profiler.WINDOW_TICKS
                    || item.samples.length != item.complete.length)
                throw new IllegalArgumentException("Invalid profiler window");
            long[][] samples = new long[item.samples.length][];
            for (int tick = 0; tick < samples.length; tick++) {
                if (item.samples[tick].length != sources.size() * 2 || Arrays.stream(item.samples[tick]).anyMatch(value -> value < 0L))
                    throw new IllegalArgumentException("Invalid profiler samples");
                samples[tick] = item.samples[tick].clone();
            }
            producers.add(new Producer(item.id, item.name, item.firstTick, sources, samples, item.complete.clone(), item.discardedSpans));
        }
        return new ProfilerWindowSnapshot(generation, version, sources, producers, droppedBatches);
    }

    public record ProducerData(long id, String name, long firstTick, long[][] samples, boolean[] complete,
                               long discardedSpans) {
    }

    public record Producer(long id, String name, long firstTick, List<ProfilerSource> sources, long[][] samples,
                           boolean[] complete, long discardedSpans) {
        public int sampleCount() {
            return this.samples.length;
        }

        public boolean complete(int sample) {
            return this.complete[sample];
        }

        public long nanos(int sample, ProfilerSource source) {
            return this.samples[sample][this.index(source) * 2];
        }

        public long hits(int sample, ProfilerSource source) {
            return this.samples[sample][this.index(source) * 2 + 1];
        }

        public Summary summary(ProfilerSource source) {
            return this.summarize(this.index(source) * 2, null);
        }

        private Summary summarize(int offset, long[] values) {
            int count = 0;
            long min = Long.MAX_VALUE, max = 0L;
            long hits = 0L;
            double total = 0D;
            for (int index = 0; index < this.samples.length; index++) {
                if (!this.complete[index]) continue;
                long value = this.samples[index][offset];
                if (values != null) values[count] = value;
                count++;
                min = Math.min(min, value);
                max = Math.max(max, value);
                total += value;
                hits += this.samples[index][offset + 1];
            }
            return new Summary(count, count == 0 ? 0L : min, count == 0 ? 0D : total / count, max, hits);
        }

        // Include idle zero ticks; exclude missing or partial ticks. Percentiles use nearest ranks.
        public Statistics statistics(ProfilerSource source) {
            long[] values = new long[this.samples.length];
            Summary summary = this.summarize(this.index(source) * 2, values);
            int count = summary.samples;
            if (count == 0) return new Statistics(0, 0L, 0D, 0L, 0L, 0L, 0L, 0L);
            Arrays.sort(values, 0, count);
            return new Statistics(count, summary.minNanos, summary.averageNanos, summary.maxNanos,
                    percentile(values, count, 0.50D), percentile(values, count, 0.95D),
                    percentile(values, count, 0.99D), summary.hits);
        }

        private int index(ProfilerSource source) {
            if (source.index() >= this.sources.size() || this.sources.get(source.index()) != source)
                throw new IllegalArgumentException("Source is not part of this snapshot");
            return source.index();
        }

        private static long percentile(long[] values, int count, double fraction) {
            return values[(int) Math.ceil(count * fraction) - 1];
        }
    }

    public record Statistics(int samples, long minNanos, double averageNanos, long maxNanos,
                             long medianNanos, long p95Nanos, long p99Nanos, long hits) {
    }

    public record Summary(int samples, long minNanos, double averageNanos, long maxNanos, long hits) {
    }
}
