package com.iafenvoy.mxt.probe.artifact;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.target.AreaTargetSelector;
import com.iafenvoy.mxt.data.ability.type.ActiveAbilityType;
import com.iafenvoy.mxt.data.ability.type.StorageAbilityType;
import com.iafenvoy.mxt.data.action.NoOpAction;
import com.iafenvoy.mxt.data.storage.DataStorageHolder;
import com.iafenvoy.mxt.data.storage.builtin.ChargesDataStorage;
import com.iafenvoy.mxt.data.storage.builtin.ContainerDataStorage;
import com.iafenvoy.mxt.data.storage.builtin.CooldownDataStorage;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactStorageService;
import com.iafenvoy.mxt.runtime.item.ItemStorageService;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.connection.ConnectionType;

import java.util.List;

/**
 * The state a carrier keeps for its abilities: the container one storage ability declares, the address a value lives
 * at, which kinds a type declares for itself, and the four action fields living on the type that runs them.
 */
public final class ArtifactStorageProbes {
    private ArtifactStorageProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("artifact/storage", ArtifactStorageProbes::storage),
                Probe.kitted("artifact/storage/address", ArtifactStorageProbes::storageAddress),
                Probe.of("artifact/ability/state", ArtifactStorageProbes::abilityState),
                Probe.of("artifact/ability/actions", ArtifactStorageProbes::abilityActions));
    }

    // The container an ability declares is one entry of the carrier's own ability storage, addressed by that
    // ability's id: slot 1 of 27, the untouched slots left as holes, and one round trip of the synced component.
    private static void storage(ProbeContext context) {
        RegistryAccess access = context.registries();
        ServerPlayer player = context.player();
        ItemStack jadeStack = new ItemStack(Items.AMETHYST_SHARD);
        ArtifactService.refine(jadeStack, player);
        Holder<Ability> storageAbility = ArtifactService.abilities(access, jadeStack).stream()
                .filter(ref -> ref.value().type() instanceof StorageAbilityType).findFirst().orElse(null);
        Holder<Ability> foreignStorage = context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.PROBE_BOUND_STORAGE);
        boolean stored = storageAbility != null
                && ArtifactStorageService.INSTANCE.set(access, jadeStack, storageAbility, 1, new ItemStack(Items.STONE, 3), player)
                // A second write of the same kind replaces that ability's one entry instead of adding a second.
                && ArtifactStorageService.INSTANCE.set(access, jadeStack, storageAbility, 2, new ItemStack(Items.DIRT), player);
        DataStorageHolder jadeStorage = jadeStack.get(MxtDataComponents.STORAGE);
        boolean container = stored
                && jadeStorage != null && jadeStorage.count(ContainerDataStorage.class) == 1
                && ArtifactStorageService.INSTANCE.get(access, jadeStack, storageAbility, 1, player).is(Items.STONE)
                && ArtifactStorageService.INSTANCE.get(access, jadeStack, storageAbility, 1, player).getCount() == 3
                // The capacity is the definition's, so the slot one past it is not there.
                && ArtifactStorageService.INSTANCE.get(access, jadeStack, storageAbility, 27, player).isEmpty()
                && ItemStorageService.get(jadeStack, storageAbility, ContainerDataStorage.class)
                .filter(value -> value.contents().size() == 27 && value.get(0).isEmpty()
                        && value.get(1).is(Items.STONE) && value.get(2).is(Items.DIRT)).isPresent()
                // Another ability's id is a different slot: two storage abilities on one carrier never share a box.
                && ItemStorageService.get(jadeStack, foreignStorage, ContainerDataStorage.class).isEmpty()
                && roundTripsStorage(jadeStorage, storageAbility, access);
        context.check("artifact roster container 27 slots, slot 1 = stone x3, slot 2 = dirt, one entry per (ability, kind), codec round-trip", container);
    }

    // One (id, kind) addresses exactly one value, so a second entry for the same address is malformed input.
    private static void storageAddress(ProbeContext context) {
        // The id has to name a registered ability: an unresolvable one is dropped by the tolerant list codec, and the
        // empty list that is left behind would let both parses look like successes.
        JsonElement oneEntry = JsonParser.parseString(
                "[{\"id\": \"mxt_test:probe_upkeep\", \"value\": {\"type\": \"mxt:cooldown\", \"duration\": 20, \"started_at\": 1}}]");
        JsonElement twoEntries = JsonParser.parseString(
                "[{\"id\": \"mxt_test:probe_upkeep\", \"value\": {\"type\": \"mxt:cooldown\", \"duration\": 20, \"started_at\": 1}},"
                        + "{\"id\": \"mxt_test:probe_upkeep\", \"value\": {\"type\": \"mxt:cooldown\", \"duration\": 40, \"started_at\": 2}}]");
        // The entries name an ability through a holder, so the parse needs the level's registries: plain JsonOps
        // cannot resolve it and both sides of the comparison would read as a failure.
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, context.registries());
        DataStorageHolder oneParsed = DataStorageHolder.CODEC.parse(ops, oneEntry).result().orElse(null);
        DataStorageHolder twoParsed = DataStorageHolder.CODEC.parse(ops, twoEntries).result().orElse(null);
        boolean oneEntryAccepted = oneParsed != null && oneParsed.count(CooldownDataStorage.class) == 1;
        boolean twoEntriesRefused = twoParsed == null;
        context.check("artifact roster storage accepts one entry for an address ("
                + (oneParsed == null ? "absent" : oneParsed.count(CooldownDataStorage.class)) + ")", oneEntryAccepted);
        context.check("artifact roster storage refuses two entries for one (id, kind) ("
                + (twoParsed == null ? "refused" : twoParsed.count(CooldownDataStorage.class) + " kept") + ")", twoEntriesRefused);
    }

    // The state kinds belong to the type now: the removed `components` key is ignored like any other unread field,
    // and the one pool a type cannot know (charges) is the ability's own field.
    private static void abilityState(ProbeContext context) {
        JsonObject removedComponents = new JsonObject();
        removedComponents.addProperty("type", "mxt:active");
        removedComponents.add("components", JsonParser.parseString(
                "[{\"type\": \"mxt:cooldown\", \"ticks\": 20}, {\"type\": \"mxt:cooldown\", \"ticks\": 40}]"));
        JsonObject chargedChannel = new JsonObject();
        chargedChannel.addProperty("type", "mxt:channelled");
        chargedChannel.add("charges", JsonParser.parseString("{\"maximum\": 2, \"recharge_ticks\": 40}"));
        Ability byType = Ability.DIRECT_CODEC.parse(JsonOps.INSTANCE, removedComponents).result().orElse(null);
        Ability declaredOnce = Ability.DIRECT_CODEC.parse(JsonOps.INSTANCE, chargedChannel).result().orElse(null);
        boolean stateByType = byType != null && declaredOnce != null
                && byType.storages().stream().filter(CooldownDataStorage.class::isInstance).count() == 1
                && byType.charges().isEmpty()
                && declaredOnce.charges().isPresent()
                && declaredOnce.storages().stream().filter(ChargesDataStorage.class::isInstance).count() == 1;
        context.check("artifact roster ability declares its state by type, removed components ignored", stateByType);
    }

    // Every type that runs actions carries the four fields itself, so one skill is one definition: the fields
    // decode straight onto the acting type.
    private static void abilityActions(ProbeContext context) {
        JsonObject timed = new JsonObject();
        timed.addProperty("type", "mxt:active");
        timed.add("target_selector", JsonParser.parseString("{\"type\": \"mxt:area\", \"radius\": 3}"));
        timed.add("bi_entity_action", JsonParser.parseString("{\"type\": \"mxt:send_message\", \"message\": \"probe\"}"));
        Ability timedAbility = Ability.DIRECT_CODEC.parse(JsonOps.INSTANCE, timed).result().orElse(null);
        Ability elemental = context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.PROBE_ELEMENTAL).value();
        boolean actionsByType = timedAbility != null && timedAbility.type() instanceof ActiveAbilityType active
                && active.targetSelector() instanceof AreaTargetSelector
                && !(active.biEntityAction() instanceof NoOpAction)
                && elemental.type() instanceof ActiveAbilityType carried
                && !(carried.biEntityAction() instanceof NoOpAction);
        context.check("artifact roster action fields live on the acting type", actionsByType);
    }

    // Sends the ability storage component through the registered network codec and reads it back: the one place an
    // empty stack in the list could take the whole container slot packet down.
    private static boolean roundTripsStorage(DataStorageHolder holder, Holder<Ability> ability, RegistryAccess registries) {
        if (holder == null) return false;
        DataComponentType<DataStorageHolder> type = MxtDataComponents.STORAGE.get();
        // The connection type only tells NeoForge what the other end is; NEOFORGE is what this server's own
        // client is, which is who the codec under test would be encoding for.
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.NEOFORGE);
        try {
            type.streamCodec().encode(buffer, holder);
            DataStorageHolder decoded = type.streamCodec().decode(buffer);
            return decoded.get(ability, ContainerDataStorage.class)
                    .filter(value -> value.contents().size() == 27 && value.get(0).isEmpty()
                            && value.get(1).is(Items.STONE) && value.get(1).getCount() == 3)
                    .isPresent();
        } catch (RuntimeException error) {
            return false;
        }
    }
}
