package com.iafenvoy.mxt.runtime;

import com.google.common.collect.ImmutableMap;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.action.NoOpAction;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.trigger.TriggerRule;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyHeatService;
import com.iafenvoy.mxt.runtime.damage.DamageElements;
import com.iafenvoy.mxt.runtime.element.ElementReactionService;
import com.iafenvoy.mxt.runtime.world.BlockAuraService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.chain.Chain;
import com.iafenvoy.mxt.util.chain.ChainCache;
import com.iafenvoy.mxt.util.formula.FormulaNames;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Derived datapack data rebuilt from tag updates, shared by both sides so a client and the integrated server of a
 * single player game read one index. The client-only cleanup is a nested class, so no client type is named here.
 */
@EventBusSubscriber
public final class DatapackCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DatapackCache EMPTY = new DatapackCache(ChainCache.empty(), ChainCache.empty(), ChainCache.empty(), new RealmLines(Map.of(), Map.of()), Map.of());
    @NotNull
    private static DatapackCache INSTANCE = EMPTY;
    // Chains
    private final ChainCache<RealmStage> realmChains;
    private final ChainCache<Progression> progressionChains;
    private final ChainCache<ItemQuality> qualityChains;
    // Mappings
    private final Map<Holder<Aura>, Chain<RealmStage>> realmLinesByAura;
    private final Map<Holder<RealmStage>, Holder<Aura>> auraByRealm;
    // Triggers
    private final Map<Identifier, List<Reference<TriggerRule>>> triggerRulesBySignal;

    private DatapackCache(ChainCache<RealmStage> realmChains, ChainCache<Progression> progressionChains, ChainCache<ItemQuality> qualityChains, RealmLines realmLines, Map<Identifier, List<Reference<TriggerRule>>> triggerRulesBySignal) {
        this.realmChains = realmChains;
        this.progressionChains = progressionChains;
        this.qualityChains = qualityChains;
        this.realmLinesByAura = realmLines.byAura();
        this.auraByRealm = realmLines.auraByRealm();
        this.triggerRulesBySignal = triggerRulesBySignal;
    }

    public static DatapackCache of(RegistryAccess access, boolean server) {
        // A link is only followed between entries this walk can read, and a realm link only inside one aura profile.
        ChainCache<RealmStage> realmChains = ChainCache.of(access.lookupOrThrow(MxtResourceKeys.REALM_STAGE));
        ChainCache<Progression> progressionChains = ChainCache.of(access.lookupOrThrow(MxtResourceKeys.PROGRESSION));
        ChainCache<ItemQuality> qualityChains = ChainCache.of(access.lookupOrThrow(MxtResourceKeys.ITEM_QUALITY));
        // A realm line is named by the aura profile that declares where it starts; the walk knows nothing about names.
        RealmLines realmLines = realmLines(access, realmChains);
        Map<Identifier, List<Reference<TriggerRule>>> triggerRules = Map.of();
        if (server) {
            realmChains.validate(RealmStage::aura, Objects::equals);
            qualityChains.validate(ItemQuality::quality, Objects::equals);
            validateTechniqueLevels(access, progressionChains);
            triggerRules = triggerRules(access);
            MiXianTu.LOGGER.info("Datapack validation passed: {} realm chains, {} progression chains, {} trigger rules", realmChains.size(), progressionChains.size(), triggerRules.values().stream().mapToInt(List::size).sum());
        }
        return new DatapackCache(realmChains, progressionChains, qualityChains, realmLines, triggerRules);
    }

    @NotNull
    public static DatapackCache get() {
        return INSTANCE;
    }

    public static <T, R> void validateRegistry(HolderLookup<T> registry, Function<T, R> mapper, BiPredicate<R, R> predicate) {
        validateRegistry(registry, (a, b) -> predicate.test(mapper.apply(a), mapper.apply(b)));
    }

    public static <T> void validateRegistry(HolderLookup<T> registry, BiPredicate<T, T> predicate) {
        List<Holder.Reference<T>> list = registry.listElements().toList();
        for (int i = 0; i < list.size(); i++)
            for (int j = i + 1; j < list.size(); j++)
                if (!predicate.test(list.get(i).value(), list.get(j).value()))
                    LOGGER.error("Validation failed between {} and {}", HolderHelper.id(list.get(i)), HolderHelper.id(list.get(j)));
    }

    // The aura profile names a realm line and declares where its own line starts, so this is the only place a realm
    // line gets a name. Two profiles claiming one resource, one start, or one line are reported.
    private static RealmLines realmLines(HolderLookup.Provider access, ChainCache<RealmStage> chains) {
        ImmutableMap.Builder<Holder<Aura>, Chain<RealmStage>> byAura = ImmutableMap.builder();
        ImmutableMap.Builder<Holder<RealmStage>, Holder<Aura>> auraByRealm = ImmutableMap.builder();
        validateRegistry(access.lookupOrThrow(MxtResourceKeys.AURA), Aura::resource, (a, b) -> !a.is(b));
        for (Holder<Aura> aura : access.lookupOrThrow(MxtResourceKeys.AURA).listElements().toList()) {
            Optional<Holder<RealmStage>> firstRealm = aura.value().firstRealm();
            if (firstRealm.isEmpty()) continue;
            Optional<Chain<RealmStage>> optional = chains.chainOf(firstRealm.get());
            if (optional.isEmpty()) continue;
            Chain<RealmStage> chain = optional.get();
            byAura.put(aura, chain);
            chain.nodes().forEach(x -> auraByRealm.put(x, aura));
        }
        return new RealmLines(byAura.build(), auraByRealm.build());
    }

    private record RealmLines(Map<Holder<Aura>, Chain<RealmStage>> byAura,
                              Map<Holder<RealmStage>, Holder<Aura>> auraByRealm) {
    }

    // A technique annotates one chain: a holder starts at its entry level and may be configured for every level
    // after it. A partially annotated chain is reported, so nobody reaches a level nothing describes.
    private static void validateTechniqueLevels(HolderLookup.Provider provider, ChainCache<Progression> chains) {
        // 遍历数据包内全部Technique（功法）定义
        provider.lookupOrThrow(MxtResourceKeys.TECHNIQUE).listElements().forEach(holder -> {
            Technique technique = holder.value();
            Holder<Progression> entry = technique.defaultLevel().orElse(null);
            if (entry == null) return;
            Identifier techniqueId = HolderHelper.id(holder);
            Chain<Progression> chain = chains.chainOf(entry).orElse(null);
            if (chain == null) return;
            Set<Holder<Progression>> configured = technique.configuration().keySet();
            // Check all progression configured
            for (int index = chain.indexOf(entry) + 1; index < chain.size(); index++) {
                Holder<Progression> level = chain.at(index).orElseThrow();
                if (configured.contains(level)) continue;
                LOGGER.error("{} does not configure the progression level {}", techniqueId, level);
                return;
            }
            // Check configuration and print warning
            for (Holder<Progression> configuredStage : configured)
                if (!chain.contains(configuredStage) || chain.indexOf(configuredStage) < chain.indexOf(entry))
                    LOGGER.warn("{} configures progression level {}, which it can never reach from {}", techniqueId, configuredStage, entry);
        });
    }

    /**
     * Indexed by signal, so publishing one never walks the whole trigger registry.
     */
    private static Map<Identifier, List<Reference<TriggerRule>>> triggerRules(HolderLookup.Provider access) {
        Map<Identifier, List<Reference<TriggerRule>>> rules = new LinkedHashMap<>();
        MxtDatapackRegistries.holders(access, MxtResourceKeys.TRIGGER).forEach(rule -> {
            TriggerRule value = rule.value();
            // A rule without an action reads like a reaction but can never do anything: the default action is
            // a no-op, so the author almost certainly forgot the field.
            if (value.action() instanceof NoOpAction)
                LOGGER.warn("{} has no action, so nothing happens when {} is published", rule.key().identifier(), value.trigger().signalType());
            rules.computeIfAbsent(value.trigger().signalType(), ignored -> new ArrayList<>()).add(rule);
        });
        return rules.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
    }

    /**
     * Every rule that reacts to one signal, in registry order.
     */
    public List<Reference<TriggerRule>> triggerRules(Identifier signal) {
        return this.triggerRulesBySignal.getOrDefault(signal, List.of());
    }

    /**
     * Every signal at least one rule reacts to, sorted, for command completion.
     */
    public List<Identifier> triggerSignals() {
        return this.triggerRulesBySignal.keySet().stream().sorted(Comparator.comparing(Identifier::toString)).toList();
    }

    /**
     * The aura profile owning a validated realm; the stored value is reached through it.
     */
    public Optional<Holder<Aura>> cultivationForRealm(Holder<RealmStage> realm) {
        return this.realmChains.chainOf(realm).map(Chain::first).map(this.auraByRealm::get);
    }

    /**
     * Zero-based, counted from the first realm of the validated chain.
     */
    public OptionalInt rankForRealm(Holder<RealmStage> realm) {
        return this.realmChains.rankOf(realm);
    }

    /**
     * One validated chain, low to high; empty for a profile the walk did not index.
     */
    public Chain<RealmStage> realmsOf(Holder<Aura> cultivation) {
        return Optional.ofNullable(this.realmLinesByAura.get(cultivation)).orElse(Chain.empty());
    }

    /**
     * The validated realm chains, for a caller that asks where two stages stand against each other.
     */
    public ChainCache<RealmStage> realmChains() {
        return this.realmChains;
    }

    /**
     * The realm one step above this one: empty at the top of its chain, and for a realm the walk did not index.
     * The holder is the chain's own, so no caller has to resolve an id back through a registry.
     */
    public Optional<Holder<RealmStage>> nextRealm(Holder<RealmStage> realm) {
        return this.realmChains.next(realm);
    }

    public OptionalInt rankForLevel(Holder<Progression> level) {
        return this.progressionChains.rankOf(level);
    }

    /**
     * The validated progression lines, for a caller that needs the whole line rather than one step: where a level
     * sits above an owner's entry level, and how long the climb is.
     */
    public ChainCache<Progression> progressionChains() {
        return this.progressionChains;
    }

    /**
     * The walked quality ladders, for the requirement layer and for a caller that needs the whole ladder.
     */
    public ChainCache<ItemQuality> qualityChains() {
        return this.qualityChains;
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (!event.shouldUpdateStaticData()) return;
        RegistryAccess access = event.getRegistries();
        INSTANCE = of(access, event instanceof TagsUpdatedEvent.ServerDataLoad);
        // The indexes below are keyed by registry instance, which a reloaded pack may keep, so they are dropped
        // here rather than left to notice the reload by themselves.
        // FIXME::Move all cache to here
        AlchemyHeatService.reload(access);
        BlockAuraService.reload(access);
        DamageElements.reload(access);
        ElementReactionService.reload(access);
        FormulaNames.reload(access);
    }

    @EventBusSubscriber(Dist.CLIENT)
    public static final class ClientDatapackCacheEvents {
        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            INSTANCE = EMPTY;
            AlchemyHeatService.clear();
            BlockAuraService.clear();
            DamageElements.clear();
            ElementReactionService.clear();
            FormulaNames.clear();
        }
    }
}
