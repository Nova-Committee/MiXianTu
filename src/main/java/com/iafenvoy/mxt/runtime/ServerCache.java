package com.iafenvoy.mxt.runtime;

import com.iafenvoy.mxt.data.cultivation.CultivationProfile;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.cultivation.SkillStage;
import com.iafenvoy.mxt.data.trigger.TriggerRule;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent.ServerDataLoad;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.*;

/**
 * Global server-lifetime cache for derived datapack data. It is absent on the
 * client and outside an active server lifecycle.
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

    private ServerCache(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Returns the active server cache, or empty when no server is running.
     */
    public static Optional<ServerCache> get() {
        return Optional.ofNullable(INSTANCE);
    }

    public MinecraftServer server() {
        return this.server;
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

    /**
     * Rebuilds validated linear cultivation chains after datapack data is available.
     * Invalid chains are rejected so no partial cache can become authoritative.
     */
    private void rebuild() {
        Map<Identifier, Identifier> resolved = new LinkedHashMap<>();
        Map<Identifier, Integer> ranks = new LinkedHashMap<>();
        Map<Identifier, Identifier> profiles = new LinkedHashMap<>();
        MxtDatapackRegistries.holders(this.server.registryAccess(), MxtResourceKeys.CULTIVATION).forEach(profileHolder -> {
            CultivationProfile profile = profileHolder.value();
            Identifier resource = HolderHelper.id(profile.resource());
            Identifier previous = profiles.putIfAbsent(resource, profileHolder.key().identifier());
            if (previous != null)
                throw new IllegalStateException("Resource " + resource + " has more than one cultivation profile: "
                        + previous + " and " + profileHolder.key().identifier());
            // The chain belongs to the profile: every stage reachable from its first realm must name it.
            profile.firstRealm().ifPresent(first -> this.indexChain(profileHolder.key().identifier(), HolderHelper.id(first), resolved, ranks));
        });
        this.cultivationByRealm = resolved;
        this.rankByRealm = ranks;
        this.rebuildTriggerRules();
        this.rebuildSkillChains();
    }

    /**
     * Indexes datapack trigger rules by the signal their trigger names, so publishing a signal never
     * walks the whole registry.
     */
    private void rebuildTriggerRules() {
        Map<Identifier, List<Reference<TriggerRule>>> rules = new LinkedHashMap<>();
        MxtDatapackRegistries.holders(this.server.registryAccess(), MxtResourceKeys.TRIGGER).forEach(rule ->
                rules.computeIfAbsent(rule.value().trigger().signalType(), ignored -> new ArrayList<>()).add(rule));
        Map<Identifier, List<Reference<TriggerRule>>> indexed = new LinkedHashMap<>();
        rules.forEach((signal, entries) -> indexed.put(signal, List.copyOf(entries)));
        this.triggerRulesBySignal = Collections.unmodifiableMap(indexed);
    }

    /**
     * Every rule that reacts to one signal, in registry order.
     */
    public List<Reference<TriggerRule>> triggerRules(Identifier signal) {
        return this.triggerRulesBySignal.getOrDefault(signal, List.of());
    }

    /**
     * Gets the cultivation profile owning a validated realm. The profile is what a realm stage
     * names, and it is what the chain belongs to; the value it stores is reached through it.
     */
    public Optional<Identifier> cultivationForRealm(Identifier realm) {
        return Optional.ofNullable(this.cultivationByRealm.get(realm));
    }

    public boolean containsRealm(Identifier realm) {
        return this.cultivationByRealm.containsKey(realm);
    }

    /**
     * Returns the zero-based rank of a realm in its validated cultivation chain.
     */
    public Optional<Integer> rankForRealm(Identifier realm) {
        return Optional.ofNullable(this.rankByRealm.get(realm));
    }

    /**
     * Returns whether two realms share a cultivation chain and current is no lower than required.
     */
    public boolean isRealmAtLeast(Identifier current, Identifier required) {
        Identifier currentCultivation = this.cultivationByRealm.get(current);
        return currentCultivation != null && currentCultivation.equals(this.cultivationByRealm.get(required))
                && this.rankByRealm.getOrDefault(current, -1) >= this.rankByRealm.getOrDefault(required, Integer.MAX_VALUE);
    }

    /**
     * Gets the skill owning a validated mastery level.
     */
    public Optional<Identifier> skillForStage(Identifier stage) {
        return Optional.ofNullable(this.skillByStage.get(stage));
    }

    public boolean containsStage(Identifier stage) {
        return this.skillByStage.containsKey(stage);
    }

    /**
     * Returns the zero-based rank of a mastery level in its validated skill chain. The first level of
     * a chain is {@code 0}, which is what lets a level be compared with another one.
     */
    public Optional<Integer> rankForStage(Identifier stage) {
        return Optional.ofNullable(this.rankByStage.get(stage));
    }

    /**
     * Returns whether two mastery levels share a skill chain and current is no lower than required.
     * This is the comparison every stage-gated rule uses: an ability keyed on a level is a minimum
     * requirement, so it applies while the holder stands on that level or a later one.
     */
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
            if (stage == null || !HolderHelper.id(stage.cultivation()).equals(cultivation)) {
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

    /**
     * Rebuilds validated linear skill chains.
     *
     * <p>A skill chain is discovered from its links rather than from whichever level a definition
     * happens to enter at: a realm chain has one declared first realm per resource, while several
     * techniques may share a skill and enter it at different levels, so the order has to come from
     * {@code next_stage} itself. The first level of a chain is the one no other level follows, and
     * ranks are assigned by walking down from it.</p>
     *
     * <p>As with realm chains, an invalid chain is rejected instead of being indexed partially: a
     * half-ordered chain would silently compare levels that never were comparable.</p>
     */
    private void rebuildSkillChains() {
        Map<Identifier, SkillStage> stages = new LinkedHashMap<>();
        MxtDatapackRegistries.holders(this.server.registryAccess(), MxtResourceKeys.SKILL_STAGE)
                .forEach(holder -> stages.put(holder.key().identifier(), holder.value()));
        Map<Identifier, Identifier> previous = new LinkedHashMap<>();
        for (Map.Entry<Identifier, SkillStage> entry : stages.entrySet()) {
            Identifier next = entry.getValue().nextStage().map(HolderHelper::id).orElse(null);
            if (next == null) continue;
            SkillStage target = stages.get(next);
            if (target == null)
                throw new IllegalStateException("Skill stage " + entry.getKey() + " points at the unknown stage " + next);
            if (!target.skill().equals(entry.getValue().skill()))
                throw new IllegalStateException("Skill stage " + entry.getKey() + " of skill " + entry.getValue().skill()
                        + " points at " + next + " of skill " + target.skill());
            Identifier other = previous.putIfAbsent(next, entry.getKey());
            if (other != null && !other.equals(entry.getKey()))
                throw new IllegalStateException("Skill stage " + next + " follows both " + other + " and " + entry.getKey());
        }
        Map<Identifier, Identifier> resolved = new LinkedHashMap<>();
        Map<Identifier, Integer> ranks = new LinkedHashMap<>();
        Map<Identifier, Identifier> firstBySkill = new LinkedHashMap<>();
        for (Identifier first : stages.keySet()) {
            if (previous.containsKey(first)) continue;
            Identifier known = firstBySkill.putIfAbsent(stages.get(first).skill(), first);
            if (known != null)
                throw new IllegalStateException("Skill " + stages.get(first).skill() + " has more than one first stage: "
                        + known + " and " + first);
            Identifier current = first;
            int rank = 0;
            while (current != null) {
                if (ranks.containsKey(current))
                    throw new IllegalStateException("Cyclic skill chain for " + stages.get(current).skill() + " at stage " + current);
                resolved.put(current, stages.get(current).skill());
                ranks.put(current, rank++);
                current = stages.get(current).nextStage().map(HolderHelper::id).orElse(null);
            }
        }
        for (Identifier id : stages.keySet())
            if (!ranks.containsKey(id))
                throw new IllegalStateException("Skill stage " + id + " cannot be reached from a first stage: the chain is cyclic");
        this.validateTechniqueChains(resolved);
        this.skillByStage = resolved;
        this.rankByStage = ranks;
    }

    /**
     * A technique's mastery levels must all belong to the chain its entry level is on; a level from
     * another skill could never be reached, and would be reported as an ordinary satisfied minimum.
     */
    private void validateTechniqueChains(Map<Identifier, Identifier> resolved) {
        MxtDatapackRegistries.holders(this.server.registryAccess(), MxtResourceKeys.CULTIVATION_TECHNIQUE).forEach(holder -> {
            CultivationTechnique technique = holder.value();
            Identifier entry = technique.defaultStage().map(HolderHelper::id).orElse(null);
            if (entry == null) return;
            Identifier skill = resolved.get(entry);
            if (skill == null)
                throw new IllegalStateException("Technique " + holder.key().identifier() + " enters the unknown skill stage " + entry);
            for (Holder<SkillStage> stage : technique.stageAbilities().keySet()) {
                Identifier id = HolderHelper.id(stage);
                if (!skill.equals(resolved.get(id)))
                    throw new IllegalStateException("Technique " + holder.key().identifier() + " unlocks abilities on stage " + id
                            + " of skill " + resolved.get(id) + " instead of " + skill);
            }
            for (Holder<SkillStage> stage : technique.advanceConditions().keySet()) {
                Identifier id = HolderHelper.id(stage);
                if (!skill.equals(resolved.get(id)))
                    throw new IllegalStateException("Technique " + holder.key().identifier() + " requires an advancement condition on stage " + id
                            + " of skill " + resolved.get(id) + " instead of " + skill);
            }
        });
    }
}
