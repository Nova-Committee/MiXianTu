package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.attachment.FriendAttachment.AddResult;
import com.iafenvoy.mxt.attachment.FriendAttachment.RemoveResult;
import com.iafenvoy.mxt.compat.ftb.FtbTeamsCompat;
import com.iafenvoy.mxt.config.MxtServerConfig.ClaimLinkage;
import com.iafenvoy.mxt.data.Formation.RequiredBlock;
import com.iafenvoy.mxt.data.Formation.Storage;
import com.iafenvoy.mxt.data.aura.AuraMaximum.Fixed;
import com.iafenvoy.mxt.data.aura.AuraMaximum.InitialMultiplier;
import com.iafenvoy.mxt.data.aura.AuraMaximum.Unlimited;
import com.iafenvoy.mxt.data.aura.AuraZone.Distribution;
import com.iafenvoy.mxt.data.formation.BuffFormationAction.TargetMode;
import com.iafenvoy.mxt.data.formation.RangeDisplayFormationAction.Shape;
import com.iafenvoy.mxt.data.item.FormationPlateComponent.Allowed.Id;
import com.iafenvoy.mxt.data.item.FormationPlateComponent.Allowed.Tag;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext.Layout;
import com.iafenvoy.mxt.event.AuraZoneEvent;
import com.iafenvoy.mxt.event.FormationEvent.Tick;
import com.iafenvoy.mxt.event.FormationEvent.TickEffects;
import com.iafenvoy.mxt.event.FormationEvent.UpkeepFailed;
import com.iafenvoy.mxt.event.FriendEvent.Relation;
import com.iafenvoy.mxt.registry.*;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueService.Result;
import com.iafenvoy.mxt.runtime.formation.FormationCenters.Match;
import com.iafenvoy.mxt.runtime.formation.FormationProtection.Action;
import com.iafenvoy.mxt.runtime.formation.FormationService.MaintainRule;
import com.iafenvoy.mxt.runtime.formation.FormationService.MaintainRule.PaymentPlan;
import com.iafenvoy.mxt.runtime.world.AuraQueryCache.AuraLocation;
import com.iafenvoy.mxt.runtime.world.FormationAbsorption.Sources;
import com.mojang.authlib.GameProfile;
import net.minecraft.util.ProblemReporter.Collector;
import net.neoforged.neoforge.common.util.FakePlayer;
import java.util.UUID;

import com.iafenvoy.mxt.command.FormationCommand;
import com.iafenvoy.mxt.command.TechniqueCommand;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.aura.BlockAura;
import com.iafenvoy.mxt.data.aura.ItemAura;
import com.iafenvoy.mxt.data.aura.ItemAuraComponent;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.data.aura.AuraValue;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.condition.AlwaysTrueCondition;
import com.iafenvoy.mxt.data.trigger.TriggerContext;
import com.iafenvoy.mxt.data.trigger.TriggerSignals;
import com.iafenvoy.mxt.data.condition.builtin.entity.AuraRangeEntityCondition;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resource.ResourceBar;
import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.attachment.FriendAttachment;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.data.badge.Badge;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.data.cultivation.CultivationProfile;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.SkillStage;
import com.iafenvoy.mxt.data.action.builtin.entity.GrantSpiritRootAction;
import com.iafenvoy.mxt.data.action.builtin.entity.GrantPhysiqueAction;
import com.iafenvoy.mxt.data.action.builtin.entity.RemovePhysiqueAction;
import com.iafenvoy.mxt.data.action.builtin.entity.RemoveSpiritRootAction;
import com.iafenvoy.mxt.data.condition.builtin.entity.HasPhysiqueEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.HasSpiritRootEntityCondition;import com.iafenvoy.mxt.data.item.WeaponBinding;
import com.iafenvoy.mxt.data.item.FormationPlateComponent;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.ItemQualityTags;
import com.iafenvoy.mxt.data.artifact.ForgingResultComponent;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMaterial;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueProgress.Mode;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueService.Failure;
import com.iafenvoy.mxt.runtime.forging.ForgingPlan;
import com.iafenvoy.mxt.runtime.forging.ForgingSession;
import com.iafenvoy.mxt.runtime.forging.ForgingTableState;
import com.iafenvoy.mxt.screen.information.InformationHelper;
import com.iafenvoy.mxt.screen.information.InformationHelper.Columns;
import com.iafenvoy.mxt.screen.menu.ForgingMenu;
import com.iafenvoy.mxt.screen.menu.ForgingMenuProbe;
import com.iafenvoy.mxt.data.resource.ResourceBar.Anchor;
import com.iafenvoy.mxt.data.resource.ResourceBar.ValueDisplay;
import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.OriginsRenderData;
import com.iafenvoy.mxt.data.resourcebar.builtin.context.ActualConcentrationContext;
import com.iafenvoy.mxt.data.resourcebar.builtin.visibility.NonZeroVisibility;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.NoOpAction;
import com.iafenvoy.mxt.data.action.builtin.entity.meta.IfElseAction;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.FormationAllyEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.FormationMemberEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.FormationOwnerEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.meta.NotEntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.formation.AttackFormationAction;
import com.iafenvoy.mxt.data.formation.BuffFormationAction;
import com.iafenvoy.mxt.data.formation.EmptyFormationAction;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.formation.ProtectionFormationAction;
import com.iafenvoy.mxt.data.formation.RangeDisplayFormationAction;
import com.iafenvoy.mxt.runtime.formation.FormationCarrier;
import com.iafenvoy.mxt.item.FormationPlateItem;
import com.iafenvoy.mxt.runtime.formation.FormationActionRunner;
import com.iafenvoy.mxt.runtime.formation.FormationCenters;
import com.iafenvoy.mxt.runtime.formation.FormationInstance;
import com.iafenvoy.mxt.runtime.formation.FormationProtection;
import com.iafenvoy.mxt.runtime.formation.FormationRangeDisplay;
import com.iafenvoy.mxt.runtime.formation.FormationRelations;
import com.iafenvoy.mxt.runtime.world.AuraChunkTicker;
import com.iafenvoy.mxt.runtime.world.BlockAuraService;
import com.iafenvoy.mxt.runtime.world.FormationAbsorption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import com.iafenvoy.mxt.runtime.formation.FormationSources;
import com.iafenvoy.mxt.runtime.formation.FormationWorldAttachment;
import com.iafenvoy.mxt.runtime.formation.FormationWorldService;
import com.iafenvoy.mxt.runtime.formation.FormationWorldTicker;
import com.iafenvoy.mxt.runtime.formation.FormationStructureValidator;
import com.iafenvoy.mxt.runtime.forging.ForgingProbe;
import com.iafenvoy.mxt.runtime.forging.ForgingSurface;
import com.iafenvoy.mxt.runtime.forging.ForgingWorkstationService;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbService;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.economy.CurrencyValueService;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationModeService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationProfiles;
import com.iafenvoy.mxt.runtime.cultivation.SkillStageService;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueMasteryService;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueProgress;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueHoldLookup;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueItemService;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueService;
import com.iafenvoy.mxt.runtime.cultivation.AuraDistributionService;
import com.iafenvoy.mxt.runtime.cultivation.ItemAuraService;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.AuraQueryCache;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraResult.SourceKind;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.runtime.world.AuraZonePriorityProbe;
import com.iafenvoy.mxt.runtime.world.BlockAuraContribution;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resource.ResourceService.Bounds;
import com.iafenvoy.mxt.runtime.spirit.SpiritItemAccess;
import com.iafenvoy.mxt.runtime.friend.FriendCache;
import com.iafenvoy.mxt.runtime.friend.FriendService;
import com.iafenvoy.mxt.runtime.trigger.TriggerDispatcher;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.InventoryUtil;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.util.formula.number.ContextVariable;
import com.iafenvoy.mxt.util.formula.number.Expression;
import com.iafenvoy.mxt.util.formula.number.WeightedList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Development-only mod that contributes the mxt_test datapack and client resources. */
@Mod(MxtTestMod.MOD_ID)
public final class MxtTestMod {
    public static final String MOD_ID = "mxt_test";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean suppressFormationTick;
    private static boolean suppressFormationUpkeepFailure;
    private static boolean refusingFormationAuraOverride;
    private static Entity friendVerdictTarget;
    private static TriState friendVerdict = TriState.DEFAULT;

    public MxtTestMod(IEventBus modBus) {
        MxtTestItems.REGISTRY.register(modBus);
        MxtTestForgeItems.REGISTRY.register(modBus);
        MxtTestTechniqueItems.REGISTRY.register(modBus);
        NeoForge.EVENT_BUS.addListener(MxtTestMod::verifyItemBindings);
        NeoForge.EVENT_BUS.addListener(MxtTestMod::grantTestAbilities);
        NeoForge.EVENT_BUS.addListener(MxtTestMod::cancelFormationTick);
        NeoForge.EVENT_BUS.addListener(MxtTestMod::cancelFormationUpkeepFailure);
        NeoForge.EVENT_BUS.addListener(MxtTestMod::refuseFormationAuraOverride);
        NeoForge.EVENT_BUS.addListener(MxtTestMod::overrideFriendVerdict);
        NeoForge.EVENT_BUS.addListener(MxtTestCommands::registerCommands);
        LOGGER.info("Loaded MiXianTu test mod");
    }

    /**
     * Suppresses one period's work, to prove a suppressed period still records the upkeep it charged.
     * Cancels {@code TickEffects}: {@code Tick} is the settled observer and cannot be cancelled.
     */
    private static void cancelFormationTick(TickEffects event) {
        if (suppressFormationTick) event.setCanceled(true);
    }

    /**
     * Lets the formation audit let a formation stand through a period it cannot pay for.
     */
    private static void cancelFormationUpkeepFailure(UpkeepFailed event) {
        if (suppressFormationUpkeepFailure) event.setCanceled(true);
    }

    /**
     * Lets the formation aura audit prove that a formation's override goes through the cancellable event
     * instead of around it.
     */
    private static void refuseFormationAuraOverride(AuraZoneEvent.Override event) {
        if (refusingFormationAuraOverride) event.setCanceled(true);
    }

    /**
     * Gives a verdict of its own, the only way to assert that the event and not the friend list decides.
     * Scoped to the audit's own throwaway candidate, so nothing else in the run can be answered by it.
     */
    private static void overrideFriendVerdict(Relation event) {
        if (event.candidate() == friendVerdictTarget && friendVerdict != TriState.DEFAULT)
            event.setResult(friendVerdict);
    }

