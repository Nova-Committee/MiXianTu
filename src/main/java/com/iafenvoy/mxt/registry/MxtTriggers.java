package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.trigger.JsTrigger;
import com.iafenvoy.mxt.data.trigger.Trigger;
import com.iafenvoy.mxt.data.trigger.Trigger.Builtin;
import com.iafenvoy.mxt.data.trigger.TriggerSignals;
import com.iafenvoy.mxt.data.trigger.VanillaTrigger;
import com.iafenvoy.mxt.data.trigger.VanillaTrigger.Matcher;
import com.iafenvoy.mxt.data.trigger.VanillaTriggerMatchers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.advancements.criterion.BredAnimalsTrigger;
import net.minecraft.advancements.criterion.BrewedPotionTrigger;
import net.minecraft.advancements.criterion.ChangeDimensionTrigger;
import net.minecraft.advancements.criterion.ConsumeItemTrigger;
import net.minecraft.advancements.criterion.DistanceTrigger;
import net.minecraft.advancements.criterion.EffectsChangedTrigger;
import net.minecraft.advancements.criterion.EnterBlockTrigger;
import net.minecraft.advancements.criterion.EntityHurtPlayerTrigger;
import net.minecraft.advancements.criterion.FallAfterExplosionTrigger;
import net.minecraft.advancements.criterion.FilledBucketTrigger;
import net.minecraft.advancements.criterion.FishingRodHookedTrigger;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.advancements.criterion.ItemDurabilityTrigger.TriggerInstance;
import net.minecraft.advancements.criterion.KilledTrigger;
import net.minecraft.advancements.criterion.LevitationTrigger;
import net.minecraft.advancements.criterion.LightningStrikeTrigger;
import net.minecraft.advancements.criterion.PickedUpItemTrigger;
import net.minecraft.advancements.criterion.PlayerHurtEntityTrigger;
import net.minecraft.advancements.criterion.PlayerInteractTrigger;
import net.minecraft.advancements.criterion.PlayerTrigger;
import net.minecraft.advancements.criterion.ShotCrossbowTrigger;
import net.minecraft.advancements.criterion.StartRidingTrigger;
import net.minecraft.advancements.criterion.TameAnimalTrigger;
import net.minecraft.advancements.criterion.TradeTrigger;
import net.minecraft.advancements.criterion.UsedTotemTrigger;
import net.minecraft.advancements.criterion.UsingItemTrigger;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Trigger matchers. Two families live here: the signals MiXianTu publishes itself, which only carry the
 * signal id, and the ported vanilla triggers, which keep vanilla's own instance codec and matching.
 * Third-party modules can register additional codecs into the same registry.
 */
@SuppressWarnings("unused")
public final class MxtTriggers {
    public static final DeferredRegister<MapCodec<? extends Trigger>> REGISTRY = DeferredRegister.create(MxtRegistries.TRIGGER_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> TICK = register("tick");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> ATTACK = register("attack");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> HURT = register("hurt");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> KILL = register("kill");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> BLOCK_BREAK = register("block_break");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> BLOCK_USE = register("block_use");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> ITEM_USE = register("item_use");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> EQUIP = register("equip");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> DEATH = register("death");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> BREAKTHROUGH = register("breakthrough");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> TECHNIQUE_STAGE = register("technique_stage");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<JsTrigger>> JS = REGISTRY.register("js", () -> JsTrigger.CODEC);

    static {
        port(TriggerSignals.CONSUME_ITEM, ConsumeItemTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::consumeItem);
        port(TriggerSignals.BREWED_POTION, BrewedPotionTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::brewedPotion);
        port(TriggerSignals.TAME_ANIMAL, TameAnimalTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::tameAnimal);
        port(TriggerSignals.BRED_ANIMALS, BredAnimalsTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::bredAnimals);
        port(TriggerSignals.VILLAGER_TRADE, TradeTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::villagerTrade);
        port(TriggerSignals.USED_TOTEM, UsedTotemTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::usedTotem);
        port(TriggerSignals.STARTED_RIDING, StartRidingTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::startedRiding);
        port(TriggerSignals.CHANGED_DIMENSION, ChangeDimensionTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::changedDimension);
        port(TriggerSignals.EFFECTS_CHANGED, EffectsChangedTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::effectsChanged);
        port(TriggerSignals.LIGHTNING_STRIKE, LightningStrikeTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::lightningStrike);
        port(TriggerSignals.PLAYER_HURT_ENTITY, PlayerHurtEntityTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::playerHurtEntity);
        port(TriggerSignals.ENTITY_HURT_PLAYER, EntityHurtPlayerTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::entityHurtPlayer);
        port(TriggerSignals.PLAYER_KILLED_ENTITY, KilledTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::playerKilledEntity);
        port(TriggerSignals.ENTITY_KILLED_PLAYER, KilledTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::entityKilledPlayer);
        port(TriggerSignals.SHOT_CROSSBOW, ShotCrossbowTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::shotCrossbow);
        port(TriggerSignals.PLAYER_INTERACTED_WITH_ENTITY, PlayerInteractTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::playerInteractedWithEntity);
        port(TriggerSignals.FISHING_ROD_HOOKED, FishingRodHookedTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::fishingRodHooked);
        port(TriggerSignals.THROWN_ITEM_PICKED_UP_BY_PLAYER, PickedUpItemTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::thrownItemPickedUpByPlayer);
        port(TriggerSignals.ENTER_BLOCK, EnterBlockTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::enterBlock);
        port(TriggerSignals.LOCATION, PlayerTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::playerOnly);
        port(TriggerSignals.SLEPT_IN_BED, PlayerTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::playerOnly);
        port(TriggerSignals.LEVITATION, LevitationTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::levitation);
        port(TriggerSignals.FALL_FROM_HEIGHT, DistanceTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::fallFromHeight);
        port(TriggerSignals.FALL_AFTER_EXPLOSION, FallAfterExplosionTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::fallAfterExplosion);
        port(TriggerSignals.RIDE_ENTITY_IN_LAVA, DistanceTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::rideEntityInLava);
        port(TriggerSignals.NETHER_TRAVEL, DistanceTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::netherTravel);
        port(TriggerSignals.INVENTORY_CHANGED, InventoryChangeTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::inventoryChanged);
        port(TriggerSignals.USING_ITEM, UsingItemTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::usingItem);
        port(TriggerSignals.FILLED_BUCKET, FilledBucketTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::filledBucket);
        port(TriggerSignals.ITEM_DURABILITY_CHANGED, TriggerInstance.CODEC, VanillaTriggerMatchers::itemDurabilityChanged);
    }

    private static DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> register(String signal) {
        return REGISTRY.register(signal, () -> MapCodec.unit(new Builtin(
                TriggerSignals.id(signal))));
    }

    /**
     * Registers one ported vanilla trigger. The type name is derived from the signal, and the payload codec is
     * vanilla's own instance codec, so the signal a definition listens to and the arguments it can write can
     * never disagree with the trigger they mirror.
     */
    private static <T extends CriterionTriggerInstance> void port(Identifier signal, Codec<T> codec, Matcher<T> matcher) {
        REGISTRY.register(signal.getPath(), () -> VanillaTrigger.mapCodec(signal, codec, matcher));
    }
}
