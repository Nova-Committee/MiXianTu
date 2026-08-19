package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.attachment.ResourceHolderData.Snapshot;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.aura.BlockAura;
import com.iafenvoy.mxt.data.aura.ItemAura;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resource.ResourceBar;
import com.iafenvoy.mxt.attachment.AuraChunkData;
import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.attachment.AbilityHolderData;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.action.builtin.entity.GrantSpiritRootAction;
import com.iafenvoy.mxt.data.item.WeaponBinding;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.ItemQualityTags;
import com.iafenvoy.mxt.data.artifact.ForgingResultData;
import com.iafenvoy.mxt.data.resource.ResourceBar.Context;
import com.iafenvoy.mxt.data.resource.ResourceBar.Anchor;
import com.iafenvoy.mxt.data.resource.ResourceBar.ValueDisplay;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarRenderers.Origins;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.ability.AbilityService.PrepareResult;
import com.iafenvoy.mxt.runtime.cultivation.CultivationActionService.Result;
import com.iafenvoy.mxt.runtime.formation.FormationService.ActivateResult;
import com.iafenvoy.mxt.runtime.formation.FormationStructureValidator;
import com.iafenvoy.mxt.runtime.formation.FormationService;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbService;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.runtime.economy.CurrencyValueService;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.runtime.cultivation.CultivationActionService;
import com.iafenvoy.mxt.runtime.cultivation.ItemAuraService;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resource.ResourceService.Bounds;
import com.iafenvoy.mxt.util.ItemMatcher;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.util.formula.number.Expression;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.util.ItemMatcher.Entry;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

/** Development-only mod that contributes the mxt_test datapack and client resources. */
@Mod(MxtTestMod.MOD_ID)
public final class MxtTestMod {
    public static final String MOD_ID = "mxt_test";
    private static final Logger LOGGER = LogUtils.getLogger();

    public MxtTestMod(IEventBus modBus) {
        MxtTestItems.REGISTRY.register(modBus);
        NeoForge.EVENT_BUS.addListener(MxtTestMod::verifyItemBindings);
        NeoForge.EVENT_BUS.addListener(MxtTestCommands::registerCommands);
        LOGGER.info("Loaded MiXianTu test mod");
    }

