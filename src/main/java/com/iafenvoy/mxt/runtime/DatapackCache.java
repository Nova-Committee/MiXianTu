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
import com.iafenvoy.mxt.runtime.alchemy.AlchemyHeatService;
import com.iafenvoy.mxt.runtime.damage.DamageElements;
import com.iafenvoy.mxt.runtime.element.ElementReactionService;
import com.iafenvoy.mxt.util.ChainCache;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaNames;
import com.iafenvoy.mxt.util.formula.number.Constant;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Derived datapack data rebuilt from tag updates, shared by both sides so a client and the integrated server of a
 * single player game read one index. The client-only cleanup is a nested class, so no client type is named here.
 */
@EventBusSubscriber
public class DatapackCache {
    private static DatapackCache INSTANCE;

    private final ChainCache<RealmStage> realmChains;
    private final ChainCache<Progression> progressionChains;
    private final ChainCache<ItemQuality> qualityChains;
    private final Map<Identifier, List<Reference<TriggerRule>>> triggerRulesBySignal;
    private final List<String> problems;

    private DatapackCache(ChainCache<RealmStage> realmChains, ChainCache<Progression> progressionChains,
                          ChainCache<ItemQuality> qualityChains,
                          Map<Identifier, List<Reference<TriggerRule>>> triggerRulesBySignal, List<String> problems) {
        this.realmChains = realmChains;
        this.progressionChains = progressionChains;
        this.qualityChains = qualityChains;
        this.triggerRulesBySignal = triggerRulesBySignal;
        this.problems = problems;
    }

    public static Optional<DatapackCache> get() {
        return Optional.ofNullable(INSTANCE);
    }

    /**
     * Every problem the last rebuild found, each naming the file to fix; reported instead of aborting.
     */
    public List<String> problems() {
        return this.problems;
    }

    // A client builds only the chains it reads - realms, progression levels and quality ladders: trigger rules feed
    // server-side dispatch and the validation is read by server commands, so neither runs where there is no server.
    static void rebuild(HolderLookup.Provider access, boolean full) {
        List<String> problems = new ArrayList<>();
        ChainCache<RealmStage> realmChains = rebuildRealmChains(access, problems);
        ChainCache<Progression> progressionChains = rebuildProgressionChains(access, problems);
        ChainCache<ItemQuality> qualityChains = QualityLadders.diagnose(access);
        Map<Identifier, List<Reference<TriggerRule>>> triggerRules = Map.of();
        if (full) {
            triggerRules = rebuildTriggerRules(access, problems);
            validateQualityLadders(access, qualityChains, problems);
            rebuildArtifacts(access, problems);
            if (problems.isEmpty()) {
                MiXianTu.LOGGER.info("Datapack validation passed: {} cultivation realms, {} progression levels, {} trigger rules",
                        realmChains.size(), progressionChains.size(),
                        triggerRules.values().stream().mapToInt(List::size).sum());
            } else {
                MiXianTu.LOGGER.warn("Found {} datapack validation problem(s):\n{}", problems.size(), String.join("\n", problems));
            }
        }
        INSTANCE = new DatapackCache(realmChains, progressionChains, qualityChains, triggerRules,
                full ? List.copyOf(problems) : List.of());
    }

    // What a chain found, told as the problem lines an author reads: one per report, under the registry its file is in.
    private static void add(List<String> problems, ResourceKey<? extends Registry<?>> registry,
                            List<ChainCache.Report> reports) {
        for (ChainCache.Report report : reports)
            problems.add(problem(registry, report.node(), report.message()));
    }

