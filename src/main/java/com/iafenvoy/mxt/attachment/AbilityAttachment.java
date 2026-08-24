package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.util.ShouldSyncAttachment;
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
public final class AbilityAttachment extends ShouldSyncAttachment {
    public static final MapCodec<AbilityAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.multiMap(Ability.CODEC, Identifier.CODEC).optionalFieldOf("sources", ImmutableMultimap.of()).forGetter(AbilityAttachment::sources),
            CollectionCodecs.longMap(Ability.CODEC).optionalFieldOf("cooldowns", Object2LongMaps.emptyMap()).forGetter(AbilityAttachment::cooldowns),
            CollectionCodecs.map(Ability.CODEC, CollectionCodecs.map(Codec.STRING, AbilityComponentState.CODEC)).optionalFieldOf("component_states", Map.of()).forGetter(AbilityAttachment::componentStates),
            Ability.CODEC.optionalFieldOf("channelled_ability").forGetter(AbilityAttachment::channelledAbility)
    ).apply(i, AbilityAttachment::new));
    private final Multimap<Holder<Ability>, Identifier> sources;
    private final Object2LongMap<Holder<Ability>> cooldowns;
    private final Map<Holder<Ability>, Map<String, AbilityComponentState>> componentStates;
    private Optional<Holder<Ability>> channelledAbility;

    public AbilityAttachment() {
        this(ArrayListMultimap.create(), Object2LongMaps.emptyMap(), Map.of(), Optional.empty());
    }

    private AbilityAttachment(Multimap<Holder<Ability>, Identifier> sources, Object2LongMap<Holder<Ability>> cooldowns, Map<Holder<Ability>, Map<String, AbilityComponentState>> componentStates, Optional<Holder<Ability>> channelledAbility) {
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
        this.markDirty();
    }

    public boolean grant(Holder<Ability> ability, Identifier source) {
        if (this.sources.containsEntry(ability, source)) return false;
        this.sources.put(ability, source);
        this.markDirty();
        return true;
    }

    public boolean revoke(Holder<Ability> ability, Identifier source) {
        if (!this.sources.remove(ability, source)) return false;
        if (!this.sources.containsKey(ability)) {
            this.cooldowns.removeLong(ability);
            this.componentStates.remove(ability);
        }
        this.markDirty();
        return true;
    }

    public boolean reconcileSource(Identifier source, Collection<Holder<Ability>> desiredAbilities) {
        Set<Holder<Ability>> desired = new LinkedHashSet<>(desiredAbilities);
        Set<Holder<Ability>> previous = this.sources.entries().stream().filter(entry -> entry.getValue().equals(source)).map(Entry::getKey).collect(Collectors.toSet());
        boolean changed = false;
        for (Holder<Ability> ability : previous) {
            if (!desired.contains(ability)) changed |= this.revoke(ability, source);
        }
        for (Holder<Ability> ability : desired) {
            if (!previous.contains(ability)) changed |= this.grant(ability, source);
        }
        return changed;
    }

    public boolean isOnCooldown(Holder<Ability> ability, long gameTime) {
        return this.cooldowns.getOrDefault(ability, -1L) > gameTime;
    }

    public void setCooldownUntil(Holder<Ability> ability, long gameTime) {
        this.cooldowns.put(ability, gameTime);
        this.markDirty();
    }

    public Optional<AbilityComponentState> componentState(Holder<Ability> ability, String key) {
        return Optional.ofNullable(this.componentStates.getOrDefault(ability, Map.of()).get(key));
    }

    public void setComponentState(Holder<Ability> ability, String key, AbilityComponentState value) {
        Map<String, AbilityComponentState> values = new LinkedHashMap<>(this.componentStates.getOrDefault(ability, Map.of()));
        values.put(key, value);
        this.componentStates.put(ability, values);
        this.markDirty();
    }

    public void setChannelledAbility(Holder<Ability> ability) {
        this.channelledAbility = Optional.ofNullable(ability);
        this.markDirty();
    }

    /**
     * Creates a detached draft for validation. It is never installed on an entity or synchronised.
     */
    public AbilityAttachment copy() {
        return new AbilityAttachment(this.sources, this.cooldowns, this.componentStates, this.channelledAbility);
    }
}
