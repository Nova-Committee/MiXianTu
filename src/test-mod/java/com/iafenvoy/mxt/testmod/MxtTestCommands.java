package com.iafenvoy.mxt.testmod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.accessor.ResourceLoadingOps;
import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.type.FlightAbilityType;
import com.iafenvoy.mxt.data.ability.type.FlightDisplay;
import com.iafenvoy.mxt.data.action.NoOpAction;
import com.iafenvoy.mxt.data.action.builtin.entity.SetNoGravityAction;
import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.artifact.ArtifactDescription;
import com.iafenvoy.mxt.data.artifact.ArtifactStorageComponent;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraRequirement;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.condition.builtin.entity.AuraElementEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.ElementAttachmentEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.HasElementEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.InSecretRealmEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.InSecretRealmEntityCondition.Role;
import com.iafenvoy.mxt.data.condition.builtin.item.ItemElementCondition;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.iafenvoy.mxt.data.creature.ContractBehavior;
import com.iafenvoy.mxt.data.creature.ContractBehaviors;
import com.iafenvoy.mxt.data.creature.ContractContext;
import com.iafenvoy.mxt.data.creature.ContractTags;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.data.creature.CreatureProfile;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.Costs;
import com.iafenvoy.mxt.data.cost.builtin.AuraCost;
import com.iafenvoy.mxt.data.cost.builtin.ResourceCost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.item.ContractBellComponent;
import com.iafenvoy.mxt.data.item.ContractScrollComponent;
import com.iafenvoy.mxt.data.item.FormationPlateComponent;
import com.iafenvoy.mxt.data.item.SecretRealmTokenComponent;
import com.iafenvoy.mxt.data.item.SpiritBeastComponent;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.event.AbilityUseEvent.Pre;
import com.iafenvoy.mxt.item.block.entity.RiftBlockEntity;
import com.iafenvoy.mxt.recipe.SpiritRecipe;
import com.iafenvoy.mxt.registry.*;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactHold;
import com.iafenvoy.mxt.runtime.artifact.ArtifactHoldService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactHoldService.ClaimResult;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService.RefineResult;
import com.iafenvoy.mxt.runtime.artifact.ArtifactUpkeepService;
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
import com.iafenvoy.mxt.runtime.cultivation.ItemElements;
import com.iafenvoy.mxt.runtime.creature.BoundBeastService;
import com.iafenvoy.mxt.runtime.creature.ContractBehaviorService;
import com.iafenvoy.mxt.runtime.creature.ContractEventBridge;
import com.iafenvoy.mxt.runtime.creature.ContractService;
import com.iafenvoy.mxt.runtime.creature.Contracts;
import com.iafenvoy.mxt.runtime.cultivation.SkillStageService;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueHold;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueService;
import com.iafenvoy.mxt.runtime.damage.DamageCalculationService;
import com.iafenvoy.mxt.runtime.damage.DamageElements;
import com.iafenvoy.mxt.runtime.element.ElementReactionService;
import com.iafenvoy.mxt.runtime.hold.HoldLookup;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.spirit.SpiritBurstService;
import com.iafenvoy.mxt.runtime.rift.RiftColors;
import com.iafenvoy.mxt.runtime.rift.RiftConnections;
import com.iafenvoy.mxt.runtime.rift.RiftConnections.Loop;
import com.iafenvoy.mxt.runtime.rift.RiftMesh;
import com.iafenvoy.mxt.runtime.rift.RiftTeleportService;
import com.iafenvoy.mxt.runtime.wheel.WheelEntryKind;
import com.iafenvoy.mxt.runtime.wheel.WheelService;
import com.iafenvoy.mxt.runtime.wheel.WheelSource;
import com.iafenvoy.mxt.runtime.wheel.WheelSources;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraResult.SourceKind;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.runtime.world.AuraZonePriorityProbe;
import com.iafenvoy.mxt.runtime.world.SecretRealmRecord;
import com.iafenvoy.mxt.runtime.world.SecretRealmRegistry;
import com.iafenvoy.mxt.runtime.world.SecretRealmService;
import com.iafenvoy.mxt.runtime.world.SecretRealmStructurePlacer;
import com.iafenvoy.mxt.screen.information.InformationCollector.InformationEntry;
import com.iafenvoy.mxt.screen.information.InformationManager;
import com.iafenvoy.mxt.screen.information.InformationManager.Side;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.PlayerNames;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.jetbrains.annotations.Nullable;
import static net.minecraft.commands.Commands.literal;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosSlotTypes;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.ISlotType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Development-only {@code /mxt_test} commands that assemble a playable Qingxiao scenario. */
public final class MxtTestCommands {
    private static final Identifier QI = id("qi");
    private static final Identifier QI_REFINING = id("qi_refining");
    private static final Identifier FOUNDATION = id("foundation");
    private static final Identifier CORE_FORMING = id("core_forming");
    private static final Identifier POOR_QUALITY = id("poor");
    private static final Identifier NORMAL_QUALITY = id("normal");
    private static final Identifier EXCELLENT_QUALITY = id("excellent");
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
    private static final Identifier TRIAL_REALM = id("trial_realm");
    private static final Identifier CONTRACT = id("master_servant");
    private static final Identifier OPEN_CONTRACT = id("open_contract");
    // A content mod's order, registered by the probe the way a content mod registers one: known to both sides, and
    // deliberately not on the probe beast's own list.
    private static final Identifier PROBE_ONLY = id("probe_only");
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
    // The flight and storage entries the fixture artifacts name; both are ordinary mxt:ability entries.
    private static final Identifier PROBE_BOUND_FLIGHT = id("bound_flight");
    private static final Identifier PROBE_BOUND_STORAGE = id("bound_storage");
    // An id of the right shape that nothing registers, so the kind check has a negative that is not an artifact.
    private static final Identifier PROBE_ABSENT_ABILITY = id("absent_storage");
    // Wrong on purpose: grants an active ability as mxt:passive.
    // The fixture kept its name from when the check existed; it is now only the other half of the claim conflict.
    private static final Identifier MISDECLARED_ARTIFACT = id("misdeclared_grant_probe");
    // Wrong on purpose: claims the item MISDECLARED_ARTIFACT already claims.
    private static final Identifier CLAIM_CONFLICT_ARTIFACT = id("claim_conflict_probe");
    // Active, so a derived wheel page may list it while it is granted.
    private static final Identifier PROBE_WHEEL_ABILITY = id("firebolt");
    // Declared mxt:modifier, so a derived page must leave it out even while granted.
    private static final Identifier PROBE_WHEEL_PASSIVE = id("artifact_guard");
    // The fixture artifact whose hand page names a flight and a storage ability, and whose own id is no ability.
    private static final Identifier PROBE_WHEEL_TOGGLE = id("bound_sword");
    // More active abilities than one page holds; the overflow leg expects thirteen entries and up.
    private static final List<Identifier> PROBE_WHEEL_MANY = List.of(
            id("firebolt"), id("awaken_divine_sense"), id("expend_test"), id("infuse_true_essence"),
            id("curse_apply_probe"), id("curse_cleanse_probe"), id("curse_query_probe"),
            id("curse_remaining_probe"), id("curse_replace_probe"), id("elemental_probe"),
            id("pos_pulse"), id("pull_pulse"), id("qingxiao_firebolt"), id("sigil_pulse")
    );
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
                .then(literal("contract").executes(context -> probeContract(context.getSource())))
                .then(literal("artifacts").executes(context -> probeArtifactRoster(context.getSource())))
                .then(literal("secret_realm")
                        .executes(context -> probeSecretRealm(context.getSource()))
                        .then(literal("keep").executes(context -> keepSecretRealm(context.getSource())))
                        .then(literal("reopen").executes(context -> reopenSecretRealm(context.getSource()))))
                .then(literal("rift").executes(context -> probeRift(context.getSource())))
                .then(literal("info").executes(context -> showInformation(context.getSource())))
                .then(literal("guide").executes(context -> showGuide(context.getSource()))));
    }

    // Re-checks the behaviours with no other observable entry point: aura zone priority selection, and the
    // minor-stage cut, which nothing else reports as a number.
    private static int verify(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        String auraFailure = verifyAuraPriority(player);
        if (auraFailure != null) {
            source.sendFailure(Component.translatable("command.mxt_test.verify.aura_failed", auraFailure));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt_test.verify.aura_ok"), false);
        String minorStageFailure = verifyMinorStages(player);
        if (minorStageFailure != null) {
            source.sendFailure(Component.translatable("command.mxt_test.verify.minor_stage_failed", minorStageFailure));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt_test.verify.minor_stage_ok"), false);
        String namesFailure = verifyGeneratedNames(player);
        if (namesFailure != null) {
            source.sendFailure(Component.translatable("command.mxt_test.verify.names_failed", namesFailure));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt_test.verify.names_ok"), false);
        String costFailure = verifyCostPayment(player);
        if (costFailure != null) {
            source.sendFailure(Component.translatable("command.mxt_test.verify.cost_failed", costFailure));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt_test.verify.cost_ok"), false);
        return 1;
    }

    // One costs shape for every channel: the same array can name a resource and an aura, both are charged in the
    // value they are measured in, a channel the context does not offer is a refusal rather than a half payment,
    // and nothing at all is taken when any single entry cannot be paid. The account is charged without a payer,
    // which is how a formation pays its upkeep while its owner is offline, and then through the player's own
    // attachment, which is the ordinary entity path.
    private static String verifyCostPayment(ServerPlayer player) {
        RegistryAccess registries = player.level().registryAccess();
        Holder<Resource> probe = require(MxtResourceKeys.RESOURCE, id("trigger_probe"));
        Holder<Resource> qi = require(MxtResourceKeys.RESOURCE, id("qi"));
        ResourceHolderAttachment account = new ResourceHolderAttachment();
        account.set(probe, 100.0D, 0.0D, 10_000.0D, -1L, "probe");
        CostContext context = CostContext.account(account, null, FormulaContext.EMPTY, CostOrigin.SCRIPT);

        CostTransaction.PayResult plain = CostTransaction.pay(decodeCosts(registries,
                "[{\"id\": \"mxt_test:trigger_probe\", \"amount\": 3}]"), context);
        if (!plain.paid()) return "a plain resource cost was refused: " + plain.failure();
        if (!close(account.get(probe), 97.0D))
            return "a 3-point cost left " + account.get(probe) + " instead of 97";

        // Two entries that reach the same value by different routes add up: the aura is charged as the resource
        // it is measured in, which is the only answer that does not depend on the order they were written in.
        account.set(qi, 10.0D, 0.0D, 100.0D, -1L, "probe");
        CostTransaction.PayResult merged = CostTransaction.pay(decodeCosts(registries,
                        "[{\"id\": \"mxt_test:qi\", \"amount\": 1}, {\"type\": \"mxt:aura\", \"aura\": \"mxt_test:qi\", \"amount\": 3}]"),
                context);
        if (!merged.paid()) return "a resource entry plus the aura it names was refused: " + merged.failure();
        if (!close(account.get(qi), 6.0D))
            return "1 + 3 of the same value left " + account.get(qi) + " instead of 6";

        double before = account.get(probe);
        CostTransaction.PayResult refused = CostTransaction.pay(decodeCosts(registries,
                        "[{\"id\": \"mxt_test:trigger_probe\", \"amount\": 1}, {\"id\": \"mxt_test:soul_power\", \"amount\": 9999}]"),
                context);
        if (refused.paid()) return "an array whose second entry is unpayable was paid anyway";
        if (!close(account.get(probe), before))
            return "a refused payment still took " + (before - account.get(probe)) + " off the first entry";

        CostTransaction.PayResult item = CostTransaction.pay(decodeCosts(registries,
                        "[{\"type\": \"mxt:item\", \"items\": [\"minecraft:emerald\"], \"amount\": 1}]"), context);
        if (item.paid() || item.failure() != CostFailure.NO_CHANNEL)
            return "an item cost without a player channel read as " + item.failure();

        CostTransaction.PayResult invalid = CostTransaction.pay(decodeCosts(registries,
                "[{\"id\": \"mxt_test:trigger_probe\", \"amount\": 0}]"), context);
        if (invalid.paid() || invalid.failure() != CostFailure.INVALID_AMOUNT)
            return "a zero amount read as " + invalid.failure();

        ResourceHolderAttachment personal = player.getData(MxtAttachments.RESOURCE_HOLDER);
        double previous = personal.get(probe);
        personal.set(probe, 10.0D, 0.0D, 10_000.0D, -1L, "probe");
        CostTransaction.PayResult onPlayer = CostTransaction.pay(decodeCosts(registries,
                        "[{\"id\": \"mxt_test:trigger_probe\", \"amount\": 2}]"),
                CostContext.of(player, FormulaContext.of(player), CostOrigin.SCRIPT));
        double left = personal.get(probe);
        personal.set(probe, previous, 0.0D, 10_000.0D, -1L, "probe");
        if (!onPlayer.paid()) return "a payer-paid resource cost was refused: " + onPlayer.failure();
        if (!close(left, 8.0D)) return "a payer-paid 2-point cost left " + left + " instead of 8";

        // The two aura-only fields read the old map and the new list, and refuse anything that is not an aura:
        // a resource entry there could never be paid by the pool or the store the field charges.
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        List<Cost> mapped = SpiritRecipe.AURA_CODEC.parse(ops, JsonParser.parseString("{\"mxt_test:qi\": 2}"))
                .getOrThrow(error -> new IllegalArgumentException("The aura map form no longer decodes: " + error));
        if (mapped.size() != 1 || !(mapped.getFirst() instanceof AuraCost mappedCost)
                || !close(mappedCost.amount().evaluate(FormulaContext.EMPTY), 2.0D))
            return "the aura map form decoded as " + mapped;
        if (SpiritRecipe.AURA_CODEC.parse(ops, JsonParser.parseString("[{\"id\": \"mxt_test:qi\", \"amount\": 1}]")).error().isEmpty())
            return "a non-aura entry loaded into an aura-only cost field";

        Holder<Aura> qiAura = require(MxtResourceKeys.AURA, id("qi"));
        Map<Holder<Aura>, Double> auraAmounts = Costs.auras(List.of(new AuraCost(qiAura, new Constant(2.0D))), context);
        if (auraAmounts == null || !close(auraAmounts.getOrDefault(qiAura, 0.0D), 2.0D))
            return "an aura entry evaluated to " + auraAmounts;
        return null;
    }

    private static List<Cost> decodeCosts(RegistryAccess registries, String json) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        return Cost.LIST_CODEC.parse(ops, JsonParser.parseString(json))
                .getOrThrow(error -> new IllegalArgumentException("Invalid cost fixture: " + error));
    }

    // The documented rule: highest priority wins inside a tier, the registry ID breaks ties, and a dimension
    // binding is never outranked by a biome binding.
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

    // Asserts the production ordering helper itself: maximum priority first, ascending registry ID on a tie.
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

    // The qi_refining fixture declares nine minor stages on a 100 requirement, so one segment is 100/9 and the
    // index is the segment the progress falls into; progress banked past the requirement stays on the ninth. The
    // last legs prove minor_stage is registered as a formula variable rather than merely computed in here.
    private static String verifyMinorStages(ServerPlayer player) {
        Holder<RealmStage> qiRefining = require(MxtResourceKeys.REALM_STAGE, QI_REFINING);
        double[][] cuts = {{0.0D, 0.0D}, {11.0D, 0.0D}, {12.0D, 1.0D}, {50.0D, 4.0D},
                {99.0D, 8.0D}, {100.0D, 8.0D}, {150.0D, 8.0D}};
        for (double[] cut : cuts) {
            double index = CultivationService.minorStage(qiRefining.value(), cut[0], FormulaContext.EMPTY);
            if (!close(index, cut[1]))
                return "progress " + cut[0] + " reads as minor stage " + index + " instead of " + cut[1];
        }
        double undefined = CultivationService.minorStage(require(MxtResourceKeys.REALM_STAGE, FOUNDATION).value(),
                50.0D, FormulaContext.EMPTY);
        if (!Double.isNaN(undefined))
            return "a stage without minor_stages reads as " + undefined + " instead of NaN";
        CultivationAttachment spirit = player.getData(MxtAttachments.CULTIVATION);
        if (!CultivationService.setRealm(spirit, QI_REFINING)) return "the realm cache could not resolve " + QI_REFINING;
        spirit.setCultivationProgress(requireProfile(QI), 80.0D);
        FormulaContext context = ResourceService.formulaContext(player, require(MxtResourceKeys.RESOURCE, QI), FormulaContext.of(player));
        double read = context.value("minor_stage");
        if (!close(read, 7.0D)) return "the minor_stage variable reads " + read + " at 80/100 instead of 7";
        spirit.setRealmStages(Map.of());
        double mortal = context.value("minor_stage");
        if (!Double.isNaN(mortal)) return "a mortal reads minor_stage as " + mortal + " instead of NaN";
        spirit.setRealmStage(qiRefining);
        spirit.setCultivationProgress(requireProfile(QI), 80.0D);
        return null;
    }

    // The generated text of a definition comes from the id it was decoded as, which only the loader knows: an
    // omitted name reads back as its own key, a counted minor_stages names every stage after that key, and a
    // written component wins over both. The expected strings are the fixture files' own words, read on a server
    // where no language file is loaded, so a translation key stays a key. The display path is asserted next to
    // the field, because a definition that carries its own text must be shown with it.
    private static String verifyGeneratedNames(ServerPlayer player) {
        Holder<RealmStage> qiRefining = require(MxtResourceKeys.REALM_STAGE, QI_REFINING);
        if (qiRefining.value().minorStages().size() != 9)
            return "the counted qi_refining reads " + qiRefining.value().minorStages().size() + " minor stages instead of 9";
        String counted = qiRefining.value().minorStages().getFirst().getString();
        if (!counted.equals("realm_stage.mxt.mxt_test.qi_refining.minor_stage.0"))
            return "the counted first minor stage reads " + counted;
        String last = qiRefining.value().minorStages().getLast().getString();
        if (!last.equals("realm_stage.mxt.mxt_test.qi_refining.minor_stage.8"))
            return "the counted last minor stage reads " + last;
        List<Component> written = require(MxtResourceKeys.REALM_STAGE, CORE_FORMING).value().minorStages();
        if (written.size() != 3 || !written.getFirst().getString().equals("realm_stage.mxt.mxt_test.core_forming.minor_stage.early"))
            return "the written minor stages read " + written;
        String poorName = require(MxtResourceKeys.ITEM_QUALITY, POOR_QUALITY).value().name().getString();
        if (!poorName.equals("quality.mxt.mxt_test.poor")) return "the omitted name reads " + poorName;
        String poorDescription = require(MxtResourceKeys.ITEM_QUALITY, POOR_QUALITY).value().description().getString();
        if (!poorDescription.equals("quality.mxt.mxt_test.poor.description"))
            return "the omitted description reads " + poorDescription;
        // The fixture writes this one as its own key, so on a server without language files the field reads back as
        // that key; that a written field beats the generated one is asserted on the element fixture's literal below.
        String excellentName = require(MxtResourceKeys.ITEM_QUALITY, EXCELLENT_QUALITY).value().name().getString();
        if (!excellentName.equals("quality.mxt.mxt_test.excellent")) return "the written name reads " + excellentName;
        String normalDescription = require(MxtResourceKeys.ITEM_QUALITY, NORMAL_QUALITY).value().description().getString();
        if (!normalDescription.equals("quality.mxt.mxt_test.normal.description"))
            return "the written description reads " + normalDescription;
        // The same pair on two of the definitions that gained the fields in this round: an aura writes neither,
        // an element writes both.
        Holder<Aura> qi = require(MxtResourceKeys.AURA, QI);
        if (!qi.value().name().getString().equals("aura.mxt.mxt_test.qi"))
            return "the omitted aura name reads " + qi.value().name().getString();
        if (!qi.value().description().getString().equals("aura.mxt.mxt_test.qi.description"))
            return "the omitted aura description reads " + qi.value().description().getString();
        Holder<Element> metal = require(MxtResourceKeys.ELEMENT, PROBE_METAL_ELEMENT);
        if (!metal.value().name().getString().equals("Metal probe"))
            return "the written element name reads " + metal.value().name().getString();
        if (!metal.value().description().getString().equals("The fixture's metal element."))
            return "the written element description reads " + metal.value().description().getString();
        if (!DefinitionText.name(qi).getString().equals(qi.value().name().getString())
                || !DefinitionText.name(metal, "element").getString().equals(metal.value().name().getString()))
            return "the display path does not read the definition's own text";
        // The loader is the only thing that puts the id on the ops, so decoding a definition here proves the
        // accessor is really mixed into RegistryOps; the fixtures above prove the loader itself sets it.
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, player.level().registryAccess());
        if (!(ops instanceof ResourceLoadingOps loading)) return "the RegistryOps accessor is not applied";
        ItemQuality decoded;
        loading.mxt$setKey(ResourceKey.create(MxtResourceKeys.ITEM_QUALITY, id("probe_quality")));
        try {
            decoded = ItemQuality.DIRECT_CODEC.parse(ops, new JsonObject()).getOrThrow();
        } catch (RuntimeException exception) {
            return "decoding a definition that writes neither text field failed: " + exception.getMessage();
        } finally {
            loading.mxt$setKey(null);
        }
        if (!decoded.name().getString().equals("quality.mxt.mxt_test.probe_quality"))
            return "the decoded name reads " + decoded.name().getString();
        return decoded.description().getString().equals("quality.mxt.mxt_test.probe_quality.description") ? null
                : "the decoded description reads " + decoded.description().getString();
    }

    // Drives both damage pipeline layers against throwaway probes whose element edges are fixture-known: fire
    // overcomes water x1.5 and water is adapted to fire x0.5, so 10 reads as outgoing 15.0 / adapted 7.5, read
    // back through the same calls the runtime uses. Legs and their arithmetic:
    //   mastery  - sword_manual stage 1 is x1.1 and stage 2 x1.25, so 4 x 1.25 = 3.75 lost, and the value must
    //              also reach the context a real cast dispatches with (driven through AbilityService);
    //   foreign  - a plain mob attack never shaped by the pipeline: only adaptation may touch it, 4 x 0.5 = 2;
    //   physique - probe_body multiplies dealt x1.5 and taken x0.5, so 10 * 1.5 * 1.5 = 22.5 then
    //              22.5 * 0.5 * 0.5 = 5.625 (the taken side is a formula, so this leg covers that path too);
    //   affinity - the fire root's element_ability_modifier 1.1 is a shaping factor: 10 * 1.1 * 1.5 = 16.5,
    //              then 16.5 * 0.5 = 8.25, and a real cast must read the same value off its context;
    //   unowned  - nobody credited reads no element relation, but the cast's own mastery stays: 4 * 2 = 8,
    //              separating 8.0 (correct) from 12.0 (relation read anyway) and 4.0 (mastery dropped);
    //   no_bonus - a mxt:no_bonus type travels untouched through both layers (6.0 handed, 6.0 lost), which also
    //              proves the shipped default tag holds the void;
    //   self_conflict - a water sword in a fire body whose root conflicts with water: 4 x 0.5 = 2 wielded vs 4
    //              unarmed;
    //   origin   - only an element the strike declared rubs off: the same fire strike leaves 4 read off
    //              minecraft:magic and 0 read off the striker's roots (claim leg 4 versus origin leg 0);
    //   ward     - the carried artifact halves the buildup: 4 left on a bare body, 2 on one holding the ward,
    //              while the strike's own damage is untouched;
    //   claim    - a type's claim may price the buildup: fire's own 4.0 from minecraft:magic, but 2.0 from
    //              minecraft:lava.
    private static int probeDamage(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos origin = source.getPlayer() != null
                ? source.getPlayer().blockPosition()
                : level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.ZERO);
        LivingEntity attacker = spawnProbe(level, origin.above(), PROBE_FIRE_ROOT);
        LivingEntity defender = spawnProbe(level, origin.above(2), PROBE_WATER_ROOT);
        // The mastery leg needs a defender of its own: the first hit leaves the target briefly invulnerable,
        // and a second strike in that window is refused rather than measured.
        LivingEntity masteryDefender = spawnProbe(level, origin.above(3), PROBE_WATER_ROOT);
        LivingEntity foreignDefender = spawnProbe(level, origin.above(4), PROBE_WATER_ROOT);
        LivingEntity bodyAttacker = spawnProbe(level, origin.above(5), PROBE_FIRE_ROOT);
        LivingEntity bodyDefender = spawnProbe(level, origin.above(6), PROBE_WATER_ROOT);
        LivingEntity affinityAttacker = spawnProbe(level, origin.above(7), PROBE_FIRE_ROOT);
        LivingEntity affinityDefender = spawnProbe(level, origin.above(8), PROBE_WATER_ROOT);
        LivingEntity unownedDefender = spawnProbe(level, origin.above(9), PROBE_WATER_ROOT);
        LivingEntity taggedAttacker = spawnProbe(level, origin.above(10), PROBE_FIRE_ROOT);
        LivingEntity taggedDefender = spawnProbe(level, origin.above(11), PROBE_WATER_ROOT);
        LivingEntity conflictAttacker = spawnProbe(level, origin.above(12), PROBE_ANTI_WATER_ROOT);
        LivingEntity conflictDefender = spawnProbe(level, origin.above(13), null);
        LivingEntity claimedVictim = spawnProbe(level, origin.above(14), null);
        LivingEntity rootVictim = spawnProbe(level, origin.above(15), null);
        LivingEntity wardVictim = spawnProbe(level, origin.above(16), null);
        LivingEntity lavaVictim = spawnProbe(level, origin.above(17), null);
        try {
            if (attacker == null || defender == null || masteryDefender == null || foreignDefender == null
                    || bodyAttacker == null || bodyDefender == null || affinityAttacker == null || affinityDefender == null
                    || unownedDefender == null || taggedAttacker == null || taggedDefender == null
                    || conflictAttacker == null || conflictDefender == null || claimedVictim == null
                    || rootVictim == null || wardVictim == null || lavaVictim == null) {
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
            double entry = SkillStageService.damageMultiplier(attacker, HolderHelper.id(ability));
            spirit.setTechniqueStage(technique, require(MxtResourceKeys.SKILL_STAGE, PROBE_TECHNIQUE_STAGE));
            double advanced = SkillStageService.damageMultiplier(attacker, HolderHelper.id(ability));
            float masteryBefore = masteryDefender.getHealth();
            DamageCalculationService.deal(attacker, masteryDefender, 4.0D, Optional.empty(),
                    context.with(DamageCalculationService.DAMAGE_MULTIPLIER, advanced));
            double masteryLost = masteryBefore - masteryDefender.getHealth();
            // The value must reach the context a real cast dispatches with, not merely exist here: this drives
            // AbilityService and reads the formula value off the event it posts before any action runs.
            double[] dispatched = {Double.NaN};
            Consumer<Pre> listener = event ->
                    dispatched[0] = event.context().explicit(DamageCalculationService.DAMAGE_MULTIPLIER);
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                AbilityService.useCarried(ability, attacker,
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

            // What makes a manual is the stack's own component: a jade slip out of the creative menu teaches
            // nothing, while the carrier the mod generates for a technique teaches exactly that technique - using
            // the item the declaration names when it names one.
            ItemStack blankSlip = new ItemStack(MxtItems.CULTIVATION_JADE_SLIP.get());
            Holder<Technique> declaredTechnique = require(MxtResourceKeys.TECHNIQUE, TECHNIQUE);
            ItemStack generated = ItemBindingService.techniqueCarrier(level.registryAccess(), declaredTechnique);
            Holder<Technique> customTechnique = require(MxtResourceKeys.TECHNIQUE, id("azure_water_manual"));
            ItemStack customCarrier = ItemBindingService.techniqueCarrier(level.registryAccess(), customTechnique);
            boolean blankless = ItemBindingService.technique(level.registryAccess(), blankSlip).isEmpty();
            boolean carrier = generated.is(MxtItems.CULTIVATION_JADE_SLIP.get())
                    && customCarrier.is(MxtTestTechniqueItems.AZURE_WATER_MANUAL.get())
                    && blankless
                    && ItemBindingService.technique(level.registryAccess(), generated)
                    .filter(binding -> HolderHelper.id(binding.technique()).equals(TECHNIQUE) && binding.learnTime() == 40)
                    .isPresent()
                    && ItemBindingService.technique(level.registryAccess(), customCarrier)
                    .filter(binding -> binding.holdAnimation() == ItemUseAnimation.BRUSH)
                    .isPresent()
                    && HoldLookup.hold(generated) instanceof TechniqueHold
                    && HoldLookup.hold(blankSlip) == null;
            source.sendSuccess(() -> Component.literal("technique probe: blank_teaches=" + !blankless
                    + " generated=" + generated.getItem() + " custom=" + customCarrier.getItem()
                    + (carrier ? " OK" : " MISMATCH")), false);

            // A source the pipeline never saw must still be reduced: this is a plain vanilla mob attack, so only
            // the defender's adaptation may touch it - 4 x 0.5 = 2 - never the attacker's edge.
            float foreignBefore = foreignDefender.getHealth();
            foreignDefender.hurtServer(level, level.damageSources().mobAttack(attacker), 4.0F);
            double foreignLost = foreignBefore - foreignDefender.getHealth();
            boolean foreign = close(foreignLost, 2.0D);
            source.sendSuccess(() -> Component.literal("damage probe: foreign health_lost=" + foreignLost
                    + (foreign ? " OK" : " MISMATCH")), false);

            // The physique half, granted to both sides (dealt x1.5, taken x0.5):
            // 10 * 1.5 (fire overcomes water) * 1.5 (dealt) = 22.5, then 22.5 * 0.5 (water adapted to fire)
            // * 0.5 (taken) = 5.625. The taken multiplier is a formula, evaluated against its own holder.
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

            // The spirit root's affinity is a shaping factor now, not just a value a formula must remember:
            // 10 * 1.1 (the fire root's element_ability_modifier) * 1.5 = 16.5, then 16.5 * 0.5 = 8.25 once the
            // water body answers. The second half drives a real cast to prove the pipeline's number and the one
            // a pack could read off the dispatched context are the same.
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
                AbilityService.useCarried(affinityAbility, affinityAttacker,
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

            // Nobody credited reads no element relation even when the declared type would lend it one:
            // minecraft:magic is fire-claimed in the test package, so that relation exists and must not be read.
            // The cast's own factors stay, hence a mastery of 2.0 - that separates 8.0 (correct: 4 * 2, relation
            // dropped) from 12.0 (relation read anyway) and 4.0 (mastery dropped too), which 1.0 could not.
            Holder<DamageType> magicDamage = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(DamageTypes.MAGIC);
            Set<Holder<Element>> declared = DamageElements.of(level.registryAccess(), magicDamage);
            FormulaContext unownedContext = FormulaContext.of(attacker)
                    .with(DamageCalculationService.DAMAGE_MULTIPLIER, 2.0D);
            double unowned = DamageCalculationService.outgoing(null, unownedDefender, 4.0D, unownedContext, declared);
            float unownedBefore = unownedDefender.getHealth();
            DamageCalculationService.deal(null, unownedDefender, 4.0D, Optional.of(magicDamage), unownedContext);
            double unownedLost = unownedBefore - unownedDefender.getHealth();
            boolean unattributed = close(unowned, 8.0D) && close(unownedLost, 4.0D);
            source.sendSuccess(() -> Component.literal("damage probe: unattributed outgoing=" + unowned
                    + " health_lost=" + unownedLost + (unattributed ? " OK" : " MISMATCH")), false);

            // A type the pack exempted travels as the number it was handed. Both bodies carry the same physique
            // (dealt x1.5, taken x0.5) the untagged legs measure with, so the readings are far apart: 6.0 means
            // both layers stood aside, 9.0 only the reduction did, 3.0 only the shaping did, 4.5 neither. The
            // same line asserts the void really is in the shipped default tag, which a misplaced file would hide.
            Holder<DamageType> voidDamage = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(DamageTypes.FELL_OUT_OF_WORLD);
            boolean tagged = DamageCalculationService.bypasses(level.damageSources().fellOutOfWorld())
                    && !DamageCalculationService.bypasses(level.damageSources().magic());
            boolean taggedBodies = grantProbePhysique(taggedAttacker, PROBE_PHYSIQUE)
                    && grantProbePhysique(taggedDefender, PROBE_PHYSIQUE);
            FormulaContext taggedContext = FormulaContext.of(taggedAttacker);
            float taggedBefore = taggedDefender.getHealth();
            double handed = DamageCalculationService.deal(taggedAttacker, taggedDefender, 6.0D,
                    Optional.of(voidDamage), taggedContext);
            double taggedLost = taggedBefore - taggedDefender.getHealth();
            boolean exempt = tagged && taggedBodies && close(handed, 6.0D) && close(taggedLost, 6.0D);
            source.sendSuccess(() -> Component.literal("damage probe: no_bonus tagged=" + tagged + " dealt=" + handed
                    + " health_lost=" + taggedLost + (exempt ? " OK" : " MISMATCH")), false);

            // A weapon the striker's own roots conflict with weakens everything they deal: the fixture root
            // (anti_water_root) is a fire body listing water in conflicting_elements, the golden sword is
            // declared water, and mxt_test:water prices it at 0.5 - so the same 4 reads as 2 in that hand and as
            // 4 in an empty one. The victim has no roots and fire meets nothing, so no relation confuses this.
            conflictAttacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_SWORD));
            FormulaContext conflictContext = FormulaContext.of(conflictAttacker);
            double conflicting = DamageCalculationService.outgoing(conflictAttacker, conflictDefender, 4.0D, conflictContext);
            conflictAttacker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            double unarmed = DamageCalculationService.outgoing(conflictAttacker, conflictDefender, 4.0D, conflictContext);
            conflictAttacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_SWORD));
            float conflictBefore = conflictDefender.getHealth();
            DamageCalculationService.deal(conflictAttacker, conflictDefender, 4.0D, Optional.empty(), conflictContext);
            double conflictLost = conflictBefore - conflictDefender.getHealth();
            boolean selfConflict = close(conflicting, 2.0D) && close(unarmed, 4.0D) && close(conflictLost, 2.0D);
            source.sendSuccess(() -> Component.literal("damage probe: self_conflict wielded=" + conflicting
                    + " unarmed=" + unarmed + " health_lost=" + conflictLost
                    + (selfConflict ? " OK" : " MISMATCH")), false);

            // Only an element the strike declared rubs off. Both victims take a fire strike of 10 with no roots
            // of their own, so neither has an element edge; the difference is the source: the first travels as
            // minecraft:magic, which the test package has fire claim, so fire's own damage_attachment of 4 is
            // left on the target, while the second declares nothing, reads fire off the striker's roots, and
            // leaves nothing behind.
            float claimedBefore = claimedVictim.getHealth();
            Holder<Element> probeFire = require(MxtResourceKeys.ELEMENT, PROBE_FIRE_ELEMENT);
            DamageCalculationService.deal(attacker, claimedVictim, 10.0D, Optional.of(magicDamage), FormulaContext.of(attacker));
            double claimedLeft = ElementReactionService.amount(claimedVictim, probeFire);
            double claimedLost = claimedBefore - claimedVictim.getHealth();
            float rootBefore = rootVictim.getHealth();
            DamageCalculationService.deal(attacker, rootVictim, 10.0D, Optional.empty(), FormulaContext.of(attacker));
            double rootLeft = ElementReactionService.amount(rootVictim, probeFire);
            double rootLost = rootBefore - rootVictim.getHealth();
            boolean strikeOrigin = close(claimedLeft, 4.0D) && close(claimedLost, 10.0D)
                    && close(rootLeft, 0.0D) && close(rootLost, 10.0D);
            source.sendSuccess(() -> Component.literal("damage probe: origin claimed_left=" + claimedLeft
                    + " root_left=" + rootLeft + " health_lost=" + claimedLost + "/" + rootLost
                    + (strikeOrigin ? " OK" : " MISMATCH")), false);

            // What the victim carries decides how much of that element gets through: the ward artifact prices
            // itself at 0.5, so the same claimed fire strike leaves 2 where a bare body kept 4 - and the
            // reduction is the item's, so the strike's own damage is untouched.
            wardVictim.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PRISMARINE_SHARD));
            double wardMultiplier = DamageCalculationService.attachmentMultiplier(wardVictim);
            float wardBefore = wardVictim.getHealth();
            DamageCalculationService.deal(attacker, wardVictim, 10.0D, Optional.of(magicDamage), FormulaContext.of(attacker));
            double wardLeft = ElementReactionService.amount(wardVictim, probeFire);
            double wardLost = wardBefore - wardVictim.getHealth();
            boolean warded = close(DamageCalculationService.attachmentMultiplier(claimedVictim), 1.0D)
                    && close(wardMultiplier, 0.5D) && close(wardLeft, 2.0D) && close(wardLost, 10.0D);
            source.sendSuccess(() -> Component.literal("damage probe: attachment_ward multiplier=" + wardMultiplier
                    + " left=" + wardLeft + " health_lost=" + wardLost
                    + (warded ? " OK" : " MISMATCH")), false);

            // A type's claim may price the buildup itself: the fixture element claims minecraft:magic plainly,
            // so fire's own 4.0 answers, and minecraft:lava with a 2.0 of its own - so one element read off two
            // types leaves two amounts, which is what makes claimed types usable as groups.
            Holder<DamageType> lavaDamage = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(DamageTypes.LAVA);
            float lavaBefore = lavaVictim.getHealth();
            DamageCalculationService.deal(attacker, lavaVictim, 10.0D, Optional.of(lavaDamage), FormulaContext.of(attacker));
            double lavaLeft = ElementReactionService.amount(lavaVictim, probeFire);
            double lavaLost = lavaBefore - lavaVictim.getHealth();
            boolean claimAmount = close(lavaLeft, 2.0D) && close(lavaLost, 10.0D);
            source.sendSuccess(() -> Component.literal("damage probe: claim amount lava_left=" + lavaLeft
                    + " magic_left=" + claimedLeft + " health_lost=" + lavaLost
                    + (claimAmount ? " OK" : " MISMATCH")), false);

            if (elements && mastery && foreign && physique && affinity && unattributed && exempt && selfConflict
                    && strikeOrigin && warded && claimAmount) {
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
            if (unownedDefender != null) unownedDefender.discard();
            if (taggedAttacker != null) taggedAttacker.discard();
            if (taggedDefender != null) taggedDefender.discard();
            if (conflictAttacker != null) conflictAttacker.discard();
            if (conflictDefender != null) conflictDefender.discard();
            if (claimedVictim != null) claimedVictim.discard();
            if (rootVictim != null) rootVictim.discard();
            if (wardVictim != null) wardVictim.discard();
            if (lavaVictim != null) lavaVictim.discard();
        }
    }

    // Grants through the authoritative service a data pack action would take; false means the definition was
    // refused, and the leg that asked for it fails rather than measuring a body that never got it.
    private static boolean grantProbePhysique(LivingEntity entity, Identifier id) {
        return CultivationIdentityService.grantPhysique(entity, id, require(MxtResourceKeys.PHYSIQUE, id).value(),
                FormulaContext.of(entity)).changed();
    }

    // Drives the cultivation identity surface end to end: the script API, the rarity a definition now reports,
    // and the reading of a physique written as if it were elemental. The three guarantees pinned: a switched-off
    // root or physique is still held while contributing nothing (so the reads answer both questions separately,
    // and a switched-off physique must not scale anything); a rarity is a field with a reader; and a field
    // belonging to another registry is ignored while the definition still loads. Every leg is a number or a
    // boolean rather than a log line, so the probe fails loudly when a documented guarantee stops holding.
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

            // A rarity is content's own word: read back as written, falling back to itself when no language file
            // names it.
            Holder<Physique> physique = require(MxtResourceKeys.PHYSIQUE, PROBE_PHYSIQUE);
            boolean rarity = physique.value().rarity().equals("probe") && DefinitionText.rarity("probe").getString().equals("probe") && require(MxtResourceKeys.SPIRIT_ROOT, PROBE_FIRE_ROOT).value().rarity().equals("uncommon");
            source.sendSuccess(() -> Component.literal("identity probe: rarity=" + rarity
                    + (rarity ? " OK" : " MISMATCH")), false);

            // A physique that also names an element, an element relation or a spirit-root field keeps loading and
            // those keys are ignored - the record codec's own reading of a key it was not told about. A written
            // number is still checked while the pack loads, so a broken multiplier is not.
            boolean foreignIgnored = ignoresForeignFields();
            boolean negative = Physique.DIRECT_CODEC.parse(JsonOps.INSTANCE, single("damage_dealt_multiplier", -1.0D)).isError();
            boolean plain = Physique.DIRECT_CODEC.parse(JsonOps.INSTANCE, single("rarity", "probe")).result().isPresent();
            boolean loose = foreignIgnored && negative && plain;
            source.sendSuccess(() -> Component.literal("identity probe: foreign_fields_ignored=" + foreignIgnored
                    + " negative_refused=" + negative + " plain_accepted=" + plain + (loose ? " OK" : " MISMATCH")), false);

            // A burst is a resource spend plus a projectile, and neither half asks for a player: the wheel is only
            // how a player requests one. The probe stands at the realm the aura's own use gate names.
            ResourceHolderAttachment burstResources = probe.getData(MxtAttachments.RESOURCE_HOLDER);
            ensureResource(probe, burstResources, require(MxtResourceKeys.RESOURCE, SPIRIT_POWER), 20.0D);
            Holder<Aura> burstAura = require(MxtResourceKeys.AURA, SPIRIT_POWER);
            Holder<Resource> burstResource = require(MxtResourceKeys.RESOURCE, SPIRIT_POWER);
            long burstAt = level.getGameTime();
            boolean burst = CultivationService.setRealm(probe.getData(MxtAttachments.CULTIVATION), SPIRIT_POWER_REFINING)
                    && SpiritBurstService.fireOnce(probe, SPIRIT_POWER)
                    && close(burstResources.get(burstResource), 10.0D)
                    && probe.getData(MxtAttachments.SPIRIT_BURST_COOLDOWNS).isOnCooldown(burstAura, burstAt)
                    && !SpiritBurstService.fireOnce(probe, SPIRIT_POWER);
            source.sendSuccess(() -> Component.literal("identity probe: mob_burst=" + burst
                    + " spent=" + burstResources.get(burstResource) + (burst ? " OK" : " MISMATCH")), false);

            if (identity && rarity && loose && burst) {
                source.sendSuccess(() -> Component.literal("identity probe: OK"), false);
                return 1;
            }
            source.sendFailure(Component.literal("identity probe: MISMATCH"));
            return 0;
        } finally {
            if (probe != null) probe.discard();
        }
    }

    // Drives the contract interfaces on a creature that implements them itself, which is the extension point a
    // content mod uses. Every leg here is synchronous: the recall landing and the following tick happen between
    // ticks inside the event bridge, so this leg asserts the state the bridge reads rather than the movement it
    // performs with it.
    private static int probeContract(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        ServerLevel level = source.getLevel();
        MinecraftServer server = source.getServer();
        ProbeBeast.reset();
        // The index outlives the probe entities a previous run discarded, so the leg starts from a clean list.
        BoundBeastService.clear(server, player.getUUID());
        List<Mob> spawned = new ArrayList<>();
        ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
        Holder<Resource> qi = require(MxtResourceKeys.RESOURCE, QI);
        double previousQi = resources.get(qi);
        try {
            Holder<ContractType> tagged = require(MxtResourceKeys.CONTRACT_TYPE, CONTRACT);
            Holder<ContractType> open = require(MxtResourceKeys.CONTRACT_TYPE, OPEN_CONTRACT);

            // Eligibility is code: a vanilla creature implements nothing, so no record ever reaches it, while the
            // contract type's own entity type tag narrows the list among the creatures that do implement it.
            Wolf wolf = EntityType.WOLF.create(level, EntitySpawnReason.COMMAND);
            if (wolf == null) {
                source.sendFailure(Component.literal("contract probe: could not create the vanilla creature"));
                return 0;
            }
            spawned.add(wolf);
            boolean vanillaRefused = ContractService.bind(tagged, player, wolf, false).failure()
                    == ContractService.Failure.NOT_CONTRACTABLE;
            boolean tagNarrows = !ContractTags.accepts(context(wolf, player, tagged))
                    && ContractTags.accepts(context(wolf, player, open));
            boolean eligibility = vanillaRefused && tagNarrows;
            source.sendSuccess(() -> Component.literal("contract probe: vanilla_refused=" + vanillaRefused
                    + " tag_narrows=" + tagNarrows + (eligibility ? " OK" : " MISMATCH")), false);

            // Price before the record: a beast that cannot pay is not bound, nothing is charged and no row is
            // written; paying leaves exactly the price behind and a record that names the owner.
            resources.set(qi, 3.0D, 0.0D, 10_000.0D, -1L, "probe");
            ProbeBeast beast = spawnProbeBeast(level, player.blockPosition().offset(6, 0, 0));
            spawned.add(beast);
            boolean priceRefused = ContractService.bind(tagged, player, beast, false).failure()
                    == ContractService.Failure.INSUFFICIENT_COST
                    && !beast.getData(MxtAttachments.CONTRACT).bound()
                    && close(resources.get(qi), 3.0D)
                    && BoundBeastService.of(server, player.getUUID()).isEmpty();
            resources.set(qi, 5.0D, 0.0D, 10_000.0D, -1L, "probe");
            boolean bound = ContractService.bind(tagged, player, beast, false).changed()
                    && close(resources.get(qi), 0.0D)
                    && Contracts.ownerOf(beast).filter(player.getUUID()::equals).isPresent()
                    && beast.getOwner() == player
                    && BoundBeastService.of(server, player.getUUID()).size() == 1;
            ItemStack probeCore = beast.getData(MxtAttachments.CREATURE_SPIRIT).innerCore();
            // A profile's spawn action is one action, and the profile is written once, so it runs once: the probe
            // beast is left weightless by its own definition rather than by probe code.
            CreatureProfile beastProfile = MxtDatapackRegistries.get(MxtResourceKeys.CREATURE_PROFILE, id("probe_beast")).orElse(null);
            boolean spawnAction = beastProfile != null && beastProfile.spawnAction() instanceof SetNoGravityAction
                    && beast.isNoGravity();
            // Declaring no action writes the no-op in, which is the same as running nothing at all.
            CreatureProfile wolfProfile = MxtDatapackRegistries.get(MxtResourceKeys.CREATURE_PROFILE, id("spirit_wolf")).orElse(null);
            boolean idleAction = wolfProfile != null && wolfProfile.spawnAction() == NoOpAction.INSTANCE;
            boolean profiled = beast.getData(MxtAttachments.CREATURE_SPIRIT).profile().isPresent()
                    && close(beast.getData(MxtAttachments.CREATURE_SPIRIT).intelligence(), 20.0D)
                    && probeCore.is(Items.AMETHYST_SHARD) && probeCore.getCount() == 2 && spawnAction && idleAction;
            boolean twice = ContractService.bind(tagged, player, beast, false).failure()
                    == ContractService.Failure.ALREADY_BOUND;
            boolean record = priceRefused && bound && profiled && twice;
            source.sendSuccess(() -> Component.literal("contract probe: price_refused=" + priceRefused
                    + " bound=" + bound + " profiled=" + profiled + " twice=" + twice
                    + (record ? " OK" : " MISMATCH")), false);

            // The owner's list is what a limit counts, and releasing frees the slot it held.
            ProbeBeast first = spawnProbeBeast(level, player.blockPosition().offset(-6, 0, 0));
            ProbeBeast second = spawnProbeBeast(level, player.blockPosition().offset(-9, 0, 0));
            spawned.add(first);
            spawned.add(second);
            boolean firstBound = ContractService.bind(open, player, first, false).changed();
            boolean limitHit = ContractService.bind(open, player, second, false).failure()
                    == ContractService.Failure.LIMIT_REACHED;
            // Ending a contract is not the same act as forgetting who owns the creature, and the owner lives on
            // the creature, so a released probe still answers the player it was signed with.
            boolean freed = ContractService.release(first, player.getUUID(), false).changed()
                    && !first.getData(MxtAttachments.CONTRACT).bound()
                    && Contracts.ownerOf(first).filter(player.getUUID()::equals).isPresent()
                    && ContractService.bind(open, player, second, false).changed();
            boolean limit = firstBound && limitHit && freed;
            source.sendSuccess(() -> Component.literal("contract probe: first_bound=" + firstBound
                    + " limit_hit=" + limitHit + " freed=" + freed + (limit ? " OK" : " MISMATCH")), false);

            // The latch and its stamp: the bell sets it, the type's cooldown gates the next one from the stamp
            // rather than from a countdown, and force is the operator's bypass of the wait alone.
            long now = level.getGameTime();
            boolean latch = ContractService.requestRecall(beast, player.getUUID(), false).changed()
                    && beast.getData(MxtAttachments.CONTRACT).recalled()
                    && beast.getData(MxtAttachments.CONTRACT).recallAt() == now;
            ContractService.completeRecall(beast);
            boolean cooling = ContractService.requestRecall(beast, player.getUUID(), false).failure()
                    == ContractService.Failure.RECALL_COOLDOWN;
            boolean forced = ContractService.requestRecall(beast, player.getUUID(), true).changed();
            ContractService.completeRecall(beast);
            // The interface default is the movement the framework used to hardcode, asked through the same
            // context the bridge builds.
            beast.setPos(player.getX() + 20.0D, player.getY(), player.getZ());
            Contracts.operations(beast).orElseThrow().recall(context(beast, player, tagged));
            boolean landed = beast.distanceToSqr(player) < 1.0D;
            boolean recall = latch && cooling && forced && landed;
            source.sendSuccess(() -> Component.literal("contract probe: latch=" + latch + " cooling=" + cooling
                    + " forced=" + forced + " landed=" + landed + (recall ? " OK" : " MISMATCH")), false);

            // The bell names one beast and carries that creature's own answer about the orders it takes, which is
            // what lets the wheel be drawn without resolving the creature. A creature that is not a bound beast of
            // the holder's is never named, however the bell is used.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MxtItems.BEAST_TAMING_BELL.get()));
            ItemStack bell = player.getMainHandItem();
            boolean tuned = bell.getItem().interactLivingEntity(bell, player, beast, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS
                    && bell.get(MxtDataComponents.CONTRACT_BELL) instanceof ContractBellComponent component
                    && component.beast().equals(beast.getUUID())
                    && component.behaviors().contains(ContractBehaviors.WANDER.id());
            boolean wolfRefused = bell.getItem().interactLivingEntity(bell, player, wolf, InteractionHand.MAIN_HAND) == InteractionResult.FAIL;
            boolean bellLeg = tuned && wolfRefused;
            source.sendSuccess(() -> Component.literal("contract probe: bell_tuned=" + tuned + " wolf_refused=" + wolfRefused
                    + (bellLeg ? " OK" : " MISMATCH")), false);

            // The wheel is the owner's input: it sends the order, the creature is asked, and the order that stays in
            // force lands on the creature's own record. A creature that refuses is left with what it had.
            boolean ordered = WheelService.trigger(player, WheelSource.CONTRACT, WheelEntryKind.BEHAVIOR,
                    ContractBehaviors.WANDER.id())
                    && beast.getData(MxtAttachments.CONTRACT).behavior().equals(ContractBehaviors.WANDER);
            ProbeBeast.refuseBehavior(true);
            boolean refusedOrder = !WheelService.trigger(player, WheelSource.CONTRACT, WheelEntryKind.BEHAVIOR,
                    ContractBehaviors.STAY.id())
                    && beast.getData(MxtAttachments.CONTRACT).behavior().equals(ContractBehaviors.WANDER);
            ProbeBeast.refuseBehavior(false);
            // An order the creature does not take is refused before the creature is even asked, which is what the
            // registered-but-not-offered probe order shows.
            ContractBehaviors.register(new ContractBehavior(PROBE_ONLY, false));
            boolean unsupported = ContractBehaviorService.request(beast, player.getUUID(),
                    ContractBehaviors.byId(PROBE_ONLY).orElseThrow(), false).failure()
                    == ContractService.Failure.UNSUPPORTED_BEHAVIOR;
            boolean input = ordered && refusedOrder && unsupported;
            source.sendSuccess(() -> Component.literal("contract probe: ordered=" + ordered + " refused=" + refusedOrder
                    + " unsupported=" + unsupported + (input ? " OK" : " MISMATCH")), false);

            // A recall is an order that happens once: it sets the latch and leaves the order in force alone. The
            // framework's own orders are dispatched from there, so a strolling beast that has been left behind is
            // brought back to its owner on the tick the bridge drives.
            boolean momentary = ContractBehaviorService.request(beast, player.getUUID(), ContractBehaviors.RECALL, false).changed()
                    && beast.getData(MxtAttachments.CONTRACT).recalled()
                    && beast.getData(MxtAttachments.CONTRACT).behavior().equals(ContractBehaviors.WANDER);
            ContractService.completeRecall(beast);
            beast.setPos(player.getX() + 40.0D, player.getY(), player.getZ());
            ContractEventBridge.onEntityTick(new EntityTickEvent.Post(beast));
            boolean dispatched = beast.distanceToSqr(player) < 1.0D && ProbeBeast.calls().contains("tick:wander");
            boolean orders = momentary && dispatched;
            source.sendSuccess(() -> Component.literal("contract probe: momentary=" + momentary + " dispatched=" + dispatched
                    + (orders ? " OK" : " MISMATCH")), false);

            // Carrying is the item's business: the framework gates nothing, the bag keeps its own rule (your own
            // contracted beast), and the creature is only told. The probe hears it, and the round trip brings its
            // owner back because the probe saves that reference itself. The bag also carries what it has to say
            // about the creature without loading it - type, name, contract type and owner - and the released one
            // is still bound, because attachments ride along in the saved data.
            ItemStack bagStack = new ItemStack(MxtItems.SPIRIT_BEAST_BAG.get());
            boolean captured = bagStack.getItem().interactLivingEntity(bagStack, player, second, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS
                    && second.isRemoved();
            SpiritBeastComponent stored = bagStack.getOrDefault(MxtDataComponents.SPIRIT_BEAST, SpiritBeastComponent.EMPTY);
            boolean described = stored.stored()
                    && stored.entityType().filter(BuiltInRegistries.ENTITY_TYPE.getKey(second.getType())::equals).isPresent()
                    && stored.name().isPresent()
                    && stored.contractType().map(HolderHelper::id).filter(OPEN_CONTRACT::equals).isPresent()
                    && stored.owner().filter(player.getUUID()::equals).isPresent()
                    && !stored.ownerName().isBlank();
            player.setItemInHand(InteractionHand.MAIN_HAND, bagStack);
            BlockPos releaseAt = player.blockPosition();
            UseOnContext release = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atBottomCenterOf(releaseAt), Direction.UP, releaseAt, false));
            boolean releasedFromBag = bagStack.getItem().useOn(release) == InteractionResult.SUCCESS_SERVER
                    && !bagStack.getOrDefault(MxtDataComponents.SPIRIT_BEAST, SpiritBeastComponent.EMPTY).stored();
            ProbeBeast restored = level.getEntitiesOfClass(ProbeBeast.class, player.getBoundingBox().inflate(2.0D)).stream()
                    .filter(candidate -> candidate != beast && candidate != first)
                    .findFirst().orElse(null);
            if (restored != null) spawned.add(restored);
            boolean bag = captured && described && releasedFromBag && restored != null
                    && restored.getUUID().equals(second.getUUID())
                    && Contracts.ownerOf(restored).filter(player.getUUID()::equals).isPresent()
                    && restored.getData(MxtAttachments.CONTRACT).bound();
            source.sendSuccess(() -> Component.literal("contract probe: captured=" + captured + " described=" + described
                    + " freed=" + releasedFromBag + " restored=" + (restored != null) + (bag ? " OK" : " MISMATCH")), false);

            // Death ends the record, runs the creature's own death hook and drops its row. The death action is a
            // field of its own, so a release and a death never share one.
            int rowsBefore = BoundBeastService.of(server, player.getUUID()).size();
            beast.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
            boolean death = !beast.getData(MxtAttachments.CONTRACT).bound()
                    && beast.isOnFire()
                    && BoundBeastService.of(server, player.getUUID()).size() == rowsBefore - 1;
            boolean order = ProbeBeast.calls().equals(List.of("bound", "bound", "released", "bound", "recall",
                    "order:wander", "order:stay", "tick:wander", "captured", "freed", "death"));
            boolean ending = death && order;
            source.sendSuccess(() -> Component.literal("contract probe: death=" + death + " calls=" + ProbeBeast.calls()
                    + (ending ? " OK" : " MISMATCH")), false);

            if (eligibility && record && limit && recall && bellLeg && input && orders && bag && ending) {
                source.sendSuccess(() -> Component.literal("contract probe: OK"), false);
                return 1;
            }
            source.sendFailure(Component.literal("contract probe: MISMATCH"));
            return 0;
        } finally {
            resources.set(qi, previousQi, 0.0D, 10_000.0D, -1L, "probe");
            for (Mob mob : spawned) mob.discard();
        }
    }

    private static ContractContext context(Mob creature, ServerPlayer owner, Holder<ContractType> type) {
        return ContractContext.of(creature, owner.getUUID(), owner, type);
    }

    private static ProbeBeast spawnProbeBeast(ServerLevel level, BlockPos pos) {
        ProbeBeast beast = MxtTestEntities.PROBE_BEAST.get().create(level, EntitySpawnReason.COMMAND);
        if (beast == null) throw new IllegalStateException("The probe beast type failed to create an entity");
        beast.setNoAi(true);
        beast.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        level.addFreshEntity(beast);
        return beast;
    }

    // Ignoring a field is only a guarantee if it leaves no trace, so the two decoded definitions are compared.
    private static boolean ignoresForeignFields() {
        JsonObject foreign = new JsonObject();
        foreign.addProperty("element", "mxt_test:fire");
        foreign.addProperty("overcomes", "mxt_test:water");
        foreign.addProperty("damage_types", "mxt_test:fire");
        foreign.addProperty("rarity", "probe");
        return Physique.DIRECT_CODEC.parse(JsonOps.INSTANCE, foreign).result()
                .equals(Physique.DIRECT_CODEC.parse(JsonOps.INSTANCE, single("rarity", "probe")).result());
    }

    // One-field JSON object, for decode checks that ask whether a single key is enough to break a definition.
    private static JsonObject single(String key, Object value) {
        JsonObject object = new JsonObject();
        if (value instanceof Number number) object.addProperty(key, number);
        else if (value instanceof Boolean flag) object.addProperty(key, flag);
        else object.addProperty(key, String.valueOf(value));
        return object;
    }

    private static JsonArray numbers(double... values) {
        JsonArray array = new JsonArray();
        for (double value : values) array.add(value);
        return array;
    }

    // The artifact fixture roster: one row per item, every question asked through the service the game itself
    // uses. The rows cover item matching by id, by list and by item tag, per-aura ceilings (one a formula),
    // storage slots from a formula, abilities named by id and by ability tag, the charm gate, the require_owner
    // gate, a refine action that charges on the way in, and the flight entry's own numbers.
    // One fixture is wrong on purpose, because the check it trips lives in ServerCache and is observable no other
    // way: claim_conflict_probe claims an item misdeclared_grant_probe also claims - and registry order decides
    // which the claim problem names, so that assertion accepts either.
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
                new ArtifactRow(Items.IRON_SWORD, "azure_flight_sword", 60, 0, 0, 0, false, true, true),
                new ArtifactRow(Items.AMETHYST_SHARD, "spirit_gathering_jade", 200, 100, 25, 27, true, false, false),
                new ArtifactRow(Items.PRISMARINE_SHARD, "ward_jade_talisman", 40, 0, 0, 0, true, false, false),
                // The second item of the same definition, reached through the item tag its `items` list names.
                new ArtifactRow(Items.QUARTZ, "ward_jade_talisman", 40, 0, 0, 0, true, false, false),
                new ArtifactRow(Items.BELL, "cloud_beast_bell", 60, 0, 0, 18, true, false, false))) {
            ItemStack stack = new ItemStack(row.item());
            Reference<Artifact> holder = ArtifactService.definition(access, stack).orElse(null);
            Artifact definition = holder == null ? null : holder.value();
            // The registry id of the definition that answered is the row's first claim.
            boolean matches = holder != null && HolderHelper.id(holder).equals(id(row.definition()))
                    && row.curiosEquipable() == ArtifactService.curiosEquipable(access, stack)
                    && row.requireOwner() == definition.requireOwner()
                    && row.flight() == ArtifactService.flight(access, stack).isPresent()
                    && ArtifactService.storageSlots(access, stack, context) == row.slots()
                    && ArtifactService.capacity(access, stack, qi, 0.0D, context) == row.qi()
                    && ArtifactService.capacity(access, stack, waterPower, 0.0D, context) == row.waterPower()
                    && ArtifactService.capacity(access, stack, soulPower, 0.0D, context) == row.soulPower();
            ok &= check(source, "artifact roster " + BuiltInRegistries.ITEM.getKey(row.item()) + " = "
                    + (holder == null ? "unclaimed" : HolderHelper.id(holder)), matches);
        }

        ItemStack flightStack = new ItemStack(Items.IRON_SWORD);
        FlightAbilityType flight = ArtifactService.flight(access, flightStack).orElse(null);
        List<Cost> flightCosts = flightStack.isEmpty() ? List.of()
                : ArtifactService.abilities(access, flightStack).stream()
                .filter(ref -> ref.value().type() instanceof FlightAbilityType)
                .findFirst().map(ref -> ref.value().costs()).orElse(List.of());
        boolean flightEntry = flight != null && close(flight.speed().evaluate(context), 0.12D)
                && flightCosts.size() == 1 && flightCosts.getFirst() instanceof ResourceCost cost
                && cost.id().equals(QI)
                && close(cost.amount().evaluate(context), 2.0D)
                && flight.display().equals(FlightDisplay.DEFAULT);
        ok &= check(source, "artifact roster flight entry speed=0.12 costs=2 qi display=default", flightEntry);

        // A written display is read the way a vanilla model's is: translation in sixteenths of a block, rotation in
        // degrees, scale as a multiplier.
        JsonObject written = new JsonObject();
        written.add("translation", numbers(0.0D, 4.0D, 0.0D));
        written.add("rotation", numbers(90.0D, 0.0D, -45.0D));
        written.add("scale", numbers(2.0D, 0.0D, 2.0D));
        boolean displayRead = FlightDisplay.CODEC.parse(JsonOps.INSTANCE, written).result()
                .filter(display -> close(display.translation().y(), 0.25D) && close(display.scale().y(), 0.0D))
                .isPresent();
        ok &= check(source, "artifact roster flight display reads 4/16 as 0.25", displayRead);

        // The same flight with a holder that is not a player: the mount, the carrier lookup and the stored state
        // all read off the entity, and only a player's own flight permission has nothing to restore. The carrier is
        // claimed first because the definition asks for an owner, and the tick is driven with the qi to pay for it.
        LivingEntity mobRider = spawnProbe(source.getLevel(), player.blockPosition().above(5), null);
        Holder<Ability> mobFlightAbility = require(MxtResourceKeys.ABILITY, id("azure_flight"));
        boolean mobFlight = false;
        if (mobRider != null) {
            ItemStack mobCarrier = flightStack.copy();
            mobRider.setItemInHand(InteractionHand.MAIN_HAND, mobCarrier);
            ensureResource(mobRider, mobRider.getData(MxtAttachments.RESOURCE_HOLDER), require(MxtResourceKeys.RESOURCE, QI), 10.0D);
            boolean sighted = ArtifactService.refine(mobCarrier, mobRider) == RefineResult.REFINED
                    && ArtifactService.carried(access, mobRider, HolderHelper.id(mobFlightAbility)).isPresent();
            FlightService.Result mounted = FlightService.mount(mobRider, mobCarrier, mobFlightAbility, FormulaContext.of(mobRider));
            boolean riding = mounted.state() == FlightService.Result.State.MOUNTED
                    && mobRider.getVehicle() instanceof FlyingSwordEntity
                    && mobRider.getData(MxtAttachments.FLIGHT).active()
                    && FlightService.tick(mobRider, mobFlightAbility, FormulaContext.of(mobRider)).state() == FlightService.Result.State.FLYING;
            boolean landed = FlightService.dismount(mobRider, FlightService.Failure.STOPPED).state() == FlightService.Result.State.STOPPED
                    && mobRider.getVehicle() == null && !mobRider.getData(MxtAttachments.FLIGHT).active();
            mobFlight = sighted && riding && landed;
        }
        if (mobRider != null) mobRider.discard();
        ok &= check(source, "artifact roster mob flight carried=in-hand mounted=on-sword tick=flying landed=off-sword", mobFlight);

        ItemStack wardStack = new ItemStack(Items.PRISMARINE_SHARD);
        // Two written entries - one id and one ability tag - reaching the two ids the definition grants: the tag
        // is expanded against the same registry the grant itself resolves in.
        boolean grants = ArtifactService.definition(access, wardStack)
                .map(holder -> holder.value().abilities().size()).orElse(0) == 2
                && ArtifactService.abilityIds(access, wardStack).equals(List.of(PROBE_ABILITY, SWORD_FOCUS));
        ok &= check(source, "artifact roster grants=id+tag expands to 2 ids", grants);

        // The refine action charges 40 of a 200 ceiling, so that feeding raises the ceiling to floor(200 * 1.1).
        ItemStack jadeStack = new ItemStack(Items.AMETHYST_SHARD);
        boolean refined = ArtifactService.refine(jadeStack, player) == RefineResult.REFINED;
        int jadeStored = ArtifactService.stored(jadeStack, qi);
        int jadeCeiling = ArtifactService.capacity(access, jadeStack, qi, 0.0D, context);
        ok &= check(source, "artifact roster claim_action stored=" + jadeStored + " ceiling=" + jadeCeiling,
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

        // ArtifactDescription builds the tooltip so the same list is readable here without a client: the rendered
        // words belong to a language file, but how many lines a definition earns belongs to this module.
        // Ownership and warmth are known because the fresh stacks carry neither and the jade was just refined.
        List<String> flightLines = ArtifactDescription.keys(ArtifactDescription.describe(access, flightStack, player, false));
        List<String> jadeLines = ArtifactDescription.keys(ArtifactDescription.describe(access, new ItemStack(Items.AMETHYST_SHARD), player, false));
        List<String> fedLines = ArtifactDescription.keys(ArtifactDescription.describe(access, jadeStack, player, false));
        List<String> wardLines = ArtifactDescription.keys(ArtifactDescription.describe(access, wardStack, player, false));
        // Anything a definition's own action charges is reported as a price; the blood-priced probe is the one
        // fixture charging health, and the only one whose claim line names a number.
        List<String> bloodLines = ArtifactDescription.keys(ArtifactDescription.describe(access, new ItemStack(Items.BLAZE_ROD), player, false));
        List<String> upkeepLines = ArtifactDescription.keys(ArtifactDescription.describe(access, new ItemStack(Items.IRON_SHOVEL), player, false));
        // The long press closes every tooltip with a gesture, and an unowned artifact is offered the claim while
        // one the reader owns is offered the pour - the two halves never appear together.
        boolean tooltip = flightLines.equals(List.of(tooltipKey("header"), tooltipKey("unowned"),
                        // One entry, one line: the speed and what riding costs share a line, so a definition with
                        // four abilities produces four lines rather than a paragraph.
                        tooltipKey("aura"), tooltipKey("flight_costs"), tooltipKey("hold_claim")))
                // The jade does not require an owner, so a fresh one says nothing about ownership at all, and its
                // action charges aura rather than health, so its claim is free.
                && jadeLines.equals(List.of(tooltipKey("header"),
                        tooltipKey("aura"), tooltipKey("aura"), tooltipKey("aura"), tooltipKey("storage"), tooltipKey("hold_claim_free")))
                && fedLines.equals(List.of(tooltipKey("header"), tooltipKey("owned"),
                        tooltipKey("aura"), tooltipKey("aura"), tooltipKey("aura"), tooltipKey("nourishment"),
                        tooltipKey("storage"), tooltipKey("hold_pour")))
                // The ward says nothing about a price, so the default two hearts are what it reports.
                && wardLines.equals(List.of(tooltipKey("header"),
                        tooltipKey("aura"), tooltipKey("passive"), tooltipKey("passive"), tooltipKey("hold_claim")))
                && bloodLines.equals(List.of(tooltipKey("header"),
                        tooltipKey("aura"), tooltipKey("hold_claim")))
                // An upkeep entry is a line of its own, and the fixture's free price keeps its claim line free.
                && upkeepLines.equals(List.of(tooltipKey("header"),
                        tooltipKey("aura"), tooltipKey("upkeep"), tooltipKey("hold_claim_free")))
                // A stack no definition claims gets nothing at all, and advanced tooltips add the id under the name.
                && ArtifactDescription.describe(access, new ItemStack(Items.DIAMOND), player, false).isEmpty()
                && ArtifactDescription.keys(ArtifactDescription.describe(access, flightStack, player, true)).size() == flightLines.size() + 1;
        ok &= check(source, "artifact roster tooltip lines flight=" + flightLines.size() + " jade=" + jadeLines.size()
                + " fed=" + fedLines.size() + " ward=" + wardLines.size() + " blood=" + bloodLines.size()
                + " upkeep=" + upkeepLines.size(), tooltip);

        // The long press: what a definition declares, who it takes over for, and the two settlements it can make.
        // Driven through the service rather than a real use cycle, because the arming half belongs to the hold
        // module and is what the client sees; the numbers below are the server's half.
        ItemStack dormantStack = new ItemStack(Items.GUNPOWDER);
        boolean declaredHold = ArtifactService.holdTicks(access, flightStack, context) == 20
                && ArtifactService.holdTicks(access, jadeStack, context) == 30
                && ArtifactService.holdTicks(access, new ItemStack(Items.GLOWSTONE_DUST), context) == 5
                // The price is the default of `claim_action`, read back out of that action: saying nothing charges
                // two hearts, doing something else entirely (the jade) charges none, and a plainly written
                // consume_health reports that number.
                && ArtifactService.claimHealthCost(access, flightStack, context) == 4.0D
                && ArtifactService.claimHealthCost(access, wardStack, context) == 4.0D
                && ArtifactService.claimHealthCost(access, jadeStack, context) == 0.0D
                && ArtifactService.claimHealthCost(access, new ItemStack(Items.GLOWSTONE_DUST), context) == 1.0D
                && ArtifactService.claimHealthCost(access, new ItemStack(Items.BLAZE_ROD), context) == 3.0D
                // hold_ticks 0 is the escape hatch: the definition is not a hold, so nothing matches the item.
                && ArtifactService.holdTicks(access, dormantStack, context) == 0
                && HoldLookup.hold(dormantStack) == null
                && HoldLookup.hold(flightStack) instanceof ArtifactHold;
        ok &= check(source, "artifact hold declared ticks 20/30/5, claim_action-stated cost default 4 / none 0 / 1 and 3",
                declaredHold);

        ItemStack ownedHoldSword = flightStack.copy();
        ArtifactService.refine(ownedHoldSword, player);
        // Filling every declared aura is what makes the pour pointless, so the gesture must stop being offered.
        ItemStack fullJade = new ItemStack(Items.AMETHYST_SHARD);
        ArtifactService.refine(fullJade, player);
        for (int round = 0; round < 3; round++) {
            ArtifactService.addEnergy(access, fullJade, qi, 1000.0D, 0.0D, context);
            ArtifactService.addEnergy(access, fullJade, waterPower, 1000.0D, 0.0D, context);
            ArtifactService.addEnergy(access, fullJade, soulPower, 1000.0D, 0.0D, context);
        }
        LivingEntity holdBystander = spawnProbe(source.getLevel(), player.blockPosition().above(2), null);
        boolean takeover = HoldLookup.hold(flightStack) instanceof ArtifactHold unownedHold
                // Unclaimed: the claim is on offer to anybody, so both the player and a probe entity take it over.
                && unownedHold.claims(player, access, flightStack)
                && holdBystander != null && unownedHold.claims(holdBystander, access, flightStack)
                // Claimed: the owner is taken over and the stranger is not - the whole point of the entity-aware
                // answer, since the stack says nothing about who is holding it.
                && unownedHold.claims(player, access, ownedHoldSword)
                && !unownedHold.claims(holdBystander, access, ownedHoldSword)
                && HoldLookup.hold(fullJade) instanceof ArtifactHold fullHold
                && !fullHold.claims(player, access, fullJade);
        if (holdBystander != null) holdBystander.discard();
        ok &= check(source, "artifact hold takeover unclaimed=both claimed=owner-only full=refused", takeover);

        // Claiming pays the price and then runs the action, and the two are separate fields: the blood fixture
        // states both, the jade replaces the price with a no-op to bind for free.
        player.setHealth(player.getMaxHealth());
        float healthBeforeClaim = player.getHealth();
        ItemStack claimBlood = new ItemStack(Items.BLAZE_ROD);
        ClaimResult claimedHold = ArtifactHoldService.claim(player, claimBlood, access);
        boolean pricePaid = claimedHold == ClaimResult.CLAIMED && ArtifactService.isOwner(claimBlood, player.getUUID())
                // Both prices of the fixture's list were charged and its claim_action ran: five points arrived.
                && ArtifactService.stored(claimBlood, qi) == 5
                // An invulnerable holder cannot be made to bleed and the binding still stands, so the number is
                // only asserted where the damage could land at all.
                && (player.isInvulnerable() || close(healthBeforeClaim - player.getHealth(), 3.0D));
        // A price replaced by mxt:no_op binds for free, at health a priced claim would have spent.
        player.setHealth(4.0F);
        ItemStack freeJade = new ItemStack(Items.AMETHYST_SHARD);
        ClaimResult freeHold = ArtifactHoldService.claim(player, freeJade, access);
        boolean freeClaim = freeHold == ClaimResult.CLAIMED && ArtifactService.isOwner(freeJade, player.getUUID())
                && close(player.getHealth(), 4.0D);
        // No health pre-check any more: a price the holder cannot survive is charged anyway and the binding
        // still stands. Driven on a disposable probe, since the alternative is killing the player mid-run.
        LivingEntity poorClaimant = spawnProbe(source.getLevel(), player.blockPosition().above(3), null);
        boolean unguarded = false;
        if (poorClaimant != null) {
            poorClaimant.setHealth(2.0F);
            ItemStack doomedSword = new ItemStack(Items.IRON_SWORD);
            ClaimResult doomed = ArtifactHoldService.claim(poorClaimant, doomedSword, access);
            unguarded = doomed == ClaimResult.CLAIMED && ArtifactService.isOwner(doomedSword, poorClaimant.getUUID())
                    && (poorClaimant.isDeadOrDying() || close(poorClaimant.getHealth(), 0.0D));
            poorClaimant.discard();
        }
        // The definition's own condition is asked before either action runs: this fixture states a price of one,
        // and a refused binding does not pay it.
        player.setHealth(4.0F);
        ItemStack sealedStack = new ItemStack(Items.GLOWSTONE_DUST);
        ClaimResult sealedHold = ArtifactHoldService.claim(player, sealedStack, access);
        boolean sealedRefused = sealedHold == ClaimResult.CONDITION_FAILED && !ArtifactService.hasOwner(sealedStack)
                && close(player.getHealth(), 4.0F);
        // Charging belongs to the actions, so every writer of a binding pays it - the loot function and a script
        // reach `refine` directly. Health is topped up first, because this path is guarded by nothing.
        player.setHealth(player.getMaxHealth());
        float healthBeforeScript = player.getHealth();
        ItemStack scriptedBlood = new ItemStack(Items.BLAZE_ROD);
        boolean scriptPaid = ArtifactService.refine(scriptedBlood, player) == RefineResult.REFINED
                && ArtifactService.stored(scriptedBlood, qi) == 5
                && (player.isInvulnerable() || close(healthBeforeScript - player.getHealth(), 3.0D));
        player.setHealth(healthBeforeClaim);
        ok &= check(source, "artifact hold claim paid=" + pricePaid + " free=" + freeClaim + " unguarded=" + unguarded
                + " condition=" + sealedRefused + " script=" + scriptPaid,
                pricePaid && freeClaim && unguarded && sealedRefused && scriptPaid);

        // Whose a stack is is reported by name: the claim above wrote this player's name next to their UUID and
        // the tooltip line carries that name rather than the id. The id stays reachable - as the indented line
        // under an advanced tooltip - but never as the only thing a reader is given.
        boolean ownerNamed = ArtifactService.state(claimBlood).ownerName()
                .filter(player.getGameProfile().name()::equals).isPresent()
                && ArtifactDescription.describe(access, claimBlood, player, false).stream()
                .anyMatch(line -> ownedLineNames(line, player.getGameProfile().name()));
        // The name behind an id is answerable on demand, which is what a client holding a stack claimed before
        // names were kept asks for: the server answers from its player list and admits when it has never seen
        // the player rather than making something up.
        boolean ownerLookup = PlayerNames.knownToServer(source.getServer(), player.getUUID())
                .filter(player.getGameProfile().name()::equals).isPresent()
                && PlayerNames.knownToServer(source.getServer(), UUID.randomUUID()).isEmpty();
        ok &= check(source, "artifact owner shown by name=" + ownerNamed + " lookup=" + ownerLookup,
                ownerNamed && ownerLookup);

        // The periodic price is an ability entry rather than a claim field: it falls due on its own interval
        // while the artifact is carried, it asks nobody but the owner when the entry says owner_only, and an
        // unpayable price runs the entry's own failure action instead of being skipped silently.
        ItemStack upkeepStack = new ItemStack(Items.IRON_SHOVEL);
        ArtifactService.refine(upkeepStack, player);
        ResourceHolderAttachment upkeepPools = player.getData(MxtAttachments.RESOURCE_HOLDER);
        // The fixture charges water_power rather than qi on purpose: qi's aura declares a realm gate, and this
        // leg drives the real resource transaction, which asks it.
        ensureResource(player, upkeepPools, waterPower.value().resource(), 10.0D);
        double upkeepPool = upkeepPools.get(waterPower.value().resource());
        boolean upkeepPaid = ArtifactUpkeepService.upkeep(player, upkeepStack, 10L)
                && close(upkeepPool - upkeepPools.get(waterPower.value().resource()), 2.0D)
                // A tick between two intervals is nobody's, so nothing falls due on it.
                && !ArtifactUpkeepService.upkeep(player, upkeepStack, 11L)
                && close(upkeepPool - upkeepPools.get(waterPower.value().resource()), 2.0D);
        FormulaContext upkeepContext = ResourceService.formulaContext(player, waterPower.value().resource(), context);
        ResourceService.change(upkeepPools, waterPower.value().resource(),
                -upkeepPools.get(waterPower.value().resource()), upkeepContext);
        boolean upkeepFailed = !ArtifactUpkeepService.upkeep(player, upkeepStack, 15L) && upkeepStack.getDamageValue() == 1;
        LivingEntity upkeepBystander = spawnProbe(source.getLevel(), player.blockPosition().above(4), null);
        boolean upkeepOwnerOnly = upkeepBystander != null
                && !ArtifactUpkeepService.upkeep(upkeepBystander, upkeepStack, 20L) && upkeepStack.getDamageValue() == 1;
        if (upkeepBystander != null) upkeepBystander.discard();
        ok &= check(source, "artifact upkeep paid=" + upkeepPaid + " fail=" + upkeepFailed
                + " owner_only=" + upkeepOwnerOnly, upkeepPaid && upkeepFailed && upkeepOwnerOnly);

        // The pour moves one unit of every declared aura out of the holder's own pools, one for one. The jade's
        // claim_action charged 40 of qi on the way in, so the tick starts from there.
        ItemStack pourStack = new ItemStack(Items.AMETHYST_SHARD);
        ArtifactService.refine(pourStack, player);
        ResourceHolderAttachment pools = player.getData(MxtAttachments.RESOURCE_HOLDER);
        ensureResource(player, pools, qi.value().resource(), 10.0D);
        ensureResource(player, pools, waterPower.value().resource(), 10.0D);
        ensureResource(player, pools, soulPower.value().resource(), 10.0D);
        double qiPool = pools.get(qi.value().resource());
        double waterPool = pools.get(waterPower.value().resource());
        double soulPool = pools.get(soulPower.value().resource());
        int poured = ArtifactHoldService.pour(player, pourStack, access,
                ArtifactService.definition(access, pourStack).map(Reference::value).orElseThrow());
        boolean pourLeg = poured == 3
                && ArtifactService.stored(pourStack, qi) == 41
                && ArtifactService.stored(pourStack, waterPower) == 1
                && ArtifactService.stored(pourStack, soulPower) == 1
                && close(pools.get(qi.value().resource()), qiPool - 1.0D)
                && close(pools.get(waterPower.value().resource()), waterPool - 1.0D)
                && close(pools.get(soulPower.value().resource()), soulPool - 1.0D);
        ok &= check(source, "artifact hold pour units=" + poured + " qi=" + ArtifactService.stored(pourStack, qi)
                + " water=" + ArtifactService.stored(pourStack, waterPower), pourLeg);

        // The two gesture-shaped actions: pour_action settles every tick that really moved aura, use_action
        // closes a gesture that ran to its end. Driven through the methods the event handlers call.
        int beforePourAction = ArtifactService.stored(pourStack, qi);
        ArtifactHoldService.runPourAction(player, pourStack);
        int beforeUseAction = ArtifactService.stored(pourStack, qi);
        ArtifactHoldService.runUseAction(player, pourStack);
        boolean gestureActions = beforeUseAction == beforePourAction + 1
                && ArtifactService.stored(pourStack, qi) == beforeUseAction + 2;
        ok &= check(source, "artifact gesture actions pour=+" + (beforeUseAction - beforePourAction)
                + " use=+" + (ArtifactService.stored(pourStack, qi) - beforeUseAction), gestureActions);

        // A pour with nothing to move has two reasons, and they are told apart: an artifact full for everything
        // it declares says nothing, while one whose aura the holder cannot pay names it. The ward is filled to
        // its ceiling for the first half and the qi pool drained for the second.
        ItemStack fullWard = new ItemStack(Items.PRISMARINE_SHARD);
        ArtifactService.addEnergy(access, fullWard, qi, 1000.0D, 0.0D, context);
        Artifact fullWardDefinition = ArtifactService.definition(access, fullWard).map(Reference::value).orElseThrow();
        boolean fullSilent = ArtifactHoldService.blockedAura(player, fullWard, fullWardDefinition) == null;
        ResourceHolderAttachment blockPools = player.getData(MxtAttachments.RESOURCE_HOLDER);
        ResourceService.change(blockPools, qi.value().resource(), -blockPools.get(qi.value().resource()),
                ResourceService.formulaContext(player, qi.value().resource(), context));
        ItemStack emptyWard = new ItemStack(Items.PRISMARINE_SHARD);
        Artifact emptyWardDefinition = ArtifactService.definition(access, emptyWard).map(Reference::value).orElseThrow();
        boolean namedShortfall = ArtifactHoldService.blockedAura(player, emptyWard, emptyWardDefinition) == qi;
        ok &= check(source, "artifact pour feedback full=" + fullSilent + " named=" + namedShortfall,
                fullSilent && namedShortfall);

        List<String> problems = ServerCache.get().map(cache -> cache.problems().stream()
                .filter(problem -> problem.contains("/mxt/artifact/")).toList()).orElse(List.of());
        // The merge deleted the "granted as mxt:passive but the ability itself is active" check: there is no second
        // intent left to disagree with. What remains is the claim conflict between the two echo-shard fixtures.
        boolean claimed = problems.stream().anyMatch(problem -> problem.contains("already claims")
                && (problem.contains(MISDECLARED_ARTIFACT.getPath()) || problem.contains(CLAIM_CONFLICT_ARTIFACT.getPath())));
        ok &= check(source, "artifact roster validator problems=" + problems, problems.size() == 1 && claimed);

        if (ok) {
            source.sendSuccess(() -> Component.literal("artifact roster: OK"), false);
            return 1;
        }
        source.sendFailure(Component.literal("artifact roster: MISMATCH"));
        return 0;
    }

    // One roster row: an item, and what every consumer must answer about the definition claiming it.
    private record ArtifactRow(Item item, String definition, int qi, int waterPower, int soulPower, int slots,
                               boolean curiosEquipable, boolean flight, boolean requireOwner) {
    }

    // One artifact tooltip key, so the roster can spell out the lines a definition must produce for a stack.
    private static String tooltipKey(String path) {
        return "tooltip.mxt.artifact." + path;
    }

    // The rendered words belong to a language file, so the check reads the component's own argument. The line is
    // a mark with the sentence appended, so its siblings are searched too.
    private static boolean ownedLineNames(Component line, String owner) {
        if (line.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(tooltipKey("owned"))
                && Arrays.stream(contents.getArgs()).anyMatch(argument -> argument instanceof String value && value.equals(owner)))
            return true;
        return line.getSiblings().stream().anyMatch(sibling -> ownedLineNames(sibling, owner));
    }

    // One disposable probe entity carrying exactly one spirit root, so the element it strikes or is struck with
    // is never a question. A pig is used because it has no armour and no innate resistance, which keeps the
    // health it loses equal to what the pipeline handed over. A null root leaves it carrying none, which is how
    // "the element came from the damage type and not from the caster" is told apart.
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

    // Drives the element channel end to end: which element a strike is read as, where that reading comes from,
    // what a strike leaves behind, which reaction answers it, and what the enable/disable module does to all of
    // it. Every leg runs against disposable probe entities.
    // Fixture numbers, which is why changing test-pack content changes a leg's arithmetic: fire overcomes water
    // x1.5 and water is adapted to fire x0.5, so a fire strike of 10 on a water body lands as
    // 10 * 1.5 * 0.5 = 7.5 while a water strike on a water body lands as 10 * 0.5 = 5. That difference is what
    // tells the readings apart: the claim leg uses a rootless caster, the declaration leg a water caster whose
    // art is written as fire.
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

            // 3. A strike leaves its element behind, and enough of it is answered by the fixture reaction, which
            //    takes the buildup away and deals its own 6 (no attacker, no claim: unchanged).
            ElementReactionService.applyFromStrike(reactionVictim, Set.of(fire), FormulaContext.of(reactionVictim));
            double built = ElementReactionService.amount(reactionVictim, fire);
            double beforeReaction = reactionVictim.getHealth();
            ElementReactionService.apply(reactionVictim, fire, 4.0D, FormulaContext.of(reactionVictim));
            double reactionLost = beforeReaction - reactionVictim.getHealth();
            double leftOver = ElementReactionService.amount(reactionVictim, fire);
            boolean reaction = close(built, 4.0D) && close(reactionLost, 6.0D) && close(leftOver, 0.0D);
            source.sendSuccess(() -> Component.literal("element probe: attachment built=" + built
                    + " reaction damage=" + reactionLost + " left=" + leftOver + (reaction ? " OK" : " MISMATCH")), false);

            // 4. A disabled element stops applying: its own root contributes nothing and the condition asking
            //    about it says no. The fixture element is fetched raw, because the enabled accessor is exactly
            //    what this leg is here to prove is empty.
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
            // The aura leg asks the environment for the same number first and brackets it, so it tests the
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

            // 7. A reaction whose own action applies the element it just consumed feeds itself, which would have
            //    no floor without the reentrancy guard: the nested application joins the chain already running
            //    for this body instead of opening another. Every pass takes the demand away and puts it straight
            //    back, so the body carries exactly what was applied however many passes the bound allows.
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

            // 9. The five-phase set the test package ships: metal beats wood in the shaping layer and wood is
            //    soft against metal in the reduction layer, so the same 4 lands as 4 * 1.4 * 1.25 = 7, while the
            //    opposite direction has neither edge and lands as 4. Neither strike declares a damage type, so
            //    both read their element off the striker's roots - and a roots reading reduces without rubbing
            //    off, which is why `metal_left` is 0. The damage probe's `origin` leg measures leaving an element
            //    behind.
            double metalBefore = woodVictim.getHealth();
            DamageCalculationService.deal(metalCaster, woodVictim, 4.0D, Optional.empty(), FormulaContext.of(metalCaster));
            double metalOnWood = metalBefore - woodVictim.getHealth();
            double woodBefore = metalVictim.getHealth();
            DamageCalculationService.deal(woodCaster, metalVictim, 4.0D, Optional.empty(), FormulaContext.of(woodCaster));
            double woodOnMetal = woodBefore - metalVictim.getHealth();
            double metalLeft = ElementReactionService.amount(woodVictim, metal);
            boolean fivePhases = close(metalOnWood, 7.0D) && close(woodOnMetal, 4.0D) && close(metalLeft, 0.0D)
                    && new HasElementEntityCondition(List.of(Either.right(fivePhasesTag())))
                    .test(metalCaster, FormulaContext.of(metalCaster));
            source.sendSuccess(() -> Component.literal("element probe: five phases metal_on_wood=" + metalOnWood
                    + " wood_on_metal=" + woodOnMetal + " metal_left=" + metalLeft
                    + (fivePhases ? " OK" : " MISMATCH")), false);

            // 10. A reaction demanding two elements at once, spending only one and acting twice: wood alone does
            //     not answer it, and afterwards the water that completed the demand is still there while the
            //     earth the action left behind has not reached its own demand.
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
            //     fires it exactly as many times as the chain allows and leaves the buildup untouched. The count
            //     is the documented bound, written out here so changing it has to be a decision.
            settleVictim.getData(MxtAttachments.ELEMENT_ATTACHMENT).add(earth, 10.0D);
            int settleFired = ElementReactionService.trigger(settleVictim, FormulaContext.of(settleVictim));
            double settleLeft = ElementReactionService.amount(settleVictim, earth);
            boolean settle = settleFired == 8 && close(settleLeft, 10.0D);
            source.sendSuccess(() -> Component.literal("element probe: unconsumed reaction fired=" + settleFired
                    + " left=" + settleLeft + (settle ? " OK" : " MISMATCH")), false);

            // 12. What an item is made of. The sword's weapon binding declares fire; the jade's artifact declares
            //     the whole #mxt_test:basic tag, which is fire and water; the spirit crystal declares nothing, so
            //     it is read from the aura it carries - spirit_power names fire as its `aura_type`; a stick
            //     matches no definition and carries no aura, so it is made of nothing. The condition is the same
            //     reading from a data pack's side.
            Set<Holder<Element>> swordElements = ItemElements.of(level.registryAccess(),
                    new ItemStack(Items.DIAMOND_SWORD));
            Set<Holder<Element>> jadeElements = ItemElements.of(level.registryAccess(),
                    new ItemStack(Items.AMETHYST_SHARD));
            Set<Holder<Element>> crystalElements = ItemElements.of(level.registryAccess(),
                    new ItemStack(MxtTestItems.QINGXIAO_SPIRIT_CRYSTAL.get()));
            Set<Holder<Element>> plainElements = ItemElements.of(level.registryAccess(), new ItemStack(Items.STICK));
            boolean itemElement = swordElements.equals(Set.of(fire)) && jadeElements.equals(Set.of(fire, water))
                    && crystalElements.equals(Set.of(fire)) && plainElements.isEmpty()
                    && new ItemElementCondition(List.of(Either.left(water)))
                    .test(toggleProbe, new ItemStack(Items.AMETHYST_SHARD), FormulaContext.of(toggleProbe))
                    && !new ItemElementCondition(List.of(Either.left(water)))
                    .test(toggleProbe, new ItemStack(Items.DIAMOND_SWORD), FormulaContext.of(toggleProbe));
            source.sendSuccess(() -> Component.literal("element probe: item element sword=" + elementIds(swordElements)
                    + " jade=" + elementIds(jadeElements) + " crystal=" + elementIds(crystalElements)
                    + " plain=" + plainElements.size() + (itemElement ? " OK" : " MISMATCH")), false);

            if (claimed && declared && reaction && disabled && hasElement && auraElement && attachment && toggle
                    && loop && conflict && fivePhases && bloom && settle && itemElement) {
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

    // One page's answer to "would a trigger for this ability from this page be honoured".
    private static boolean offers(LivingEntity probe, WheelSource source, Identifier ability) {
        return WheelSources.offers(probe, source, WheelEntryKind.ABILITY, ability);
    }

    // Sends one storage component through the registered network codec and reads it back - the very codec the
    // server uses when handing a container slot to a client. This is the only way a probe reaches that failure:
    // it happens while encoding a packet, not while opening the window, so the window looks healthy right up to
    // the moment the stack is synced.
    private static boolean roundTripsStorage(ArtifactStorageComponent component, RegistryAccess registries) {
        DataComponentType<ArtifactStorageComponent> type = MxtDataComponents.ARTIFACT_STORAGE.get();
        // The connection type only tells NeoForge what the other end is; NEOFORGE is what this server's own
        // client is, which is who the codec under test would be encoding for.
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.NEOFORGE);
        try {
            type.streamCodec().encode(buffer, component);
            ArtifactStorageComponent decoded = type.streamCodec().decode(buffer);
            return decoded.contents().size() == component.contents().size()
                    && decoded.get(0).isEmpty()
                    && decoded.get(1).is(Items.STONE)
                    && decoded.get(1).getCount() == 3;
        } catch (RuntimeException error) {
            return false;
        }
    }

    // Sorted, for a probe line that has to show which elements an item was read as rather than only how many.
    private static List<String> elementIds(Set<Holder<Element>> elements) {
        return elements.stream().map(HolderHelper::id).map(Object::toString).sorted().toList();
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

    // Exercises the secret realm machine end to end without a player. An instance is a dimension, so every leg
    // drives the registry and the generation service directly and then inspects the world that came out: its
    // border, its placed structure, its landing spot, the instance cap and the claim rules.
    private static int probeSecretRealm(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ServerLevel overworld = server.overworld();
        Holder<SecretRealm> trial = require(MxtResourceKeys.SECRET_REALM, TRIAL_REALM);
        Holder<SecretRealm> mirror = require(MxtResourceKeys.SECRET_REALM, id("mirror_realm"));
        Holder<SecretRealm> existing = require(MxtResourceKeys.SECRET_REALM, id("existing_realm"));
        Holder<SecretRealm> absentStructure = require(MxtResourceKeys.SECRET_REALM, id("missing_structure_realm"));
        Holder<SecretRealm> absentTemplate = require(MxtResourceKeys.SECRET_REALM, id("template_realm"));
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
            SecretRealmRecord first = openInstance(server, trial, 0, 12345L, opened);
            ServerLevel dimension = first == null ? null : server.getLevel(first.dimension());
            ok &= check(source, "secret realm probe: tower_saved=" + towerSaved + " dimension=" + (first != null)
                    + " level=" + (dimension != null), towerSaved && first != null && dimension != null);
            if (dimension == null) return 0;

            // 2. The declared border lands on the instance dimension, which also carries the instance seed.
            WorldBorder border = dimension.getWorldBorder();
            ok &= check(source, "secret realm probe: border size=" + border.getSize() + " center=(" + border.getCenterX()
                            + "," + border.getCenterZ() + ")",
                    close(border.getSize(), 128.0D) && close(border.getCenterX(), 0.0D) && close(border.getCenterZ(), 0.0D));
            ok &= check(source, "secret realm probe: seed=" + dimension.getSeed(), dimension.getSeed() == 12345L);

            // 3. Structures are placed before the landing is chosen, so an arrival can stand on them.
            ok &= check(source, "secret realm probe: structure block=" + dimension.getBlockState(new BlockPos(8, 64, 8)).getBlock(),
                    dimension.getBlockState(new BlockPos(8, 64, 8)).is(Blocks.GOLD_BLOCK));

            // 4. A fixed entry point becomes the instance anchor.
            Vec3 anchor = first.anchor().orElse(null);
            ok &= check(source, "secret realm probe: anchor=" + anchor + " prepared=" + first.prepared(),
                    anchor != null && close(anchor.x, 0.5D) && close(anchor.y, 65.0D) && close(anchor.z, 0.5D) && first.prepared());

            // 5. The instance cap counts every instance of the definition.
            SecretRealmRecord second = openInstance(server, trial, 1, 999L, opened);
            int count = SecretRealmRegistry.of(trial).size();
            ok &= check(source, "secret realm probe: instances=" + count + " cap=" + trial.value().maxInstances(),
                    second != null && count >= trial.value().maxInstances());

            // 6. Membership is capped per instance and a full instance is not joinable.
            List<UUID> two = List.of(UUID.randomUUID(), UUID.randomUUID());
            SecretRealmRecord full = second.with(two);
            SecretRealmRegistry.replace(full);
            SecretRealmRegistry.at(first.dimension()).ifPresent(record -> SecretRealmRegistry.replace(record.with(two)));
            boolean joinable = SecretRealmRegistry.joinable(trial, UUID.randomUUID()).isPresent();
            ok &= check(source, "secret realm probe: full=" + full.full() + " joinable=" + joinable, full.full() && !joinable);

            // 7. A claim is recorded, and the condition tells owner from guest.
            LivingEntity probe = spawnProbe(overworld, overworld.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.ZERO).above(2), PROBE_FIRE_ROOT);
            probes.add(probe);
            boolean ownerSeen = false, guestSeen = true, foreignSeen = true;
            if (probe != null) {
                SecretRealmRegistry.replace(first.withOwner(probe.getUUID()).with(List.of(probe.getUUID())));
                ownerSeen = new InSecretRealmEntityCondition(Optional.of(Either.left(trial)), Role.OWNER).test(probe, FormulaContext.of(probe));
                guestSeen = new InSecretRealmEntityCondition(Optional.of(Either.left(trial)), Role.GUEST).test(probe, FormulaContext.of(probe));
                foreignSeen = new InSecretRealmEntityCondition(Optional.of(Either.left(mirror)), Role.ANY).test(probe, FormulaContext.of(probe));
            }
            ok &= check(source, "secret realm probe: role owner=" + ownerSeen + " guest=" + guestSeen + " foreign=" + foreignSeen,
                    probe != null && ownerSeen && !guestSeen && !foreignSeen);

            // 8. A secret realm dimension is reachable by its definition id, a stem realm also by its stem.
            List<Identifier> aliases = SecretRealmRegistry.aliases(first.dimension().identifier()).toList();
            ok &= check(source, "secret realm probe: aliases=" + aliases, aliases.contains(TRIAL_REALM));

            // 9. A weighted entry list picks by weight: the zero-weight fixed point is never chosen, so a random
            //    landing inside random_radius is what the anchor has to be.
            SecretRealmRecord mirrorRecord = openInstance(server, mirror, 0, 777L, opened);
            Vec3 mirrorAnchor = mirrorRecord == null ? null : mirrorRecord.anchor().orElse(null);
            ServerLevel mirrorLevel = mirrorRecord == null ? null : server.getLevel(mirrorRecord.dimension());
            boolean inRadius = mirrorAnchor != null && Math.hypot(mirrorAnchor.x, mirrorAnchor.z) <= 16.0D + 1.0E-6D;
            boolean skipped = mirrorLevel != null && !mirrorLevel.getBlockState(new BlockPos(0, 100, 0)).is(Blocks.GOLD_BLOCK);
            ok &= check(source, "secret realm probe: random anchor=" + mirrorAnchor + " chance_skipped=" + skipped, inRadius && skipped);
            ok &= check(source, "secret realm probe: stem alias=" + (mirrorRecord == null ? "-"
                            : SecretRealmRegistry.aliases(mirrorRecord.dimension().identifier()).toList()),
                    mirrorRecord != null && SecretRealmRegistry.aliases(mirrorRecord.dimension().identifier())
                            .anyMatch(alias -> alias.equals(Identifier.fromNamespaceAndPath("minecraft", "the_end"))));

            // 10. An existing secret realm creates nothing and reuses the dimension it names.
            SecretRealmRecord existingRecord = openInstance(server, existing, 0, 0L, opened);
            ok &= check(source, "secret realm probe: existing=" + (existingRecord == null ? "-" : existingRecord.dimension().identifier()),
                    existingRecord != null && existingRecord.dimension().equals(Level.OVERWORLD)
                            && server.getLevel(existingRecord.dimension()) == overworld);

            // 11. A definition that names a template or a structure that does not exist creates nothing.
            boolean resolvable = SecretRealmStructurePlacer.resolvable(server.getStructureManager(), absentStructure.value());
            SecretRealmRecord templateAttempt = planned(absentTemplate, 0, 0L);
            boolean templateFailed = SecretRealmService.open(server, templateAttempt).isEmpty();
            ok &= check(source, "secret realm probe: structure_resolvable=" + resolvable + " template_failed=" + templateFailed
                            + " leftover=" + SecretRealmRegistry.at(templateAttempt.dimension()).isPresent(),
                    !resolvable && templateFailed && SecretRealmRegistry.at(templateAttempt.dimension()).isEmpty());

            // 12. An unclaimed instance dies with its clock: the record goes and the dimension is released.
            boolean expired = mirrorRecord != null && SecretRealmService.expire(server, mirrorRecord, server.overworld().getGameTime() + 100L);
            boolean released = mirrorRecord != null && SecretRealmRegistry.at(mirrorRecord.dimension()).isEmpty()
                    && server.getLevel(mirrorRecord.dimension()) == null;
            ok &= check(source, "secret realm probe: expired=" + expired + " released=" + released, expired && released);

            // 13. A claimed secret realm survives its visitors: the policy is what decides keep versus delete.
            SecretRealmRecord claimed = SecretRealmRegistry.at(first.dimension()).orElse(null);
            SecretRealmRecord idled = claimed == null ? null : claimed.idle();
            ok &= check(source, "secret realm probe: persists=" + (claimed != null && claimed.persists())
                            + " idle_members=" + (idled == null ? -1 : idled.members().size()),
                    claimed != null && claimed.persists() && idled.empty() && probe != null && idled.isOwner(probe.getUUID()));

            // 14. A traveller that is not a player enters and leaves through the same calls: the record, the
            //     membership row and the teleport are keyed on the entity, not on a connection.
            LivingEntity mobTraveller = spawnProbe(overworld, overworld.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.ZERO).above(4), null);
            probes.add(mobTraveller);
            SecretRealmService.Result mobEntered = mobTraveller == null ? null : SecretRealmService.enter(mobTraveller, mirror);
            SecretRealmRecord mobHome = mobTraveller == null ? null : SecretRealmRegistry.ofMember(mobTraveller.getUUID()).orElse(null);
            if (mobHome != null) opened.add(mobHome.dimension());
            boolean mobIn = mobTraveller != null && mobEntered != null && mobEntered.changed() && mobHome != null
                    && mobTraveller.level().dimension().equals(mobHome.dimension())
                    && mobTraveller.getData(MxtAttachments.SECRET_REALM_TRAVEL).active();
            boolean mobOut = mobIn && SecretRealmService.exit(mobTraveller).changed()
                    && mobTraveller.level().dimension().equals(Level.OVERWORLD)
                    && !mobTraveller.getData(MxtAttachments.SECRET_REALM_TRAVEL).active()
                    && SecretRealmRegistry.ofMember(mobTraveller.getUUID()).isEmpty();
            ok &= check(source, "secret realm probe: mob_entered=" + mobIn + " mob_returned=" + mobOut, mobIn && mobOut);
        } finally {
            for (LivingEntity probe : probes) if (probe != null) probe.discard();
            for (ResourceKey<Level> key : opened) {
                SecretRealmRegistry.at(key).ifPresent(record -> SecretRealmService.destroy(server, record));
            }
        }
        if (ok) {
            source.sendSuccess(() -> Component.literal("secret realm probe: OK"), false);
            return 1;
        }
        source.sendFailure(Component.literal("secret realm probe: MISMATCH"));
        return 0;
    }

    // Leaves one claimed instance behind instead of cleaning up, so a restart can be checked for keeping it.
    // The secret realm probe itself destroys everything it opens.
    private static int keepSecretRealm(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        Holder<SecretRealm> trial = require(MxtResourceKeys.SECRET_REALM, TRIAL_REALM);
        SecretRealmRecord record = SecretRealmService.open(server, planned(trial, 0, 4242L)).orElse(null);
        if (record == null) {
            source.sendFailure(Component.literal("secret realm keep: could not open the instance"));
            return 0;
        }
        UUID owner = UUID.randomUUID();
        SecretRealmRegistry.replace(record.withOwner(owner));
        source.sendSuccess(() -> Component.literal("secret realm keep: " + record.dimension().identifier() + " owner=" + owner), false);
        return 1;
    }

    // Reopens a dormant instance the way an entry does, to prove a claimed secret realm comes back with the terrain it
    // had instead of being generated and furnished again.
    private static int reopenSecretRealm(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        Holder<SecretRealm> trial = require(MxtResourceKeys.SECRET_REALM, TRIAL_REALM);
        SecretRealmRecord dormant = SecretRealmRegistry.of(trial).stream()
                .filter(record -> record.index() == 0).findFirst().orElse(null);
        if (dormant == null) {
            source.sendFailure(Component.literal("secret realm reopen: there is no dormant instance"));
            return 0;
        }
        SecretRealmRecord reopened = SecretRealmService.open(server, dormant.restarted(server.overworld().getGameTime())).orElse(null);
        ServerLevel level = reopened == null ? null : server.getLevel(reopened.dimension());
        boolean kept = level != null && level.getBlockState(new BlockPos(8, 64, 8)).is(Blocks.GOLD_BLOCK);
        boolean prepared = reopened != null && reopened.prepared();
        if (kept && prepared) {
            source.sendSuccess(() -> Component.literal("secret realm reopen: terrain kept=" + true + " prepared=" + true), false);
            return 1;
        }
        source.sendFailure(Component.literal("secret realm reopen: terrain kept=" + kept + " prepared=" + prepared));
        return 0;
    }

    // Exercises the rift's linking rule, the mesh it is drawn from and its portal behaviour without a client.
    // Every leg drives the calls the renderer and the portal use - the point, link and triangle meshes, the 3x3x3
    // neighbour scan, the colour derivation, the stored state, the transition - and then inspects the world that
    // came out. What cannot be checked from a server is the drawing itself.
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

            //    Counting the loops of the whole wall catches a triangle two of its blocks both claim or neither
            //    draws: nine blocks see forty-eight loops, which is sixteen triangles three ways.
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
            //    no direction and no opacity to lose. The metadata-carrying form is used because a static load has
            //    to read the block entity id back out of the tag.
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

    // The closest rift leading to {@code target} within a small box, which is how the arrival service's own
    // "carve one and then find it again" behaviour is observed from the outside.
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

    // A neighbour's centre in {@code self}'s block-local coordinates, which is where the renderer places the
    // links it draws: a neighbour is at most one block away on each axis.
    private static Vec3 local(BlockPos other, BlockPos self) {
        return RiftMesh.CENTRE.add(other.getX() - self.getX(), other.getY() - self.getY(), other.getZ() - self.getZ());
    }

    // The side of a vertex list that is a cube, or -1 when its three extents differ, which is how a mesh claiming
    // to be a point is caught being a flat square instead.
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

    // How far a mesh reaches along an axis, measured from the rift's own centre. A link half must reach exactly
    // as far as the midpoint it meets its neighbour's half at.
    private static double axialReach(List<Vec3> vertices, Vec3 axis) {
        Vec3 unit = axis.normalize();
        double reach = 0.0;
        for (Vec3 vertex : vertices) reach = Math.max(reach, vertex.subtract(RiftMesh.CENTRE).dot(unit));
        return reach;
    }

    // How far a mesh reaches away from an axis, measured from the rift's own centre. For a beam that is the corner
    // of its cross-section, so it checks the thickness the link claims to have.
    private static double radialReach(List<Vec3> vertices, Vec3 axis) {
        Vec3 unit = axis.normalize();
        double reach = 0.0;
        for (Vec3 vertex : vertices) {
            Vec3 relative = vertex.subtract(RiftMesh.CENTRE);
            reach = Math.max(reach, relative.subtract(unit.scale(relative.dot(unit))).length());
        }
        return reach;
    }

    // Whether the block at {@code partner} also sees the loop through {@code self} and {@code other}. All three
    // blocks of a loop draw their own share, so two agreeing would leave a gap and this is how it shows up.
    private static boolean agreesOnLoop(ServerLevel level, BlockPos partner, BlockPos self, BlockPos other) {
        if (!(level.getBlockEntity(partner) instanceof RiftBlockEntity)) return false;
        for (Loop candidate : RiftConnections.loops(partner, RiftConnections.connected(level, partner)))
            if (candidate.forward().equals(self) && candidate.backward().equals(other)
                    || candidate.forward().equals(other) && candidate.backward().equals(self)) return true;
        return false;
    }

    // The distance between the two surfaces of a filled triangle share, which is the thickness the fill claims to
    // have. A share is laid out top face, bottom face and then the rims, so a surface vertex and the one four
    // places after it are the same corner of the triangle above and below.
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

    private static SecretRealmRecord planned(Holder<SecretRealm> definition, int index, long seed) {
        return SecretRealmService.plan(definition, index, seed, 0L);
    }

    private static SecretRealmRecord openInstance(MinecraftServer server, Holder<SecretRealm> definition, int index,
                                            long seed, List<ResourceKey<Level>> opened) {
        SecretRealmRecord record = SecretRealmService.open(server, planned(definition, index, seed)).orElse(null);
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
        give(player, secretRealmToken());
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
        TEST_ACTIVE_ABILITIES.forEach(id -> player.getData(MxtAttachments.ABILITY_HOLDER).grant(id, TEST_ABILITY_SOURCE));
        // Granting an ability does not register its triggers by itself; the runtime index is only rebuilt where
        // the ability sources actually change.
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

    // Prints the character panel's own line model, so what the panel would show is readable from the server
    // instead of a screenshot. It calls the very same InformationManager.collectEntries the screen does.
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

    private static void ensureResource(LivingEntity holder, ResourceHolderAttachment resources, Holder<Resource> resource, double minimum) {
        FormulaContext context = ResourceService.formulaContext(holder, resource, FormulaContext.of(holder));
        ResourceService.initialize(resources, resource, context);
        double missing = minimum - resources.get(resource);
        if (missing > 0.0D) ResourceService.change(resources, resource, missing, context);
    }

    private static ItemStack formationPlate() {
        ItemStack stack = new ItemStack(MxtItems.FORMATION_PLATE.get());
        // An empty allow list, which is the shipped plate: unrestricted unless the server option says otherwise,
        // and therefore bound to whatever the kit's own formation is.
        stack.set(MxtDataComponents.FORMATION_PLATE,
                new FormationPlateComponent(List.of(), Optional.of(require(MxtResourceKeys.FORMATION, FORMATION))));
        return stack;
    }

    private static ItemStack secretRealmToken() {
        ItemStack stack = new ItemStack(MxtItems.SECRET_REALM_TOKEN.get());
        stack.set(MxtDataComponents.SECRET_REALM_TOKEN, new SecretRealmTokenComponent(Optional.of(require(MxtResourceKeys.SECRET_REALM, TRIAL_REALM))));
        return stack;
    }

    private static ItemStack contractScroll() {
        ItemStack stack = new ItemStack(MxtItems.CONTRACT_SCROLL.get());
        stack.set(MxtDataComponents.CONTRACT_SCROLL, new ContractScrollComponent(Optional.of(require(MxtResourceKeys.CONTRACT_TYPE, CONTRACT))));
        return stack;
    }

    // Gives one artifact fixture bound to the player, since the test pack has no other way to refine an item.
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
