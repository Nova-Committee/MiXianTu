package com.iafenvoy.mxt.runtime;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.action.NoOpAction;
import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.condition.AlwaysCondition;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.QualityLadders;
import com.iafenvoy.mxt.data.trigger.TriggerRule;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.damage.DamageElements;
import com.iafenvoy.mxt.runtime.element.ElementReactionService;
import com.iafenvoy.mxt.util.ChainCache;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaNames;
import com.iafenvoy.mxt.util.formula.number.Constant;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent.ServerDataLoad;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.*;
import java.util.Map.Entry;

/**
 * Global server-lifetime cache for derived datapack data; absent on the client and outside an active
 * server lifecycle.
 */
@EventBusSubscriber
public final class ServerCache {
    private static ServerCache INSTANCE;

    private final MinecraftServer server;
    private ChainCache<RealmStage> realmChains = ChainCache.empty();
    private ChainCache<Progression> progressionChains = ChainCache.empty();
    private Map<Identifier, Identifier> headByLevel = new LinkedHashMap<>();
    private Map<Identifier, Integer> rankByLevel = new LinkedHashMap<>();
    private Map<Identifier, List<Reference<TriggerRule>>> triggerRulesBySignal = Map.of();
    private List<String> problems = List.of();

    private ServerCache(MinecraftServer server) {
        this.server = server;
    }

    public static Optional<ServerCache> get() {
        return Optional.ofNullable(INSTANCE);
    }

    public MinecraftServer server() {
        return this.server;
    }

    /**
     * Every problem the last rebuild found, each naming the file to fix; reported instead of aborting.
     */
    public List<String> problems() {
        return this.problems;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        INSTANCE = new ServerCache(event.getServer());
        INSTANCE.rebuild();
    }

    @SubscribeEvent
    public static void onDatapackLoaded(ServerDataLoad event) {
        // Rebuild only after a server datapack load or /reload, not for every player sync. The four indexes
        // below are keyed by registry instance, which a reloaded pack may keep, so they are dropped here rather
        // than left to notice the reload by themselves.
        DamageElements.invalidate();
        ElementReactionService.invalidate();
        FormulaNames.invalidate();
        ChainCache.invalidate();
        get().ifPresent(ServerCache::rebuild);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (INSTANCE != null && INSTANCE.server == event.getServer()) INSTANCE = null;
    }

    // Collects every problem and carries on: an invalid chain is never indexed, the definitions around it are,
    // so an author sees the whole list at once instead of one problem per restart.
    private void rebuild() {
        List<String> problems = new ArrayList<>();
        this.rebuildRealmChains(problems);
        this.rebuildTriggerRules(problems);
        this.rebuildProgressionChains(problems);
        this.validateQualityLadders(problems);
        this.rebuildArtifacts(problems);
        this.problems = List.copyOf(problems);
        if (problems.isEmpty()) {
            MiXianTu.LOGGER.info("Datapack validation passed: {} cultivation realms, {} progression levels, {} trigger rules",
                    this.realmChains.size(), this.headByLevel.size(),
                    this.triggerRulesBySignal.values().stream().mapToInt(List::size).sum());
        } else {
            MiXianTu.LOGGER.warn("Found {} datapack validation problem(s):\n{}", problems.size(), String.join("\n", problems));
        }
    }

    // The path shape the game uses for its own datapack reports, so the author can open the file directly.
    private static String problem(ResourceKey<? extends Registry<?>> registry, Identifier id, String message) {
        Identifier directory = registry.identifier();
        return "data/" + id.getNamespace() + "/" + directory.getNamespace() + "/" + directory.getPath()
                + "/" + id.getPath() + ": " + message;
    }

