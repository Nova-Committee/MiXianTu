package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.ability.AbilityComponentState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Ability grants are tracked by source, so removing one source cannot remove another source's ability.
 */
public final class AbilityHolderData {
    public static final MapCodec<AbilityHolderData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Identifier.CODEC, Identifier.CODEC.listOf()).optionalFieldOf("sources", Map.of()).forGetter(AbilityHolderData::sources),
            Codec.unboundedMap(Identifier.CODEC, Codec.LONG).optionalFieldOf("cooldowns", Map.of()).forGetter(AbilityHolderData::cooldowns),
            Codec.unboundedMap(Identifier.CODEC, Codec.unboundedMap(Codec.STRING, AbilityComponentState.CODEC)).optionalFieldOf("component_states", Map.of()).forGetter(AbilityHolderData::componentStates),
            Identifier.CODEC.optionalFieldOf("channelled_ability").forGetter(AbilityHolderData::channelledAbility)
    ).apply(instance, AbilityHolderData::decode));
    public static final Codec<AbilityHolderData> CODEC = MAP_CODEC.codec();
    private final Map<Identifier, List<Identifier>> sources;
    private final Map<Identifier, Long> cooldowns;
    private final Map<Identifier, Map<String, AbilityComponentState>> componentStates;
    private Optional<Identifier> channelledAbility;

    public AbilityHolderData() {
        this(Map.of(), Map.of(), Map.of(), Optional.empty());
    }

    private AbilityHolderData(Map<Identifier, List<Identifier>> sources, Map<Identifier, Long> cooldowns,
                              Map<Identifier, Map<String, AbilityComponentState>> componentStates, Optional<Identifier> channelledAbility) {
        this.sources = new LinkedHashMap<>();
        sources.forEach((ability, values) -> this.sources.put(ability, List.copyOf(values)));
        this.cooldowns = new LinkedHashMap<>(cooldowns);
        this.componentStates = new LinkedHashMap<>();
        componentStates.forEach((ability, values) -> this.componentStates.put(ability, Map.copyOf(values)));
        this.channelledAbility = channelledAbility;
    }

    private static AbilityHolderData decode(Map<Identifier, List<Identifier>> sources, Map<Identifier, Long> cooldowns,
                                            Map<Identifier, Map<String, AbilityComponentState>> componentStates, Optional<Identifier> channelledAbility) {
        return new AbilityHolderData(sources, cooldowns, componentStates, channelledAbility);
    }

    public Map<Identifier, List<Identifier>> sources() {
        return Map.copyOf(this.sources);
    }

    public Map<Identifier, Long> cooldowns() {
        return Map.copyOf(this.cooldowns);
    }

    public Map<Identifier, Map<String, AbilityComponentState>> componentStates() {
        return Map.copyOf(this.componentStates);
    }

    public Optional<Identifier> channelledAbility() {
        return this.channelledAbility;
    }

    public boolean has(Identifier ability) {
        return this.sources.containsKey(ability);
    }

    public void setSources(Identifier ability, List<Identifier> values) {
        if (values.isEmpty()) this.sources.remove(ability);
        else this.sources.put(ability, List.copyOf(values));
    }

    public boolean grant(Identifier ability, Identifier source) {
        List<Identifier> values = new ArrayList<>(this.sources.getOrDefault(ability, List.of()));
        if (values.contains(source)) return false;
        values.add(source);
        this.sources.put(ability, List.copyOf(values));
        return true;
    }

    public boolean revoke(Identifier ability, Identifier source) {
        List<Identifier> values = new ArrayList<>(this.sources.getOrDefault(ability, List.of()));
        if (!values.remove(source)) return false;
        this.setSources(ability, values);
        if (!this.has(ability)) {
            this.cooldowns.remove(ability);
            this.componentStates.remove(ability);
        }
        return true;
    }

    /**
     * Replaces exactly one grant source while preserving every other source for each ability.
     */
    public void reconcileSource(Identifier source, Collection<Identifier> desiredAbilities) {
        Set<Identifier> desired = Set.copyOf(desiredAbilities);
        Set<Identifier> previous = this.sources.entrySet().stream().filter(entry -> entry.getValue().contains(source))
                .map(Entry::getKey).collect(Collectors.toSet());
        previous.stream().filter(ability -> !desired.contains(ability)).toList().forEach(ability -> this.revoke(ability, source));
        desired.stream().filter(ability -> !previous.contains(ability)).forEach(ability -> this.grant(ability, source));
    }

    public boolean isOnCooldown(Identifier ability, long gameTime) {
        return this.cooldowns.getOrDefault(ability, -1L) > gameTime;
    }

    public void setCooldownUntil(Identifier ability, long gameTime) {
        this.cooldowns.put(ability, gameTime);
    }

    public Optional<AbilityComponentState> componentState(Identifier ability, String key) {
        return Optional.ofNullable(this.componentStates.getOrDefault(ability, Map.of()).get(key));
    }

    public void setComponentState(Identifier ability, String key, AbilityComponentState value) {
        Map<String, AbilityComponentState> values = new LinkedHashMap<>(this.componentStates.getOrDefault(ability, Map.of()));
        values.put(key, value);
        this.componentStates.put(ability, Map.copyOf(values));
    }

    public void setChannelledAbility(Identifier ability) {
        this.channelledAbility = Optional.ofNullable(ability);
    }

    public Snapshot snapshot() {
        return new Snapshot(this.sources, this.cooldowns, this.componentStates, this.channelledAbility);
    }

    public void restore(Snapshot snapshot) {
        this.sources.clear();
        snapshot.sources().forEach((id, values) -> this.sources.put(id, List.copyOf(values)));
        this.cooldowns.clear();
        this.cooldowns.putAll(snapshot.cooldowns());
        this.componentStates.clear();
        snapshot.componentStates().forEach((id, values) -> this.componentStates.put(id, Map.copyOf(values)));
        this.channelledAbility = snapshot.channelledAbility();
    }

    public record Snapshot(Map<Identifier, List<Identifier>> sources, Map<Identifier, Long> cooldowns,
                           Map<Identifier, Map<String, AbilityComponentState>> componentStates,
                           Optional<Identifier> channelledAbility) {
        public Snapshot {
            sources = Map.copyOf(sources);
            cooldowns = Map.copyOf(cooldowns);
            componentStates = Map.copyOf(componentStates);
        }
    }
}
