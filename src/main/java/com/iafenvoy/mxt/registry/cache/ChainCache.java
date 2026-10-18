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

public class ChainCache<T extends ChainNode<T>> extends RegistryCache<ChainCache<T>> {
    private final List<Chain<T>> chains = new LinkedList<>();
    private final Map<Holder<T>, Chain<T>> byNode = new LinkedHashMap<>();
    private final List<BiPredicate<T, T>> chainValidators = new LinkedList<>();
    private final ResourceKey<Registry<T>> registry;

    public ChainCache(ResourceKey<Registry<T>> registry) {
        this.registry = registry;
    }

    @Override
    public void build(RegistryAccess access) {
        this.chains.addAll(ChainWalker.walk(access.lookupOrThrow(this.registry).listElements().map(x -> (Holder<T>) x).toList(), x -> x.value().next().orElse(null), Chain::new));
        for (Chain<T> chain : this.chains)
            for (Holder<T> holder : chain.nodes())
                this.byNode.put(holder, chain);
        this.chains.forEach(c -> this.chainValidators.forEach(c::validate));
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
