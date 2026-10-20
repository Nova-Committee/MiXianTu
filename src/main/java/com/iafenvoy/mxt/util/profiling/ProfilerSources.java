package com.iafenvoy.mxt.util.profiling;

import java.util.*;

public final class ProfilerSources {
    private final List<ProfilerSource> sources = new ArrayList<>();
    private final Map<String, ProfilerSource> byId = new HashMap<>();

    ProfilerSources() {
    }

    // Register once outside the sampling path; repeated ids share one source.
    public synchronized ProfilerSource register(String id) {
        Objects.requireNonNull(id);
        if (id.isBlank()) throw new IllegalArgumentException("A profiler source id cannot be blank");
        return this.byId.computeIfAbsent(id, key -> {
            ProfilerSource source = new ProfilerSource(this, this.sources.size(), key);
            this.sources.add(source);
            return source;
        });
    }

    public synchronized List<ProfilerSource> all() {
        return List.copyOf(this.sources);
    }

    synchronized int size() {
        return this.sources.size();
    }
}
