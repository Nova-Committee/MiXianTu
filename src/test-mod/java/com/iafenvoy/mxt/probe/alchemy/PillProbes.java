package com.iafenvoy.mxt.probe.alchemy;

import com.google.gson.JsonParser;
import com.iafenvoy.mxt.attachment.PillToxicityAttachment;
import com.iafenvoy.mxt.attachment.PillUsageAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.action.builtin.entity.ModifyPillToxicityAction;
import com.iafenvoy.mxt.data.condition.builtin.entity.PillToxicityCondition;
import com.iafenvoy.mxt.data.item.Pill;
import com.iafenvoy.mxt.data.item.PillBinding;
import com.iafenvoy.mxt.data.item.PillComponent;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.*;
import com.iafenvoy.mxt.runtime.item.PillService;
import com.iafenvoy.mxt.runtime.item.PillService.ModifyMode;
import com.iafenvoy.mxt.runtime.item.PillUseService;
import com.iafenvoy.mxt.runtime.item.QualityService;
import com.iafenvoy.mxt.testmod.PillProbeActions;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.util.math.CompareOperation;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
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
 * Real use cycles against the probe's own player and one spawned pig, one named leg per test.
 *
 * <p>The old command ran every leg in a row on one player, so a leg could read the toxicity and the use counts the
 * leg above it left behind. Where a leg did, it earns that state itself now; nothing else about a leg changed.
 */
public final class PillProbes {
    private static final Consumable QUICK = new Consumable(0.075F, ItemUseAnimation.EAT, SoundEvents.GENERIC_EAT, false, List.of());
    private static String stopReason = "";

