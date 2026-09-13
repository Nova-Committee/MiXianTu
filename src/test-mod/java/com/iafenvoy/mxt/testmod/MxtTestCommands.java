package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.registry.*;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.SectAttachment;
import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.Title;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.data.cultivation.CultivationProfile;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.data.item.ContractScrollComponent;
import com.iafenvoy.mxt.data.item.FormationPlateComponent;
import com.iafenvoy.mxt.data.item.RealmTokenComponent;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.runtime.cultivation.CultivationActionService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationGrantService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationProfiles;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueService;
import com.iafenvoy.mxt.runtime.cultivation.TitleService;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.sect.SectService;
import com.iafenvoy.mxt.runtime.sect.SectService.Result;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraResult.SourceKind;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.runtime.world.AuraZonePriorityProbe;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static net.minecraft.commands.Commands.literal;

/**
 * Commands that assemble a playable, development-only Qingxiao Sect scenario.
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
    private static final Identifier TITLE = id("qingxiao_outer_disciple");
    private static final Identifier CULTIVATE = id("qingxiao_meditation");
    private static final Identifier SECT = id("qingxiao_sect");
    private static final Identifier SECT_TASK = id("meditate");
    private static final Identifier FORMATION = id("spirit_gathering");
    private static final Identifier REALM = id("trial_realm");
    private static final Identifier CONTRACT = id("master_servant");
    private static final Identifier TEST_ABILITY_SOURCE = id("grant/test_kit");    private static final List<Identifier> TEST_ACTIVE_ABILITIES = List.of(
            id("firebolt"), id("awaken_divine_sense"), id("expend_test"), id("infuse_true_essence")
    );

    private MxtTestCommands() {
    }

    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(literal("mxt_test")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(literal("kit").executes(context -> giveKit(context.getSource())))
                .then(literal("cultivate").executes(context -> startCultivation(context.getSource())))
                .then(literal("task").executes(context -> completeSectTask(context.getSource())))
                .then(literal("verify").executes(context -> verify(context.getSource())))
                .then(literal("guide").executes(context -> showGuide(context.getSource()))));
    }

    /**
     * Re-checks the two behaviours that have no other observable entry point: the aura zone
     * priority selection and the channelled ability upkeep pulse.
     */
    private static int verify(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        int checked = 0;
        String auraFailure = verifyAuraPriority(player);
        if (auraFailure != null) {
            source.sendFailure(Component.translatable("command.mxt_test.verify.aura_failed", auraFailure));
        } else {
            source.sendSuccess(() -> Component.translatable("command.mxt_test.verify.aura_ok"), false);
            checked++;
        }
        String channelFailure = verifyChannel(player);
        if (channelFailure != null) {
            source.sendFailure(Component.translatable("command.mxt_test.verify.channel_failed", channelFailure));
        } else {
            source.sendSuccess(() -> Component.translatable("command.mxt_test.verify.channel_ok"), false);
            checked++;
        }
        return checked;
    }

    /**
     * Asserts the documented rule: the highest priority wins inside a tier, the registry ID
     * breaks ties, and a dimension binding is never outranked by a biome binding.
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
     * Asserts that a channelled child of a composite ability starts a channel and applies its
     * effect on every due upkeep pulse.
     */
    private static String verifyChannel(ServerPlayer player) {
        return ChannelProbe.verify(player);
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
        joinSect(player);

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
        HolderLookup<CultivationTechnique> technique = new HolderLookup<>(MxtResourceKeys.CULTIVATION_TECHNIQUE, TECHNIQUE);
        HolderLookup<Title> title = new HolderLookup<>(MxtResourceKeys.TITLE, TITLE);
        CultivationIdentityService.grantSpiritRoot(player, ROOT, root.value());
        CultivationIdentityService.grantSpiritRoot(player, WATER_ROOT, waterRoot.value());
        CultivationIdentityService.grantPhysique(player, PHYSIQUE, physique.value(), context);
        TechniqueService.learn(player, spirit, technique.holder(), context);
        TitleService.grant(player, spirit, TITLE, title.value(), ignored -> null, context);
        spirit.addLearnedTechnique(technique.holder());
        CultivationGrantService.recalculate(spirit, player.getData(MxtAttachments.ABILITY_HOLDER));
        TEST_ACTIVE_ABILITIES.forEach(id -> MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, id)
                .ifPresent(ability -> player.getData(MxtAttachments.ABILITY_HOLDER).grant(ability, TEST_ABILITY_SOURCE)));
    }

    private static int startCultivation(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        CultivateAction action = require(MxtResourceKeys.CULTIVATE_ACTION, CULTIVATE).value();
        CultivationActionService.Result result = CultivationActionService.start(player, player.getData(MxtAttachments.CULTIVATION),
                CULTIVATE, action, player.level().getGameTime(), FormulaContext.of(player));
        if (!result.started()) {
            source.sendFailure(Component.translatable("command.mxt_test.cultivate.failed", result.failure().name()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt_test.cultivate.success"), true);
        return 1;
    }

    private static int completeSectTask(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        SectAttachment data = player.getData(MxtAttachments.SECT);
        Result result = SectService.completeTask(data, require(MxtResourceKeys.SECT, SECT), SECT_TASK);
        if (!result.changed()) {
            source.sendFailure(Component.translatable("command.mxt_test.task.failed", result.failure().name()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt_test.task.success", data.contribution()), true);
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

    private static void joinSect(ServerPlayer player) {
        SectAttachment data = player.getData(MxtAttachments.SECT);
        if (data.member()) return;
        SectService.join(data, require(MxtResourceKeys.SECT, SECT));
    }

    private static ItemStack formationPlate() {
        ItemStack stack = new ItemStack(MxtItems.FORMATION_PLATE.get());
        stack.set(MxtDataComponents.FORMATION_PLATE, new FormationPlateComponent(Optional.of(require(MxtResourceKeys.FORMATION, FORMATION))));
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

    private static Reference<CultivationProfile> requireProfile(Identifier resource) {
        return CultivationProfiles.holderServer(resource)
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
