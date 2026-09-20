package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraRequirement;
import com.iafenvoy.mxt.event.AbilityUseEvent.Pre;
import com.iafenvoy.mxt.registry.*;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.condition.builtin.entity.AuraElementEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.ElementAttachmentEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.HasElementEntityCondition;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.data.item.ContractScrollComponent;
import com.iafenvoy.mxt.data.item.FormationPlateComponent;
import com.iafenvoy.mxt.data.item.RealmTokenComponent;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
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
import com.iafenvoy.mxt.screen.information.InformationCollector.InformationEntry;
import com.iafenvoy.mxt.screen.information.InformationManager;
import com.iafenvoy.mxt.screen.information.InformationManager.Side;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.datafixers.util.Either;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private static final Identifier PROBE_FIRE_ELEMENT = id("fire");
    private static final Identifier PROBE_WATER_ELEMENT = id("water");
    private static final Identifier PROBE_INERT_ELEMENT = id("inert");
    private static final Identifier PROBE_ELEMENT_ABILITY = id("elemental_probe");
    private static final Identifier PROBE_ELEMENT_TAG = id("basic");
    private static final Identifier PROBE_TECHNIQUE = id("sword_manual");
    private static final Identifier PROBE_TECHNIQUE_STAGE = id("sword_art_2");
    private static final Identifier PROBE_ABILITY = id("artifact_guard");
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
        try {
            if (attacker == null || defender == null || masteryDefender == null || foreignDefender == null) {
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

            if (elements && mastery && foreign) {
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
        }
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
        Holder<DamageType> magic = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.MAGIC);
        LivingEntity rootless = spawnProbe(level, origin.above(), null);
        LivingEntity waterVictim = spawnProbe(level, origin.above(1), PROBE_WATER_ROOT);
        LivingEntity waterCaster = spawnProbe(level, origin.above(2), PROBE_WATER_ROOT);
        LivingEntity declaredVictim = spawnProbe(level, origin.above(3), PROBE_WATER_ROOT);
        LivingEntity inertHolder = spawnProbe(level, origin.above(4), PROBE_INERT_ROOT);
        LivingEntity reactionVictim = spawnProbe(level, origin.above(5), PROBE_WATER_ROOT);
        LivingEntity toggleProbe = spawnProbe(level, origin.above(6), PROBE_FIRE_ROOT);
        try {
            if (rootless == null || waterVictim == null || waterCaster == null || declaredVictim == null
                    || inertHolder == null || reactionVictim == null || toggleProbe == null) {
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

            if (claimed && declared && reaction && disabled && hasElement && auraElement && attachment && toggle) {
                source.sendSuccess(() -> Component.literal("element probe: OK"), false);
                return 1;
            }
            source.sendFailure(Component.literal("element probe: MISMATCH"));
            return 0;
        } finally {
            for (LivingEntity probe : List.of(rootless, waterVictim, waterCaster, declaredVictim, inertHolder,
                    reactionVictim, toggleProbe))
                if (probe != null) probe.discard();
        }
    }

    private static TagKey<Element> elementTag() {
        return TagKey.create(MxtResourceKeys.ELEMENT, PROBE_ELEMENT_TAG);
    }

    private static AuraRequirement minimumRange(double minimum) {
        return new AuraRequirement(new Constant(minimum), new Constant(1.0E9D));
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
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
