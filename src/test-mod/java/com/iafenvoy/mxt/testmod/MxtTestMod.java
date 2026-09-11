package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.data.aura.AuraMaximum.Fixed;
import com.iafenvoy.mxt.data.aura.AuraMaximum.InitialMultiplier;
import com.iafenvoy.mxt.data.aura.AuraMaximum.Unlimited;
import com.iafenvoy.mxt.data.aura.AuraZone.Distribution;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext.Layout;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.aura.BlockAura;
import com.iafenvoy.mxt.data.aura.ItemAura;
import com.iafenvoy.mxt.data.aura.ItemAuraComponent;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.data.aura.AuraValue;
import com.iafenvoy.mxt.data.condition.builtin.entity.AuraRangeEntityCondition;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resource.ResourceBar;
import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.action.builtin.entity.GrantSpiritRootAction;
import com.iafenvoy.mxt.data.action.builtin.entity.GrantPhysiqueAction;
import com.iafenvoy.mxt.data.action.builtin.entity.RemovePhysiqueAction;
import com.iafenvoy.mxt.data.action.builtin.entity.RemoveSpiritRootAction;
import com.iafenvoy.mxt.data.condition.builtin.entity.HasPhysiqueEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.HasSpiritRootEntityCondition;
import com.iafenvoy.mxt.data.item.WeaponBinding;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.ItemQualityTags;
import com.iafenvoy.mxt.data.artifact.ForgingResultComponent;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMaterial;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.runtime.forging.ForgingPlan;
import com.iafenvoy.mxt.runtime.forging.ForgingSession;
import com.iafenvoy.mxt.runtime.forging.ForgingTableState;
import com.iafenvoy.mxt.screen.menu.ForgingMenu;
import com.iafenvoy.mxt.screen.menu.ForgingMenuProbe;
import com.iafenvoy.mxt.data.resource.ResourceBar.Anchor;
import com.iafenvoy.mxt.data.resource.ResourceBar.ValueDisplay;
import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.OriginsRenderData;
import com.iafenvoy.mxt.data.resourcebar.builtin.context.ActualConcentrationContext;
import com.iafenvoy.mxt.data.resourcebar.builtin.visibility.NonZeroVisibility;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.ability.AbilityService.PrepareResult;
import com.iafenvoy.mxt.runtime.cultivation.CultivationActionService.Result;
import com.iafenvoy.mxt.runtime.formation.FormationService.ActivateResult;
import com.iafenvoy.mxt.runtime.formation.FormationStructureValidator;
import com.iafenvoy.mxt.runtime.formation.FormationService;
import com.iafenvoy.mxt.runtime.forging.ForgingProbe;
import com.iafenvoy.mxt.runtime.forging.ForgingSurface;
import com.iafenvoy.mxt.runtime.forging.ForgingWorkstationService;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbService;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.runtime.economy.CurrencyValueService;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.runtime.cultivation.CultivationActionService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationModeService;
import com.iafenvoy.mxt.runtime.cultivation.AuraDistributionService;
import com.iafenvoy.mxt.runtime.cultivation.ItemAuraService;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraResult.SourceKind;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.runtime.world.AuraZonePriorityProbe;
import com.iafenvoy.mxt.runtime.world.BlockAuraContribution;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resource.ResourceService.Bounds;
import com.iafenvoy.mxt.runtime.spirit.SpiritItemAccess;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.InventoryUtil;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.util.formula.number.Expression;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder.Reference;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Development-only mod that contributes the mxt_test datapack and client resources. */
@Mod(MxtTestMod.MOD_ID)
public final class MxtTestMod {
    public static final String MOD_ID = "mxt_test";
    private static final Logger LOGGER = LogUtils.getLogger();