    /**
     * Indexed by signal, so publishing one never walks the whole trigger registry.
     */
    private void rebuildTriggerRules(List<String> problems) {
        Map<Identifier, List<Reference<TriggerRule>>> rules = new LinkedHashMap<>();
        MxtDatapackRegistries.holders(this.server.registryAccess(), MxtResourceKeys.TRIGGER).forEach(rule -> {
            TriggerRule value = rule.value();
            // A rule without an action reads like a reaction but can never do anything: the default action is
            // a no-op, so the author almost certainly forgot the field.
            if (value.action() instanceof NoOpAction)
                problems.add(problem(MxtResourceKeys.TRIGGER, rule.key().identifier(),
                        "has no action, so nothing happens when " + value.trigger().signalType() + " is published"));
            rules.computeIfAbsent(value.trigger().signalType(), ignored -> new ArrayList<>()).add(rule);
        });
        Map<Identifier, List<Reference<TriggerRule>>> indexed = new LinkedHashMap<>();
        rules.forEach((signal, entries) -> indexed.put(signal, List.copyOf(entries)));
        this.triggerRulesBySignal = Collections.unmodifiableMap(indexed);
    }

    /**
     * The one artifact check a definition cannot make about itself, because it needs the other registry entries:
     * two definitions claiming the same item on the same priority, which registry order then decides.
     */
    private void rebuildArtifacts(List<String> problems) {
        Map<Item, Reference<Artifact>> claimed = new LinkedHashMap<>();
        for (Reference<Artifact> holder : MxtDatapackRegistries.holders(this.server.registryAccess(), MxtResourceKeys.ARTIFACT).toList()) {
            Artifact definition = holder.value();
            Identifier previous = this.claimItems(holder, definition, claimed);
            if (previous != null)
                problems.add(problem(MxtResourceKeys.ARTIFACT, holder.key().identifier(),
                        "claims an item that " + previous + " already claims as its artifact on the same priority"));
        }
    }

