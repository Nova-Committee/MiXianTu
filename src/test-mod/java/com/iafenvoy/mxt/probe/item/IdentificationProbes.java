package com.iafenvoy.mxt.probe.item;

import com.iafenvoy.mxt.data.item.IdentificationComponent;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.item.IdentificationService;
import com.iafenvoy.mxt.runtime.item.QualityService;
import com.google.gson.JsonObject;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;

public final class IdentificationProbes {
    private IdentificationProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("item/identification/loot", IdentificationProbes::loot),
                Probe.of("item/identification/space", IdentificationProbes::space),
                Probe.of("item/identification/refusals", IdentificationProbes::refusals));
    }

    private static void loot(ProbeContext context) {
        ServerPlayer player = context.player();
        LootParams params = new LootParams.Builder(context.level())
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.GIFT);
        List<ItemStack> loot = context.server().reloadableRegistries()
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, ProbeDefinitions.id("identification/iron_sword")))
                .getRandomItems(params);
        check(context, "loot creates two wrapped items", loot.size() == 1 && loot.getFirst().is(MxtItems.UNIDENTIFIED_ITEM.get())
                && loot.getFirst().getCount() == 2 && loot.getFirst().has(MxtDataComponents.IDENTIFICATION));
        if (loot.isEmpty()) return;
        ItemStack wrapped = roundTrip(context, loot.getFirst());
        check(context, "unidentified stack survives saving", ItemStack.matches(wrapped, loot.getFirst()));

        ItemStack mirror = MxtItems.IDENTIFICATION_MIRROR.toStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, mirror);
        player.setItemInHand(InteractionHand.OFF_HAND, wrapped);
        InteractionResult first = use(player, InteractionHand.MAIN_HAND);
        ItemStack sword = player.getInventory().getNonEquipmentItems().stream().filter(stack -> stack.is(Items.IRON_SWORD))
                .findFirst().orElse(ItemStack.EMPTY);
        check(context, "one item from a stack becomes one sword in inventory", first.consumesAction()
                && player.getOffhandItem() == wrapped && wrapped.getCount() == 1 && sword.getCount() == 1);
        check(context, "output uses the configured components", sword.getDamageValue() == 12
                && sword.getHoverName().getString().equals("Recovered Sword")
                && QualityService.find(context.registries(), sword).filter(context.require(MxtResourceKeys.ITEM_QUALITY, "normal")::equals).isPresent()
                && !sword.has(MxtDataComponents.IDENTIFICATION) && ItemStack.matches(sword, roundTrip(context, sword)));
        InteractionResult last = use(player, InteractionHand.MAIN_HAND);
        check(context, "the last item is replaced in the off hand and the mirror stays intact",
                last.consumesAction() && ItemStack.matches(sword, player.getOffhandItem())
                        && player.getMainHandItem() == mirror);
    }

    private static void space(ProbeContext context) {
        ServerPlayer player = context.player();
        player.getInventory().getNonEquipmentItems().replaceAll(_ -> new ItemStack(Items.STONE, 64));
        player.setItemInHand(InteractionHand.MAIN_HAND, MxtItems.IDENTIFICATION_MIRROR.toStack());
        JsonObject legacyData = new JsonObject();
        legacyData.addProperty("result", "minecraft:diamond");
        IdentificationComponent legacy = IdentificationComponent.CODEC.parse(JsonOps.INSTANCE, legacyData).getOrThrow();
        ItemStack wrapped = carrier(2, legacy);
        player.setItemInHand(InteractionHand.OFF_HAND, wrapped);
        for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
            player.setGameMode(mode);
            check(context, "full inventory preserves both items in " + mode,
                    use(player, InteractionHand.MAIN_HAND) == InteractionResult.FAIL && wrapped.getCount() == 2);
        }
        player.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 63));
        check(context, "compatible stack provides space even with all slots occupied", use(player, InteractionHand.MAIN_HAND)
                .consumesAction() && wrapped.getCount() == 1 && player.getInventory().getItem(9).getCount() == 64);
        check(context, "a final item can be identified with a full inventory", use(player, InteractionHand.MAIN_HAND)
                .consumesAction() && player.getOffhandItem().is(Items.DIAMOND) && player.getOffhandItem().getCount() == 1);
    }

    private static void refusals(ProbeContext context) {
        ServerPlayer player = context.player();
        ItemStack mirror = MxtItems.IDENTIFICATION_MIRROR.toStack();
        player.setItemInHand(InteractionHand.OFF_HAND, mirror);
        check(context, "off-hand mirror passes", use(player, InteractionHand.OFF_HAND) == InteractionResult.PASS);
        player.setItemInHand(InteractionHand.MAIN_HAND, mirror);
        player.setItemInHand(InteractionHand.OFF_HAND, MxtItems.UNIDENTIFIED_ITEM.toStack());
        IdentificationComponent valid = new IdentificationComponent(Identifier.withDefaultNamespace("diamond"));
        ItemStack inventoryTarget = carrier(1, valid);
        player.getInventory().setItem(10, inventoryTarget);
        check(context, "only marked off-hand items are identified", use(player, InteractionHand.MAIN_HAND)
                == InteractionResult.FAIL && player.getInventory().getItem(10) == inventoryTarget);

        for (IdentificationComponent data : List.of(
                new IdentificationComponent(ProbeDefinitions.id("missing_item")),
                new IdentificationComponent(Identifier.withDefaultNamespace("air")),
                new IdentificationComponent(MxtItems.UNIDENTIFIED_ITEM.getId()),
                new IdentificationComponent(Identifier.withDefaultNamespace("diamond"),
                        DataComponentPatch.builder().set(MxtDataComponents.IDENTIFICATION.get(), valid).build()),
                new IdentificationComponent(Identifier.withDefaultNamespace("iron_sword"),
                        DataComponentPatch.builder().set(DataComponents.MAX_STACK_SIZE, 64).build()))) {
            ItemStack wrapped = carrier(2, data);
            player.setItemInHand(InteractionHand.OFF_HAND, wrapped);
            Result<Component> failure = IdentificationService.identifyOffHand(player);
            check(context, "invalid result preserves the input: " + data.result(),
                    failure.getFailure().filter(reason -> reason.key().equals("item.mxt.identification_mirror.invalid_result")).isPresent()
                            && player.getOffhandItem() == wrapped && wrapped.getCount() == 2);
        }
    }

    private static InteractionResult use(ServerPlayer player, InteractionHand hand) {
        return player.gameMode.useItem(player, player.level(), player.getItemInHand(hand), hand);
    }

    private static ItemStack carrier(int count, IdentificationComponent data) {
        ItemStack stack = MxtItems.UNIDENTIFIED_ITEM.toStack(count);
        stack.set(MxtDataComponents.IDENTIFICATION, data);
        return stack;
    }

    private static ItemStack roundTrip(ProbeContext context, ItemStack stack) {
        RegistryOps<Tag> ops = context.registries().createSerializationContext(NbtOps.INSTANCE);
        return ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, stack).getOrThrow()).getOrThrow();
    }

    private static void check(ProbeContext context, String name, boolean success) {
        context.note("identification: " + name + (success ? " OK" : " MISMATCH"));
        context.check(name, success);
    }
}