    public MxtTestMod(IEventBus modBus) {
        MxtTestItems.REGISTRY.register(modBus);
        MxtTestForgeItems.REGISTRY.register(modBus);
        NeoForge.EVENT_BUS.addListener(MxtTestMod::verifyItemBindings);
        NeoForge.EVENT_BUS.addListener(MxtTestMod::grantTestAbilities);
        NeoForge.EVENT_BUS.addListener(MxtTestCommands::registerCommands);
        LOGGER.info("Loaded MiXianTu test mod");
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
        verifyDynamicResourceValues(foundation, coreForming);
        verifyEnergyCosts();
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
        Holder<CultivateAction> qingxiaoMeditation = requireHolder(MxtResourceKeys.CULTIVATE_ACTION, Identifier.parse("mxt_test:qingxiao_meditation"));
        if (!qingxiaoMeditation.value().defaultAction()
                || !same(qingxiaoMeditation.value().absorbAmount().evaluate(FormulaContext.EMPTY), 2.0D)) {
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
        verifyFormationTemplate(event);
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
        verifyChannelAbility(event);
        verifyWeaponAttributeMerge(event);
        verifyInventoryUtilAtomicity();
        verifyForgingMaterialMatching();
        verifyForgingStepLimit();
        verifyForgingAssets();
        verifyForgingBindingsLoaded();
        verifyForgingMethodIntersection(event.getServer().registryAccess());
        verifyForgingPlans(event.getServer().registryAccess());
        verifyForgingStepRows(event.getServer().registryAccess());
        verifyForgingSuffixWindow();
        verifyForgingSessionRoundTrip();
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

        // The blueprint tooltip prints one line per entry from availableCount, and the button is gated by
        // materialsCovered - two helpers answering the same question from different directions. If they
        // ever disagreed, the tooltip would tick every line while the button stayed dark, so they are
        // compared here on a container that covers the list exactly and on one that falls short.
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
     * Every binding the test items name must actually resolve.
     *
     * <p>The items declare their binding as a key rather than a resolved holder - items are built
     * before the datapack registries load, so that is the only way - and a key that names nothing is
     * silent: the component simply reads as absent, the table's slot refuses the item, and it looks
     * like the slot filter is broken rather than like a typo in a file name. Resolving each one here
     * turns that into a startup failure.
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
     * The method list is the blueprint's {@code allowed_methods} intersected with the tools', and
     * declaring nothing - or declaring an empty list - restricts nothing.
     *
     * <p>Every way of writing the field is exercised here, because two of them fail quietly. An
     * {@code allowed_methods} that names a tag nobody loads decodes to an empty set; an item whose
     * {@code delayedHolderComponent} never resolved carries no binding at all and simply cannot be
     * placed. Both look like "the slot filter is broken" from in game, so they are pinned here.</p>
     *
     * <p>The counts are the test datapacks' own: master unlocks ten, smith five, crude two; iron_sword
     * lists all ten, and pickaxe declares the three-member tag {@code #mxt_test:pickaxe_methods}.</p>
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
     * Every test blueprint must be able to build a plan, and that plan must be able to reach its target.
     *
     * <p>A plan is built when a session starts, not when the datapack loads, so a blueprint whose target
     * band cannot be reached - or whose {@code finish_pattern} names a method its {@code allowed_methods}
     * excludes - only fails once a player has put their materials in and pressed the button. Building each
     * one here moves that to startup, where it is a crash instead of a lost afternoon.</p>
     *
     * <p>That membership check is the one the decode-time validator cannot do: a tag's members are not
     * known while the entry is being decoded, so it lives in the plan constructor, and this is what
     * exercises it.</p>
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
     * The 要求 row must show the <em>last</em> {@code required} steps of the finish pattern.
     *
     * <p>The server compares the session's last steps against {@code pattern[6 - required .. 5]}. A row
     * built from the pattern's first {@code required} entries fills exactly the same six cells, so it looks
     * right while naming a sequence the server never asks for - and the only symptom is a player who
     * follows the row and is refused. That is why this is pinned rather than eyeballed.</p>
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
     * Only the <em>last</em> {@code required_suffix_steps} history entries are checked.
     *
     * <p>The pattern is always six long and the requirement may be shorter, so the leading entries are the
     * ones the 要求 row draws as barriers. They must take no part in the rule - and that is only observable
     * with a pattern whose two halves differ, which is why this builds one instead of reading the test
     * datapack.</p>
     *
     * <p>The plan is hand-built for the same reason: methods only have to be ids and deltas to the rule,
     * and a plan of my own lets the value land exactly on the target with a history I chose.</p>
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

        // The last three are the pattern's *first* three. Same value, so if the rule read the wrong end - or
        // counted the barriers as positions that have to match something - this is the case that slips
        // through.
        ForgingSession reversed = new ForgingSession(plan);
        for (Identifier method : List.of(lead, b, c, a)) reversed.strike(method);
        if (reversed.canComplete())
            throw new IllegalStateException("Forging audit found the pattern's first three entries being checked as the suffix");
    }

    /**
     * A session must answer the same way before and after a save/reload round trip, and a method the plan
     * does not list must be a refusal rather than an error.
     *
     * <p>Both are about the split between the two classes. The session persists only its progress, so the
     * shortest run and the whole rule set have to come back from the plan beside it - a session that stored
     * its own {@code optimal_steps} could be restored disagreeing with the plan it was restored against, and
     * nothing else in the audit would notice. The round trip is therefore compared on the answers, not just
     * on the numbers: the same strike has to be accepted and the same completion answer given.</p>
     *
     * <p>The unlisted method is the other half: the client offers whatever the placed tools resolve to, and
     * the session is reached through layers that can each see a slightly older plan, so asking about a
     * method the plan does not list has to be an ordinary <em>no</em>. It used to be raised and caught.</p>
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
     * Ending a session must unlock the table - by any route, and in both directions.
     *
     * <p>Placement and pickup both end up in {@link ForgingSurface}, and what it asks about a busy table comes
     * from the session state. That is what makes the two symptoms a stuck table shows - it refuses materials,
     * and it will not give the blueprint back - testable without a player, as the four calls below.</p>
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
     * A weapon binding must add its own attribute modifiers on top of the item's vanilla ones
     * instead of replacing the component. The check drives the real entity-tick refresh on a
     * temporary entity holding a bound diamond sword and asserts that the vanilla +7 attack damage
     * modifier survives next to the binding's contribution.
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
                .filter(entry -> entry.attribute().is(Attributes.ATTACK_DAMAGE))
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
     * Runs the channelled ability assertion on a temporary entity. The lifecycle only needs a
     * {@link LivingEntity}, so spawning one keeps the check runnable on a dedicated server without
     * a connected player. The entity is discarded immediately afterwards.
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
     * Proves the documented aura tier rule with the test datapack content. The biome tier holds the
     * highest priority in the whole registry (150) while the highest dimension priority is 100, so a
     * dimension result proves the dimension tier is compared before any biome priority. Same-tier
     * ordering is then taken from the production ordering helper, so the check also fails if the
     * priority-then-ID rule changes.
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

    private static void verifyDynamicResourceValues(Identifier foundation, Identifier coreForming) {
        Identifier qiId = Identifier.parse("mxt_test:qi");
        Identifier spiritPowerId = Identifier.parse("mxt_test:spirit_power");
        Holder<Resource> qi = requireHolder(MxtResourceKeys.RESOURCE, qiId);
        Holder<Resource> spiritPower = requireHolder(MxtResourceKeys.RESOURCE, spiritPowerId);
        if (!same(qi.value().cultivationToResource().multiplier().evaluate(FormulaContext.EMPTY), 0.25D)
                || !same(qi.value().cultivationToResource().maxPerTick().evaluate(FormulaContext.EMPTY), 0.5D)
                || !same(qi.value().resourceToCultivation().multiplier().evaluate(FormulaContext.EMPTY), 0.5D)
                || !same(qi.value().resourceToCultivation().maxPerTick().evaluate(FormulaContext.EMPTY), 0.75D)
                || !same(spiritPower.value().cultivationToResource().multiplier().evaluate(FormulaContext.EMPTY), 1.0D)
                || !same(spiritPower.value().cultivationToResource().maxPerTick().evaluate(FormulaContext.EMPTY), 1.0D)
                || !same(spiritPower.value().resourceToCultivation().multiplier().evaluate(FormulaContext.EMPTY), 1.0D)
                || !same(spiritPower.value().resourceToCultivation().maxPerTick().evaluate(FormulaContext.EMPTY), 1.0D)
                || !same(spiritPower.value().burstAmount().evaluate(FormulaContext.EMPTY), 10.0D)
                || !same(qi.value().startExp().evaluate(FormulaContext.EMPTY), 100.0D)
                || !same(spiritPower.value().startExp().evaluate(FormulaContext.EMPTY), 10.0D)
                || !same(spiritPower.value().max().evaluate(FormulaContext.EMPTY), 0.0D)
                || spiritPower.value().particleColor() != 0x66CCFF) {
            throw new IllegalStateException("Resource cultivation conversion settings were not decoded correctly");
        }
        CultivationAttachment spirit = new CultivationAttachment();
        CultivationAttachment mortal = new CultivationAttachment();
        if (!same(CultivationService.addProgress(mortal, qi, 120.0D, FormulaContext.EMPTY), 100.0D)
                || !same(CultivationService.addProgress(mortal, qi, 1.0D, FormulaContext.EMPTY), 0.0D)
                || !same(mortal.cultivationProgress(qi), 100.0D)) {
            throw new IllegalStateException("Mortal cultivation progress must stop at resource start_exp");
        }
        spirit.setRealmStage(requireHolder(MxtResourceKeys.REALM_STAGE, foundation));
        spirit.setCultivationProgress(qi, 40.0D);
        Holder<RealmStage> spiritPowerRealm = requireHolder(MxtResourceKeys.REALM_STAGE,
                Identifier.parse("mxt_test:spirit_power_refining"));
        CultivationAttachment multiChain = new CultivationAttachment();
        Holder<RealmStage> foundationHolder = requireHolder(MxtResourceKeys.REALM_STAGE, foundation);
        multiChain.setRealmStages(Map.of(foundationHolder.value().resource(), foundationHolder,
                spiritPowerRealm.value().resource(), spiritPowerRealm));
        multiChain.setCultivationProgress(qi, 12.0D);
        multiChain.setCultivationProgress(spiritPower, 7.0D);
        if (multiChain.realmStages().size() != 2 || !same(multiChain.cultivationProgress(qi), 12.0D)
                || !same(multiChain.cultivationProgress(spiritPower), 7.0D)) {
            throw new IllegalStateException("Multiple realm chains must retain independent resources and progress");
        }
        if (spirit.realmStage(spiritPower) != null) {
            throw new IllegalStateException("A resource without a realm must resolve to null");
        }
        FormulaContext foundationContext = ResourceService.formulaContext(spirit, qi, FormulaContext.EMPTY);
        Bounds foundationBounds = ResourceService.resolveBounds(qi.value(), foundationContext)
                .orElseThrow(() -> new IllegalStateException("Foundation qi bounds were invalid"));
        if (!same(foundationBounds.max(), 170.0D) || !same(qi.value().regen().evaluate(foundationContext), 0.35D)) {
            throw new IllegalStateException("Qi maximum and regeneration did not use foundation absorbed aura");
        }
        ResourceHolderAttachment holder = new ResourceHolderAttachment();
        ResourceService.initialize(holder, qi, foundationContext);
        ResourceService.regenerate(holder, qi, 4L, foundationContext);
        if (!same(holder.get(qi), 1.4D)) {
            throw new IllegalStateException("Qi regeneration did not use its dynamic formula");
        }
        spirit.setRealmStage(requireHolder(MxtResourceKeys.REALM_STAGE, coreForming));
        FormulaContext coreContext = ResourceService.formulaContext(spirit, qi, FormulaContext.EMPTY);
        Bounds coreBounds = ResourceService.resolveBounds(qi.value(), coreContext)
                .orElseThrow(() -> new IllegalStateException("Core-forming qi bounds were invalid"));
        if (!same(coreBounds.max(), 220.0D) || !same(qi.value().regen().evaluate(coreContext), 0.45D)) {
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

        Identifier meditationId = Identifier.parse("mxt_test:fire_meditation");
        CultivateAction meditation = MxtDatapackRegistries.get(MxtResourceKeys.CULTIVATE_ACTION, meditationId)
                .orElseThrow(() -> new IllegalStateException("Cultivation restoration test action was not loaded"));
        CultivationAttachment absorbingSpirit = new CultivationAttachment();
        absorbingSpirit.setRealmStage(requireHolder(MxtResourceKeys.REALM_STAGE, foundation));
        ResourceHolderAttachment absorbingResources = new ResourceHolderAttachment();
        absorbingResources.set(spiritPower, 5.0D);
        AuraChunkAttachment absorbingAura = new AuraChunkAttachment();
        Holder<Resource> fire = requireHolder(MxtResourceKeys.RESOURCE, Identifier.parse("mxt_test:spirit_power"));
        absorbingAura.initializeAuras(Map.of(fire, new AuraPool(10.0D, 10.0D, 0.0D)), List.of(Identifier.parse("mxt_test:aura_kind/fire")));
        if (!CultivationActionService.start(absorbingSpirit, meditationId, meditation, 0L, () -> true).started()) {
            throw new IllegalStateException("Cultivation restoration test action did not start");
        }
        Result absorbed = CultivationActionService.tick(absorbingSpirit, absorbingResources,
                absorbingAura, meditationId, meditation, 0L, FormulaContext.EMPTY, () -> true);
        if (!absorbed.progressed() || !same(absorbingSpirit.cultivationProgress(qi), 0.375D)
                || !same(absorbingResources.get(qi), 2.25D) || !same(absorbingResources.get(spiritPower), 4.0D)) {
            throw new IllegalStateException("Cultivation did not apply source-side resource conversion limits correctly");
        }
        Result convertedWhileWaiting = CultivationActionService.tick(absorbingSpirit, absorbingResources,
                absorbingAura, meditationId, meditation, 1L, FormulaContext.EMPTY, () -> true);
        if (!convertedWhileWaiting.waiting() || !same(absorbingSpirit.cultivationProgress(qi), 0.75D)
                || !same(absorbingResources.get(qi), 1.5D) || !same(absorbingAura.auras().get(fire).amount(), 9.0D)) {
            throw new IllegalStateException("Cultivation conversion did not use its source-side limit every game tick");
        }
    }

    private static void verifyEnergyCosts() {
        if (MiscCodecs.COLOR.parse(JsonOps.INSTANCE, JsonParser.parseString("\"#FFFFFFFF\""))
                .result().filter(value -> value == -1).isEmpty()
                || MiscCodecs.COLOR.parse(JsonOps.INSTANCE, JsonParser.parseString("4294967295"))
                .result().filter(value -> value == -1).isEmpty()
                || MiscCodecs.COLOR_NO_ALPHA.parse(JsonOps.INSTANCE, JsonParser.parseString("\"#66CCFF\""))
                .result().filter(value -> value == 0x66CCFF).isEmpty()
                || MiscCodecs.COLOR_NO_ALPHA.parse(JsonOps.INSTANCE, JsonParser.parseString("\"66CCFF\""))
                .result().isPresent()
                || MiscCodecs.COLOR_NO_ALPHA.parse(JsonOps.INSTANCE, JsonParser.parseString("\"#GGGGGG\""))
                .result().isPresent()) {
            throw new IllegalStateException("Color codecs must validate hexadecimal values and preserve unsigned int bits");
        }
        if (ItemAuraComponent.CODEC.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createDouble(3.5D))
                .result().map(ItemAuraComponent::remain).filter(value -> same(value, 3.5D)).isEmpty()
                || ItemAuraComponent.CODEC.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createDouble(-1.0D)).result().isPresent()
                || ItemAuraComponent.CODEC.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createDouble(Double.NaN)).result().isPresent()) {
            throw new IllegalStateException("Item-aura component codec must only accept finite non-negative remainders");
        }
        if (NumberProvider.FINITE_DOUBLE_CODEC.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createDouble(Double.NaN)).result().isPresent()
                || NumberProvider.FINITE_DOUBLE_CODEC.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createDouble(Double.POSITIVE_INFINITY)).result().isPresent()) {
            throw new IllegalStateException("Number-provider codecs must reject non-finite values while loading");
        }
        if (!same(new Expression("1 / 0").evaluate(FormulaContext.EMPTY), 0.0D)) {
            throw new IllegalStateException("Non-finite runtime formula results must fall back to zero");
        }
        if (!same(new Expression("round(1.7) + clamp(4, 0, 3) + zero").evaluate(FormulaContext.EMPTY), 5.0D)) {
            throw new IllegalStateException("Intrinsic formula functions or variables were not available to expressions");
        }
        if (!same(new Expression("damage * 2", Map.of("damage", new Constant(3.0D)))
                .evaluate(FormulaContext.EMPTY.with("damage", 10.0D)), 6.0D)) {
            throw new IllegalStateException("Expression parameters did not override the formula context");
        }
        NumberProvider parameterizedExpression = NumberProvider.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"type":"mxt:expression","expression":"damage * 2","params":{"damage":"1 + level"}}
                """)).getOrThrow();
        if (!same(parameterizedExpression.evaluate(FormulaContext.EMPTY.with("damage", 10.0D).with("level", 2.0D)), 6.0D)) {
            throw new IllegalStateException("Expression parameter codecs did not decode or override the formula context");
        }
        Holder<Resource> spiritPower = requireHolder(MxtResourceKeys.RESOURCE, Identifier.parse("mxt_test:spirit_power"));
        Holder<Resource> soulPower = requireHolder(MxtResourceKeys.RESOURCE, Identifier.parse("mxt_test:soul_power"));
        Identifier firebolt = Identifier.parse("mxt_test:firebolt");
        Holder<Ability> ability = requireHolder(MxtResourceKeys.ABILITY, firebolt);
        if (!same(ability.value().castTime().evaluate(FormulaContext.EMPTY), 0.0D)) {
            throw new IllegalStateException("Inline structured number providers did not evaluate correctly");
        }
        AbilityAttachment abilities = new AbilityAttachment();
        abilities.grant(ability, Identifier.fromNamespaceAndPath(MOD_ID, "test"));
        ResourceHolderAttachment abilityResources = new ResourceHolderAttachment();
        abilityResources.set(spiritPower, 20.0D);
        abilityResources.set(soulPower, 3.0D);
        PrepareResult prepared = AbilityService.prepare(ability, ability.value(), abilities, abilityResources, 0L, FormulaContext.EMPTY);
        if (!prepared.approved() || !AbilityService.commit(prepared.use(), abilities, abilityResources, 0L).committed()
                || !same(abilityResources.get(spiritPower), 12.0D) || !same(abilityResources.get(soulPower), 1.0D)) {
            throw new IllegalStateException("Ability costs did not deduct their declared resource bars");
        }

        Formation formation = MxtDatapackRegistries.get(MxtResourceKeys.FORMATION, Identifier.parse("mxt_test:spirit_gathering"))
                .orElseThrow(() -> new IllegalStateException("Formation energy-cost test definition was not loaded"));
        ResourceHolderAttachment formationResources = new ResourceHolderAttachment();
        formationResources.set(spiritPower, 20.0D);
        ActivateResult activation = FormationService.activate(Identifier.parse("mxt_test:spirit_gathering"), formation,
                formationResources, FormulaContext.EMPTY);
        if (!activation.active() || !same(formationResources.get(spiritPower), 10.0D)
                || !FormationService.maintain(activation.instance(), formation, formationResources, FormulaContext.EMPTY).maintained()
                || !same(formationResources.get(spiritPower), 9.0D)
                || !same(formation.maxBonus().get(requireHolder(MxtResourceKeys.RESOURCE, Identifier.parse("mxt_test:spirit_power"))).evaluate(FormulaContext.EMPTY), 50.0D)) {
            throw new IllegalStateException("Formation activation and upkeep did not deduct their declared resource bar");
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
        capacity.initializeAuras(Map.of(capacityFire, new AuraPool(10.0D, 10.0D, 0.0D)), List.of());
        capacity.setBlockContribution(List.of(new BlockAuraContribution(BlockPos.ZERO,
                Map.of(capacityFire, new AuraValue(5.0D, new Fixed(5.0D), 1.0D, 0xFFFFFF)))), List.of());
        capacity.regenerateAuras(20L);
        AuraPool pool = capacity.auras().get(capacityFire);
        if (!same(pool.maximum(), 15.0D) || !same(pool.amount(), 15.0D)) {
            throw new IllegalStateException("Block aura must extend, rather than consume, environmental capacity");
        }
        AuraPool formationPool = pool.withMaximum(pool.maximum() + 50.0D).change(50.0D);
        if (!same(formationPool.maximum(), 65.0D) || !same(formationPool.amount(), 65.0D)) {
            throw new IllegalStateException("Formation capacity bonuses did not extend the chunk limit");
        }
        AuraPool unlimited = new AuraPool(1_000.0D, Double.POSITIVE_INFINITY, 0.0D);
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
        if (!FormationStructureValidator.TEMPLATE.matches(level, controller, definition)) {
            throw new IllegalStateException("Vanilla structure-template formation validation failed");
        }
        level.removeBlock(controller, false);
    }

    private static CompoundTag singleBlockTemplate() {
        CompoundTag template = new CompoundTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(1));
        size.add(IntTag.valueOf(1));
        size.add(IntTag.valueOf(1));
        template.put("size", size);
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:gold_block");
        ListTag palette = new ListTag();
        palette.add(state);
        template.put("palette", palette);
        CompoundTag block = new CompoundTag();
        ListTag position = new ListTag();
        position.add(IntTag.valueOf(0));
        position.add(IntTag.valueOf(0));
        position.add(IntTag.valueOf(0));
        block.put("pos", position);
        block.putInt("state", 0);
        ListTag blocks = new ListTag();
        blocks.add(block);
        template.put("blocks", blocks);
        template.put("entities", new ListTag());
        return template;
    }
}