    private static void verifyItemBindings(ServerStartedEvent event) {
        verifyDatapackCoverage(event);
        verifyRecursiveDefinitionDiagnostics();
        ServerCache cache = ServerCache.get().orElseThrow(() -> new IllegalStateException("Server cache was not created"));
        Identifier foundation = Identifier.parse("mxt_test:foundation");
        Identifier coreForming = Identifier.parse("mxt_test:core_forming");
        if (!cache.isRealmAtLeast(coreForming, foundation) || cache.isRealmAtLeast(foundation, coreForming)) {
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
                || ItemBindingService.weapon(event.getServer().registryAccess(), weapon).isEmpty()) {
            throw new IllegalStateException("Weapon binding did not retain its combat values on both registry sides");
        }
        ItemStack pill = new ItemStack(Items.HONEY_BOTTLE);
        if (ItemBindingService.pill(pill).filter(value -> same(value.toxicityGain().evaluate(FormulaContext.EMPTY), 25.0D)).isEmpty()
                || ItemBindingService.pill(event.getServer().registryAccess(), pill).isEmpty()) {
            throw new IllegalStateException("Pill binding did not resolve its existing item");
        }
        ItemStack root = new ItemStack(Items.APPLE);
        if (ItemBindingService.actions(root).stream().noneMatch(GrantSpiritRootAction.class::isInstance)) {
            throw new IllegalStateException("Generic item binding did not resolve its grant-spirit-root action");
        }
        ItemStack jadeSlip = new ItemStack(MxtItems.CULTIVATION_JADE_SLIP.get());
        TechniqueBinding techniqueBinding = ItemBindingService.technique(jadeSlip)
                .orElseThrow(() -> new IllegalStateException("Technique binding did not resolve its existing item"));
        if (!HolderHelper.id(techniqueBinding.technique()).equals(Identifier.parse("mxt_test:qingxiao_breathing_manual"))
                || !techniqueBinding.setActive()
                || ItemBindingService.technique(event.getServer().registryAccess(), jadeSlip).isEmpty()) {
            throw new IllegalStateException("Technique binding did not retain its technique item configuration");
        }
        ItemStack spiritStone = new ItemStack(MxtItems.SPIRIT_STONE.get());
        ItemAura itemAura = MxtDatapackRegistries.get(MxtResourceKeys.ITEM_AURA,
                        Identifier.parse("mxt_test:spirit_stone"))
                .orElseThrow(() -> new IllegalStateException("Item aura test definition was not loaded"));
        if (!same(itemAura.totalAura().evaluate(FormulaContext.EMPTY), 100.0D)
                || !same(itemAura.consumeSpeed().evaluate(FormulaContext.EMPTY), 1.0D)
                || itemAura.resultStack().isEmpty()
                || itemAura.resultStack().get().count() != 1
                || ItemAuraService.find(event.getServer().registryAccess(), spiritStone).isEmpty()) {
            throw new IllegalStateException("Item aura did not resolve its fuel item and values");
        }
        ItemStack qingxiaoCrystal = new ItemStack(MxtTestItems.QINGXIAO_SPIRIT_CRYSTAL.get());
        ItemAura qingxiaoAura = MxtDatapackRegistries.get(MxtResourceKeys.ITEM_AURA,
                        Identifier.parse("mxt_test:qingxiao_spirit_crystal"))
                .orElseThrow(() -> new IllegalStateException("Qingxiao spirit crystal definition was not loaded"));
        if (!same(qingxiaoAura.totalAura().evaluate(FormulaContext.EMPTY), 180.0D)
                || !same(qingxiaoAura.consumeSpeed().evaluate(FormulaContext.EMPTY), 1.0D)
                || ItemAuraService.find(event.getServer().registryAccess(), qingxiaoCrystal).isEmpty()) {
            throw new IllegalStateException("Qingxiao spirit crystal did not bind its test-mod item");
        }
        List<Entry> matcherEntries = ItemMatcher.ENTRIES_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, event.getServer().registryAccess()), JsonParser.parseString("""
                ["minecraft:stick", "minecraft:diamond_sword"]
                """)).getOrThrow();
        if (matcherEntries.size() != 2 || !matcherEntries.get(1).matches(weapon)) {
            throw new IllegalStateException("Physical item matcher entries did not match the weapon stack");
        }
        if (ResourceHolderData.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("""
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
                && same(herb.quality().value().valueMultiplier().evaluate(FormulaContext.EMPTY), 1.5D)
                && same(herb.quality().value().forgingModifier().evaluate(FormulaContext.EMPTY), 1.1D)).isEmpty()
                || ItemQualityService.find(event.getServer().registryAccess(), fireGinseng)
                .map(HolderHelper::id).filter(Identifier.parse("mxt_test:spirit_iron")::equals).isEmpty()) {
            throw new IllegalStateException("Spirit herb quality did not resolve through the shared item-quality system");
        }
        LOGGER.info("MiXianTu server audit passed");
    }

    private static void verifyDatapackCoverage(ServerStartedEvent event) {
        for (ResourceKey<? extends Registry<?>> key : MxtDatapackRegistries.registries()) {
            Registry<?> registry = event.getServer().registryAccess().lookupOrThrow(key);
            boolean hasTestDefinition = registry.listElements()
                    .anyMatch(holder -> MOD_ID.equals(holder.key().identifier().getNamespace()));
            if (!hasTestDefinition) {
                throw new IllegalStateException("No mxt_test datapack definition was loaded for " + key.identifier());
            }
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
                new ForgingResultData(Identifier.parse("mxt_test:iron_sword"), 3, 2, 2, 0, excellent));
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
                || !same(spiritPower.value().resourceToCultivation().maxPerTick().evaluate(FormulaContext.EMPTY), 1.0D)) {
            throw new IllegalStateException("Resource cultivation conversion settings were not decoded correctly");
        }
        SpiritData spirit = new SpiritData();
        spirit.setRealmStage(requireHolder(MxtResourceKeys.REALM_STAGE, foundation));
        spirit.setCultivationProgress(40.0D);
        FormulaContext foundationContext = ResourceService.formulaContext(spirit, qi, FormulaContext.EMPTY);
        Bounds foundationBounds = ResourceService.resolveBounds(qi.value(), foundationContext)
                .orElseThrow(() -> new IllegalStateException("Foundation qi bounds were invalid"));
        if (!same(foundationBounds.max(), 170.0D) || !same(qi.value().regen().evaluate(foundationContext), 0.35D)) {
            throw new IllegalStateException("Qi maximum and regeneration did not use foundation absorbed aura");
        }
        ResourceHolderData holder = new ResourceHolderData();
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
        Snapshot snapshot = holder.snapshot();
        holder.set(qi, 2.0D);
        holder.restore(snapshot);
        if (!same(holder.get(qi), 220.0D) || !same(holder.audit(qi).minSnapshot(), 0.0D)
                || !same(holder.audit(qi).maxSnapshot(), 220.0D)) {
            throw new IllegalStateException("Resource rollback did not preserve server-resolved bounds");
        }

        Identifier meditationId = Identifier.parse("mxt_test:fire_meditation");
        CultivateAction meditation = MxtDatapackRegistries.get(MxtResourceKeys.CULTIVATE_ACTION, meditationId)
                .orElseThrow(() -> new IllegalStateException("Cultivation restoration test action was not loaded"));
        SpiritData absorbingSpirit = new SpiritData();
        absorbingSpirit.setRealmStage(requireHolder(MxtResourceKeys.REALM_STAGE, foundation));
        ResourceHolderData absorbingResources = new ResourceHolderData();
        absorbingResources.set(spiritPower, 5.0D);
        AuraChunkData absorbingAura = new AuraChunkData();
        absorbingAura.setConcentration(10.0D);
        absorbingAura.setAuraKinds(List.of(Identifier.parse("mxt_test:aura_kind/fire")));
        if (!CultivationActionService.start(absorbingSpirit, meditationId, meditation, 0L, () -> true).started()) {
            throw new IllegalStateException("Cultivation restoration test action did not start");
        }
        Result absorbed = CultivationActionService.tick(absorbingSpirit, absorbingResources,
                absorbingAura, meditationId, meditation, 0L, FormulaContext.EMPTY, () -> true);
        if (!absorbed.progressed() || !same(absorbingSpirit.cultivationProgress(), 0.375D)
                || !same(absorbingResources.get(qi), 2.25D) || !same(absorbingResources.get(spiritPower), 4.0D)) {
            throw new IllegalStateException("Cultivation did not apply source-side resource conversion limits correctly");
        }
        Result convertedWhileWaiting = CultivationActionService.tick(absorbingSpirit, absorbingResources,
                absorbingAura, meditationId, meditation, 1L, FormulaContext.EMPTY, () -> true);
        if (!convertedWhileWaiting.waiting() || !same(absorbingSpirit.cultivationProgress(), 0.75D)
                || !same(absorbingResources.get(qi), 1.5D) || !same(absorbingAura.concentration(), 9.0D)) {
            throw new IllegalStateException("Cultivation conversion did not use its source-side limit every game tick");
        }
    }

    private static void verifyEnergyCosts() {
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
        AbilityHolderData abilities = new AbilityHolderData();
        abilities.grant(ability, Identifier.fromNamespaceAndPath(MOD_ID, "test"));
        ResourceHolderData abilityResources = new ResourceHolderData();
        abilityResources.set(spiritPower, 20.0D);
        abilityResources.set(soulPower, 3.0D);
        PrepareResult prepared = AbilityService.prepare(ability, ability.value(), abilities, abilityResources, 0L, FormulaContext.EMPTY);
        if (!prepared.approved() || !AbilityService.commit(prepared.use(), abilities, abilityResources, 0L).committed()
                || !same(abilityResources.get(spiritPower), 12.0D) || !same(abilityResources.get(soulPower), 1.0D)) {
            throw new IllegalStateException("Ability costs did not deduct their declared resource bars");
        }

        Formation formation = MxtDatapackRegistries.get(MxtResourceKeys.FORMATION, Identifier.parse("mxt_test:spirit_gathering"))
                .orElseThrow(() -> new IllegalStateException("Formation energy-cost test definition was not loaded"));
        ResourceHolderData formationResources = new ResourceHolderData();
        formationResources.set(spiritPower, 20.0D);
        ActivateResult activation = FormationService.activate(Identifier.parse("mxt_test:spirit_gathering"), formation,
                formationResources, FormulaContext.EMPTY);
        if (!activation.active() || !same(formationResources.get(spiritPower), 10.0D)
                || !FormationService.maintain(activation.instance(), formation, formationResources, FormulaContext.EMPTY).maintained()
                || !same(formationResources.get(spiritPower), 9.0D)) {
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
        if (MxtDatapackRegistries.holders(MxtResourceKeys.RESOURCE)
                .filter(resource -> MOD_ID.equals(resource.key().identifier().getNamespace()))
                .flatMap(resource -> resource.value().bars().stream())
                .anyMatch(bar -> !(bar.renderer() instanceof Origins))) {
            throw new IllegalStateException("All test resource bars must use the Origins renderer");
        }
        ResourceBar target = qi.bars().stream()
                .filter(bar -> bar.context() == Context.TARGET_OVERLAY && bar.replaceDefault())
                .findFirst().orElseThrow(() -> new IllegalStateException("Target resource-bar test definition was not loaded"));
        if (target.context() != Context.TARGET_OVERLAY || !target.replaceDefault()) {
            throw new IllegalStateException("Target resource-bar replacement configuration was not retained");
        }
        ResourceBar qiHud = qi.bars().stream().filter(bar -> bar.context() == Context.SELF_HUD && !bar.replaceDefault())
                .findFirst().orElseThrow(() -> new IllegalStateException("Qi resource-bar test definition was not loaded"));
        if (qiHud.valueDisplay() != ValueDisplay.CURRENT_AND_MAXIMUM || qiHud.anchor() != Anchor.LEFT) {
            throw new IllegalStateException("Resource-bar left-column configuration was not retained");
        }
        ResourceBar divineSenseHud = divineSense.bars().getFirst();
        if (divineSenseHud.anchor() != Anchor.RIGHT || divineSenseHud.order() != 0) {
            throw new IllegalStateException("Resource-bar right-column order configuration was not retained");
        }
        ResourceBar boss = spiritPower.bars().stream().filter(bar -> bar.context() == Context.BOSS_OVERLAY)
                .findFirst().orElseThrow(() -> new IllegalStateException("Boss resource-bar test definition was not loaded"));
        if (boss.context() != Context.BOSS_OVERLAY) {
            throw new IllegalStateException("Boss resource-bar context was not retained");
        }
        AuraZone visuals = MxtDatapackRegistries.get(MxtResourceKeys.AURA_ZONE, Identifier.parse("mxt_test:firelands"))
                .orElseThrow(() -> new IllegalStateException("Aura visual test definition was not loaded"));
        if (visuals.particle().isEmpty() || visuals.particle().get().count() <= 0
                || "#FFFFFF".equalsIgnoreCase(visuals.clientRender().fogColor())) {
            throw new IllegalStateException("Aura-zone client render configuration was not retained");
        }
        RealmStage foundationStage = requireHolder(MxtResourceKeys.REALM_STAGE, Identifier.parse("mxt_test:foundation")).value();
        if (foundationStage.breakthroughParticle().isEmpty()
                || foundationStage.breakthroughParticle().get().count() <= 0) {
            throw new IllegalStateException("Realm breakthrough particle configuration was not decoded");
        }
        if (visuals.elementAura().size() != 2 || visuals.elementAura().keySet().stream()
                .noneMatch(element -> HolderHelper.id(element).equals(Identifier.parse("mxt_test:fire")))) {
            throw new IllegalStateException("Aura-zone element values were not decoded as element holders");
        }
        AuraZone overworld = MxtDatapackRegistries.get(MxtResourceKeys.AURA_ZONE, Identifier.parse("mxt_test:overworld"))
                .orElseThrow(() -> new IllegalStateException("Aura HUD test definition was not loaded"));
        if (overworld.clientHud().storedAura().isEmpty() || overworld.clientHud().sensedConcentration().isEmpty()
                || !same(overworld.clientHud().storedAura().get().maximum(), 100.0D)
                || !same(overworld.clientHud().sensedConcentration().get().maximum(), 100.0D)
                || overworld.clientHud().storedAura().get().anchor() != Anchor.LEFT
                || overworld.clientHud().storedAura().get().order() != 4
                || overworld.clientHud().sensedConcentration().get().anchor() != Anchor.LEFT
                || overworld.clientHud().sensedConcentration().get().order() != 5) {
            throw new IllegalStateException("Aura-zone optional HUD bars were not decoded correctly");
        }
        BlockAura spiritStone = MxtDatapackRegistries.get(MxtResourceKeys.BLOCK_AURA, Identifier.parse("mxt_test:spirit_stone_ore"))
                .orElseThrow(() -> new IllegalStateException("Spirit-stone aura configuration was not loaded"));
        if (!same(overworld.baseAura(), 0.0D) || overworld.noise().amplitude() <= 0.0D
                || spiritStone.auraPerBlock() * 12.0D <= overworld.noise().amplitude() * 2.0D) {
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
