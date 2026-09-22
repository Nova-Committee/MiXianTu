package com.iafenvoy.mxt.runtime;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.action.NoOpAction;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.type.ActiveAbilityType;
import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.artifact.ability.ArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.GrantArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.GrantArtifactAbility.Intent;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.cultivation.SkillStage;
import com.iafenvoy.mxt.data.trigger.TriggerRule;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
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
    private Map<Identifier, Identifier> cultivationByRealm = new LinkedHashMap<>();
    private Map<Identifier, Integer> rankByRealm = new LinkedHashMap<>();
    private Map<Identifier, Identifier> skillByStage = new LinkedHashMap<>();
    private Map<Identifier, Integer> rankByStage = new LinkedHashMap<>();
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

    /** Every problem the last rebuild found, each naming the file to fix; reported instead of aborting. */
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
        // Rebuild only after a server datapack load or /reload, not for every player sync.
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
        Map<Identifier, Identifier> resolved = new LinkedHashMap<>();
        Map<Identifier, Integer> ranks = new LinkedHashMap<>();
        Map<Identifier, Identifier> profiles = new LinkedHashMap<>();
        MxtDatapackRegistries.holders(this.server.registryAccess(), MxtResourceKeys.AURA).forEach(profileHolder -> {
            Aura profile = profileHolder.value();
            Identifier resource = HolderHelper.id(profile.resource());
            Identifier previous = profiles.putIfAbsent(resource, profileHolder.key().identifier());
            if (previous != null) {
                problems.add(problem(MxtResourceKeys.AURA, profileHolder.key().identifier(),
                        "resource " + resource + " already has the cultivation profile " + previous));
                return;
            }
            // The chain belongs to the profile: every stage reachable from its first realm must name it.
            profile.firstRealm().ifPresent(first -> {
                try {
                    this.indexChain(profileHolder.key().identifier(), HolderHelper.id(first), resolved, ranks);
                } catch (RuntimeException exception) {
                    problems.add(problem(MxtResourceKeys.AURA, profileHolder.key().identifier(), message(exception)));
                }
            });
        });
        this.cultivationByRealm = resolved;
        this.rankByRealm = ranks;
        this.rebuildTriggerRules(problems);
        this.rebuildSkillChains(problems);
        this.rebuildArtifacts(problems);
        this.problems = List.copyOf(problems);
        if (problems.isEmpty()) {
            MiXianTu.LOGGER.info("Datapack validation passed: {} cultivation realms, {} skill stages, {} trigger rules",
                    this.cultivationByRealm.size(), this.skillByStage.size(),
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

    private static String message(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    /** Indexed by signal, so publishing one never walks the whole trigger registry. */
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
     * The two artifact checks a definition cannot make about itself, because both need the other registries: a
     * granted ability whose kind is the opposite of the intent it was granted under, and two definitions
     * claiming the same item (the winner would be decided by registry order).
     */
    private void rebuildArtifacts(List<String> problems) {
        Registry<Ability> abilities = this.server.registryAccess().lookupOrThrow(MxtResourceKeys.ABILITY);
        Map<Item, Identifier> claimed = new LinkedHashMap<>();
        for (Reference<Artifact> holder : MxtDatapackRegistries.holders(this.server.registryAccess(), MxtResourceKeys.ARTIFACT).toList()) {
            Artifact definition = holder.value();
            this.checkGrantedAbilityKinds(holder, definition, abilities, problems);
            Identifier previous = this.claimItems(holder, definition, claimed);
            if (previous != null)
                problems.add(problem(MxtResourceKeys.ARTIFACT, holder.key().identifier(),
                        "claims an item that " + previous + " already claims as its artifact"));
        }
    }

    private void checkGrantedAbilityKinds(Reference<Artifact> holder, Artifact definition,
                                          Registry<Ability> abilities, List<String> problems) {
        for (ArtifactAbility ability : definition.abilities()) {
            if (!(ability instanceof GrantArtifactAbility(
                    GrantArtifactAbility.Intent intent,
                    List<Either<Holder<Ability>, TagKey<Ability>>> abilities1
            ))) continue;
            boolean wantsActive = intent == Intent.ACTIVE;
            for (Holder<Ability> granted : RegistryCodecs.listAll(abilities1, abilities)) {
                boolean isActive = granted.value().type() instanceof ActiveAbilityType;
                if (wantsActive == isActive) continue;
                problems.add(problem(MxtResourceKeys.ARTIFACT, holder.key().identifier(),
                        "grants " + HolderHelper.id(granted) + " as mxt:" + (wantsActive ? "active" : "passive")
                                + ", but that ability's own type is " + (isActive ? "active" : "not active")));
            }
        }
    }

    // Reserves every item the definition matches; returns the definition holding the first item it could not
    // take, or null when it took them all.
    private Identifier claimItems(Reference<Artifact> holder, Artifact definition, Map<Item, Identifier> claimed) {
        for (Reference<Item> item : BuiltInRegistries.ITEM.listElements().toList()) {
            ItemStack stack = new ItemStack(item.value());
            if (definition.entries().stream().noneMatch(entry -> entry.matches(stack))) continue;
            Identifier owner = claimed.putIfAbsent(item.value(), holder.key().identifier());
            if (owner != null) return owner;
        }
        return null;
    }

    /**
     * Every rule that reacts to one signal, in registry order.
     */
    public List<Reference<TriggerRule>> triggerRules(Identifier signal) {
        return this.triggerRulesBySignal.getOrDefault(signal, List.of());
    }

    /** Every signal at least one rule reacts to, sorted, for command completion. */
    public List<Identifier> triggerSignals() {
        return this.triggerRulesBySignal.keySet().stream().sorted(Comparator.comparing(Identifier::toString)).toList();
    }

    /** The aura profile owning a validated realm; the stored value is reached through it. */
    public Optional<Identifier> cultivationForRealm(Identifier realm) {
        return Optional.ofNullable(this.cultivationByRealm.get(realm));
    }

    public boolean containsRealm(Identifier realm) {
        return this.cultivationByRealm.containsKey(realm);
    }

    /** Zero-based, counted from the first realm of the validated chain. */
    public Optional<Integer> rankForRealm(Identifier realm) {
        return Optional.ofNullable(this.rankByRealm.get(realm));
    }

    public boolean isRealmAtLeast(Identifier current, Identifier required) {
        Identifier currentCultivation = this.cultivationByRealm.get(current);
        return currentCultivation != null && currentCultivation.equals(this.cultivationByRealm.get(required))
                && this.rankByRealm.getOrDefault(current, -1) >= this.rankByRealm.getOrDefault(required, Integer.MAX_VALUE);
    }

    public Optional<Identifier> skillForStage(Identifier stage) {
        return Optional.ofNullable(this.skillByStage.get(stage));
    }

    public boolean containsStage(Identifier stage) {
        return this.skillByStage.containsKey(stage);
    }

    public Optional<Integer> rankForStage(Identifier stage) {
        return Optional.ofNullable(this.rankByStage.get(stage));
    }

    /** The comparison every stage-gated rule uses. */
    public boolean isStageAtLeast(Identifier current, Identifier required) {
        Identifier currentSkill = this.skillByStage.get(current);
        return currentSkill != null && currentSkill.equals(this.skillByStage.get(required))
                && this.rankByStage.getOrDefault(current, -1) >= this.rankByStage.getOrDefault(required, Integer.MAX_VALUE);
    }

    private void indexChain(Identifier cultivation, Identifier first, Map<Identifier, Identifier> resolved, Map<Identifier, Integer> ranks) {
        Set<Identifier> visited = new HashSet<>();
        Map<Identifier, Identifier> chain = new LinkedHashMap<>();
        Map<Identifier, Integer> chainRanks = new LinkedHashMap<>();
        Identifier current = first;
        int rank = 0;
        while (current != null) {
            if (!visited.add(current)) {
                throw new IllegalStateException("Cyclic cultivation realm chain for " + cultivation + " at realm " + current);
            }
            RealmStage stage = MxtDatapackRegistries.get(MxtResourceKeys.REALM_STAGE, current).orElse(null);
            if (stage == null || !HolderHelper.id(stage.aura()).equals(cultivation)) {
                throw new IllegalStateException("Invalid cultivation realm chain for " + cultivation + " at realm " + current);
            }
            Identifier previous = resolved.get(current);
            if (previous != null && !previous.equals(cultivation)) {
                throw new IllegalStateException("Realm " + current + " belongs to both " + previous + " and " + cultivation);
            }
            chain.put(current, cultivation);
            chainRanks.put(current, rank++);
            current = stage.nextRealm().map(HolderHelper::id).orElse(null);
        }
        resolved.putAll(chain);
        ranks.putAll(chainRanks);
    }

    // A chain is discovered from its next_stage links, not from the level a definition enters at, because
    // several techniques may share a skill and enter it at different levels. A chain that cannot be walked is
    // reported and left out, never indexed partially.
    private void rebuildSkillChains(List<String> problems) {
        Map<Identifier, SkillStage> stages = new LinkedHashMap<>();
        MxtDatapackRegistries.holders(this.server.registryAccess(), MxtResourceKeys.SKILL_STAGE)
                .forEach(holder -> stages.put(holder.key().identifier(), holder.value()));
        Map<Identifier, Identifier> previous = new LinkedHashMap<>();
        for (Entry<Identifier, SkillStage> entry : stages.entrySet()) {
            Identifier next = entry.getValue().nextStage().map(HolderHelper::id).orElse(null);
            if (next == null) continue;
            SkillStage target = stages.get(next);
            if (target == null) {
                problems.add(problem(MxtResourceKeys.SKILL_STAGE, entry.getKey(), "next_stage " + next + " is not a skill stage"));
                continue;
            }
            if (!target.skill().equals(entry.getValue().skill())) {
                problems.add(problem(MxtResourceKeys.SKILL_STAGE, entry.getKey(),
                        "next_stage " + next + " belongs to skill " + target.skill() + " instead of " + entry.getValue().skill()));
                continue;
            }
            Identifier other = previous.putIfAbsent(next, entry.getKey());
            if (other != null && !other.equals(entry.getKey()))
                problems.add(problem(MxtResourceKeys.SKILL_STAGE, next,
                        "follows both " + other + " and " + entry.getKey()));
        }
        Map<Identifier, Identifier> resolved = new LinkedHashMap<>();
        Map<Identifier, Integer> ranks = new LinkedHashMap<>();
        Map<Identifier, Identifier> firstBySkill = new LinkedHashMap<>();
        Set<Identifier> unwalked = new LinkedHashSet<>();
        for (Identifier first : stages.keySet()) {
            if (previous.containsKey(first)) continue;
            Identifier known = firstBySkill.putIfAbsent(stages.get(first).skill(), first);
            if (known != null) {
                problems.add(problem(MxtResourceKeys.SKILL_STAGE, first,
                        "shares skill " + stages.get(first).skill() + " with the first stage " + known));
                unwalked.add(first);
                continue;
            }
            Map<Identifier, Identifier> chain = new LinkedHashMap<>();
            Map<Identifier, Integer> chainRanks = new LinkedHashMap<>();
            Identifier current = first;
            int rank = 0;
            double lastMastery = Double.NEGATIVE_INFINITY;
            String failure = null;
            while (current != null) {
                if (chain.containsKey(current) || ranks.containsKey(current)) {
                    failure = "chain is cyclic, or joins another chain, at stage " + current;
                    break;
                }
                // A later level may not ask for less mastery than an earlier one. Only a constant can be
                // compared: a formula provider that drops only makes advancement climb faster.
                if (stages.get(current).mastery() instanceof Constant(double mastery)) {
                    if (mastery < lastMastery) {
                        failure = "lowers its mastery requirement at stage " + current;
                        break;
                    }
                    lastMastery = mastery;
                }
                chain.put(current, stages.get(current).skill());
                chainRanks.put(current, rank++);
                current = stages.get(current).nextStage().map(HolderHelper::id).orElse(null);
            }
            if (failure != null) {
                problems.add(problem(MxtResourceKeys.SKILL_STAGE, first, failure));
                unwalked.addAll(chain.keySet());
                unwalked.add(current);
                continue;
            }
            // A chain is merged only once it has been walked to its end, so a failure never indexes a prefix.
            resolved.putAll(chain);
            ranks.putAll(chainRanks);
        }
        for (Identifier id : stages.keySet())
            if (!ranks.containsKey(id) && !unwalked.contains(id))
                problems.add(problem(MxtResourceKeys.SKILL_STAGE, id,
                        "cannot be reached from a first stage: the chain is cyclic or split"));
        this.validateTechniqueChains(resolved, stages, problems);
        this.skillByStage = resolved;
        this.rankByStage = ranks;
    }

    // A technique annotates one chain: a holder starts at its entry level and may be configured for every level
    // after it. A partially annotated chain is reported, so nobody reaches a level nothing describes.
    private void validateTechniqueChains(Map<Identifier, Identifier> resolved, Map<Identifier, SkillStage> stages,
                                         List<String> problems) {
        MxtDatapackRegistries.holders(this.server.registryAccess(), MxtResourceKeys.TECHNIQUE).forEach(holder -> {
            Technique technique = holder.value();
            Identifier entry = technique.defaultStage().map(HolderHelper::id).orElse(null);
            if (entry == null) return;
            Identifier techniqueId = holder.key().identifier();
            Identifier skill = resolved.get(entry);
            if (skill == null) {
                // A stage that exists but was not indexed belongs to a chain that was already reported.
                if (!stages.containsKey(entry))
                    problems.add(problem(MxtResourceKeys.TECHNIQUE, techniqueId, "enters the unknown skill stage " + entry));
                return;
            }
            Set<Identifier> configured = new LinkedHashSet<>();
            technique.configuration().keySet().forEach(stage -> configured.add(HolderHelper.id(stage)));
            Set<Identifier> reached = new LinkedHashSet<>();
            Identifier current = entry;
            while (current != null) {
                if (!reached.add(current)) {
                    problems.add(problem(MxtResourceKeys.TECHNIQUE, techniqueId, "walks a cyclic skill chain at stage " + current));
                    return;
                }
                if (!entry.equals(current) && !configured.contains(current)) {
                    problems.add(problem(MxtResourceKeys.TECHNIQUE, techniqueId, "does not configure the skill stage " + current));
                    return;
                }
                Identifier stageSkill = resolved.get(current);
                if (!skill.equals(stageSkill)) {
                    problems.add(problem(MxtResourceKeys.TECHNIQUE, techniqueId,
                            "walks stage " + current + " of skill " + stageSkill + " instead of " + skill));
                    return;
                }
                SkillStage stage = stages.get(current);
                if (stage == null) {
                    problems.add(problem(MxtResourceKeys.TECHNIQUE, techniqueId, "walks the unknown skill stage " + current));
                    return;
                }
                current = stage.nextStage().map(HolderHelper::id).orElse(null);
            }
            for (Identifier configuredStage : configured)
                if (!reached.contains(configuredStage))
                    problems.add(problem(MxtResourceKeys.TECHNIQUE, techniqueId,
                            "configures skill stage " + configuredStage + ", which it can never reach from " + entry));
        });
    }
}
