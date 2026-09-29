package com.iafenvoy.mxt.util;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

/**
 * The chains of one registry, walked once and kept. A chain is a line of entries linked by one successor field: an
 * entry names at most one entry after it, so a line is walked from its head and every entry on it can be read in
 * either direction without walking the links again. An entry two others name as their successor is a fork and is
 * reported, because "what follows this" then has no single answer; nothing is ever indexed partially, since a line
 * whose entry refuses, repeats, or runs into another line is left out whole.
 *
 * <p>The caller owns the vocabulary: it says which entries start a line, what each entry hands over to, why one
 * cannot be walked, what a line is known by, and how the problem reads.
 *
 * <p>An index is expensive enough to keep: {@link #cached} memoises one per key, which for both chains in this mod
 * is the registry instance they were read from, so {@link #invalidate} has to be called when a data pack replaces
 * it (a reload may keep the same instance).
 */
public final class ChainCache<T> {
    private static final int MAX_CACHED_REGISTRIES = 4;

    private static final Object LOCK = new Object();
    private static final Map<Object, ChainCache<?>> CACHE = new LinkedHashMap<>();

    private final Map<Identifier, Chain<T>> byNode;
    private final Map<Identifier, Chain<T>> byKey;
    private final List<Report> reports;

    private ChainCache(Map<Identifier, Chain<T>> byNode, Map<Identifier, Chain<T>> byKey, List<Report> reports) {
        this.byNode = byNode;
        this.byKey = byKey;
        this.reports = reports;
    }

    public static <T> ChainCache<T> empty() {
        return new ChainCache<>(Map.of(), Map.of(), List.of());
    }

    public static void invalidate() {
        synchronized (LOCK) {
            CACHE.clear();
        }
    }

    /**
     * The cache built for one key, built on first use. A key has to keep meaning the same thing: the cached value
     * is handed back exactly as it was built, so one key may not be reused for a different node type.
     */
    @SuppressWarnings("unchecked")
    public static <T> ChainCache<T> cached(Object key, Supplier<ChainCache<T>> build) {
        synchronized (LOCK) {
            ChainCache<?> cached = CACHE.get(key);
            if (cached != null) {
                // Re-inserting keeps the eviction order least-recently-used.
                CACHE.remove(key);
                CACHE.put(key, cached);
                return (ChainCache<T>) cached;
            }
        }
        ChainCache<T> built = build.get();
        synchronized (LOCK) {
            CACHE.put(key, built);
            while (CACHE.size() > MAX_CACHED_REGISTRIES) CACHE.remove(CACHE.keySet().iterator().next());
        }
        return built;
    }

    /**
     * The chain an entry is on, empty when the walk left it out.
     */
    public Optional<Chain<T>> chainOf(Identifier node) {
        return Optional.ofNullable(this.byNode.get(node));
    }

    /**
     * The chain known by a key, empty for a key no line carries.
     */
    public Optional<Chain<T>> chain(Identifier key) {
        return Optional.ofNullable(this.byKey.get(key));
    }

    /**
     * The head of the chain known by a key, empty for a key no line carries.
     */
    public Optional<Holder<T>> first(Identifier key) {
        return this.chain(key).map(Chain::first);
    }

    /**
     * The entry one step above this one, empty at the top of its chain and for an entry no chain holds.
     */
    public Optional<Holder<T>> next(Identifier node) {
        return this.chainOf(node).flatMap(chain -> chain.next(node));
    }

    /**
     * The same step downwards, empty at the head of its chain.
     */
    public Optional<Holder<T>> previous(Identifier node) {
        return this.chainOf(node).flatMap(chain -> chain.previous(node));
    }

    /**
     * Where an entry sits on its chain, counted from the head, empty for an entry no chain holds.
     */
    public Optional<Integer> rankOf(Identifier node) {
        return this.chainOf(node).map(chain -> chain.indexOf(node));
    }

    /**
     * The head of the chain an entry is on, empty for an entry no chain holds.
     */
    public Optional<Holder<T>> firstOf(Identifier node) {
        return this.chainOf(node).map(Chain::first);
    }

    /**
     * What the chain an entry is on is known by: empty for an entry no chain holds, and for a chain with no name.
     */
    public Optional<Identifier> keyOf(Identifier node) {
        return this.chainOf(node).map(Chain::key);
    }

    public boolean contains(Identifier node) {
        return this.byNode.containsKey(node);
    }

    /**
     * How many entries the walk indexed.
     */
    public int size() {
        return this.byNode.size();
    }

    /**
     * Every problem the walk found, each naming the entry whose file an author has to open.
     */
    public List<Report> reports() {
        return this.reports;
    }

    /**
     * The same chains with more problems on them, for the checks a caller makes once it has the lines.
     */
    public ChainCache<T> with(List<Report> extra) {
        if (extra.isEmpty()) return this;
        List<Report> all = new ArrayList<>(this.reports);
        all.addAll(extra);
        return new ChainCache<>(this.byNode, this.byKey, List.copyOf(all));
    }

    /**
     * One walked line, low to high. {@code key} is what the caller knows the line by, null when it has no name.
     */
    public record Chain<T>(@Nullable Identifier key, List<Holder<T>> nodes) {
        public int size() {
            return this.nodes.size();
        }

        public Holder<T> first() {
            return this.nodes.getFirst();
        }

        public Holder<T> last() {
            return this.nodes.getLast();
        }

        public Optional<Holder<T>> at(int index) {
            return index < 0 || index >= this.nodes.size() ? Optional.empty() : Optional.of(this.nodes.get(index));
        }

