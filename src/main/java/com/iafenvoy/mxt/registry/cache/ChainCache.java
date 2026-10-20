package com.iafenvoy.mxt.registry.cache;

import com.iafenvoy.mxt.util.chain.Chain;
import com.iafenvoy.mxt.util.chain.ChainNode;
import com.iafenvoy.mxt.util.chain.ChainWalker;
import com.iafenvoy.mxt.util.math.CompareOperation;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

public class ChainCache<T extends ChainNode<T>> extends RegistryCache<ChainCache<T>> {
    private final ResourceKey<Registry<T>> registry;
    private final List<Chain<T>> chains = new LinkedList<>();
    private final Map<Holder<T>, Chain<T>> byNode = new LinkedHashMap<>();
    private final List<BiPredicate<T, T>> chainValidators = new LinkedList<>();
    private final List<Predicate<Chain<T>>> lineRules = new LinkedList<>();

    public ChainCache(ResourceKey<Registry<T>> registry) {
        this.registry = registry;
    }

    @Override
    public void build(RegistryAccess access) {
        this.chains.addAll(ChainWalker.walk(access.lookupOrThrow(this.registry).listElements().map(x -> (Holder<T>) x).toList(), x -> x.value().next().orElse(null), Chain::new));
        // A line the domain rejects is dropped whole before anything is published, so no caller can read a step out
        // of a ladder that the pack itself declared wrong.
        this.chains.removeIf(chain -> !this.accepts(chain));
        for (Chain<T> chain : this.chains)
            for (Holder<T> holder : chain.nodes())
                this.byNode.put(holder, chain);
    }

    private boolean accepts(Chain<T> chain) {
        boolean whole = true;
        for (BiPredicate<T, T> validator : this.chainValidators) whole &= chain.validate(validator);
        for (Predicate<Chain<T>> rule : this.lineRules)
            if (!rule.test(chain)) {
                LOGGER.error("Chain {} breaks a domain rule and is left out whole.", chain.ids());
                whole = false;
            }
        return whole;
    }

    @Override
    protected void clear() {
        this.chains.clear();
        this.byNode.clear();
    }

    @Override
    public ChainCache<T> get() {
        return this;
    }

    @Override
    public ChainCache<T> callback(BiConsumer<RegistryAccess, ChainCache<T>> callback) {
        return (ChainCache<T>) super.callback(callback);
    }

    public <R> ChainCache<T> validate(Function<T, R> mapper, BiPredicate<R, R> predicate) {
        return this.validate((a, b) -> predicate.test(mapper.apply(a), mapper.apply(b)));
    }

    public ChainCache<T> validate(BiPredicate<T, T> predicate) {
        this.chainValidators.add(predicate);
        return this;
    }

    /**
     * A rule a whole line has to satisfy, which a per-link check cannot express - a ladder naming itself twice, or
     * one that climbs into another domain. A line that breaks one is dropped whole rather than published.
     */
    public ChainCache<T> require(Predicate<Chain<T>> rule) {
        this.lineRules.add(rule);
        return this;
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

    public OptionalInt rankOf(Holder<T> node) {
        Optional<Chain<T>> chain = this.chainOf(node);
        return chain.map(tChain -> OptionalInt.of(tChain.indexOf(node))).orElseGet(OptionalInt::empty);
    }

    /**
     * Whether both entries sit on one line and their ranks satisfy the operation. A rank only means something
     * inside its own line, so a pair spanning two lines answers false instead of comparing unrelated indices.
     */
    public boolean compare(CompareOperation operation, Holder<T> current, Holder<T> required) {
        return this.chainOf(required)
                .filter(chain -> chain.contains(current))
                .map(chain -> operation.compare(chain.indexOf(current), chain.indexOf(required)))
                .orElse(false);
    }

    public boolean contains(Holder<T> node) {
        return this.byNode.containsKey(node);
    }

    public int size() {
        return this.chains.size();
    }
}
