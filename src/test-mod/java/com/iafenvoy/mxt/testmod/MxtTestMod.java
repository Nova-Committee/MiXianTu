package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.data.alchemy.PillDefinition;
import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.data.item.ItemDefinitionReference;
import com.iafenvoy.mxt.data.item.ItemDefinitionRegistry;
import com.iafenvoy.mxt.data.item.SpiritRootItemEffect;
import com.iafenvoy.mxt.data.weapon.WeaponDefinition;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.economy.CurrencyValueService;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.util.ItemMatcher;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

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
        ServerCache cache = ServerCache.get().orElseThrow(() -> new IllegalStateException("Server cache was not created"));
        Identifier foundation = Identifier.parse("mxt_test:foundation");
        Identifier coreForming = Identifier.parse("mxt_test:core_forming");
        if (!cache.isRealmAtLeast(coreForming, foundation) || cache.isRealmAtLeast(foundation, coreForming)) {
            throw new IllegalStateException("Realm cache did not preserve linear realm ordering");
        }
        Identifier weaponItem = Identifier.parse("mxt_test:bound_sword");
        ItemDefinitionReference weaponReference = new ItemDefinitionReference(ItemDefinitionRegistry.WEAPON, weaponItem);
        ItemStack weapon = ItemBindingService.create(Items.DIAMOND_SWORD, weaponReference)
                .orElseThrow(() -> new IllegalStateException("Item binding did not accept its declared logical item"));
        if (!Identifier.parse("mxt_test:mxt/bound_sword").equals(weapon.get(DataComponents.ITEM_MODEL))) {
            throw new IllegalStateException("Weapon binding did not apply its conventional item model");
        }
        ItemAttributeModifiers modifiers = weapon.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null || modifiers.compute(Attributes.ATTACK_DAMAGE, 0.0D, EquipmentSlot.MAINHAND) != 6.0D
                || modifiers.compute(Attributes.ATTACK_SPEED, 0.0D, EquipmentSlot.MAINHAND) != -2.4D) {
            throw new IllegalStateException("Weapon effect did not apply its datapack combat values");
        }
        if (ItemBindingService.effects(weapon).stream().noneMatch(effect -> effect.definition() instanceof WeaponDefinition)) {
            throw new IllegalStateException("Item definition did not resolve its weapon effect");
        }
        if (ItemBindingService.effects(event.getServer().registryAccess(), weapon).stream()
                .noneMatch(effect -> effect.definition() instanceof WeaponDefinition)) {
            throw new IllegalStateException("Client registry lookup did not resolve the weapon effect");
        }
        Optional<ItemStack> pill = ItemBindingService.create(Items.HONEY_BOTTLE,
                new ItemDefinitionReference(ItemDefinitionRegistry.PILL, Identifier.parse("mxt_test:toxicity_pill")));
        if (pill.isEmpty() || ItemBindingService.effects(pill.get()).stream().noneMatch(effect -> effect.definition() instanceof PillDefinition)) {
            throw new IllegalStateException("Item definition did not resolve its pill effect");
        }
        Optional<ItemStack> root = ItemBindingService.create(Items.APPLE,
                new ItemDefinitionReference(ItemDefinitionRegistry.OTHER, Identifier.parse("mxt_test:fire_root_item")));
        if (root.isEmpty() || ItemBindingService.effects(root.get()).stream().noneMatch(effect -> effect.definition() instanceof SpiritRootItemEffect)) {
            throw new IllegalStateException("Item definition did not resolve its spirit-root effect");
        }
        List<ItemMatcher.Entry> matcherEntries = ItemMatcher.ENTRIES_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, event.getServer().registryAccess()), JsonParser.parseString("""
                ["minecraft:stick", {"registry": "mxt:weapon", "id": "mxt_test:bound_sword"}]
                """)).getOrThrow();
        if (matcherEntries.size() != 2 || !matcherEntries.get(1).matches(weapon)) {
            throw new IllegalStateException("Mixed physical and data-driven item matcher entries did not match the weapon stack");
        }
        if (ItemDefinitionReference.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"mxt_test:bound_sword\"")).result().isPresent()) {
            throw new IllegalStateException("Item definition references must require the category-qualified object form");
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
    }
}
