package com.iafenvoy.mxt.util.chain;

import net.minecraft.core.Holder;

import java.util.Optional;

public interface ChainNode<T> {
    Optional<Holder<T>> next();
}
