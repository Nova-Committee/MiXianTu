package com.iafenvoy.mxt.probe.world;

import com.iafenvoy.mxt.attachment.SoulAttachment;
import com.iafenvoy.mxt.command.server.SoulCommand;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.entity.SoulEntity;
import com.iafenvoy.mxt.event.SoulEvent;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtEntityTypes;
import com.iafenvoy.mxt.runtime.world.SoulAppearance;
import com.iafenvoy.mxt.runtime.world.SoulEventBridge;
import com.iafenvoy.mxt.runtime.world.SoulService;
import com.iafenvoy.mxt.runtime.world.SoulSnapshot;
import com.mojang.brigadier.CommandDispatcher;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class SoulProbes {
    private SoulProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("world/soul/snapshot", SoulProbes::snapshot),
                Probe.of("world/soul/player", SoulProbes::player),
                Probe.of("world/soul/appearance", SoulProbes::appearance),
                Probe.of("world/soul/persistence", SoulProbes::persistence),
                Probe.of("world/soul/lifecycle", SoulProbes::lifecycle),
                Probe.of("world/soul/commands", SoulProbes::commands),
                Probe.of("world/soul/death_generation", SoulProbes::deathGeneration),
                Probe.of("world/soul/timeout", SoulProbes::timeout));
    }

    private static <T extends LivingEntity> T source(ProbeContext c, EntityType<T> type) {
        T source = type.create(c.level(), EntitySpawnReason.LOAD);
        if (source == null) throw new IllegalStateException("Missing source " + type);
        source.setPos(c.at(8, 1, 8).getCenter());
        return source;
    }

    private static SoulEntity manifestation(ProbeContext c, Entity source) {
        UUID id = source.getExistingData(MxtAttachments.SOUL).orElseThrow().manifestation().orElseThrow();
        return (SoulEntity) c.level().getEntity(id);
    }

    private static CompoundTag save(Entity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    private static void snapshot(ProbeContext c) {
        Sheep sheep = source(c, EntityType.SHEEP);
        sheep.setColor(DyeColor.RED);
        sheep.setCustomName(Component.literal("Snapshot Sheep"));
        sheep.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        sheep.getData(MxtAttachments.SOUL_STATS).setKarma(12.5);
        sheep.getPersistentData().putString("private_marker", "mxt_soul_private_inventory_123");
        CompoundTag before = save(sheep);
        SoulSnapshot snapshot = SoulSnapshot.capture(sheep);
        c.expect("snapshot exactly preserves the vanilla entity save", snapshot.data(), before);
        c.expect("snapshot retains the original UUID", snapshot.data().read("UUID", UUIDUtil.CODEC).orElseThrow(), sheep.getUUID());
        sheep.setColor(DyeColor.BLUE);
        sheep.setCustomName(Component.literal("Changed"));
        sheep.getItemBySlot(EquipmentSlot.HEAD).shrink(1);
        sheep.getData(MxtAttachments.SOUL_STATS).setKarma(0);
        c.expect("later source changes do not alter the snapshot", snapshot.data(), before);
        CompoundTag exposed = snapshot.data();
        exposed.putString("CustomName", "corrupt");
        c.expect("reading NBT returns an isolated copy", snapshot.data(), before);
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, c.registries());
        SoulSnapshot decoded = SoulSnapshot.CODEC.parse(ops, SoulSnapshot.CODEC.encodeStart(ops, snapshot).getOrThrow()).getOrThrow();
        c.expect("snapshot codec preserves the complete save", decoded.data(), before);
        Sheep restored = (Sheep) decoded.appearanceSource(c.level()).orElseThrow();
        c.expect("reconstruction preserves the saved variant", restored.getColor(), DyeColor.RED);
        c.check("temporary source is not added to the world", c.level().getEntity(restored.getUUID()) == null);
        c.check("temporary source does not restore attachments", restored.getExistingData(MxtAttachments.SOUL_STATS).isEmpty());
        c.note("snapshot: complete save/UUID/attachments/equipment/isolation/codec/variant OK");
    }

    private static void player(ProbeContext c) {
        ServerPlayer player = c.player();
        ClientInformation options = player.clientInformation();
        int parts = PlayerModelPart.HAT.getMask() | PlayerModelPart.RIGHT_SLEEVE.getMask();
        player.updateOptions(new ClientInformation(options.language(), options.viewDistance(), options.chatVisibility(), options.chatColors(),
                parts, HumanoidArm.LEFT, options.textFilteringEnabled(), options.allowsListing(), options.particleStatus()));
        player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 17));
        player.getEnderChestInventory().setItem(3, new ItemStack(Items.DIAMOND, 5));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        player.getData(MxtAttachments.SOUL_STATS).setSoulStrength(42.25);
        CompoundTag before = save(player);
        c.check("player generates a manifestation", SoulService.transfer(player, "probe"));
        SoulEntity soul = manifestation(c, player);
        try {
            SoulSnapshot snapshot = soul.snapshot().orElseThrow();
            c.expect("player snapshot retains complete inventory and custom state", snapshot.data(), before);
            c.expect("player profile retains the original identity", snapshot.profile().orElseThrow().partialProfile(), player.getGameProfile());
            c.expect("soul receives a new UUID", soul.getUUID().equals(player.getUUID()), false);
            c.expect("origin points at the original player", soul.origin().orElseThrow(), player.getUUID());
            c.check("source's soul attachment is activated after capture", player.getData(MxtAttachments.SOUL).active());
            LivingEntity restored = snapshot.appearanceSource(c.level()).orElseThrow();
            c.expect("temporary player reconstructs held equipment", restored.getItemBySlot(EquipmentSlot.OFFHAND).getItem(), Items.SHIELD);
            c.expect("player reconstruction retains the main hand", restored.getMainArm(), HumanoidArm.LEFT);
            for (PlayerModelPart part : PlayerModelPart.values())
                c.expect("player reconstruction retains layer " + part, ((Player) restored).isModelPartShown(part), player.isModelPartShown(part));
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, c.registries());
            SoulSnapshot decoded = SoulSnapshot.CODEC.parse(ops, SoulSnapshot.CODEC.encodeStart(ops, snapshot).getOrThrow()).getOrThrow();
            c.expect("save codec retains player visual settings", decoded.playerVisual(), snapshot.playerVisual());
            c.expect("source still holds its inventory", player.getInventory().getItem(9).getCount(), 17);
            c.expect("source still holds its ender inventory", player.getEnderChestInventory().getItem(3).getCount(), 5);
            c.check("explicit capture does not kill the source", player.isAlive());
        } finally {
            SoulService.reclaim(player);
            soul.discard();
        }
        c.note("player: complete state/profile/new identity/inert source OK");
    }

    private static void appearance(ProbeContext c) {
        Sheep sheep = source(c, EntityType.SHEEP);
        sheep.setColor(DyeColor.CYAN);
        sheep.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        sheep.getPersistentData().putString("secret", "mxt_soul_private_inventory_123");
        SoulSnapshot snapshot = SoulSnapshot.capture(sheep);
        SoulAppearance visual = SoulAppearance.capture(sheep, snapshot);
        RegistryFriendlyByteBuf wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), c.registries());
        try {
            SoulAppearance.STREAM_CODEC.encode(wire, visual);
            c.check("appearance does not transmit full NBT", !wire.toString(0, wire.readableBytes(), StandardCharsets.ISO_8859_1).contains("mxt_soul_private_inventory_123"));
            SoulAppearance decoded = SoulAppearance.STREAM_CODEC.decode(wire);
            c.expect("appearance reads the entire packet", wire.readableBytes(), 0);
            Sheep proxy = source(c, EntityType.SHEEP);
            proxy.getEntityData().assignValues(decoded.metadata());
            decoded.equipment().forEach(proxy::setItemSlot);
            c.expect("metadata restores the original variant", proxy.getColor(), DyeColor.CYAN);
            c.expect("equipment restores the original item", proxy.getItemBySlot(EquipmentSlot.HEAD).getItem(), Items.GOLDEN_HELMET);
            decoded.equipment().get(EquipmentSlot.HEAD).shrink(1);
            c.expect("network state cannot be mutated through its accessor", decoded.equipment().get(EquipmentSlot.HEAD).getCount(), 1);
            c.expect("metadata capture leaves source data intact", sheep.getColor(), DyeColor.CYAN);
            c.expect("all bytes are appearance data", decoded.entityType(), snapshot.entityType());
        } finally {
            wire.release();
        }
        c.note("appearance: metadata/equipment/network/privacy/isolation OK");
    }

    private static void persistence(ProbeContext c) {
        Slime slime = source(c, EntityType.SLIME);
        slime.setSize(4, true);
        c.check("slime generates a soul", SoulService.transfer(slime, "probe"));
        SoulEntity first = manifestation(c, slime);
        SoulEntity loaded = new SoulEntity(MxtEntityTypes.SOUL.get(), c.level());
        try {
            CompoundTag saved = save(first);
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, c.registries(), saved));
            c.expect("save reload preserves the full snapshot", loaded.snapshot().orElseThrow().data(), first.snapshot().orElseThrow().data());
            c.expect("save reload preserves the origin", loaded.origin(), first.origin());
            c.expect("save reload preserves the source", loaded.source(), "probe");
            SoulAppearance visual = loaded.appearance().orElseThrow();
            Slime proxy = source(c, EntityType.SLIME);
            proxy.getEntityData().assignValues(visual.metadata());
            c.expect("save reload rebuilds size metadata", proxy.getSize(), 4);
            close(c, "culling width follows the source", loaded.getBbWidth(), slime.getBbWidth());
            close(c, "culling height follows the source", loaded.getBbHeight(), slime.getBbHeight());
            c.check("soul is noncolliding and invulnerable", loaded.noPhysics && loaded.isNoGravity() && !loaded.isPushable() && !loaded.isPickable()
                    && !loaded.hurtServer(c.level(), c.level().damageSources().generic(), 100));
            loaded.tick();
            c.expect("tick does not run the source or expire the soul", loaded.isRemoved(), false);
            c.expect("tick leaves the source snapshot intact", loaded.snapshot().orElseThrow().data(), first.snapshot().orElseThrow().data());
            loaded.setPos(0, c.level().getMinY() - 65, 0);
            loaded.checkBelowWorld();
            c.check("soul persists in the void and cannot enter portals", !loaded.isRemoved() && !loaded.canUsePortal(false));
            CompoundTag withoutSnapshot = saved.copy();
            withoutSnapshot.remove("snapshot");
            withoutSnapshot.putBoolean("NoGravity", false);
            withoutSnapshot.putBoolean("Invulnerable", false);
            withoutSnapshot.store("Motion", Vec3.CODEC, new Vec3(1, -1, 2));
            loaded.clientAppearance(new Object());
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, c.registries(), withoutSnapshot));
            c.check("loading without a snapshot clears stale appearance", loaded.snapshot().isEmpty() && loaded.appearance().isEmpty() && loaded.clientAppearance() == null);
            c.check("loading keeps the manifestation inert", loaded.noPhysics && loaded.isNoGravity() && loaded.isInvulnerable());
            c.expect("loading rejects stored momentum", loaded.getDeltaMovement(), Vec3.ZERO);
        } finally {
            SoulService.reclaim(slime);
            first.discard();
            loaded.discard();
        }
        c.note("persistence: snapshot/rebuilt metadata/dimensions/inert lifecycle OK");
    }

    private static void lifecycle(ProbeContext c) {
        ServerPlayer player = c.player();
        Consumer<SoulEvent.TransferPre> cancel = event -> { if (event.getEntity() == player) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            c.check("cancelled transfer does not activate the source", !SoulService.transfer(player, "cancelled") && !player.getData(MxtAttachments.SOUL).active());
        } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        Consumer<EntityJoinLevelEvent> reject = event -> { if (event.getEntity() instanceof SoulEntity) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(reject);
        try {
            c.check("spawn failure does not activate the source", !SoulService.transfer(player, "rejected") && !player.getData(MxtAttachments.SOUL).active());
        } finally { NeoForge.EVENT_BUS.unregister(reject); }
        AtomicInteger nested = new AtomicInteger();
        Consumer<SoulEvent.TransferPre> reenter = event -> {
            if (event.getEntity() == player) {
                if (!SoulService.transfer(player, "recursive")) nested.incrementAndGet();
                if (!SoulService.reclaim(player)) nested.incrementAndGet();
            }
        };
        NeoForge.EVENT_BUS.addListener(reenter);
        SoulEntity soul = null;
        try {
            c.check("valid transfer succeeds after earlier failures", SoulService.transfer(player, "accepted"));
            soul = manifestation(c, player);
            c.expect("both nested transitions are refused", nested.get(), 2);
            c.check("duplicate transfer is refused", !SoulService.transfer(player, "duplicate"));
            c.expect("duplicate leaves the original manifestation", manifestation(c, player), soul);
            Consumer<SoulEvent.ReclaimPre> keep = event -> event.setCanceled(true);
            NeoForge.EVENT_BUS.addListener(keep);
            try { c.check("cancelled reclaim keeps the soul", !SoulService.reclaim(player) && !soul.isRemoved()); }
            finally { NeoForge.EVENT_BUS.unregister(keep); }
            c.check("reclaim removes the soul", SoulService.reclaim(player) && soul.isRemoved());
            c.check("reclaim clears the source and is idempotent", !player.getData(MxtAttachments.SOUL).active() && !SoulService.reclaim(player));
            SoulAttachment attachment = player.getData(MxtAttachments.SOUL);
            attachment.activate(player.getUUID(), c.level().getGameTime(), "unloaded", UUID.randomUUID());
            c.check("unavailable soul is not silently orphaned", !SoulService.reclaim(player) && attachment.active());
            attachment.clear();
            c.check("nonliving sources are refused", !SoulService.transfer(new ItemEntity(c.level(), 0, 0, 0, new ItemStack(Items.STONE)), "nonliving"));
        } finally {
            NeoForge.EVENT_BUS.unregister(reenter);
            player.getData(MxtAttachments.SOUL).clear();
            if (soul != null) soul.discard();
        }
        c.note("lifecycle: cancellation/spawn failure/reentry/duplicate/reclaim/nonliving OK");
    }

    private static void commands(ProbeContext c) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(SoulCommand.build());
        CommandSourceStack admin = c.player().createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER);
        CommandSourceStack user = c.player().createCommandSourceStack().withPermission(LevelBasedPermissionSet.NO_PERMISSIONS);
        c.check("ordinary players cannot access the soul command", !dispatcher.getRoot().getChild("soul").canUse(user));
        try {
            c.expect("admin command creates one soul", dispatcher.execute("soul create", admin), 1);
            SoulEntity soul = manifestation(c, c.player());
            c.expect("admin command reclaims one soul", dispatcher.execute("soul reclaim", admin), 1);
            c.check("command and service share state", soul.isRemoved() && !c.player().getData(MxtAttachments.SOUL).active());
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        } finally { SoulService.reclaim(c.player()); }
        c.note("commands: permission/default target/create/reclaim OK");
    }

    private static void deathGeneration(ProbeContext c) {
        MxtServerConfig.Souls settings = MxtServerConfig.INSTANCE.souls;
        boolean enabled = settings.spawnOnDeath.getValue();
        List<SoulEntity> generated = new ArrayList<>();
        Sheep disabled = source(c, EntityType.SHEEP);
        Sheep cancelled = source(c, EntityType.SHEEP);
        Sheep automatic = source(c, EntityType.SHEEP);
        Consumer<LivingDeathEvent> preventDeath = event -> {
            if (event.getEntity() == cancelled) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(preventDeath);
        try {
            settings.spawnOnDeath.setValue(false);
            disabled.hurtServer(c.level(), c.level().damageSources().genericKill(), 10000);
            c.check("disabled death generation leaves no soul record", disabled.getExistingData(MxtAttachments.SOUL).isEmpty());
            c.check("disabled death generation still permits explicit creation", SoulService.transfer(c.player(), "probe"));
            generated.add(manifestation(c, c.player()));
            SoulService.reclaim(c.player());
            settings.spawnOnDeath.setValue(true);
            cancelled.hurtServer(c.level(), c.level().damageSources().genericKill(), 10000);
            c.check("cancelled death leaves no soul record", cancelled.getExistingData(MxtAttachments.SOUL).isEmpty());
            automatic.setColor(DyeColor.MAGENTA);
            automatic.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            automatic.hurtServer(c.level(), c.level().damageSources().genericKill(), 10000);
            SoulEntity animalSoul = manifestation(c, automatic);
            generated.add(animalSoul);
            c.expect("actual mob death identifies its source", animalSoul.source(), "death");
            Sheep restored = (Sheep) animalSoul.snapshot().orElseThrow().appearanceSource(c.level()).orElseThrow();
            c.expect("death retains the variant", restored.getColor(), DyeColor.MAGENTA);
            c.expect("death captures equipment before drops", restored.getItemBySlot(EquipmentSlot.HEAD).getItem(), Items.DIAMOND_HELMET);
            c.player().getInventory().setItem(9, new ItemStack(Items.EMERALD, 17));
            c.player().hurtServer(c.level(), c.level().damageSources().genericKill(), 10000);
            SoulEntity playerSoul = manifestation(c, c.player());
            generated.add(playerSoul);
            Player restoredPlayer = (Player) playerSoul.snapshot().orElseThrow().appearanceSource(c.level()).orElseThrow();
            c.expect("actual player death captures inventory before drops", restoredPlayer.getInventory().getItem(9).getCount(), 17);
            c.expect("actual player death captures its profile", playerSoul.snapshot().orElseThrow().profile().orElseThrow().partialProfile(), c.player().getGameProfile());
        } finally {
            NeoForge.EVENT_BUS.unregister(preventDeath);
            settings.spawnOnDeath.setValue(enabled);
            SoulService.reclaim(automatic);
            SoulService.reclaim(c.player());
            generated.forEach(SoulEntity::discard);
            disabled.discard();
            cancelled.discard();
            automatic.discard();
        }
        c.note("death_generation: switch/cancellation/mob/player/pre-drop snapshot OK");
    }

    private static void timeout(ProbeContext c) {
        MxtServerConfig.Souls settings = MxtServerConfig.INSTANCE.souls;
        int lifetime = settings.lifetimeTicks.getValue();
        List<SoulEntity> generated = new ArrayList<>();
        try {
            settings.lifetimeTicks.setValue(1);
            long now = c.server().overworld().getGameTime();
            c.check("timeout fixture has a positive game clock", now >= 1);
            c.check("timeout fixture creates a soul", SoulService.transfer(c.player(), "probe"));
            SoulEntity soul = manifestation(c, c.player());
            generated.add(soul);
            soul.tick();
            c.check("soul remains before its deadline", !soul.isRemoved());
            soul.bind(c.player().getUUID(), now - 1, "probe");
            soul.tick();
            c.check("tick expires the soul at its deadline", soul.isRemoved());
            c.check("expiry clears the loaded origin's matching record", !c.player().getData(MxtAttachments.SOUL).active());
            c.check("origin can create another soul after expiry", SoulService.transfer(c.player(), "again"));
            SoulEntity replacement = manifestation(c, c.player());
            generated.add(replacement);
            replacement.bind(c.player().getUUID(), now - 1, "again");
            settings.lifetimeTicks.setValue(0);
            replacement.tick();
            c.check("zero lifetime preserves old souls", !replacement.isRemoved());
            settings.lifetimeTicks.setValue(1);
            UUID newerId = UUID.randomUUID();
            c.player().getData(MxtAttachments.SOUL).activate(c.player().getUUID(), now, "newer", newerId);
            replacement.tick();
            c.check("expiry does not clear a newer association", replacement.isRemoved()
                    && c.player().getData(MxtAttachments.SOUL).manifestation().filter(newerId::equals).isPresent());
            c.player().getData(MxtAttachments.SOUL).activate(c.player().getUUID(), now - 1, "offline", UUID.randomUUID());
            SoulEventBridge.onPlayerLogin(new PlayerLoggedInEvent(c.player()));
            c.check("login clears an expired unloaded association", !c.player().getData(MxtAttachments.SOUL).active());
            c.player().getData(MxtAttachments.SOUL).activate(c.player().getUUID(), now - 1, "stale", UUID.randomUUID());
            c.check("creation replaces an expired unavailable association", SoulService.transfer(c.player(), "after_unload"));
            SoulEntity persisted = manifestation(c, c.player());
            generated.add(persisted);
            persisted.bind(c.player().getUUID(), now - 1, "after_unload");
            SoulEntity loaded = new SoulEntity(MxtEntityTypes.SOUL.get(), c.level());
            generated.add(loaded);
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, c.registries(), save(persisted)));
            c.expect("reload retains the original timeout clock", loaded.createdAt(), now - 1);
            loaded.tick();
            c.check("first tick removes an overdue reloaded soul", loaded.isRemoved());
            SoulEntity unbound = new SoulEntity(MxtEntityTypes.SOUL.get(), c.level());
            generated.add(unbound);
            unbound.tick();
            c.check("an unbound soul starts its clock on the first tick", !unbound.isRemoved() && unbound.createdAt() == now);
        } finally {
            settings.lifetimeTicks.setValue(lifetime);
            generated.forEach(SoulEntity::discard);
            c.player().getData(MxtAttachments.SOUL).clear();
        }
        c.note("timeout: boundary/zero/cleanup/newer identity/offline/reload/unbound OK");
    }

    private static void close(ProbeContext c, String name, double actual, double expected) {
        c.check(name + ": " + actual + " != " + expected, Math.abs(actual - expected) < 1.0E-5);
    }
}
