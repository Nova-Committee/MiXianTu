package com.iafenvoy.mxt.util.chain;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public final class ChainCache<T> {
    private final List<Chain<T>> chains;
    private final Map<Holder<T>, Chain<T>> byNode;

    public ChainCache(List<Chain<T>> chains) {
        this.chains = chains;
        ImmutableMap.Builder<Holder<T>, Chain<T>> builder = ImmutableMap.builder();
        for (Chain<T> chain : chains)
            for (Holder<T> holder : chain.nodes())
                builder.put(holder, chain);
        this.byNode = builder.build();
    }

    public static <T> ChainCache<T> empty() {
        return new ChainCache<>(List.of());
    }

    public static <T extends ChainNode<T>> ChainCache<T> of(HolderLookup<T> registry) {
        return new ChainCache<>(ChainWalker.walk(registry.listElements().map(x -> (Holder<T>) x).toList(), x -> x.value().next().orElse(null), Chain::new));
    }

    public static <T> ChainCache<T> of(HolderLookup<T> registry, UnaryOperator<Holder<T>> nextGetter) {
        return new ChainCache<>(ChainWalker.walk(registry.listElements().map(x -> (Holder<T>) x).toList(), nextGetter, Chain::new));
    }

    public Optional<Chain<T>> chainOf(Holder<T> node) {
        return Optional.ofNullable(this.byNode.get(node));
    }

    public Optional<Holder<T>> next(Holder<T> node) {
        return this.chainOf(node).flatMap(chain -> chain.next(node));
    }

    public Optional<Holder<T>> previous(Holder<T> node) {
        return this.chainOf(node).flatMap(chain -> chain.previous(node));
    }

    public Optional<Integer> rankOf(Holder<T> node) {
        return this.chainOf(node).map(chain -> chain.indexOf(node));
    }

    public boolean contains(Holder<T> node) {
        return this.byNode.containsKey(node);
    }

    public int size() {
        return this.chains.size();
    }

    public <R> void validate(Function<T, R> mapper, BiPredicate<R, R> predicate) {
        this.validate((a, b) -> predicate.test(mapper.apply(a), mapper.apply(b)));
    }

    public void validate(BiPredicate<T, T> predicate) {
        this.chains.forEach(x -> x.validate(predicate));
    }
}
