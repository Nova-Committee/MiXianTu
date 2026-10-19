package com.iafenvoy.mxt.runtime.failure;

import com.mojang.serialization.Codec;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class AlchemyFailure {
    public static final Failure FURNACE_QUALITY = SimpleFailure.of("furnace_quality", FailureSources.ALCHEMY_FURNACE);
    public static final Failure FURNACE_TIER = SimpleFailure.of("furnace_tier", FailureSources.ALCHEMY_FURNACE);
    public static final Failure INPUT_TIER = SimpleFailure.of("input_tier", FailureSources.ALCHEMY_FURNACE);
    public static final Failure QUALITY_CONDITIONS = SimpleFailure.of("quality_conditions", FailureSources.ALCHEMY_FURNACE);
    public static final Failure BINDING_CONDITIONS = SimpleFailure.of("binding_conditions", FailureSources.ALCHEMY_FURNACE);
    public static final Failure NO_FURNACE = SimpleFailure.of("no_furnace", FailureSources.ALCHEMY_FURNACE);
    public static final Failure STRUCTURE = SimpleFailure.of("structure", FailureSources.ALCHEMY_FURNACE);
    public static final Failure ACTIVE = SimpleFailure.of("active", FailureSources.ALCHEMY_FURNACE);
    public static final Failure CAPACITY = SimpleFailure.of("capacity", FailureSources.ALCHEMY_FURNACE);
    public static final Failure SLOTS = SimpleFailure.of("slots", FailureSources.ALCHEMY_FURNACE);
    public static final Failure ZERO_POWER = SimpleFailure.of("zero_power", FailureSources.ALCHEMY_FURNACE);
    public static final Failure NOT_HERB = SimpleFailure.of("not_herb", FailureSources.ALCHEMY_FURNACE);
    public static final Failure INSUFFICIENT = SimpleFailure.of("insufficient", FailureSources.ALCHEMY_FURNACE);
    public static final Failure CONFLICT = SimpleFailure.of("conflict", FailureSources.ALCHEMY_FURNACE);
    public static final Failure IMBALANCE = SimpleFailure.of("imbalance", FailureSources.ALCHEMY_FURNACE);
    public static final Failure AMBIGUOUS = SimpleFailure.of("ambiguous", FailureSources.ALCHEMY_FURNACE);
    public static final Failure TEMPERATURE = SimpleFailure.of("temperature", FailureSources.ALCHEMY_FURNACE);
    public static final Failure ENVIRONMENT = SimpleFailure.of("environment", FailureSources.ALCHEMY_FURNACE);
    public static final Failure OUTPUT_CAPACITY = SimpleFailure.of("output_capacity", FailureSources.ALCHEMY_FURNACE);
    public static final Failure CANCELLED = SimpleFailure.of("cancelled", FailureSources.ALCHEMY_FURNACE);
    public static final Failure DISABLED = SimpleFailure.of("disabled", FailureSources.ALCHEMY_FURNACE);

    private static final List<Failure> ALL = List.of(FURNACE_QUALITY, FURNACE_TIER, INPUT_TIER, QUALITY_CONDITIONS,
            BINDING_CONDITIONS, NO_FURNACE, STRUCTURE, ACTIVE, CAPACITY, SLOTS, ZERO_POWER, NOT_HERB, INSUFFICIENT,
            CONFLICT, IMBALANCE, AMBIGUOUS, TEMPERATURE, ENVIRONMENT, OUTPUT_CAPACITY, CANCELLED, DISABLED);
    private static final Map<String, Failure> BY_NAME = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(Failure::getSerializedName, Function.identity()));

    /**
     * The stored shape is one plain string, and a name this build no longer defines still loads as a refusal of this
     * module instead of breaking the snapshot that carries it.
     */
    public static final Codec<Failure> CODEC = Codec.STRING.xmap(AlchemyFailure::resolve, Failure::getSerializedName);

    private AlchemyFailure() {
    }

    private static Failure resolve(String name) {
        return BY_NAME.getOrDefault(name, SimpleFailure.of(name, FailureSources.ALCHEMY_FURNACE));
    }
}