    // Reserves every item the definition matches, keeping the highest priority claim. Returns the definition
    // holding the first item that could not be taken on equal terms, or null when it took everything it asked for.
    private Identifier claimItems(Reference<Artifact> holder, Artifact definition, Map<Item, Reference<Artifact>> claimed) {
        for (Reference<Item> item : BuiltInRegistries.ITEM.listElements().toList()) {
            ItemStack stack = new ItemStack(item.value());
            if (definition.entries().stream().noneMatch(entry -> entry.matches(stack))) continue;
            Reference<Artifact> owner = claimed.get(item.value());
            if (owner == null) {
                claimed.put(item.value(), holder);
                continue;
            }
            int priority = definition.priority();
            int claimedPriority = owner.value().priority();
            if (priority == claimedPriority) return owner.key().identifier();
            if (priority > claimedPriority) claimed.put(item.value(), holder);
        }
        return null;
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
    public Optional<Identifier> cultivationForRealm(Identifier realm) {
        return this.realmChains.keyOf(realm);
    }

    public boolean containsRealm(Identifier realm) {
        return this.realmChains.contains(realm);
    }

    /**
     * Zero-based, counted from the first realm of the validated chain.
     */
    public Optional<Integer> rankForRealm(Identifier realm) {
        return this.realmChains.rankOf(realm);
    }

    /**
     * One validated chain, low to high; empty for a profile the walk did not index.
     */
    public List<Identifier> realmsOf(Identifier cultivation) {
        return this.realmChains.chain(cultivation).map(ChainCache.Chain::ids).orElse(List.of());
    }

    /**
     * The realm one step above this one: empty at the top of its chain, and for a realm the walk did not index.
     */
    public Optional<Identifier> nextRealm(Identifier realm) {
        return this.realmChains.next(realm).map(HolderHelper::id);
    }

    /**
     * The realm one step below this one: empty at the first realm of its chain, and for one the walk did not index.
     */
    public Optional<Identifier> previousRealm(Identifier realm) {
        return this.realmChains.previous(realm).map(HolderHelper::id);
    }

    public boolean isRealmAtLeast(Identifier current, Identifier required) {
        Optional<Identifier> cultivation = this.cultivationForRealm(current);
        return cultivation.isPresent() && cultivation.equals(this.cultivationForRealm(required))
                && this.rankForRealm(current).orElse(-1) >= this.rankForRealm(required).orElse(Integer.MAX_VALUE);
    }

    public boolean containsLevel(Identifier level) {
        return this.headByLevel.containsKey(level);
    }

    /**
     * The validated progression lines, for a caller that needs the whole line rather than one step: where a level
     * sits above an owner's entry level, and how long the climb is.
     */
    public ChainCache<Progression> progressionChains() {
        return this.progressionChains;
    }

    public Optional<Integer> rankForLevel(Identifier level) {
        return Optional.ofNullable(this.rankByLevel.get(level));
    }

    /**
     * The comparison every level-gated rule uses.
     */
    public boolean isLevelAtLeast(Identifier current, Identifier required) {
        Identifier chain = this.headByLevel.get(current);
        return chain != null && chain.equals(this.headByLevel.get(required))
                && this.rankByLevel.getOrDefault(current, -1) >= this.rankByLevel.getOrDefault(required, Integer.MAX_VALUE);
    }

    // The one thing a quality tier cannot check about itself, because it needs the whole registry: a next tier
    // that does not exist, a ladder nothing can walk, or upgrade data on a tier that leads nowhere. The walk and
    // the index behind it live in QualityLadders, over the shared ChainCache, so a report can never describe a
    // different registry than the one the runtime reads.
    private void validateQualityLadders(List<String> problems) {
        Registry<ItemQuality> registry = MxtDatapackRegistries.registry(MxtResourceKeys.ITEM_QUALITY);
        registry.listElements().forEach(holder -> {
            ItemQuality tier = holder.value();
            // Upgrade data on a tier that leads nowhere would be read by nobody, which is the forgotten-field
            // mistake this check exists for - the codec itself cannot see the other tiers.
            if (tier.next().isEmpty() && (!tier.upgradeCosts().isEmpty() || !(tier.upgradeCondition() instanceof AlwaysCondition)))
                problems.add(problem(MxtResourceKeys.ITEM_QUALITY, holder.key().identifier(),
                        "declares upgrade_costs or upgrade_condition but no next tier"));
            tier.next().map(HolderHelper::id)
                    .filter(next -> MxtDatapackRegistries.get(MxtResourceKeys.ITEM_QUALITY, next).isEmpty())
                    .ifPresent(next -> problems.add(problem(MxtResourceKeys.ITEM_QUALITY, holder.key().identifier(),
                            "next " + next + " is not a quality")));
        });
        for (ChainCache.Report report : QualityLadders.diagnose(registry).reports())
            problems.add(problem(MxtResourceKeys.ITEM_QUALITY, report.node(), report.message()));
    }

    // A realm chain is one line over the stages' next_realm links, and the aura profile declares where its own line
    // starts. A link that leaves the profile, and a profile starting at a stage that is not its own, refuse that
    // line instead of ordering part of it.
    private void rebuildRealmChains(List<String> problems) {
        Map<Identifier, Holder<RealmStage>> stages = new LinkedHashMap<>();
        MxtDatapackRegistries.holders(this.server.registryAccess(), MxtResourceKeys.REALM_STAGE)
                .forEach(stage -> stages.put(stage.key().identifier(), stage));
        ChainCache.Builder<RealmStage> chains = ChainCache.builder(stages);
        for (Map.Entry<Identifier, Holder<RealmStage>> entry : stages.entrySet()) {
            RealmStage stage = entry.getValue().value();
            Identifier aura = HolderHelper.id(stage.aura());
            Identifier next = stage.nextRealm().map(HolderHelper::id).orElse(null);
            if (next == null) continue;
            Holder<RealmStage> target = stages.get(next);
            if (target == null) {
                chains.refuse(entry.getKey(), "next_realm " + next + " is not a realm stage");
                continue;
            }
            Identifier nextAura = HolderHelper.id(target.value().aura());
            if (!nextAura.equals(aura)) {
                chains.refuse(entry.getKey(), "next_realm " + next + " belongs to " + nextAura + " instead of " + aura);
                continue;
            }
            chains.link(entry.getKey(), next);
        }
        // The profile is what a chain is known by, and it declares where its own line starts.
        Map<Identifier, Identifier> claimed = new LinkedHashMap<>();
        Map<Identifier, Identifier> profiles = new LinkedHashMap<>();
        MxtDatapackRegistries.holders(this.server.registryAccess(), MxtResourceKeys.AURA).forEach(profileHolder -> {
            Aura profile = profileHolder.value();
            Identifier cultivation = profileHolder.key().identifier();
            Identifier resource = HolderHelper.id(profile.resource());
            Identifier previous = profiles.putIfAbsent(resource, cultivation);
            if (previous != null) {
                problems.add(problem(MxtResourceKeys.AURA, cultivation,
                        "resource " + resource + " already has the cultivation profile " + previous));
                return;
            }
            // The chain belongs to the profile: its first realm has to be a stage naming it, and only one profile
            // can start there.
            profile.firstRealm().ifPresent(first -> {
                Identifier head = HolderHelper.id(first);
                Identifier owner = claimed.putIfAbsent(head, cultivation);
                if (owner != null) {
                    problems.add(problem(MxtResourceKeys.AURA, cultivation,
                            "starts at the realm " + head + ", where " + owner + " also starts"));
                    return;
                }
                Holder<RealmStage> stage = stages.get(head);
                if (stage == null)
                    chains.refuse(head, "the first realm " + head + " is not a realm stage");
                else if (!HolderHelper.id(stage.value().aura()).equals(cultivation))
                    chains.refuse(head, "is the first realm of " + cultivation + " but belongs to "
                            + HolderHelper.id(stage.value().aura()));
                chains.head(head, cultivation);
            });
        });
        this.realmChains = chains.build();
        for (ChainCache.Report report : this.realmChains.reports())
            problems.add(problem(MxtResourceKeys.REALM_STAGE, report.node(), report.message()));
        this.reportUnreachedRealms(stages, problems);
    }

    // Every stage of a chain that was walked has to be on it: one left out is a second line beside the first, and
    // nothing would ever reach it. A chain the walk refused whole is reported once, at the stage that refused it.
    private void reportUnreachedRealms(Map<Identifier, Holder<RealmStage>> stages, List<String> problems) {
        for (Map.Entry<Identifier, Holder<RealmStage>> entry : stages.entrySet()) {
            if (this.realmChains.contains(entry.getKey())) continue;
            Identifier cultivation = HolderHelper.id(entry.getValue().value().aura());
            Optional<Identifier> first = this.realmChains.first(cultivation).map(HolderHelper::id);
            if (first.isEmpty()) continue;
            problems.add(problem(MxtResourceKeys.REALM_STAGE, entry.getKey(),
                    "cannot be reached from " + first.orElseThrow() + ", the first realm of " + cultivation));
        }
    }

    // A chain is its next_level links, not the level a definition enters at: several owners may walk one chain
    // and enter it at different levels. A chain that cannot be walked is reported and left out, never indexed
    // partially.
    // A chain is its next_level links, not the level a definition enters at: several owners may walk one chain
    // and enter it at different levels. The walk, the fork rule and the whole-line refusal are ChainCache's; what is
    // added here is only what a progression chain means - a level may not ask for less mastery than the one below.
    private void rebuildProgressionChains(List<String> problems) {
        Map<Identifier, Holder<Progression>> stages = new LinkedHashMap<>();
        MxtDatapackRegistries.holders(this.server.registryAccess(), MxtResourceKeys.PROGRESSION)
                .forEach(holder -> stages.put(holder.key().identifier(), holder));
        ChainCache.Builder<Progression> chains = ChainCache.builder(stages);
        Set<Identifier> pointedAt = new LinkedHashSet<>();
        for (Entry<Identifier, Holder<Progression>> entry : stages.entrySet()) {
            Identifier next = entry.getValue().value().nextLevel().map(HolderHelper::id).orElse(null);
            if (next == null) continue;
            if (!stages.containsKey(next)) {
                chains.refuse(entry.getKey(), "next_level " + next + " is not a progression");
                continue;
            }
            pointedAt.add(next);
            chains.link(entry.getKey(), next);
        }
        // A level nothing points at starts a line; the levels above it are its own.
        for (Identifier id : stages.keySet())
            if (!pointedAt.contains(id)) chains.head(id, null);
        ChainCache<Progression> built = chains.check(ServerCache::masteryRefusal).build();
        for (ChainCache.Report report : built.reports())
            problems.add(problem(MxtResourceKeys.PROGRESSION, report.node(), report.message()));
        Map<Identifier, Identifier> heads = new LinkedHashMap<>();
        Map<Identifier, Integer> ranks = new LinkedHashMap<>();
        for (Identifier id : stages.keySet()) {
            ChainCache.Chain<Progression> chain = built.chainOf(id).orElse(null);
            if (chain == null) continue;
            heads.put(id, HolderHelper.id(chain.first()));
            ranks.put(id, chain.indexOf(id));
        }
        // A level reported on its own line is not named a second time: the break that cut it is the one message
        // that matters for everything the line carried.
        Map<Identifier, Identifier> below = new LinkedHashMap<>();
        stages.forEach((id, holder) -> holder.value().nextLevel().map(HolderHelper::id)
                .ifPresent(next -> below.putIfAbsent(next, id)));
        Set<Identifier> reported = new LinkedHashSet<>();
        built.reports().forEach(report -> reported.add(report.node()));
        for (Identifier id : stages.keySet())
            if (!ranks.containsKey(id) && !reported.contains(id))
                problems.add(problem(MxtResourceKeys.PROGRESSION, id,
                        "cannot be reached from a first level: the chain is cyclic, split, or refused"));
        this.validateTechniqueLevels(built, stages, problems);
        this.progressionChains = built;
        this.headByLevel = heads;
        this.rankByLevel = ranks;
    }

    // A later level may not ask for less mastery than an earlier one. Only a constant can be compared: a formula
    // provider that drops only makes advancement climb faster.
    private static String masteryRefusal(List<Holder<Progression>> line) {
        double lastMastery = Double.NEGATIVE_INFINITY;
        for (Holder<Progression> level : line) {
            if (!(level.value().mastery() instanceof Constant(double mastery))) continue;
            if (mastery < lastMastery) return "lowers its mastery requirement at level " + HolderHelper.id(level);
            lastMastery = mastery;
        }
        return null;
    }

    // A technique annotates one chain: a holder starts at its entry level and may be configured for every level
    // after it. A partially annotated chain is reported, so nobody reaches a level nothing describes.
    private void validateTechniqueLevels(ChainCache<Progression> chains, Map<Identifier, Holder<Progression>> stages,
                                         List<String> problems) {
        MxtDatapackRegistries.holders(this.server.registryAccess(), MxtResourceKeys.TECHNIQUE).forEach(holder -> {
            Technique technique = holder.value();
            Identifier entry = technique.defaultLevel().map(HolderHelper::id).orElse(null);
            if (entry == null) return;
            Identifier techniqueId = holder.key().identifier();
            ChainCache.Chain<Progression> chain = chains.chainOf(entry).orElse(null);
            if (chain == null) {
                // A stage that exists but was not indexed belongs to a chain that was already reported.
                if (!stages.containsKey(entry))
                    problems.add(problem(MxtResourceKeys.TECHNIQUE, techniqueId, "enters the unknown progression level " + entry));
                return;
            }
            Set<Identifier> configured = new LinkedHashSet<>();
            technique.configuration().keySet().forEach(stage -> configured.add(HolderHelper.id(stage)));
            for (Identifier configuredStage : configured)
                if (!chain.contains(configuredStage) || chain.indexOf(configuredStage) < chain.indexOf(entry))
                    problems.add(problem(MxtResourceKeys.TECHNIQUE, techniqueId,
                            "configures progression level " + configuredStage + ", which it can never reach from " + entry));
            // Every level from the entry up has to be configured: one nobody describes is a level nobody reaches.
            for (int index = chain.indexOf(entry) + 1; index < chain.size(); index++) {
                Identifier level = HolderHelper.id(chain.at(index).orElseThrow());
                if (configured.contains(level)) continue;
                problems.add(problem(MxtResourceKeys.TECHNIQUE, techniqueId, "does not configure the progression level " + level));
                return;
            }
        });
    }
}
