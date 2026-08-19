package com.iafenvoy.mxt.attachment;

import com.google.common.collect.*;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.AbilityComponentState;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ability grants are tracked by source, so removing one source cannot remove another source's ability.
 */
public final class AbilityHolderData {
    public static final MapCodec<AbilityHolderData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.multiMap(Ability.CODEC, Identifier.CODEC).optionalFieldOf("sources", ImmutableMultimap.of()).forGetter(AbilityHolderData::sources),
            CollectionCodecs.longMap(Ability.CODEC).optionalFieldOf("cooldowns", Object2LongMaps.emptyMap()).forGetter(AbilityHolderData::cooldowns),
            CollectionCodecs.map(Ability.CODEC, CollectionCodecs.map(Codec.STRING, AbilityComponentState.CODEC)).optionalFieldOf("component_states", Map.of()).forGetter(AbilityHolderData::componentStates),
            Ability.CODEC.optionalFieldOf("channelled_ability").forGetter(AbilityHolderData::channelledAbility)
    ).apply(i, AbilityHolderData::new));
    private final Multimap<Holder<Ability>, Identifier> sources;
    private final Object2LongMap<Holder<Ability>> cooldowns;
    private final Map<Holder<Ability>, Map<String, AbilityComponentState>> componentStates;
    private Optional<Holder<Ability>> channelledAbility;

    public AbilityHolderData() {
        this(ArrayListMultimap.create(), Object2LongMaps.emptyMap(), Map.of(), Optional.empty());
    }

    private AbilityHolderData(Multimap<Holder<Ability>, Identifier> sources, Object2LongMap<Holder<Ability>> cooldowns, Map<Holder<Ability>, Map<String, AbilityComponentState>> componentStates, Optional<Holder<Ability>> channelledAbility) {
        this.sources = ArrayListMultimap.create(sources);
        this.cooldowns = new Object2LongOpenHashMap<>(cooldowns);
        this.componentStates = new LinkedHashMap<>();
        componentStates.forEach((ability, values) -> this.componentStates.put(ability, new LinkedHashMap<>(values)));
        this.channelledAbility = channelledAbility;
    }

    public Multimap<Holder<Ability>, Identifier> sources() {
        return this.sources;
    }

    public Object2LongMap<Holder<Ability>> cooldowns() {
        return this.cooldowns;
    }

    public Map<Holder<Ability>, Map<String, AbilityComponentState>> componentStates() {
        return this.componentStates;
    }

    public Optional<Holder<Ability>> channelledAbility() {
        return this.channelledAbility;
    }

    public boolean has(Holder<Ability> ability) {
        return this.sources.containsKey(ability);
    }

    public void setSources(Holder<Ability> ability, List<Identifier> values) {
        if (values.isEmpty()) this.sources.removeAll(ability);
        else {
            this.sources.removeAll(ability);
            this.sources.putAll(ability, values);
        }
    }

    public boolean grant(Holder<Ability> ability, Identifier source) {
        if (this.sources.containsEntry(ability, source)) return false;
        this.sources.put(ability, source);
        return true;
    }

    public boolean revoke(Holder<Ability> ability, Identifier source) {
        if (!this.sources.remove(ability, source)) return false;
        if (!this.sources.containsKey(ability)) {
            this.cooldowns.removeLong(ability);
            this.componentStates.remove(ability);
        }
        return true;
    }

    public void reconcileSource(Identifier source, Collection<Holder<Ability>> desiredAbilities) {
        Set<Holder<Ability>> desired = new LinkedHashSet<>(desiredAbilities);
        Set<Holder<Ability>> previous = this.sources.entries().stream().filter(entry -> entry.getValue().equals(source)).map(Entry::getKey).collect(Collectors.toSet());
        previous.stream().filter(ability -> !desired.contains(ability)).forEach(ability -> this.revoke(ability, source));
        desired.stream().filter(ability -> !previous.contains(ability)).forEach(ability -> this.grant(ability, source));
    }

    public boolean isOnCooldown(Holder<Ability> ability, long gameTime) {
        return this.cooldowns.getOrDefault(ability, -1L) > gameTime;
    }

    public void setCooldownUntil(Holder<Ability> ability, long gameTime) {
        this.cooldowns.put(ability, gameTime);
    }

    public Optional<AbilityComponentState> componentState(Holder<Ability> ability, String key) {
        return Optional.ofNullable(this.componentStates.getOrDefault(ability, Map.of()).get(key));
    }

    public void setComponentState(Holder<Ability> ability, String key, AbilityComponentState value) {
        Map<String, AbilityComponentState> values = new LinkedHashMap<>(this.componentStates.getOrDefault(ability, Map.of()));
        values.put(key, value);
        this.componentStates.put(ability, values);
    }

    public void setChannelledAbility(Holder<Ability> ability) {
        this.channelledAbility = Optional.ofNullable(ability);
    }

    public Snapshot snapshot() {
        return new Snapshot(this.sources, this.cooldowns, this.componentStates, this.channelledAbility);
    }

    public void restore(Snapshot snapshot) {
        this.sources.clear();
        this.sources.putAll(snapshot.sources());
        this.cooldowns.clear();
        this.cooldowns.putAll(snapshot.cooldowns());
        this.componentStates.clear();
        this.componentStates.putAll(snapshot.componentStates());
        this.channelledAbility = snapshot.channelledAbility();
    }

    public record Snapshot(Multimap<Holder<Ability>, Identifier> sources, Map<Holder<Ability>, Long> cooldowns,
                           Map<Holder<Ability>, Map<String, AbilityComponentState>> componentStates,
                           Optional<Holder<Ability>> channelledAbility) {
        public Snapshot {
            sources = ArrayListMultimap.create(sources);
            cooldowns = new LinkedHashMap<>(cooldowns);
            componentStates = new LinkedHashMap<>(componentStates);
        }
    }
}