    private static void grantTestAbilities(PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AbilityAttachment holder = player.getData(MxtAttachments.ABILITY_HOLDER);
        Identifier source = Identifier.fromNamespaceAndPath(MOD_ID, "hotbar_test");
        for (String id : List.of("firebolt", "water_shield", "infuse_true_essence", "awaken_divine_sense")) {
            MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, Identifier.fromNamespaceAndPath(MOD_ID, id))
                    .ifPresent(ability -> holder.grant(ability, source));
        }
        AbilityEventBridge.rebuildTriggerSubscriptions(player);
    }

    private static void verifyItemBindings(ServerStartedEvent event) {
        verifyRecursiveDefinitionDiagnostics();
        ServerCache cache = ServerCache.get().orElseThrow(() -> new IllegalStateException("Server cache was not created"));
        Identifier foundation = Identifier.parse("mxt_test:foundation");
        Identifier coreForming = Identifier.parse("mxt_test:core_forming");
        Identifier tribulation = Identifier.parse("mxt_test:tribulation");
        if (!cache.isRealmAtLeast(coreForming, foundation) || cache.isRealmAtLeast(foundation, coreForming)
                || !cache.isRealmAtLeast(tribulation, foundation)
                || cache.rankForRealm(tribulation).filter(rank -> rank == 8).isEmpty()) {
            throw new IllegalStateException("Realm cache did not preserve linear realm ordering");
        }
        verifyDynamicResourceValues(event.getServer().registryAccess(), foundation, coreForming);
        verifyTechniqueRefusals(event);
        verifyFormulaDiagnostics();
        verifyItemQualities(event);
        ItemStack weapon = new ItemStack(Items.DIAMOND_SWORD);
        WeaponBinding weaponBinding = ItemBindingService.weapon(weapon)
                .orElseThrow(() -> new IllegalStateException("Weapon binding did not resolve its existing item"));
        if (!same(weaponBinding.attackDamage().evaluate(FormulaContext.EMPTY), 6.0D)
                || !same(weaponBinding.attackSpeed().evaluate(FormulaContext.EMPTY), -2.4D)
                || weaponBinding.conditions().stream().anyMatch(condition -> condition.description().isPresent())) {
            throw new IllegalStateException("Weapon binding did not retain its combat values on both registry sides");
        }
        ItemStack pill = new ItemStack(Items.HONEY_BOTTLE);
        if (ItemBindingService.pill(pill).filter(value -> same(value.toxicityGain().evaluate(FormulaContext.EMPTY), 25.0D)
                        && value.conditions().stream().anyMatch(condition -> condition.description().isPresent())).isEmpty()) {
            throw new IllegalStateException("Pill binding did not resolve its existing item");
        }
        ItemStack root = new ItemStack(Items.APPLE);
        if (ItemBindingService.actions(root).stream().noneMatch(GrantSpiritRootAction.class::isInstance)
                || ItemBindingService.resolve(event.getServer().registryAccess(), root).item()
                .filter(value -> value.conditions().stream().anyMatch(condition -> condition.description().isPresent()))
                .isEmpty()) {
            throw new IllegalStateException("Generic item binding did not resolve its grant-spirit-root action");
        }
        ItemStack physiqueGiver = new ItemStack(Items.MELON_SLICE);
        ItemStack physiqueRemover = new ItemStack(Items.BREAD);
        ItemStack rootRemover = new ItemStack(Items.COOKIE);
        if (ItemBindingService.actions(physiqueGiver).stream().noneMatch(GrantPhysiqueAction.class::isInstance)
                || ItemBindingService.actions(physiqueRemover).stream().noneMatch(RemovePhysiqueAction.class::isInstance)
                || ItemBindingService.actions(rootRemover).stream().noneMatch(RemoveSpiritRootAction.class::isInstance)) {
            throw new IllegalStateException("Physique or spirit-root grant/remove item bindings were not loaded");
        }
        Physique blazing = MxtDatapackRegistries.get(MxtResourceKeys.PHYSIQUE, Identifier.parse("mxt_test:blazing_body"))
                .orElseThrow(() -> new IllegalStateException("Physique holder-condition test definition was not loaded"));
        Physique swordMaster = MxtDatapackRegistries.get(MxtResourceKeys.PHYSIQUE, Identifier.parse("mxt_test:sword_master_body"))
                .orElseThrow(() -> new IllegalStateException("Physique stacking-condition test definition was not loaded"));
        if (!(blazing.holderCondition() instanceof HasSpiritRootEntityCondition)
                || !(swordMaster.holderCondition() instanceof HasPhysiqueEntityCondition)) {
            throw new IllegalStateException("Physique holder conditions did not decode their identity predicates");
        }
        ItemStack lockedCarrot = new ItemStack(Items.CARROT);
        if (ItemBindingService.resolve(event.getServer().registryAccess(), lockedCarrot).item()
                .filter(value -> value.conditions().stream().anyMatch(condition -> condition.description().isPresent()
                        && !condition.value().test(null, FormulaContext.EMPTY)))
                .isEmpty()) {
            throw new IllegalStateException("Unsatisfied item condition test binding was not loaded");
        }
        ItemStack jadeSlip = new ItemStack(MxtItems.CULTIVATION_JADE_SLIP.get());
        TechniqueBinding techniqueBinding = ItemBindingService.technique(jadeSlip)
                .orElseThrow(() -> new IllegalStateException("Technique binding did not resolve its existing item"));
        if (!HolderHelper.id(techniqueBinding.technique()).equals(Identifier.parse("mxt_test:qingxiao_breathing_manual"))
                || techniqueBinding.conditions().stream().anyMatch(condition -> condition.description().isPresent())) {
            throw new IllegalStateException("Technique binding did not retain its technique item configuration");
        }
        CultivationTechnique swordManual = MxtDatapackRegistries
                .get(MxtResourceKeys.CULTIVATION_TECHNIQUE, Identifier.parse("mxt_test:sword_manual"))
                .orElseThrow(() -> new IllegalStateException("Skill-stage test technique was not loaded"));
        SkillStage firstStage = swordManual.defaultStage().map(Holder::value)
                .orElseThrow(() -> new IllegalStateException("Technique default_stage was not decoded"));
        if (!firstStage.skill().equals(Identifier.parse("mxt_test:sword_art")) || !same(firstStage.damageMultiplier(), 1.1D)
                || swordManual.configuration().size() != 2
                || swordManual.configuration().values().stream().mapToInt(config -> config.abilities().size()).sum() != 3
                || swordManual.configuration().keySet().stream()
                .anyMatch(stage -> !stage.value().skill().equals(firstStage.skill()))) {
            throw new IllegalStateException("Technique skill stages did not decode their chain, multiplier or configuration");
        }
        ServerCache skillChain = ServerCache.get()
                .orElseThrow(() -> new IllegalStateException("The server cache was not built before the audit"));
        Identifier firstStageId = Identifier.parse("mxt_test:sword_art_1");
        Identifier secondStageId = Identifier.parse("mxt_test:sword_art_2");
        if (skillChain.rankForStage(firstStageId).orElse(-1) != 0 || skillChain.rankForStage(secondStageId).orElse(-1) != 1
                || !skillChain.isStageAtLeast(secondStageId, firstStageId) || skillChain.isStageAtLeast(firstStageId, secondStageId)) {
            throw new IllegalStateException("Skill stages were not ordered into a linear chain");
        }
        Holder<SkillStage> firstStageHolder = requireHolder(MxtResourceKeys.SKILL_STAGE, firstStageId);
        Holder<SkillStage> secondStageHolder = requireHolder(MxtResourceKeys.SKILL_STAGE, secondStageId);
        if (SkillStageService.unlockedAbilities(swordManual, firstStageHolder).size() != 1
                || SkillStageService.unlockedAbilities(swordManual, secondStageHolder).size() != 2) {
            throw new IllegalStateException("Configured abilities were not gated as minimum requirements");
        }
        if (SkillStageService.nextStage(swordManual, firstStageHolder).filter(secondStageHolder::equals).isEmpty()
                || SkillStageService.nextStage(swordManual, secondStageHolder).isPresent()
                || swordManual.configuration().get(firstStageHolder) == null
                || swordManual.configuration().get(secondStageHolder) == null
                || swordManual.configuration().get(firstStageHolder).condition() == AlwaysTrueCondition.INSTANCE
                || swordManual.configuration().get(secondStageHolder).condition() == AlwaysTrueCondition.INSTANCE
                || SkillStageService.advanceCondition(swordManual, firstStageHolder)
                != swordManual.configuration().get(firstStageHolder).condition()
                || SkillStageService.advanceCondition(swordManual, secondStageHolder)
                != swordManual.configuration().get(secondStageHolder).condition()) {
            throw new IllegalStateException("Technique configuration did not keep its per-level conditions and abilities");
        }
        Holder<CultivateAction> qingxiaoMeditation = requireHolder(MxtResourceKeys.CULTIVATE_ACTION, Identifier.parse("mxt_test:qingxiao_meditation"));
        if (!qingxiaoMeditation.value().defaultAction()) {
            throw new IllegalStateException("Default cultivation action settings were not decoded");
        }
        CultivationAttachment cultivationMode = new CultivationAttachment();
        if (CultivationModeService.resolveAction(cultivationMode).filter(qingxiaoMeditation::equals).isEmpty()) {
            throw new IllegalStateException("Default cultivation action was not resolved without a technique");
        }
        cultivationMode.startCultivateAction(qingxiaoMeditation, 0L, 0L);
        cultivationMode.stopCultivateAction(qingxiaoMeditation, 0L);
        if (cultivationMode.cultivating() || cultivationMode.cultivateAction().filter(qingxiaoMeditation::equals).isEmpty()) {
            throw new IllegalStateException("Cultivation mode did not preserve its selected action after stopping");
        }
        ItemStack spiritStone = new ItemStack(MxtItems.SPIRIT_STONE.get());
        ItemAura itemAura = MxtDatapackRegistries.get(MxtResourceKeys.ITEM_AURA,
                        Identifier.parse("mxt_test:spirit_stone"))
                .orElseThrow(() -> new IllegalStateException("Item aura test definition was not loaded"));
        if (!same(itemAura.aura().evaluate(FormulaContext.EMPTY), 100.0D)
                || !HolderHelper.id(itemAura.type()).equals(Identifier.parse("mxt_test:spirit_power"))
                || !same(itemAura.consumeSpeed().evaluate(FormulaContext.EMPTY), 1.0D)
                || !same(itemAura.releaseSpeed().evaluate(FormulaContext.EMPTY), 2.0D)
                || itemAura.resultStack().isPresent()
                || spiritStone.get(MxtDataComponents.SPIRIT_STORAGE) != null
                || ItemAuraService.find(event.getServer().registryAccess(), spiritStone).isEmpty()) {
            throw new IllegalStateException("Item aura did not resolve its fuel item and values");
        }
        SpiritItemAccess stoneAccess = (SpiritItemAccess) spiritStone.getItem();
        Holder<Resource> commonAura = requireHolder(MxtResourceKeys.RESOURCE, Identifier.parse("mxt:common"));
        spiritStone.set(MxtDataComponents.SPIRIT_STORAGE, new SpiritStorageComponent(101));
        if (stoneAccess.getCapacity(null, spiritStone).getInt(commonAura) != 100
                || stoneAccess.add(null, spiritStone, commonAura, 0, false) != 0
                || spiritStone.getOrDefault(MxtDataComponents.SPIRIT_STORAGE, new SpiritStorageComponent(0)).amount() != 100
                || stoneAccess.extract(null, spiritStone, commonAura, 100, false) != 0
                || spiritStone.getOrDefault(MxtDataComponents.SPIRIT_STORAGE, new SpiritStorageComponent(0)).amount() != 0
                || stoneAccess.add(null, spiritStone, commonAura, 100, false) != 0
                || spiritStone.getOrDefault(MxtDataComponents.SPIRIT_STORAGE, new SpiritStorageComponent(0)).amount() != 100) {
            throw new IllegalStateException("Spirit stone charging did not clamp overflow or preserve empty charge");
        }
        spiritStone.set(MxtDataComponents.ITEM_AURA, new ItemAuraComponent(0.0D));
        stoneAccess.extract(null, spiritStone, commonAura, 100, false);
        stoneAccess.add(null, spiritStone, commonAura, 1, false);
        if (spiritStone.get(MxtDataComponents.ITEM_AURA) != null) {
            throw new IllegalStateException("Recharged spirit stones must become eligible for item-aura consumption again");
        }
        ItemStack qingxiaoCrystal = new ItemStack(MxtTestItems.QINGXIAO_SPIRIT_CRYSTAL.get());
        ItemAura qingxiaoAura = MxtDatapackRegistries.get(MxtResourceKeys.ITEM_AURA,
                        Identifier.parse("mxt_test:qingxiao_spirit_crystal"))
                .orElseThrow(() -> new IllegalStateException("Qingxiao spirit crystal definition was not loaded"));
        if (!same(qingxiaoAura.aura().evaluate(FormulaContext.EMPTY), 180.0D)
                || !same(qingxiaoAura.consumeSpeed().evaluate(FormulaContext.EMPTY), 1.0D)
                || !same(qingxiaoAura.releaseSpeed().evaluate(FormulaContext.EMPTY), 3.0D)
                || ItemAuraService.find(event.getServer().registryAccess(), qingxiaoCrystal).isEmpty()) {
            throw new IllegalStateException("Qingxiao spirit crystal did not bind its test-mod item");
        }
        List<Entry> matcherEntries = ItemMatcher.ENTRIES_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, event.getServer().registryAccess()), JsonParser.parseString("""
                ["minecraft:stick", "minecraft:diamond_sword"]
                """)).getOrThrow();
        if (matcherEntries.stream().noneMatch(entry -> entry.matches(weapon))) {
            throw new IllegalStateException("Physical item matcher entries did not match the weapon stack");
        }
        if (ResourceHolderAttachment.CODEC.codec().parse(RegistryOps.create(JsonOps.INSTANCE, event.getServer().registryAccess()), JsonParser.parseString("""
                {"values": {"mxt_test:spirit_power": 1.0}}
                """)).result().isPresent()) {
            throw new IllegalStateException("Resource holder data must require its current audit structure");
        }
        boolean hasCopperToIron = CurrencyValueService.exchangeOffers(new ItemStack(MxtItems.COPPER_COIN.get(), 10))
                .stream()
                .anyMatch(offer -> offer.cost() == 10
                        && offer.output().is(MxtItems.IRON_COIN.get()) && offer.output().getCount() == 1);
        if (!hasCopperToIron) {
            throw new IllegalStateException("Currency exchange definition did not offer 10 copper coins for one iron coin");
        }
        verifyClientDefinitions(event);
        verifyConfigKeyMigration();
        verifyFormationTemplate(event);
        verifyFormationRuntime(event);
        verifyFriendIdentification(event.getServer().overworld());
        ItemStack fireGinseng = new ItemStack(Items.RED_MUSHROOM);
        if (SpiritHerbService.find(fireGinseng).filter(herb -> HolderHelper.id(herb.quality()).equals(Identifier.parse("mxt_test:spirit_iron"))
                && same(herb.quality().value().valueMultiplier().modifier().evaluate(FormulaContext.EMPTY), 1.5D)
                && same(herb.quality().value().forgingModifier().modifier().evaluate(FormulaContext.EMPTY), 1.1D)
                && same(herb.quality().value().alchemyModifier().modifier().evaluate(FormulaContext.EMPTY), 1.0D)).isEmpty()
                || ItemQualityService.find(event.getServer().registryAccess(), fireGinseng)
                .map(HolderHelper::id).filter(Identifier.parse("mxt_test:spirit_iron")::equals).isEmpty()) {
            throw new IllegalStateException("Spirit herb quality did not resolve through the shared item-quality system");
        }
        verifyAuraZonePriority(event);
        verifyFormationAuraOverride(event.getServer().overworld());
        verifyAuraResolutionMemo(event);
        verifyChannelAbility(event);
        verifyWeaponAttributeMerge(event);
        verifyInventoryUtilAtomicity();
        verifyForgingMaterialMatching();
        verifyForgingStepLimit();
        verifyForgingAssets();
        verifyTechniquePanelAssets();
        verifyDisplayNames();
        verifyInformationColumns();
        verifyIconReferences();
        verifySampleTechniques();
        verifyForgingBindingsLoaded();
        verifyForgingMethodIntersection(event.getServer().registryAccess());
        verifyForgingPlans(event.getServer().registryAccess());
        verifyForgingStepRows(event.getServer().registryAccess());
        verifyForgingSuffixWindow();
        verifyForgingSessionRoundTrip();
        verifyForgingMethodSounds(event.getServer().registryAccess());
        verifyForgingUnlocks();
        LOGGER.info("MiXianTu server audit passed");
    }

    /**
     * Starting a session consumes a blueprint's materials from the surface slots as an
     * order-independent multiset, and a requirement the surface cannot cover must consume nothing.
     */
    private static void verifyForgingMaterialMatching() {
        List<ForgingMaterial> requirement = List.of(
                new ForgingMaterial(Identifier.parse("minecraft:iron_ingot"), 3),
                new ForgingMaterial(Identifier.parse("minecraft:coal"), 1));

        Container covered = forgingInputs();
        // Deliberately loaded out of declaration order and split across slots.
        covered.setItem(ForgingSurface.INPUT_START, new ItemStack(Items.COAL, 2));
        covered.setItem(ForgingSurface.INPUT_START + 1, new ItemStack(Items.IRON_INGOT, 1));
        covered.setItem(ForgingSurface.INPUT_START + 4, new ItemStack(Items.IRON_INGOT, 4));
        if (!ForgingProbe.materialsCovered(covered, requirement))
            throw new IllegalStateException("Forging audit expected an out-of-order, split material list to match");
        if (!ForgingProbe.consumeMaterials(covered, requirement))
            throw new IllegalStateException("Forging audit expected the material list to be consumable");
        if (covered.getItem(ForgingSurface.INPUT_START).getCount() != 1
                || !covered.getItem(ForgingSurface.INPUT_START + 1).isEmpty()
                || covered.getItem(ForgingSurface.INPUT_START + 4).getCount() != 2) {
            throw new IllegalStateException("Forging audit found materials consumed from the wrong slots: "
                    + covered.getItem(ForgingSurface.INPUT_START) + " / "
                    + covered.getItem(ForgingSurface.INPUT_START + 1) + " / "
                    + covered.getItem(ForgingSurface.INPUT_START + 4));
        }

        Container short1 = forgingInputs();
        short1.setItem(ForgingSurface.INPUT_START, new ItemStack(Items.IRON_INGOT, 2));
        short1.setItem(ForgingSurface.INPUT_START + 1, new ItemStack(Items.COAL, 1));
        if (ForgingProbe.materialsCovered(short1, requirement))
            throw new IllegalStateException("Forging audit expected an undersupplied material list to be rejected");
        if (ForgingProbe.consumeMaterials(short1, requirement))
            throw new IllegalStateException("Forging audit expected consumption of an undersupplied list to fail");
        if (short1.getItem(ForgingSurface.INPUT_START).getCount() != 2
                || short1.getItem(ForgingSurface.INPUT_START + 1).getCount() != 1) {
            throw new IllegalStateException("Forging audit found materials partially consumed on failure: "
                    + short1.getItem(ForgingSurface.INPUT_START) + " / " + short1.getItem(ForgingSurface.INPUT_START + 1));
        }

        Container wrongItem = forgingInputs();
        wrongItem.setItem(ForgingSurface.INPUT_START, new ItemStack(Items.GOLD_INGOT, 5));
        wrongItem.setItem(ForgingSurface.INPUT_START + 1, new ItemStack(Items.COAL, 1));
        if (ForgingProbe.materialsCovered(wrongItem, requirement))
            throw new IllegalStateException("Forging audit expected a wrong-item material list to be rejected");

        // The tooltip reads availableCount and the button reads materialsCovered; if they disagreed the
        // tooltip would tick every line while the button stayed dark, so both are compared here.
        Container exact = forgingInputs();
        exact.setItem(ForgingSurface.INPUT_START, new ItemStack(Items.IRON_INGOT, 3));
        exact.setItem(ForgingSurface.INPUT_START + 1, new ItemStack(Items.COAL, 1));
        if (!ForgingWorkstationService.materialsCovered(exact, requirement))
            throw new IllegalStateException("Forging audit expected an exact material list to be covered");
        for (ForgingMaterial entry : requirement) {
            int have = ForgingWorkstationService.availableCount(exact, entry);
            if (have < entry.count())
                throw new IllegalStateException("Forging audit found " + have + " of " + entry.id()
                        + ", which would print a cross on a container the button accepts");
        }
        if (ForgingWorkstationService.materialsCovered(short1, requirement))
            throw new IllegalStateException("Forging audit expected the undersupplied container to stay uncovered");
        ForgingMaterial iron = requirement.getFirst();
        int shortHave = ForgingWorkstationService.availableCount(short1, iron);
        if (shortHave != 2)
            throw new IllegalStateException("Forging audit expected the undersupplied container to hold 2 of "
                    + iron.id() + ", got " + shortHave);
    }

    /**
     * An omitted {@code max_steps} means "never fail for running long", and the plan must still hold
     * a positive bound because {@code ForgingPlan} rejects a non-positive step limit.
     */
    private static void verifyForgingStepLimit() {
        if (ForgingProbe.planMaxSteps(0) != Integer.MAX_VALUE)
            throw new IllegalStateException("Forging audit expected an omitted max_steps to become an unbounded plan limit");
        if (ForgingProbe.planMaxSteps(32) != 32)
            throw new IllegalStateException("Forging audit expected an explicit max_steps to survive normalisation");
    }
    /**
     * Every binding the test items name must resolve: a key that names nothing reads as an absent
     * component, which looks like a broken slot filter rather than a typo in a file name.
     */
    private static void verifyForgingBindingsLoaded() {
        List<String> tools = List.of("crude_hammer", "smith_hammer", "master_hammer");
        for (String path : tools) {
            Identifier id = Identifier.parse("mxt_test:" + path);
            if (MxtDatapackRegistries.holder(MxtResourceKeys.TOOL_BINDING, id).isEmpty())
                throw new IllegalStateException("Test item " + path + " names a tool_binding that does not exist: " + id);
        }
        List<String> manuals = List.of("sword_manual", "pickaxe_manual");
        for (String path : manuals) {
            Identifier id = Identifier.parse("mxt_test:" + path);
            if (MxtDatapackRegistries.holder(MxtResourceKeys.BLUEPRINT_BINDING, id).isEmpty())
                throw new IllegalStateException("Test item " + path + " names a blueprint_binding that does not exist: " + id);
        }
    }
    /**
     * The method list is the blueprint's {@code allowed_methods} intersected with the tools'; the shapes
     * that fail silently - an unloaded tag, an unresolved binding - are pinned here too.
     */
    private static void verifyForgingMethodIntersection(RegistryAccess registries) {
        Identifier ironSword = Identifier.parse("mxt_test:iron_sword");
        Identifier pickaxe = Identifier.parse("mxt_test:pickaxe");
        Container master = forgingTools(MxtTestForgeItems.MASTER_HAMMER.get());
        Container smith = forgingTools(MxtTestForgeItems.SMITH_HAMMER.get());

        // Nothing selected: a null blueprint restricts nothing, which is the state the grid starts in.
        expectMethods(registries, master, null, 10, "no blueprint");
        // An explicit list that covers everything the tool has.
        expectMethods(registries, master, ironSword, 10, "iron_sword's explicit list");
        expectMethods(registries, smith, ironSword, 5, "smith hammer against iron_sword");

        // A tag, which is the case that fails silently when the tag path is wrong. Exact equality, not
        // just a count, so a tag resolving to the wrong three is caught too.
        List<Identifier> tagged = ForgingWorkstationService.availableMethodIds(master, registries, pickaxe);
        List<Identifier> expectedTag = List.of(Identifier.parse("mxt_test:heavy_strike"),
                Identifier.parse("mxt_test:light_strike"), Identifier.parse("mxt_test:fold"));
        if (!tagged.equals(expectedTag))
            throw new IllegalStateException("Expected pickaxe's tag to resolve to " + expectedTag + ", got " + tagged);

        // The intersection bites from both sides: smith performs five and the tag names three, and they
        // only agree on the two strikes.
        expectMethods(registries, smith, pickaxe, 2, "smith hammer against pickaxe's tag");
    }

    private static void expectMethods(RegistryAccess registries, Container tools, Identifier blueprint,
                                      int expected, String what) {
        int actual = ForgingWorkstationService.availableMethodIds(tools, registries, blueprint).size();
        if (actual != expected)
            throw new IllegalStateException("Expected " + expected + " methods from " + what + ", got " + actual);
    }

    private static Container forgingTools(Item tool) {
        Container container = new SimpleContainer(ForgingSurface.TOTAL_SLOTS);
        container.setItem(ForgingSurface.TOOL_START, new ItemStack(tool));
        return container;
    }

    /**
     * Every test blueprint must build a plan that can reach its target; an unreachable target band or a
     * {@code finish_pattern} its {@code allowed_methods} exclude would otherwise only fail at the button.
     */
    private static void verifyForgingPlans(RegistryAccess registries) {
        for (Reference<ForgingBlueprint> holder : MxtDatapackRegistries.holders(registries, MxtResourceKeys.FORGING_BLUEPRINT).toList()) {
            Identifier id = HolderHelper.id(holder);
            try {
                ForgingPlan plan = holder.value().plan(registries);
                if (plan.deltas().isEmpty())
                    throw new IllegalStateException("Test blueprint " + id + " resolves to no methods at all");
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Test blueprint " + id + " cannot be forged: " + exception.getMessage(), exception);
            }
        }
    }

    /**
     * The required row must show the last {@code required} steps of the finish pattern: a row built from the
     * pattern's first entries fills the same six cells but names a sequence the server never asks for.
     */
    private static void verifyForgingStepRows(RegistryAccess registries) {
        ForgingBlueprint blueprint = MxtDatapackRegistries
                .get(registries, MxtResourceKeys.FORGING_BLUEPRINT, Identifier.parse("mxt_test:iron_sword"))
                .orElseThrow(() -> new IllegalStateException("The step-row audit needs mxt_test:iron_sword"));
        List<Identifier> pattern = blueprint.finishPattern().steps().stream().map(HolderHelper::id).toList();
        int required = blueprint.finishPattern().requiredSuffixSteps();
        int first = ForgingMenu.SUFFIX_STEPS - required;

        // Two halves that are equal would pass whichever end the row came from, so the test data has to
        // be able to tell the difference at all.
        if (pattern.subList(0, first).equals(pattern.subList(first, ForgingMenu.SUFFIX_STEPS)))
            throw new IllegalStateException("iron_sword's finish pattern is symmetric, so it cannot catch a"
                    + " required row taken from the wrong end; make its last " + required + " steps differ from its first " + required);

        int[] row = ForgingMenuProbe.targetRow(pattern, required);
        for (int position = 0; position < ForgingMenu.SUFFIX_STEPS; position++) {
            int expected = position < first ? ForgingMenu.NONE : registryId(registries, pattern.get(position));
            if (row[position] != expected)
                throw new IllegalStateException("Target row position " + position + " is " + row[position]
                        + " but the finish pattern puts " + expected + " there");
        }

        // The current row is right-aligned: three strikes occupy the last three cells and nothing else.
        List<Identifier> three = pattern.subList(0, 3);
        int[] current = ForgingMenuProbe.historyRow(three);
        for (int position = 0; position < ForgingMenu.SUFFIX_STEPS; position++) {
            int expected = position < 3 ? ForgingMenu.NONE : registryId(registries, three.get(position - 3));
            if (current[position] != expected)
                throw new IllegalStateException("Current row position " + position + " is " + current[position]
                        + " but a three-strike history puts " + expected + " there");
        }
    }

    private static int registryId(RegistryAccess registries, Identifier method) {
        Registry<ForgingMethod> registry = registries.lookupOrThrow(MxtResourceKeys.FORGING_METHOD);
        return registry.get(method).map(holder -> registry.getId(holder.value())).orElse(ForgingMenu.NONE);
    }

    /**
     * Only the last {@code required_suffix_steps} history entries are checked; the leading ones are drawn as
     * barriers and take no part. Pattern and plan are built here because both halves must differ to show it.
     */
    private static void verifyForgingSuffixWindow() {
        Identifier a = Identifier.parse("mxt_test:probe_a");
        Identifier b = Identifier.parse("mxt_test:probe_b");
        Identifier c = Identifier.parse("mxt_test:probe_c");
        Identifier lead = Identifier.parse("mxt_test:probe_lead");

        // Required three: the rule reads [a, b, c] out of this. The first three are deliberately not that.
        List<Identifier> pattern = List.of(b, c, a, a, b, c);
        Map<Identifier, Integer> deltas = new LinkedHashMap<>();
        for (Identifier method : List.of(a, b, c, lead)) deltas.put(method, 1);
        // Every method moves the value by one, so n strikes put it at n and the target is "n == 4".
        ForgingPlan plan = new ForgingPlan(-10, 10, 4, 4, pattern, 3, deltas, 10);

        // The last three are [a, b, c]. The leading step is not part of the pattern at all, and the cell it
        // occupies in the row is one of the three barriers.
        ForgingSession matched = new ForgingSession(plan);
        for (Identifier method : List.of(lead, a, b, c)) matched.strike(method);
        if (!matched.canComplete())
            throw new IllegalStateException("Forging audit expected [lead, a, b, c] to satisfy a last-three rule of [a, b, c]");

        // The last three are the pattern's *first* three: same value, so this is the case that slips through
        // if the rule read the wrong end or counted the barriers as positions that have to match.
        ForgingSession reversed = new ForgingSession(plan);
        for (Identifier method : List.of(lead, b, c, a)) reversed.strike(method);
        if (reversed.canComplete())
            throw new IllegalStateException("Forging audit found the pattern's first three entries being checked as the suffix");
    }

    /**
     * A restored session must answer as the original did - the session persists only its progress, so the
     * rule comes back from the plan - and a method the plan does not list must be a refusal, not an error.
     */
    private static void verifyForgingSessionRoundTrip() {
        Identifier allowed = Identifier.parse("mxt_test:probe_a");
        Identifier stranger = Identifier.parse("mxt_test:probe_stranger");

        Map<Identifier, Integer> deltas = new LinkedHashMap<>();
        deltas.put(allowed, 1);
        // Every strike moves the value by one, so the target is entered on the third strike and a fourth
        // strike still sits inside it: three is optimal, four is exactly one step worse.
        ForgingPlan plan = new ForgingPlan(-10, 10, 3, 4, List.of(), 0, deltas, 10);
        if (plan.optimalSteps() != 3)
            throw new IllegalStateException("Forging audit expected a target of three to be solvable in three steps");

        ForgingSession session = new ForgingSession(plan);
        if (session.canStrike(stranger) || session.strike(stranger) || session.value() != 0 || session.steps() != 0)
            throw new IllegalStateException("Forging audit expected an unlisted method to be refused without touching the session");

        for (int index = 0; index < 4; index++) {
            if (!session.strike(allowed))
                throw new IllegalStateException("Forging audit expected four strikes towards a target of three to be allowed");
        }
        if (!session.canComplete() || session.extraSteps() != 1)
            throw new IllegalStateException("Forging audit expected a four-step run to be one step over an optimal three");

        ForgingSession restored = ForgingSession.restore(plan, session.snapshot());
        if (restored.value() != session.value() || restored.steps() != session.steps()
                || restored.optimalSteps() != plan.optimalSteps() || restored.extraSteps() != session.extraSteps())
            throw new IllegalStateException("Forging audit found a restored session disagreeing about its own progress");
        if (!restored.snapshot().history().equals(session.snapshot().history()))
            throw new IllegalStateException("Forging audit found a restored session losing its method history");
        if (restored.canStrike(allowed) != session.canStrike(allowed) || restored.canComplete() != session.canComplete())
            throw new IllegalStateException("Forging audit found a restored session judging a strike differently");

        // And the meter still refuses a step that would leave it, which is the other answer `canStrike` gives
        // without an exception: up by one from the top of a range that ends at one.
        ForgingPlan narrow = new ForgingPlan(-1, 1, 0, 0, List.of(), 0, deltas, 10);
        ForgingSession bounded = new ForgingSession(narrow);
        if (!bounded.strike(allowed) || bounded.value() != 1 || bounded.canStrike(allowed))
            throw new IllegalStateException("Forging audit expected a strike out of the meter to be refused at the bound");
    }

    /**
     * A method's sound must survive the datapack: declared when written, the anvil when omitted. The two
     * methods are named rather than counted, so editing either into the other shape is reported.
     */
    private static void verifyForgingMethodSounds(RegistryAccess registries) {
        ForgingMethod declared = MxtDatapackRegistries
                .get(registries, MxtResourceKeys.FORGING_METHOD, Identifier.parse("mxt_test:heavy_strike"))
                .orElseThrow(() -> new IllegalStateException("The forging sound audit needs mxt_test:heavy_strike"));
        if (declared.sound() != SoundEvents.ANVIL_LAND)
            throw new IllegalStateException("heavy_strike declares minecraft:block.anvil.land, so its parsed sound"
                    + " must be the anvil landing rather than " + declared.sound());

        ForgingMethod omitted = MxtDatapackRegistries
                .get(registries, MxtResourceKeys.FORGING_METHOD, Identifier.parse("mxt_test:light_strike"))
                .orElseThrow(() -> new IllegalStateException("The forging sound audit needs mxt_test:light_strike"));
        if (omitted.sound() != SoundEvents.ANVIL_PLACE)
            throw new IllegalStateException("light_strike writes no sound, so it must fall back to the anvil being"
                    + " placed rather than " + omitted.sound());
    }

    /**
     * Ending a session must unlock the table by any route and in both directions: placement and pickup both
     * end in {@link ForgingSurface}, which reads the session state, so a stuck table shows either symptom.
     */
    private static void verifyForgingUnlocks() {
        ItemStack material = new ItemStack(Items.IRON_INGOT);
        ItemStack manual = new ItemStack(MxtTestForgeItems.SWORD_MANUAL.get());

        ForgingTableState state = new ForgingTableState();
        if (state.active())
            throw new IllegalStateException("A fresh forging state must not be busy");
        if (!ForgingSurface.canPlace(ForgingSurface.INPUT_START, material, state.active(), null))
            throw new IllegalStateException("A fresh forging state must accept materials");
        if (!ForgingSurface.canPlace(ForgingSurface.BLUEPRINT_START, manual, state.active(), null))
            throw new IllegalStateException("A fresh forging state must accept a blueprint");

        // Busy: the session holds materials that have to stay where they are until it settles.
        ForgingPlan plan = probePlan();
        state.lock(Identifier.parse("mxt_test:iron_sword"), plan, new ForgingSession(plan),
                List.of(new ItemStack(Items.IRON_SWORD)), null);
        if (!state.active())
            throw new IllegalStateException("A locked forging state must be busy");
        if (ForgingSurface.canPlace(ForgingSurface.INPUT_START, material, state.active(), null))
            throw new IllegalStateException("A busy forging state must refuse materials");
        if (ForgingSurface.canTake(ForgingSurface.BLUEPRINT_START, state.active()))
            throw new IllegalStateException("A busy forging state must hold on to its blueprint");

        // Cancelled or failed: nothing is held, so nothing may stay locked.
        state.clear();
        if (state.active() || state.blueprint().isPresent() || state.plan().isPresent() || state.session().isPresent())
            throw new IllegalStateException("Clearing a forging state must leave none of it behind");
        if (!ForgingSurface.canPlace(ForgingSurface.INPUT_START, material, state.active(), null))
            throw new IllegalStateException("A cleared forging state must accept materials again");
        if (!ForgingSurface.canPlace(ForgingSurface.BLUEPRINT_START, manual, state.active(), null))
            throw new IllegalStateException("A cleared forging state must accept a blueprint again");
        if (!ForgingSurface.canTake(ForgingSurface.BLUEPRINT_START, state.active()))
            throw new IllegalStateException("A cleared forging state must give its blueprint back");
    }

    /**
     * A hand-built plan for checks that only need a valid one: one method that moves the value by one, no
     * finish pattern, and a target the shortest possible session already meets.
     */
    private static ForgingPlan probePlan() {
        Map<Identifier, Integer> deltas = new LinkedHashMap<>();
        deltas.put(Identifier.parse("mxt_test:probe_a"), 1);
        return new ForgingPlan(-10, 10, 0, 0, List.of(), 0, deltas, 10);
    }

    private static void verifyForgingAssets() {
        List<String> required = List.of(
                "assets/mxt/blockstates/forging_table.json",
                "assets/mxt/models/block/forging_table.json",
                "assets/mxt/models/item/forging_table.json",
                "assets/mxt/textures/block/forging_table/smithing_table_side.png",
                "assets/mxt/textures/block/forging_table/smithing_table_top.png",
                "assets/mxt/textures/block/forging_table/smithing_table_bottom.png",
                "assets/mxt/textures/block/forging_table/hammer.png",
                "assets/mxt/textures/gui/forging_table.png");
        for (String path : required) {
            if (MxtTestMod.class.getClassLoader().getResource(path) == null)
                throw new IllegalStateException("Forging asset is missing from the jar: " + path);
        }
    }

    private static Container forgingInputs() {
        return new SimpleContainer(ForgingSurface.INPUT_SLOTS);
    }

    /**
     * The panel is client-only, so the audit only proves its assets and message keys are in the jar; a
     * misspelled path or key would otherwise be silent - a placeholder texture, or a raw key on screen.
     */
    private static void verifyTechniquePanelAssets() {
        for (String path : List.of(
                "assets/mxt/textures/gui/classic/technique_panel.png",
                "assets/mxt/textures/gui/classic/slot_24.png")) {
            if (MxtTestMod.class.getClassLoader().getResource(path) == null)
                throw new IllegalStateException("Technique panel asset is missing from the jar: " + path);
        }
        List<String> keys = new ArrayList<>(List.of(
                "screen.mxt.technique_panel",
                "screen.mxt.technique_panel.empty",
                "screen.mxt.technique_panel.level",
                "screen.mxt.technique_panel.level_unknown",
                "screen.mxt.technique_panel.value",
                "screen.mxt.technique_panel.value_max",
                "screen.mxt.technique_panel.value_unknown",
                "key.mxt.technique_panel",
                "config.mxt.client.techniques",
                "config.mxt.client.techniques.progress_mode"));
        for (Mode mode : Mode.values())
            keys.add("config.mxt.client.techniques.progress_mode." + mode.name().toLowerCase(Locale.ROOT));
        for (String language : List.of("en_us", "zh_cn")) {
            JsonObject lang = readLang(language);
            for (String key : keys)
                if (!lang.has(key))
                    throw new IllegalStateException("Technique panel lang key is missing from " + language + ": " + key);
        }
    }

    private static JsonObject readLang(String language) {
        String path = "assets/mxt/lang/" + language + ".json";
        try (InputStream stream = MxtTestMod.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Lang file is missing from the jar: " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }

    private static JsonObject readTestLang(String language) {
        String path = "assets/" + MOD_ID + "/lang/" + language + ".json";
        try (InputStream stream = MxtTestMod.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Test lang file is missing from the jar: " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }

    /**
     * Every name a panel prints must exist in the test pack in both languages, or the panel prints the raw
     * key instead; {@code skill_stage} is deliberately absent and falls back to the level's rank.
     */
    private static void verifyDisplayNames() {
        JsonObject en = readTestLang("en_us");
        JsonObject zh = readTestLang("zh_cn");
        verifyDisplayNames(en, zh, MxtResourceKeys.RESOURCE, "resource");
        verifyDisplayNames(en, zh, MxtResourceKeys.REALM_STAGE, "realm_stage");
        verifyDisplayNames(en, zh, MxtResourceKeys.SPIRIT_ROOT, "spirit_root");
        verifyDisplayNames(en, zh, MxtResourceKeys.PHYSIQUE, "physique");
        verifyDisplayNames(en, zh, MxtResourceKeys.CULTIVATION_TECHNIQUE, "cultivation_technique");
    }

    private static <T> void verifyDisplayNames(JsonObject en, JsonObject zh,
                                               ResourceKey<? extends Registry<T>> registry, String category) {
        for (Reference<T> holder : MxtDatapackRegistries.holders(registry).toList()) {
            Identifier id = holder.key().identifier();
            if (!MOD_ID.equals(id.getNamespace())) continue;
            String key = id.toLanguageKey(category);
            if (!en.has(key) || !zh.has(key))
                throw new IllegalStateException("Test definition has no display name: " + key);
        }
    }

    private static void verifyInventoryUtilAtomicity() {
        ItemStack initial = new ItemStack(Items.DIAMOND, 10);

        Container target = new SimpleContainer(2);
        target.setItem(0, initial.copy());
        Container tooMany = new SimpleContainer(1);
        tooMany.setItem(0, new ItemStack(Items.DIAMOND, 64));
        if (InventoryUtil.removeItems(target, tooMany))
            throw new IllegalStateException("InventoryUtil audit expected removeItems to fail when the requirement exceeds the stock");
        if (target.getItem(0).getCount() != initial.getCount() || !target.getItem(1).isEmpty())
            throw new IllegalStateException("InventoryUtil audit found removeItems partially applied on failure: "
                    + target.getItem(0) + " / " + target.getItem(1));

        Container half = new SimpleContainer(1);
        half.setItem(0, new ItemStack(Items.DIAMOND, 4));
        if (!InventoryUtil.removeItems(target, half))
            throw new IllegalStateException("InventoryUtil audit expected removeItems to succeed within the stock");
        if (target.getItem(0).getCount() != 6)
            throw new IllegalStateException("InventoryUtil audit found removeItems did not apply fully: " + target.getItem(0));

        Container full = new SimpleContainer(1);
        full.setItem(0, new ItemStack(Items.STONE, 1));
        Container overflow = new SimpleContainer(1);
        overflow.setItem(0, new ItemStack(Items.STONE, 64));
        if (InventoryUtil.insertItems(full, overflow))
            throw new IllegalStateException("InventoryUtil audit expected insertItems to fail when the target is full");
        if (!full.getItem(0).is(Items.STONE) || full.getItem(0).getCount() != 1)
            throw new IllegalStateException("InventoryUtil audit found insertItems partially applied on failure: " + full.getItem(0));

        Container empty = new SimpleContainer(1);
        Container fitting = new SimpleContainer(1);
        fitting.setItem(0, new ItemStack(Items.STONE, 5));
        if (!InventoryUtil.insertItems(empty, fitting) || empty.getItem(0).getCount() != 5)
            throw new IllegalStateException("InventoryUtil audit found insertItems did not apply fully: " + empty.getItem(0));
    }

    /**
     * A weapon binding must add its modifiers on top of the item's vanilla ones instead of replacing the
     * component: the check drives the real entity-tick refresh and asserts the vanilla +7 damage survives.
     */
    private static void verifyWeaponAttributeMerge(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        Pig actor = new Pig(EntityType.PIG, overworld);
        actor.setPos(BlockPos.ZERO.getX() + 0.5D, BlockPos.ZERO.getY(), BlockPos.ZERO.getZ() + 0.5D);
        overworld.addFreshEntity(actor);
        try {
            List<AttributeModifier> vanillaDamage = damageModifiers(new ItemStack(Items.DIAMOND_SWORD));
            if (vanillaDamage.isEmpty())
                throw new IllegalStateException("Weapon binding audit needs a vanilla sword with an attack damage modifier");
            ItemStack bound = new ItemStack(Items.DIAMOND_SWORD);
            actor.setItemInHand(InteractionHand.MAIN_HAND, bound);
            // The real refresh path: ItemBindingService refreshes equipment from the post entity tick.
            ItemBindingService.onEntityTick(new Post(actor));

            ItemAttributeModifiers merged = actor.getMainHandItem().get(DataComponents.ATTRIBUTE_MODIFIERS);
            if (merged == null)
                throw new IllegalStateException("Weapon binding audit expected the attribute component to exist after the refresh");
            List<AttributeModifier> mergedDamage = damageModifiers(actor.getMainHandItem());
            for (AttributeModifier modifier : vanillaDamage) {
                if (mergedDamage.stream().noneMatch(candidate -> candidate.id().equals(modifier.id())
                        && Double.compare(candidate.amount(), modifier.amount()) == 0)) {
                    throw new IllegalStateException("Weapon binding audit lost the vanilla attack damage modifier "
                            + modifier.id() + " (" + modifier.amount() + "); merged component is " + merged.modifiers());
                }
            }
            if (mergedDamage.stream().noneMatch(modifier -> MxtTestMod.MOD_ID.equals(modifier.id().getNamespace())
                    || "mxt".equals(modifier.id().getNamespace())))
                throw new IllegalStateException("Weapon binding audit did not add a binding attack damage modifier; merged component is "
                        + merged.modifiers());
        } finally {
            actor.discard();
        }
    }

    /**
     * Returns the attack damage modifiers of a stack. The component may be absent, in which case the
     * item prototype still supplies the vanilla values.
     */
    private static List<AttributeModifier> damageModifiers(ItemStack stack) {
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return modifiers.modifiers().stream()
                .filter(entry -> entry.attribute().is(Attributes.ATTACK_DAMAGE.unwrapKey().orElseThrow()))
                .map(ItemAttributeModifiers.Entry::modifier)
                .toList();
    }

    private static Identifier bindingIdOf(ItemStack stack) {
        return ItemBindingService.resolve(stack).weapon()
                .flatMap(binding -> MxtDatapackRegistries.holders(MxtResourceKeys.WEAPON_BINDING)
                        .filter(holder -> holder.value() == binding).findFirst())
                .map(HolderHelper::id).orElse(null);
    }

    /**
     * Runs the channelled ability assertion on a temporary entity, since the lifecycle needs only a
     * {@link LivingEntity}; the entity is discarded immediately afterwards.
     */
    private static void verifyChannelAbility(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        Pig actor = new Pig(EntityType.PIG, overworld);
        actor.setPos(BlockPos.ZERO.getX() + 0.5D, BlockPos.ZERO.getY(), BlockPos.ZERO.getZ() + 0.5D);
        overworld.addFreshEntity(actor);
        String failure;
        try {
            failure = ChannelProbe.verify(actor);
        } finally {
            ChannelProbe.clear(actor);
            actor.discard();
        }
        if (failure != null) throw new IllegalStateException("Channelled ability audit failed: " + failure);
    }

    /**
     * The test pack puts the highest biome priority (150) above the highest dimension one (100), so a
     * dimension result proves the tiers are compared first; same-tier ordering comes from the helper.
     */
    private static void verifyAuraZonePriority(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        Identifier overworldId = Identifier.parse("minecraft:overworld");
        List<Reference<AuraZone>> biomeZones = new ArrayList<>();
        List<Reference<AuraZone>> dimensionZones = new ArrayList<>();
        for (Reference<AuraZone> holder : MxtDatapackRegistries.holders(event.getServer().registryAccess(), MxtResourceKeys.AURA_ZONE).toList()) {
            AuraZone zone = holder.value();
            if (!zone.biomes().isEmpty()) biomeZones.add(holder);
            for (Either<ResourceKey<LevelStem>, TagKey<LevelStem>> entry : zone.dimensions()) {
                if (entry.left().map(key -> key.identifier().equals(overworldId)).orElse(false)) dimensionZones.add(holder);
            }
        }
        if (biomeZones.isEmpty() || dimensionZones.isEmpty())
            throw new IllegalStateException("Aura zone priority audit needs both a biome binding and a dimension binding");
        Reference<AuraZone> highestBiome = biomeZones.stream()
                .max(Comparator.comparingInt(holder -> holder.value().priority())).orElseThrow();
        int highestDimensionPriority = dimensionZones.stream()
                .mapToInt(holder -> holder.value().priority()).max().orElseThrow();
        if (highestBiome.value().priority() <= highestDimensionPriority)
            throw new IllegalStateException("Aura zone priority audit needs a biome binding above the highest dimension priority, but the highest biome priority is "
                    + highestBiome.value().priority() + " and the highest dimension priority is " + highestDimensionPriority);
        // The biome tier holds the highest priority in the whole registry, so a dimension result
        // proves the dimension tier is compared before any biome priority is considered.
        AuraResult resolved = AuraService.getPositionAura(overworld, BlockPos.ZERO);
        if (resolved.sourceKind() != SourceKind.DIMENSION)
            throw new IllegalStateException("Aura zone priority audit expected the dimension binding to outrank a higher-priority biome binding, but resolved "
                    + resolved.sourceKind() + " as " + resolved.source() + " while the highest biome priority is "
                    + highestBiome.value().priority() + " (" + HolderHelper.id(highestBiome) + ")");
        // Two definitions of one tier must resolve by priority, then by ascending registry ID.
        for (List<Reference<AuraZone>> candidates : List.of(biomeZones, dimensionZones)) {
            if (candidates.size() < 2) continue;
            int highest = candidates.stream().mapToInt(holder -> holder.value().priority()).max().orElseThrow();
            Identifier expected = candidates.stream().filter(holder -> holder.value().priority() == highest)
                    .map(HolderHelper::id).min(Comparator.naturalOrder()).orElseThrow();
            Identifier winner = AuraZonePriorityProbe.select(candidates).orElseThrow();
            if (!expected.equals(winner))
                throw new IllegalStateException("Aura zone priority audit expected " + expected + " but the ordering helper returned " + winner);
        }
    }

    /**
     * The aura memo must never change an answer and must actually remove repetition, so both are measured;
     * the resolver then reports its own stage costs, which a sampling profiler cannot separate.
     */
    private static void verifyAuraResolutionMemo(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        BlockPos probe = BlockPos.ZERO;
        int batches = 3;
        int perBatch = 100;
        AuraQueryCache.setTiming(true);
        // The loop itself has a cost, so it is measured on a plain game-time read and subtracted. The
        // best batch is used instead of the average so one GC pause cannot skew the result.
        long baseline = Long.MAX_VALUE;
        for (int batch = 0; batch < batches; batch++) {
            long started = System.nanoTime();
            for (int index = 0; index < perBatch; index++) overworld.getGameTime();
            baseline = Math.min(baseline, System.nanoTime() - started);
        }
        AuraQueryCache.setEnabled(false);
        AuraQueryCache.advance(overworld, overworld.getGameTime());
        long uncached = Long.MAX_VALUE;
        AuraResult first = null;
        for (int batch = 0; batch < batches; batch++) {
            long started = System.nanoTime();
            for (int index = 0; index < perBatch; index++) first = AuraService.getPositionAura(overworld, probe);
            uncached = Math.min(uncached, System.nanoTime() - started - baseline);
        }
        AuraQueryCache.setEnabled(true);
        AuraQueryCache.advance(overworld, overworld.getGameTime());
        AuraQueryCache.resetStats();
        long cached = Long.MAX_VALUE;
        AuraResult repeat = null;
        for (int batch = 0; batch < batches; batch++) {
            long started = System.nanoTime();
            for (int index = 0; index < perBatch; index++) repeat = AuraService.getPositionAura(overworld, probe);
            cached = Math.min(cached, System.nanoTime() - started - baseline);
            AuraQueryCache.advance(overworld, overworld.getGameTime());
        }
        long queries = AuraQueryCache.queries();
        // One fully unresolved query, with the stage timer on, so the resolver reports where the time
        // of a single query actually goes.
        AuraQueryCache.setEnabled(false);
        AuraQueryCache.resetStats();
        AuraQueryCache.advance(overworld, overworld.getGameTime());
        AuraQueryCache.resetStats();
        AuraService.getPositionAura(overworld, probe);
        LOGGER.info("MiXianTu aura single-query stage report at {}:", probe);
        AuraQueryCache.reportDiagnostics();
        AuraQueryCache.reportStageCosts();
        AuraQueryCache.setEnabled(true);
        if (first == null || repeat == null) throw new IllegalStateException("Aura resolution audit produced no result");
        if (!first.aura().equals(repeat.aura()) || !first.source().equals(repeat.source())
                || first.sourceKind() != repeat.sourceKind() || !first.rules().equals(repeat.rules())) {
            throw new IllegalStateException("Aura memo changed the resolved result at " + probe
                    + ": uncached " + first.source() + "/" + first.sourceKind() + " " + first.aura()
                    + " versus memoised " + repeat.source() + "/" + repeat.sourceKind() + " " + repeat.aura());
        }
        if (queries < (long) perBatch * batches) {
            throw new IllegalStateException("Aura memo did not observe every query: " + queries + " of " + (perBatch * batches));
        }
        if (cached * 5L >= uncached) {
            throw new IllegalStateException("Aura memo did not make repeat resolution cheaper: best memoised batch took "
                    + cached / 1000L + " us against " + uncached / 1000L + " us uncached for " + perBatch + " queries");
        }
        verifyEntityQueryGate(overworld);
        LOGGER.info("MiXianTu aura memo audit: best of {} batches of {} queries on {} cost {} us memoised ({}.{} us each) "
                        + "against {} us unresolved ({}.{} us each, {}x), after subtracting a {} us loop baseline",
                batches, perBatch, first.source(), cached / 1000L, cached / perBatch / 1000L, cached / perBatch % 1000L,
                uncached / 1000L, uncached / perBatch / 1000L, uncached / perBatch % 1000L,
                String.format(Locale.ROOT, "%.1f", (double) uncached / Math.max(1L, cached)), baseline / 1000L);
    }

    /**
     * The entity gate must resolve a new entity, skip a stationary one inside its refresh interval, resolve
     * it again once the interval elapses, and resolve it immediately when it moves.
     */
    private static void verifyEntityQueryGate(ServerLevel overworld) {
        UUID id = UUID.randomUUID();
        long now = overworld.getGameTime();
        int interval = MxtServerConfig.INSTANCE.aura.entityRefreshInterval.getValue();
        AuraLocation here = new AuraLocation(overworld.dimension().identifier(), BlockPos.ZERO, now);
        if (!AuraQueryCache.needsQuery(overworld, id, here, interval))
            throw new IllegalStateException("Aura entity gate skipped an entity it had never seen");
        AuraQueryCache.recordQuery(overworld, id, here);
        if (AuraQueryCache.needsQuery(overworld, id, here, interval))
            throw new IllegalStateException("Aura entity gate resolved a stationary entity twice in the same tick");
        for (long offset = 1L; offset < interval; offset++) {
            AuraLocation same = new AuraLocation(here.dimension(), here.pos(), now + offset);
            if (AuraQueryCache.needsQuery(overworld, id, same, interval))
                throw new IllegalStateException("Aura entity gate resolved a stationary entity after only " + offset + " of " + interval + " ticks");
        }
        AuraLocation stale = new AuraLocation(here.dimension(), here.pos(), now + interval);
        if (!AuraQueryCache.needsQuery(overworld, id, stale, interval))
            throw new IllegalStateException("Aura entity gate never refreshed a stationary entity, so a zone change could go unnoticed forever");
        AuraLocation moved = new AuraLocation(here.dimension(), BlockPos.ZERO.east(), now + interval + 1L);
        if (!AuraQueryCache.needsQuery(overworld, id, moved, interval))
            throw new IllegalStateException("Aura entity gate did not resolve an entity that moved one block");
        AuraQueryCache.forget(overworld, id);
        if (!AuraQueryCache.needsQuery(overworld, id, here, interval))
            throw new IllegalStateException("Aura entity gate kept state for an entity that left the level");
    }

    private static void verifyItemQualities(ServerStartedEvent event) {
        Identifier excellentId = Identifier.parse("mxt_test:excellent");
        Identifier normalId = Identifier.parse("mxt_test:normal");
        Identifier poorId = Identifier.parse("mxt_test:poor");
        Holder<ItemQuality> excellent = requireHolder(MxtResourceKeys.ITEM_QUALITY, excellentId);
        Holder<ItemQuality> poor = requireHolder(MxtResourceKeys.ITEM_QUALITY, poorId);
        List<Holder<ItemQuality>> ordered = ItemQualityService.ordered(event.getServer().registryAccess());
        if (ordered.size() < 3 || !HolderHelper.id(ordered.get(0)).equals(excellentId)
                || !HolderHelper.id(ordered.get(1)).equals(normalId) || !HolderHelper.id(ordered.get(2)).equals(poorId)) {
            throw new IllegalStateException("Item-quality tooltip-order tag was not preserved");
        }
        if (ItemQualityService.group(event.getServer().registryAccess(), Identifier.parse("mxt_test:forged")).size() != 3
                || ItemQualityService.groups(event.getServer().registryAccess()).stream()
                .noneMatch(tag -> tag.equals(ItemQualityTags.group(Identifier.parse("mxt_test:forged"))))
                || !ItemQualityService.inGroup(excellent, Identifier.parse("mxt_test:forged"))) {
            throw new IllegalStateException("Item-quality group tags were not exposed by the read API");
        }
        ItemStack bound = new ItemStack(Items.DIAMOND_SWORD);
        Identifier forgedGroup = Identifier.parse("mxt_test:forged");
        if (ItemBindingService.qualityGroup(event.getServer().registryAccess(), bound)
                .filter(ItemQualityTags.group(forgedGroup)::equals).isEmpty()
                || ItemQualityService.find(event.getServer().registryAccess(), bound)
                .map(HolderHelper::id).filter(poorId::equals).isEmpty()) {
            throw new IllegalStateException("Weapon quality group did not resolve its tag-defined default");
        }
        ItemStack forged = new ItemStack(Items.IRON_SWORD);
        forged.set(MxtDataComponents.FORGING_RESULT,
                new ForgingResultComponent(Identifier.parse("mxt_test:iron_sword"), 3, 2, 2, 0, excellent));
        if (ItemQualityService.find(event.getServer().registryAccess(), forged)
                .map(HolderHelper::id).filter(excellentId::equals).isEmpty()) {
            throw new IllegalStateException("Forging-result quality did not resolve");
        }
        ItemQualityService.set(forged, poor);
        if (ItemQualityService.find(event.getServer().registryAccess(), forged)
                .map(HolderHelper::id).filter(poorId::equals).isEmpty()) {
            throw new IllegalStateException("Direct item quality did not take precedence");
        }
    }

    /** Test definitions include cross-references that must remain safe to print from an error path. */
    private static void verifyRecursiveDefinitionDiagnostics() {
        verifyDiagnostic("Element", requireHolder(MxtResourceKeys.ELEMENT, Identifier.parse("mxt_test:fire")));
        verifyDiagnostic("RealmStage", requireHolder(MxtResourceKeys.REALM_STAGE, Identifier.parse("mxt_test:foundation")));
        verifyDiagnostic("Ability", requireHolder(MxtResourceKeys.ABILITY, Identifier.parse("mxt_test:firebolt")));
        verifyDiagnostic("Curse", requireHolder(MxtResourceKeys.CURSE, Identifier.parse("mxt_test:dan_toxicity")));
    }

    private static void verifyDiagnostic(String type, Holder<?> holder) {
        String diagnostic = holder.toString();
        if (!diagnostic.contains(type + "[")) {
            throw new IllegalStateException(type + " diagnostics must not expand holder relations");
        }
    }

    private static void verifyDynamicResourceValues(RegistryAccess registries, Identifier foundation, Identifier coreForming) {
        Identifier qiId = Identifier.parse("mxt_test:qi");
        Identifier spiritPowerId = Identifier.parse("mxt_test:spirit_power");
        Holder<Resource> qi = requireHolder(MxtResourceKeys.RESOURCE, qiId);
        Holder<Resource> spiritPower = requireHolder(MxtResourceKeys.RESOURCE, spiritPowerId);
        Reference<CultivationProfile> qiChain = CultivationProfiles.holder(registries, qi)
                .orElseThrow(() -> new IllegalStateException("Qi cultivation profile was not loaded"));
        Reference<CultivationProfile> spiritPowerChain = CultivationProfiles.holder(registries, spiritPower)
                .orElseThrow(() -> new IllegalStateException("Spirit power cultivation profile was not loaded"));
        CultivationProfile qiProfile = qiChain.value();
        CultivationProfile spiritPowerProfile = spiritPowerChain.value();
        if (!same(qiProfile.cultivationToResource().multiplier().evaluate(FormulaContext.EMPTY), 0.25D)
                || !same(qiProfile.cultivationToResource().maxPerTick().evaluate(FormulaContext.EMPTY), 0.5D)
                || !same(qiProfile.resourceToCultivation().multiplier().evaluate(FormulaContext.EMPTY), 0.5D)
                || !same(qiProfile.resourceToCultivation().maxPerTick().evaluate(FormulaContext.EMPTY), 0.75D)
                || !same(spiritPowerProfile.cultivationToResource().multiplier().evaluate(FormulaContext.EMPTY), 1.0D)
                || !same(spiritPowerProfile.cultivationToResource().maxPerTick().evaluate(FormulaContext.EMPTY), 1.0D)
                || !same(spiritPowerProfile.resourceToCultivation().multiplier().evaluate(FormulaContext.EMPTY), 1.0D)
                || !same(spiritPowerProfile.resourceToCultivation().maxPerTick().evaluate(FormulaContext.EMPTY), 1.0D)
                || !same(spiritPowerProfile.burstAmount().evaluate(FormulaContext.EMPTY), 10.0D)
                || !same(qiProfile.startExp().evaluate(FormulaContext.EMPTY), 100.0D)
                || !same(spiritPowerProfile.startExp().evaluate(FormulaContext.EMPTY), 10.0D)
                || !same(spiritPower.value().max().evaluate(FormulaContext.EMPTY), 0.0D)
                || spiritPower.value().particleColor() != 0x66CCFF) {
            throw new IllegalStateException("Cultivation profile conversion settings were not decoded correctly");
        }
        CultivationAttachment spirit = new CultivationAttachment();
        CultivationAttachment mortal = new CultivationAttachment();
        if (!same(CultivationService.addProgress(mortal, qi, 120.0D, FormulaContext.EMPTY), 100.0D)
                || !same(CultivationService.addProgress(mortal, qi, 1.0D, FormulaContext.EMPTY), 0.0D)
                || !same(mortal.cultivationProgress(qiChain), 100.0D)) {
            throw new IllegalStateException("Mortal cultivation progress must stop at resource start_exp");
        }
        spirit.setRealmStage(requireHolder(MxtResourceKeys.REALM_STAGE, foundation));
        spirit.setCultivationProgress(qiChain, 40.0D);
        Holder<RealmStage> spiritPowerRealm = requireHolder(MxtResourceKeys.REALM_STAGE,
                Identifier.parse("mxt_test:spirit_power_refining"));
        CultivationAttachment multiChain = new CultivationAttachment();
        Holder<RealmStage> foundationHolder = requireHolder(MxtResourceKeys.REALM_STAGE, foundation);
        multiChain.setRealmStages(Map.of(foundationHolder.value().cultivation(), foundationHolder,
                spiritPowerRealm.value().cultivation(), spiritPowerRealm));
        multiChain.setCultivationProgress(qiChain, 12.0D);
        multiChain.setCultivationProgress(spiritPowerChain, 7.0D);
        if (multiChain.realmStages().size() != 2 || !same(multiChain.cultivationProgress(qiChain), 12.0D)
                || !same(multiChain.cultivationProgress(spiritPowerChain), 7.0D)) {
            throw new IllegalStateException("Multiple realm chains must retain independent chains and progress");
        }
        if (spirit.realmStage(spiritPowerChain) != null) {
            throw new IllegalStateException("A chain without a stage must resolve to null");
        }
        FormulaContext foundationContext = ResourceService.formulaContext(spirit, qi, FormulaContext.EMPTY);
        Bounds foundationBounds = ResourceService.resolveBounds(qi.value(), foundationContext)
                .orElseThrow(() -> new IllegalStateException("Foundation qi bounds were invalid"));
        if (!same(foundationBounds.max(), 170.0D) || !same(qiProfile.regen().evaluate(foundationContext), 0.35D)) {
            throw new IllegalStateException("Qi maximum and regeneration did not use foundation absorbed aura");
        }
        ResourceHolderAttachment holder = new ResourceHolderAttachment();
        ResourceService.initialize(holder, qi, foundationContext);
        ResourceService.regenerate(holder, qi, qiProfile.regen(), 4L, foundationContext);
        if (!same(holder.get(qi), 1.4D)) {
            throw new IllegalStateException("Qi regeneration did not use its dynamic formula");
        }
        spirit.setRealmStage(requireHolder(MxtResourceKeys.REALM_STAGE, coreForming));
        FormulaContext coreContext = ResourceService.formulaContext(spirit, qi, FormulaContext.EMPTY);
        Bounds coreBounds = ResourceService.resolveBounds(qi.value(), coreContext)
                .orElseThrow(() -> new IllegalStateException("Core-forming qi bounds were invalid"));
        if (!same(coreBounds.max(), 220.0D) || !same(qiProfile.regen().evaluate(coreContext), 0.45D)) {
            throw new IllegalStateException("Qi maximum and regeneration did not use realm rank");
        }
        ResourceService.change(holder, qi, 1_000.0D, coreContext);
        if (!same(holder.get(qi), 220.0D) || !same(holder.audit(qi).minSnapshot(), 0.0D)
                || !same(holder.audit(qi).maxSnapshot(), 220.0D)) {
            throw new IllegalStateException("Qi resource changes did not clamp to its dynamic maximum");
        }
        ResourceHolderAttachment draft = holder.copy();
        draft.set(qi, 2.0D);
        if (!same(holder.get(qi), 220.0D) || !same(holder.audit(qi).minSnapshot(), 0.0D)
                || !same(holder.audit(qi).maxSnapshot(), 220.0D)) {
            throw new IllegalStateException("Resource validation draft modified live server-resolved bounds");
        }

        // A datapack trigger rule is a reaction of its own: publishing its signal runs its condition
        // and action for the actor, with no ability and no subscription involved.
        ServerCache triggerCache = ServerCache.get()
                .orElseThrow(() -> new IllegalStateException("The server cache was not built before the audit"));
        if (triggerCache.triggerRules(TriggerSignals.BLOCK_BREAK).isEmpty()) {
            throw new IllegalStateException("The trigger rule test fixture was not indexed by its signal");
        }
        Pig triggered = new Pig(EntityType.PIG, triggerCache.server().overworld());
        publishBlockBreak(triggered);
        if (!same(triggered.getData(MxtAttachments.RESOURCE_HOLDER).get(qi), 1.0D)) {
            throw new IllegalStateException("A datapack trigger rule did not add its resource when its signal was published");
        }

        // Mastery is data-driven end to end: a trigger rule grows the counter, the level's own
        // requirement decides when the level moves, and the promotion recalculates the grants.
        Holder<CultivationTechnique> masteryTechnique = MxtDatapackRegistries
                .holder(MxtResourceKeys.CULTIVATION_TECHNIQUE, Identifier.parse("mxt_test:sword_manual"))
                .orElseThrow(() -> new IllegalStateException("The mastery test technique was not loaded"));
        Holder<Resource> mastery = requireHolder(MxtResourceKeys.RESOURCE, Identifier.parse("mxt_test:sword_mastery"));
        Holder<SkillStage> entryStage = requireHolder(MxtResourceKeys.SKILL_STAGE, Identifier.parse("mxt_test:sword_art_1"));
        Holder<SkillStage> stagedStage = requireHolder(MxtResourceKeys.SKILL_STAGE, Identifier.parse("mxt_test:sword_art_2"));
        Holder<Ability> stagedAbility = requireHolder(MxtResourceKeys.ABILITY, Identifier.parse("mxt_test:qingxiao_firebolt"));
        Pig student = new Pig(EntityType.PIG, triggerCache.server().overworld());
        SpiritIdentityAttachment identity = student.getData(MxtAttachments.SPIRIT_IDENTITY);
        identity.setLearnedTechniques(List.of(masteryTechnique));
        AbilityAttachment studentAbilities = student.getData(MxtAttachments.ABILITY_HOLDER);
        if (SkillStageService.currentStage(identity, masteryTechnique).filter(entryStage::equals).isEmpty()
                || studentAbilities.has(stagedAbility)) {
            throw new IllegalStateException("A technique did not start on its default stage without its staged ability");
        }
        publishBlockBreak(student);
        TechniqueMasteryService.tick(student);
        if (!same(student.getData(MxtAttachments.RESOURCE_HOLDER).get(mastery), 5.0D)
                || SkillStageService.currentStage(identity, masteryTechnique).filter(stagedStage::equals).isPresent()) {
            throw new IllegalStateException("A technique advanced before its mastery reached the level requirement");
        }
        publishBlockBreak(student);
        TechniqueMasteryService.tick(student);
        if (!same(student.getData(MxtAttachments.RESOURCE_HOLDER).get(mastery), 10.0D)
                || SkillStageService.currentStage(identity, masteryTechnique).filter(stagedStage::equals).isEmpty()
                || !studentAbilities.has(stagedAbility)) {
            throw new IllegalStateException("A technique did not advance and regrant when its mastery reached the requirement");
        }
        verifyTechniqueProgress(student, masteryTechnique, entryStage, stagedStage);
    }

    /**
     * The panel's rows are computed from synchronized state, so the same walk has to run on a server; this
     * pins the reported level, the rank pair, and both mastery progress modes.
     */
    private static void verifyTechniqueProgress(LivingEntity student, Holder<CultivationTechnique> technique,
                                                Holder<SkillStage> entryStage, Holder<SkillStage> stagedStage) {
        FormulaContext context = FormulaContext.of(student);
        List<TechniqueProgress.Entry> rows = TechniqueProgress.rows(
                student.getData(MxtAttachments.SPIRIT_IDENTITY),
                student.getData(MxtAttachments.RESOURCE_HOLDER), context);
        if (rows.size() != 1) {
            throw new IllegalStateException("Technique progress did not list exactly the learned technique");
        }
        TechniqueProgress.Entry row = rows.getFirst();
        if (!row.technique().equals(technique) || !stagedStage.equals(row.stage()) || !row.hasStage()
                || row.rank() != 1 || row.total() != 2 || !row.hasMastery()
                || !same(row.mastery(), 10.0D) || !same(row.currentRequirement(), 10.0D) || row.hasNextLevel()) {
            throw new IllegalStateException("Technique progress did not report the promoted level: " + row);
        }
        if (!same(TechniqueProgress.progress(row, Mode.ABSOLUTE).fraction(), 1.0D)) {
            throw new IllegalStateException("A finished climb did not fill the progress bar");
        }
        // At the entry level both modes measure the same span, because nothing was required to reach it.
        TechniqueProgress.Entry start = new TechniqueProgress.Entry(technique, entryStage, 0, 2, 0.0D, true, 4.0D, 10.0D);
        if (!same(TechniqueProgress.progress(start, Mode.ABSOLUTE).fraction(), 0.4D)
                || !same(TechniqueProgress.progress(start, Mode.WITHIN_LEVEL).fraction(), 0.4D)) {
            throw new IllegalStateException("Technique progress modes disagreed at the entry level");
        }
        // Above the entry level the relative mode subtracts what the current level already asked for.
        TechniqueProgress.Entry midway = new TechniqueProgress.Entry(technique, stagedStage, 1, 3, 10.0D, true, 18.0D, 25.0D);
        if (!same(TechniqueProgress.progress(midway, Mode.ABSOLUTE).fraction(), 0.72D)
                || !same(TechniqueProgress.progress(midway, Mode.WITHIN_LEVEL).fraction(), 8.0D / 15.0D)
                || !same(TechniqueProgress.progress(midway, Mode.WITHIN_LEVEL).done(), 8.0D)
                || !same(TechniqueProgress.progress(midway, Mode.WITHIN_LEVEL).span(), 15.0D)) {
            throw new IllegalStateException("Technique progress modes did not measure different spans");
        }
    }

    /**
     * Publishes the signal the mastery test rules react to, for one actor.
     */
    private static void publishBlockBreak(LivingEntity actor) {
        TriggerDispatcher.publish(TriggerSignals.BLOCK_BREAK, new TriggerContext().actor(actor)
                .level(actor.level()).formula(FormulaContext.of(actor)), actor.level().getGameTime());
    }

    /**
     * A refusal is part of the contract, not a silent no-op: the item gate names which of its checks
     * refused and the learning transaction names its own failure, so the exact values are pinned here.
     */
    private static void verifyTechniqueRefusals(ServerStartedEvent event) {
        Pig student = new Pig(EntityType.PIG, event.getServer().overworld());
        ItemStack lockedCarrot = new ItemStack(Items.CARROT);
        if (ItemQualityService.check(student, lockedCarrot)
                .filter(ItemQualityService.Failure.BINDING_CONDITIONS::equals).isEmpty()
                || ItemQualityService.canUse(student, lockedCarrot)) {
            throw new IllegalStateException("A failing item binding did not report its own gate failure");
        }
        ItemStack jadeSlip = new ItemStack(MxtItems.CULTIVATION_JADE_SLIP.get());
        if (ItemQualityService.check(student, jadeSlip).isPresent() || !ItemQualityService.canUse(student, jadeSlip)) {
            throw new IllegalStateException("A usable technique item was refused by its gate");
        }
        SpiritIdentityAttachment identity = student.getData(MxtAttachments.SPIRIT_IDENTITY);
        FormulaContext context = FormulaContext.of(student);
        Holder<CultivationTechnique> sword = requireHolder(MxtResourceKeys.CULTIVATION_TECHNIQUE, Identifier.parse("mxt_test:sword_manual"));
        Holder<CultivationTechnique> body = requireHolder(MxtResourceKeys.CULTIVATION_TECHNIQUE, Identifier.parse("mxt_test:body_manual"));
        if (!TechniqueService.learn(student, identity, sword, context).learned()) {
            throw new IllegalStateException("The refusal audit could not learn its first technique");
        }
        if (TechniqueService.learn(student, identity, sword, context).failure() != Failure.ALREADY_LEARNED
                || TechniqueService.learn(student, identity, body, context).failure() != Failure.CONFLICT) {
            throw new IllegalStateException("A rejected learning attempt did not report its own failure");
        }
    }

    /**
     * The two sample manuals, one held and one instant. The hold is asserted as data rather than as a
     * simulated keypress, which also keeps this runnable on a server where no client holds a button down.
     */
    private static void verifySampleTechniques() {
        Holder<CultivationTechnique> azureWater = requireHolder(MxtResourceKeys.CULTIVATION_TECHNIQUE,
                Identifier.parse("mxt_test:azure_water_manual"));
        Holder<CultivationTechnique> ironBody = requireHolder(MxtResourceKeys.CULTIVATION_TECHNIQUE,
                Identifier.parse("mxt_test:iron_body_manual"));
        if (azureWater.value().icon().flatMap(IconReference::texture).isEmpty()
                || ironBody.value().icon().flatMap(IconReference::stack).filter(stack -> stack.is(Items.WRITTEN_BOOK)).isEmpty())
            throw new IllegalStateException("A sample technique did not keep its own icon branch");
        if (azureWater.value().grantedAbilities().isEmpty() || ironBody.value().defaultStage().isEmpty())
            throw new IllegalStateException("A sample technique did not decode its grants or its skill chain");

        // The held manual declares a duration and the instant one does not, so both paths stay covered.
        TechniqueBinding held = ItemBindingService.technique(new ItemStack(MxtTestTechniqueItems.AZURE_WATER_MANUAL.get()))
                .orElseThrow(() -> new IllegalStateException("The held manual did not resolve its binding"));
        if (held.learnTime() != 60 || !held.requiresHold())
            throw new IllegalStateException("The held manual did not keep its learn_time: " + held.learnTime());
        TechniqueBinding instant = ItemBindingService.technique(new ItemStack(MxtTestTechniqueItems.IRON_BODY_MANUAL.get()))
                .orElseThrow(() -> new IllegalStateException("The instant manual did not resolve its binding"));
        if (instant.learnTime() != TechniqueBinding.NO_HOLD || instant.requiresHold())
            throw new IllegalStateException("An unheld manual reported a hold: " + instant.learnTime());

        verifyHoldAnimations(held, instant);

        // A hold must not also learn on the click that starts it: the click is deliberately left unclaimed
        // for a held binding, so `use` answering false here is what lets the use cycle begin.
        Pig student = new Pig(EntityType.PIG,
                ServerCache.get().orElseThrow(() -> new IllegalStateException("The sample audit needs the server cache"))
                        .server().overworld());
        SpiritIdentityAttachment identity = student.getData(MxtAttachments.SPIRIT_IDENTITY);
        if (TechniqueItemService.use(student, new ItemStack(MxtTestTechniqueItems.AZURE_WATER_MANUAL.get()))
                || identity.learnedTechniques().stream()
                .anyMatch(value -> HolderHelper.id(value).equals(Identifier.parse("mxt_test:azure_water_manual"))))
            throw new IllegalStateException("A held manual claimed the click or taught on it");
        // ...while the instant one still claims the click and teaches on it, which it must keep doing.
        if (!TechniqueItemService.use(student, new ItemStack(MxtTestTechniqueItems.IRON_BODY_MANUAL.get()))
                || identity.learnedTechniques().stream()
                .noneMatch(value -> HolderHelper.id(value).equals(Identifier.parse("mxt_test:iron_body_manual"))))
            throw new IllegalStateException("An instant manual stopped teaching on use");

        verifyLearnFeedback();
        verifyHoldLifecycle();
        verifyRepairSweep();
    }

    /**
     * Checks that the repair sweep keeps exactly the references that still resolve: if {@code resolves}
     * answered true for a stale id, the sweep would report a clean bill of health and change nothing.
     */
    private static void verifyRepairSweep() {
        Reference<CultivationTechnique> real = MxtDatapackRegistries
                .holder(MxtResourceKeys.CULTIVATION_TECHNIQUE, Identifier.parse("mxt_test:sword_manual"))
                .orElseThrow(() -> new IllegalStateException("The repair audit needs a real technique"));

        // A live technique resolves; an id nobody defines does not. The second is the shape a removed
        // data pack file leaves in saved data, and the whole command turns on telling them apart.
        if (!TechniqueCommand.resolves(real))
            throw new IllegalStateException("The repair sweep called a live technique stale");
        if (!TechniqueCommand.resolvesStage(MxtDatapackRegistries
                .holder(MxtResourceKeys.SKILL_STAGE, Identifier.parse("mxt_test:sword_art_1"))
                .orElseThrow(() -> new IllegalStateException("The repair audit needs a real skill stage"))))
            throw new IllegalStateException("The repair sweep called a live skill stage stale");

        // A clean list is left completely alone, and the sweep reports nothing - so running the command
        // on healthy data is a no-op rather than a way to lose techniques.
        List<Identifier> clean = new ArrayList<>();
        List<Holder<CultivationTechnique>> untouched =
                TechniqueCommand.prune(new ArrayList<>(List.of(real)), clean);
        if (untouched.size() != 1 || !clean.isEmpty())
            throw new IllegalStateException("The repair sweep removed something from an already clean list");

        // A duplicate of a live entry goes: two copies of one technique would double every passive
        // modifier the definition grants, which is a real corruption the sweep should clear.
        List<Identifier> dupes = new ArrayList<>();
        if (TechniqueCommand.prune(new ArrayList<>(List.of(real, real)), dupes).size() != 1
                || dupes.size() != 1)
            throw new IllegalStateException("The repair sweep kept a duplicate technique");

        // A stage map whose technique is gone must lose the entry rather than keep a dangling level.
        Map<Holder<CultivationTechnique>, Holder<SkillStage>> stages = new LinkedHashMap<>();
        Reference<SkillStage> stage = MxtDatapackRegistries
                .holder(MxtResourceKeys.SKILL_STAGE, Identifier.parse("mxt_test:sword_art_1"))
                .orElseThrow(() -> new IllegalStateException("The repair audit needs a real skill stage"));
        stages.put(real, stage);
        List<Identifier> removedStages = new ArrayList<>();
        Map<Holder<CultivationTechnique>, Holder<SkillStage>> sweptStages =
                TechniqueCommand.pruneStages(stages, removedStages);
        if (sweptStages.size() != 1 || !removedStages.isEmpty())
            throw new IllegalStateException("The repair sweep dropped a healthy stage entry");

        // And the messages the command prints must exist, including for the "nothing to do" case - a
        // missing key would surface as a raw translation id in chat.
        for (String key : List.of("command.mxt.technique.repair.clean", "command.mxt.technique.repair.done",
                "command.mxt.technique.repair.dry", "command.mxt.technique.drop.done",
                "command.mxt.technique.drop.absent", "command.mxt.technique.diagnose.item",
                "command.mxt.technique.diagnose.no_binding", "command.mxt.technique.diagnose.binding",
                "command.mxt.technique.diagnose.gate", "command.mxt.technique.diagnose.known",
                "command.mxt.technique.diagnose.condition", "command.mxt.technique.diagnose.cooldown",
                "command.mxt.technique.diagnose.empty_hand", "command.mxt.technique.diagnose.hold",
                "command.mxt.technique.diagnose.ending")) {
            if (Component.translatable(key).getString().equals(key))
                throw new IllegalStateException("A repair message key has no translation: " + key);
        }

        verifyStaleReferenceDecode();
        verifyAzureManualIsLearnable();
    }

    /**
     * Reproduces the reported situation - the azure manual cannot be learned in game - by running every step
     * of the real path against a real entity, so whichever gate refuses gets named instead of guessed at.
     */
    private static void verifyAzureManualIsLearnable() {
        ServerLevel level = ServerCache.get()
                .orElseThrow(() -> new IllegalStateException("The sample audit needs the server cache"))
                .server().overworld();
        Pig student = new Pig(EntityType.PIG, level);
        ItemStack manual = new ItemStack(MxtTestTechniqueItems.AZURE_WATER_MANUAL.get());

        // The binding must resolve at all, or nothing downstream can work.
        if (ItemBindingService.technique(manual).isEmpty())
            throw new IllegalStateException("The azure manual resolves no technique binding");

        // The item gate runs at HIGHEST on Start/Tick and cancels there, which is exactly what a pose
        // that appears and then vanishes looks like.
        if (!ItemQualityService.canUse(student, manual))
            throw new IllegalStateException("The item gate refuses the azure manual");

        // The technique's own learn condition, evaluated the way the real transaction does.
        Holder<CultivationTechnique> technique = MxtDatapackRegistries
                .holder(MxtResourceKeys.CULTIVATION_TECHNIQUE, Identifier.parse("mxt_test:azure_water_manual"))
                .orElseThrow(() -> new IllegalStateException("The azure technique does not resolve"));
        if (!technique.value().learnCondition().test(student, FormulaContext.of(student)))
            throw new IllegalStateException("The azure technique's learn condition refuses this entity");

        // And the transaction itself must succeed on a holder who knows nothing yet.
        SpiritIdentityAttachment spirit = student.getData(MxtAttachments.SPIRIT_IDENTITY);
        Result result = TechniqueService.learn(student, spirit, technique, FormulaContext.of(student));
        if (!result.learned())
            throw new IllegalStateException("Learning the azure technique was refused: " + result.failure());
    }

    /**
     * Decodes an attachment payload holding a technique id that no longer exists: the list codec skips the
     * elements that fail, so a stale id costs only itself and the rest of the attachment still loads.
     */
    private static void verifyStaleReferenceDecode() {
        ServerCache.get().orElseThrow(() -> new IllegalStateException("The sample audit needs the server cache"));
        String json = """
                {
                  "learned_techniques": ["mxt_test:sword_manual", "mxt_test:never_existed"],
                  "titles": []
                }
                """;
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE,
                ServerCache.get().orElseThrow().server().registryAccess());
        SpiritIdentityAttachment decoded = SpiritIdentityAttachment.CODEC.codec()
                .parse(ops, JsonParser.parseString(json))
                .getOrThrow(error -> new IllegalStateException("A stale technique id took down the whole attachment: " + error));

        // The good entry survives and the bad one is gone, rather than the list dying with it.
        if (decoded.learnedTechniques().size() != 1)
            throw new IllegalStateException("A stale technique id removed healthy entries too: "
                    + decoded.learnedTechniques().size());
        if (!HolderHelper.id(decoded.learnedTechniques().getFirst()).equals(Identifier.parse("mxt_test:sword_manual")))
            throw new IllegalStateException("The surviving technique is not the one that should have loaded");
    }

    /**
     * Drives a hold through the real vanilla use cycle, because calling the mod's own handlers directly
     * passed while the in-game hold was broken; it covers the use duration and animation the item supplies.
     */
    private static void verifyHoldLifecycle() {
        ServerLevel level = ServerCache.get()
                .orElseThrow(() -> new IllegalStateException("The sample audit needs the server cache"))
                .server().overworld();

        ItemStack manual = new ItemStack(MxtTestTechniqueItems.AZURE_WATER_MANUAL.get());
        ItemStack instant = new ItemStack(MxtTestTechniqueItems.IRON_BODY_MANUAL.get());
        TechniqueBinding held = ItemBindingService.technique(manual)
                .orElseThrow(() -> new IllegalStateException("The held manual resolves no binding"));
        if (held.learnTime() != 60 || !held.requiresHold())
            throw new IllegalStateException("The held manual did not keep its learn_time: " + held.learnTime());

        // Step 0: the lookup the mixin reads was built, from the data pack, for the right items only.
        if (TechniqueHoldLookup.hold(manual) == null)
            throw new IllegalStateException("The hold lookup has no entry for the held manual");
        if (TechniqueHoldLookup.hold(instant) != null)
            throw new IllegalStateException("The hold lookup listed an instant manual as a hold");

        // Step 1: the item answers the two questions the use cycle asks, from the binding's own values.
        // A plain item would answer 0 and NONE here, so this is also what proves the mixin applied.
        Pig probe = new Pig(EntityType.PIG, level);
        int duration = manual.getItem().getUseDuration(manual, probe);
        if (duration != held.learnTime())
            throw new IllegalStateException("The manual reported a duration of " + duration
                    + " instead of its learn_time of " + held.learnTime());
        if (manual.getItem().getUseAnimation(manual) != held.holdAnimation())
            throw new IllegalStateException("The manual reported the wrong pose: "
                    + manual.getItem().getUseAnimation(manual) + " instead of " + held.holdAnimation());

        // Step 2: the real cycle. It must not finish early, and it must finish at the binding's duration.
        Pig reader = new Pig(EntityType.PIG, level);
        reader.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MxtTestTechniqueItems.AZURE_WATER_MANUAL.get()));
        ItemStack handStack = reader.getItemInHand(InteractionHand.MAIN_HAND);
        int before = handStack.getCount();
        if (handStack.getItem().getUseDuration(handStack, reader) != 60)
            throw new IllegalStateException("The held manual did not report a hold to run");

        reader.startUsingItem(InteractionHand.MAIN_HAND);
        if (!reader.isUsingItem())
            throw new IllegalStateException("The hold did not start, so no cycle can complete");
        for (int i = 0; i < 30; i++) reader.tick();
        if (taught(reader, "mxt_test:azure_water_manual"))
            throw new IllegalStateException("The hold taught after 30 ticks, so its duration is not being honoured");
        for (int i = 0; i < 35; i++) reader.tick();

        // Step 3: it taught at the end, and the manual survived - nothing in this design is edible.
        if (!taught(reader, "mxt_test:azure_water_manual"))
            throw new IllegalStateException("A completed hold taught nothing");
        if (reader.getItemInHand(InteractionHand.MAIN_HAND).getCount() != before)
            throw new IllegalStateException("A completed hold consumed the manual");
        if (reader.isUsingItem())
            throw new IllegalStateException("A completed hold left the entity still using the manual");

        // Step 4: reading a manual that is already known is a harmless no-op, not a way to lose it.
        Pig veteran = new Pig(EntityType.PIG, level);
        veteran.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MxtTestTechniqueItems.AZURE_WATER_MANUAL.get()));
        ItemStack second = veteran.getItemInHand(InteractionHand.MAIN_HAND);
        int count = second.getCount();
        for (int round = 0; round < 2; round++) {
            veteran.startUsingItem(InteractionHand.MAIN_HAND);
            for (int i = 0; i < 65; i++) veteran.tick();
        }
        if (veteran.getItemInHand(InteractionHand.MAIN_HAND).getCount() != count)
            throw new IllegalStateException("Re-reading a known manual destroyed it");

        verifyHoldProgress();
        verifyCooldownOnAnyOutcome();
    }

    /**
     * The reading cooldown is charged for the attempt rather than the result: a refused read must still
     * cost one, or a manual the holder cannot learn would be free to read over and over.
     */
    private static void verifyCooldownOnAnyOutcome() {
        // Disabled by configuration: there is no cooldown to observe, and asserting one would be asserting
        // something the operator switched off.
        if (MxtServerConfig.INSTANCE.cultivation.techniqueLearnCooldown.getValue() <= 0) return;

        ServerLevel level = ServerCache.get()
                .orElseThrow(() -> new IllegalStateException("The sample audit needs the server cache"))
                .server().overworld();
        FakePlayer holder = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "mxt-audit"));
        ItemStack manual = new ItemStack(MxtTestTechniqueItems.AZURE_WATER_MANUAL.get());
        holder.setItemInHand(InteractionHand.MAIN_HAND, manual);

        TechniqueItemService.onUseFinish(new Finish(holder, manual.copy(), 0, manual.copy()));
        int known = holder.getData(MxtAttachments.SPIRIT_IDENTITY).learnedTechniques().size();
        if (known == 0)
            throw new IllegalStateException("The first read of the cooldown check did not teach anything");
        if (!holder.getCooldowns().isOnCooldown(manual))
            throw new IllegalStateException("A read that taught did not put the manual on cooldown");

        holder.getCooldowns().removeCooldown(holder.getCooldowns().getCooldownGroup(manual));
        if (holder.getCooldowns().isOnCooldown(manual))
            throw new IllegalStateException("The cooldown check could not clear the cooldown it observes");

        // Already known, so this read is refused - and still has to cost a cooldown.
        TechniqueItemService.onUseFinish(new Finish(holder, manual.copy(), 0, manual.copy()));
        if (holder.getData(MxtAttachments.SPIRIT_IDENTITY).learnedTechniques().size() != known)
            throw new IllegalStateException("The second read of the cooldown check was expected to be refused");
        if (!holder.getCooldowns().isOnCooldown(manual))
            throw new IllegalStateException("A refused read did not put the manual on cooldown");
    }

    /**
     * The percentage shown while a manual is read, where the ends matter most: an off-by-one would show a
     * full bar a tick early, and a zero-length hold must not divide by zero.
     */
    private static void verifyHoldProgress() {
        if (TechniqueItemService.holdPercent(60, 60) != 0)
            throw new IllegalStateException("A hold that has not started is not at zero percent");
        if (TechniqueItemService.holdPercent(60, 30) != 50)
            throw new IllegalStateException("A half-finished hold is not at fifty percent");
        if (TechniqueItemService.holdPercent(60, 0) != 100)
            throw new IllegalStateException("A finished hold is not at full");
        if (TechniqueItemService.holdPercent(0, 5) != 100)
            throw new IllegalStateException("A zero-length hold did not resolve to full without dividing by zero");
        if (TechniqueItemService.holdPercent(60, -3) != 100 || TechniqueItemService.holdPercent(60, 999) != 0)
            throw new IllegalStateException("The hold percentage is not clamped to its ends");

        // The last tick a read ever sees arrives with one tick left, since the tick after it completes the
        // read; reporting that honestly would leave the bar stuck at 98% on every successful read.
        if (TechniqueItemService.displayPercent(60, 1) != 100)
            throw new IllegalStateException("The final tick of a read does not show a full bar");
        if (TechniqueItemService.displayPercent(60, 60) != 0 || TechniqueItemService.displayPercent(60, 30) != 50)
            throw new IllegalStateException("The last-tick rule leaked into the rest of the read");

        // Once the client's own count runs out the pose is kept alive by folding the count back into the
        // positive range, and it has to land on the phase the pose would have reached anyway.
        for (int remaining = 0; remaining > -25; remaining--) {
            int wrapped = TechniqueItemService.loopingUseRemaining(remaining);
            if (wrapped <= 0)
                throw new IllegalStateException("A folded count is not positive and would drop the pose: " + wrapped);
            // The pose code reads the remainder, so the remainder must keep counting down by one per tick
            // and wrap off the bottom of the loop back onto its top.
            if (Math.floorMod(wrapped, 10) != Math.floorMod(remaining, 10))
                throw new IllegalStateException("A folded count is out of phase at " + remaining + ": " + wrapped);
        }

        String key = "actionbar.mxt.technique.holding";
        if (Component.translatable(key).getString().equals(key))
            throw new IllegalStateException("The hold progress message has no translation: " + key);
        // It must carry both the bar and the number, or it would not show progress at all.
        if (Component.translatable(key, "#---------", 10).getString().equals(key))
            throw new IllegalStateException("The hold progress message ignores its arguments");
    }

    private static boolean taught(Pig reader, String technique) {
        Identifier id = Identifier.parse(technique);
        return reader.getData(MxtAttachments.SPIRIT_IDENTITY).learnedTechniques().stream()
                .anyMatch(value -> HolderHelper.id(value).equals(id));
    }

    /**
     * The feedback a successful learn produces, and the cooldown gate behind it. The success message was once
     * missing while refusals were reported, a one-line bug with no crash and no log, so its key is asserted.
     */
    private static void verifyLearnFeedback() {
        // Both messages belong to one family and must stay in step: a player who sees the refusal
        // message must also be able to see the success one.
        for (String key : List.of("actionbar.mxt.technique.learned", "actionbar.mxt.technique.failed"))
            if (Component.translatable(key).getString().equals(key))
                throw new IllegalStateException("A learning message has no translation: " + key);
        // The success message has to name the technique, or "learned" would not say what was learned.
        if (!Component.translatable("actionbar.mxt.technique.learned").getString().contains("%s"))
            throw new IllegalStateException("The success message must name the technique it reports");

        int cooldown = MxtServerConfig.INSTANCE.cultivation.techniqueLearnCooldown.getValue();
        if (cooldown < 0)
            throw new IllegalStateException("The learn cooldown must not be negative: " + cooldown);
        // A pig is not a player, so `learn` must survive being asked to cool down for one rather than
        // throwing on the cast.
        Pig animal = new Pig(EntityType.PIG,
                ServerCache.get().orElseThrow(() -> new IllegalStateException("The sample audit needs the server cache"))
                        .server().overworld());
        SpiritIdentityAttachment identity = animal.getData(MxtAttachments.SPIRIT_IDENTITY);
        if (!TechniqueItemService.use(animal, new ItemStack(MxtTestTechniqueItems.IRON_BODY_MANUAL.get()))
                || identity.learnedTechniques().isEmpty())
            throw new IllegalStateException("A non-player learner must still learn without a cooldown");
    }

    /**
     * The hold animation field: the declared value, the default, and the values the whitelist refuses. The
     * refusals are decoded from JSON rather than read off the list, so the list is shown to be consulted.
     */
    private static void verifyHoldAnimations(TechniqueBinding declared, TechniqueBinding instant) {
        if (declared.holdAnimation() != ItemUseAnimation.BRUSH)
            throw new IllegalStateException("A held manual did not keep its declared hold_animation: "
                    + declared.holdAnimation());
        if (instant.holdAnimation() != TechniqueBinding.DEFAULT_HOLD_ANIMATION)
            throw new IllegalStateException("An instant manual did not fall back to the default animation: "
                    + instant.holdAnimation());

        // The jade slip asks for a hold without naming an animation, which is the case the default covers.
        TechniqueBinding undeclared = ItemBindingService.technique(new ItemStack(MxtItems.CULTIVATION_JADE_SLIP.get()))
                .orElseThrow(() -> new IllegalStateException("The jade slip did not resolve its binding"));
        if (!undeclared.requiresHold() || undeclared.holdAnimation() != TechniqueBinding.DEFAULT_HOLD_ANIMATION)
            throw new IllegalStateException("An undeclared hold_animation did not take the default: "
                    + undeclared.holdAnimation());

        // Every allowed animation has to survive a round trip, or the whitelist would advertise values the
        // codec rejects; the binding holds a registry reference, so the server's registries are needed.
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE,
                ServerCache.get().orElseThrow(() -> new IllegalStateException("The sample audit needs the server cache"))
                        .server().registryAccess());
        for (ItemUseAnimation animation : TechniqueBinding.ALLOWED_ANIMATIONS) {
            DataResult<TechniqueBinding> decoded = TechniqueBinding.CODEC.parse(ops,
                    holdBindingJson(animation.getSerializedName()));
            if (decoded.result().isEmpty() || decoded.result().orElseThrow().holdAnimation() != animation)
                throw new IllegalStateException("The whitelist allowed an animation the codec rejects: " + animation
                        + " (" + decoded.error().map(DataResult.Error::message).orElse("no value") + ")");
        }

        // SPYGLASS is refused because vanilla reaches outside the item from it: the pose *is*
        // Player#isScoping, and it is the one pose that renders no item at all.
        for (String refused : List.of("spyglass", "eat", "drink", "bow", "trident", "crossbow", "spear")) {
            if (TechniqueBinding.CODEC.parse(ops, holdBindingJson(refused)).result().isPresent())
                throw new IllegalStateException("The whitelist let through a side-effecting animation: " + refused);
        }
        // An animation without a hold is rejected too, since nothing would ever play it; "block" *is* the
        // default, so writing it on an instant binding asks for nothing and is correctly accepted.
        Map<JsonElement, JsonElement> instantFields = new LinkedHashMap<>();
        instantFields.put(new JsonPrimitive("items"), new JsonPrimitive("minecraft:stick"));
        instantFields.put(new JsonPrimitive("technique"), new JsonPrimitive("mxt_test:qingxiao_breathing_manual"));
        instantFields.put(new JsonPrimitive("hold_animation"), new JsonPrimitive("brush"));
        DataResult<TechniqueBinding> orphaned = TechniqueBinding.CODEC.parse(ops,
                JsonOps.INSTANCE.createMap(instantFields));
        if (orphaned.result().isPresent())
            throw new IllegalStateException("An animation on an instant binding must not decode");

        // ...while the same field on a *held* binding is accepted, so the check above is about the
        // missing learn_time and not about the animation.
        Map<JsonElement, JsonElement> heldFields = new LinkedHashMap<>();
        heldFields.put(new JsonPrimitive("items"), new JsonPrimitive("minecraft:stick"));
        heldFields.put(new JsonPrimitive("technique"), new JsonPrimitive("mxt_test:qingxiao_breathing_manual"));
        heldFields.put(new JsonPrimitive("learn_time"), new JsonPrimitive(20));
        heldFields.put(new JsonPrimitive("hold_animation"), new JsonPrimitive("brush"));
        if (TechniqueBinding.CODEC.parse(ops, JsonOps.INSTANCE.createMap(heldFields)).result().isEmpty())
            throw new IllegalStateException("A held binding must accept a non-default animation");
    }

    /**
     * A minimal held binding carrying one animation, for the codec checks above.
     */
    private static JsonElement holdBindingJson(String animation) {
        Map<JsonElement, JsonElement> fields = new LinkedHashMap<>();
        fields.put(new JsonPrimitive("items"), new JsonPrimitive("minecraft:stick"));
        fields.put(new JsonPrimitive("technique"), new JsonPrimitive("mxt_test:qingxiao_breathing_manual"));
        fields.put(new JsonPrimitive("learn_time"), new JsonPrimitive(20));
        fields.put(new JsonPrimitive("hold_animation"), new JsonPrimitive(animation));
        return JsonOps.INSTANCE.createMap(fields);
    }

    private static void verifyFormulaDiagnostics() {
        // A malformed formula is a decode error rather than a thrown exception, so the registry
        // loader can list every broken formula of one load instead of stopping at the first one.
        DataResult<Expression> broken = Expression.decode("1 +");
        if (broken.result().isPresent()) {
            throw new IllegalStateException("A malformed expression must not decode");
        }
        String brokenMessage = broken.error().map(DataResult.Error::message).orElse("");
        if (!brokenMessage.contains("Invalid number expression '1 +'")) {
            throw new IllegalStateException("A malformed expression must name its source: " + brokenMessage);
        }
        // Every problem of one expression is collected, not just the first one.
        List<String> problems = new Expression("1 +", Map.of("unused", new Constant(1.0D))).problems();
        if (problems.size() < 2) {
            throw new IllegalStateException("Expression problems must be collected together, got " + problems);
        }
        if (Expression.decode("level * 2").result().isEmpty()) {
            throw new IllegalStateException("A valid expression must decode");
        }
        // Provider-level checks that used to abort the load on their own are decode errors too.
        if (WeightedList.MAP_CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("{\"distribution\":[]}"))
                .result().isPresent()) {
            throw new IllegalStateException("An empty weighted list must not decode");
        }
        if (ContextVariable.MAP_CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("{\"variable\":\" \"}"))
                .result().isPresent()) {
            throw new IllegalStateException("A blank context variable must not decode");
        }
    }

    /**
     * The information panel splits each row between its label and its value; the arithmetic is audited with
     * the widths a narrow window produces, since one global label width is what used to cut values off.
     */
    private static void verifyInformationColumns() {
        // A narrow window: 90 px of row, the widest label is 36 px, the value wants 54 px.
        Columns fitted = InformationHelper.columns(90, 36, 54);
        if (fitted.nameWidth() != 28 || fitted.valueWidth() != 54)
            throw new IllegalStateException("Information columns did not give the value its own width: " + fitted);
        // A short value leaves the full label width alone, which is what keeps values aligned.
        Columns aligned = InformationHelper.columns(90, 36, 6);
        if (aligned.nameWidth() != 36 || aligned.valueWidth() != 46)
            throw new IllegalStateException("Information columns did not keep the label width while it fits: " + aligned);
        // A value that cannot fit anywhere still keeps a quarter of the row for the label.
        Columns floored = InformationHelper.columns(90, 36, 200);
        if (floored.nameWidth() != 22 || floored.valueWidth() != 60)
            throw new IllegalStateException("Information columns did not floor the label width: " + floored);
        Columns degenerate = InformationHelper.columns(1, 36, 200);
        if (degenerate.nameWidth() < 0 || degenerate.valueWidth() < 1)
            throw new IllegalStateException("Information columns went negative on a degenerate row: " + degenerate);
    }

    /**
     * One icon type is shared by abilities, resources, badges, forging methods and techniques, so its two
     * branches are pinned here: a bare string is a texture, an object is an item, anything else is rejected.
     */
    private static void verifyIconReferences() {
        if (IconReference.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"mxt:textures/gui/badge/star.png\""))
                .result().flatMap(IconReference::texture)
                .filter(Identifier.parse("mxt:textures/gui/badge/star.png")::equals).isEmpty())
            throw new IllegalStateException("An icon did not decode a bare string as its texture branch");
        if (IconReference.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"id\":\"minecraft:diamond\"}"))
                .result().flatMap(IconReference::stack)
                .filter(stack -> stack.is(Items.DIAMOND)).isEmpty())
            throw new IllegalStateException("An icon did not decode an object as its item branch");
        // An item is only reachable through the object form, because a bare string is claimed by the texture
        // branch above. This pins that ordering rather than leaving it to whichever branch happens to win.
        if (IconReference.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"minecraft:diamond\""))
                .result().flatMap(IconReference::texture).isEmpty())
            throw new IllegalStateException("A bare item id was not claimed by the texture branch");
        for (String malformed : List.of("{}", "[]", "7")) {
            if (IconReference.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(malformed)).result().isPresent())
                throw new IllegalStateException("An icon accepted a malformed reference: " + malformed);
        }
        // The definitions that carry an icon must have kept it through their own codecs.
        Holder<Ability> firebolt = requireHolder(MxtResourceKeys.ABILITY, Identifier.parse("mxt_test:firebolt"));
        if (firebolt.value().icon().flatMap(IconReference::stack)
                .filter(stack -> stack.is(Items.FIRE_CHARGE)).isEmpty())
            throw new IllegalStateException("An ability did not keep its icon");
        Holder<Resource> spiritPower = requireHolder(MxtResourceKeys.RESOURCE, Identifier.parse("mxt_test:spirit_power"));
        if (spiritPower.value().icon().flatMap(IconReference::texture).isEmpty())
            throw new IllegalStateException("A resource did not keep its texture icon");
        Holder<Badge> badge = requireHolder(MxtResourceKeys.BADGE, Identifier.parse("mxt_test:sprite"));
        if (badge.value().icon().texture().filter(Identifier.parse("mxt:textures/gui/badge/star.png")::equals).isEmpty())
            throw new IllegalStateException("A badge did not keep its icon");
        ForgingMethod polish = MxtDatapackRegistries.get(MxtResourceKeys.FORGING_METHOD, Identifier.parse("mxt_test:polish"))
                .orElseThrow(() -> new IllegalStateException("The icon audit needs mxt_test:polish"));
        if (!polish.iconStack().is(Items.DIAMOND)
                || !polish.displayName(Identifier.parse("mxt_test:polish")).getString()
                .equals(new ItemStack(Items.DIAMOND).getHoverName().getString()))
            throw new IllegalStateException("A forging method did not keep its icon or the name it borrows from it");
        // Both technique fixtures carry an icon, one of each branch.
        CultivationTechnique swordManual = MxtDatapackRegistries
                .get(MxtResourceKeys.CULTIVATION_TECHNIQUE, Identifier.parse("mxt_test:sword_manual"))
                .orElseThrow(() -> new IllegalStateException("The icon audit needs mxt_test:sword_manual"));
        CultivationTechnique bodyManual = MxtDatapackRegistries
                .get(MxtResourceKeys.CULTIVATION_TECHNIQUE, Identifier.parse("mxt_test:body_manual"))
                .orElseThrow(() -> new IllegalStateException("The icon audit needs mxt_test:body_manual"));
        if (swordManual.icon().flatMap(IconReference::texture).isEmpty()
                || bodyManual.icon().flatMap(IconReference::stack).filter(stack -> stack.is(Items.BOOK)).isEmpty()) {
            throw new IllegalStateException("A technique did not keep its icon");
        }
    }

    private static boolean same(double left, double right) {
        return Math.abs(left - right) < 0.000001D;
    }

    private static <T> Holder<T> requireHolder(ResourceKey<? extends Registry<T>> registry, Identifier id) {
        return MxtDatapackRegistries.holder(registry, id)
                .orElseThrow(() -> new IllegalStateException("Missing test definition " + id + " in " + registry.identifier()));
    }

    private static void verifyClientDefinitions(ServerStartedEvent event) {
        Resource qi = MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, Identifier.parse("mxt_test:qi"))
                .orElseThrow(() -> new IllegalStateException("Qi resource test definition was not loaded"));
        Resource divineSense = MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, Identifier.parse("mxt_test:divine_sense"))
                .orElseThrow(() -> new IllegalStateException("Divine-sense resource test definition was not loaded"));
        Resource spiritPower = MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, Identifier.parse("mxt_test:spirit_power"))
                .orElseThrow(() -> new IllegalStateException("Spirit-power resource test definition was not loaded"));
        Resource soulPower = MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, Identifier.parse("mxt_test:soul_power"))
                .orElseThrow(() -> new IllegalStateException("Soul-power resource test definition was not loaded"));
        if (MxtDatapackRegistries.holders(MxtResourceKeys.RESOURCE)
                .filter(resource -> MOD_ID.equals(resource.key().identifier().getNamespace()))
                .flatMap(resource -> resource.value().bars().stream())
                .anyMatch(bar -> !(bar.renderer() instanceof OriginsRenderData))) {
            throw new IllegalStateException("All test resource bars must use the Origins renderer");
        }
        qi.bars().stream()
                .filter(bar -> bar.context().layout() == Layout.TARGET_OVERLAY)
                .findFirst().orElseThrow(() -> new IllegalStateException("Target resource-bar test definition was not loaded"));
        ResourceBar qiHud = qi.bars().stream().filter(bar -> bar.context().layout() == Layout.SELF_HUD)
                .findFirst().orElseThrow(() -> new IllegalStateException("Qi resource-bar test definition was not loaded"));
        if (qiHud.valueDisplay() != ValueDisplay.CURRENT_AND_MAXIMUM || qiHud.anchor() != Anchor.LEFT) {
            throw new IllegalStateException("Resource-bar left-column configuration was not retained");
        }
        ResourceBar divineSenseHud = divineSense.bars().getFirst();
        if (divineSenseHud.anchor() != Anchor.RIGHT || divineSenseHud.order() != 0) {
            throw new IllegalStateException("Resource-bar right-column order configuration was not retained");
        }
        ResourceBar soulPowerHud = soulPower.bars().stream()
                .filter(bar -> bar.context().layout() == Layout.SELF_HUD)
                .findFirst().orElseThrow(() -> new IllegalStateException("Soul-power resource-bar test definition was not loaded"));
        if (!(soulPowerHud.visibility() instanceof NonZeroVisibility)) {
            throw new IllegalStateException("Non-zero resource-bar visibility was not decoded");
        }
        ResourceBar boss = spiritPower.bars().stream().filter(bar -> bar.context().layout() == Layout.BOSS_OVERLAY)
                .findFirst().orElseThrow(() -> new IllegalStateException("Boss resource-bar test definition was not loaded"));
        if (boss.context().layout() != Layout.BOSS_OVERLAY) {
            throw new IllegalStateException("Boss resource-bar context was not retained");
        }
        if (spiritPower.bars().stream().noneMatch(bar -> bar.context() == ActualConcentrationContext.INSTANCE)) {
            throw new IllegalStateException("Actual-concentration resource-bar context was not retained");
        }
        AuraZone visuals = MxtDatapackRegistries.get(MxtResourceKeys.AURA_ZONE, Identifier.parse("mxt_test:firelands"))
                .orElseThrow(() -> new IllegalStateException("Aura visual test definition was not loaded"));
        if (visuals.clientRender().fogColor() == 0xFFFFFF) {
            throw new IllegalStateException("Aura-zone client render configuration was not retained");
        }
        RealmStage foundationStage = requireHolder(MxtResourceKeys.REALM_STAGE, Identifier.parse("mxt_test:foundation")).value();
        if (visuals.aura().keySet().stream()
                .noneMatch(resource -> HolderHelper.id(resource).equals(Identifier.parse("mxt_test:spirit_power")))) {
            throw new IllegalStateException("Aura-zone element values were not decoded as element holders");
        }
        AuraZone overworld = MxtDatapackRegistries.get(MxtResourceKeys.AURA_ZONE, Identifier.parse("mxt_test:overworld"))
                .orElseThrow(() -> new IllegalStateException("Aura HUD test definition was not loaded"));
        AuraZone firelands = MxtDatapackRegistries.get(MxtResourceKeys.AURA_ZONE, Identifier.parse("mxt_test:firelands"))
                .orElseThrow(() -> new IllegalStateException("Initial-multiplier aura maximum test definition was not loaded"));
        AuraZone nether = MxtDatapackRegistries.get(MxtResourceKeys.AURA_ZONE, Identifier.parse("mxt_test:nether"))
                .orElseThrow(() -> new IllegalStateException("Unlimited aura maximum test definition was not loaded"));
        AuraZone end = MxtDatapackRegistries.get(MxtResourceKeys.AURA_ZONE, Identifier.parse("mxt_test:end_suppressed"))
                .orElseThrow(() -> new IllegalStateException("Default aura maximum test definition was not loaded"));
        Holder<Resource> fire = requireHolder(MxtResourceKeys.RESOURCE, Identifier.parse("mxt_test:spirit_power"));
        if (!(overworld.aura().get(fire).max() instanceof Fixed(double value1)) || !same(value1, 40.0D)
                || !(firelands.aura().get(fire).max() instanceof InitialMultiplier(
                double multiplier1
        )) || !same(multiplier1, 3.0D)
                || !(nether.aura().get(fire).max() instanceof Unlimited)
                || !end.aura().isEmpty()) {
            throw new IllegalStateException("Aura-zone maximum definitions were not decoded correctly");
        }
        if (overworld.distribution() != Distribution.EQUAL
                || firelands.distribution() != Distribution.RANDOM
                || nether.distribution() != Distribution.REALM_WEIGHTED
                || !(overworld.cultivateCondition() instanceof AuraRangeEntityCondition)
                || !(foundationStage.cultivateCondition() instanceof AuraRangeEntityCondition)
                || !same(foundationStage.auraShareWeight().evaluate(FormulaContext.EMPTY), 2.0D)) {
            throw new IllegalStateException("Aura sharing strategies, cultivation condition, or realm weights were not decoded correctly");
        }
        List<Double> equalShares = AuraDistributionService.distribute(List.of(10.0D, 10.0D, 10.0D), List.of(1.0D, 1.0D, 1.0D),
                12.0D, Distribution.EQUAL, RandomSource.create(1L));
        List<Double> weightedShares = AuraDistributionService.distribute(List.of(10.0D, 10.0D, 10.0D), List.of(1.0D, 2.0D, 4.0D),
                14.0D, Distribution.REALM_WEIGHTED, RandomSource.create(1L));
        List<Double> randomShares = AuraDistributionService.distribute(List.of(10.0D, 10.0D), List.of(1.0D, 1.0D),
                10.0D, Distribution.RANDOM, RandomSource.create(1L));
        if (equalShares.stream().anyMatch(value -> !same(value, 4.0D))
                || !same(weightedShares.get(0), 2.0D) || !same(weightedShares.get(1), 4.0D) || !same(weightedShares.get(2), 8.0D)
                || !same(randomShares.stream().mapToDouble(Double::doubleValue).sum(), 10.0D)
                || randomShares.stream().filter(value -> same(value, 10.0D)).count() != 1L) {
            throw new IllegalStateException("Shared aura distribution did not honor the configured allocation strategies");
        }
        AuraChunkAttachment capacity = new AuraChunkAttachment();
        Holder<Resource> capacityFire = requireHolder(MxtResourceKeys.RESOURCE, Identifier.parse("mxt_test:spirit_power"));
        capacity.initializeAuras(Map.of(capacityFire, AuraPool.natural(10.0D, 10.0D, 0.0D)), List.of());
        capacity.setBlockContribution(List.of(new BlockAuraContribution(BlockPos.ZERO,
                Map.of(capacityFire, new AuraValue(5.0D, new Fixed(5.0D), 1.0D, 0xFFFFFF)), false)), List.of());
        capacity.regenerateAuras(20L);
        AuraPool pool = capacity.auras().get(capacityFire);
        if (!same(pool.maximum(), 15.0D) || !same(pool.amount(), 15.0D)) {
            throw new IllegalStateException("Block aura must extend, rather than consume, environmental capacity");
        }
        AuraPool formationPool = pool.withMaximum(pool.maximum() + 50.0D).change(50.0D);
        if (!same(formationPool.maximum(), 65.0D) || !same(formationPool.amount(), 65.0D)) {
            throw new IllegalStateException("Formation capacity bonuses did not extend the chunk limit");
        }
        AuraPool unlimited = AuraPool.natural(1_000.0D, Double.POSITIVE_INFINITY, 0.0D);
        if (!Double.isInfinite(unlimited.maximum()) || !same(unlimited.amount(), 1_000.0D)) {
            throw new IllegalStateException("Unlimited aura capacity did not preserve stored aura");
        }
        BlockAura spiritStone = MxtDatapackRegistries.get(MxtResourceKeys.BLOCK_AURA, Identifier.parse("mxt_test:spirit_stone_ore"))
                .orElseThrow(() -> new IllegalStateException("Spirit-stone aura configuration was not loaded"));
        if (overworld.noise().amplitude() <= 0.0D
                || spiritStone.aura().values().stream().mapToDouble(AuraValue::amount).sum() * 12.0D <= overworld.noise().amplitude() * 2.0D) {
            throw new IllegalStateException("Natural aura must remain sparse compared with a spirit-stone vein");
        }
    }

    private static void verifyFormationTemplate(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        Identifier id = Identifier.parse("mxt_test:spirit_gathering");
        StructureTemplate template = level.getStructureManager().getOrCreate(id);
        template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), singleBlockTemplate());
        BlockPos controller = new BlockPos(0, level.getMinY() + 2, 0);
        level.setBlock(controller, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        Formation definition = MxtDatapackRegistries.get(MxtResourceKeys.FORMATION, id)
                .orElseThrow(() -> new IllegalStateException("Formation test definition was not loaded"));
        if (!FormationStructureValidator.STRUCTURE.matches(level, controller, definition)) {
            throw new IllegalStateException("Vanilla structure-template formation validation failed");
        }
        level.removeBlock(controller, false);
    }

    /**
     * Formation runtime audit. Drives {@link FormationWorldTicker#dispatch} directly, so every assertion runs
     * against the method the game calls; probes stay in loaded chunks, where entity lookup can see them.
     */
    private static void verifyFormationRuntime(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        if (FormationWorldTicker.PERIOD != 20L || !FormationWorldTicker.due(0L)
                || FormationWorldTicker.due(1L) || FormationWorldTicker.due(19L)
                || !FormationWorldTicker.due(20L) || !FormationWorldTicker.due(60L)) {
            throw new IllegalStateException("Formation dispatch was not bound to its 20 tick cadence");
        }
        verifyFormationConditionNesting(level);
        verifyFormationContextAndRange(level);
        verifyFormationOwnerCondition(level);
        verifyFormationGrantLifecycle(level);
        verifyFormationUpkeep(level);
        verifyFormationInlineStructure(level);
        verifyFormationProbeStructures(level);
        verifyFormationTemplateAir(level);
        verifyFormationPlateBinding(level);
        verifyFormationPlateAllowList(level);
        verifyFormationFriendProtection(level);
        verifyFormationDismantlePermission(level);
        verifyFormationActionTypes(level);
        verifyFormationAbsorption(level);
        verifyFormationStorage(level);
        verifyFormationRangeDisplay(level);
        verifyFormationPlateAutoDetect(level);
        verifyFormationEventSplit();
        verifyFormationUpkeepFailure(level);
        verifyFormationIndexTolerance(level);
        verifyFormationPersistence(level);
        LOGGER.info("MiXianTu formation audit: {} tick cadence; explicit formation_x/y/z reaching the action; spherical "
                        + "range inside the entity lookup box; mxt:formation_owner distinct from mxt:formation_member; "
                        + "scoped grants released on exit and on teardown; upkeep recorded through a suppressed period; "
                        + "an unpaid period survivable when UpkeepFailed is cancelled; Tick split from TickEffects; "
                        + "inline and template structures both enforced and mutually exclusive, with every probe "
                        + "telling itself apart by a structure of its own; air entries in a "
                        + "template ignored rather than required; a formation centre resolved one block off and "
                        + "binding a formation onto a hand-held plate; a plate's allow list honoured by id, by tag "
                        + "and on the selected formation; a formation's aura_zone and max_bonus reaching the resolved "
                        + "aura through the cancellable hook; block emitters inside a formation supplying it instead "
                        + "of the environment and paying its upkeep, with the ambient draw following its option; "
                        + "malformed and duplicate "
                        + "index rows dropped without losing the rest; a formation that declares spare_friends "
                        + "sparing its owner and whoever the identification event calls a friend while affecting "
                        + "everyone else, honouring a verdict for an owner it cannot resolve, standing down on a pair "
                        + "nobody can identify, and the server option restoring the unconditional behaviour when it "
                        + "is turned off, with taking an array down left to its owner and operators until the "
                        + "teammate option says otherwise; an attack module selected through the module registry, attributing its "
                        + "damage to the owner and sparing him and his friends only because the formation declares "
                        + "the switch; a benefit module granting under the "
                        + "formation's own source and taking the grant back from an entity it stops targeting; a "
                        + "a protection ward holding its radius against a stranger at either end of an action and "
                        + "against actorless ones, while its owner always passes and a friend passes only while the "
                        + "friend judgement is on, and falling back to its own flags while enforcing none of them once "
                        + "it delegates to claims and there is no claim protection to hand them to, with both claim "
                        + "linkage modes staying inert on a server that has no claims rather than turning into "
                        + "\"cannot build\" or \"protect nothing\", with the foreign-claim rule telling a ward from an "
                        + "array that is not one; "
                        + "a storage field banking what the ground supplies beyond the bill and spending it on the "
                        + "periods the ground stops, split three ways by resource, carried through a save, and keeping "
                        + "a formation alive through an unpayable period until the bank itself runs out; a range display "
                        + "module drawing its boundary on the periods its interval asks for and only to the players "
                        + "looking at it; and an unbound plate identifying the formation standing in front of it, in a "
                        + "deterministic order, raising it by name and taking it back down, while the server option "
                        + "restores the old \"no formation\" answer; "
                        + "and a live index "
                        + "surviving the save path NeoForge "
                        + "writes it through",
                FormationWorldTicker.PERIOD);
    }

    /**
     * The carrier has to reach a condition three levels down: the ticker only hands it to the top level
     * action, so everything nested below depends on {@code Context.copyTo} carrying the extension data.
     */
    private static void verifyFormationConditionNesting(ServerLevel level) {
        Pig subject = new Pig(EntityType.PIG, level);
        Pig outsider = new Pig(EntityType.PIG, level);
        FormationCarrier carrier = new FormationCarrier(Identifier.parse("mxt_test:formation_owner_probe"),
                BlockPos.ZERO, 8.0D, Optional.of(subject.getUUID()));
        FormationContextProbe probe = new FormationContextProbe();
        EntityAction nested = new IfElseAction(new NotEntityCondition(FormationOwnerEntityCondition.INSTANCE),
                probe, NoOpAction.INSTANCE);
        // Carrier present, entity is not the owner: the condition is read from the nested context and
        // the probe runs, so what it captured proves the carrier survived the nesting.
        executeWithCarrier(outsider, carrier, nested);
        if (probe.invocations() != 1 || probe.carriers().size() != 1 || !probe.carriers().getFirst().equals(carrier))
            throw new IllegalStateException("The formation carrier did not survive if_else and not nesting");
        // Same nesting, but now the entity is the owner, so the else branch must be the one taken.
        probe.clear();
        executeWithCarrier(subject, carrier, nested);
        if (probe.invocations() != 0)
            throw new IllegalStateException("mxt:formation_owner did not read the carrier through the nesting");
        // Without a formation there is nothing to be the owner of. The condition must say so rather
        // than falling back to "owns an active formation somewhere", which is a different question.
        probe.clear();
        executeWithCarrier(subject, null, nested);
        if (probe.invocations() != 1 || !probe.carriers().isEmpty())
            throw new IllegalStateException("mxt:formation_owner answered outside a formation context");
    }

    /**
     * The explicit values a per-entity action can read, and the shape of the range they describe.
     */
    private static void verifyFormationContextAndRange(ServerLevel level) {
        Identifier id = Identifier.parse("mxt_test:formation_context_probe");
        Formation definition = formationDefinition(level, id);
        BlockPos controller = prepareFormationController(level, definition);
        Pig inside = spawnProbe(level, controller.offset(2, 1, 0));
        Pig corner = spawnProbe(level, controller.offset(6, 1, 6));
        Vec3 cornerStart = corner.position();
        try {
            activateFormation(level, controller, id, null);
            FormationWorldTicker.dispatch(level);
            if (inside.position().distanceTo(controller.getCenter()) > 0.1D)
                throw new IllegalStateException("formation_x/y/z did not reach the entity action: "
                        + inside.position() + " instead of " + controller.getCenter());
            if (!inside.hasEffect(MobEffects.GLOWING))
                throw new IllegalStateException("The formation enter action did not run for a covered entity");
            // 8.5 blocks away: inside the box the entity lookup hands over, outside the sphere the
            // formation claims. Affecting it would mean the range had silently become a box.
            if (corner.position().distanceTo(cornerStart) > 0.1D || corner.hasEffect(MobEffects.GLOWING))
                throw new IllegalStateException("The formation reached an entity inside its box but outside its sphere");
        } finally {
            clearFormation(level, controller, definition, inside, corner);
        }
    }

    /**
     * {@code mxt:formation_owner} means "owns <em>this</em> formation", which is a different question
     * from what {@code mxt:formation_member} already answered.
     */
    private static void verifyFormationOwnerCondition(ServerLevel level) {
        Identifier id = Identifier.parse("mxt_test:formation_owner_probe");
        Formation definition = formationDefinition(level, id);
        BlockPos controller = prepareFormationController(level, definition);
        Pig owner = spawnProbe(level, controller.offset(2, 1, 0));
        Pig other = spawnProbe(level, controller.offset(-2, 1, 0));
        Vec3 ownerStart = owner.position();
        try {
            activateFormation(level, controller, id, owner.getUUID());
            FormationWorldTicker.dispatch(level);
            if (owner.position().distanceTo(ownerStart) > 0.1D)
                throw new IllegalStateException("mxt:formation_owner did not recognise the formation's own owner");
            if (other.position().distanceTo(controller.getCenter()) > 0.1D)
                throw new IllegalStateException("mxt:formation_owner excluded an entity that is not the owner");
            // The level-wide condition must keep answering its own question, and the narrow one must
            // not answer it: the owner owns an active formation here yet owns no formation context.
            if (!FormationMemberEntityCondition.INSTANCE.test(owner, FormulaContext.EMPTY))
                throw new IllegalStateException("mxt:formation_member stopped answering the level-wide question");
            if (FormationOwnerEntityCondition.INSTANCE.test(owner, FormulaContext.EMPTY))
                throw new IllegalStateException("mxt:formation_owner answered outside a formation context");
        } finally {
            clearFormation(level, controller, definition, owner, other);
        }
    }

    /**
     * A grant made by a formation must not outlive it: {@code ABILITY_HOLDER} is serialised and copied on
     * death, and {@code deactivate_action} cannot revoke it, so leaving, re-entering and teardown do.
     */
    private static void verifyFormationGrantLifecycle(ServerLevel level) {
        Identifier id = Identifier.parse("mxt_test:formation_grant_probe");
        Formation definition = formationDefinition(level, id);
        Identifier source = FormationSources.of(id);
        if (!source.equals(Identifier.parse("mxt:formation/mxt_test/formation_grant_probe")))
            throw new IllegalStateException("The formation source convention changed: " + source);
        BlockPos controller = prepareFormationController(level, definition);
        Pig subject = spawnProbe(level, controller.offset(2, 1, 0));
        try {
            activateFormation(level, controller, id, null);
            FormationWorldTicker.dispatch(level);
            if (!holdsSource(subject, source))
                throw new IllegalStateException("The formation did not grant through its scoped source");
            // Twelve blocks away: outside the radius of eight, but still inside the loaded chunks, so
            // the entity stays resolvable and the release path is actually exercised.
            subject.teleportTo(controller.getX() + 12.5D, controller.getY() + 1.0D, controller.getZ() + 0.5D);
            FormationWorldTicker.dispatch(level);
            if (holdsSource(subject, source))
                throw new IllegalStateException("Leaving a formation did not release its scoped grant");
            // Releasing on exit must not have disabled the instance: coming back has to grant again.
            subject.teleportTo(controller.getX() + 2.5D, controller.getY() + 1.0D, controller.getZ() + 0.5D);
            FormationWorldTicker.dispatch(level);
            if (!holdsSource(subject, source))
                throw new IllegalStateException("Re-entering a formation did not restore its grant");
            if (!FormationWorldService.deactivate(level, controller))
                throw new IllegalStateException("Formation teardown reported nothing to remove");
            if (holdsSource(subject, source))
                throw new IllegalStateException("Formation teardown did not release its scoped grant");
            if (level.getData(MxtAttachments.FORMATION_WORLD).get(controller).isPresent())
                throw new IllegalStateException("Formation teardown left the instance registered");
            if (FormationWorldService.deactivate(level, controller))
                throw new IllegalStateException("Formation teardown was not idempotent");
        } finally {
            clearFormation(level, controller, definition, subject);
        }
    }

    /**
     * Upkeep is charged and recorded in the same pass, including when a listener cancels the tick: the
     * attachment holds the live instance, so the counter moves in place.
     */
    private static void verifyFormationUpkeep(ServerLevel level) {
        Identifier id = Identifier.parse("mxt_test:formation_upkeep_probe");
        Formation definition = formationDefinition(level, id);
        Holder<Resource> spiritPower = MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, Identifier.parse("mxt_test:spirit_power"))
                .orElseThrow(() -> new IllegalStateException("The upkeep audit needs the spirit power resource"));
        BlockPos controller = prepareFormationController(level, definition);
        Pig payer = spawnProbe(level, controller.offset(1, 1, 0));
        Pig subject = spawnProbe(level, controller.offset(-2, 1, 0));
        payer.getData(MxtAttachments.RESOURCE_HOLDER).set(spiritPower, 100.0D);
        try {
            activateFormation(level, controller, id, payer.getUUID());
            suppressFormationTick = true;
            FormationWorldTicker.dispatch(level);
            long canceled = upkeep(level, controller);
            double afterCanceled = payer.getData(MxtAttachments.RESOURCE_HOLDER).get(spiritPower);
            if (!same(afterCanceled, 95.0D))
                throw new IllegalStateException("Formation upkeep was not charged: " + afterCanceled);
            if (canceled != 1L)
                throw new IllegalStateException("A canceled formation tick charged upkeep and then dropped the record of it: " + canceled);
            if (subject.hasEffect(MobEffects.GLOWING))
                throw new IllegalStateException("A canceled formation tick still ran the per-entity actions");
            suppressFormationTick = false;
            FormationWorldTicker.dispatch(level);
            if (upkeep(level, controller) != 2L)
                throw new IllegalStateException("Formation upkeep was not counted in place");
            if (!same(payer.getData(MxtAttachments.RESOURCE_HOLDER).get(spiritPower), 90.0D))
                throw new IllegalStateException("Formation upkeep was not charged twice");
            if (!subject.hasEffect(MobEffects.GLOWING))
                throw new IllegalStateException("An uncanceled formation tick did not run the per-entity actions");
        } finally {
            suppressFormationTick = false;
            clearFormation(level, controller, definition, payer, subject);
        }
    }

    /**
     * A live formation has to survive a save: the store and read are driven the way NeoForge does it, with the
     * writer's problem reporter asserted empty because it decides whether the attachment is written at all.
     */
    @SuppressWarnings("deprecation")
    private static void verifyFormationPersistence(ServerLevel level) {
        Identifier id = Identifier.parse("mxt_test:formation_context_probe");
        Formation definition = formationDefinition(level, id);
        BlockPos controller = prepareFormationController(level, definition);
        Pig owner = spawnProbe(level, controller.offset(2, 1, 0));
        double radius = definition.radius().evaluate(FormulaContext.of(level));
        try {
            activateFormation(level, controller, id, owner.getUUID());
            Collector writer = new Collector();
            TagValueOutput output = TagValueOutput.createWithContext(writer, level.registryAccess());
            output.store(FormationWorldAttachment.MAP_CODEC, level.getData(MxtAttachments.FORMATION_WORLD));
            if (!writer.isEmpty())
                throw new IllegalStateException("Saving a live formation index reported: " + writer.getReport());
            Collector reader = new Collector();
            FormationWorldAttachment decoded = TagValueInput
                    .create(reader, level.registryAccess(), output.buildResult())
                    .read(FormationWorldAttachment.MAP_CODEC)
                    .orElseThrow(() -> new IllegalStateException("Reading a saved formation index failed: " + reader.getReport()));
            FormationInstance restored = decoded.get(controller)
                    .orElseThrow(() -> new IllegalStateException("A saved formation index lost its controller"));
            if (!restored.formation().equals(id) || !same(restored.radius(), radius)
                    || !restored.owner().equals(Optional.of(owner.getUUID())) || restored.maintenanceCount() != 0L) {
                throw new IllegalStateException("A saved formation index did not round trip: "
                        + restored.formation() + " r=" + restored.radius() + " owner=" + restored.owner()
                        + " count=" + restored.maintenanceCount());
            }
        } finally {
            clearFormation(level, controller, definition, owner);
        }
    }

    private static long upkeep(ServerLevel level, BlockPos controller) {
        return level.getData(MxtAttachments.FORMATION_WORLD).get(controller)
                .map(FormationInstance::maintenanceCount).orElse(-1L);
    }

    private static void executeWithCarrier(Pig entity, FormationCarrier carrier, EntityAction action) {
        EntityActionContext context = new EntityActionContext(entity, FormulaContext.of(entity));
        if (carrier != null) context.set(FormationCarrier.KEY, carrier);
        action.execute(context);
    }

    private static Formation formationDefinition(ServerLevel level, Identifier id) {
        return MxtDatapackRegistries.get(MxtResourceKeys.FORMATION, id)
                .orElseThrow(() -> new IllegalStateException("Formation test definition was not loaded: " + id));
    }

    /**
     * An inline {@code structure} must both satisfy activation and keep enforcing afterwards, and the two
     * shape forms must stay mutually exclusive.
     */
    private static void verifyFormationInlineStructure(ServerLevel level) {
        Identifier id = Identifier.parse("mxt_test:formation_inline_probe");
        Formation definition = formationDefinition(level, id);
        if (definition.structure().size() != 3 || definition.structureTemplate().isPresent())
            throw new IllegalStateException("The inline test formation did not declare exactly its three blocks");
        BlockPos required = new BlockPos(2, 0, 0);
        BlockPos controller = prepareFormationController(level, definition);
        Pig subject = spawnProbe(level, controller.offset(-2, 1, 0));
        try {
            activateFormation(level, controller, id, null);
            FormationWorldTicker.dispatch(level);
            if (!subject.hasEffect(MobEffects.GLOWING))
                throw new IllegalStateException("An inline-structured formation did not run its per-entity action");
            // Break one required block: the inline path is what has to notice, and it must notice on the
            // next pass rather than only at activation.
            level.removeBlock(controller.offset(required), false);
            FormationWorldTicker.dispatch(level);
            if (level.getData(MxtAttachments.FORMATION_WORLD).get(controller).isPresent())
                throw new IllegalStateException("Breaking an inline structure block left the formation active");
        } finally {
            clearFormation(level, controller, definition, subject);
        }
        rejectFormationShape(level, "{\"radius\": 8}");
        rejectFormationShape(level, """
                {"structure_template": "mxt_test:spirit_gathering",
                 "structure": [{"offset": [0, 0, 0], "state": "minecraft:gold_block"}],
                 "radius": 8}
                """);
    }

    /**
     * The probes have to describe structures of their own. They were once all one gold block, which left the
     * fixture unable to ask anything that a second definition would have answered differently.
     */
    private static void verifyFormationProbeStructures(ServerLevel level) {
        Registry<Formation> registry = level.registryAccess().lookupOrThrow(MxtResourceKeys.FORMATION);
        List<String> signatures = new ArrayList<>();
        List<Identifier> owners = new ArrayList<>();
        for (Map.Entry<ResourceKey<Formation>, Formation> entry : registry.entrySet()) {
            Formation definition = entry.getValue();
            if (definition.structure().isEmpty()) continue;
            StringBuilder signature = new StringBuilder();
            definition.structure().stream()
                    .map(required -> required.offset().toShortString() + "=" + required.state())
                    .sorted()
                    .forEach(part -> signature.append(part).append(';'));
            int duplicate = signatures.indexOf(signature.toString());
            if (duplicate >= 0)
                throw new IllegalStateException("Two formations declare the same structure: "
                        + owners.get(duplicate) + " and " + entry.getKey().identifier());
            signatures.add(signature.toString());
            owners.add(entry.getKey().identifier());
        }
        if (signatures.size() < 2)
            throw new IllegalStateException("The formation fixture no longer declares distinct inline structures");
    }

    /**
     * Air entries in a structure template must be ignored rather than required: a template carries its whole
     * bounding box, so a torch on a cell recorded as empty would otherwise fail the formation.
     */
    private static void verifyFormationTemplateAir(ServerLevel level) {
        Identifier id = Identifier.parse("mxt_test:formation_template_probe");
        Formation definition = formationDefinition(level, id);
        if (definition.structureTemplate().isEmpty() || !definition.structure().isEmpty())
            throw new IllegalStateException("The template test formation no longer declares a template");
        BlockPos controller = prepareFormationController(level, definition);
        Pig subject = spawnProbe(level, controller.offset(1, 1, 0));
        try {
            level.setBlock(controller.offset(1, 0, 0), Blocks.STONE.defaultBlockState(), 3);
            activateFormation(level, controller, id, null);
            FormationWorldTicker.dispatch(level);
            if (!subject.hasEffect(MobEffects.GLOWING))
                throw new IllegalStateException("An air entry in a template was required, so the formation would not stand");
            level.removeBlock(controller, false);
            FormationWorldTicker.dispatch(level);
            if (level.getData(MxtAttachments.FORMATION_WORLD).get(controller).isPresent())
                throw new IllegalStateException("Breaking a template's non-air block left the formation active");
        } finally {
            level.removeBlock(controller.offset(1, 0, 0), false);
            clearFormation(level, controller, definition, subject);
        }
    }

    /**
     * The two halves of "a player can actually get a running formation": where the plate puts the centre,
     * resolved on a two-block corridor, and how a plate gets bound, driven through the command body.
     */
    private static void verifyFormationPlateBinding(ServerLevel level) {
        try {
            verifyFormationPlateBindingChecked(level);
        } catch (CommandSyntaxException failure) {
            throw new IllegalStateException("The formation plate binding check could not finish", failure);
        }
    }

    private static void verifyFormationPlateBindingChecked(ServerLevel level) throws CommandSyntaxException {
        Identifier id = Identifier.parse("mxt_test:formation_center_probe");
        Formation definition = formationDefinition(level, id);
        if (definition.structure().size() != 2 || definition.structureTemplate().isPresent())
            throw new IllegalStateException("The centre probe did not declare exactly its two blocks");
        BlockPos controller = new BlockPos(0, level.getMinY() + 2, 0);
        BlockPos ahead = controller.offset(definition.structure().get(1).offset());
        if (controller.distManhattan(ahead) != 1)
            throw new IllegalStateException("The centre probe's two blocks are no longer a corridor: " + ahead);
        placeInline(level, controller, definition.structure());
        try {
            // Both blocks of the corridor are centres in their own right, which is what makes "the search
            // works" distinguishable from "the click happened to land on the controller".
            for (BlockPos own : List.of(controller, ahead)) {
                Optional<BlockPos> resolved = FormationCenters.resolve(level, own, definition);
                if (resolved.isEmpty() || !List.of(controller, ahead).contains(resolved.get()))
                    throw new IllegalStateException("A click on the structure did not resolve to a centre of it: "
                            + own + " -> " + resolved.orElse(null));
            }
            Optional<BlockPos> exact = FormationCenters.resolve(level, controller, definition);
            if (exact.isEmpty() || !exact.get().equals(controller))
                throw new IllegalStateException("A click on a formation's own centre did not resolve to it: "
                        + exact.orElse(null));
            // Outside the 3x3x3 there is nothing to find: a click on empty ground must not resolve.
            if (FormationCenters.resolve(level, controller.offset(1, 0, 4), definition).isPresent())
                throw new IllegalStateException("A click outside the search window still resolved to a centre");
            level.removeBlock(ahead, false);
            if (FormationCenters.resolve(level, controller.offset(1, 0, 4), definition).isPresent())
                throw new IllegalStateException("A broken structure still resolved to a formation centre");
            level.setBlock(ahead, definition.structure().get(1).state(), 3);

            FakePlayer holder = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "mxt-audit-plate"));
            CommandSourceStack source = new CommandSourceStack(holder.commandSource(), holder.position(), Vec2.ZERO, level,
                    PermissionSet.ALL_PERMISSIONS, holder.getName().getString(), holder.getDisplayName(),
                    level.getServer(), holder);
            ItemStack blank = new ItemStack(MxtItems.FORMATION_PLATE.get());
            if (blank.getOrDefault(MxtDataComponents.FORMATION_PLATE, FormationPlateComponent.EMPTY).formation().isPresent())
                throw new IllegalStateException("A fresh formation plate arrived already bound");
            holder.setItemInHand(InteractionHand.MAIN_HAND, blank);
            if (FormationCommand.bind(source, id) != 1)
                throw new IllegalStateException("Binding a formation to a held plate reported no change");
            Optional<Holder<Formation>> bound = holder.getMainHandItem()
                    .getOrDefault(MxtDataComponents.FORMATION_PLATE, FormationPlateComponent.EMPTY).formation();
            if (bound.isEmpty() || !HolderHelper.id(bound.get()).equals(id))
                throw new IllegalStateException("The bind command did not put the formation on the held plate");
            holder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            if (FormationCommand.bind(source, id) != 0)
                throw new IllegalStateException("The bind command modified an item that is not a plate");
            // A typo must be refused before anything is written, so a fresh plate stays unbound; reusing the
            // stack bound above would make this vacuous.
            holder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MxtItems.FORMATION_PLATE.get()));
            try {
                FormationCommand.bind(source, Identifier.parse("mxt_test:never_existed"));
                throw new IllegalStateException("The bind command accepted a formation that does not exist");
            } catch (CommandSyntaxException expected) {
                // Refused, and the plate is untouched.
            }
            if (holder.getMainHandItem().getOrDefault(MxtDataComponents.FORMATION_PLATE, FormationPlateComponent.EMPTY)
                    .formation().isPresent())
                throw new IllegalStateException("A refused bind still wrote something onto the plate");

            ServerLevel overworld = ServerCache.get()
                    .orElseThrow(() -> new IllegalStateException("The formation audit needs the server cache"))
                    .server().overworld();
            CommandNode<CommandSourceStack> formation = overworld.getServer().getCommands().getDispatcher()
                    .getRoot().getChild("mxt").getChild("formation");
            if (formation == null || formation.getChild("bind") == null)
                throw new IllegalStateException("The formation bind command is not registered under /mxt formation");
            if (formation.getChild("bind").getRequirement().test(source.withPermission(PermissionSet.NO_PERMISSIONS)))
                throw new IllegalStateException("Binding a formation does not require gamemaster permission");
            if (formation.getChild("list") == null
                    || !formation.getChild("list").getRequirement().test(source.withPermission(PermissionSet.NO_PERMISSIONS)))
                throw new IllegalStateException("The formation diagnostics stopped being readable without permission");
            // The top-level alias is a separate registration and the server option decides whether it exists,
            // so the check is that presence follows the option rather than that it is present.
            CommandNode<CommandSourceStack> alias = overworld.getServer().getCommands().getDispatcher()
                    .getRoot().getChild("formation");
            if ((alias == null) == MxtServerConfig.INSTANCE.commands.formation.getValue())
                throw new IllegalStateException("The top-level /formation alias does not follow its server option");
            if (alias != null && (alias.getChild("bind") == null || alias.getChild("list") == null))
                throw new IllegalStateException("The /formation alias does not expose the same subtree as /mxt formation");
            // The two registrations share one builder, and the risk of that is a node that silently carries
            // different children. Compare the child sets rather than trusting the shared source.
            if (alias != null && !children(alias).equals(children(formation)))
                throw new IllegalStateException("The /formation alias and /mxt formation expose different children: "
                        + children(alias) + " vs " + children(formation));
        } finally {
            removeInline(level, controller, definition.structure());
            level.removeBlock(controller, false);
        }
    }

    /**
     * The plate's allow list keeps "which formation can this run" a property of the item: ids and
     * {@code #tag} entries admit their formations, and a selection is usable only when it is itself admitted.
     */
    private static void verifyFormationPlateAllowList(ServerLevel level) {
        Registry<Formation> registry = level.registryAccess().lookupOrThrow(MxtResourceKeys.FORMATION);
        Identifier allowedId = Identifier.parse("mxt_test:formation_center_probe");
        Identifier otherId = Identifier.parse("mxt_test:formation_inline_probe");
        Identifier tagId = Identifier.parse("mxt_test:probe_group");
        Reference<Formation> allowedFormation = registry.getOrThrow(ResourceKey.create(MxtResourceKeys.FORMATION, allowedId));
        Reference<Formation> otherFormation = registry.getOrThrow(ResourceKey.create(MxtResourceKeys.FORMATION, otherId));

        FormationPlateComponent empty = new FormationPlateComponent(List.of(), Optional.empty());
        if (empty.admits(allowedFormation) != MxtServerConfig.INSTANCE.formations.emptyAllowsAll.getValue())
            throw new IllegalStateException("An empty plate allow list ignored the empty_plate_allows_all option");
        if (empty.admitsSelection())
            throw new IllegalStateException("An unbound plate reported a usable selection");

        FormationPlateComponent restricted = new FormationPlateComponent(List.of(new Id(allowedId)), Optional.empty());
        if (!restricted.admits(allowedFormation))
            throw new IllegalStateException("An allow list refused the formation it lists");
        if (restricted.admits(otherFormation))
            throw new IllegalStateException("An allow list admitted a formation it does not list");
        if (restricted.admitsSelection())
            throw new IllegalStateException("A restricted but unbound plate reported a usable selection");

        FormationPlateComponent tagged = new FormationPlateComponent(List.of(new Tag(TagKey.create(MxtResourceKeys.FORMATION, tagId))), Optional.empty());
        if (!tagged.admits(allowedFormation))
            throw new IllegalStateException("A tag entry did not admit the formation the tag lists");
        if (tagged.admits(otherFormation))
            throw new IllegalStateException("A tag entry admitted a formation outside the tag");
        // The enumeration is what binding and suggestions consume, so it has to agree with admits().
        if (!tagged.admissible(registry).contains(allowedFormation) || tagged.admissible(registry).contains(otherFormation))
            throw new IllegalStateException("The admissible set disagreed with the tag's own admits() result");

        // Bound to something the list refuses: the plate says so rather than activating it.
        FormationPlateComponent stalemate = new FormationPlateComponent(restricted.allowed(), Optional.of(otherFormation));
        if (stalemate.admitsSelection())
            throw new IllegalStateException("A plate bound to a formation outside its allow list still had a usable selection");
        FormationPlateComponent usable = new FormationPlateComponent(restricted.allowed(), Optional.of(allowedFormation));
        if (!usable.admitsSelection())
            throw new IllegalStateException("A plate bound inside its own allow list was not usable");
    }

    private static Set<String> children(CommandNode<CommandSourceStack> node) {
        return node.getChildren().stream().map(CommandNode::getName).collect(Collectors.toSet());
    }

    /**
     * A formation's {@code aura_zone} has to reach the aura resolver through the cancellable
     * {@link AuraZoneEvent.Override} rather than unconditionally - the join between formations and aura.
     */
    private static void verifyFormationAuraOverride(ServerLevel level) {
        Identifier id = Identifier.parse("mxt_test:formation_aura_probe");
        Formation definition = formationDefinition(level, id);
        BuffFormationAction auraModule = module(definition, BuffFormationAction.class, id);
        Identifier zone = auraModule.auraZone()
                .orElseThrow(() -> new IllegalStateException("The aura probe formation declares no aura zone"))
                .unwrapKey()
                .orElseThrow(() -> new IllegalStateException("The aura probe's aura zone has no identifier"))
                .identifier();
        BlockPos controller = new BlockPos(496, level.getMinY() + 2, 496);
        try {
            for (RequiredBlock required : definition.structure())
                level.setBlock(controller.offset(required.offset()), required.state(), 3);
            sweepAuraMemo(level);
            AuraResult before = AuraService.getPositionAura(level, controller);
            if (before.sourceKind() == SourceKind.FORMATION)
                throw new IllegalStateException("A position reported a formation override before any formation stood there");

            FormationWorldService.Result activated = FormationWorldService.activate(level, controller, id, definition,
                    new ResourceHolderAttachment(), FormulaContext.of(level), null);
            if (!activated.active())
                throw new IllegalStateException("The aura probe formation would not activate: " + activated.failure());
            sweepAuraMemo(level);
            AuraResult overridden = AuraService.getPositionAura(level, controller);
            if (overridden.sourceKind() != SourceKind.FORMATION || !zone.equals(overridden.source()))
                throw new IllegalStateException("A standing formation did not override the aura: kind="
                        + overridden.sourceKind() + " source=" + overridden.source() + " expected=" + zone);
            // The formation's max_bonus rides on the same override, added to the zone's own bound and only
            // for a resource the zone already provides.
            Holder<Resource> bonusResource = MxtDatapackRegistries
                    .holder(MxtResourceKeys.RESOURCE, Identifier.parse("mxt_test:spirit_power"))
                    .orElseThrow(() -> new IllegalStateException("The aura probe's bonus resource is missing"));
            AuraPool plain = before.pool(bonusResource);
            AuraPool boosted = overridden.pool(bonusResource);
            if (boosted == null)
                throw new IllegalStateException("The formation's override dropped the resource its bonus applies to");
            double expectedMaximum = (plain == null ? 0.0D : plain.maximum()) + 37.5D;
            if (!same(boosted.maximum(), expectedMaximum))
                throw new IllegalStateException("The formation's max_bonus did not raise the aura ceiling: "
                        + boosted.maximum() + " instead of " + expectedMaximum);

            // The override is an event, so refusing it has to leave the static answer in place. This is
            // what proves the formation arrives through the hook rather than around it.
            refusingFormationAuraOverride = true;
            sweepAuraMemo(level);
            AuraResult refused = AuraService.getPositionAura(level, controller);
            if (refused.sourceKind() == SourceKind.FORMATION)
                throw new IllegalStateException("A refused aura override was applied anyway");
        } finally {
            refusingFormationAuraOverride = false;
            FormationWorldService.deactivate(level, controller);
            for (RequiredBlock required : definition.structure())
                level.removeBlock(controller.offset(required.offset()), false);
        }
        sweepAuraMemo(level);
        AuraResult after = AuraService.getPositionAura(level, controller);
        if (after.sourceKind() == SourceKind.FORMATION)
            throw new IllegalStateException("Dismantling a formation left its aura override in place");
    }

    /**
     * Drops every memoised aura answer for a level by opening a new tick window with the cache switched off
     * and back on: the memo is keyed by game time, so a formation appearing mid-tick would not be seen.
     */
    private static void sweepAuraMemo(ServerLevel level) {
        AuraQueryCache.setEnabled(false);
        AuraQueryCache.advance(level, level.getGameTime());
        AuraQueryCache.setEnabled(true);
    }

    /**
     * A block emitter inside a formation supplies the formation, not the environment, and the formation
     * spends it on its own upkeep - so an absorbed emitter must also stay out of the shared chunk stock.
     */
    private static void verifyFormationAbsorption(ServerLevel level) {
        Holder<Resource> common = requireHolder(MxtResourceKeys.RESOURCE, Identifier.parse("mxt:common"));
        Identifier id = Identifier.parse("mxt_test:formation_absorb_probe");
        Formation definition = formationDefinition(level, id);
        if (definition.maintenanceCosts().isEmpty())
            throw new IllegalStateException("The absorb probe formation declares no upkeep to offset");

        // The predicate itself, against a real formation in the index.
        BlockPos controller = new BlockPos(16, level.getMinY() + 2, 16);
        placeInline(level, controller, definition.structure());
        try {
            FormationWorldService.Result activated = FormationWorldService.activate(level, controller, id, definition,
                    new ResourceHolderAttachment(), FormulaContext.of(level), null);
            if (!activated.active())
                throw new IllegalStateException("The absorb probe formation would not activate: " + activated.failure());
            Sources sources = Sources.of(level,
                    controller.getX() - 8, controller.getZ() - 8, controller.getX() + 8, controller.getZ() + 8);
            if (!sources.absorbed(controller.offset(4, 0, 0))
                    || sources.absorbed(controller.offset(9, 0, 0)))
                throw new IllegalStateException("Formation coverage did not follow its own radius: "
                        + sources.absorbed(controller.offset(4, 0, 0)) + " / "
                        + sources.absorbed(controller.offset(9, 0, 0)));

            // An emitter placed inside the formation must leave the shared stock and appear in the absorbed
            // totals, and putting a formation up is what triggers the rebuild.
            Block emitter = BuiltInRegistries.BLOCK.getValue(Identifier.parse("mxt:spirit_stone_block"));
            if (emitter == null || emitter == Blocks.AIR)
                throw new IllegalStateException("The absorption audit needs the spirit stone block to emit aura");
            if (!BlockAuraService.matches(level, emitter.defaultBlockState()))
                throw new IllegalStateException("The spirit stone block is no longer a block aura emitter");
            BlockPos emitterPos = controller.offset(4, 0, 4);
            level.setBlock(emitterPos, emitter.defaultBlockState(), 3);
            AuraChunkTicker.markDirty(level, emitterPos);
            AuraChunkTicker.flushDirty(level);
            Map<Holder<Resource>, AuraValue> absorbedByChunk = level.getChunkAt(emitterPos)
                    .getData(MxtAttachments.AURA_CHUNK).absorbedAura();
            if (!absorbedByChunk.containsKey(common) || absorbedByChunk.get(common).amount() <= 0.0D)
                throw new IllegalStateException("An emitter inside a formation did not reach the absorbed totals: "
                        + absorbedByChunk);
            Map<Holder<Resource>, Double> supplied = FormationAbsorption.absorbedFor(level, controller,
                    definition.radius().evaluate(FormulaContext.of(level)));
            if (supplied.getOrDefault(common, 0.0D) <= 0.0D)
                throw new IllegalStateException("A formation standing over an emitter supplied nothing: " + supplied);
            level.removeBlock(emitterPos, false);
        } finally {
            FormationWorldService.deactivate(level, controller);
            level.removeBlock(controller, false);
            removeInline(level, controller, definition.structure());
        }
        Sources empty = Sources.of(level,
                controller.getX() - 8, controller.getZ() - 8, controller.getX() + 8, controller.getZ() + 8);
        if (!empty.empty())
            throw new IllegalStateException("A dismantled formation still absorbs emitters");

        // An absorbed emitter leaves the shared stock and is totalled on its own.
        Map<Holder<Resource>, AuraValue> aura = Map.of(common, new AuraValue(50.0D, new Fixed(50.0D), 0.1D, 0xFFFFFF));
        AuraChunkAttachment chunk = new AuraChunkAttachment();
        chunk.setBlockContribution(List.of(
                new BlockAuraContribution(BlockPos.ZERO, aura, false),
                new BlockAuraContribution(new BlockPos(0, 0, 1), aura, true)), List.of());
        if (!chunk.blockAura().containsKey(common) || !same(chunk.blockAura().get(common).amount(), 50.0D))
            throw new IllegalStateException("An absorbed emitter was left in the shared stock: " + chunk.blockAura());
        if (!chunk.absorbedAura().containsKey(common) || !same(chunk.absorbedAura().get(common).amount(), 50.0D))
            throw new IllegalStateException("An absorbed emitter was not totalled for the formation: " + chunk.absorbedAura());

        // The supply reduces the charge, and covers it completely when there is enough of it.
        double cost = definition.maintenanceCosts().getFirst().evaluate(FormulaContext.of(level));
        Identifier costId = definition.maintenanceCosts().getFirst().id();
        Map<Identifier, Double> reduced = MaintainRule.remaining(definition, FormulaContext.of(level),
                Map.of(common, cost / 4.0D));
        if (!same(reduced.getOrDefault(costId, 0.0D), cost * 0.75D))
            throw new IllegalStateException("The supplied aura did not reduce the upkeep charge: "
                    + reduced.get(costId) + " instead of " + cost * 0.75D);
        // A fully covered cost drops out of the charge entirely, which is what lets a formation standing on
        // enough emitters keep running with no owner resources at all.
        if (!MaintainRule.remaining(definition, FormulaContext.of(level),
                Map.of(common, cost * 2.0D)).isEmpty())
            throw new IllegalStateException("A fully supplied upkeep still demanded payment from its owner");
        // Aura of another resource offsets nothing: the charge is not a generic pool.
        Holder<Resource> qi = requireHolder(MxtResourceKeys.RESOURCE, Identifier.parse("mxt_test:qi"));
        if (!same(MaintainRule.remaining(definition, FormulaContext.of(level),
                Map.of(qi, cost * 10.0D)).getOrDefault(costId, 0.0D), cost))
            throw new IllegalStateException("Aura of a resource the upkeep does not cost reduced the charge anyway");

        // The optional second source. Off is the shipped behaviour — only the formation's own emitters count
        // — and it must not be possible for the ambient aura to arrive anyway.
        Map<Holder<Resource>, Double> environment = Map.of(common, 7.5D);
        Map<Holder<Resource>, Double> absorbed = Map.of(common, 2.5D);
        Map<Holder<Resource>, Double> emitterOnly = FormationWorldTicker.combine(absorbed, environment, false);
        if (!same(emitterOnly.getOrDefault(common, 0.0D), 2.5D))
            throw new IllegalStateException("The ambient aura of the ground paid upkeep while its server option was off");
        if (!same(FormationWorldTicker.combine(absorbed, environment, true).getOrDefault(common, 0.0D), 10.0D))
            throw new IllegalStateException("Turning the ambient draw on did not add the ground's aura to the supply");
        // The pool has to say how much of itself came from field blocks, or a formation allowed to spend the
        // ground would spend its own emission back and count the same aura twice.
        if (!same(AuraPool.empty().supplied(), 0.0D)
                || !same(new AuraPool(5.0D, 5.0D, 0.0D, 3.0D).supplied(), 3.0D))
            throw new IllegalStateException("A resolved pool does not report the part of it that field blocks supply");
    }

    /**
     * Asserts a formation definition is refused, and refused because of how it declares its shape rather
     * than for some unrelated reason.
     */
    private static void rejectFormationShape(ServerLevel level, String json) {
        DataResult<Formation> result = Formation.DIRECT_CODEC.parse(
                RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), JsonParser.parseString(json));
        if (result.result().isPresent())
            throw new IllegalStateException("A formation declaring the wrong shape was accepted: " + json);
        String message = result.error().map(DataResult.Error::message).orElse("");
        if (!message.contains("structure"))
            throw new IllegalStateException("A formation with a wrong shape was refused for another reason: " + message);
    }

    /**
     * The period hooks must be split the way the design says: {@code Tick} is the settled observer and
     * cannot be cancelled, while {@code TickEffects} and {@code UpkeepFailed} can.
     */
    private static void verifyFormationEventSplit() {
        if (ICancellableEvent.class.isAssignableFrom(Tick.class))
            throw new IllegalStateException("FormationEvent.Tick is cancellable again, so cancelling it would suppress effects while upkeep is still charged");
        if (!ICancellableEvent.class.isAssignableFrom(TickEffects.class))
            throw new IllegalStateException("FormationEvent.TickEffects cannot be cancelled, so no listener can suppress a period's work");
        if (!ICancellableEvent.class.isAssignableFrom(UpkeepFailed.class))
            throw new IllegalStateException("FormationEvent.UpkeepFailed cannot be cancelled, so a formation cannot survive an unpaid period");
    }

    /**
     * A formation that cannot pay must be able to stand through the period when a listener says so, and
     * must come down when nobody intervenes.
     */
    private static void verifyFormationUpkeepFailure(ServerLevel level) {
        Identifier id = Identifier.parse("mxt_test:formation_upkeep_probe");
        Formation definition = formationDefinition(level, id);
        Holder<Resource> spiritPower = MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, Identifier.parse("mxt_test:spirit_power"))
                .orElseThrow(() -> new IllegalStateException("The upkeep audit needs the spirit power resource"));
        BlockPos controller = prepareFormationController(level, definition);
        Pig payer = spawnProbe(level, controller.offset(1, 1, 0));
        Pig subject = spawnProbe(level, controller.offset(-2, 1, 0));
        // One short of the five the formation asks for.
        payer.getData(MxtAttachments.RESOURCE_HOLDER).set(spiritPower, 1.0D);
        try {
            activateFormation(level, controller, id, payer.getUUID());
            suppressFormationUpkeepFailure = true;
            FormationWorldTicker.dispatch(level);
            if (level.getData(MxtAttachments.FORMATION_WORLD).get(controller).isEmpty())
                throw new IllegalStateException("A cancelled UpkeepFailed did not let the formation stand");
            if (upkeep(level, controller) != 0L)
                throw new IllegalStateException("An unpaid period was counted as paid upkeep");
            if (subject.hasEffect(MobEffects.GLOWING))
                throw new IllegalStateException("An unpaid period still ran the per-entity actions");
            suppressFormationUpkeepFailure = false;
            FormationWorldTicker.dispatch(level);
            if (level.getData(MxtAttachments.FORMATION_WORLD).get(controller).isPresent())
                throw new IllegalStateException("An unpaid period nobody defended left the formation standing");
        } finally {
            suppressFormationUpkeepFailure = false;
            clearFormation(level, controller, definition, payer, subject);
        }
    }

    /**
     * One unreadable row must not cost the whole index, and a repeated controller must not either: a codec
     * that throws escapes the tolerant decoder and takes the attachment down with it.
     */
    private static void verifyFormationIndexTolerance(ServerLevel level) {
        BlockPos controller = new BlockPos(0, level.getMinY() + 2, 0);
        long packed = controller.asLong();
        String json = """
                {"formations": [
                  {"position": %d, "formation": {"formation": "mxt_test:formation_context_probe", "radius": 8}},
                  {"position": %d, "formation": {"formation": "mxt_test:formation_context_probe", "radius": -1}},
                  {"position": %d, "formation": {"formation": "mxt_test:formation_context_probe", "radius": 8, "maintenance_count": -5}},
                  {"position": %d, "formation": {"formation": "mxt_test:formation_context_probe", "radius": 12}}
                ]}
                """.formatted(packed, packed + 1, packed + 2, packed);
        FormationWorldAttachment decoded = FormationWorldAttachment.CODEC.parse(
                        RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), JsonParser.parseString(json))
                .getOrThrow();
        if (decoded.formations().size() != 1)
            throw new IllegalStateException("A malformed or duplicated index row was not dropped: kept " + decoded.formations().size());
        FormationInstance kept = decoded.get(controller)
                .orElseThrow(() -> new IllegalStateException("A tolerant index decode dropped its only valid row"));
        if (!same(kept.radius(), 8.0D))
            throw new IllegalStateException("A duplicate controller replaced the first row instead of being ignored: r=" + kept.radius());
    }

    /**
     * Makes the world match what a test formation declares, so activation validates: a template gets the
     * one-block gold template written into the structure manager, an inline one exactly the blocks it lists.
     */
    private static BlockPos prepareFormationController(ServerLevel level, Formation definition) {
        BlockPos controller = new BlockPos(0, level.getMinY() + 2, 0);
        definition.structureTemplate().ifPresent(id -> {
            StructureTemplate template = level.getStructureManager().getOrCreate(id);
            template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), singleBlockTemplate());
            level.setBlock(controller, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        });
        placeInline(level, controller, definition.structure());
        return controller;
    }

    /**
     * Builds or clears the blocks an inline structure requires, so an audit can stand a probe up wherever it
     * needs it rather than only at the shared controller.
     */
    private static void placeInline(ServerLevel level, BlockPos controller, List<RequiredBlock> structure) {
        for (RequiredBlock required : structure)
            level.setBlock(controller.offset(required.offset()), required.state(), 3);
    }

    private static void removeInline(ServerLevel level, BlockPos controller, List<RequiredBlock> structure) {
        for (RequiredBlock required : structure)
            level.removeBlock(controller.offset(required.offset()), false);
    }

    private static void activateFormation(ServerLevel level, BlockPos controller, Identifier id, UUID owner) {
        FormationWorldService.Result result = FormationWorldService.activate(level, controller, id,
                formationDefinition(level, id), new ResourceHolderAttachment(), FormulaContext.of(level), owner);
        if (!result.active())
            throw new IllegalStateException("Formation audit could not activate " + id + ": " + result.failure());
    }

    /**
     * The friend judgement a formation that declares {@code spare_friends} makes about the entities it covers:
     * the owner and whoever the identification event calls a friend are spared, everyone else is affected, the
     * option runs both ways, and an array without the switch hits its own side.
     */
    private static void verifyFormationFriendProtection(ServerLevel level) {
        Identifier id = Identifier.parse("mxt_test:formation_friend_probe");
        Formation definition = formationDefinition(level, id);
        if (!definition.spareFriends())
            throw new IllegalStateException("The friend probe did not decode its friend-or-foe switch");
        Formation passive = formationDefinition(level, Identifier.parse("mxt_test:formation_inline_probe"));
        BlockPos controller = prepareFormationController(level, definition);
        Pig owner = spawnProbe(level, controller.offset(2, 1, 0));
        Pig friend = spawnProbe(level, controller.offset(0, 1, 2));
        Pig stranger = spawnProbe(level, controller.offset(-2, 1, 0));
        boolean configured = MxtServerConfig.INSTANCE.formations.respectFriends.getValue();
        try {
            friendVerdictTarget = friend;
            friendVerdict = TriState.TRUE;
            if (!FriendService.isFriend(owner, friend))
                throw new IllegalStateException("The audit's own verdict did not make the entity a friend");

            // The rule on its own, without a world: a formation without the switch spares nobody, and one
            // that identifies friends with nobody to ask fires at nobody.
            if (!FormationRelations.affects(passive, owner.getUUID(), owner, stranger))
                throw new IllegalStateException("A formation without the friend-or-foe switch spared somebody");
            if (!FormationRelations.affects(passive, null, null, stranger))
                throw new IllegalStateException("A formation without the switch stood down with no owner");
            if (FormationRelations.affects(definition, owner.getUUID(), null, stranger))
                throw new IllegalStateException("A formation identifying friends with no owner to ask still fired at a stranger");
            if (FormationRelations.affects(definition, null, null, stranger))
                throw new IllegalStateException("A formation identifying friends with no owner recorded still fired");
            // The switch decides and nothing else does: an array whose action attacks, without declaring it,
            // hits its owner exactly like anybody else.
            Formation offensive = Formation.DIRECT_CODEC.parse(
                            RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), JsonParser.parseString("""
                            {"structure":[{"offset":[0,0,0],"state":"minecraft:gold_block"}],"radius":8,
                             "actions":[{"type":"mxt:attack","damage":1}]}
                            """))
                    .getOrThrow();
            if (offensive.spareFriends() || !FormationRelations.affects(offensive, owner.getUUID(), owner, friend))
                throw new IllegalStateException("An attacking array that did not declare the switch spared its owner");

            // The datapack condition asks the same question from inside a formation context and refuses to
            // guess outside one, so a pack can write the judgement per action.
            FormationCarrier carrier = new FormationCarrier(id, controller,
                    definition.radius().evaluate(FormulaContext.of(level)), Optional.of(owner.getUUID()));
            if (!allied(friend, carrier))
                throw new IllegalStateException("mxt:formation_ally did not recognise a friend of the owner");
            if (allied(stranger, carrier))
                throw new IllegalStateException("mxt:formation_ally recognised somebody the owner does not know");
            if (FormationAllyEntityCondition.INSTANCE.test(stranger, FormulaContext.EMPTY))
                throw new IllegalStateException("mxt:formation_ally answered outside a formation context");
            // An owner who is offline is still answered for by whatever source speaks about the pair, or the
            // two ways of asking would disagree exactly when it matters most.
            FormationCarrier absent = new FormationCarrier(id, controller,
                    definition.radius().evaluate(FormulaContext.of(level)), Optional.of(UUID.randomUUID()));
            if (!allied(friend, absent))
                throw new IllegalStateException("mxt:formation_ally ignored a verdict for an offline owner");
            if (allied(stranger, absent))
                throw new IllegalStateException("mxt:formation_ally invented an ally for an offline owner");

            activateFormation(level, controller, id, owner.getUUID());
            FormationWorldTicker.dispatch(level);
            if (!stranger.hasEffect(MobEffects.GLOWING))
                throw new IllegalStateException("A formation identifying friends stopped affecting a stranger");
            if (owner.hasEffect(MobEffects.GLOWING) || friend.hasEffect(MobEffects.GLOWING))
                throw new IllegalStateException("A formation identifying friends affected its owner or a friend");

            // The other direction: an entity that stops being a friend has to be picked back up, which only
            // works if being spared means the formation never started tracking it.
            friendVerdict = TriState.FALSE;
            FormationWorldTicker.dispatch(level);
            if (!friend.hasEffect(MobEffects.GLOWING))
                throw new IllegalStateException("An entity that stopped being a friend was not picked back up");

            friendVerdict = TriState.TRUE;
            owner.removeEffect(MobEffects.GLOWING);
            friend.removeEffect(MobEffects.GLOWING);
            stranger.removeEffect(MobEffects.GLOWING);
            FormationWorldTicker.dispatch(level);
            if (friend.hasEffect(MobEffects.GLOWING))
                throw new IllegalStateException("A formation identifying friends kept affecting an entity that became a friend");
            if (!stranger.hasEffect(MobEffects.GLOWING))
                throw new IllegalStateException("Spared one entity and stopped affecting the rest");

            MxtServerConfig.INSTANCE.formations.respectFriends.setValue(false);
            owner.removeEffect(MobEffects.GLOWING);
            friend.removeEffect(MobEffects.GLOWING);
            stranger.removeEffect(MobEffects.GLOWING);
            FormationWorldTicker.dispatch(level);
            if (!owner.hasEffect(MobEffects.GLOWING) || !friend.hasEffect(MobEffects.GLOWING))
                throw new IllegalStateException("Turning the friend judgement off did not restore the unconditional behaviour");

            // Last, the owner is gone: the formation is re-pointed at an owner this level cannot resolve, which
            // is how an offline owner looks to the ticker, so it stands down rather than firing at everybody.
            MxtServerConfig.INSTANCE.formations.respectFriends.setValue(true);
            FormationWorldService.deactivate(level, controller);
            UUID absentOwner = UUID.randomUUID();
            activateFormation(level, controller, id, absentOwner);
            owner.removeEffect(MobEffects.GLOWING);
            friend.removeEffect(MobEffects.GLOWING);
            stranger.removeEffect(MobEffects.GLOWING);
            FormationWorldTicker.dispatch(level);
            if (stranger.hasEffect(MobEffects.GLOWING) || friend.hasEffect(MobEffects.GLOWING))
                throw new IllegalStateException("A formation identifying friends kept firing with no owner to identify anybody");
            // The two dark outcomes differ, and only a verdict that *affects* tells them apart: the stranger
            // was spared because nobody could answer, the friend because a listener answered for an absent owner.
            friendVerdict = TriState.FALSE;
            friend.removeEffect(MobEffects.GLOWING);
            stranger.removeEffect(MobEffects.GLOWING);
            FormationWorldTicker.dispatch(level);
            if (!friend.hasEffect(MobEffects.GLOWING))
                throw new IllegalStateException("A verdict for an offline owner was not honoured");
            if (FormationRelations.affects(definition, absentOwner, null, stranger))
                throw new IllegalStateException("An unanswered pair was fired at instead of standing down");
        } finally {
            friendVerdictTarget = null;
            friendVerdict = TriState.DEFAULT;
            MxtServerConfig.INSTANCE.formations.respectFriends.setValue(configured);
            clearFormation(level, controller, definition, owner, friend, stranger);
        }
    }

    /**
     * Who may take an array down: the owner and an operator always, a stranger never, and a friend only while
     * the server option says so - demolition is not an effect, so it is off by default and an entity nobody can
     * identify is refused like anyone else. The plate's own click path is what is driven, so the refusal is
     * asserted where a player would meet it.
     */
    private static void verifyFormationDismantlePermission(ServerLevel level) {
        Identifier id = Identifier.parse("mxt_test:formation_inline_probe");
        Formation definition = formationDefinition(level, id);
        BlockPos controller = prepareFormationController(level, definition);
        FakePlayer owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "mxt-audit-owner"));
        FakePlayer friend = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "mxt-audit-friend"));
        FakePlayer stranger = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "mxt-audit-stranger"));
        boolean configured = MxtServerConfig.INSTANCE.formations.teammatesCanDismantle.getValue();
        try {
            activateFormation(level, controller, id, owner.getUUID());
            FormationInstance instance = level.getData(MxtAttachments.FORMATION_WORLD).get(controller)
                    .orElseThrow(() -> new IllegalStateException("The dismantle audit could not raise its probe"));
            if (!FormationRelations.canDismantle(instance, owner))
                throw new IllegalStateException("A formation's own owner could not take it down");
            if (FormationRelations.canDismantle(instance, stranger))
                throw new IllegalStateException("A stranger could take somebody else's formation down");

            friendVerdictTarget = friend;
            friendVerdict = TriState.TRUE;
            if (FormationRelations.canDismantle(instance, friend))
                throw new IllegalStateException("A teammate took a formation down while the option was off");

            MxtServerConfig.INSTANCE.formations.teammatesCanDismantle.setValue(true);
            if (!FormationRelations.canDismantle(instance, friend))
                throw new IllegalStateException("A teammate could not take a formation down while the option was on");
            // The option is about teammates, not about everyone the judgement fails to place.
            friendVerdictTarget = stranger;
            friendVerdict = TriState.DEFAULT;
            if (FormationRelations.canDismantle(instance, stranger))
                throw new IllegalStateException("An entity nobody could identify was allowed to take a formation down");
            // And the plate is where a player meets the rule: a refused click leaves the array standing.
            ItemStack plate = new ItemStack(MxtItems.FORMATION_PLATE.get());
            stranger.setItemInHand(InteractionHand.MAIN_HAND, plate);
            if (plate.useOn(plateClick(stranger, controller)) != InteractionResult.FAIL
                    || level.getData(MxtAttachments.FORMATION_WORLD).get(controller).isEmpty())
                throw new IllegalStateException("A refused dismantle changed the formation anyway");
            MxtServerConfig.INSTANCE.formations.teammatesCanDismantle.setValue(false);
            if (FormationRelations.canDismantle(instance, friend))
                throw new IllegalStateException("Turning the option back off did not restore the owner-only rule");
            if (plate.useOn(plateClick(stranger, controller)) != InteractionResult.FAIL
                    || level.getData(MxtAttachments.FORMATION_WORLD).get(controller).isEmpty())
                throw new IllegalStateException("A stranger took a formation down through the plate");
        } finally {
            friendVerdictTarget = null;
            friendVerdict = TriState.DEFAULT;
            MxtServerConfig.INSTANCE.formations.teammatesCanDismantle.setValue(configured);
            clearFormation(level, controller, definition);
        }
    }

    /**
     * The function modules: the registry that selects them, and what each of the three does. A wrong
     * {@code type} would decode into defaults and an unread module would fail silently, so both are pinned.
     */
    private static void verifyFormationActionTypes(ServerLevel level) {
        Identifier attackId = Identifier.parse("mxt_test:formation_attack_probe");
        Formation attack = formationDefinition(level, attackId);
        AttackFormationAction strike = module(attack, AttackFormationAction.class, attackId);
        if (!same(strike.damage().evaluate(FormulaContext.of(level)), 3.0D) || strike.effects().size() != 1)
            throw new IllegalStateException("The attack probe's damage or effect list did not decode");
        if (strike.damageType().isPresent() || !strike.attributeToOwner())
            throw new IllegalStateException("An attack module did not default to an untyped, owner-attributed strike");
        if (!attack.spareFriends())
            throw new IllegalStateException("The attack probe did not declare the friend-or-foe switch");
        if (formationDefinition(level, Identifier.parse("mxt_test:formation_inline_probe")).spareFriends())
            throw new IllegalStateException("A formation with no switch declared one anyway");

        Identifier buffId = Identifier.parse("mxt_test:formation_buff_probe");
        Formation buffProbe = formationDefinition(level, buffId);
        BuffFormationAction buff = module(buffProbe, BuffFormationAction.class, buffId);
        if (buff.target() != TargetMode.ALLIES || buff.abilities().size() != 1)
            throw new IllegalStateException("The benefit probe's target or ability list did not decode");
        if (buff.auraZone().isPresent() || !buff.maxBonus().isEmpty())
            throw new IllegalStateException("A benefit module with no aura fields invented an aura override");
        Identifier auraId = Identifier.parse("mxt_test:formation_aura_probe");
        BuffFormationAction aura = module(formationDefinition(level, auraId), BuffFormationAction.class, auraId);
        if (aura.auraZone().isEmpty() || aura.maxBonus().isEmpty())
            throw new IllegalStateException("The aura fields did not survive their move into a benefit module");

        Identifier protectionId = Identifier.parse("mxt_test:formation_protection_probe");
        Formation protectionProbe = formationDefinition(level, protectionId);
        ProtectionFormationAction protection = module(protectionProbe, ProtectionFormationAction.class, protectionId);
        if (protection.delegateToClaims())
            throw new IllegalStateException("A protection module delegated to claims without being asked to");
        Identifier openId = Identifier.parse("mxt_test:formation_protection_open_probe");
        ProtectionFormationAction open = module(formationDefinition(level, openId), ProtectionFormationAction.class, openId);
        // The pair is the defaults against every flag written false. An all-true record cannot tell "read as
        // true" from "never read", and the opened probe is what pins each field to the JSON that declares it.
        expectProtectionCoverage(protection, true, true, true, true, true, true, true, true, true, "The default protection module");
        expectProtectionCoverage(open, false, false, false, false, false, false, false, false, false, "The opened protection module");
        Identifier delegateId = Identifier.parse("mxt_test:formation_protection_delegate_probe");
        Formation delegateProbe = formationDefinition(level, delegateId);
        ProtectionFormationAction delegate = module(delegateProbe, ProtectionFormationAction.class, delegateId);
        if (!delegate.delegateToClaims() || !delegate.blockBreak() || !delegate.itemUse())
            throw new IllegalStateException("The delegating probe did not decode its switch, or lost the flags beside it");

        verifyFormationModuleCodec(level);
        verifyFormationAttackModule(level, attackId, attack);
        verifyFormationBuffModule(level, buffId, buffProbe);
        verifyFormationProtection(level, protectionId, protectionProbe);
        verifyFormationProtectionDelegation(level, delegateId, delegateProbe);
        verifyFormationProtectionLinkage(level, protectionId, protectionProbe);
    }

    /**
     * The stock: what a formation banks, what pays for it, and that the bank survives a save - asserted
     * separately, since a wrong split or a bank that reset on load is invisible in play.
     */
    private static void verifyFormationStorage(ServerLevel level) {
        Identifier id = Identifier.parse("mxt_test:formation_storage_probe");
        Formation definition = formationDefinition(level, id);
        Storage storage = definition.storage()
                .orElseThrow(() -> new IllegalStateException("The storage probe declares no stock"));
        Holder<Resource> common = requireHolder(MxtResourceKeys.RESOURCE, Identifier.parse("mxt:common"));
        Identifier cost = Identifier.parse("mxt:common");
        FormulaContext context = FormulaContext.of(level);

        Map<Identifier, Double> capacity = MaintainRule.capacities(storage, context);
        if (capacity.size() != 1 || !same(capacity.getOrDefault(cost, 0.0D), 500.0D))
            throw new IllegalStateException("A storage declaration's capacity did not decode: " + capacity);
        // The stock is a framework field, so a formation that says nothing about it keeps nothing — and one
        // that declares the object has to name what it stores rather than decoding into a no-op.
        if (formationDefinition(level, Identifier.parse("mxt_test:formation_inline_probe")).storage().isPresent())
            throw new IllegalStateException("A formation that declares no stock reported one");
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        if (Formation.DIRECT_CODEC.parse(ops, JsonParser.parseString("""
                {"structure":[{"offset":[0,0,0],"state":"minecraft:gold_block"}],"radius":8,
                 "storage":{}}
                """)).result().isPresent())
            throw new IllegalStateException("A stock with no capacity decoded instead of being reported");

        Map<Identifier, Double> room = Map.of(cost, 500.0D);
        expectStoragePlan(definition, context, Map.of(common, 50.0D), Map.of(), room,
                Map.of(), Map.of(), Map.of(cost, 40.0D), "a surplus the capacity can hold");
        expectStoragePlan(definition, context, Map.of(), Map.of(cost, 30.0D), room,
                Map.of(cost, 10.0D), Map.of(), Map.of(), "a bill the bank covers on its own");
        expectStoragePlan(definition, context, Map.of(common, 4.0D), Map.of(cost, 3.0D), room,
                Map.of(cost, 3.0D), Map.of(cost, 3.0D), Map.of(), "a bill split three ways");
        expectStoragePlan(definition, context, Map.of(common, 50.0D), Map.of(cost, 490.0D), room,
                Map.of(), Map.of(), Map.of(cost, 10.0D), "a bank with ten of room left");
        expectStoragePlan(definition, context, Map.of(common, 50.0D), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), "a formation with no capacity at all");
        if (!MaintainRule.remaining(definition, context, Map.of()).equals(Map.of(cost, 10.0D)))
            throw new IllegalStateException("The payer's share stopped being the bill minus the ground's supply");

        // High above the terrain, and not at the shared controller: the ground here supplies nothing once
        // the formation's own emitters are gone, while a chunk at the bottom of the world may hold ore.
        BlockPos controller = new BlockPos(8, level.getMaxY() - 16, 8);
        placeInline(level, controller, definition.structure());
        Block emitter = BuiltInRegistries.BLOCK.getValue(Identifier.parse("mxt:spirit_stone_block"));
        if (emitter == null || emitter == Blocks.AIR)
            throw new IllegalStateException("The storage audit needs the spirit stone block to emit aura");
        List<BlockPos> emitters = List.of(controller.offset(3, 0, 3), controller.offset(-3, 0, -3));
        try {
            // No payer at all: the ground supplies more than the bill, which is what has to reach the bank.
            activateFormation(level, controller, id, UUID.randomUUID());
            for (BlockPos emitterPos : emitters) {
                level.setBlock(emitterPos, emitter.defaultBlockState(), 3);
                AuraChunkTicker.markDirty(level, emitterPos);
            }
            AuraChunkTicker.flushDirty(level);
            FormationWorldTicker.dispatch(level);
            Map<Identifier, Double> banked = stock(level, controller);
            if (banked.getOrDefault(cost, 0.0D) <= 0.0D)
                throw new IllegalStateException("A formation standing on emitters banked nothing: " + banked);

            // The index has to carry the bank: it is the only per-instance state that is not a property of the
            // definition, and a bank that reset on load would make it worthless.
            Collector writer = new Collector();
            TagValueOutput output = TagValueOutput.createWithContext(writer, level.registryAccess());
            output.store(FormationWorldAttachment.MAP_CODEC, level.getData(MxtAttachments.FORMATION_WORLD));
            if (!writer.isEmpty())
                throw new IllegalStateException("Saving a formation with a bank reported: " + writer.getReport());
            FormationWorldAttachment saved = TagValueInput
                    .create(new Collector(), level.registryAccess(), output.buildResult())
                    .read(FormationWorldAttachment.MAP_CODEC)
                    .orElseThrow(() -> new IllegalStateException("Reading a saved bank failed"));
            if (!saved.get(controller).orElseThrow().stored().equals(banked))
                throw new IllegalStateException("A saved formation index lost its bank: "
                        + saved.get(controller).orElseThrow().stored());

            // The emitters go, so the bill is the bank's from now on, with still no owner to ask. Every
            // position is marked, since the two emitters may sit in different chunks.
            for (BlockPos emitterPos : emitters) {
                level.removeBlock(emitterPos, false);
                AuraChunkTicker.markDirty(level, emitterPos);
            }
            AuraChunkTicker.flushDirty(level);
            Map<Holder<Resource>, Double> afterRemoval = supplyOf(level, controller, definition);
            if (afterRemoval.getOrDefault(common, 0.0D) > 0.0D)
                throw new IllegalStateException("The storage audit needs ground that supplies nothing once its own "
                        + "emitters are gone, but found " + afterRemoval);
            int paidByBank = 0;
            while (!stock(level, controller).isEmpty() && paidByBank < 64) {
                FormationWorldTicker.dispatch(level);
                paidByBank++;
                if (level.getData(MxtAttachments.FORMATION_WORLD).get(controller).isEmpty())
                    throw new IllegalStateException("A formation with a bank was taken down with " + paidByBank
                            + " periods of it left: " + stock(level, controller));
            }
            if (paidByBank == 0)
                throw new IllegalStateException("A banked supply paid no period once the emitters were gone");
            if (paidByBank >= 64)
                throw new IllegalStateException("A bank never drained over " + paidByBank + " periods, with "
                        + supplyOf(level, controller, definition) + " still supplied per period and "
                        + stock(level, controller) + " banked");
            // ... and it is finite: the period after it runs out is the one that ends the formation.
            FormationWorldTicker.dispatch(level);
            if (level.getData(MxtAttachments.FORMATION_WORLD).get(controller).isPresent())
                throw new IllegalStateException("An empty bank still paid a bill its owner could not, with "
                        + supplyOf(level, controller, definition) + " supplied and " + stock(level, controller) + " banked");
        } finally {
            FormationWorldService.deactivate(level, controller);
            level.removeBlock(controller, false);
            removeInline(level, controller, definition.structure());
            for (BlockPos emitterPos : emitters) level.removeBlock(emitterPos, false);
        }
    }

    /**
     * The display module: where the boundary is drawn, how often, and that the ticker reaches it. Geometry is
     * asserted against the definition, and the periodic pass is asserted to reach the module - or reach none.
     */
    private static void verifyFormationRangeDisplay(ServerLevel level) {
        Identifier id = Identifier.parse("mxt_test:formation_range_probe");
        Formation definition = formationDefinition(level, id);
        RangeDisplayFormationAction display = module(definition, RangeDisplayFormationAction.class, id);
        if (display.shape() != Shape.SPHERE || display.points() != 24
                || display.intervalPeriods() != 2 || display.particle() != ParticleTypes.END_ROD)
            throw new IllegalStateException("The range probe's display module did not decode");
        // The defaults, from the JSON that omits them.
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        Formation bare = Formation.DIRECT_CODEC.parse(ops, JsonParser.parseString("""
                {"structure":[{"offset":[0,0,0],"state":"minecraft:gold_block"}],"radius":8,
                 "actions":[{"type":"mxt:range_display","particle":{"type":"minecraft:end_rod"}}]}
                """)).result().orElseThrow(() -> new IllegalStateException("A display module with only a particle did not decode"));
        RangeDisplayFormationAction defaults = module(bare, RangeDisplayFormationAction.class, id);
        if (defaults.shape() != Shape.RING || defaults.points() != 32
                || defaults.intervalPeriods() != 1)
            throw new IllegalStateException("A display module did not default to a ring drawn every period");

        Vec3 center = new Vec3(0.5D, 64.0D, 0.5D);
        double radius = 8.0D;
        List<Vec3> ring = FormationRangeDisplay.points(Shape.RING, center, radius, 16);
        expectOnBoundary(ring, center, radius, "A ring outline");
        for (Vec3 point : ring) {
            if (!same(point.y, center.y))
                throw new IllegalStateException("A ring outline left the controller's own level: " + point.y);
        }
        List<Vec3> sphere = FormationRangeDisplay.points(Shape.SPHERE, center, radius, 16);
        expectOnBoundary(sphere, center, radius, "A sphere outline");
        if (sphere.stream().mapToDouble(Vec3::y).min().orElseThrow() >= center.y
                || sphere.stream().mapToDouble(Vec3::y).max().orElseThrow() <= center.y)
            throw new IllegalStateException("A sphere outline never left the controller's own level");

        // Only the players looking at it are sent it, and the margin past the radius is what makes a
        // boundary legible from just outside itself.
        double edge = radius + FormationRangeDisplay.VISIBLE_MARGIN;
        if (!FormationRangeDisplay.visible(center, radius, center.add(edge - 0.5D, 0.0D, 0.0D))
                || FormationRangeDisplay.visible(center, radius, center.add(edge + 0.5D, 0.0D, 0.0D)))
            throw new IllegalStateException("The display's visibility rule does not follow its own margin");
        long period = FormationWorldTicker.PERIOD;
        // Interval two, counted from the first period: the even periods draw and the odd ones do not, and the
        // default of one draws on every period.
        if (!FormationRangeDisplay.due(display, 0L, period)
                || FormationRangeDisplay.due(display, period, period)
                || !FormationRangeDisplay.due(display, period * 2L, period)
                || FormationRangeDisplay.due(display, period * 3L, period))
            throw new IllegalStateException("A display module drew on a period its interval excludes");
        if (!FormationRangeDisplay.due(defaults, period, period))
            throw new IllegalStateException("A display module with the default interval skipped a period");

        BlockPos controller = prepareFormationController(level, definition);
        try {
            activateFormation(level, controller, id, null);
            FormationInstance instance = level.getData(MxtAttachments.FORMATION_WORLD).get(controller)
                    .orElseThrow(() -> new IllegalStateException("The range probe did not activate"));
            if (FormationActionRunner.perPeriod(level, definition, instance, controller) != 1)
                throw new IllegalStateException("A display module was declared but never reached by the periodic pass");
            Formation plain = formationDefinition(level, Identifier.parse("mxt_test:formation_inline_probe"));
            if (FormationActionRunner.perPeriod(level, plain, instance, controller) != 0)
                throw new IllegalStateException("A formation with no display module reached one anyway");
        } finally {
            clearFormation(level, controller, definition);
        }
    }

    /**
     * An unbound plate identifies the formation standing in front of it: the click path is driven for real,
     * the order candidates are tried in is pinned, and the server option is checked in both directions.
     */
    private static void verifyFormationPlateAutoDetect(ServerLevel level) {
        FormationPlateComponent unbound = new FormationPlateComponent(List.of(), Optional.empty());
        List<Holder<Formation>> candidates = FormationPlateItem.admissibleCandidates(unbound);
        if (candidates.isEmpty())
            throw new IllegalStateException("An unrestricted plate found nothing it may try");
        for (int index = 1; index < candidates.size(); index++) {
            if (HolderHelper.id(candidates.get(index - 1)).compareTo(HolderHelper.id(candidates.get(index))) >= 0)
                throw new IllegalStateException("The plate's candidates are not in a deterministic order: "
                        + HolderHelper.id(candidates.get(index - 1)) + " then " + HolderHelper.id(candidates.get(index)));
        }

        // The probes describe different structures, so standing one of them up here is what makes the
        // identification a question rather than a formality.
        Identifier standing = Identifier.parse("mxt_test:formation_range_probe");
        Formation standingDefinition = formationDefinition(level, standing);
        BlockPos clicked = new BlockPos(4, level.getMinY() + 2, 4);
        placeInline(level, clicked, standingDefinition.structure());
        FakePlayer holder = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "mxt-audit-auto"));
        ItemStack plate = new ItemStack(MxtItems.FORMATION_PLATE.get());
        holder.setItemInHand(InteractionHand.MAIN_HAND, plate);
        boolean configured = MxtServerConfig.INSTANCE.formations.plateAutoDetect.getValue();
        try {
            // What the identification has to answer is the first admissible definition whose structure is
            // standing at the clicked block. Everything else about the order is the caller's business.
            List<Holder<Formation>> matching = candidates.stream()
                    .filter(candidate -> FormationStructureValidator.STRUCTURE.matches(level, clicked, candidate.value()))
                    .toList();
            if (matching.size() != 1 || !HolderHelper.id(matching.getFirst()).equals(standing))
                throw new IllegalStateException("The structure standing at the clicked block identified "
                        + matching.stream().map(HolderHelper::id).toList() + " instead of only " + standing);
            Holder<Formation> expected = matching.getFirst();
            Optional<Match> match = FormationCenters.resolveAny(level, clicked, candidates);
            if (match.isEmpty() || !match.get().center().equals(clicked) || !match.get().formation().equals(expected))
                throw new IllegalStateException("The identification did not answer with the first admissible match");

            MxtServerConfig.INSTANCE.formations.plateAutoDetect.setValue(false);
            if (plate.useOn(plateClick(holder, clicked)) != InteractionResult.FAIL
                    || level.getData(MxtAttachments.FORMATION_WORLD).get(clicked).isPresent())
                throw new IllegalStateException("An unbound plate raised something with the option turned off");

            MxtServerConfig.INSTANCE.formations.plateAutoDetect.setValue(true);
            if (plate.useOn(plateClick(holder, clicked)) != InteractionResult.SUCCESS)
                throw new IllegalStateException("An unbound plate refused the formation standing in front of it");
            Identifier raised = level.getData(MxtAttachments.FORMATION_WORLD).get(clicked)
                    .orElseThrow(() -> new IllegalStateException("A successful identification raised nothing"))
                    .formation();
            if (!raised.equals(HolderHelper.id(expected)))
                throw new IllegalStateException("An unbound plate raised a formation other than the one it identified: "
                        + raised + " instead of " + HolderHelper.id(expected));
            // The same plate is the off switch, which is what makes adding the identification safe.
            if (plate.useOn(plateClick(holder, clicked)) != InteractionResult.SUCCESS
                    || level.getData(MxtAttachments.FORMATION_WORLD).get(clicked).isPresent())
                throw new IllegalStateException("An identifying plate could not take the formation down again");
        } finally {
            MxtServerConfig.INSTANCE.formations.plateAutoDetect.setValue(configured);
            FormationWorldService.deactivate(level, clicked);
            level.removeBlock(clicked, false);
            removeInline(level, clicked, standingDefinition.structure());
        }
    }

    /**
     * A right-click on one block, held in the main hand: the plate's own click path, driven without a client.
     */
    private static UseOnContext plateClick(ServerPlayer player, BlockPos position) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(position.getCenter(), Direction.UP, position, false));
    }

    /**
     * Asserts one period's three-way split of the upkeep: what the bank pays, what is left to the payer, and
     * what the bank gains.
     */
    private static void expectStoragePlan(Formation definition, FormulaContext context,
                                          Map<Holder<Resource>, Double> supplied, Map<Identifier, Double> stored,
                                          Map<Identifier, Double> capacity, Map<Identifier, Double> fromStock,
                                          Map<Identifier, Double> fromOwner, Map<Identifier, Double> deposit,
                                          String what) {
        PaymentPlan plan =
                MaintainRule.plan(definition, context, supplied, stored, capacity);
        if (!plan.fromStock().equals(fromStock) || !plan.fromOwner().equals(fromOwner) || !plan.deposit().equals(deposit))
            throw new IllegalStateException("The upkeep split was wrong for " + what + ": bank paid " + plan.fromStock()
                    + ", owner owes " + plan.fromOwner() + ", bank gains " + plan.deposit());
    }

    /**
     * What a standing formation has banked, or nothing when it is not standing.
     */
    private static Map<Identifier, Double> stock(ServerLevel level, BlockPos controller) {
        return level.getData(MxtAttachments.FORMATION_WORLD).get(controller)
                .map(FormationInstance::stored).orElse(Map.of());
    }

    /**
     * What the ground supplies a formation this period, for the assertions that have to say why they failed.
     */
    private static Map<Holder<Resource>, Double> supplyOf(ServerLevel level, BlockPos controller, Formation definition) {
        return FormationAbsorption.absorbedFor(level, controller, definition.radius().evaluate(FormulaContext.of(level)));
    }

    /**
     * Asserts every point of an outline sits exactly on the boundary.
     */
    private static void expectOnBoundary(List<Vec3> points, Vec3 center, double radius, String what) {
        if (points.isEmpty())
            throw new IllegalStateException(what + " produced no points");
        for (Vec3 point : points) {
            if (!same(point.distanceTo(center), radius))
                throw new IllegalStateException(what + " put a point off the boundary: " + point.distanceTo(center));
        }
    }

    /**
     * One module list has to survive the codec in both directions, and an unknown type has to be refused: a
     * silent fallback to the default entry would turn a typo into an array that stands there doing nothing.
     */
    private static void verifyFormationModuleCodec(ServerLevel level) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        String json = """
                {"structure":[{"offset":[0,0,0],"state":"minecraft:gold_block"}],"radius":8,
                 "actions":[{"type":"mxt:attack","damage":1},
                            {"type":"mxt:buff","abilities":["mxt_test:water_shield"]},
                            {"type":"mxt:protection","delegate_to_claims":true},
                            {"type":"mxt:none"}]}
                """;
        DataResult<Formation> decoded = Formation.DIRECT_CODEC.parse(ops, JsonParser.parseString(json));
        if (decoded.result().isEmpty())
            throw new IllegalStateException("A formation with four modules did not decode: "
                    + decoded.error().map(String::valueOf).orElse("no detail"));
        Formation definition = decoded.result().orElseThrow();
        if (!(definition.actions().get(0) instanceof AttackFormationAction)
                || !(definition.actions().get(1) instanceof BuffFormationAction)
                || !(definition.actions().get(2) instanceof ProtectionFormationAction protection)
                || !protection.delegateToClaims()
                || !(definition.actions().get(3) instanceof EmptyFormationAction))
            throw new IllegalStateException("A formation's module list did not dispatch on its type ids");
        DataResult<Formation> roundTripped = Formation.DIRECT_CODEC.parse(ops,
                Formation.DIRECT_CODEC.encodeStart(ops, definition).getOrThrow());
        if (roundTripped.result().isEmpty()
                || !(roundTripped.result().orElseThrow().actions().getFirst() instanceof AttackFormationAction restored)
                || !same(restored.damage().evaluate(FormulaContext.EMPTY), 1.0D))
            throw new IllegalStateException("A module list did not survive an encode and a decode");
        DataResult<Formation> unknown = Formation.DIRECT_CODEC.parse(ops, JsonParser.parseString("""
                {"structure":[{"offset":[0,0,0],"state":"minecraft:gold_block"}],"radius":8,
                 "actions":[{"type":"mxt_test:not_a_module"}]}
                """));
        if (unknown.result().isPresent())
            throw new IllegalStateException("An unknown module type decoded instead of being reported");
    }

    /**
     * An attack module hurts strangers, spares the owner and his friends, and credits the owner for what it
     * does: damage with no attacker leaves no mob aggro behind, and nothing throws when it goes missing.
     */
    private static void verifyFormationAttackModule(ServerLevel level, Identifier id, Formation definition) {
        BlockPos controller = prepareFormationController(level, definition);
        Pig owner = spawnProbe(level, controller.offset(2, 1, 0));
        Pig friend = spawnProbe(level, controller.offset(0, 1, 2));
        Pig stranger = spawnProbe(level, controller.offset(-2, 1, 0));
        try {
            friendVerdictTarget = friend;
            friendVerdict = TriState.TRUE;
            double before = stranger.getHealth();
            activateFormation(level, controller, id, owner.getUUID());
            FormationWorldTicker.dispatch(level);
            if (!stranger.hasEffect(MobEffects.GLOWING))
                throw new IllegalStateException("An attack module did not reach a stranger");
            if (stranger.getHealth() >= before)
                throw new IllegalStateException("An attack module's damage did not land: " + stranger.getHealth());
            if (stranger.getLastHurtByMob() != owner)
                throw new IllegalStateException("An attack module's damage was not attributed to the formation's owner");
            if (owner.hasEffect(MobEffects.GLOWING) || friend.hasEffect(MobEffects.GLOWING))
                throw new IllegalStateException("An attack module reached its owner or a friend");
            // Being spared means never being tracked, so turning the verdict around is the same pick-up
            // path a declaration of spare_friends has — and the module has to run through it as well.
            friendVerdict = TriState.FALSE;
            FormationWorldTicker.dispatch(level);
            if (!friend.hasEffect(MobEffects.GLOWING))
                throw new IllegalStateException("An attack module kept sparing an entity that stopped being a friend");
        } finally {
            friendVerdictTarget = null;
            friendVerdict = TriState.DEFAULT;
            clearFormation(level, controller, definition, owner, friend, stranger);
        }
    }

    /**
     * A benefit module grants under the formation's own source, respects its target, and takes the grant back
     * from an entity it stops targeting - the one release path nothing else covers.
     */
    private static void verifyFormationBuffModule(ServerLevel level, Identifier id, Formation definition) {
        BlockPos controller = prepareFormationController(level, definition);
        Identifier source = FormationSources.of(id);
        Pig owner = spawnProbe(level, controller.offset(2, 1, 0));
        Pig friend = spawnProbe(level, controller.offset(0, 1, 2));
        Pig stranger = spawnProbe(level, controller.offset(-2, 1, 0));
        try {
            friendVerdictTarget = friend;
            friendVerdict = TriState.TRUE;
            activateFormation(level, controller, id, owner.getUUID());
            FormationWorldTicker.dispatch(level);
            if (!holdsSource(friend, source))
                throw new IllegalStateException("A benefit module did not reach a friend of its owner");
            // The owner is his own friend, so an ALLIES module reaches him too: the modes are one rule at
            // different widths, not two rules that happen to agree.
            if (!holdsSource(owner, source))
                throw new IllegalStateException("A benefit module did not reach its own owner");
            if (holdsSource(stranger, source))
                throw new IllegalStateException("A benefit module reached a stranger");
            friendVerdict = TriState.FALSE;
            FormationWorldTicker.dispatch(level);
            if (holdsSource(friend, source))
                throw new IllegalStateException("A benefit module left its grant on an entity it no longer targets");
            friendVerdict = TriState.TRUE;
            FormationWorldTicker.dispatch(level);
            if (!holdsSource(friend, source))
                throw new IllegalStateException("A benefit module did not restore a grant it had released");
        } finally {
            friendVerdictTarget = null;
            friendVerdict = TriState.DEFAULT;
            clearFormation(level, controller, definition, owner, friend, stranger);
        }
    }

    /**
     * A protection ward holds its radius against everyone except its owner and, while the judgement is on, his
     * friends, and is asked about both ends of an action; exemptions are asked by id, having no player to use.
     */
    private static void verifyFormationProtection(ServerLevel level, Identifier id, Formation definition) {
        BlockPos controller = prepareFormationController(level, definition);
        Pig owner = spawnProbe(level, controller.offset(2, 1, 0));
        Pig friend = spawnProbe(level, controller.offset(0, 1, 2));
        Pig stranger = spawnProbe(level, controller.offset(-2, 1, 0));
        Pig outsider = spawnProbe(level, controller.offset(12, 1, 0));
        BlockPos inside = controller.offset(2, 0, 0);
        BlockPos outside = controller.offset(12, 0, 0);
        boolean configured = MxtServerConfig.INSTANCE.formations.respectFriends.getValue();
        try {
            friendVerdictTarget = friend;
            friendVerdict = TriState.TRUE;
            activateFormation(level, controller, id, owner.getUUID());
            for (Action action : Action.values()) {
                if (!FormationProtection.prevented(level, action, inside, stranger.getUUID()))
                    throw new IllegalStateException("A ward let a stranger through: " + action);
                if (FormationProtection.prevented(level, action, inside, owner.getUUID()))
                    throw new IllegalStateException("A ward locked its own owner out: " + action);
                if (FormationProtection.prevented(level, action, inside, friend.getUUID()))
                    throw new IllegalStateException("A ward did not spare a friend of its owner: " + action);
                if (FormationProtection.prevented(level, action, outside, outsider.getUUID()))
                    throw new IllegalStateException("A ward reached outside its own radius: " + action);
            }
            // Both ends of the action, each on its own: an outsider reaching in, and an insider reaching
            // out. Looking at only one of them would be watching the wrong end of a ward.
            if (!FormationProtection.prevented(level, Action.BREAK, inside, outsider.getUUID()))
                throw new IllegalStateException("A ward ignored a stranger reaching into it from outside");
            if (!FormationProtection.prevented(level, Action.BREAK, outside, stranger.getUUID()))
                throw new IllegalStateException("A ward ignored a stranger acting from inside it");
            if (!FormationProtection.prevented(level, Action.ITEM_USE, null, stranger.getUUID()))
                throw new IllegalStateException("A ward ignored an act with no target by an actor inside it");
            if (FormationProtection.prevented(level, Action.ITEM_USE, null, outsider.getUUID()))
                throw new IllegalStateException("A ward answered for an act with no target and an actor outside it");
            // An explosion has no actor to be exempted, which the owner's own blast would otherwise be.
            if (!FormationProtection.prevented(level, Action.EXPLOSION, inside, null))
                throw new IllegalStateException("A ward let an actorless action inside it through");
            MxtServerConfig.INSTANCE.formations.respectFriends.setValue(false);
            if (!FormationProtection.prevented(level, Action.BREAK, inside, friend.getUUID()))
                throw new IllegalStateException("Turning the friend judgement off did not restore the ward");
            if (FormationProtection.prevented(level, Action.BREAK, inside, owner.getUUID()))
                throw new IllegalStateException("The friend judgement's option locked a ward's owner out");
        } finally {
            friendVerdictTarget = null;
            friendVerdict = TriState.DEFAULT;
            MxtServerConfig.INSTANCE.formations.respectFriends.setValue(configured);
            clearFormation(level, controller, definition, owner, friend, stranger, outsider);
        }
    }

    /**
     * A module that hands its protection to claims enforces none of its own flags while there is something to
     * hand it to. With no claim plugin here, both answers of the option are asserted for every action.
     */
    private static void verifyFormationProtectionDelegation(ServerLevel level, Identifier id, Formation definition) {
        BlockPos controller = prepareFormationController(level, definition);
        Pig owner = spawnProbe(level, controller.offset(2, 1, 0));
        Pig stranger = spawnProbe(level, controller.offset(-2, 1, 0));
        BlockPos inside = controller.offset(2, 0, 0);
        boolean configured = MxtServerConfig.INSTANCE.compat.delegateRequiresClaims.getValue();
        try {
            activateFormation(level, controller, id, owner.getUUID());
            MxtServerConfig.INSTANCE.compat.delegateRequiresClaims.setValue(true);
            if (FormationProtection.delegationHandsOver())
                throw new IllegalStateException("A formation handed its protection over with no claim protection to hand it to");
            for (Action action : Action.values()) {
                if (!FormationProtection.prevented(level, action, inside, stranger.getUUID()))
                    throw new IllegalStateException("A delegating ward fell back to nothing rather than to its own flags: " + action);
            }
            // The literal reading, on purpose: hand it over regardless, and enforce nothing.
            MxtServerConfig.INSTANCE.compat.delegateRequiresClaims.setValue(false);
            if (!FormationProtection.delegationHandsOver())
                throw new IllegalStateException("A formation refused to hand its protection over with the server option off");
            for (Action action : Action.values()) {
                if (FormationProtection.prevented(level, action, inside, stranger.getUUID()))
                    throw new IllegalStateException("A delegated ward enforced a flag it handed over: " + action);
            }
        } finally {
            MxtServerConfig.INSTANCE.compat.delegateRequiresClaims.setValue(configured);
            clearFormation(level, controller, definition, owner, stranger);
        }
    }

    /**
     * The claim linkage options, as far as a server without a claim plugin can show them: they stay inert
     * rather than becoming "cannot build", and only a ward counts as a claim of jurisdiction.
     */
    private static void verifyFormationProtectionLinkage(ServerLevel level, Identifier id, Formation definition) {
        Formation attack = formationDefinition(level, Identifier.parse("mxt_test:formation_attack_probe"));
        if (!FormationProtection.hasProtection(definition) || FormationProtection.hasProtection(attack))
            throw new IllegalStateException("The audit could not tell a formation with a ward from one without");
        ClaimLinkage configured = MxtServerConfig.INSTANCE.compat.claimLinkage.getValue();
        boolean configuredPermission = MxtServerConfig.INSTANCE.compat.wardsNeedClaimPermission.getValue();
        BlockPos controller = prepareFormationController(level, definition);
        Pig owner = spawnProbe(level, controller.offset(2, 1, 0));
        Pig stranger = spawnProbe(level, controller.offset(-2, 1, 0));
        BlockPos inside = controller.offset(2, 0, 0);
        try {
            MxtServerConfig.INSTANCE.compat.claimLinkage.setValue(ClaimLinkage.CLAIMS_ONLY);
            if (FormationProtection.claimsOnlyRefuses(level, controller))
                throw new IllegalStateException("claims_only refused a ward with no claim plugin to require");
            // The foreign-claim rule is asked with the option on, so that this asserts the rule and not the
            // default: with no claims it has nobody's land to judge either.
            MxtServerConfig.INSTANCE.compat.wardsNeedClaimPermission.setValue(true);
            if (FormationProtection.foreignClaimRefuses(level, controller, stranger.getUUID()))
                throw new IllegalStateException("The foreign-claim rule refused a ward with no claims to judge");
            // The modes have to leave ordinary activation working, not merely report themselves as inert.
            activateFormation(level, controller, id, owner.getUUID());
            MxtServerConfig.INSTANCE.compat.claimLinkage.setValue(ClaimLinkage.CLAIMS_PRECEDENCE);
            for (Action action : Action.values()) {
                if (!FormationProtection.prevented(level, action, inside, stranger.getUUID()))
                    throw new IllegalStateException("claims_precedence handed a ward over with no claim to hand it to: " + action);
            }
        } finally {
            MxtServerConfig.INSTANCE.compat.claimLinkage.setValue(configured);
            MxtServerConfig.INSTANCE.compat.wardsNeedClaimPermission.setValue(configuredPermission);
            clearFormation(level, controller, definition, owner, stranger);
        }
    }

    /**
     * Reads a module out of a decoded definition by its record type, which is also the assertion that the
     * definition really carries one.
     */
    private static <T extends FormationActionType> T module(Formation definition, Class<T> type, Identifier id) {
        return definition.actions().stream().filter(type::isInstance).map(type::cast).findFirst()
                .orElseThrow(() -> new IllegalStateException(id + " declares no " + type.getSimpleName()));
    }

    /**
     * Every flag of a protection module, as declared and as {@code covers} answers for it.
     */
    private static void expectProtectionCoverage(ProtectionFormationAction ward, boolean blockBreak, boolean blockPlace,
                                                 boolean blockInteract, boolean explosions, boolean mobGriefing,
                                                 boolean entityInteract, boolean attackEntity, boolean itemUse,
                                                 boolean spareFriends, String what) {
        if (ward.blockBreak() != blockBreak || ward.blockPlace() != blockPlace || ward.blockInteract() != blockInteract
                || ward.explosions() != explosions || ward.mobGriefing() != mobGriefing
                || ward.entityInteract() != entityInteract || ward.attackEntity() != attackEntity
                || ward.itemUse() != itemUse || ward.spareFriends() != spareFriends)
            throw new IllegalStateException(what + " did not decode the flags it declares");
        for (Action action : Action.values()) {
            boolean expected = switch (action) {
                case BREAK -> blockBreak;
                case PLACE -> blockPlace;
                case INTERACT -> blockInteract;
                case EXPLOSION -> explosions;
                case MOB_GRIEFING -> mobGriefing;
                case ENTITY_INTERACT -> entityInteract;
                case ATTACK_ENTITY -> attackEntity;
                case ITEM_USE -> itemUse;
            };
            if (FormationProtection.covers(ward, action) != expected)
                throw new IllegalStateException(what + " answered for " + action + " against its own flag");
        }
    }

    /**
     * Evaluates {@code mxt:formation_ally} the way a per-entity action does: decoded from its own JSON by id,
     * with the formation carrier in the context, which is the only route to the owner that shape has.
     */
    private static boolean allied(Pig entity, FormationCarrier carrier) {
        EntityCondition condition = EntityCondition.SINGLE_CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\": \"mxt:formation_ally\"}")).getOrThrow();
        EntityConditionContext context = new EntityConditionContext(entity, FormulaContext.of(entity));
        context.set(FormationCarrier.KEY, carrier);
        return condition.test(context);
    }

    /**
     * The saved file has to survive a rename: keys are short now and the friend ranks moved into the compat
     * tab, so an old file must still land on the entries it meant, or a server that had tuned something
     * quietly goes back to the default.
     */
    private static void verifyConfigKeyMigration() {
        MxtServerConfig config = MxtServerConfig.INSTANCE;
        String before = config.serialize();
        boolean ally = config.compat.ftbTeamsAlly.getValue();
        boolean invited = config.compat.ftbTeamsInvited.getValue();
        boolean respect = config.formations.respectFriends.getValue();
        boolean detect = config.formations.plateAutoDetect.getValue();
        try {
            config.deserialize("""
                    {"friends": {"config.mxt.server.friends.ftb_teams_ally": false},
                     "formation": {"config.mxt.server.formation.respect_friends": false,
                                   "plate_auto_detect": false},
                     "compat": {"ftb_teams_invited": true}}
                    """);
            if (config.compat.ftbTeamsAlly.getValue())
                throw new IllegalStateException("An old full key lost its value: the tab rename or the key rule did not apply");
            if (!config.compat.ftbTeamsInvited.getValue())
                throw new IllegalStateException("A key that was already short was rewritten by the key rules");
            if (config.formations.respectFriends.getValue() || config.formations.plateAutoDetect.getValue())
                throw new IllegalStateException("A renamed key did not land on its entry");
        } finally {
            // Whatever the check borrowed goes back, and the rest of the file has to be untouched by it.
            config.compat.ftbTeamsAlly.setValue(ally);
            config.compat.ftbTeamsInvited.setValue(invited);
            config.formations.respectFriends.setValue(respect);
            config.formations.plateAutoDetect.setValue(detect);
            if (!config.serialize().equals(before))
                throw new IllegalStateException("The config key migration check did not leave the config as it found it");
        }
    }

    /**
     * The friend system: what "temporary" means, what the identification event may override, and that the
     * command reaches a list - the codec must carry both lists, since a death serialises the attachment.
     */
    private static void verifyFriendIdentification(ServerLevel level) {
        NameAndId saved = new NameAndId(UUID.randomUUID(), "mxt-audit-saved");
        NameAndId session = new NameAndId(UUID.randomUUID(), "mxt-audit-session");

        // What the codec holds is the definition of what a death keeps: both lists, because NeoForge copies
        // an attachment on death by serialising and deserialising it.
        FriendAttachment encoded = new FriendAttachment();
        if (encoded.add(session) != AddResult.ADDED
                || encoded.addPermanent(saved) != AddResult.ADDED)
            throw new IllegalStateException("A fresh friend list refused its first entries");
        if (!encoded.isFriend(saved.id()) || !encoded.isFriend(session.id()))
            throw new IllegalStateException("Adding a friend did not make it a friend");
        Collector writer = new Collector();
        TagValueOutput output = TagValueOutput.createWithContext(writer, level.registryAccess());
        output.store(FriendAttachment.CODEC, encoded);
        if (!writer.isEmpty())
            throw new IllegalStateException("Saving a friend list reported: " + writer.getReport());
        FriendAttachment decoded = FriendAttachment.CODEC.codec().parse(NbtOps.INSTANCE, output.buildResult()).getOrThrow();
        if (!decoded.isFriend(saved.id()) || decoded.permanent().size() != 1)
            throw new IllegalStateException("A permanent friend did not survive the save");
        if (!decoded.isFriend(session.id()) || decoded.temporary().size() != 1)
            throw new IllegalStateException("A session friend was not saved, so dying would take it with it");

        // A hand-edited file can put one player on both lists; the saved entry is the one that has to win.
        FriendAttachment merged = FriendAttachment.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"permanent": [{"id": "%s", "name": "mxt-audit-both"}],
                 "temporary": [{"id": "%s", "name": "mxt-audit-both"}]}
                """.formatted(session.id(), session.id()))).getOrThrow();
        if (merged.permanent().size() != 1 || !merged.temporary().isEmpty())
            throw new IllegalStateException("A player on both lists was kept as both a saved and a session friend");

        // The two lists describe one player at most: saving a session friend moves it rather than copying it.
        FriendAttachment promoting = new FriendAttachment();
        if (promoting.add(session) != AddResult.ADDED
                || promoting.addPermanent(session) != AddResult.ADDED)
            throw new IllegalStateException("Saving a session friend was refused");
        if (!promoting.temporary().isEmpty() || promoting.permanent().size() != 1)
            throw new IllegalStateException("Saving a session friend left it on both lists");
        if (promoting.add(session) != AddResult.ALREADY_PERMANENT
                || promoting.addPermanent(session) != AddResult.ALREADY_PERMANENT)
            throw new IllegalStateException("A saved friend was not reported as already saved");
        if (promoting.remove(session.id()) != RemoveResult.PERMANENT || !promoting.isFriend(session.id()))
            throw new IllegalStateException("A session removal took a saved friend away");
        if (promoting.removePermanent(session.id()) != RemoveResult.REMOVED || promoting.isFriend(session.id()))
            throw new IllegalStateException("Removing a saved friend left it behind");
        if (promoting.removePermanent(session.id()) != RemoveResult.NOT_A_FRIEND)
            throw new IllegalStateException("Removing a saved friend twice reported a change");

        FakePlayer judge = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "mxt-audit-friend"));
        Pig target = new Pig(EntityType.PIG, level);
        Pig stranger = new Pig(EntityType.PIG, level);
        // An entity is its own friend, so no consumer has to special-case self, and a judge with no list is
        // nobody's friend rather than everybody's.
        if (FriendService.builtin(judge, judge) != TriState.TRUE || FriendService.builtin(judge, target) != TriState.FALSE)
            throw new IllegalStateException("A judge with no friend list did not answer from the list alone");
        if (FriendService.builtin(target, target) != TriState.TRUE || FriendService.builtin(target, stranger) != TriState.FALSE)
            throw new IllegalStateException("A judge that is not a player was not nobody's friend");
        // The list is read, never created: this is asked about every entity in range of every effect.
        if (judge.getExistingData(MxtAttachments.FRIEND).isPresent())
            throw new IllegalStateException("Asking whether somebody is a friend created a friend list for the judge");
        judge.getData(MxtAttachments.FRIEND).addPermanent(new NameAndId(target.getUUID(), "mxt-audit-target"));
        if (!FriendService.isFriend(judge, target))
            throw new IllegalStateException("A saved friend was not recognised");
        // Reachable by id from JSON, not merely present as a Java object: a registration that is missing or
        // spelled differently is invisible until a datapack tries to use it.
        if (!BiEntityCondition.SINGLE_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\": \"mxt:friend\"}"))
                .getOrThrow().test(judge, target, FormulaContext.EMPTY))
            throw new IllegalStateException("mxt:friend is not reachable as a datapack condition");

        // The event gets the question first, in both directions...
        friendVerdictTarget = target;
        friendVerdict = TriState.FALSE;
        if (FriendService.identify(judge, target) != TriState.FALSE || FriendService.isFriend(judge, target))
            throw new IllegalStateException("A listener could not turn a saved friend into a stranger");
        friendVerdictTarget = stranger;
        friendVerdict = TriState.TRUE;
        if (!FriendService.isFriend(judge, stranger))
            throw new IllegalStateException("A listener could not turn a stranger into a friend");
        // ... and abstaining hands the question back rather than answering "no".
        friendVerdict = TriState.DEFAULT;
        if (!FriendService.isFriend(judge, target) || FriendService.isFriend(judge, stranger))
            throw new IllegalStateException("A listener that abstained did not hand the question back to the friend list");
        friendVerdictTarget = null;

        // An id-only question is the form that reaches a source with its own per-player data, and the only
        // form for an offline judge: with no listener and no entity, nobody can answer - not a disguised "no".
        if (FriendService.identify(stranger.getUUID(), null, target) != TriState.DEFAULT)
            throw new IllegalStateException("A question with no judge entity and no listener invented an answer");
        if (FriendService.identify(judge.getUUID(), null, judge) != TriState.TRUE)
            throw new IllegalStateException("An entity was not its own friend when named by id alone");
        if (FriendCache.lookup(judge.getUUID(), target.getUUID()) != TriState.DEFAULT)
            throw new IllegalStateException("A judge nobody has logged in as was already mirrored");

        // The FTB Teams source is a soft dependency, and this is what keeps it soft: with FTB Teams absent
        // the listener must answer nothing, without loading a single FTB class.
        Relation ftb = new Relation(UUID.randomUUID(), null, stranger);
        FtbTeamsCompat.onRelation(ftb);
        if (ftb.answered())
            throw new IllegalStateException("The FTB Teams source answered for an owner it knows nothing about");

        // The command has to reach the list, and a name has to resolve while that player is offline; seeding
        // the profile cache keeps the lookup off the network.
        String name = "MxtAuditFriend";
        UUID named = UUID.randomUUID();
        MinecraftServer server = level.getServer();
        server.services().nameToIdCache().add(new NameAndId(named, name));
        CommandSourceStack source = new CommandSourceStack(judge.commandSource(), judge.position(), Vec2.ZERO, level,
                PermissionSet.ALL_PERMISSIONS, judge.getName().getString(), judge.getDisplayName(), server, judge);
        FriendAttachment throughCommand = judge.getData(MxtAttachments.FRIEND);
        server.getCommands().performPrefixedCommand(source, "mxt friend add " + name);
        if (throughCommand.permanent().stream().anyMatch(friend -> friend.id().equals(named))
                || throughCommand.temporary().stream().noneMatch(friend -> friend.id().equals(named)))
            throw new IllegalStateException("/mxt friend add did not add a session friend: " + throughCommand.temporary());
        server.getCommands().performPrefixedCommand(source, "mxt friend permanent add " + name);
        if (throughCommand.temporary().stream().anyMatch(friend -> friend.id().equals(named))
                || throughCommand.permanent().stream().noneMatch(friend -> friend.id().equals(named)))
            throw new IllegalStateException("/mxt friend permanent add did not move the entry to the saved list: "
                    + throughCommand.permanent());
        // The session removal refuses a saved friend. It is the refusal a player is most likely to meet, and
        // the one that must not quietly succeed.
        server.getCommands().performPrefixedCommand(source, "mxt friend remove " + name);
        if (throughCommand.permanent().stream().noneMatch(friend -> friend.id().equals(named)))
            throw new IllegalStateException("/mxt friend remove took a saved friend away");
        server.getCommands().performPrefixedCommand(source, "mxt friend permanent remove " + name);
        if (throughCommand.isFriend(named))
            throw new IllegalStateException("/mxt friend permanent remove left the friend behind");

        // A login is what ends a session, and it is posted through the real bus: the failure worth
        // guarding against is not a wrong comparison, it is a bridge that never runs.
        throughCommand.add(new NameAndId(stranger.getUUID(), "mxt-audit-session-target"));
        if (throughCommand.temporary().isEmpty())
            throw new IllegalStateException("A session friend could not be added for the login check");
        NeoForge.EVENT_BUS.post(new PlayerLoggedInEvent(judge));
        if (!throughCommand.temporary().isEmpty())
            throw new IllegalStateException("Logging in did not end the session friend list");
        if (throughCommand.permanent().stream().noneMatch(friend -> friend.id().equals(target.getUUID())))
            throw new IllegalStateException("Logging in dropped a saved friend");

        // The login is also where the mirror is filled, and the mirror is what lets the built-in system answer
        // for a player whose entity is gone; no listener speaks here, so the answer can only be the mirror's.
        if (FriendCache.lookup(judge.getUUID(), target.getUUID()) != TriState.TRUE
                || FriendCache.lookup(judge.getUUID(), stranger.getUUID()) != TriState.FALSE)
            throw new IllegalStateException("Logging in did not mirror the friend lists");
        if (FriendService.identify(judge.getUUID(), null, target) != TriState.TRUE)
            throw new IllegalStateException("The built-in system could not answer for an offline judge");

        // The logout end of the session makes the mirror correct for the offline stretch: a change made while
        // the player is online is invisible to it until the session ends.
        throughCommand.addPermanent(new NameAndId(stranger.getUUID(), "mxt-audit-late"));
        if (FriendCache.lookup(judge.getUUID(), stranger.getUUID()) != TriState.FALSE)
            throw new IllegalStateException("The mirror followed a change made during the session");
        NeoForge.EVENT_BUS.post(new PlayerLoggedOutEvent(judge));
        if (FriendCache.lookup(judge.getUUID(), stranger.getUUID()) != TriState.TRUE)
            throw new IllegalStateException("Logging out did not refresh the friend mirror");

        CommandNode<CommandSourceStack> friend = server.getCommands().getDispatcher().getRoot().getChild("mxt").getChild("friend");
        if (friend == null || friend.getChild("list") == null || friend.getChild("add") == null
                || friend.getChild("remove") == null || friend.getChild("permanent") == null
                || friend.getChild("permanent").getChild("add") == null
                || friend.getChild("permanent").getChild("remove") == null)
            throw new IllegalStateException("The friend command is not registered under /mxt friend");
        // A friend list belongs to a player, so there is nothing for any other source to act on.
        if (friend.getRequirement().test(server.createCommandSourceStack()))
            throw new IllegalStateException("The friend command accepted a source that is not a player");
        // The alias is a separate registration from the /mxt subtree and the server option decides whether
        // it exists, so the check is that presence follows the option rather than that it is present.
        CommandNode<CommandSourceStack> alias = server.getCommands().getDispatcher().getRoot().getChild("friend");
        if ((alias == null) == MxtServerConfig.INSTANCE.commands.friend.getValue())
            throw new IllegalStateException("The top-level /friend alias does not follow its server option");
        if (alias != null && !children(alias).equals(children(friend)))
            throw new IllegalStateException("The /friend alias and /mxt friend expose different children: "
                    + children(alias) + " vs " + children(friend));
        LOGGER.info("MiXianTu friend audit: both lists surviving the codec and a real login ending the session "
                + "one while leaving the saved one alone; a session friend promoted rather than duplicated; a "
                + "hand-edited file listing one player twice resolved to the saved entry; a listener's TriState "
                + "verdict outranking the friend lists while an abstaining listener hands the question back; an "
                + "id-only question answered by self or by a listener and otherwise left unanswered; the mirror of "
                + "the lists filled at login and refreshed at logout so the built-in system can answer for an "
                + "offline judge; a judge that is "
                + "not a player owning nobody; the FTB Teams source staying silent with the mod absent, and its two "
                + "rank options reading their own entries; and add, "
                + "permanent add, remove and permanent remove each reaching the list they "
                + "name through the dispatcher");
    }

    private static Pig spawnProbe(ServerLevel level, BlockPos position) {
        Pig probe = new Pig(EntityType.PIG, level);
        probe.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        level.addFreshEntity(probe);
        return probe;
    }

    /**
     * Removes the formation, the probes, and every block the definition required, so one audit cannot
     * leave a structure behind for the next one to trip over.
     */
    private static void clearFormation(ServerLevel level, BlockPos controller, Formation definition, Pig... probes) {
        FormationWorldService.deactivate(level, controller);
        for (Pig probe : probes) probe.discard();
        level.removeBlock(controller, false);
        for (RequiredBlock required : definition.structure())
            level.removeBlock(controller.offset(required.offset()), false);
    }

    private static boolean holdsSource(LivingEntity entity, Identifier source) {
        return entity.getData(MxtAttachments.ABILITY_HOLDER).sources().containsValue(source);
    }

    /**
     * Records the formation context an action was handed so that nesting can be asserted rather than
     * assumed. It is never encoded: the audit builds the action tree in code.
     */
    private static final class FormationContextProbe implements EntityAction {
        private final List<FormationCarrier> carriers = new ArrayList<>();
        private int invocations;

        @Override
        public void execute(@NonNull EntityActionContext context) {
            this.invocations++;
            FormationCarrier.of(context).ifPresent(this.carriers::add);
        }

        @Override
        public @NonNull MapCodec<? extends EntityAction> codec() {
            return MapCodec.unit(this);
        }

        private List<FormationCarrier> carriers() {
            return this.carriers;
        }

        private int invocations() {
            return this.invocations;
        }

        private void clear() {
            this.carriers.clear();
            this.invocations = 0;
        }
    }

    /**
     * The template the audit installs for every template-based test formation: a gold block at the controller
     * and an air entry beside it, since a saved template records its whole bounding box.
     */
    private static CompoundTag singleBlockTemplate() {
        CompoundTag template = new CompoundTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(2));
        size.add(IntTag.valueOf(1));
        size.add(IntTag.valueOf(1));
        template.put("size", size);
        ListTag palette = new ListTag();
        CompoundTag gold = new CompoundTag();
        gold.putString("Name", "minecraft:gold_block");
        palette.add(gold);
        CompoundTag air = new CompoundTag();
        air.putString("Name", "minecraft:air");
        palette.add(air);
        template.put("palette", palette);
        ListTag blocks = new ListTag();
        blocks.add(blockAt(0, 0, 0, 0));
        blocks.add(blockAt(1, 0, 0, 1));
        template.put("blocks", blocks);
        template.put("entities", new ListTag());
        return template;
    }

    private static CompoundTag blockAt(int x, int y, int z, int state) {
        CompoundTag block = new CompoundTag();
        ListTag position = new ListTag();
        position.add(IntTag.valueOf(x));
        position.add(IntTag.valueOf(y));
        position.add(IntTag.valueOf(z));
        block.put("pos", position);
        block.putInt("state", state);
        return block;
    }
}
