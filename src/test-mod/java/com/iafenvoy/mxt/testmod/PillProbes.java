package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.attachment.PillToxicityAttachment;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.data.item.Pill;
import com.iafenvoy.mxt.data.item.PillComponent;
import com.mojang.serialization.JsonOps;
import com.iafenvoy.mxt.attachment.PillUsageAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.action.builtin.entity.ModifyPillToxicityAction;
import com.iafenvoy.mxt.data.condition.builtin.entity.PillToxicityCondition;
import com.iafenvoy.mxt.data.item.PillBinding;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.runtime.item.PillService;
import com.iafenvoy.mxt.runtime.item.PillService.ModifyMode;
import com.iafenvoy.mxt.runtime.item.PillUseService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Real use cycles against the command player and one spawned pig. Not run by this class loading.
 */
public final class PillProbes {
    private static final Consumable QUICK = new Consumable(0.075F, ItemUseAnimation.EAT, SoundEvents.GENERIC_EAT, false, List.of());
    private static String stopReason = "";

    private PillProbes() {
    }

    public static int run(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("pill probe: needs a player"));
            return 0;
        }
        Inventory inventory = player.getInventory();
        ItemStack[] slots = new ItemStack[inventory.getContainerSize()];
        for (int slot = 0; slot < slots.length; slot++) slots[slot] = inventory.getItem(slot).copy();
        int selected = inventory.getSelectedSlot();
        int glass = count(player, Items.GLASS_BOTTLE);
        int food = player.getFoodData().getFoodLevel();
        float saturation = player.getFoodData().getSaturationLevel();
        float health = player.getHealth();
        float absorption = player.getAbsorptionAmount();
        List<MobEffectInstance> effects = player.getActiveEffects().stream().map(MobEffectInstance::new).toList();
        Collection<ItemEntity> previousDrops = player.captureDrops();
        // Forced player ticks must not pick up world items; only this player's probe remainders are captured.
        Consumer<ItemEntityPickupEvent.Pre> preventPickups = event -> {
            if (event.getPlayer() == player) event.setCanPickup(TriState.FALSE);
        };
        boolean hadUse = player.hasData(MxtAttachments.PILL_USAGE);
        boolean hadTox = player.hasData(MxtAttachments.PILL_TOXICITY);
        PillUsageAttachment savedUse = hadUse ? copyUsage(player.getData(MxtAttachments.PILL_USAGE), player) : null;
        double savedTox = PillService.toxicity(player);
        int savedRemainder = hadTox ? player.getData(MxtAttachments.PILL_TOXICITY).decayRemainder() : 0;
        double savedDecay = MxtServerConfig.INSTANCE.alchemy.toxicityDecayPerSecond.getValue();
        long savedTime = player.level().getServer().overworld().getGameTime();
        ServerLevelData clock = player.level().getServer().getWorldData().overworldData();
        GameType mode = player.gameMode();
        boolean instabuild = player.getAbilities().instabuild;
        boolean naturalRegen = player.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
        Pig pig = null;
        Pig quiet = null;
        boolean ok = true;
        try {
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, preventPickups);
            player.captureDrops(new ArrayList<>());
            MxtServerConfig.INSTANCE.alchemy.toxicityDecayPerSecond.setValue(0.0D);
            // Creative never shrinks a consumable. Peaceful regen also lifts saturation every 20 ticks,
            // which cancels a sat==0 drink before honey's 40-tick Finish.
            player.setGameMode(GameType.SURVIVAL);
            player.getAbilities().instabuild = false;
            player.level().getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, player.level().getServer());
            clearLedgers(player);
            Holder<PillBinding> blocked = binding(player, "blocked_pill");
            Holder<PillBinding> hungry = binding(player, "hungry_pill");
            Holder<PillBinding> reenter = binding(player, "reenter_pill");
            Holder<PillBinding> limited = binding(player, "limited_pill");
            Holder<PillBinding> toxicity = binding(player, "toxicity_pill");
            Holder<PillBinding> plainItem = binding(player, "plain_item_pill");
            boolean missingPill = pill(player, "disabled_ref") == null;
            if (blocked == null || hungry == null || reenter == null || limited == null
                    || toxicity == null || plainItem == null || !missingPill) {
                source.sendFailure(Component.literal("pill probe: fixture missing"));
                return 0;
            }

            feed(player, 1, 0.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.DRIED_KELP));
            int before = player.getMainHandItem().getCount();
            boolean started = begin(player);
            ok &= leg(source, "illegal_start", !started && player.getMainHandItem().getCount() == before
                            && count(player, Items.GLASS_BOTTLE) == glass
                            && PillService.toxicity(player) == 0.0D && PillService.uses(player, blocked) == 0
                            && !player.hasData(MxtAttachments.PILL_USAGE),
                    "started=" + started + " count=" + player.getMainHandItem().getCount() + " tox=" + PillService.toxicity(player),
                    "started=false count=" + before + " tox=0");
            player.stopUsingItem();

            feed(player, 10, 0.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.GLOW_BERRIES));
            before = player.getMainHandItem().getCount();
            boolean tickStarted = begin(player);
            player.getFoodData().setSaturation(5.0F);
            player.doTick();
            boolean tickHeld = player.isUsingItem();
            ok &= leg(source, "illegal_tick", tickStarted && !tickHeld && player.getMainHandItem().getCount() == before
                            && PillService.toxicity(player) == 0.0D && PillService.uses(player, hungry) == 0,
                    "started=" + tickStarted + " stillUsing=" + tickHeld + " count=" + player.getMainHandItem().getCount(),
                    "started=true stillUsing=false count=" + before);
            player.stopUsingItem();

            feed(player, 10, 0.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.GLOW_BERRIES));
            int berriesBefore = count(player, Items.GLOW_BERRIES);
            boolean finished = finish(player, 4);
            ok &= leg(source, "finish_after_saturation", finished && close(PillService.toxicity(player), 3.0D)
                            && count(player, Items.GLOW_BERRIES) == berriesBefore - 1
                            && player.getFoodData().getSaturationLevel() > 0.0F
                            && PillService.uses(player, hungry) == 1,
                    "done=" + finished + " reason=" + stopReason + " tox=" + PillService.toxicity(player)
                            + " sat=" + player.getFoodData().getSaturationLevel()
                            + " berries=" + count(player, Items.GLOW_BERRIES)
                            + " uses=" + PillService.uses(player, hungry),
                    "tox=3 berries-1 sat>0 uses=1");

            clearLedgers(player);
            PillProbeActions.calls = 0;
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.SPIDER_EYE));
            finished = finish(player, 4);
            ok &= leg(source, "reentry", finished && PillProbeActions.calls == 1 && close(PillService.toxicity(player), 7.0D)
                            && PillService.uses(player, reenter) == 1 && PillService.uses(player, toxicity) == 0,
                    "calls=" + PillProbeActions.calls + " tox=" + PillService.toxicity(player) + " uses=" + PillService.uses(player, reenter),
                    "calls=1 tox=7 uses=1 nested=0");

            clearLedgers(player);
            long atConsume = PillService.overworldGameTime(player);
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.SWEET_BERRIES));
            finished = finish(player, 4);
            long until = PillService.cooldownUntil(player, limited);
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.SWEET_BERRIES));
            boolean immediate = begin(player);
            player.stopUsingItem();
            clock.setGameTime(until - 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.SWEET_BERRIES));
            boolean at19 = begin(player);
            player.stopUsingItem();
            clock.setGameTime(until);
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.SWEET_BERRIES));
            boolean at20 = begin(player);
            boolean second = at20 && finish(player, 4);
            clock.setGameTime(until + 100);
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.SWEET_BERRIES));
            boolean third = begin(player);
            player.stopUsingItem();
            ok &= leg(source, "cooldown_max_uses", finished && until == atConsume + 20 && !immediate && !at19 && second
                            && PillService.uses(player, limited) == 2 && !third,
                    "until=" + until + " now=" + atConsume + " immediate=" + immediate + " at19=" + at19 + " second=" + second
                            + " uses=" + PillService.uses(player, limited) + " third=" + third,
                    "until=now+20 reject@19 accept@20 uses=2 reject_after");

            PillUsageAttachment roundTrip = copyUsage(player.getData(MxtAttachments.PILL_USAGE), player);
            pig = new Pig(EntityType.PIG, player.level());
            pig.setPos(player.getX(), player.getY(), player.getZ());
            player.level().addFreshEntity(pig);
            pig.setData(MxtAttachments.PILL_USAGE, roundTrip);
            ok &= leg(source, "persistent_usage", roundTrip.uses(limited) == 2 && pig.getData(MxtAttachments.PILL_USAGE).uses(limited) == 2,
                    "saved=" + roundTrip.uses(limited) + " copied=" + pig.getData(MxtAttachments.PILL_USAGE).uses(limited),
                    "2 and 2");

            // A pill bound to an item with no use of its own: the click arms the carrier's cycle for it, vanilla
            // eats one, and the item is left exactly as its author wrote it. Driven without a client, so the arm
            // is called the way the click handler calls it.
            clearLedgers(player);
            feed(player, 20, 5.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT, 2));
            ItemStack ingot = player.getMainHandItem();
            PillUseService.arm(player, ingot);
            boolean armed = ingot.has(DataComponents.CONSUMABLE);
            int ingotsBefore = count(player, Items.GOLD_INGOT);
            boolean swallowed = finish(player, 40);
            ok &= leg(source, "plain_item_armed", armed && swallowed && close(PillService.toxicity(player), 25.0D)
                            && count(player, Items.GOLD_INGOT) == ingotsBefore - 1
                            && !player.getMainHandItem().has(DataComponents.CONSUMABLE)
                            && PillService.uses(player, plainItem) == 1,
                    "armed=" + armed + " eaten=" + swallowed + " reason=" + stopReason + " tox=" + PillService.toxicity(player)
                            + " ingots=" + count(player, Items.GOLD_INGOT)
                            + " component=" + player.getMainHandItem().has(DataComponents.CONSUMABLE)
                            + " uses=" + PillService.uses(player, plainItem),
                    "armed=true eaten=true tox=25 ingots-1 component=false uses=1");

            clearLedgers(player);
            boolean doses = true;
            for (int dose = 0; dose < 4 && doses; dose++) {
                feed(player, 1, 0.0F);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HONEY_BOTTLE));
                doses = finish(player, 40);
            }
            double afterFour = PillService.toxicity(player);
            new ModifyPillToxicityAction(ModifyMode.ADD, new Constant(-30.0D)).execute(player, FormulaContext.of(player));
            boolean cleared = new PillToxicityCondition(new Comparison(Comparison.Operation.EQUAL, 0.0D)).test(player, FormulaContext.of(player));
            ok &= leg(source, "overdose_detox", doses && close(afterFour, 20.0D) && close(PillService.toxicity(player), 0.0D)
                            && PillService.uses(player, toxicity) == 4 && cleared,
                    "doses=" + doses + " reason=" + stopReason + " after4=" + afterFour + " afterDetox=" + PillService.toxicity(player)
                            + " uses=" + PillService.uses(player, toxicity),
                    "4 doses tox=20 then 0 uses=4");

            clock.setGameTime(savedTime);
            MxtServerConfig.INSTANCE.alchemy.toxicityDecayPerSecond.setValue(2.0D);
            quiet = new Pig(EntityType.PIG, player.level());
            quiet.setPos(player.getX() + 1, player.getY(), player.getZ());
            player.level().addFreshEntity(quiet);
            ServerLevel level = (ServerLevel) quiet.level();
            for (int tick = 0; tick < 20; tick++) level.tickNonPassenger(quiet);
            boolean empty = !quiet.hasData(MxtAttachments.PILL_TOXICITY);
            PillService.modify(quiet, ModifyMode.SET, 10.0D);
            MxtServerConfig.INSTANCE.alchemy.toxicityDecayPerSecond.setValue(0.0D);
            for (int tick = 0; tick < 20; tick++) level.tickNonPassenger(quiet);
            double held = PillService.toxicity(quiet);
            clock.setGameTime(savedTime + 100);
            double offline = PillService.toxicity(quiet);
            clock.setGameTime(savedTime);
            MxtServerConfig.INSTANCE.alchemy.toxicityDecayPerSecond.setValue(2.0D);
            for (int tick = 0; tick < 19; tick++) level.tickNonPassenger(quiet);
            double early = PillService.toxicity(quiet);
            int remainder = quiet.getData(MxtAttachments.PILL_TOXICITY).decayRemainder();
            level.tickNonPassenger(quiet);
            double decayed = PillService.toxicity(quiet);
            ok &= leg(source, "decay", empty && close(held, 10.0D) && close(offline, 10.0D) && close(early, 10.0D)
                            && remainder == 19 && close(decayed, 8.0D),
                    "empty=" + empty + " held=" + held + " offline=" + offline + " early=" + early
                            + " remainder=" + remainder + " decayed=" + decayed,
                    "empty held=10 offline=10 early=10 remainder=19 decayed=8");

            boolean excludedFile = player.level().getServer().getResourceManager()
                    .getResource(Identifier.fromNamespaceAndPath("mxt_test", "mxt/pill/disabled_ref.json")).isPresent();
            ok &= leg(source, "excluded_pill", excludedFile && missingPill,
                    "file=" + excludedFile + " pill=" + (missingPill ? "absent" : "present"),
                    "file=true pill=absent");

            // Nothing in the pack may name the excluded pill: a definition that did would leave an unbound value in the
            // registry and the whole load would fail. The guard is exercised with the one shape it is for instead, a
            // holder that carries the key but no value; it must refuse rather than fall back to another pill.
            clearLedgers(player);
            feed(player, 10, 0.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND,
                    dosed(MxtItems.PILL.get(), PillComponent.ofPill(unboundPill(player))));
            ItemBindingService.PillResolution unbound = ItemBindingService.resolvePill(player.registryAccess(), player.getMainHandItem());
            boolean unboundStarted = begin(player);
            player.stopUsingItem();
            ok &= leg(source, "unbound_pill", unbound.unbound() && unbound.effects().isEmpty() && !unboundStarted
                            && PillService.toxicity(player) == 0.0D && !player.hasData(MxtAttachments.PILL_USAGE),
                    "unbound=" + unbound.unbound() + " effects=" + unbound.effects().isPresent() + " started=" + unboundStarted
                            + " reason=" + refusal(player) + " tox=" + PillService.toxicity(player),
                    "unbound=true effects=false started=false reason=unbound tox=0");

            clearLedgers(player);
            feed(player, 10, 0.0F);
            long overlayAt = PillService.overworldGameTime(player);
            // The component names another pill than the binding does: its effects win, the binding's caps stay.
            PillComponent overlaid = component(player, "{\"pill\":\"mxt_test:toxicity\",\"toxicity_gain\":9}");
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.SWEET_BERRIES, overlaid));
            boolean overlayFirst = finish(player, 4);
            long overlayUntil = PillService.cooldownUntil(player, limited);
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.SWEET_BERRIES, overlaid));
            boolean overlayImmediate = begin(player);
            player.stopUsingItem();
            clock.setGameTime(overlayUntil);
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.SWEET_BERRIES, overlaid));
            boolean overlaySecond = begin(player) && finish(player, 4);
            clock.setGameTime(overlayUntil + 100);
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.SWEET_BERRIES, overlaid));
            boolean overlayThird = begin(player);
            player.stopUsingItem();
            ok &= leg(source, "overlay_keeps_limits", overlayFirst && overlayUntil == overlayAt + 20 && !overlayImmediate
                            && overlaySecond && !overlayThird && close(PillService.toxicity(player), 18.0D)
                            && PillService.uses(player, limited) == 2 && PillService.uses(player, toxicity) == 0,
                    "first=" + overlayFirst + " until=" + overlayUntil + " at=" + overlayAt
                            + " immediate=" + overlayImmediate + " second=" + overlaySecond + " third=" + overlayThird
                            + " tox=" + PillService.toxicity(player) + " limited=" + PillService.uses(player, limited)
                            + " toxicityUses=" + PillService.uses(player, toxicity),
                    "gain=9 cooldown=20 uses=2 toxicityUses=0");

            clearLedgers(player);
            feed(player, 10, 0.0F);
            PillComponent overtaken = component(player, "{\"pill\":\"mxt_test:blocked\"}");
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.HONEY_BOTTLE, overtaken));
            int honeyCount = player.getMainHandItem().getCount();
            boolean overtakenStarted = begin(player);
            player.stopUsingItem();
            ok &= leg(source, "component_over_binding", !overtakenStarted
                            && player.getMainHandItem().getCount() == honeyCount
                            && PillService.toxicity(player) == 0.0D && PillService.uses(player, toxicity) == 0,
                    "started=" + overtakenStarted + " reason=" + refusal(player) + " count=" + player.getMainHandItem().getCount()
                            + " tox=" + PillService.toxicity(player),
                    "started=false reason=conditions count=" + honeyCount + " tox=0");

            clearLedgers(player);
            PillComponent only = component(player, "{\"toxicity_gain\":4}");
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(MxtItems.PILL.get(), only));
            boolean onlyFirst = finish(player, 4);
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(MxtItems.PILL.get(), only));
            boolean onlySecond = finish(player, 4);
            ok &= leg(source, "component_only", onlyFirst && onlySecond && close(PillService.toxicity(player), 8.0D)
                            && !player.hasData(MxtAttachments.PILL_USAGE)
                            && PillService.uses(player, limited) == 0 && PillService.uses(player, toxicity) == 0
                            && PillService.uses(player, hungry) == 0 && PillService.cooldownUntil(player, limited) == 0L,
                    "first=" + onlyFirst + " second=" + onlySecond + " tox=" + PillService.toxicity(player)
                            + " limited=" + PillService.uses(player, limited)
                            + " toxicityUses=" + PillService.uses(player, toxicity),
                    "gain=4 twice no bound counter");

            // What the client tint reads off a carrier stack: the colour of the pill it resolves. A dose that resolves
            // to nothing paints nothing, which the unbound_pill leg above already covers.
            Pill painted = ItemBindingService.resolvePill(player.registryAccess(),
                    dosed(MxtItems.PILL.get(), component(player, "{\"pill\":\"mxt_test:toxicity\"}"))).effects().orElse(null);
            ok &= leg(source, "pill_color", painted != null && painted.color() == 0x8F3FA8,
                    "color=" + (painted == null ? "none" : Integer.toHexString(painted.color())),
                    "color=8f3fa8");
        } catch (RuntimeException failure) {
            ok = false;
            source.sendFailure(Component.literal("pill probe: " + failure.getClass().getSimpleName() + " " + failure.getMessage()));
        } finally {
            NeoForge.EVENT_BUS.unregister(preventPickups);
            player.captureDrops(previousDrops);
            clock.setGameTime(savedTime);
            MxtServerConfig.INSTANCE.alchemy.toxicityDecayPerSecond.setValue(savedDecay);
            player.level().getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, naturalRegen, player.level().getServer());
            if (player.gameMode() != mode) player.setGameMode(mode);
            player.getAbilities().instabuild = instabuild;
            player.onUpdateAbilities();
            player.stopUsingItem();
            restoreInventory(player, slots, selected);
            player.getFoodData().setFoodLevel(food);
            player.getFoodData().setSaturation(saturation);
            restoreEffects(player, effects);
            player.setHealth(health);
            player.setAbsorptionAmount(absorption);
            if (hadUse && savedUse != null) player.setData(MxtAttachments.PILL_USAGE, savedUse);
            else player.removeData(MxtAttachments.PILL_USAGE);
            if (hadTox) {
                PillToxicityAttachment attachment = player.getData(MxtAttachments.PILL_TOXICITY);
                attachment.set(savedTox);
                attachment.setDecayRemainder(savedRemainder);
            } else {
                player.removeData(MxtAttachments.PILL_TOXICITY);
            }
            if (pig != null) pig.discard();
            if (quiet != null) quiet.discard();
        }
        if (ok) source.sendSuccess(() -> Component.literal("pill probe: OK"), false);
        else source.sendFailure(Component.literal("pill probe: MISMATCH"));
        return ok ? 1 : 0;
    }

    private static Holder<PillBinding> binding(ServerPlayer player, String path) {
        return MxtDatapackRegistries.holder(player.registryAccess(), MxtResourceKeys.PILL_BINDING, id(path)).orElse(null);
    }

    private static Holder<Pill> pill(ServerPlayer player, String path) {
        return MxtDatapackRegistries.holder(player.registryAccess(), MxtResourceKeys.PILL, id(path)).orElse(null);
    }

    // A holder that carries the key but no value: what a reference to a pill the pack no longer provides looks like,
    // and the one shape the resolution must refuse. No definition may name that excluded pill, since the loader would
    // then leave an unbound value in the registry and the whole world would fail to load.
    private static Holder<Pill> unboundPill(ServerPlayer player) {
        return Holder.Reference.createStandAlone(player.registryAccess().lookupOrThrow(MxtResourceKeys.PILL),
                ResourceKey.create(MxtResourceKeys.PILL, id("disabled_ref")));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, path);
    }

    // A stack of an item a binding claims: the dose runs that binding's pill and the counters are the binding's.
    private static ItemStack dosed(Item item) {
        return arm(new ItemStack(item));
    }

    // Same, with the stack's own component on top: it decides what the dose does, never the counters.
    private static ItemStack dosed(Item item, PillComponent component) {
        ItemStack stack = new ItemStack(item);
        stack.set(MxtDataComponents.PILL.get(), component);
        return arm(stack);
    }

    private static ItemStack arm(ItemStack stack) {
        stack.set(DataComponents.CONSUMABLE, QUICK);
        return stack;
    }

    private static PillComponent component(ServerPlayer player, String json) {
        return PillComponent.CODEC.parse(player.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                JsonParser.parseString(json)).getOrThrow();
    }

    private static void feed(ServerPlayer player, int food, float saturation) {
        player.getFoodData().setFoodLevel(food);
        player.getFoodData().setSaturation(saturation);
    }

    private static void clearLedgers(ServerPlayer player) {
        player.removeData(MxtAttachments.PILL_USAGE);
        player.removeData(MxtAttachments.PILL_TOXICITY);
    }

    private static boolean begin(ServerPlayer player) {
        player.startUsingItem(InteractionHand.MAIN_HAND);
        return player.isUsingItem();
    }

    private static boolean finish(ServerPlayer player, int ticks) {
        stopReason = "";
        int duration = player.getMainHandItem().getUseDuration(player);
        if (!begin(player)) {
            stopReason = "start " + refusal(player);
            return false;
        }
        int ran = 0;
        for (; ran < ticks && player.isUsingItem(); ran++) player.doTick();
        if (player.isUsingItem()) {
            stopReason = "incomplete ran=" + ran + "/" + duration;
            player.stopUsingItem();
            return false;
        }
        // Releasing the item early is a gate cancel, not evidence that Finish ran.
        if (duration > 0 && ran < duration) {
            stopReason = "cancelled ran=" + ran + "/" + duration + " " + refusal(player);
            return false;
        }
        return true;
    }

    private static String refusal(ServerPlayer player) {
        return ItemQualityService.check(player, player.getMainHandItem())
                .map(failure -> failure.name().toLowerCase(Locale.ROOT))
                .orElse("none") + " sat=" + player.getFoodData().getSaturationLevel();
    }

    private static PillUsageAttachment copyUsage(PillUsageAttachment usage, ServerPlayer player) {
        Codec<PillUsageAttachment> codec = PillUsageAttachment.CODEC.codec();
        RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        return codec.parse(ops, codec.encodeStart(ops, usage).getOrThrow()).getOrThrow();
    }

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    // mxt_test:toxicity declares no consume or overdose action. Honey's vanilla consume clears poison, and the
    // forced player ticks can shorten other effects or spend health, so those are put back with the inventory.
    private static void restoreEffects(ServerPlayer player, List<MobEffectInstance> saved) {
        List<Holder<MobEffect>> extra = new ArrayList<>();
        for (MobEffectInstance current : List.copyOf(player.getActiveEffects())) {
            boolean kept = false;
            for (MobEffectInstance effect : saved) {
                if (effect.getEffect().equals(current.getEffect())) {
                    kept = true;
                    break;
                }
            }
            if (!kept) extra.add(current.getEffect());
        }
        for (Holder<MobEffect> effect : extra) player.removeEffect(effect);
        for (MobEffectInstance effect : saved) {
            player.removeEffect(effect.getEffect());
            player.addEffect(new MobEffectInstance(effect));
        }
    }

    private static void restoreInventory(ServerPlayer player, ItemStack[] slots, int selected) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < slots.length; slot++) inventory.setItem(slot, slots[slot]);
        inventory.setSelectedSlot(selected);
        player.connection.send(new ClientboundSetHeldSlotPacket(selected));
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }

    private static boolean close(double actual, double expected) {
        return Double.isFinite(actual) && Math.abs(actual - expected) < 1.0E-6D;
    }

    private static boolean leg(CommandSourceStack source, String name, boolean ok, String actual, String expected) {
        String line = "pill probe: " + name + " actual=" + actual + " expected=" + expected + (ok ? " OK" : " MISMATCH");
        if (ok) source.sendSuccess(() -> Component.literal(line), false);
        else source.sendFailure(Component.literal(line));
        return ok;
    }
}
