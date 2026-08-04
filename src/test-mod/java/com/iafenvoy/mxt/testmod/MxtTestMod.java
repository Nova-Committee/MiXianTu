package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resource.ResourceBar;
import com.iafenvoy.mxt.attachment.AuraChunkData;
import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.attachment.AbilityHolderData;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.data.action.builtin.entity.GrantSpiritRootAction;
import com.iafenvoy.mxt.data.item.WeaponBinding;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.formation.FormationStructureValidator;
import com.iafenvoy.mxt.runtime.formation.FormationService;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbService;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.economy.CurrencyValueService;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.runtime.cultivation.CultivationActionService;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.util.ItemMatcher;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.util.ItemMatcher.Entry;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
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

/** Development-only mod that contributes the mxt_test datapack and client resources. */
@Mod(MxtTestMod.MOD_ID)
public final class MxtTestMod {
    public static final String MOD_ID = "mxt_test";
    private static final Logger LOGGER = LogUtils.getLogger();

    public MxtTestMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(MxtTestMod::verifyItemBindings);
        LOGGER.info("Loaded MiXianTu test mod");
    }

    private static void verifyItemBindings(ServerStartedEvent event) {
        verifyDatapackCoverage(event);
        ServerCache cache = ServerCache.get().orElseThrow(() -> new IllegalStateException("Server cache was not created"));
        Identifier foundation = Identifier.parse("mxt_test:foundation");
        Identifier coreForming = Identifier.parse("mxt_test:core_forming");
        if (!cache.isRealmAtLeast(coreForming, foundation) || cache.isRealmAtLeast(foundation, coreForming)) {
            throw new IllegalStateException("Realm cache did not preserve linear realm ordering");
        }
        verifyDynamicResourceValues(foundation, coreForming);
        verifyEnergyCosts();
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
        List<Entry> matcherEntries = ItemMatcher.ENTRIES_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, event.getServer().registryAccess()), JsonParser.parseString("""
                ["minecraft:stick", "minecraft:diamond_sword"]
                """)).getOrThrow();
        if (matcherEntries.size() != 2 || !matcherEntries.get(1).matches(weapon)) {
            throw new IllegalStateException("Physical item matcher entries did not match the weapon stack");
        }
        if (ResourceHolderData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
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
        if (SpiritHerbService.find(new ItemStack(Items.RED_MUSHROOM)).isEmpty()) {
            throw new IllegalStateException("Spirit herb metadata did not bind its existing physical item");
        }
        LOGGER.info("MiXianTu server audit passed");
    }

    private static void verifyDatapackCoverage(ServerStartedEvent event) {
        for (var key : MxtDatapackRegistries.registries()) {
            Registry<?> registry = event.getServer().registryAccess().lookupOrThrow(key);
            boolean hasTestDefinition = registry.listElements()
                    .anyMatch(holder -> MOD_ID.equals(holder.key().identifier().getNamespace()));
            if (!hasTestDefinition) {
                throw new IllegalStateException("No mxt_test datapack definition was loaded for " + key.identifier());
            }
        }
    }

    private static void verifyDynamicResourceValues(Identifier foundation, Identifier coreForming) {
        Identifier qiId = Identifier.parse("mxt_test:qi");
        Identifier spiritPowerId = Identifier.parse("mxt_test:spirit_power");
        Resource qi = MxtDatapackRegistries.get(MxtDatapackRegistries.RESOURCE, qiId)
                .orElseThrow(() -> new IllegalStateException("Dynamic qi resource test definition was not loaded"));
        Resource spiritPower = MxtDatapackRegistries.get(MxtDatapackRegistries.RESOURCE, spiritPowerId)
                .orElseThrow(() -> new IllegalStateException("Spirit-power resource test definition was not loaded"));
        if (!qi.restoreOnAbsorb() || spiritPower.restoreOnAbsorb()) {
            throw new IllegalStateException("Resource absorbed-aura restoration configuration was not decoded correctly");
        }
        SpiritData spirit = new SpiritData();
        spirit.setRealmStage(foundation);
        spirit.setCultivationProgress(40.0D);
        FormulaContext foundationContext = ResourceService.formulaContext(spirit, qiId, qi, FormulaContext.EMPTY);
        ResourceService.Bounds foundationBounds = ResourceService.resolveBounds(qi, foundationContext)
                .orElseThrow(() -> new IllegalStateException("Foundation qi bounds were invalid"));
        if (!same(foundationBounds.max(), 120.0D) || !same(qi.regen().evaluate(foundationContext), 0.25D)) {
            throw new IllegalStateException("Qi maximum and regeneration did not use foundation absorbed aura");
        }
        ResourceHolderData holder = new ResourceHolderData();
        ResourceService.initialize(holder, qiId, qi, foundationContext);
        ResourceService.regenerate(holder, qiId, qi, 4L, foundationContext);
        if (!same(holder.get(qiId), 1.0D)) {
            throw new IllegalStateException("Qi regeneration did not use its dynamic formula");
        }
        spirit.setRealmStage(coreForming);
        FormulaContext coreContext = ResourceService.formulaContext(spirit, qiId, qi, FormulaContext.EMPTY);
        ResourceService.Bounds coreBounds = ResourceService.resolveBounds(qi, coreContext)
                .orElseThrow(() -> new IllegalStateException("Core-forming qi bounds were invalid"));
        if (!same(coreBounds.max(), 170.0D) || !same(qi.regen().evaluate(coreContext), 0.35D)) {
            throw new IllegalStateException("Qi maximum and regeneration did not use realm rank");
        }
        ResourceService.change(holder, qiId, qi, 1_000.0D, coreContext);
        if (!same(holder.get(qiId), 170.0D) || !same(holder.audit(qiId).maxSnapshot(), 170.0D)) {
            throw new IllegalStateException("Qi resource changes did not clamp to its dynamic maximum");
        }

        Identifier meditationId = Identifier.parse("mxt_test:fire_meditation");
        CultivateAction meditation = MxtDatapackRegistries.get(MxtDatapackRegistries.CULTIVATE_ACTION, meditationId)
                .orElseThrow(() -> new IllegalStateException("Cultivation restoration test action was not loaded"));
        SpiritData absorbingSpirit = new SpiritData();
        absorbingSpirit.setRealmStage(foundation);
        ResourceHolderData absorbingResources = new ResourceHolderData();
        absorbingResources.set(spiritPowerId, 5.0D);
        AuraChunkData absorbingAura = new AuraChunkData();
        absorbingAura.setConcentration(10.0D);
        absorbingAura.setEnvironmentTags(List.of(Identifier.parse("mxt_test:environment/fire")));
        if (!CultivationActionService.start(absorbingSpirit, meditationId, meditation, 0L, () -> true).started()) {
            throw new IllegalStateException("Cultivation restoration test action did not start");
        }
        CultivationActionService.Result absorbed = CultivationActionService.tick(absorbingSpirit, absorbingResources,
                absorbingAura, meditationId, meditation, 0L, FormulaContext.EMPTY, () -> true);
        if (!absorbed.progressed() || !same(absorbingSpirit.cultivationProgress(), 1.0D)
                || !same(absorbingResources.get(qiId), 3.0D) || !same(absorbingResources.get(spiritPowerId), 4.0D)) {
            throw new IllegalStateException("Absorbing aura did not also restore the opted-in resource bar");
        }
    }

    private static void verifyEnergyCosts() {
        Identifier spiritPower = Identifier.parse("mxt_test:spirit_power");
        Identifier soulPower = Identifier.parse("mxt_test:soul_power");
        Identifier firebolt = Identifier.parse("mxt_test:firebolt");
        Ability ability = MxtDatapackRegistries.get(MxtDatapackRegistries.ABILITY, firebolt)
                .orElseThrow(() -> new IllegalStateException("Ability energy-cost test definition was not loaded"));
        AbilityHolderData abilities = new AbilityHolderData();
        abilities.grant(firebolt, Identifier.fromNamespaceAndPath(MOD_ID, "test"));
        ResourceHolderData abilityResources = new ResourceHolderData();
        abilityResources.set(spiritPower, 20.0D);
        abilityResources.set(soulPower, 3.0D);
        AbilityService.PrepareResult prepared = AbilityService.prepare(firebolt, ability, abilities, abilityResources, 0L, FormulaContext.EMPTY);
        if (!prepared.approved() || !AbilityService.commit(prepared.use(), abilities, abilityResources, 0L).committed()
                || !same(abilityResources.get(spiritPower), 12.0D) || !same(abilityResources.get(soulPower), 1.0D)) {
            throw new IllegalStateException("Ability costs did not deduct their declared resource bars");
        }

        Formation formation = MxtDatapackRegistries.get(MxtDatapackRegistries.FORMATION, Identifier.parse("mxt_test:spirit_gathering"))
                .orElseThrow(() -> new IllegalStateException("Formation energy-cost test definition was not loaded"));
        ResourceHolderData formationResources = new ResourceHolderData();
        formationResources.set(spiritPower, 20.0D);
        FormationService.ActivateResult activation = FormationService.activate(Identifier.parse("mxt_test:spirit_gathering"), formation,
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

    private static void verifyClientDefinitions(ServerStartedEvent event) {
        ResourceBar target = MxtDatapackRegistries.get(MxtDatapackRegistries.RESOURCE_BAR, Identifier.parse("mxt_test:qi_target_override"))
                .orElseThrow(() -> new IllegalStateException("Target resource-bar test definition was not loaded"));
        if (target.context() != ResourceBar.Context.TARGET_OVERLAY || !target.replaceDefault()) {
            throw new IllegalStateException("Target resource-bar replacement configuration was not retained");
        }
        ResourceBar qiHud = MxtDatapackRegistries.get(MxtDatapackRegistries.RESOURCE_BAR, Identifier.parse("mxt_test:qi_hud"))
                .orElseThrow(() -> new IllegalStateException("Qi resource-bar test definition was not loaded"));
        if (qiHud.valueDisplay() != ResourceBar.ValueDisplay.CURRENT_AND_MAXIMUM) {
            throw new IllegalStateException("Resource-bar numeric display configuration was not retained");
        }
        ResourceBar boss = MxtDatapackRegistries.get(MxtDatapackRegistries.RESOURCE_BAR, Identifier.parse("mxt_test:spirit_power_boss"))
                .orElseThrow(() -> new IllegalStateException("Boss resource-bar test definition was not loaded"));
        if (boss.context() != ResourceBar.Context.BOSS_OVERLAY) {
            throw new IllegalStateException("Boss resource-bar context was not retained");
        }
        AuraZone visuals = MxtDatapackRegistries.get(MxtDatapackRegistries.AURA_ZONE, Identifier.parse("mxt_test:firelands"))
                .orElseThrow(() -> new IllegalStateException("Aura visual test definition was not loaded"));
        if (visuals.clientRender().particleDensity() <= 0 || "#FFFFFF".equalsIgnoreCase(visuals.clientRender().fogColor())) {
            throw new IllegalStateException("Aura-zone client render configuration was not retained");
        }
    }

    private static void verifyFormationTemplate(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        Identifier id = Identifier.parse("mxt_test:spirit_gathering");
        StructureTemplate template = level.getStructureManager().getOrCreate(id);
        template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), singleBlockTemplate());
        BlockPos controller = new BlockPos(0, level.getMinY() + 2, 0);
        level.setBlock(controller, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        Formation definition = MxtDatapackRegistries.get(MxtDatapackRegistries.FORMATION, id)
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
