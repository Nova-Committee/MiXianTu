package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraRequirement;
import com.iafenvoy.mxt.event.AbilityUseEvent.Pre;
import com.iafenvoy.mxt.registry.*;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.artifact.ArtifactDescription;
import com.iafenvoy.mxt.data.artifact.ability.FlightArtifactAbility;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.condition.builtin.entity.AuraElementEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.ElementAttachmentEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.HasElementEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.InRealmInstanceEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.InRealmInstanceEntityCondition.Role;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.data.item.ContractScrollComponent;
import com.iafenvoy.mxt.data.item.FormationPlateComponent;
import com.iafenvoy.mxt.data.item.RealmTokenComponent;
import com.iafenvoy.mxt.data.realm.RealmInstance;
import com.iafenvoy.mxt.item.block.entity.RiftBlockEntity;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService.RefineResult;
import com.iafenvoy.mxt.runtime.artifact.FlightService.Result.State;
import com.iafenvoy.mxt.runtime.rift.RiftColors;
import com.iafenvoy.mxt.runtime.rift.RiftConnections;
import com.iafenvoy.mxt.runtime.rift.RiftConnections.Loop;
import com.iafenvoy.mxt.runtime.rift.RiftMesh;
import com.iafenvoy.mxt.runtime.rift.RiftTeleportService;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactStorageService;
import com.iafenvoy.mxt.runtime.artifact.FlightService;
import com.iafenvoy.mxt.runtime.artifact.FlyingSwordEntity;
import com.iafenvoy.mxt.runtime.aura.AuraLookup;
import com.iafenvoy.mxt.runtime.cultivation.CultivationActionService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationActionService.Result;
import com.iafenvoy.mxt.runtime.cultivation.CultivationGrantService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationToggleService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationToggleService.Failure;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.runtime.cultivation.SkillStageService;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueService;
import com.iafenvoy.mxt.runtime.damage.DamageCalculationService;
import com.iafenvoy.mxt.runtime.damage.DamageElements;
import com.iafenvoy.mxt.runtime.element.ElementReactionService;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraResult.SourceKind;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.runtime.world.AuraZonePriorityProbe;
import com.iafenvoy.mxt.runtime.world.RealmInstanceRegistry;
import com.iafenvoy.mxt.runtime.world.RealmInstanceService;
import com.iafenvoy.mxt.runtime.world.RealmRecord;
import com.iafenvoy.mxt.runtime.world.RealmStructurePlacer;
import com.iafenvoy.mxt.screen.information.InformationCollector.InformationEntry;
import com.iafenvoy.mxt.screen.information.InformationManager;
import com.iafenvoy.mxt.screen.information.InformationManager.Side;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosSlotTypes;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.ISlotType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static net.minecraft.commands.Commands.literal;

/**
 * Commands that assemble a playable, development-only Qingxiao cultivation scenario.
 */
public final class MxtTestCommands {
    private static final Identifier QI = id("qi");
    private static final Identifier QI_REFINING = id("qi_refining");
    private static final Identifier SPIRIT_POWER = id("spirit_power");
    private static final Identifier SPIRIT_POWER_REFINING = id("spirit_power_refining");
    private static final Identifier WATER_POWER = id("water_power");
    private static final Identifier SOUL_POWER = id("soul_power");
    private static final Identifier ROOT = id("qingxiao_fire_root");
    private static final Identifier WATER_ROOT = id("water_root");
    private static final Identifier PHYSIQUE = id("qingxiao_body");
    private static final Identifier TECHNIQUE = id("qingxiao_breathing_manual");
    private static final Identifier CULTIVATE = id("qingxiao_meditation");
    private static final Identifier FORMATION = id("spirit_gathering");
    private static final Identifier REALM = id("trial_realm");
    private static final Identifier CONTRACT = id("master_servant");
    private static final Identifier PROBE_FIRE_ROOT = id("fire_root");
    private static final Identifier PROBE_WATER_ROOT = id("water_root");
    private static final Identifier PROBE_INERT_ROOT = id("inert_root");
    private static final Identifier PROBE_ANTI_WATER_ROOT = id("anti_water_root");
    private static final Identifier PROBE_METAL_ROOT = id("metal_root");
    private static final Identifier PROBE_WOOD_ROOT = id("wood_root");
    private static final Identifier PROBE_EARTH_ROOT = id("earth_root");
    private static final Identifier PROBE_METAL_ELEMENT = id("metal");
    private static final Identifier PROBE_WOOD_ELEMENT = id("wood");
    private static final Identifier PROBE_EARTH_ELEMENT = id("earth");
    private static final Identifier PROBE_FIVE_PHASES_TAG = id("five_phases");
    private static final Identifier PROBE_FIRE_ELEMENT = id("fire");
    private static final Identifier PROBE_WATER_ELEMENT = id("water");
    private static final Identifier PROBE_INERT_ELEMENT = id("inert");
    private static final Identifier PROBE_ELEMENT_ABILITY = id("elemental_probe");
    private static final Identifier PROBE_ELEMENT_TAG = id("basic");
    private static final Identifier PROBE_PHYSIQUE = id("probe_body");
    private static final Identifier PROBE_AFFINITY_ABILITY = id("firebolt");
    private static final double PROBE_FIRE_AFFINITY = 1.1D;
    private static final Identifier PROBE_TECHNIQUE = id("sword_manual");
    private static final Identifier PROBE_TECHNIQUE_STAGE = id("sword_art_2");
    private static final Identifier PROBE_ABILITY = id("artifact_guard");
    private static final Identifier SWORD_FOCUS = id("sword_focus");
    /** Wrong on purpose: it grants an active ability as {@code mxt:passive}. */
    private static final Identifier MISDECLARED_ARTIFACT = id("misdeclared_grant_probe");
    /** Wrong on purpose: it claims the item {@link #MISDECLARED_ARTIFACT} already claims. */
    private static final Identifier CLAIM_CONFLICT_ARTIFACT = id("claim_conflict_probe");
    private static final Identifier TEST_ABILITY_SOURCE = id("grant/test_kit");
    private static final List<Identifier> TEST_ACTIVE_ABILITIES = List.of(
            id("firebolt"), id("awaken_divine_sense"), id("expend_test"), id("infuse_true_essence"),
            id("curse_apply_probe"), id("curse_cleanse_probe"), id("curse_query_probe"),
            id("curse_remaining_probe"), id("curse_replace_probe")
    );