    // Dropped when a session ends; every reader falls back to the definitions when nothing is indexed.
    static void clear() {
        INSTANCE = null;
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
    private static Map<Identifier, List<Reference<TriggerRule>>> rebuildTriggerRules(HolderLookup.Provider access,
                                                                                    List<String> problems) {
        Map<Identifier, List<Reference<TriggerRule>>> rules = new LinkedHashMap<>();
        MxtDatapackRegistries.holders(access, MxtResourceKeys.TRIGGER).forEach(rule -> {
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
        return Collections.unmodifiableMap(indexed);
    }

    /**
     * The one artifact check a definition cannot make about itself, because it needs the other registry entries:
     * two definitions claiming the same item on the same priority, which registry order then decides.
     */
    private static void rebuildArtifacts(HolderLookup.Provider access, List<String> problems) {
        Map<Item, Reference<Artifact>> claimed = new LinkedHashMap<>();
        for (Reference<Artifact> holder : MxtDatapackRegistries.holders(access, MxtResourceKeys.ARTIFACT).toList()) {
            Artifact definition = holder.value();
            Identifier previous = claimItems(holder, definition, claimed);
            if (previous != null)
                problems.add(problem(MxtResourceKeys.ARTIFACT, holder.key().identifier(),
                        "claims an item that " + previous + " already claims as its artifact on the same priority"));
        }
    }

    // Reserves every item the definition matches, keeping the highest priority claim. Returns the definition
    // holding the first item that could not be taken on equal terms, or null when it took everything it asked for.
    private static Identifier claimItems(Reference<Artifact> holder, Artifact definition, Map<Item, Reference<Artifact>> claimed) {
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
        return this.progressionChains.contains(level);
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

    public Optional<Integer> rankForLevel(Identifier level) {
        return this.progressionChains.rankOf(level);
    }

    /**
     * The comparison every level-gated rule uses.
     */
    public boolean isLevelAtLeast(Identifier current, Identifier required) {
        return this.progressionChains.chainOf(current)
                .filter(chain -> chain.contains(required))
                .map(chain -> chain.indexOf(current) >= chain.indexOf(required))
                .orElse(false);
    }

    // What a quality tier cannot check about itself, because it needs the whole registry: upgrade data on a tier that
    // leads nowhere. The ladder's own reports come off the index this cache keeps, so report and runtime read one walk.
    private static void validateQualityLadders(HolderLookup.Provider access, ChainCache<ItemQuality> ladders,
                                               List<String> problems) {
        HolderLookup.RegistryLookup<ItemQuality> registry = access.lookupOrThrow(MxtResourceKeys.ITEM_QUALITY);
        registry.listElements().forEach(holder -> {
            ItemQuality tier = holder.value();
            // Upgrade data on a tier that leads nowhere would be read by nobody, which is the forgotten-field
            // mistake this check exists for - the codec itself cannot see the other tiers.
            if (tier.next().isEmpty() && (!tier.upgradeCosts().isEmpty() || !(tier.upgradeCondition() instanceof AlwaysCondition)))
                problems.add(problem(MxtResourceKeys.ITEM_QUALITY, holder.key().identifier(),
                        "declares upgrade_costs or upgrade_condition but no next tier"));
        });
        add(problems, MxtResourceKeys.ITEM_QUALITY, ladders.reports());
    }

    // A realm chain is one line over the stages' next_realm links, and the aura profile declares where its own line
    // starts. Everything else - the walk, the fork rule, the whole-line refusal - is ChainCache's.
    private static ChainCache<RealmStage> rebuildRealmChains(HolderLookup.Provider access, List<String> problems) {
        RealmWalk walk = new RealmWalk(access);
        ChainCache<RealmStage> built = ChainCache.walk(walk);
        add(problems, MxtResourceKeys.AURA, walk.auraProblems());
        add(problems, MxtResourceKeys.REALM_STAGE, built.reports());
        return built;
    }

    private static final class RealmWalk implements ChainCache.Rules<RealmStage> {
        private final Map<Identifier, Holder<RealmStage>> stages = new LinkedHashMap<>();
        private final List<Reference<Aura>> auras;
        private final List<ChainCache.Report> auraProblems = new ArrayList<>();

        private RealmWalk(HolderLookup.Provider access) {
            MxtDatapackRegistries.holders(access, MxtResourceKeys.REALM_STAGE)
                    .forEach(stage -> this.stages.put(stage.key().identifier(), stage));
            this.auras = MxtDatapackRegistries.holders(access, MxtResourceKeys.AURA).toList();
        }

        @Override
        public Map<Identifier, Holder<RealmStage>> nodes() {
            return this.stages;
        }

        // A next_realm the pack does not provide is not reported here: the reference stays unbound and the registry
        // refuses to freeze, so the server does not start and this walk never runs on it.
        @Override
        public void link(Identifier node, Holder<RealmStage> value, Map<Identifier, Holder<RealmStage>> nodes,
                         ChainCache.Builder<RealmStage> lines) {
            Identifier next = value.value().nextRealm().map(HolderHelper::id).orElse(null);
            if (next == null) return;
            Identifier aura = HolderHelper.id(value.value().aura());
            Identifier nextAura = HolderHelper.id(nodes.get(next).value().aura());
            if (nextAura.equals(aura)) lines.link(node, next);
            else lines.refuse(node, "next_realm " + next + " belongs to " + nextAura + " instead of " + aura);
        }

        // The profile is what a chain is known by, and it declares where its own line starts. What a profile says
        // about itself is reported under the aura registry, since that is the file an author opens.
        @Override
        public void heads(Map<Identifier, Holder<RealmStage>> nodes, Set<Identifier> pointedAt,
                          ChainCache.Builder<RealmStage> lines) {
            Map<Identifier, Identifier> claimed = new LinkedHashMap<>();
            Map<Identifier, Identifier> byResource = new LinkedHashMap<>();
            for (Reference<Aura> holder : this.auras) {
                Aura profile = holder.value();
                Identifier cultivation = holder.key().identifier();
                Identifier resource = HolderHelper.id(profile.resource());
                Identifier previous = byResource.putIfAbsent(resource, cultivation);
                if (previous != null) {
                    this.auraProblems.add(new ChainCache.Report(cultivation,
                            "resource " + resource + " already has the cultivation profile " + previous));
                    continue;
                }
                // The chain belongs to the profile: its first realm has to be a stage naming it, and only one
                // profile can start there.
                profile.firstRealm().ifPresent(first -> {
                    Identifier head = HolderHelper.id(first);
                    Identifier owner = claimed.putIfAbsent(head, cultivation);
                    if (owner != null) {
                        this.auraProblems.add(new ChainCache.Report(cultivation,
                                "starts at the realm " + head + ", where " + owner + " also starts"));
                        return;
                    }
                    Holder<RealmStage> stage = nodes.get(head);
                    if (stage == null)
                        lines.refuse(head, "the first realm " + head + " is not a realm stage");
                    else if (!HolderHelper.id(stage.value().aura()).equals(cultivation))
                        lines.refuse(head, "is the first realm of " + cultivation + " but belongs to "
                                + HolderHelper.id(stage.value().aura()));
                    lines.head(head, cultivation);
                });
            }
        }

        // Every stage of a chain that was walked has to be on it: one left out is a second line beside the first, and
        // nothing would ever reach it. A chain the walk refused whole is reported once, at the stage that refused it.
        @Override
        public void after(ChainCache<RealmStage> lines, Map<Identifier, Holder<RealmStage>> nodes,
                          List<ChainCache.Report> reports) {
            for (Map.Entry<Identifier, Holder<RealmStage>> entry : nodes.entrySet()) {
                if (lines.contains(entry.getKey())) continue;
                Identifier cultivation = HolderHelper.id(entry.getValue().value().aura());
                Optional<Identifier> first = lines.first(cultivation).map(HolderHelper::id);
                if (first.isEmpty()) continue;
                reports.add(new ChainCache.Report(entry.getKey(),
                        "cannot be reached from " + first.orElseThrow() + ", the first realm of " + cultivation));
            }
        }

        private List<ChainCache.Report> auraProblems() {
            return this.auraProblems;
        }
    }

    // A chain is its next_level links, not the level a definition enters at: several owners may walk one chain
    // and enter it at different levels.
    private static ChainCache<Progression> rebuildProgressionChains(HolderLookup.Provider access, List<String> problems) {
        ProgressionWalk walk = new ProgressionWalk(access);
        ChainCache<Progression> built = ChainCache.walk(walk);
        add(problems, MxtResourceKeys.PROGRESSION, built.reports());
        validateTechniqueLevels(access, built, walk.nodes(), problems);
        return built;
    }

    private static final class ProgressionWalk implements ChainCache.Rules<Progression> {
        private final Map<Identifier, Holder<Progression>> stages = new LinkedHashMap<>();

        private ProgressionWalk(HolderLookup.Provider access) {
            MxtDatapackRegistries.holders(access, MxtResourceKeys.PROGRESSION)
                    .forEach(holder -> this.stages.put(holder.key().identifier(), holder));
        }

        @Override
        public Map<Identifier, Holder<Progression>> nodes() {
            return this.stages;
        }

        @Override
        public void link(Identifier node, Holder<Progression> value, Map<Identifier, Holder<Progression>> nodes,
                         ChainCache.Builder<Progression> lines) {
            Identifier next = value.value().nextLevel().map(HolderHelper::id).orElse(null);
            if (next == null) return;
            if (nodes.containsKey(next)) lines.link(node, next);
            else lines.refuse(node, "next_level " + next + " is not a progression");
        }

        // A later level may not ask for less mastery than an earlier one. Only a constant can be compared: a formula
        // provider that drops only makes advancement climb faster.
        @Override
        public @Nullable String refuseLine(List<Holder<Progression>> line) {
            double lastMastery = Double.NEGATIVE_INFINITY;
            for (Holder<Progression> level : line) {
                if (!(level.value().mastery() instanceof Constant(double mastery))) continue;
                if (mastery < lastMastery) return "lowers its mastery requirement at level " + HolderHelper.id(level);
                lastMastery = mastery;
            }
            return null;
        }

        // A level nothing points at starts a line, and a level on no line is reported once - not again when the walk
        // already named it.
        @Override
        public void after(ChainCache<Progression> lines, Map<Identifier, Holder<Progression>> nodes,
                          List<ChainCache.Report> reports) {
            Set<Identifier> reported = new LinkedHashSet<>();
            lines.reports().forEach(report -> reported.add(report.node()));
            for (Identifier id : nodes.keySet())
                if (!lines.contains(id) && !reported.contains(id))
                    reports.add(new ChainCache.Report(id,
                            "cannot be reached from a first level: the chain is cyclic, split, or refused"));
        }
    }

    // A technique annotates one chain: a holder starts at its entry level and may be configured for every level
    // after it. A partially annotated chain is reported, so nobody reaches a level nothing describes.
    private static void validateTechniqueLevels(HolderLookup.Provider access, ChainCache<Progression> chains,
                                                Map<Identifier, Holder<Progression>> stages, List<String> problems) {
        MxtDatapackRegistries.holders(access, MxtResourceKeys.TECHNIQUE).forEach(holder -> {
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

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (!event.shouldUpdateStaticData()) return;
        // The indexes below are keyed by registry instance, which a reloaded pack may keep, so they are dropped
        // here rather than left to notice the reload by themselves.
        DamageElements.invalidate();
        ElementReactionService.invalidate();
        FormulaNames.invalidate();
        AlchemyHeatService.invalidate();
        ChainCache.invalidate();
        rebuild(event.getRegistries(), event instanceof TagsUpdatedEvent.ServerDataLoad);
    }

    @EventBusSubscriber(Dist.CLIENT)
    public static final class ClientDatapackCacheEvents {
        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            DatapackCache.clear();
        }
    }
}