    private PillProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("alchemy/pill/illegal_start", PillProbes::illegalStart),
                Probe.of("alchemy/pill/illegal_tick", PillProbes::illegalTick),
                Probe.of("alchemy/pill/finish_after_saturation", PillProbes::finishAfterSaturation),
                Probe.of("alchemy/pill/reentry", PillProbes::reentry),
                Probe.of("alchemy/pill/cooldown_max_uses", PillProbes::cooldownMaxUses),
                Probe.of("alchemy/pill/plain_item_armed", PillProbes::plainItemArmed),
                Probe.of("alchemy/pill/overdose_detox", PillProbes::overdoseDetox),
                Probe.of("alchemy/pill/decay", PillProbes::decay),
                Probe.of("alchemy/pill/overlay_keeps_limits", PillProbes::overlayKeepsLimits),
                Probe.of("alchemy/pill/component_over_binding", PillProbes::componentOverBinding),
                Probe.of("alchemy/pill/component_only", PillProbes::componentOnly));
    }

    // The six bindings and the absent pill the old command resolved once, before it ran any leg.
    private record Bindings(Holder<PillBinding> blocked, Holder<PillBinding> hungry, Holder<PillBinding> reenter,
                            Holder<PillBinding> limited, Holder<PillBinding> toxicity, Holder<PillBinding> plainItem) {
    }

    /**
     * One leg under the old command's preamble and epilogue: the config, the gamerule, the level clock and the drop
     * capture belong to the shared level, so each leg sets them and puts them back, and discards what it spawns.
     */
    private static void run(ProbeContext context, Consumer<List<Entity>> body) {
        ServerPlayer player = context.player();
        Inventory inventory = player.getInventory();
        ItemStack[] slots = new ItemStack[inventory.getContainerSize()];
        for (int slot = 0; slot < slots.length; slot++) slots[slot] = inventory.getItem(slot).copy();
        int selected = inventory.getSelectedSlot();
        int food = player.getFoodData().getFoodLevel();
        float saturation = player.getFoodData().getSaturationLevel();
        float health = player.getHealth();
        float absorption = player.getAbsorptionAmount();
        List<MobEffectInstance> effects = player.getActiveEffects().stream().map(MobEffectInstance::new).toList();
        Collection<ItemEntity> previousDrops = player.captureDrops();
        boolean hadUse = player.hasData(MxtAttachments.PILL_USAGE);
        boolean hadTox = player.hasData(MxtAttachments.PILL_TOXICITY);
        PillUsageAttachment savedUse = hadUse ? copyUsage(player.getData(MxtAttachments.PILL_USAGE), context) : null;
        double savedTox = PillService.toxicity(player);
        int savedRemainder = hadTox ? player.getData(MxtAttachments.PILL_TOXICITY).decayRemainder() : 0;
        double savedDecay = MxtServerConfig.INSTANCE.alchemy.toxicityDecayPerSecond.getValue();
        long savedTime = context.server().overworld().getGameTime();
        ServerLevelData clock = context.server().getWorldData().overworldData();
        GameType mode = player.gameMode();
        boolean instabuild = player.getAbilities().instabuild;
        boolean naturalRegen = player.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
        List<Entity> spawned = new ArrayList<>();
        // Forced player ticks must not pick up world items; only this player's probe remainders are captured.
        Consumer<ItemEntityPickupEvent.Pre> preventPickups = event -> {
            if (event.getPlayer() == player) event.setCanPickup(TriState.FALSE);
        };
        try {
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, preventPickups);
            player.captureDrops(new ArrayList<>());
            MxtServerConfig.INSTANCE.alchemy.toxicityDecayPerSecond.setValue(0.0D);
            // Creative never shrinks a consumable. Peaceful regen also lifts saturation every 20 ticks,
            // which cancels a sat==0 drink before honey's 40-tick Finish.
            player.setGameMode(GameType.SURVIVAL);
            player.getAbilities().instabuild = false;
            player.level().getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, context.server());
            clearLedgers(player);
            body.accept(spawned);
        } catch (RuntimeException failure) {
            context.fail("pill probe: " + failure.getClass().getSimpleName() + " " + failure.getMessage());
        } finally {
            NeoForge.EVENT_BUS.unregister(preventPickups);
            player.captureDrops(previousDrops);
            clock.setGameTime(savedTime);
            MxtServerConfig.INSTANCE.alchemy.toxicityDecayPerSecond.setValue(savedDecay);
            player.level().getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, naturalRegen, context.server());
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
            spawned.forEach(Entity::discard);
        }
    }

    private static void illegalStart(ProbeContext context) {
        run(context, spawned -> {
            Bindings bindings = fixtures(context);
            if (bindings == null) return;
            ServerPlayer player = context.player();
            int glass = count(player, Items.GLASS_BOTTLE);
            feed(player, 1, 0.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.DRIED_KELP));
            int before = player.getMainHandItem().getCount();
            boolean started = begin(player);
            leg(context, "illegal_start", !started && player.getMainHandItem().getCount() == before
                            && count(player, Items.GLASS_BOTTLE) == glass
                            && PillService.toxicity(player) == 0.0D && PillService.uses(player, bindings.blocked()) == 0
                            && !player.hasData(MxtAttachments.PILL_USAGE),
                    "started=" + started + " count=" + player.getMainHandItem().getCount() + " tox=" + PillService.toxicity(player),
                    "started=false count=" + before + " tox=0");
            player.stopUsingItem();
        });
    }

    private static void illegalTick(ProbeContext context) {
        run(context, spawned -> {
            Bindings bindings = fixtures(context);
            if (bindings == null) return;
            ServerPlayer player = context.player();
            feed(player, 10, 0.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.GLOW_BERRIES));
            int before = player.getMainHandItem().getCount();
            boolean tickStarted = begin(player);
            player.getFoodData().setSaturation(5.0F);
            player.doTick();
            boolean tickHeld = player.isUsingItem();
            leg(context, "illegal_tick", tickStarted && !tickHeld && player.getMainHandItem().getCount() == before
                            && PillService.toxicity(player) == 0.0D && PillService.uses(player, bindings.hungry()) == 0,
                    "started=" + tickStarted + " stillUsing=" + tickHeld + " count=" + player.getMainHandItem().getCount(),
                    "started=true stillUsing=false count=" + before);
            player.stopUsingItem();
        });
    }

    private static void finishAfterSaturation(ProbeContext context) {
        run(context, spawned -> {
            Bindings bindings = fixtures(context);
            if (bindings == null) return;
            ServerPlayer player = context.player();
            feed(player, 10, 0.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.GLOW_BERRIES));
            int berriesBefore = count(player, Items.GLOW_BERRIES);
            boolean finished = finish(player, 4);
            leg(context, "finish_after_saturation", finished && close(PillService.toxicity(player), 3.0D)
                            && count(player, Items.GLOW_BERRIES) == berriesBefore - 1
                            && player.getFoodData().getSaturationLevel() > 0.0F
                            && PillService.uses(player, bindings.hungry()) == 1,
                    "done=" + finished + " reason=" + stopReason + " tox=" + PillService.toxicity(player)
                            + " sat=" + player.getFoodData().getSaturationLevel()
                            + " berries=" + count(player, Items.GLOW_BERRIES)
                            + " uses=" + PillService.uses(player, bindings.hungry()),
                    "tox=3 berries-1 sat>0 uses=1");
        });
    }

    private static void reentry(ProbeContext context) {
        run(context, spawned -> {
            Bindings bindings = fixtures(context);
            if (bindings == null) return;
            ServerPlayer player = context.player();
            clearLedgers(player);
            PillProbeActions.calls = 0;
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.SPIDER_EYE));
            boolean finished = finish(player, 4);
            leg(context, "reentry", finished && PillProbeActions.calls == 1 && close(PillService.toxicity(player), 7.0D)
                            && PillService.uses(player, bindings.reenter()) == 1 && PillService.uses(player, bindings.toxicity()) == 0,
                    "calls=" + PillProbeActions.calls + " tox=" + PillService.toxicity(player) + " uses=" + PillService.uses(player, bindings.reenter()),
                    "calls=1 tox=7 uses=1 nested=0");
        });
    }

    private static void cooldownMaxUses(ProbeContext context) {
        run(context, spawned -> {
            Bindings bindings = fixtures(context);
            if (bindings == null) return;
            ServerPlayer player = context.player();
            ServerLevelData clock = context.server().getWorldData().overworldData();
            clearLedgers(player);
            long atConsume = PillService.overworldGameTime(player);
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.SWEET_BERRIES));
            boolean finished = finish(player, 4);
            long until = PillService.cooldownUntil(player, bindings.limited());
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
            leg(context, "cooldown_max_uses", finished && until == atConsume + 20 && !immediate && !at19 && second
                            && PillService.uses(player, bindings.limited()) == 2 && !third,
                    "until=" + until + " now=" + atConsume + " immediate=" + immediate + " at19=" + at19 + " second=" + second
                            + " uses=" + PillService.uses(player, bindings.limited()) + " third=" + third,
                    "until=now+20 reject@19 accept@20 uses=2 reject_after");
        });
    }

    private static void plainItemArmed(ProbeContext context) {
        run(context, spawned -> {
            Bindings bindings = fixtures(context);
            if (bindings == null) return;
            ServerPlayer player = context.player();
            // A pill bound to an item with no use of its own: the click arms the carrier's cycle for it, vanilla eats
            // one, and the item is left as its author wrote it. Driven without a client, the way a click handler does.
            clearLedgers(player);
            feed(player, 20, 5.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT, 2));
            ItemStack ingot = player.getMainHandItem();
            PillUseService.arm(player, ingot);
            boolean armed = ingot.getUseDuration(player) == 32 && !ingot.has(DataComponents.CONSUMABLE);
            int ingotsBefore = count(player, Items.GOLD_INGOT);
            boolean swallowed = finish(player, 40);
            leg(context, "plain_item_armed", armed && swallowed && close(PillService.toxicity(player), 25.0D)
                            && count(player, Items.GOLD_INGOT) == ingotsBefore - 1
                            && !player.getMainHandItem().has(DataComponents.CONSUMABLE)
                            && PillService.uses(player, bindings.plainItem()) == 1,
                    "armed=" + armed + " eaten=" + swallowed + " reason=" + stopReason + " tox=" + PillService.toxicity(player)
                            + " ingots=" + count(player, Items.GOLD_INGOT)
                            + " component=" + player.getMainHandItem().has(DataComponents.CONSUMABLE)
                            + " uses=" + PillService.uses(player, bindings.plainItem()),
                    "armed=true eaten=true tox=25 ingots-1 component=false uses=1");
        });
    }

    private static void overdoseDetox(ProbeContext context) {
        run(context, spawned -> {
            Bindings bindings = fixtures(context);
            if (bindings == null) return;
            ServerPlayer player = context.player();
            clearLedgers(player);
            boolean doses = true;
            for (int dose = 0; dose < 4 && doses; dose++) {
                feed(player, 1, 0.0F);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HONEY_BOTTLE));
                doses = finish(player, 40);
            }
            double afterFour = PillService.toxicity(player);
            new ModifyPillToxicityAction(ModifyMode.ADD, new Constant(-30.0D)).execute(player, FormulaContext.of(player));
            boolean cleared = new PillToxicityCondition(new Comparison(CompareOperation.EQUAL, 0.0D)).test(player, FormulaContext.of(player));
            leg(context, "overdose_detox", doses && close(afterFour, 20.0D) && close(PillService.toxicity(player), 0.0D)
                            && PillService.uses(player, bindings.toxicity()) == 4 && cleared,
                    "doses=" + doses + " reason=" + stopReason + " after4=" + afterFour + " afterDetox=" + PillService.toxicity(player)
                            + " uses=" + PillService.uses(player, bindings.toxicity()),
                    "4 doses tox=20 then 0 uses=4");
        });
    }

    private static void decay(ProbeContext context) {
        run(context, spawned -> {
            ServerPlayer player = context.player();
            ServerLevel level = context.level();
            ServerLevelData clock = context.server().getWorldData().overworldData();
            long savedTime = context.server().overworld().getGameTime();
            clock.setGameTime(savedTime);
            MxtServerConfig.INSTANCE.alchemy.toxicityDecayPerSecond.setValue(2.0D);
            Pig quiet = new Pig(EntityType.PIG, player.level());
            quiet.setPos(player.getX() + 1, player.getY(), player.getZ());
            player.level().addFreshEntity(quiet);
            spawned.add(quiet);
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
            leg(context, "decay", empty && close(held, 10.0D) && close(offline, 10.0D) && close(early, 10.0D)
                            && remainder == 19 && close(decayed, 8.0D),
                    "empty=" + empty + " held=" + held + " offline=" + offline + " early=" + early
                            + " remainder=" + remainder + " decayed=" + decayed,
                    "empty held=10 offline=10 early=10 remainder=19 decayed=8");
        });
    }

    private static void overlayKeepsLimits(ProbeContext context) {
        run(context, spawned -> {
            Bindings bindings = fixtures(context);
            if (bindings == null) return;
            ServerPlayer player = context.player();
            ServerLevelData clock = context.server().getWorldData().overworldData();
            clearLedgers(player);
            feed(player, 10, 0.0F);
            long overlayAt = PillService.overworldGameTime(player);
            // The component names another pill than the binding does: its effects win, the binding's caps stay.
            PillComponent overlaid = component(context, "{\"pill\":\"mxt_test:toxicity\",\"toxicity_gain\":9}");
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.SWEET_BERRIES, overlaid));
            boolean overlayFirst = finish(player, 4);
            long overlayUntil = PillService.cooldownUntil(player, bindings.limited());
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
            leg(context, "overlay_keeps_limits", overlayFirst && overlayUntil == overlayAt + 20 && !overlayImmediate
                            && overlaySecond && !overlayThird && close(PillService.toxicity(player), 18.0D)
                            && PillService.uses(player, bindings.limited()) == 2 && PillService.uses(player, bindings.toxicity()) == 0,
                    "first=" + overlayFirst + " until=" + overlayUntil + " at=" + overlayAt
                            + " immediate=" + overlayImmediate + " second=" + overlaySecond + " third=" + overlayThird
                            + " tox=" + PillService.toxicity(player) + " limited=" + PillService.uses(player, bindings.limited())
                            + " toxicityUses=" + PillService.uses(player, bindings.toxicity()),
                    "gain=9 cooldown=20 uses=2 toxicityUses=0");
        });
    }

    private static void componentOverBinding(ProbeContext context) {
        run(context, spawned -> {
            Bindings bindings = fixtures(context);
            if (bindings == null) return;
            ServerPlayer player = context.player();
            clearLedgers(player);
            feed(player, 10, 0.0F);
            PillComponent overtaken = component(context, "{\"pill\":\"mxt_test:blocked\"}");
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(Items.HONEY_BOTTLE, overtaken));
            int honeyCount = player.getMainHandItem().getCount();
            boolean overtakenStarted = begin(player);
            player.stopUsingItem();
            leg(context, "component_over_binding", !overtakenStarted
                            && player.getMainHandItem().getCount() == honeyCount
                            && PillService.toxicity(player) == 0.0D && PillService.uses(player, bindings.toxicity()) == 0,
                    "started=" + overtakenStarted + " reason=" + refusal(player) + " count=" + player.getMainHandItem().getCount()
                            + " tox=" + PillService.toxicity(player),
                    "started=false reason=conditions count=" + honeyCount + " tox=0");
        });
    }

    private static void componentOnly(ProbeContext context) {
        run(context, spawned -> {
            Bindings bindings = fixtures(context);
            if (bindings == null) return;
            ServerPlayer player = context.player();
            clearLedgers(player);
            PillComponent only = component(context, "{\"toxicity_gain\":4}");
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(MxtItems.PILL.get(), only));
            boolean onlyFirst = finish(player, 4);
            player.setItemInHand(InteractionHand.MAIN_HAND, dosed(MxtItems.PILL.get(), only));
            boolean onlySecond = finish(player, 4);
            leg(context, "component_only", onlyFirst && onlySecond && close(PillService.toxicity(player), 8.0D)
                            && !player.hasData(MxtAttachments.PILL_USAGE)
                            && PillService.uses(player, bindings.limited()) == 0 && PillService.uses(player, bindings.toxicity()) == 0
                            && PillService.uses(player, bindings.hungry()) == 0 && PillService.cooldownUntil(player, bindings.limited()) == 0L,
                    "first=" + onlyFirst + " second=" + onlySecond + " tox=" + PillService.toxicity(player)
                            + " limited=" + PillService.uses(player, bindings.limited())
                            + " toxicityUses=" + PillService.uses(player, bindings.toxicity()),
                    "gain=4 twice no bound counter");
        });
    }

    // The fixture gate the old command ran before any leg: six bindings present and the excluded pill absent.
    private static Bindings fixtures(ProbeContext context) {
        Holder<PillBinding> blocked = binding(context, "blocked_pill");
        Holder<PillBinding> hungry = binding(context, "hungry_pill");
        Holder<PillBinding> reenter = binding(context, "reenter_pill");
        Holder<PillBinding> limited = binding(context, "limited_pill");
        Holder<PillBinding> toxicity = binding(context, "toxicity_pill");
        Holder<PillBinding> plainItem = binding(context, "plain_item_pill");
        boolean missingPill = pill(context, "disabled_ref") == null;
        if (blocked == null || hungry == null || reenter == null || limited == null
                || toxicity == null || plainItem == null || !missingPill) {
            context.fail("pill probe: fixture missing");
            return null;
        }
        return new Bindings(blocked, hungry, reenter, limited, toxicity, plainItem);
    }

    private static Holder<PillBinding> binding(ProbeContext context, String path) {
        return MxtDatapackRegistries.holder(context.registries(), MxtResourceKeys.PILL_BINDING, context.id(path)).orElse(null);
    }

    private static Holder<Pill> pill(ProbeContext context, String path) {
        return MxtDatapackRegistries.holder(context.registries(), MxtResourceKeys.PILL, context.id(path)).orElse(null);
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

    private static PillComponent component(ProbeContext context, String json) {
        return PillComponent.CODEC.parse(context.registries().createSerializationContext(JsonOps.INSTANCE),
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
        return QualityService.check(player, player.getMainHandItem())
                .map(failure -> failure.name().toLowerCase(Locale.ROOT))
                .orElse("none") + " sat=" + player.getFoodData().getSaturationLevel();
    }

    private static PillUsageAttachment copyUsage(PillUsageAttachment usage, ProbeContext context) {
        Codec<PillUsageAttachment> codec = PillUsageAttachment.CODEC.codec();
        RegistryOps<Tag> ops = context.registries().createSerializationContext(NbtOps.INSTANCE);
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

    private static boolean leg(ProbeContext context, String name, boolean ok, String actual, String expected) {
        String line = "pill probe: " + name + " actual=" + actual + " expected=" + expected + (ok ? " OK" : " MISMATCH");
        if (ok) context.note(line);
        else context.fail(line);
        return ok;
    }
}