    private MxtTestCommands() {
    }

    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(literal("mxt_test")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(literal("kit").executes(context -> giveKit(context.getSource())))
                .then(literal("cultivate").executes(context -> startCultivation(context.getSource())))
                .then(literal("verify").executes(context -> verify(context.getSource())))
                .then(literal("damage").executes(context -> probeDamage(context.getSource())))
                .then(literal("element").executes(context -> probeElement(context.getSource())))
                .then(literal("identity").executes(context -> probeIdentity(context.getSource())))
                .then(literal("artifact").executes(context -> probeArtifact(context.getSource())))
                .then(literal("artifacts").executes(context -> probeArtifactRoster(context.getSource())))
                .then(literal("realm")
                        .executes(context -> probeRealm(context.getSource()))
                        .then(literal("keep").executes(context -> keepRealm(context.getSource())))
                        .then(literal("reopen").executes(context -> reopenRealm(context.getSource()))))
                .then(literal("rift").executes(context -> probeRift(context.getSource())))
                .then(literal("info").executes(context -> showInformation(context.getSource())))
                .then(literal("guide").executes(context -> showGuide(context.getSource()))));
    }

    /**
     * Re-checks the behaviour that has no other observable entry point: the aura zone priority selection.
     */
    private static int verify(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        String auraFailure = verifyAuraPriority(player);
        if (auraFailure != null) {
            source.sendFailure(Component.translatable("command.mxt_test.verify.aura_failed", auraFailure));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt_test.verify.aura_ok"), false);
        return 1;
    }

    /**
     * Asserts the documented rule: highest priority wins inside a tier, the registry ID breaks ties, and
     * a dimension binding is never outranked by a biome binding.
     */
    private static String verifyAuraPriority(ServerPlayer player) {
        Identifier level = player.level().dimension().identifier();
        List<Reference<AuraZone>> biomeZones = new ArrayList<>();
        List<Reference<AuraZone>> dimensionZones = new ArrayList<>();
        for (Reference<AuraZone> holder : MxtDatapackRegistries.holders(player.level().registryAccess(), MxtResourceKeys.AURA_ZONE).toList()) {
            if (declaresDimension(holder.value(), level)) dimensionZones.add(holder);
            if (!holder.value().biomes().isEmpty()) biomeZones.add(holder);
        }
        String tieBreak = verifyPriorityOrdering(biomeZones, dimensionZones);
        if (tieBreak != null) return tieBreak;
        AuraResult resolved = AuraService.getPositionAura(player.level(), player.blockPosition());
        if (!dimensionZones.isEmpty()) {
            Identifier expected = AuraZonePriorityProbe.select(dimensionZones).orElseThrow();
            if (resolved.sourceKind() != SourceKind.DIMENSION)
                return "expected a dimension binding but resolved " + resolved.sourceKind();
            return expected.equals(resolved.source()) ? null
                    : "dimension winner mismatch: expected " + expected + " but resolved " + resolved.source();
        }
        if (biomeZones.isEmpty()) return "no aura zone declares this level, so the rule cannot be checked";
        Identifier expected = AuraZonePriorityProbe.select(biomeZones).orElseThrow();
        if (resolved.sourceKind() != SourceKind.BIOME)
            return "expected a biome binding but resolved " + resolved.sourceKind();
        return expected.equals(resolved.source()) ? null
                : "biome winner mismatch: expected " + expected + " but resolved " + resolved.source();
    }

    /**
     * The production ordering helper must return the maximum priority and, for equal priorities,
     * the ascending registry ID. This is asserted through the same helper the runtime uses.
     */
    private static String verifyPriorityOrdering(List<Reference<AuraZone>> biomeZones,
                                                 List<Reference<AuraZone>> dimensionZones) {
        for (List<Reference<AuraZone>> candidates : List.of(biomeZones, dimensionZones)) {
            if (candidates.isEmpty()) continue;
            int highest = candidates.stream().mapToInt(holder -> holder.value().priority()).max().orElseThrow();
            Identifier expected = candidates.stream()
                    .filter(holder -> holder.value().priority() == highest)
                    .map(HolderHelper::id)
                    .min(Comparator.naturalOrder())
                    .orElseThrow();
            Identifier winner = AuraZonePriorityProbe.select(candidates).orElse(null);
            if (!expected.equals(winner))
                return "priority ordering mismatch: expected " + expected + " but resolved " + winner;
        }
        return null;
    }

    private static boolean declaresDimension(AuraZone zone, Identifier level) {
        return zone.dimensions().stream().anyMatch(value -> value.left()
                .map(key -> key.identifier().equals(level)).orElse(false));
    }

    /**
     * Drives both layers of the damage pipeline against throwaway entities whose element edges are known,
     * because a pipeline is the one thing a data file cannot show: the numbers below are the fixture's own
     * (fire overcomes water {@code 1.5}, water is adapted to fire {@code 0.5}) read back through the same
     * calls the runtime uses, so a regression in either layer or in the incoming event shows up as a
     * mismatch here rather than as quiet damage drift in play.
     *
     * <p>The mastery leg then walks a real chain: the entry stage of {@code mxt_test:sword_manual} is worth
     * {@code 1.1} on the ability it grants and the second stage {@code 1.25}, and the hit asserts that the
     * value reaches both the health of the target and the context a real cast dispatches with. A last leg
     * takes damage the pipeline never shaped - a plain vanilla mob attack - and asserts only the defender's
     * adaptation touched it, which is what makes the two layers two rather than one applied twice.</p>
     */
    private static int probeDamage(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos origin = source.getPlayer() != null
                ? source.getPlayer().blockPosition()
                : level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.ZERO);
        LivingEntity attacker = spawnProbe(level, origin.above(), PROBE_FIRE_ROOT);
        LivingEntity defender = spawnProbe(level, origin.above(2), PROBE_WATER_ROOT);
        // The mastery leg needs a defender of its own: a hit leaves the target briefly invulnerable, and the
        // second strike would land in that window and be refused rather than measured.
        LivingEntity masteryDefender = spawnProbe(level, origin.above(3), PROBE_WATER_ROOT);
        LivingEntity foreignDefender = spawnProbe(level, origin.above(4), PROBE_WATER_ROOT);
        LivingEntity bodyAttacker = spawnProbe(level, origin.above(5), PROBE_FIRE_ROOT);
        LivingEntity bodyDefender = spawnProbe(level, origin.above(6), PROBE_WATER_ROOT);
        LivingEntity affinityAttacker = spawnProbe(level, origin.above(7), PROBE_FIRE_ROOT);
        LivingEntity affinityDefender = spawnProbe(level, origin.above(8), PROBE_WATER_ROOT);
        try {
            if (attacker == null || defender == null || masteryDefender == null || foreignDefender == null
                    || bodyAttacker == null || bodyDefender == null || affinityAttacker == null || affinityDefender == null) {
                source.sendFailure(Component.literal("damage probe: could not create the probe entities"));
                return 0;
            }
            FormulaContext context = FormulaContext.of(attacker);
            double outgoing = DamageCalculationService.outgoing(attacker, defender, 10.0D, context);
            double adapted = DamageCalculationService.incoming(defender, attacker, outgoing);
            float before = defender.getHealth();
            DamageCalculationService.deal(attacker, defender, 10.0D, Optional.empty(), context);
            double lost = before - defender.getHealth();
            boolean elements = close(outgoing, 15.0D) && close(adapted, 7.5D) && close(lost, 7.5D);
            source.sendSuccess(() -> Component.literal("damage probe: element outgoing=" + outgoing
                    + " adapted=" + adapted + " health_lost=" + lost + (elements ? " OK" : " MISMATCH")), false);

            SpiritIdentityAttachment spirit = attacker.getData(MxtAttachments.SPIRIT_IDENTITY);
            Holder<Technique> technique = require(MxtResourceKeys.TECHNIQUE, PROBE_TECHNIQUE);
            Holder<Ability> ability = require(MxtResourceKeys.ABILITY, PROBE_ABILITY);
            spirit.addLearnedTechnique(technique);
            double entry = SkillStageService.damageMultiplier(attacker, ability);
            spirit.setTechniqueStage(technique, require(MxtResourceKeys.SKILL_STAGE, PROBE_TECHNIQUE_STAGE));
            double advanced = SkillStageService.damageMultiplier(attacker, ability);
            float masteryBefore = masteryDefender.getHealth();
            DamageCalculationService.deal(attacker, masteryDefender, 4.0D, Optional.empty(),
                    context.with(DamageCalculationService.DAMAGE_MULTIPLIER, advanced));
            double masteryLost = masteryBefore - masteryDefender.getHealth();
            // The value has to reach the context a real cast dispatches with, not merely exist here: this
            // drives the ability service itself and reads the formula value off the event it posts before
            // running any action, which is the same context a damage action would be handed.
            double[] dispatched = {Double.NaN};
            Consumer<Pre> listener = event ->
                    dispatched[0] = event.context().explicit(DamageCalculationService.DAMAGE_MULTIPLIER);
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                AbilityService.useCarried(ability, ability.value(), attacker,
                        attacker.getData(MxtAttachments.ABILITY_HOLDER), attacker.getData(MxtAttachments.RESOURCE_HOLDER),
                        level.getGameTime(), context, null);
            } finally {
                NeoForge.EVENT_BUS.unregister(listener);
            }
            boolean mastery = close(entry, 1.1D) && close(advanced, 1.25D) && close(masteryLost, 3.75D)
                    && close(dispatched[0], 1.25D);
            source.sendSuccess(() -> Component.literal("damage probe: mastery entry=" + entry
                    + " advanced=" + advanced + " health_lost=" + masteryLost + " dispatched=" + dispatched[0]
                    + (mastery ? " OK" : " MISMATCH")), false);

            // A source the pipeline never saw has to be reduced all the same: this is a plain vanilla mob
            // attack, so only the defender's adaptation may touch it - the attacker's edge must not, or the
            // two layers would not really be two.
            float foreignBefore = foreignDefender.getHealth();
            foreignDefender.hurtServer(level, level.damageSources().mobAttack(attacker), 4.0F);
            double foreignLost = foreignBefore - foreignDefender.getHealth();
            boolean foreign = close(foreignLost, 2.0D);
            source.sendSuccess(() -> Component.literal("damage probe: foreign health_lost=" + foreignLost
                    + (foreign ? " OK" : " MISMATCH")), false);

            // The physique half of the shaping and the reduction. One fixture is granted to both sides - it
            // multiplies what its holder deals by 1.5 and what its holder takes by 0.5 - so one pair of numbers
            // reads both: 10 * 1.5 (fire overcomes water) * 1.5 (dealt) = 22.5, then
            // 22.5 * 0.5 (water is adapted to fire) * 0.5 (taken) = 5.625. The taken multiplier is written as
            // an expression rather than a number, so this leg covers the formula path as well as the constant
            // one, and it is evaluated against the physique holder's own context rather than the attacker's.
            boolean bodyGranted = grantProbePhysique(bodyAttacker, PROBE_PHYSIQUE)
                    && grantProbePhysique(bodyDefender, PROBE_PHYSIQUE);
            FormulaContext bodyContext = FormulaContext.of(bodyAttacker);
            double bodyOutgoing = DamageCalculationService.outgoing(bodyAttacker, bodyDefender, 10.0D, bodyContext);
            double bodyIncoming = DamageCalculationService.incoming(bodyDefender, bodyAttacker, bodyOutgoing);
            float bodyBefore = bodyDefender.getHealth();
            DamageCalculationService.deal(bodyAttacker, bodyDefender, 10.0D, Optional.empty(), bodyContext);
            double bodyLost = bodyBefore - bodyDefender.getHealth();
            boolean physique = bodyGranted && close(bodyOutgoing, 22.5D) && close(bodyIncoming, 5.625D)
                    && close(bodyLost, 5.625D);
            source.sendSuccess(() -> Component.literal("damage probe: physique outgoing=" + bodyOutgoing
                    + " taken=" + bodyIncoming + " health_lost=" + bodyLost + (physique ? " OK" : " MISMATCH")), false);

            // The spirit root's affinity is a factor of the shaping layer now, not only a value a damage
            // formula has to remember: 10 * 1.1 (the fire root's element_ability_modifier) * 1.5 = 16.5, and
            // 16.5 * 0.5 = 8.25 once the water body answers for it. The second half drives a real cast and
            // reads the same value off the context it dispatched with, which is what proves the number the
            // pipeline applies and the number a pack could read are the same one.
            FormulaContext affinityContext = FormulaContext.of(affinityAttacker)
                    .with(DamageCalculationService.ELEMENT_MODIFIER, PROBE_FIRE_AFFINITY);
            double affinityOutgoing = DamageCalculationService.outgoing(affinityAttacker, affinityDefender, 10.0D, affinityContext);
            double affinityIncoming = DamageCalculationService.incoming(affinityDefender, affinityAttacker, affinityOutgoing);
            float affinityBefore = affinityDefender.getHealth();
            DamageCalculationService.deal(affinityAttacker, affinityDefender, 10.0D, Optional.empty(), affinityContext);
            double affinityLost = affinityBefore - affinityDefender.getHealth();
            double[] castAffinity = {Double.NaN};
            Consumer<Pre> affinityListener = event ->
                    castAffinity[0] = event.context().explicit(DamageCalculationService.ELEMENT_MODIFIER);
            NeoForge.EVENT_BUS.addListener(affinityListener);
            try {
                Holder<Ability> affinityAbility = require(MxtResourceKeys.ABILITY, PROBE_AFFINITY_ABILITY);
                AbilityService.useCarried(affinityAbility, affinityAbility.value(), affinityAttacker,
                        affinityAttacker.getData(MxtAttachments.ABILITY_HOLDER), affinityAttacker.getData(MxtAttachments.RESOURCE_HOLDER),
                        level.getGameTime(), FormulaContext.of(affinityAttacker), null);
            } finally {
                NeoForge.EVENT_BUS.unregister(affinityListener);
            }
            boolean affinity = close(castAffinity[0], PROBE_FIRE_AFFINITY) && close(affinityOutgoing, 16.5D)
                    && close(affinityIncoming, 8.25D) && close(affinityLost, 8.25D);
            source.sendSuccess(() -> Component.literal("damage probe: affinity outgoing=" + affinityOutgoing
                    + " reduced=" + affinityIncoming + " health_lost=" + affinityLost + " cast=" + castAffinity[0]
                    + (affinity ? " OK" : " MISMATCH")), false);

            if (elements && mastery && foreign && physique && affinity) {
                source.sendSuccess(() -> Component.literal("damage probe: OK"), false);
                return 1;
            }
            source.sendFailure(Component.literal("damage probe: MISMATCH"));
            return 0;
        } finally {
            if (attacker != null) attacker.discard();
            if (defender != null) defender.discard();
            if (masteryDefender != null) masteryDefender.discard();
            if (foreignDefender != null) foreignDefender.discard();
            if (bodyAttacker != null) bodyAttacker.discard();
            if (bodyDefender != null) bodyDefender.discard();
            if (affinityAttacker != null) affinityAttacker.discard();
            if (affinityDefender != null) affinityDefender.discard();
        }
    }

    /**
     * Hands one disposable probe a physique through the authoritative service, which is the same path a data
     * pack action takes; {@code false} means the definition was refused, and the leg that asked for it fails
     * rather than measuring a body that never got it.
     */
    private static boolean grantProbePhysique(LivingEntity entity, Identifier id) {
        return CultivationIdentityService.grantPhysique(entity, id, require(MxtResourceKeys.PHYSIQUE, id).value(),
                FormulaContext.of(entity)).changed();
    }

    /**
     * Drives the cultivation identity surface end to end: the script API, the rarity a definition now reports,
     * and the reading of a physique written as if it were elemental. Every leg is a number or a boolean rather
     * than a log line, because the point of the probe is to fail loudly when a guarantee the documentation
     * makes stops holding.
     *
     * <p>The three guarantees it pins: a switched-off root or physique is still <em>held</em> while
     * contributing nothing (so the reads have to answer both questions separately, and a switched-off physique
     * must not scale anything); a rarity is a field with a reader rather than a comment; and a field belonging
     * to another registry is ignored while the definition still loads, which is what the record codec does with
     * every key it was not told about.</p>
     */
    private static int probeIdentity(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos origin = source.getPlayer() != null
                ? source.getPlayer().blockPosition()
                : level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.ZERO);
        LivingEntity probe = spawnProbe(level, origin.above(), null);
        try {
            if (probe == null) {
                source.sendFailure(Component.literal("identity probe: could not create the probe entity"));
                return 0;
            }
            boolean granted = MxtKubeJsApi.grantSpiritRoot(probe, PROBE_FIRE_ROOT).changed()
                    && MxtKubeJsApi.grantPhysique(probe, PROBE_PHYSIQUE).changed();
            boolean listed = MxtKubeJsApi.spiritRoots(probe).equals(List.of(PROBE_FIRE_ROOT.toString()))
                    && MxtKubeJsApi.physiques(probe).equals(List.of(PROBE_PHYSIQUE.toString()))
                    && MxtKubeJsApi.activeSpiritRoots(probe).equals(List.of(PROBE_FIRE_ROOT.toString()))
                    && MxtKubeJsApi.activePhysiques(probe).equals(List.of(PROBE_PHYSIQUE.toString()))
                    && MxtKubeJsApi.hasSpiritRoot(probe, PROBE_FIRE_ROOT)
                    && MxtKubeJsApi.hasPhysique(probe, PROBE_PHYSIQUE)
                    && MxtKubeJsApi.isSpiritRootEnabled(probe, PROBE_FIRE_ROOT)
                    && MxtKubeJsApi.isPhysiqueEnabled(probe, PROBE_PHYSIQUE);
            // Off is not gone: the body still holds both, nothing of either counts, and switching again is a
            // change that did not happen rather than a second toggle.
            boolean switchedOff = MxtKubeJsApi.setSpiritRootEnabled(probe, PROBE_FIRE_ROOT, false).changed()
                    && MxtKubeJsApi.setPhysiqueEnabled(probe, PROBE_PHYSIQUE, false).changed()
                    && MxtKubeJsApi.hasSpiritRoot(probe, PROBE_FIRE_ROOT)
                    && MxtKubeJsApi.hasPhysique(probe, PROBE_PHYSIQUE)
                    && !MxtKubeJsApi.isSpiritRootEnabled(probe, PROBE_FIRE_ROOT)
                    && MxtKubeJsApi.activeSpiritRoots(probe).isEmpty()
                    && MxtKubeJsApi.activePhysiques(probe).isEmpty()
                    && close(DamageCalculationService.physiqueMultiplier(probe, true), 1.0D)
                    && !MxtKubeJsApi.setPhysiqueEnabled(probe, PROBE_PHYSIQUE, false).changed();
            boolean removed = MxtKubeJsApi.removeSpiritRoot(probe, PROBE_FIRE_ROOT)
                    && MxtKubeJsApi.removePhysique(probe, PROBE_PHYSIQUE)
                    && MxtKubeJsApi.spiritRoots(probe).isEmpty() && MxtKubeJsApi.physiques(probe).isEmpty()
                    && !MxtKubeJsApi.hasSpiritRoot(probe, PROBE_FIRE_ROOT)
                    && !MxtKubeJsApi.setSpiritRootEnabled(probe, PROBE_FIRE_ROOT, true).changed();
            boolean identity = granted && listed && switchedOff && removed;
            source.sendSuccess(() -> Component.literal("identity probe: granted=" + granted + " listed=" + listed
                    + " switched_off=" + switchedOff + " removed=" + removed + (identity ? " OK" : " MISMATCH")), false);

            // A rarity is content's own word, so it is read back as written and falls back to itself when no
            // language file names it - the two things a consumer that reports it has to be able to do.
            Holder<Physique> physique = require(MxtResourceKeys.PHYSIQUE, PROBE_PHYSIQUE);
            boolean rarity = physique.value().rarity().equals("probe") && DefinitionText.rarity("probe").getString().equals("probe") && require(MxtResourceKeys.SPIRIT_ROOT, PROBE_FIRE_ROOT).value().rarity().equals("uncommon");
            source.sendSuccess(() -> Component.literal("identity probe: rarity=" + rarity
                    + (rarity ? " OK" : " MISMATCH")), false);

            // A physique that also names an element, an element relation or a spirit-root field keeps loading
            // and those keys are ignored, which is the record codec's own reading of a key it was not told
            // about. A written number is still checked while the pack loads, so a broken multiplier is not.
            boolean foreignIgnored = ignoresForeignFields();
            boolean negative = Physique.DIRECT_CODEC.parse(JsonOps.INSTANCE, single("damage_dealt_multiplier", -1.0D)).isError();
            boolean plain = Physique.DIRECT_CODEC.parse(JsonOps.INSTANCE, single("rarity", "probe")).result().isPresent();
            boolean loose = foreignIgnored && negative && plain;
            source.sendSuccess(() -> Component.literal("identity probe: foreign_fields_ignored=" + foreignIgnored
                    + " negative_refused=" + negative + " plain_accepted=" + plain + (loose ? " OK" : " MISMATCH")), false);

            if (identity && rarity && loose) {
                source.sendSuccess(() -> Component.literal("identity probe: OK"), false);
                return 1;
            }
            source.sendFailure(Component.literal("identity probe: MISMATCH"));
            return 0;
        } finally {
            if (probe != null) probe.discard();
        }
    }

    /**
     * Whether a physique written as if it were elemental decodes the same as one that never mentioned those
     * keys. Ignoring a field is only a guarantee if it leaves no trace, which comparing the two decoded
     * definitions checks.
     */
    private static boolean ignoresForeignFields() {
        JsonObject foreign = new JsonObject();
        foreign.addProperty("element", "mxt_test:fire");
        foreign.addProperty("overcomes", "mxt_test:water");
        foreign.addProperty("damage_types", "mxt_test:fire");
        foreign.addProperty("rarity", "probe");
        return Physique.DIRECT_CODEC.parse(JsonOps.INSTANCE, foreign).result()
                .equals(Physique.DIRECT_CODEC.parse(JsonOps.INSTANCE, single("rarity", "probe")).result());
    }

    /**
     * One-field JSON object, for the decode checks that ask whether a single key is enough to break a
     * definition.
     */
    private static JsonObject single(String key, Object value) {
        JsonObject object = new JsonObject();
        if (value instanceof Number number) object.addProperty(key, number);
        else if (value instanceof Boolean flag) object.addProperty(key, flag);
        else object.addProperty(key, String.valueOf(value));
        return object;
    }

    /**
     * Drives the artifact module: a definition claims a real item, the stack is bound to its owner, aura is
     * poured into one of the two kinds it names, its abilities follow the equipment slot, its inventory answers
     * only its owner, its flight entry carries the holder, and it fits the four charm slots.
     *
     * <p>Fixture numbers: {@code mxt_test:qi} declares {@code 100} and {@code mxt_test:water_power} {@code 50},
     * and {@code mxt_test:soul_power} declares nothing. A half feeding therefore raises the ceiling to
     * {@code floor(100 * 1.25) = 125}, the next fill takes the remaining {@code 75}, and a fully fed artifact
     * reaches {@code 100 * 1.5 = 150}.</p>
     */
    private static int probeArtifact(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        ServerLevel level = source.getLevel();
        // Fully qualified: this file's own HolderLookup record shadows the vanilla one inside this class.
        Provider access = level.registryAccess();
        FormulaContext context = FormulaContext.of(player);
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);

        Artifact definition = ArtifactService.definition(access, stack).map(Reference::value).orElse(null);
        boolean declared = definition != null && "sword".equals(definition.itemType())
                && definition.curiosEquipable() && definition.flight().isPresent() && definition.storage().isPresent();
        if (!declared) {
            source.sendFailure(Component.literal("artifact probe: the fixture definition does not claim a diamond sword"));
            return 0;
        }

        Holder<Aura> qi = require(MxtResourceKeys.AURA, QI);
        Holder<Aura> waterPower = require(MxtResourceKeys.AURA, WATER_POWER);
        Holder<Aura> soulPower = require(MxtResourceKeys.AURA, SOUL_POWER);
        int qiCeiling = ArtifactService.capacity(access, stack, qi, 0.0D, context);
        int waterCeiling = ArtifactService.capacity(access, stack, waterPower, 0.0D, context);
        int undeclaredCeiling = ArtifactService.capacity(access, stack, soulPower, 0.0D, context);
        int half = ArtifactService.addEnergy(access, stack, qi, 50.0D, 0.0D, context);
        int raisedCeiling = ArtifactService.capacity(access, stack, qi, 0.0D, context);
        int rest = ArtifactService.addEnergy(access, stack, qi, 1000.0D, 0.0D, context);
        int fullCeiling = ArtifactService.capacity(access, stack, qi, 0.0D, context);
        int stored = ArtifactService.stored(stack, qi);
        int spent = ArtifactService.consumeEnergy(stack, qi, 25.0D);
        boolean aura = qiCeiling == 100 && waterCeiling == 50 && undeclaredCeiling == 0
                && half == 50 && raisedCeiling == 125 && rest == 75 && fullCeiling == 150 && stored == 125
                && spent == 25 && ArtifactService.stored(stack, qi) == 100
                // Nourishment is never lowered by spending, so the ceiling stays where the feeding put it.
                && ArtifactService.capacity(access, stack, qi, 0.0D, context) == 150;
        source.sendSuccess(() -> Component.literal("artifact probe: aura ceiling=" + qiCeiling + "/" + waterCeiling
                + "/" + undeclaredCeiling + " filled=" + half + "+" + rest + " ceiling_after=" + raisedCeiling
                + "->" + fullCeiling + " stored=" + stored + " spent=" + spent), false);

        boolean unowned = !ArtifactService.isOwner(stack, player.getUUID());
        boolean bound = ArtifactService.refine(stack, player) == RefineResult.REFINED
                && ArtifactService.isOwner(stack, player.getUUID());
        LivingEntity bystander = spawnProbe(level, player.blockPosition().above(1), null);
        boolean foreignRefused = bystander != null
                && ArtifactService.refine(stack, bystander) == RefineResult.OWNED_BY_OTHER;
        if (bystander != null) bystander.discard();
        boolean ownership = unowned && bound && foreignRefused;
        source.sendSuccess(() -> Component.literal("artifact probe: ownership unowned=" + unowned + " bound=" + bound
                + " foreign_refused=" + foreignRefused), false);

        Holder<Ability> passive = require(MxtResourceKeys.ABILITY, PROBE_ABILITY);
        Holder<Ability> active = require(MxtResourceKeys.ABILITY, PROBE_AFFINITY_ABILITY);
        List<Identifier> declaredAbilities = ArtifactService.abilityIds(access, stack);
        boolean grantsBoth = declaredAbilities.contains(PROBE_ABILITY) && declaredAbilities.contains(PROBE_AFFINITY_ABILITY);
        AbilityAttachment holder = player.getData(MxtAttachments.ABILITY_HOLDER);
        boolean hadPassive = holder.has(passive);
        boolean hadActive = holder.has(active);
        ItemStack previous = player.getMainHandItem().copy();
        player.setItemSlot(EquipmentSlot.MAINHAND, stack);
        holder = player.getData(MxtAttachments.ABILITY_HOLDER);
        boolean equipped = holder.has(passive) && holder.has(active);
        player.setItemSlot(EquipmentSlot.MAINHAND, previous);
        holder = player.getData(MxtAttachments.ABILITY_HOLDER);
        boolean released = holder.has(passive) == hadPassive && holder.has(active) == hadActive;
        boolean abilities = grantsBoth && equipped && released;
        source.sendSuccess(() -> Component.literal("artifact probe: abilities declared=" + grantsBoth
                + " equipped=" + equipped + " released=" + released), false);

        boolean storage = ArtifactStorageService.INSTANCE.slots(access, stack, context) == 9
                && ArtifactStorageService.INSTANCE.set(access, stack, 0, new ItemStack(Items.APPLE), player)
                && ArtifactStorageService.INSTANCE.get(access, stack, 0, player).is(Items.APPLE)
                && !ArtifactStorageService.INSTANCE.set(access, stack, 9, new ItemStack(Items.APPLE), player);
        source.sendSuccess(() -> Component.literal("artifact probe: storage=" + storage), false);

        player.setItemSlot(EquipmentSlot.MAINHAND, stack);
        Optional<Reference<Artifact>> resolved = ArtifactService.definition(access, stack);
        FlightService.Result mounted = resolved.map(holderFound -> FlightService.mount(player, stack, holderFound, context)).orElse(null);
        boolean flew = mounted != null && mounted.state() == State.MOUNTED
                && player.getData(MxtAttachments.FLIGHT).active() && player.getVehicle() instanceof FlyingSwordEntity;
        FlightService.dismount(player, FlightService.Failure.STOPPED);
        boolean landed = !player.getData(MxtAttachments.FLIGHT).active()
                && !(player.getVehicle() instanceof FlyingSwordEntity);
        player.setItemSlot(EquipmentSlot.MAINHAND, previous);
        boolean flight = flew && landed;
        source.sendSuccess(() -> Component.literal("artifact probe: flight mounted=" + flew + " landed=" + landed), false);

        // The charm slots are Curios' built-in slot, so this asserts the merged definition: the size a data pack
        // added, and that membership is answered by the definition rather than by a tag.
        ISlotType charm = CuriosSlotTypes.getSlotType("charm", false);
        SlotContext charmSlot = new SlotContext("charm", player, 0, false, true);
        boolean artifactFits = charm != null && CuriosApi.isStackValid(charmSlot, stack);
        // The control is a sword no artifact claims: the netherite sword is only a weapon binding, so it must be
        // refused while the fixture - which declares curios_equipable - is accepted.
        boolean plainRefused = charm != null && !CuriosApi.isStackValid(charmSlot, new ItemStack(Items.NETHERITE_SWORD));
        boolean curios = charm != null && charm.getSize() == 4 && artifactFits && plainRefused;
        source.sendSuccess(() -> Component.literal("artifact probe: charm size=" + (charm == null ? "missing" : charm.getSize())
                + " artifact=" + artifactFits + " plain_refused=" + plainRefused), false);

        if (aura && ownership && abilities && storage && flight && curios) {
            source.sendSuccess(() -> Component.literal("artifact probe: OK"), false);
            return 1;
        }
        source.sendFailure(Component.literal("artifact probe: MISMATCH (aura=" + aura + " ownership=" + ownership
                + " abilities=" + abilities + " storage=" + storage + " flight=" + flight + " curios=" + curios + ")"));
        return 0;
    }

    /**
     * The artifact fixture roster: one row per item, every question asked through the service the game itself uses.
     * Together the rows cover item matching by id, by list and by item tag, per-aura ceilings (one of them written
     * as a formula), storage slots from a formula, an {@code mxt:empty} placeholder beside a grant that mixes an id
     * with an ability tag, the charm gate, the ownership gate {@code require_owner} switches, a refine action that
     * charges on the way in, and the flight entry's own numbers.
     *
     * <p>Two fixtures are wrong on purpose, because the checks they trip live in {@code ServerCache} and have no
     * other way to be observed: {@code mxt_test:misdeclared_grant_probe} grants an active ability as
     * {@code mxt:passive}, and {@code mxt_test:claim_conflict_probe} claims an item the first one also claims.
     * Registry order decides which of the two the claim problem names, so that assertion accepts either.</p>
     */
    private static int probeArtifactRoster(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        Provider access = source.getLevel().registryAccess();
        FormulaContext context = FormulaContext.of(player);
        Holder<Aura> qi = require(MxtResourceKeys.AURA, QI);
        Holder<Aura> waterPower = require(MxtResourceKeys.AURA, WATER_POWER);
        Holder<Aura> soulPower = require(MxtResourceKeys.AURA, SOUL_POWER);

        boolean ok = true;
        for (ArtifactRow row : List.of(
                new ArtifactRow(Items.IRON_SWORD, "flying_sword", 60, 0, 0, 0, false, true, true),
                new ArtifactRow(Items.AMETHYST_SHARD, "jade_pendant", 200, 100, 25, 27, true, false, false),
                new ArtifactRow(Items.PRISMARINE_SHARD, "protective_talisman", 40, 0, 0, 0, true, false, false),
                // The second item of the same definition, reached through the item tag its `items` list names.
                new ArtifactRow(Items.QUARTZ, "protective_talisman", 40, 0, 0, 0, true, false, false),
                new ArtifactRow(Items.BELL, "beast_bell", 60, 0, 0, 18, true, false, false))) {
            ItemStack stack = new ItemStack(row.item());
            Artifact definition = ArtifactService.definition(access, stack).map(Reference::value).orElse(null);
            boolean matches = definition != null && row.itemType().equals(definition.itemType())
                    && row.curiosEquipable() == ArtifactService.curiosEquipable(access, stack)
                    && row.requireOwner() == definition.requireOwner()
                    && row.flight() == definition.flight().isPresent()
                    && ArtifactService.storageSlots(access, stack, context) == row.slots()
                    && ArtifactService.capacity(access, stack, qi, 0.0D, context) == row.qi()
                    && ArtifactService.capacity(access, stack, waterPower, 0.0D, context) == row.waterPower()
                    && ArtifactService.capacity(access, stack, soulPower, 0.0D, context) == row.soulPower();
            ok &= check(source, "artifact roster " + BuiltInRegistries.ITEM.getKey(row.item()) + " = "
                    + (definition == null ? "unclaimed" : definition.itemType()), matches);
        }

        ItemStack flightStack = new ItemStack(Items.IRON_SWORD);
        FlightArtifactAbility flight = ArtifactService.flight(access, flightStack).orElse(null);
        boolean flightEntry = flight != null && close(flight.speed().evaluate(context), 0.12D)
                && flight.costs().size() == 1 && flight.costs().getFirst().id().equals(QI)
                && close(flight.costs().getFirst().amount().evaluate(context), 2.0D);
        ok &= check(source, "artifact roster flight entry speed=0.12 costs=2 qi", flightEntry);

        ItemStack wardStack = new ItemStack(Items.PRISMARINE_SHARD);
        boolean grants = ArtifactService.definition(access, wardStack)
                .map(holder -> holder.value().abilities().size()).orElse(0) == 2
                && ArtifactService.abilityIds(access, wardStack).equals(List.of(PROBE_ABILITY, SWORD_FOCUS));
        ok &= check(source, "artifact roster grants=id+tag beside an mxt:empty placeholder", grants);

        // The refine action charges 40 of a 200 ceiling, so that feeding raises the ceiling to floor(200 * 1.1).
        ItemStack jadeStack = new ItemStack(Items.AMETHYST_SHARD);
        boolean refined = ArtifactService.refine(jadeStack, player) == RefineResult.REFINED;
        int jadeStored = ArtifactService.stored(jadeStack, qi);
        int jadeCeiling = ArtifactService.capacity(access, jadeStack, qi, 0.0D, context);
        ok &= check(source, "artifact roster refine_action stored=" + jadeStored + " ceiling=" + jadeCeiling,
                refined && ArtifactService.isOwner(jadeStack, player.getUUID()) && jadeStored == 40 && jadeCeiling == 220);

        ISlotType charm = CuriosSlotTypes.getSlotType("charm", false);
        SlotContext charmSlot = new SlotContext("charm", player, 0, false, true);
        boolean charmGate = charm != null && CuriosApi.isStackValid(charmSlot, wardStack)
                && CuriosApi.isStackValid(charmSlot, jadeStack)
                && !CuriosApi.isStackValid(charmSlot, flightStack);
        ok &= check(source, "artifact roster charm gate (declared yes, undeclared no)", charmGate);

        // Ownership is the definition's choice: a definition asking for an owner refuses until it has one, while
        // one that does not is open to anybody until it is refined and then answers to its owner alone.
        Reference<Artifact> flightHolder = ArtifactService.definition(access, flightStack).orElse(null);
        Reference<Artifact> jadeHolder = ArtifactService.definition(access, jadeStack).orElse(null);
        ItemStack plainJade = new ItemStack(Items.AMETHYST_SHARD);
        ItemStack ownedSword = flightStack.copy();
        ArtifactService.refine(ownedSword, player);
        UUID stranger = UUID.randomUUID();
        boolean ownershipGate = flightHolder != null && jadeHolder != null
                && !ArtifactService.mayUse(flightStack, flightHolder, player.getUUID())
                && !ArtifactService.mayUse(ownedSword, flightHolder, stranger)
                && ArtifactService.mayUse(ownedSword, flightHolder, player.getUUID())
                && ArtifactService.mayUse(plainJade, jadeHolder, player.getUUID())
                && ArtifactService.mayUse(plainJade, jadeHolder, stranger)
                && ArtifactService.mayUse(jadeStack, jadeHolder, player.getUUID())
                && !ArtifactService.mayUse(jadeStack, jadeHolder, stranger)
                && !ArtifactService.hasOwner(plainJade) && ArtifactService.hasOwner(jadeStack);
        ok &= check(source, "artifact roster ownership gate require_owner=true refuses, =false binds", ownershipGate);

        // The tooltip is built by ArtifactDescription so the same list can be read here without a client: the
        // rendered words belong to a language file, but how many lines a definition earns belongs to this module.
        // Ownership and warmth are known because the fresh stacks carry neither and the jade was just refined.
        List<String> flightLines = ArtifactDescription.keys(ArtifactDescription.describe(access, flightStack, player, false));
        List<String> jadeLines = ArtifactDescription.keys(ArtifactDescription.describe(access, new ItemStack(Items.AMETHYST_SHARD), player, false));
        List<String> fedLines = ArtifactDescription.keys(ArtifactDescription.describe(access, jadeStack, player, false));
        List<String> wardLines = ArtifactDescription.keys(ArtifactDescription.describe(access, wardStack, player, false));
        boolean tooltip = flightLines.equals(List.of(tooltipKey("header"), tooltipKey("item_type"), tooltipKey("unowned"),
                        tooltipKey("aura"), tooltipKey("flight"), tooltipKey("flight_cost")))
                // The jade does not require an owner, so a fresh one says nothing about ownership at all.
                && jadeLines.equals(List.of(tooltipKey("header"), tooltipKey("item_type"),
                        tooltipKey("aura"), tooltipKey("aura"), tooltipKey("aura"), tooltipKey("storage")))
                && fedLines.equals(List.of(tooltipKey("header"), tooltipKey("item_type"), tooltipKey("owned"),
                        tooltipKey("aura"), tooltipKey("aura"), tooltipKey("aura"), tooltipKey("nourishment"),
                        tooltipKey("storage")))
                && wardLines.equals(List.of(tooltipKey("header"), tooltipKey("item_type"),
                        tooltipKey("aura"), tooltipKey("passive"), tooltipKey("passive")))
                // A stack no definition claims gets nothing at all, and advanced tooltips add the id under the name.
                && ArtifactDescription.describe(access, new ItemStack(Items.DIAMOND), player, false).isEmpty()
                && ArtifactDescription.keys(ArtifactDescription.describe(access, flightStack, player, true)).size() == flightLines.size() + 1;
        ok &= check(source, "artifact roster tooltip lines flight=" + flightLines.size() + " jade=" + jadeLines.size()
                + " fed=" + fedLines.size() + " ward=" + wardLines.size(), tooltip);

        List<String> problems = ServerCache.get().map(cache -> cache.problems().stream()
                .filter(problem -> problem.contains("/mxt/artifact/")).toList()).orElse(List.of());
        boolean misdeclared = problems.stream().anyMatch(problem ->
                problem.contains(MISDECLARED_ARTIFACT.getPath()) && problem.contains("as mxt:passive")
                        && problem.contains("own type is active"));
        boolean claimed = problems.stream().anyMatch(problem -> problem.contains("already claims")
                && (problem.contains(MISDECLARED_ARTIFACT.getPath()) || problem.contains(CLAIM_CONFLICT_ARTIFACT.getPath())));
        ok &= check(source, "artifact roster validator problems=" + problems,
                problems.size() == 2 && misdeclared && claimed);

        if (ok) {
            source.sendSuccess(() -> Component.literal("artifact roster: OK"), false);
            return 1;
        }
        source.sendFailure(Component.literal("artifact roster: MISMATCH"));
        return 0;
    }

    /**
     * One roster row: an item, and what every consumer must answer about the definition claiming it.
     */
    private record ArtifactRow(Item item, String itemType, int qi, int waterPower, int soulPower, int slots,
                               boolean curiosEquipable, boolean flight, boolean requireOwner) {
    }

    /** One artifact tooltip key, so the roster can spell out the lines a definition has to produce for a stack. */
    private static String tooltipKey(String path) {
        return "tooltip.mxt.artifact." + path;
    }

    /**
     * One disposable probe entity carrying exactly one spirit root, so the element it strikes or is struck
     * with is never a question. A pig is used because it has no armour and no innate resistance, which keeps
     * the health it loses equal to the amount the pipeline handed over. A null root leaves the probe carrying
     * none, which is how "the element came from the damage type and not from the caster" is told apart.
     */
    private static LivingEntity spawnProbe(ServerLevel level, BlockPos pos, @Nullable Identifier root) {
        Pig pig = EntityType.PIG.create(level, EntitySpawnReason.COMMAND);
        if (pig == null) return null;
        pig.setNoAi(true);
        pig.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        level.addFreshEntity(pig);
        if (root != null)
            CultivationIdentityService.grantSpiritRoot(pig, root, require(MxtResourceKeys.SPIRIT_ROOT, root).value());
        return pig;
    }

    /**
     * Drives the element channel end to end: which element a strike is read as, where that reading comes from,
     * what a strike leaves behind, which reaction answers it, and what the enable/disable module does to all of
     * it. Every leg below runs against disposable probe entities, so a mismatch is a number rather than a
     * difference nobody notices in play.
     *
     * <p>The fixture numbers are the ones the test package writes: fire overcomes water {@code 1.5} and water
     * is adapted to fire {@code 0.5}, so a fire strike of {@code 10} on a water body lands as
     * {@code 10 * 1.5 * 0.5 = 7.5} while a water strike on a water body lands as {@code 10 * 0.5 = 5}. That
     * difference is what makes the legs tell the readings apart: the claim leg uses a caster with no roots at
     * all, and the declaration leg a water caster whose art is written as fire.</p>
     */
    private static int probeElement(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos origin = source.getPlayer() != null
                ? source.getPlayer().blockPosition()
                : level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.ZERO);
        Holder<Element> fire = require(MxtResourceKeys.ELEMENT, PROBE_FIRE_ELEMENT);
        Holder<Element> water = require(MxtResourceKeys.ELEMENT, PROBE_WATER_ELEMENT);
        Holder<Element> metal = require(MxtResourceKeys.ELEMENT, PROBE_METAL_ELEMENT);
        Holder<Element> wood = require(MxtResourceKeys.ELEMENT, PROBE_WOOD_ELEMENT);
        Holder<Element> earth = require(MxtResourceKeys.ELEMENT, PROBE_EARTH_ELEMENT);
        Holder<DamageType> magic = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.MAGIC);
        LivingEntity rootless = spawnProbe(level, origin.above(), null);
        LivingEntity waterVictim = spawnProbe(level, origin.above(1), PROBE_WATER_ROOT);
        LivingEntity waterCaster = spawnProbe(level, origin.above(2), PROBE_WATER_ROOT);
        LivingEntity declaredVictim = spawnProbe(level, origin.above(3), PROBE_WATER_ROOT);
        LivingEntity inertHolder = spawnProbe(level, origin.above(4), PROBE_INERT_ROOT);
        LivingEntity reactionVictim = spawnProbe(level, origin.above(5), PROBE_WATER_ROOT);
        LivingEntity toggleProbe = spawnProbe(level, origin.above(6), PROBE_FIRE_ROOT);
        LivingEntity loopVictim = spawnProbe(level, origin.above(7), null);
        LivingEntity conflictProbe = spawnProbe(level, origin.above(8), null);
        LivingEntity metalCaster = spawnProbe(level, origin.above(9), PROBE_METAL_ROOT);
        LivingEntity woodVictim = spawnProbe(level, origin.above(10), PROBE_WOOD_ROOT);
        LivingEntity woodCaster = spawnProbe(level, origin.above(11), PROBE_WOOD_ROOT);
        LivingEntity metalVictim = spawnProbe(level, origin.above(12), PROBE_METAL_ROOT);
        LivingEntity bloomVictim = spawnProbe(level, origin.above(13), null);
        LivingEntity settleVictim = spawnProbe(level, origin.above(14), null);
        try {
            if (rootless == null || waterVictim == null || waterCaster == null || declaredVictim == null
                    || inertHolder == null || reactionVictim == null || toggleProbe == null
                    || loopVictim == null || conflictProbe == null || metalCaster == null || woodVictim == null
                    || woodCaster == null || metalVictim == null || bloomVictim == null || settleVictim == null) {
                source.sendFailure(Component.literal("element probe: could not create the probe entities"));
                return 0;
            }
            // 1. A claimed damage type names the element even when the caster has no roots at all.
            boolean typeClaimsFire = DamageElements.of(level.registryAccess(), magic).contains(fire);
            double beforeClaim = waterVictim.getHealth();
            DamageCalculationService.deal(rootless, waterVictim, 10.0D, Optional.of(magic), FormulaContext.of(rootless));
            double claimedLost = beforeClaim - waterVictim.getHealth();
            boolean claimed = typeClaimsFire && close(claimedLost, 7.5D);
            source.sendSuccess(() -> Component.literal("element probe: claimed magic -> fire, health_lost=" + claimedLost
                    + (claimed ? " OK" : " MISMATCH")), false);

            // 2. An action that declares an element is read as that element, not as the caster's roots: a water
            //    caster would otherwise land 10 * 1.0 * 0.5 = 5 on a water body.
            Ability declaration = require(MxtResourceKeys.ABILITY, PROBE_ELEMENT_ABILITY).value();
            FormulaContext casterContext = FormulaContext.of(waterCaster);
            double beforeDeclared = declaredVictim.getHealth();
            declaration.biEntityAction().execute(waterCaster, declaredVictim,
                    new BiEntityActionContext(waterCaster, declaredVictim, casterContext, null));
            double declaredLost = beforeDeclared - declaredVictim.getHealth();
            boolean declared = close(declaredLost, 7.5D);
            source.sendSuccess(() -> Component.literal("element probe: declared element on a water caster, health_lost="
                    + declaredLost + (declared ? " OK" : " MISMATCH")), false);

            // 3. A strike leaves its element behind, and enough of it is answered by the fixture reaction,
            //    which takes the buildup away and deals its own 6 (no attacker, no claim: unchanged).
            ElementReactionService.applyFromStrike(reactionVictim, Set.of(fire), FormulaContext.of(reactionVictim));
            double built = ElementReactionService.amount(reactionVictim, fire);
            double beforeReaction = reactionVictim.getHealth();
            ElementReactionService.apply(reactionVictim, fire, 4.0D, FormulaContext.of(reactionVictim));
            double reactionLost = beforeReaction - reactionVictim.getHealth();
            double leftOver = ElementReactionService.amount(reactionVictim, fire);
            boolean reaction = close(built, 4.0D) && close(reactionLost, 6.0D) && close(leftOver, 0.0D);
            source.sendSuccess(() -> Component.literal("element probe: attachment built=" + built
                    + " reaction damage=" + reactionLost + " left=" + leftOver + (reaction ? " OK" : " MISMATCH")), false);

            // 4. A disabled element stops applying: its own root contributes nothing, and the condition that
            //    asks about it says no. The fixture element is fetched raw, because the enabled accessor is
            //    exactly what it is here to prove is empty.
            Holder<Element> inert = MxtDatapackRegistries.rawHolder(MxtResourceKeys.ELEMENT, PROBE_INERT_ELEMENT)
                    .orElseThrow(() -> new IllegalStateException("Missing disabled element fixture " + PROBE_INERT_ELEMENT));
            boolean disabled = !Elements.enabled(inert) && Elements.of(inertHolder).isEmpty() && Elements.enabled(fire);
            source.sendSuccess(() -> Component.literal("element probe: disabled element inert=" + disabled
                    + (disabled ? " OK" : " MISMATCH")), false);

            // 5. The conditions that read elements: by element, by element tag, and by what has built up.
            boolean hasElement = new HasElementEntityCondition(List.of(Either.left(fire)))
                    .test(toggleProbe, FormulaContext.of(toggleProbe))
                    && !new HasElementEntityCondition(List.of(Either.left(fire)))
                    .test(waterVictim, FormulaContext.of(waterVictim))
                    && new HasElementEntityCondition(List.of(Either.right(elementTag())))
                    .test(toggleProbe, FormulaContext.of(toggleProbe));
            // The aura leg asks the environment for the same number first and then brackets it, so it tests the
            // element aggregation rather than this world's stock: a chunk emptied by an earlier run reads as
            // "zero fire aura" and the condition is still expected to say so.
            AuraResult resolved = AuraService.getPositionAura(level, toggleProbe.blockPosition());
            double fireAura = resolved.aura().entrySet().stream()
                    .filter(entry -> entry.getKey().value().auraType().filter(fire::equals).isPresent())
                    .mapToDouble(entry -> entry.getValue().amount()).sum();
            boolean auraElement = new AuraElementEntityCondition(Map.of(fire, minimumRange(Math.max(0.0D, fireAura - 0.5D))))
                    .test(toggleProbe, FormulaContext.of(toggleProbe))
                    && !new AuraElementEntityCondition(Map.of(fire, minimumRange(fireAura + 0.5D)))
                    .test(toggleProbe, FormulaContext.of(toggleProbe));
            ElementReactionService.apply(inertHolder, fire, 3.0D, FormulaContext.of(inertHolder));
            boolean attachment = new ElementAttachmentEntityCondition(Map.of(fire, minimumRange(2.0D)))
                    .test(inertHolder, FormulaContext.of(inertHolder))
                    && !new ElementAttachmentEntityCondition(Map.of(fire, minimumRange(5.0D)))
                    .test(inertHolder, FormulaContext.of(inertHolder));
            source.sendSuccess(() -> Component.literal("element probe: conditions has_element=" + hasElement
                    + " aura_element=" + auraElement + " (fire_aura=" + fireAura + ") attachment=" + attachment
                    + (hasElement && auraElement && attachment ? " OK" : " MISMATCH")), false);

            // 6. The enable/disable module: off means held but inert, on means everything comes back, and a root
            //    the body does not hold has no state to switch.
            Holder<SpiritRoot> fireRoot = require(MxtResourceKeys.SPIRIT_ROOT, PROBE_FIRE_ROOT);
            boolean off = CultivationToggleService.setSpiritRootEnabled(toggleProbe, fireRoot, false).changed()
                    && !CultivationToggleService.isSpiritRootEnabled(toggleProbe, fireRoot)
                    && Elements.of(toggleProbe).isEmpty()
                    && toggleProbe.getData(MxtAttachments.SPIRIT_IDENTITY).spiritRoots().contains(fireRoot);
            boolean on = CultivationToggleService.setSpiritRootEnabled(toggleProbe, fireRoot, true).changed()
                    && CultivationToggleService.isSpiritRootEnabled(toggleProbe, fireRoot)
                    && Elements.of(toggleProbe).contains(fire);
            boolean unchanged = !CultivationToggleService.setSpiritRootEnabled(toggleProbe, fireRoot, true).changed();
            boolean notHeld = CultivationToggleService.setSpiritRootEnabled(toggleProbe,
                    require(MxtResourceKeys.SPIRIT_ROOT, PROBE_WATER_ROOT), false).failure()
                    == Failure.NOT_HELD;
            boolean toggle = off && on && unchanged && notHeld;
            source.sendSuccess(() -> Component.literal("element probe: toggle off=" + off + " on=" + on
                    + " unchanged=" + unchanged + " not_held=" + notHeld + (toggle ? " OK" : " MISMATCH")), false);

            // 7. A reaction whose own action applies the element it just consumed feeds itself. The nested
            //    application joins the chain that is already running for this body instead of opening another
            //    one, so the call comes back: every pass takes the demand away and puts it straight back, which
            //    leaves the body carrying exactly what was applied however many passes the bound allows. Before
            //    that guard this leg did not answer at all - the nesting had no floor.
            ElementReactionService.apply(loopVictim, water, 8.0D, FormulaContext.of(loopVictim));
            double loopLeft = ElementReactionService.amount(loopVictim, water);
            boolean loop = close(loopLeft, 8.0D);
            source.sendSuccess(() -> Component.literal("element probe: self-feeding reaction left=" + loopLeft
                    + (loop ? " OK" : " MISMATCH")), false);

            // 8. Who a spirit root rules out. The fixture root carries fire and declares both water and the
            //    disabled inert element, so one body answers all three rules: a live element is refused, a
            //    disabled element is not an element as far as the declaration goes, and once the declaring root
            //    itself is switched off its declaration is not in force either.
            Holder<SpiritRoot> antiWater = require(MxtResourceKeys.SPIRIT_ROOT, PROBE_ANTI_WATER_ROOT);
            Holder<SpiritRoot> probeWater = require(MxtResourceKeys.SPIRIT_ROOT, PROBE_WATER_ROOT);
            Holder<SpiritRoot> probeInert = require(MxtResourceKeys.SPIRIT_ROOT, PROBE_INERT_ROOT);
            boolean blocked = CultivationIdentityService.grantSpiritRoot(conflictProbe, PROBE_ANTI_WATER_ROOT, antiWater.value()).changed()
                    && CultivationIdentityService.grantSpiritRoot(conflictProbe, PROBE_WATER_ROOT, probeWater.value()).failure()
                    == CultivationIdentityService.Failure.ELEMENT_CONFLICT;
            boolean inertFree = CultivationIdentityService.grantSpiritRoot(conflictProbe, PROBE_INERT_ROOT, probeInert.value()).changed();
            boolean reopened = CultivationToggleService.setSpiritRootEnabled(conflictProbe, antiWater, false).changed()
                    && CultivationIdentityService.grantSpiritRoot(conflictProbe, PROBE_WATER_ROOT, probeWater.value()).changed();
            boolean conflict = blocked && inertFree && reopened;
            source.sendSuccess(() -> Component.literal("element probe: conflict blocked=" + blocked
                    + " inert_free=" + inertFree + " reopened=" + reopened
                    + (conflict ? " OK" : " MISMATCH")), false);

            // 9. The five-phase set the test package now ships: metal beats wood in the shaping layer and wood
            //    is soft against metal in the reduction layer, so the same 4 lands as 4 * 1.4 * 1.25 = 7, while
            //    the opposite direction has neither edge and lands as 4. The strike also leaves the striker's
            //    element behind, and a tag over the whole set answers for a member.
            double metalBefore = woodVictim.getHealth();
            DamageCalculationService.deal(metalCaster, woodVictim, 4.0D, Optional.empty(), FormulaContext.of(metalCaster));
            double metalOnWood = metalBefore - woodVictim.getHealth();
            double woodBefore = metalVictim.getHealth();
            DamageCalculationService.deal(woodCaster, metalVictim, 4.0D, Optional.empty(), FormulaContext.of(woodCaster));
            double woodOnMetal = woodBefore - metalVictim.getHealth();
            double metalLeft = ElementReactionService.amount(woodVictim, metal);
            boolean fivePhases = close(metalOnWood, 7.0D) && close(woodOnMetal, 4.0D) && close(metalLeft, 3.0D)
                    && new HasElementEntityCondition(List.of(Either.right(fivePhasesTag())))
                    .test(metalCaster, FormulaContext.of(metalCaster));
            source.sendSuccess(() -> Component.literal("element probe: five phases metal_on_wood=" + metalOnWood
                    + " wood_on_metal=" + woodOnMetal + " metal_left=" + metalLeft
                    + (fivePhases ? " OK" : " MISMATCH")), false);

            // 10. A reaction that demands two elements at once, spends only one of them and acts twice: wood
            //     alone does not answer it, and afterwards the water that completed the demand is still there
            //     while the earth the action left behind has not reached its own demand.
            ElementReactionService.apply(bloomVictim, wood, 6.0D, FormulaContext.of(bloomVictim));
            boolean halfDemand = close(ElementReactionService.amount(bloomVictim, wood), 6.0D);
            double bloomBefore = bloomVictim.getHealth();
            ElementReactionService.apply(bloomVictim, water, 4.0D, FormulaContext.of(bloomVictim));
            double bloomLost = bloomBefore - bloomVictim.getHealth();
            double woodLeft = ElementReactionService.amount(bloomVictim, wood);
            double waterLeft = ElementReactionService.amount(bloomVictim, water);
            double earthLeft = ElementReactionService.amount(bloomVictim, earth);
            boolean bloom = halfDemand && close(woodLeft, 0.0D) && close(waterLeft, 4.0D)
                    && close(earthLeft, 2.0D) && close(bloomLost, 3.0D);
            source.sendSuccess(() -> Component.literal("element probe: two-element reaction half=" + halfDemand
                    + " wood=" + woodLeft + " water=" + waterLeft + " earth=" + earthLeft + " damage=" + bloomLost
                    + (bloom ? " OK" : " MISMATCH")), false);

            // 11. A reaction that consumes nothing answers the same demand on every pass, so one application
            //     fires it exactly as many times as the chain allows and leaves the buildup untouched. The
            //     count is the documented bound, written out here so changing it has to be a decision.
            settleVictim.getData(MxtAttachments.ELEMENT_ATTACHMENT).add(earth, 10.0D);
            int settleFired = ElementReactionService.trigger(settleVictim, FormulaContext.of(settleVictim));
            double settleLeft = ElementReactionService.amount(settleVictim, earth);
            boolean settle = settleFired == 8 && close(settleLeft, 10.0D);
            source.sendSuccess(() -> Component.literal("element probe: unconsumed reaction fired=" + settleFired
                    + " left=" + settleLeft + (settle ? " OK" : " MISMATCH")), false);

            if (claimed && declared && reaction && disabled && hasElement && auraElement && attachment && toggle
                    && loop && conflict && fivePhases && bloom && settle) {
                source.sendSuccess(() -> Component.literal("element probe: OK"), false);
                return 1;
            }
            source.sendFailure(Component.literal("element probe: MISMATCH"));
            return 0;
        } finally {
            //noinspection DataFlowIssue
            for (LivingEntity probe : List.of(rootless, waterVictim, waterCaster, declaredVictim, inertHolder,
                    reactionVictim, toggleProbe, loopVictim, conflictProbe, metalCaster, woodVictim, woodCaster,
                    metalVictim, bloomVictim, settleVictim))
                if (probe != null) probe.discard();
        }
    }

    private static TagKey<Element> elementTag() {
        return TagKey.create(MxtResourceKeys.ELEMENT, PROBE_ELEMENT_TAG);
    }

    private static TagKey<Element> fivePhasesTag() {
        return TagKey.create(MxtResourceKeys.ELEMENT, PROBE_FIVE_PHASES_TAG);
    }

    private static AuraRequirement minimumRange(double minimum) {
        return new AuraRequirement(new Constant(minimum), new Constant(1.0E9D));
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }

    /**
     * Exercises the realm instance machine end to end without a player. An instance is a dimension, so every
     * leg drives the registry and the generation service directly and then inspects the world that came out of
     * it: its border, its placed structure, its landing spot, the instance cap and the claim rules.
     */
    private static int probeRealm(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ServerLevel overworld = server.overworld();
        Holder<RealmInstance> trial = require(MxtResourceKeys.REALM_INSTANCE, REALM);
        Holder<RealmInstance> mirror = require(MxtResourceKeys.REALM_INSTANCE, id("mirror_realm"));
        Holder<RealmInstance> existing = require(MxtResourceKeys.REALM_INSTANCE, id("existing_realm"));
        Holder<RealmInstance> absentStructure = require(MxtResourceKeys.REALM_INSTANCE, id("missing_structure_realm"));
        Holder<RealmInstance> absentTemplate = require(MxtResourceKeys.REALM_INSTANCE, id("template_realm"));
        boolean ok = true;
        List<ResourceKey<Level>> opened = new ArrayList<>();
        List<LivingEntity> probes = new ArrayList<>();
        try {
            // The structure the fixture places is built here instead of shipping a binary fixture.
            Identifier towerId = id("probe/tower");
            BlockPos scratch = overworld.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.ZERO).above(6);
            for (int x = 0; x < 3; x++)
                for (int z = 0; z < 3; z++)
                    overworld.setBlockAndUpdate(scratch.offset(x, 0, z), Blocks.GOLD_BLOCK.defaultBlockState());
            StructureTemplate tower = overworld.getStructureManager().getOrCreate(towerId);
            tower.fillFromWorld(overworld, scratch, new Vec3i(3, 1, 3), false, List.of());
            boolean towerSaved = overworld.getStructureManager().save(towerId);
            for (int x = 0; x < 3; x++)
                for (int z = 0; z < 3; z++)
                    overworld.setBlockAndUpdate(scratch.offset(x, 0, z), Blocks.AIR.defaultBlockState());

            // 1. An instance dimension is created on demand, before anybody is allowed in.
            RealmRecord first = openInstance(server, trial, 0, 12345L, opened);
            ServerLevel dimension = first == null ? null : server.getLevel(first.dimension());
            ok &= check(source, "realm probe: tower_saved=" + towerSaved + " dimension=" + (first != null)
                    + " level=" + (dimension != null), towerSaved && first != null && dimension != null);
            if (dimension == null) return 0;

            // 2. The declared border lands on the instance dimension, which also carries the instance seed.
            WorldBorder border = dimension.getWorldBorder();
            ok &= check(source, "realm probe: border size=" + border.getSize() + " center=(" + border.getCenterX()
                            + "," + border.getCenterZ() + ")",
                    close(border.getSize(), 128.0D) && close(border.getCenterX(), 0.0D) && close(border.getCenterZ(), 0.0D));
            ok &= check(source, "realm probe: seed=" + dimension.getSeed(), dimension.getSeed() == 12345L);

            // 3. Structures are placed before the landing is chosen, so an arrival can stand on them.
            ok &= check(source, "realm probe: structure block=" + dimension.getBlockState(new BlockPos(8, 64, 8)).getBlock(),
                    dimension.getBlockState(new BlockPos(8, 64, 8)).is(Blocks.GOLD_BLOCK));

            // 4. A fixed entry point becomes the instance anchor.
            Vec3 anchor = first.anchor().orElse(null);
            ok &= check(source, "realm probe: anchor=" + anchor + " prepared=" + first.prepared(),
                    anchor != null && close(anchor.x, 0.5D) && close(anchor.y, 65.0D) && close(anchor.z, 0.5D) && first.prepared());

            // 5. The instance cap counts every instance of the definition.
            RealmRecord second = openInstance(server, trial, 1, 999L, opened);
            int count = RealmInstanceRegistry.of(trial).size();
            ok &= check(source, "realm probe: instances=" + count + " cap=" + trial.value().maxInstances(),
                    second != null && count >= trial.value().maxInstances());

            // 6. Membership is capped per instance and a full instance is not joinable.
            List<UUID> two = List.of(UUID.randomUUID(), UUID.randomUUID());
            RealmRecord full = second.with(two);
            RealmInstanceRegistry.replace(full);
            RealmInstanceRegistry.at(first.dimension()).ifPresent(record -> RealmInstanceRegistry.replace(record.with(two)));
            boolean joinable = RealmInstanceRegistry.joinable(trial, UUID.randomUUID()).isPresent();
            ok &= check(source, "realm probe: full=" + full.full() + " joinable=" + joinable, full.full() && !joinable);

            // 7. A claim is recorded, and the condition tells owner from guest.
            LivingEntity probe = spawnProbe(overworld, overworld.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.ZERO).above(2), PROBE_FIRE_ROOT);
            probes.add(probe);
            boolean ownerSeen = false, guestSeen = true, foreignSeen = true;
            if (probe != null) {
                RealmInstanceRegistry.replace(first.withOwner(probe.getUUID()).with(List.of(probe.getUUID())));
                ownerSeen = new InRealmInstanceEntityCondition(Optional.of(Either.left(trial)), Role.OWNER).test(probe, FormulaContext.of(probe));
                guestSeen = new InRealmInstanceEntityCondition(Optional.of(Either.left(trial)), Role.GUEST).test(probe, FormulaContext.of(probe));
                foreignSeen = new InRealmInstanceEntityCondition(Optional.of(Either.left(mirror)), Role.ANY).test(probe, FormulaContext.of(probe));
            }
            ok &= check(source, "realm probe: role owner=" + ownerSeen + " guest=" + guestSeen + " foreign=" + foreignSeen,
                    probe != null && ownerSeen && !guestSeen && !foreignSeen);

            // 8. A realm dimension is reachable by its definition id, a stem realm also by its stem.
            List<Identifier> aliases = RealmInstanceRegistry.aliases(first.dimension().identifier()).toList();
            ok &= check(source, "realm probe: aliases=" + aliases, aliases.contains(REALM));

            // 9. A weighted entry list picks by weight: the zero-weight fixed point is never chosen, so a random
            //    landing inside random_radius is what the anchor has to be.
            RealmRecord mirrorRecord = openInstance(server, mirror, 0, 777L, opened);
            Vec3 mirrorAnchor = mirrorRecord == null ? null : mirrorRecord.anchor().orElse(null);
            ServerLevel mirrorLevel = mirrorRecord == null ? null : server.getLevel(mirrorRecord.dimension());
            boolean inRadius = mirrorAnchor != null && Math.hypot(mirrorAnchor.x, mirrorAnchor.z) <= 16.0D + 1.0E-6D;
            boolean skipped = mirrorLevel != null && !mirrorLevel.getBlockState(new BlockPos(0, 100, 0)).is(Blocks.GOLD_BLOCK);
            ok &= check(source, "realm probe: random anchor=" + mirrorAnchor + " chance_skipped=" + skipped, inRadius && skipped);
            ok &= check(source, "realm probe: stem alias=" + (mirrorRecord == null ? "-"
                            : RealmInstanceRegistry.aliases(mirrorRecord.dimension().identifier()).toList()),
                    mirrorRecord != null && RealmInstanceRegistry.aliases(mirrorRecord.dimension().identifier())
                            .anyMatch(alias -> alias.equals(Identifier.fromNamespaceAndPath("minecraft", "the_end"))));

            // 10. An existing realm creates nothing and reuses the dimension it names.
            RealmRecord existingRecord = openInstance(server, existing, 0, 0L, opened);
            ok &= check(source, "realm probe: existing=" + (existingRecord == null ? "-" : existingRecord.dimension().identifier()),
                    existingRecord != null && existingRecord.dimension().equals(Level.OVERWORLD)
                            && server.getLevel(existingRecord.dimension()) == overworld);

            // 11. A definition that names a template or a structure that does not exist creates nothing.
            boolean resolvable = RealmStructurePlacer.resolvable(server.getStructureManager(), absentStructure.value());
            RealmRecord templateAttempt = planned(absentTemplate, 0, 0L);
            boolean templateFailed = RealmInstanceService.open(server, templateAttempt).isEmpty();
            ok &= check(source, "realm probe: structure_resolvable=" + resolvable + " template_failed=" + templateFailed
                            + " leftover=" + RealmInstanceRegistry.at(templateAttempt.dimension()).isPresent(),
                    !resolvable && templateFailed && RealmInstanceRegistry.at(templateAttempt.dimension()).isEmpty());

            // 12. An unclaimed instance dies with its clock: the record goes and the dimension is released.
            boolean expired = mirrorRecord != null && RealmInstanceService.expire(server, mirrorRecord, server.overworld().getGameTime() + 100L);
            boolean released = mirrorRecord != null && RealmInstanceRegistry.at(mirrorRecord.dimension()).isEmpty()
                    && server.getLevel(mirrorRecord.dimension()) == null;
            ok &= check(source, "realm probe: expired=" + expired + " released=" + released, expired && released);

            // 13. A claimed realm survives its visitors: the policy is what decides keep versus delete.
            RealmRecord claimed = RealmInstanceRegistry.at(first.dimension()).orElse(null);
            RealmRecord idled = claimed == null ? null : claimed.idle();
            ok &= check(source, "realm probe: persists=" + (claimed != null && claimed.persists())
                            + " idle_members=" + (idled == null ? -1 : idled.members().size()),
                    claimed != null && claimed.persists() && idled.empty() && probe != null && idled.isOwner(probe.getUUID()));
        } finally {
            for (LivingEntity probe : probes) if (probe != null) probe.discard();
            for (ResourceKey<Level> key : opened) {
                RealmInstanceRegistry.at(key).ifPresent(record -> RealmInstanceService.destroy(server, record));
            }
        }
        if (ok) {
            source.sendSuccess(() -> Component.literal("realm probe: OK"), false);
            return 1;
        }
        source.sendFailure(Component.literal("realm probe: MISMATCH"));
        return 0;
    }

    /**
     * Leaves one claimed instance behind instead of cleaning up, so a restart can be checked for keeping it.
     * The realm probe itself destroys everything it opens.
     */
    private static int keepRealm(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        Holder<RealmInstance> trial = require(MxtResourceKeys.REALM_INSTANCE, REALM);
        RealmRecord record = RealmInstanceService.open(server, planned(trial, 0, 4242L)).orElse(null);
        if (record == null) {
            source.sendFailure(Component.literal("realm keep: could not open the instance"));
            return 0;
        }
        UUID owner = UUID.randomUUID();
        RealmInstanceRegistry.replace(record.withOwner(owner));
        source.sendSuccess(() -> Component.literal("realm keep: " + record.dimension().identifier() + " owner=" + owner), false);
        return 1;
    }

    /**
     * Reopens a dormant instance the way an entry does, to prove that a claimed realm comes back with the
     * terrain it had instead of being generated and furnished again.
     */
    private static int reopenRealm(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        Holder<RealmInstance> trial = require(MxtResourceKeys.REALM_INSTANCE, REALM);
        RealmRecord dormant = RealmInstanceRegistry.of(trial).stream()
                .filter(record -> record.index() == 0).findFirst().orElse(null);
        if (dormant == null) {
            source.sendFailure(Component.literal("realm reopen: there is no dormant instance"));
            return 0;
        }
        RealmRecord reopened = RealmInstanceService.open(server, dormant.restarted(server.overworld().getGameTime())).orElse(null);
        ServerLevel level = reopened == null ? null : server.getLevel(reopened.dimension());
        boolean kept = level != null && level.getBlockState(new BlockPos(8, 64, 8)).is(Blocks.GOLD_BLOCK);
        boolean prepared = reopened != null && reopened.prepared();
        if (kept && prepared) {
            source.sendSuccess(() -> Component.literal("realm reopen: terrain kept=" + true + " prepared=" + true), false);
            return 1;
        }
        source.sendFailure(Component.literal("realm reopen: terrain kept=" + kept + " prepared=" + prepared));
        return 0;
    }

    /**
     * Exercises the rift's linking rule, the mesh it is drawn from and its portal behaviour without a client.
     *
     * <p>Every leg drives the calls the renderer and the portal use - the point, link and triangle meshes, the
     * 3x3x3 neighbour scan, the colour derivation, the stored state, the transition - and then inspects the world
     * that came out of it. What cannot be checked from a server is the drawing itself.
     */
    private static int probeRift(CommandSourceStack source) {
        double thickness = RiftMesh.DEFAULT_THICKNESS;
        MinecraftServer server = source.getServer();
        ServerLevel level = server.overworld();
        Identifier here = level.dimension().identifier();
        BlockPos base = level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(400, 0, 400)).above(2);
        BlockPos middle = base.offset(1, 1, 0);
        List<BlockPos> touched = new ArrayList<>();
        List<BlockPos> touchedInEnd = new ArrayList<>();
        boolean ok = true;
        try {
            // A 3x3 wall of rifts. Every block of it is inside the others' 3x3x3 reach, so all nine link up.
            RiftBlockEntity centre = null;
            for (int x = 0; x < 3; x++)
                for (int y = 0; y < 3; y++) {
                    RiftBlockEntity placed = placeRift(level, base.offset(x, y, 0), here, touched);
                    if (x == 1 && y == 1) centre = placed;
                }
            if (centre == null) {
                source.sendFailure(Component.literal("rift probe: could not place the wall"));
                return 0;
            }

            // 1. Linking: the middle of the wall has all eight neighbours as links, a corner has three, and the
            //    wall is one network of nine blocks.
            int links = RiftConnections.connected(level, middle).size();
            RiftBlockEntity corner = level.getBlockEntity(base) instanceof RiftBlockEntity found ? found : null;
            int cornerLinks = corner == null ? -1 : RiftConnections.connected(level, base).size();
            int network = RiftConnections.componentSize(level, middle);
            ok &= check(source, "rift probe: links=" + links + " corner=" + cornerLinks + " network=" + network,
                    links == 8 && cornerLinks == 3 && network == 9);

            // 2. The wall's mesh: eight links, twelve triangles, every vertex within half a thickness of the
            //    plane the wall lies in, a link half reaching exactly the midpoint it meets, and a width that
            //    really is the configured thickness rather than a number of its own.
            List<BlockPos> wallLinks = RiftConnections.connected(level, middle);
            List<Loop> loops = RiftConnections.loops(middle, wallLinks);
            List<Vec3> wallMesh = new ArrayList<>(RiftMesh.node(thickness));
            for (BlockPos link : wallLinks) wallMesh.addAll(RiftMesh.linkShare(local(link, middle), thickness));
            double flatness = 0.0;
            for (Vec3 vertex : wallMesh) flatness = Math.max(flatness, Math.abs(vertex.z - 0.5));
            List<Vec3> straightLink = RiftMesh.linkShare(local(base.offset(2, 1, 0), middle), thickness);
            List<Vec3> diagonalLink = RiftMesh.linkShare(local(base.offset(2, 2, 0), middle), thickness);
            double straightReach = axialReach(straightLink, new Vec3(1.0, 0.0, 0.0));
            double diagonalReach = axialReach(diagonalLink, new Vec3(1.0, 1.0, 0.0));
            double linkWidth = radialReach(diagonalLink, new Vec3(1.0, 1.0, 0.0)) / Math.sqrt(2.0) * 2.0;
            ok &= check(source, "rift probe: mesh_links=" + wallLinks.size() + " triangles=" + loops.size()
                            + " flatness=" + "%.4f".formatted(flatness)
                            + " half_link=" + "%.4f".formatted(straightReach) + "/" + "%.4f".formatted(diagonalReach)
                            + " link_width=" + "%.4f".formatted(linkWidth),
                    wallLinks.size() == 8 && loops.size() == 12
                            && flatness <= thickness / 2.0 + 1.0E-6
                            && close(straightReach, 0.5) && close(diagonalReach, Math.sqrt(2.0) / 2.0)
                            && close(linkWidth, thickness));

            //    Counting the loops of the whole wall catches a triangle that two of its blocks both claim or
            //    neither of them draws: nine blocks see forty-eight loops, which is sixteen triangles three ways.
            int loopTotal = 0;
            for (int x = 0; x < 3; x++)
                for (int y = 0; y < 3; y++) {
                    BlockPos at = base.offset(x, y, 0);
                    if (!(level.getBlockEntity(at) instanceof RiftBlockEntity)) continue;
                    loopTotal += RiftConnections.loops(at, RiftConnections.connected(level, at)).size();
                }
            ok &= check(source, "rift probe: wall_loops=" + loopTotal + " triangles=" + (loopTotal / 3), loopTotal == 48);

            // 3. A loop is drawn by its three blocks as three shares that tile it, and each share is a slab whose
            //    two surfaces are one thickness apart. Every block of the loop agrees the loop is there, the
            //    shares add up to the whole triangle, and each of them is a third of it.
            Loop loop = loops.isEmpty() ? null : loops.getFirst();
            Vec3 forward = loop == null ? RiftMesh.CENTRE : local(loop.forward(), middle);
            Vec3 backward = loop == null ? RiftMesh.CENTRE : local(loop.backward(), middle);
            double whole = RiftMesh.area(List.of(RiftMesh.CENTRE, forward, backward));
            double first = RiftMesh.area(RiftMesh.triangleFace(RiftMesh.CENTRE, forward, backward));
            double second = RiftMesh.area(RiftMesh.triangleFace(forward, backward, RiftMesh.CENTRE));
            double third = RiftMesh.area(RiftMesh.triangleFace(backward, RiftMesh.CENTRE, forward));
            double slab = slabThickness(RiftMesh.triangleShare(RiftMesh.CENTRE, forward, backward, thickness));
            boolean agreed = loop != null
                    && agreesOnLoop(level, loop.forward(), middle, loop.backward())
                    && agreesOnLoop(level, loop.backward(), middle, loop.forward());
            ok &= check(source, "rift probe: loop_area=" + "%.4f".formatted(whole)
                            + " shares=" + "%.4f".formatted(first) + "/" + "%.4f".formatted(second) + "/" + "%.4f".formatted(third)
                            + " slab=" + "%.4f".formatted(slab) + " agreed=" + agreed,
                    agreed && close(first, whole / 3.0) && close(second, whole / 3.0) && close(third, whole / 3.0)
                            && close(first + second + third, whole) && close(slab, thickness));

            // 4. A rift with nothing next to it draws exactly one point: no links, no triangles, and the point is
            //    a cube of the configured thickness rather than a flat square.
            BlockPos lonePos = base.offset(6, 0, 0);
            RiftBlockEntity lone = placeRift(level, lonePos, here, touched);
            List<BlockPos> loneLinks = lone == null ? List.of() : RiftConnections.connected(level, lonePos);
            List<Vec3> point = RiftMesh.node(thickness);
            double pointSide = cubeSide(point);
            boolean loneAlone = lone != null && RiftConnections.isolated(level, lonePos);
            ok &= check(source, "rift probe: lone_alone=" + loneAlone + " links=" + loneLinks.size()
                            + " triangles=" + RiftConnections.loops(lonePos, loneLinks).size()
                            + " point_vertices=" + point.size() + " point_side=" + "%.4f".formatted(pointSide),
                    loneAlone && loneLinks.isEmpty() && RiftConnections.loops(lonePos, loneLinks).isEmpty()
                            && point.size() == 24 && close(pointSide, thickness));

            // 5. Linking is decided by position alone, and reaches the whole 3x3x3: a rift one layer up links to
            //    all nine wall blocks and to the one placed beside the wall, whichever target each of them has.
            RiftBlockEntity layer = placeRift(level, base.offset(1, 1, 1), Level.NETHER.identifier(), touched);
            RiftBlockEntity beside = placeRift(level, base.offset(0, 0, 1), here, touched);
            int layerLinks = layer == null ? -1 : RiftConnections.connected(level, base.offset(1, 1, 1)).size();
            int besideLinks = beside == null ? -1 : RiftConnections.connected(level, base.offset(0, 0, 1)).size();
            int cornerWithExtras = corner == null ? -1 : RiftConnections.connected(level, base).size();
            boolean reached = layer != null && beside != null
                    && !RiftConnections.isolated(level, base.offset(1, 1, 1));
            ok &= check(source, "rift probe: layer_links=" + layerLinks + " beside_links=" + besideLinks
                            + " corner_links=" + cornerWithExtras + " reached=" + reached,
                    layerLinks == 10 && besideLinks == 5 && cornerWithExtras == 5 && reached);
            level.setBlockAndUpdate(base.offset(1, 1, 1), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(base.offset(0, 0, 1), Blocks.AIR.defaultBlockState());

            // 6. Colour follows the target dimension unless it is overridden, and the derivation is stable.
            Identifier end = Level.END.identifier();
            int derived = RiftColors.forDimension(end);
            lone.setTarget(end);
            int resolvedAutomatic = RiftColors.resolve(lone);
            lone.setColor(0xFF12AB34);
            int resolvedOverride = RiftColors.resolve(lone);
            ok &= check(source, "rift probe: colour_stable=" + (derived == RiftColors.forDimension(end))
                            + " distinct=" + (derived != RiftColors.forDimension(Level.NETHER.identifier()))
                            + " automatic=" + (resolvedAutomatic == derived)
                            + " override=" + RiftColors.format(resolvedOverride),
                    derived == RiftColors.forDimension(end) && derived != RiftColors.forDimension(Level.NETHER.identifier())
                            && resolvedAutomatic == derived && resolvedOverride == 0xFF12AB34);

            // 7. Destination and colour override survive a save and load, and nothing else is stored: a rift has
            //    no direction and no opacity to lose. The metadata-carrying form is used because a static load
            //    has to read the block entity id back out of the tag.
            CompoundTag saved = lone.saveWithFullMetadata(level.registryAccess());
            BlockEntity reloaded = BlockEntity.loadStatic(lonePos, level.getBlockState(lonePos), saved, level.registryAccess());
            boolean persisted = reloaded instanceof RiftBlockEntity copy
                    && copy.target().equals(end)
                    && copy.color() == 0xFF12AB34;
            ok &= check(source, "rift probe: persisted=" + persisted, persisted);

            // 8. An arrival with no rift to land at carves one that leads back, and a second arrival reuses it
            //    rather than stacking a new rift next to the first.
            ServerLevel end_ = server.getLevel(Level.END);
            BlockPos openSky = new BlockPos(0, 200, 0);
            Vec3 firstArrival = end_ == null ? Vec3.ZERO : RiftTeleportService.arrival(end_, level.dimension(), openSky);
            BlockPos carved = end_ == null ? null : findRiftNear(end_, BlockPos.containing(firstArrival), here);
            Vec3 secondArrival = end_ == null ? Vec3.ZERO : RiftTeleportService.arrival(end_, level.dimension(), openSky);
            boolean reused = carved != null && firstArrival.equals(secondArrival);
            if (carved != null) touchedInEnd.add(carved);
            ok &= check(source, "rift probe: carved_return=" + (carved != null) + " reused=" + reused,
                    carved != null && reused);

            // 9. The portal itself reports a transition into the configured dimension.
            centre.setTarget(end);
            Entity probe = EntityType.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
            TeleportTransition transition = MxtBlocks.RIFT.get().getPortalDestination(level, probe, middle);
            boolean leadsToEnd = transition != null && transition.newLevel().dimension().equals(Level.END);
            ok &= check(source, "rift probe: transition=" + (transition == null ? "-"
                            : transition.newLevel().dimension().identifier() + "@" + "%.1f".formatted(transition.position().y)),
                    leadsToEnd);
            probe.discard();

            // 10. Linking is read from the world each time: removing one block drops it out of the network.
            level.setBlockAndUpdate(base, Blocks.AIR.defaultBlockState());
            int afterRemoval = RiftConnections.connected(level, middle).size();
            ok &= check(source, "rift probe: after_removal=" + afterRemoval, afterRemoval == 7);
        } finally {
            for (BlockPos pos : touched) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            ServerLevel end_ = server.getLevel(Level.END);
            if (end_ != null) for (BlockPos pos : touchedInEnd) end_.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        if (ok) {
            source.sendSuccess(() -> Component.literal("rift probe: OK"), false);
            return 1;
        }
        source.sendFailure(Component.literal("rift probe: MISMATCH"));
        return 0;
    }

    private static RiftBlockEntity placeRift(ServerLevel level, BlockPos pos, Identifier target,
                                             List<BlockPos> touched) {
        level.setBlockAndUpdate(pos, MxtBlocks.RIFT.get().defaultBlockState());
        touched.add(pos.immutable());
        if (level.getBlockEntity(pos) instanceof RiftBlockEntity rift) {
            rift.configure(target, RiftColors.AUTO);
            return rift;
        }
        return null;
    }

    /**
     * The closest rift leading to {@code target} within a small box, which is how the arrival service's own
     * "carve one and then find it again" behaviour can be observed from the outside.
     */
    @Nullable
    private static BlockPos findRiftNear(ServerLevel level, BlockPos around, Identifier target) {
        BlockPos min = around.offset(-3, -3, -3);
        BlockPos max = around.offset(3, 3, 3);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof RiftBlockEntity rift && rift.target().equals(target))
                return pos.immutable();
        }
        return null;
    }

    /**
     * A neighbour's centre in {@code self}'s block-local coordinates, which is where the renderer places the
     * links it draws: a neighbour is at most one block away on each axis.
     */
    private static Vec3 local(BlockPos other, BlockPos self) {
        return RiftMesh.CENTRE.add(other.getX() - self.getX(), other.getY() - self.getY(), other.getZ() - self.getZ());
    }

    /**
     * The side of a vertex list that is a cube, or {@code -1} when its three extents are not all the same, which
     * is how a mesh claiming to be a point is caught being a flat square instead.
     */
    private static double cubeSide(List<Vec3> vertices) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3 vertex : vertices) {
            minX = Math.min(minX, vertex.x);
            minY = Math.min(minY, vertex.y);
            minZ = Math.min(minZ, vertex.z);
            maxX = Math.max(maxX, vertex.x);
            maxY = Math.max(maxY, vertex.y);
            maxZ = Math.max(maxZ, vertex.z);
        }
        double width = maxX - minX;
        double height = maxY - minY;
        double depth = maxZ - minZ;
        return close(width, height) && close(height, depth) ? width : -1.0;
    }

    /**
     * How far a mesh reaches along an axis, measured from the rift's own centre. A link half must reach exactly
     * as far as the midpoint it meets its neighbour's half at.
     */
    private static double axialReach(List<Vec3> vertices, Vec3 axis) {
        Vec3 unit = axis.normalize();
        double reach = 0.0;
        for (Vec3 vertex : vertices) reach = Math.max(reach, vertex.subtract(RiftMesh.CENTRE).dot(unit));
        return reach;
    }

    /**
     * How far a mesh reaches away from an axis, measured from the rift's own centre. For a beam that is the
     * corner of its cross-section, so it checks the thickness the link claims to have.
     */
    private static double radialReach(List<Vec3> vertices, Vec3 axis) {
        Vec3 unit = axis.normalize();
        double reach = 0.0;
        for (Vec3 vertex : vertices) {
            Vec3 relative = vertex.subtract(RiftMesh.CENTRE);
            reach = Math.max(reach, relative.subtract(unit.scale(relative.dot(unit))).length());
        }
        return reach;
    }

    /**
     * Whether the block at {@code partner} also sees the loop through {@code self} and {@code other}. All three
     * blocks of a loop have to draw their own share of it, so two of them agreeing would leave a gap and this is
     * how a disagreement shows up.
     */
    private static boolean agreesOnLoop(ServerLevel level, BlockPos partner, BlockPos self, BlockPos other) {
        if (!(level.getBlockEntity(partner) instanceof RiftBlockEntity)) return false;
        for (Loop candidate : RiftConnections.loops(partner, RiftConnections.connected(level, partner)))
            if (candidate.forward().equals(self) && candidate.backward().equals(other)
                    || candidate.forward().equals(other) && candidate.backward().equals(self)) return true;
        return false;
    }

    /**
     * The distance between the two surfaces of a filled triangle share, which is the thickness the fill claims
     * to have. A share is laid out top face, bottom face and then the rims, so a surface vertex and the one four
     * places after it are the same corner of the triangle above and below.
     */
    private static double slabThickness(List<Vec3> share) {
        if (share.size() < 8) return -1.0;
        return share.get(0).distanceTo(share.get(4));
    }

    private static boolean check(CommandSourceStack source, String message, boolean value) {
        if (value) {
            source.sendSuccess(() -> Component.literal(message + " OK"), false);
            return true;
        }
        source.sendFailure(Component.literal(message + " MISMATCH"));
        return false;
    }

    private static RealmRecord planned(Holder<RealmInstance> definition, int index, long seed) {
        return RealmInstanceService.plan(definition, index, seed, 0L);
    }

    private static RealmRecord openInstance(MinecraftServer server, Holder<RealmInstance> definition, int index,
                                            long seed, List<ResourceKey<Level>> opened) {
        RealmRecord record = RealmInstanceService.open(server, planned(definition, index, seed)).orElse(null);
        if (record != null) opened.add(record.dimension());
        return record;
    }

    private static int giveKit(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        CultivationAttachment spirit = player.getData(MxtAttachments.CULTIVATION);
        SpiritIdentityAttachment identity = player.getData(MxtAttachments.SPIRIT_IDENTITY);
        ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
        FormulaContext context = FormulaContext.of(player);

        grantIdentity(player, identity, context);
        if (!CultivationService.setRealm(spirit, QI_REFINING)
                || !CultivationService.setRealm(spirit, SPIRIT_POWER_REFINING)) {
            source.sendFailure(Component.translatable("command.mxt_test.kit.realm_failed"));
            return 0;
        }
        spirit.setCultivationProgress(requireProfile(QI), 80.0D);
        ensureResource(player, resources, require(MxtResourceKeys.RESOURCE, QI), 80.0D);
        ensureResource(player, resources, require(MxtResourceKeys.RESOURCE, SPIRIT_POWER), 80.0D);
        ensureResource(player, resources, require(MxtResourceKeys.RESOURCE, WATER_POWER), 80.0D);
        ensureResource(player, resources, require(MxtResourceKeys.RESOURCE, SOUL_POWER), 20.0D);

        // These attachments are mutable; explicit sync is handled by the attachment dispatcher.
        player.setData(MxtAttachments.RESOURCE_HOLDER, resources);
        player.setData(MxtAttachments.ABILITY_HOLDER, player.getData(MxtAttachments.ABILITY_HOLDER));

        give(player, new ItemStack(MxtTestItems.QINGXIAO_SPIRIT_CRYSTAL.get(), 8));
        give(player, new ItemStack(MxtItems.SPIRIT_STONE.get(), 3));
        give(player, new ItemStack(Items.DIAMOND_SWORD));
        give(player, new ItemStack(Items.HONEY_BOTTLE, 2));
        give(player, new ItemStack(Items.APPLE));
        give(player, formationPlate());
        give(player, realmToken());
        give(player, contractScroll());
        // The artifact fixtures, handed over already bound: nothing in the test pack refines an item yet, and an
        // unowned artifact refuses flight, storage and mxt:owned_by.
        giveArtifact(player, Items.IRON_SWORD);
        giveArtifact(player, Items.AMETHYST_SHARD);
        giveArtifact(player, Items.PRISMARINE_SHARD);
        giveArtifact(player, Items.BELL);
        source.sendSuccess(() -> Component.translatable("command.mxt_test.kit.success"), true);
        return 1;
    }

    private static void grantIdentity(ServerPlayer player, SpiritIdentityAttachment spirit, FormulaContext context) {
        HolderLookup<SpiritRoot> root = new HolderLookup<>(MxtResourceKeys.SPIRIT_ROOT, ROOT);
        HolderLookup<SpiritRoot> waterRoot = new HolderLookup<>(MxtResourceKeys.SPIRIT_ROOT, WATER_ROOT);
        HolderLookup<Physique> physique = new HolderLookup<>(MxtResourceKeys.PHYSIQUE, PHYSIQUE);
        HolderLookup<Technique> technique = new HolderLookup<>(MxtResourceKeys.TECHNIQUE, TECHNIQUE);
        CultivationIdentityService.grantSpiritRoot(player, ROOT, root.value());
        CultivationIdentityService.grantSpiritRoot(player, WATER_ROOT, waterRoot.value());
        CultivationIdentityService.grantPhysique(player, PHYSIQUE, physique.value(), context);
        TechniqueService.learn(player, spirit, technique.holder(), context);
        spirit.addLearnedTechnique(technique.holder());
        CultivationGrantService.recalculate(spirit, player.getData(MxtAttachments.ABILITY_HOLDER));
        TEST_ACTIVE_ABILITIES.forEach(id -> MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, id)
                .ifPresent(ability -> player.getData(MxtAttachments.ABILITY_HOLDER).grant(ability, TEST_ABILITY_SOURCE)));
        // Granting an ability does not register its triggers by itself; the runtime index is only rebuilt
        // where the ability sources actually change.
        AbilityEventBridge.rebuildTriggerSubscriptions(player);
    }

    private static int startCultivation(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        CultivateAction action = require(MxtResourceKeys.CULTIVATE_ACTION, CULTIVATE).value();
        Result result = CultivationActionService.start(player, player.getData(MxtAttachments.CULTIVATION),
                CULTIVATE, action, player.level().getGameTime(), FormulaContext.of(player));
        if (!result.started()) {
            source.sendFailure(Component.translatable("command.mxt_test.cultivate.failed", result.failure().name()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt_test.cultivate.success"), true);
        return 1;
    }

    /**
     * Prints the character panel's own line model, so what the panel would show can be read from the server
     * instead of a screenshot. It calls the very same {@code InformationManager.collectEntries} the screen does.
     */
    private static int showInformation(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        for (Side side : Side.values()) {
            List<InformationEntry> entries = InformationManager.collectEntries(player, side);
            source.sendSuccess(() -> Component.literal("[" + side + "] " + entries.size() + " entries"), false);
            for (InformationEntry entry : entries) {
                String name = entry.name() == null ? "" : entry.name().getString();
                String value = entry.value().getString();
                source.sendSuccess(() -> Component.literal("- " + name + " = " + value), false);
            }
        }
        return 1;
    }

    private static int showGuide(CommandSourceStack source) {
        if (player(source) == null) return 0;
        source.sendSuccess(() -> Component.translatable("command.mxt_test.guide.1"), false);
        source.sendSuccess(() -> Component.translatable("command.mxt_test.guide.2"), false);
        source.sendSuccess(() -> Component.translatable("command.mxt_test.guide.3"), false);
        source.sendSuccess(() -> Component.translatable("command.mxt_test.guide.4"), false);
        return 1;
    }

    private static void ensureResource(ServerPlayer player, ResourceHolderAttachment resources, Holder<Resource> resource, double minimum) {
        FormulaContext context = ResourceService.formulaContext(player, resource, FormulaContext.of(player));
        ResourceService.initialize(resources, resource, context);
        double missing = minimum - resources.get(resource);
        if (missing > 0.0D) ResourceService.change(resources, resource, missing, context);
    }

    private static ItemStack formationPlate() {
        ItemStack stack = new ItemStack(MxtItems.FORMATION_PLATE.get());
        // An empty allow list, which is the shipped plate: unrestricted unless the server option says
        // otherwise, and therefore bound to whatever the kit's own formation is.
        stack.set(MxtDataComponents.FORMATION_PLATE,
                new FormationPlateComponent(List.of(), Optional.of(require(MxtResourceKeys.FORMATION, FORMATION))));
        return stack;
    }

    private static ItemStack realmToken() {
        ItemStack stack = new ItemStack(MxtItems.REALM_TOKEN.get());
        stack.set(MxtDataComponents.REALM_TOKEN, new RealmTokenComponent(Optional.of(require(MxtResourceKeys.REALM_INSTANCE, REALM))));
        return stack;
    }

    private static ItemStack contractScroll() {
        ItemStack stack = new ItemStack(MxtItems.CONTRACT_SCROLL.get());
        stack.set(MxtDataComponents.CONTRACT_SCROLL, new ContractScrollComponent(Optional.of(require(MxtResourceKeys.CONTRACT_TYPE, CONTRACT))));
        return stack;
    }

    /** Gives one artifact fixture bound to the player, since the test pack has no other way to refine an item. */
    private static void giveArtifact(ServerPlayer player, Item item) {
        ItemStack stack = new ItemStack(item);
        ArtifactService.refine(stack, player);
        give(player, stack);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().placeItemBackInInventory(stack);
    }

    private static ServerPlayer player(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) source.sendFailure(Component.translatable("command.mxt.requires_player"));
        return player;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, path);
    }

    private static <T> Holder<T> require(ResourceKey<? extends Registry<T>> registry,
                                         Identifier id) {
        return MxtDatapackRegistries.holder(registry, id)
                .orElseThrow(() -> new IllegalStateException("Missing Qingxiao test definition " + id));
    }

    private static Reference<Aura> requireProfile(Identifier resource) {
        return AuraLookup.holderServer(resource)
                .orElseThrow(() -> new IllegalStateException("Missing Qingxiao cultivation profile " + resource));
    }

    private record HolderLookup<T>(ResourceKey<? extends Registry<T>> registry, Identifier id) {
        private Holder<T> holder() {
            return require(this.registry, this.id);
        }

        private T value() {
            return this.holder().value();
        }
    }
}