        // -1 when the entry is not on this line. Compared by id because a holder is not stable across reloads.
        public int indexOf(Identifier node) {
            for (int index = 0; index < this.nodes.size(); index++)
                if (HolderHelper.id(this.nodes.get(index)).equals(node)) return index;
            return -1;
        }

        public boolean contains(Identifier node) {
            return this.indexOf(node) >= 0;
        }

        // The step above, empty at the top of the line and for an entry this line does not hold.
        public Optional<Holder<T>> next(Identifier node) {
            int index = this.indexOf(node);
            return index < 0 ? Optional.empty() : this.at(index + 1);
        }

        // The same step downwards, empty at the head of the line.
        public Optional<Holder<T>> previous(Identifier node) {
            int index = this.indexOf(node);
            return index <= 0 ? Optional.empty() : this.at(index - 1);
        }

        public List<Identifier> ids() {
            return this.nodes.stream().map(HolderHelper::id).toList();
        }
    }

    /**
     * One problem, at the entry whose file an author has to open.
     */
    public record Report(Identifier node, String message) {
    }

    /**
     * One registry's lines as the caller walks it: the entries, what each one hands over to, where a line starts
     * with the key it is known by, and what stops a line. Nothing is read until {@link #build}.
     */
    public static final class Builder<T> {
        private final Map<Identifier, Holder<T>> nodes;
        private final Map<Identifier, Identifier> links = new LinkedHashMap<>();
        private final Map<Identifier, @Nullable Identifier> heads = new LinkedHashMap<>();
        private final Map<Identifier, String> refusals = new LinkedHashMap<>();

        private Builder(Map<Identifier, Holder<T>> nodes) {
            this.nodes = nodes;
        }

        // The entry this one hands over to; absent means the line ends here.
        public Builder<T> link(Identifier node, Identifier next) {
            this.links.put(node, next);
            return this;
        }

        // A line starts here; the key is what the caller knows it by, null when it has no name for it.
        public Builder<T> head(Identifier node, @Nullable Identifier key) {
            this.heads.put(node, key);
            return this;
        }

        // This entry's own line cannot be walked at all, and the message says why.
        public Builder<T> refuse(Identifier node, String message) {
            this.refusals.put(node, message);
            return this;
        }

        public ChainCache<T> build() {
            List<Report> reports = new ArrayList<>(forks(this.links));
            this.refusals.forEach((node, message) -> reports.add(new Report(node, message)));
            Map<Identifier, Chain<T>> byNode = new LinkedHashMap<>();
            Map<Identifier, Chain<T>> byKey = new LinkedHashMap<>();
            for (Map.Entry<Identifier, @Nullable Identifier> head : this.heads.entrySet()) {
                // Walked from its head; an entry the walk stops at leaves the whole line out, since a prefix is
                // not the line the data describes.
                List<Identifier> order = new ArrayList<>();
                Set<Identifier> seen = new LinkedHashSet<>();
                Identifier stopped = null;
                Identifier current = head.getKey();
                while (current != null) {
                    if (this.refusals.containsKey(current) || !this.nodes.containsKey(current)
                            || !seen.add(current) || byNode.containsKey(current)) {
                        stopped = current;
                        break;
                    }
                    order.add(current);
                    current = this.links.get(current);
                }
                if (stopped != null) {
                    // A refused entry already reported itself; anything else is this line's own problem.
                    if (!this.refusals.containsKey(stopped))
                        reports.add(new Report(head.getKey(), stopMessage(stopped, this.nodes, byNode)));
                    continue;
                }
                List<Holder<T>> walked = new ArrayList<>();
                for (Identifier node : order) walked.add(this.nodes.get(node));
                Chain<T> chain = new Chain<>(head.getValue(), List.copyOf(walked));
                order.forEach(node -> byNode.put(node, chain));
                // Two lines may carry the same key when the caller reports that itself; the first one keeps it.
                if (head.getValue() != null) byKey.putIfAbsent(head.getValue(), chain);
            }
            return new ChainCache<>(Map.copyOf(byNode), Map.copyOf(byKey), List.copyOf(reports));
        }
    }

    /**
     * Collects the lines of one registry, going by the entries it hands over: every entry has to be in {@code
     * nodes} before anything is read, since a line is walked by id.
     */
    public static <T> Builder<T> builder(Map<Identifier, Holder<T>> nodes) {
        return new Builder<>(Map.copyOf(nodes));
    }

    // Two entries naming one successor: the successor is reported, and the first claimant in map order is the one
    // the walk below keeps, so what a fork resolves to is fixed by the caller's own order rather than by chance.
    private static List<Report> forks(Map<Identifier, Identifier> links) {
        List<Report> reports = new ArrayList<>();
        Map<Identifier, Identifier> below = new LinkedHashMap<>();
        links.forEach((node, next) -> {
            Identifier other = below.putIfAbsent(next, node);
            if (other != null && !other.equals(node))
                reports.add(new Report(next, "follows both " + other + " and " + node));
        });
        return reports;
    }

    // Why the walk stopped there, for an entry the caller did not refuse itself.
    private static <T> String stopMessage(Identifier stopped, Map<Identifier, Holder<T>> nodes,
                                          Map<Identifier, Chain<T>> indexed) {
        if (!nodes.containsKey(stopped)) return "reaches " + stopped + ", which is not an entry";
        return indexed.containsKey(stopped) ? "joins " + stopped + ", which another chain already holds"
                : "the chain is cyclic at " + stopped;
    }
}
