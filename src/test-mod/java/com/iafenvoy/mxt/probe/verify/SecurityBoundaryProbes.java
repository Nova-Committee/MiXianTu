package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.compat.ftb.FtbChunksCompat;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.item.block.entity.TradeStationBlockEntity;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbGrowthService;
import com.iafenvoy.mxt.runtime.economy.CurrencyPaymentService;
import com.iafenvoy.mxt.runtime.economy.CurrencyPaymentService.Denomination;
import com.iafenvoy.mxt.runtime.formation.FormationInstance;
import com.iafenvoy.mxt.runtime.rift.RiftLanding;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.SecretRealmRecord;
import com.iafenvoy.mxt.runtime.world.SecretRealmRegistry;
import com.iafenvoy.mxt.runtime.world.SecretRealmService;
import com.iafenvoy.mxt.screen.menu.ChequeTableMenu;
import com.iafenvoy.mxt.screen.menu.ExchangeStationMenu;
import com.iafenvoy.mxt.screen.menu.StationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SecurityBoundaryProbes {
    private SecurityBoundaryProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/security_boundaries/menu", SecurityBoundaryProbes::menu),
                Probe.of("verify/security_boundaries/sync", SecurityBoundaryProbes::sync),
                Probe.of("verify/security_boundaries/herb_payment", context -> {
                    context.after(25, () -> {
                        herbPayment(context);
                        context.done();
                    });
                }),
                Probe.of("verify/security_boundaries/realm_cleanup", SecurityBoundaryProbes::realmCleanup),
                Probe.of("verify/security_boundaries/change_budget", SecurityBoundaryProbes::changeBudget),
                Probe.of("verify/security_boundaries/exchange", SecurityBoundaryProbes::exchange),
                Probe.of("verify/security_boundaries/claim", SecurityBoundaryProbes::claim),
                Probe.of("verify/security_boundaries/platform", SecurityBoundaryProbes::platform));
    }

    private static void menu(ProbeContext c) {
        var player = c.player();
        var level = c.level();
        BlockPos pos = c.at(5, 1, 5);
        player.snapTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0, 0);
        level.setBlockAndUpdate(pos, MxtBlocks.TRADE_STATION.get().defaultBlockState());
        var station = (TradeStationBlockEntity) level.getBlockEntity(pos);
        station.setOwner(player.getUUID());
        player.openMenu(station);
        var ownerMenu = (StationMenu) player.containerMenu;
        station.setOwner(UUID.randomUUID());
        c.check("station owner loses the open management menu on ownership change", !ownerMenu.stillValid(player));
        player.closeContainer();

        station.costs().setItem(0, new ItemStack(Items.EMERALD));
        station.rewards().setItem(0, new ItemStack(Items.DIAMOND));
        station.stock().setItem(0, new ItemStack(Items.DIAMOND));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.EMERALD));
        player.openMenu(station);
        var customer = (StationMenu) player.containerMenu;
        c.check("station payment excludes equipment", !customer.trade(player, customer.offerRevision()));
        c.check("offhand stack was not charged", player.getOffhandItem().is(Items.EMERALD));
        level.removeBlock(pos, false);
        level.setBlockAndUpdate(pos, MxtBlocks.TRADE_STATION.get().defaultBlockState());
        c.check("same block type does not keep old entity's menu valid", !customer.stillValid(player));
        player.closeContainer();

        var cheque = new ChequeTableMenu(1, player.getInventory(), ContainerLevelAccess.create(level, pos));
        level.setBlockAndUpdate(pos, MxtBlocks.CHEQUE_TABLE.get().defaultBlockState());
        cheque.getSlot(0).set(new ItemStack(Items.EMERALD));
        cheque.getSlot(15).set(MxtItems.CHEQUE.toStack());
        c.check("a closed cheque menu refuses signing", !cheque.checkIn(player));
        player.containerMenu = cheque;
        level.removeBlock(pos, false);
        c.check("an open cheque menu refuses signing after removal", !cheque.checkIn(player));
        player.closeContainer();
    }

    private static void sync(ProbeContext c) {
        var station = new TradeStationBlockEntity(c.at(5, 1, 5), MxtBlocks.TRADE_STATION.get().defaultBlockState());
        station.setOwner(c.player().getUUID());
        station.stock().setItem(0, new ItemStack(Items.DIAMOND, 7));
        station.display().setItem(0, new ItemStack(Items.DIAMOND, 1));
        var update = station.getUpdateTag(c.registries());
        c.check("station sends only its display stack", update.size() == 1 && update.contains("display_stack"));
        var saved = station.saveWithoutMetadata(c.registries());
        c.check("storage and ownership remain in save data", saved.contains("stock") && saved.contains("owner"));
    }

    private static void herbPayment(ProbeContext c) {
        var level = c.level();
        BlockPos pos = c.at(5, 1, 5);
        level.setBlockAndUpdate(pos, Blocks.PITCHER_PLANT.defaultBlockState());
        var chunk = level.getChunkAt(pos);
        var data = chunk.getData(MxtAttachments.HERB_CHUNK);
        var sections = chunk.getData(MxtAttachments.AURA_CHUNK).auras();
        int section = SectionPos.blockToSectionCoord(pos.getY());
        var aura = c.require(MxtResourceKeys.AURA, "spirit_power");
        var pool = new AuraPool(0, 10, 0, 0);
        var previous = sections.put(section, Map.of(aura, pool));
        try {
            long now = level.getGameTime();
            data.set(pos, 0, now - SpiritHerbGrowthService.PERIOD);
            SpiritHerbGrowthService.settle(level, chunk, data);
            c.expect("unpaid growth leaves age unchanged", data.find(pos).orElseThrow().age(), 0.0D);
            c.expect("an unpaid period still advances its clock", data.find(pos).orElseThrow().clock(), now);
            pool.set(5, 10, 0, 0);
            data.set(pos, 0, now - SpiritHerbGrowthService.PERIOD);
            SpiritHerbGrowthService.settle(level, chunk, data);
            c.check("paid growth advances age", data.find(pos).orElseThrow().age() > 0.0D);
            c.expect("paid period deducts the configured amount", pool.amount(), 3.0D);
        } finally {
            data.remove(pos);
            if (previous == null) sections.remove(section);
            else sections.put(section, previous);
        }
    }

    private static void realmCleanup(ProbeContext c) {
        var definition = c.require(MxtResourceKeys.SECRET_REALM, "portal_realm");
        var server = c.server();
        var cap = MxtServerConfig.INSTANCE.rifts.maxVertices;
        int previous = cap.getValue();
        var plan = SecretRealmService.plan(definition, 89, 7, c.level().getGameTime());
        try {
            cap.setValue(3);
            c.check("layout refusal aborts instance preparation", SecretRealmService.open(server, plan).isEmpty());
            c.check("failed preparation leaves no live level or registry entry",
                    server.getLevel(plan.dimension()) == null && SecretRealmRegistry.at(plan.dimension()).isEmpty());
        } finally {
            cap.setValue(previous);
        }
        var record = SecretRealmService.open(server, plan).orElseThrow();
        var player = c.player();
        Vec3 start = player.position();
        var instance = server.getLevel(record.dimension());
        try {
            c.check("probe player arrived in the runtime dimension",
                    player.teleportTo(instance, 0.5, 65, 0.5, Set.of(), 0, 0, false));
            c.check("occupied runtime dimension refuses deletion", !SecretRealmService.destroy(server, record));
            var pending = SecretRealmRegistry.at(record.dimension()).orElseThrow();
            c.expect("cleanup remains pending while dimension is occupied",
                    pending.cleanup(), SecretRealmRecord.Cleanup.DESTROY);
            var ops = RegistryOps.create(NbtOps.INSTANCE, c.registries());
            var saved = SecretRealmRecord.CODEC.encodeStart(ops, pending).getOrThrow();
            c.expect("the cleanup state survives serialization", SecretRealmRecord.CODEC.parse(ops, saved).getOrThrow().cleanup(),
                    SecretRealmRecord.Cleanup.DESTROY);
            c.check("an instance pending deletion refuses reopening", SecretRealmService.open(server, record).isEmpty());
        } finally {
            player.teleportTo(c.level(), start.x, start.y, start.z, Set.of(), 0, 0, false);
        }
        c.check("cleanup retry removes the registry entry and runtime dimension",
                SecretRealmService.expire(server, record, c.level().getGameTime()) && server.getLevel(record.dimension()) == null
                        && SecretRealmRegistry.at(record.dimension()).isEmpty());
    }

    private static void changeBudget(ProbeContext c) {
        var first = CurrencyPaymentService.denominations(c.registries());
        c.check("quotes share one denomination table per registry snapshot", first == CurrencyPaymentService.denominations(c.registries()));
        var combinations = List.of(new Denomination(Items.GOLD_INGOT, 4), new Denomination(Items.IRON_INGOT, 3));
        c.check("bounded fallback still finds a non-greedy answer", CurrencyPaymentService.planCounts(combinations, 6, 15).isPresent());
        var wideSearch = List.of(new Denomination(Items.GOLD_INGOT, 2_000_001), new Denomination(Items.IRON_INGOT, 1_000_003));
        c.check("large search stops without a partial payout", CurrencyPaymentService.planCounts(wideSearch, 400_001_200_000L, 10_000).isEmpty());
    }

    private static void exchange(ProbeContext c) {
        var player = c.player();
        BlockPos pos = c.at(5, 1, 5);
        var level = c.level();
        player.snapTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0, 0);
        level.setBlockAndUpdate(pos, MxtBlocks.EXCHANGE_STATION.get().defaultBlockState());
        var menu = new ExchangeStationMenu(1, player.getInventory(), ContainerLevelAccess.create(level, pos));
        player.containerMenu = menu;
        var input = new ItemStack(Items.CLAY_BALL, 2);
        menu.getSlot(0).set(input);
        c.check("initial eligible stack creates an exchange quote", menu.clickMenuButton(player, 0));
        input.grow(1);
        c.check("ordinary pickup refuses the ineligible quote", !menu.getSlot(1).mayPickup(player));
        input.shrink(1);
        menu.getSlot(0).setChanged();
        c.check("eligible stack restores the quote", menu.clickMenuButton(player, 0));
        input.grow(1);
        c.check("shift-click refuses the ineligible quote", menu.quickMoveStack(player, 1).isEmpty());
        c.expect("refused exchange leaves the input intact", input.getCount(), 3);
        input.shrink(1);
        menu.getSlot(0).setChanged();
        c.check("eligible shift-click completes", menu.clickMenuButton(player, 0) && !menu.quickMoveStack(player, 1).isEmpty());
        c.check("completed exchange spends the input exactly once", menu.getSlot(0).getItem().isEmpty());
        player.closeContainer();
    }

    private static void claim(ProbeContext c) {
        c.check("unclaimed land allows a ward", new FtbChunksCompat.Claim(false, false, Optional.empty()).allowsWard(_ -> false));
        c.check("server-owned claim without edit permission denies a ward", !new FtbChunksCompat.Claim(true, false, Optional.empty()).allowsWard(_ -> true));
        c.check("server-owned claim with edit permission accepts a ward", new FtbChunksCompat.Claim(true, true, Optional.empty()).allowsWard(_ -> false));
        c.check("known friendly claim owner may authorize a ward", new FtbChunksCompat.Claim(true, false, Optional.of(UUID.randomUUID())).allowsWard(_ -> true));
    }

    private static void platform(ProbeContext c) {
        var level = c.level();
        var player = c.player();
        BlockPos feet = c.at(5, 1, 5).above(16);
        var formations = level.getData(MxtAttachments.FORMATION_WORLD);
        BlockPos protectedEdge = feet.below().east();
        var tag = new CompoundTag();
        tag.putString("formation", c.id("formation_protection_probe").toString());
        tag.putDouble("radius", 0.25D);
        formations.put(protectedEdge, FormationInstance.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
        try {
            Vec3 target = Vec3.atBottomCenterOf(feet);
            c.expect("a platform intersecting a ward is refused", RiftLanding.resolve(level, player, target, false).getFailure().orElse(null),
                    RiftLanding.Reason.LANDING_PROTECTED);
            c.check("denial leaves the unprotected footprint untouched", level.getBlockState(feet.below()).isAir());
        } finally {
            formations.remove(protectedEdge);
        }
    }
}
