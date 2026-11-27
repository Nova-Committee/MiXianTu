package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.api.AuraHandler;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.data.item.ResourceCapacityComponent;
import com.iafenvoy.mxt.data.item.TalismanComponent;
import com.iafenvoy.mxt.entity.SpiritBurstEntity;
import com.iafenvoy.mxt.item.SpiritStoneItem;
import com.iafenvoy.mxt.item.SpiritVesselItem;
import com.iafenvoy.mxt.item.block.entity.DisplayStandBlockEntity;
import com.iafenvoy.mxt.item.block.entity.SpiritCraftingTableBlockEntity;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtCapabilities;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.spirit.AuraHandlerUtil;
import com.iafenvoy.mxt.runtime.spirit.AuraTransfers;
import com.iafenvoy.mxt.runtime.spirit.SpiritBurstService;
import com.iafenvoy.mxt.runtime.talisman.TalismanService;
import com.iafenvoy.mxt.runtime.wheel.MxtWheelActions;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.runtime.world.WorldAuraAccess;
import com.iafenvoy.saturn.attachment.ResourceHolderAttachment;
import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.saturn.registry.SaturnAttachments;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

public final class AuraHandlerVerifyProbes {
    private AuraHandlerVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/aura_handler/transfer", AuraHandlerVerifyProbes::transfer),
                Probe.of("verify/aura_handler/block_capability", AuraHandlerVerifyProbes::blockCapability),
                Probe.of("verify/aura_handler/item_slot", AuraHandlerVerifyProbes::itemSlot),
                Probe.of("verify/aura_handler/display_commit", AuraHandlerVerifyProbes::displayCommit),
                Probe.of("verify/aura_handler/burst_to_display", AuraHandlerVerifyProbes::burstToDisplay),
                Probe.of("verify/aura_handler/burst_to_vessel", AuraHandlerVerifyProbes::burstToVessel),
                Probe.of("verify/aura_handler/world_stock", AuraHandlerVerifyProbes::worldStock));
    }

    private static void transfer(ProbeContext context) {
        Holder<Aura> qi = context.require(MxtResourceKeys.AURA, "qi");
        Holder<Aura> spirit = context.require(MxtResourceKeys.AURA, "spirit_power");
        verify(qi, spirit, context::check);
        context.note("AuraHandler transfer checks completed");
    }

    private static void blockCapability(ProbeContext context) {
        Holder<Aura> qi = context.require(MxtResourceKeys.AURA, "qi");
        BlockPos intake = context.at(5, 3, 5);
        context.level().setBlockAndUpdate(intake.below(), MxtBlocks.SPIRIT_CRAFTING_TABLE.get().defaultBlockState());
        context.level().setBlockAndUpdate(intake, Blocks.TARGET.defaultBlockState());
        SpiritCraftingTableBlockEntity table = (SpiritCraftingTableBlockEntity) context.level().getBlockEntity(intake.below());
        assert table != null;
        table.configureAuraCosts(Map.of(qi, 1.0D));
        AuraHandler direct = MxtCapabilities.findBlock(context.level(), intake.below(), null, null);
        AuraHandler external = MxtCapabilities.findBlock(context.level(), intake, Direction.WEST, context.player());
        context.check("built-in and external capabilities resolve the same store", direct == table && external == table);
        context.check("external capability respects the queried face",
                MxtCapabilities.findBlock(context.level(), intake, Direction.EAST, context.player()) == null);
        try (Transaction transaction = Transaction.openRoot()) {
            context.expect("transaction credits placed worktable", table.insert(qi, 0.25D, transaction), 0.25D);
            context.expect("worktable exposes pending balance", table.getAmount(qi), 0.25D);
        }
        context.expect("worktable rolls back pending balance", table.getAmount(qi), 0.0D);
        Vec3 hit = intake.getCenter();
        SpiritBurstEntity burst = new SpiritBurstEntity(context.level(), context.player(), qi, 0.5D, 0xFFFFFF);
        burst.setPos(hit.x - 0.75D, hit.y, hit.z);
        burst.setDeltaMovement(new Vec3(1.0D, 0.0D, 0.0D));
        burst.tick();
        context.check("burst hits the external provider and deposits fractional aura", burst.isRemoved() && table.getAmount(qi) == 0.5D);
        context.level().setBlock(intake.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        context.expect("removed worktable refuses writes through an existing handler",
                AuraHandlerUtil.insert(table, qi, 0.5D, false, false, null), 0.0D);
    }

    private static void itemSlot(ProbeContext context) {
        Holder<Aura> qi = context.require(MxtResourceKeys.AURA, "qi");
        ItemStack stack = MxtItems.SPIRIT_STONE.toStack(4);
        SpiritStoneItem.setStoredPerItem(stack, qi, 0.75D);
        context.player().setItemInHand(InteractionHand.OFF_HAND, stack);
        AuraHandler source = hand(context, InteractionHand.OFF_HAND);
        AuraHandler alias = hand(context, InteractionHand.OFF_HAND);
        context.expect("four stones expose their total charge", source.getAmount(qi), 3.0D);
        context.expect("separate queries identify the same slot", alias.storageIdentity(qi), source.storageIdentity(qi));
        try (Transaction transaction = Transaction.openRoot()) {
            context.expect("slot extraction reserves a fraction", source.extract(qi, 0.25D, transaction), 0.25D);
            context.expect("another wrapper sees the updated slot", alias.getAmount(qi), 2.75D);
        }
        context.check("rollback restores the original stack and amount",
                context.player().getOffhandItem() == stack && source.getAmount(qi) == 3.0D);
        context.expect("root commit extracts the exact fraction",
                AuraHandlerUtil.extract(source, qi, 0.25D, true, false, null), 0.25D);
        context.check("committed components stay in the same hand",
                context.player().getOffhandItem().getCount() == 4 && hand(context, InteractionHand.OFF_HAND).getAmount(qi) == 2.75D);
        context.player().setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
        context.expect("old handler refuses a replacement item type",
                AuraHandlerUtil.extract(source, qi, 0.25D, false, false, null), 0.0D);
        ItemStack vessel = MxtItems.SPIRIT_VESSEL.toStack();
        Object2DoubleOpenHashMap<Holder<Resource>> capacities = new Object2DoubleOpenHashMap<>();
        capacities.put(qi.value().resource(), 1.25D);
        vessel.set(MxtDataComponents.RESOURCE_CAPACITY, new ResourceCapacityComponent(capacities));
        context.player().setItemInHand(InteractionHand.MAIN_HAND, vessel);
        AuraHandler target = hand(context, InteractionHand.MAIN_HAND);
        context.expect("vessel receives only up to its remaining capacity",
                AuraHandlerUtil.insert(target, qi, 2.0D, false, false, null), 1.25D);
        context.expect("full vessel still reports its maximum capacity", target.getCapacity(qi), 1.25D);
        context.expect("vessel cannot be drained by the capability",
                AuraHandlerUtil.extract(target, qi, 0.25D, false, false, null), 0.0D);
    }

    private static AuraHandler hand(ProbeContext context, InteractionHand hand) {
        return Objects.requireNonNull(MxtCapabilities.findItem(context.player().getItemInHand(hand),
                MxtCapabilities.ItemContext.forHand(context.player(), hand)));
    }

    private static void displayCommit(ProbeContext context) {
        Holder<Aura> spirit = context.require(MxtResourceKeys.AURA, "spirit_power");
        Holder<Resource> common = context.require(SaturnResourceKeys.RESOURCE, Identifier.parse("mxt:common"));
        ResourceHolderAttachment account = context.player().getData(SaturnAttachments.RESOURCE_HOLDER);
        account.set(common, 0.0D, 0.0D, 100.0D, -1L, "probe");
        ItemStack talisman = MxtItems.TALISMAN.toStack();
        talisman.set(MxtDataComponents.TALISMAN, TalismanComponent.of(
                context.require(MxtResourceKeys.TALISMAN, "buffer_sigil"), TalismanComponent.TriggerMode.FIRE));
        double capacity = TalismanService.capacity(talisman).get(spirit);
        BlockPos pos = context.at(5, 1, 5);
        context.level().setBlockAndUpdate(pos, MxtBlocks.OAK_DISPLAY_STAND.get().defaultBlockState());
        DisplayStandBlockEntity stand = (DisplayStandBlockEntity) context.level().getBlockEntity(pos);
        assert stand != null;
        AuraHandler handler = Objects.requireNonNull(MxtCapabilities.findBlock(context.level(), pos, null, context.player()));
        context.check("empty stand exposes its intake with no available aura", handler.getAuras().isEmpty());
        context.expect("empty stand accepts no aura",
                AuraHandlerUtil.insert(handler, spirit, 1.0D, false, false, null), 0.0D);
        SpiritBurstEntity burst = new SpiritBurstEntity(context.level(), context.player(), spirit, 1.0D, 0xFFFFFF);
        Vec3 target = pos.getCenter().add(0.0D, 1.3D, 0.0D);
        burst.setPos(target.x - 0.75D, target.y, target.z);
        burst.setDeltaMovement(new Vec3(1.0D, 0.0D, 0.0D));
        burst.tick();
        context.check("empty stand intercepts a burst above its collision shape", burst.isRemoved());
        stand.setDisplayedItem(talisman);
        context.expect("simulated fill reports capacity", AuraHandlerUtil.insert(handler, spirit, capacity, true, true, null), capacity);
        context.check("simulated fill restores the slot without firing",
                stand.displayedItem() == talisman && handler.getAmount(spirit) == 0.0D && account.get(common) == 0.0D);
        try (Transaction transaction = Transaction.openRoot()) {
            context.expect("first insertion reaches the displayed stack", handler.insert(spirit, 0.25D, transaction), 0.25D);
            context.expect("second insertion fills the displayed stack", handler.insert(spirit, capacity - 0.25D, transaction), capacity - 0.25D);
            context.expect("full talisman does not fire until commit", account.get(common), 0.0D);
            transaction.commit();
        }
        context.expect("root commit fires the charged talisman exactly once", account.get(common), 3.0D);
        stand.setDisplayedItem(ItemStack.EMPTY);
        context.expect("cached stand handler sees a removed stack", handler.getAmount(spirit), 0.0D);
    }

    private static void burstToDisplay(ProbeContext context) {
        Holder<Aura> spirit = context.require(MxtResourceKeys.AURA, ProbeDefinitions.SPIRIT_POWER);
        Holder<Aura> fire = context.require(MxtResourceKeys.AURA, "fire_power");
        ServerPlayer player = context.player();
        player.getData(MxtAttachments.CULTIVATION).setRealmStage(
                context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.SPIRIT_POWER_REFINING));
        ResourceHolderAttachment account = player.getData(SaturnAttachments.RESOURCE_HOLDER);
        account.set(spirit.value().resource(), 100.0D);
        account.set(fire.value().resource(), 100.0D);

        BlockPos pos = context.at(5, 1, 5);
        context.level().setBlockAndUpdate(pos, MxtBlocks.OAK_DISPLAY_STAND.get().defaultBlockState());
        DisplayStandBlockEntity stand = (DisplayStandBlockEntity) context.level().getBlockEntity(pos);
        assert stand != null;
        ItemStack talisman = MxtItems.TALISMAN.toStack();
        talisman.set(MxtDataComponents.TALISMAN, TalismanComponent.of(
                context.require(MxtResourceKeys.TALISMAN, "buffer_sigil"), TalismanComponent.TriggerMode.STORE));
        stand.setDisplayedItem(talisman);
        double capacity = TalismanService.capacity(talisman).get(spirit);
        Vec3 start = pos.getCenter().add(0.0D, 1.3D, 0.0D);
        player.snapTo(start.x, start.y - player.getEyeHeight() + 0.1D, start.z, -90.0F, 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        AABB launchArea = new AABB(start, start).inflate(0.25D);

        context.check("the wheel fires the wrong aura toward the stand", MxtWheelActions.burst(player, ProbeDefinitions.id("fire_power")));
        SpiritBurstEntity wrongBurst = context.level().getEntitiesOfClass(SpiritBurstEntity.class, launchArea,
                entity -> entity.getOwner() == player).getFirst();
        wrongBurst.tick();
        context.check("the displayed talisman rejects an unsupported aura",
                wrongBurst.isRemoved() && stand.displayedItem() == talisman);

        context.check("the wheel fires the supported aura", MxtWheelActions.burst(player, ProbeDefinitions.SPIRIT_POWER));
        SpiritBurstEntity burst = context.level().getEntitiesOfClass(SpiritBurstEntity.class, launchArea,
                entity -> entity.getOwner() == player && !entity.isRemoved()).getFirst();
        burst.tick();
        context.check("a burst starting above the stand is intercepted", burst.isRemoved());
        context.expect("the slot stores a full charge of the supported aura", stand.displayedItem()
                .getOrDefault(MxtDataComponents.SPIRIT_STORAGE, SpiritStorageComponent.EMPTY).get(spirit), capacity);
        context.expect("firing spends one burst", account.get(spirit.value().resource()), 90.0D);
    }

    private static void burstToVessel(ProbeContext context) {
        Identifier auraId = ProbeDefinitions.id("fire_power");
        Holder<Aura> aura = context.require(MxtResourceKeys.AURA, auraId);
        Holder<Resource> resource = aura.value().resource();
        ServerPlayer player = context.player();
        ResourceHolderAttachment account = player.getData(SaturnAttachments.RESOURCE_HOLDER);
        account.set(resource, 20.0D);
        ItemStack vessel = MxtItems.SPIRIT_VESSEL.toStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, vessel);

        context.expect("a vessel without capacity refuses the burst", SpiritBurstService.fireOnceResult(player, auraId)
                .getFailure().map(Failure::message).orElseGet(Component::empty), Component.translatable("failure.mxt.aura.vessel_refused"));
        context.expect("the refusal leaves the account intact", account.get(resource), 20.0D);
        Object2DoubleOpenHashMap<Holder<Resource>> caps = new Object2DoubleOpenHashMap<>();
        caps.put(resource, 10.0D);
        vessel.set(MxtDataComponents.RESOURCE_CAPACITY, new ResourceCapacityComponent(caps));
        context.check("capacity now allows a burst without waiting for a cooldown", SpiritBurstService.fireOnce(player, auraId));
        context.expect("the real hand slot stores the charge", SpiritVesselItem.storedAura(player.getMainHandItem(), aura), 10.0D);
        context.expect("a successful charge spends one burst", account.get(resource), 10.0D);

        player.removeData(MxtAttachments.SPIRIT_BURST_COOLDOWNS);
        context.expect("a full vessel reports its state", SpiritBurstService.fireOnceResult(player, auraId)
                .getFailure().map(Failure::message).orElseGet(Component::empty), Component.translatable("failure.mxt.aura.vessel_full"));
        context.expect("the full vessel does not spend the remaining balance", account.get(resource), 10.0D);
        AABB launchArea = player.getBoundingBox().inflate(2.0D);
        context.check("none of the attempts releases a projectile", context.level().getEntitiesOfClass(SpiritBurstEntity.class,
                launchArea, entity -> entity.getOwner() == player).isEmpty());
    }

    private static void worldStock(ProbeContext context) {
        Holder<Aura> qi = context.require(MxtResourceKeys.AURA, "qi");
        BlockPos pos = context.at(4, 1, 4);
        int x = SectionPos.blockToSectionCoord(pos.getX());
        int y = SectionPos.blockToSectionCoord(pos.getY());
        int z = SectionPos.blockToSectionCoord(pos.getZ());
        LevelChunk chunk = Objects.requireNonNull(AuraService.loadedChunk(context.level(), x, z));
        AuraChunkAttachment data = chunk.getData(MxtAttachments.AURA_CHUNK);
        AuraPool pool = AuraPool.natural(2.0D, 8.0D, 0.0D);
        Map<Holder<Aura>, AuraPool> original = data.auras().put(y, Map.of(qi, pool));
        try {
            WorldAuraAccess source = new WorldAuraAccess(context.level(), pos);
            WorldAuraAccess alias = new WorldAuraAccess(context.level(), SectionPos.of(pos).origin());
            context.expect("same section has the same physical inventory", alias.storageIdentity(qi), source.storageIdentity(qi));
            try (Transaction transaction = Transaction.openRoot()) {
                context.expect("section debit is visible immediately", source.extract(qi, 0.5D, transaction), 0.5D);
                context.expect("new wrapper sees pending debit", alias.getAmount(qi), 1.5D);
            }
            context.expect("section rollback restores the exact balance", pool.amount(), 2.0D);
            context.expect("committed section debit reports the accepted amount",
                    AuraHandlerUtil.extract(source, qi, 0.5D, true, false, null), 0.5D);
            context.expect("debit reaches persisted section stock", pool.amount(), 1.5D);
        } finally {
            if (original == null) data.auras().remove(y);
            else data.auras().put(y, original);
        }
        BlockPos distant = pos.offset(80_000, 0, 80_000);
        context.expect("capability lookup rejects an unloaded chunk", MxtCapabilities.findBlock(context.level(), distant, null, null), null);
        context.expect("unloaded stock is not created",
                AuraHandlerUtil.insert(new WorldAuraAccess(context.level(), distant), qi, 0.5D, false, false, null), 0.0D);
        context.expect("queries leave the distant chunk unloaded", AuraService.loadedChunk(context.level(), x + 5000, z + 5000), null);
    }

    static void verify(Holder<Aura> qi, Holder<Aura> spirit, BiConsumer<String, Boolean> check) {
        Handler source = new Handler(qi, 2.0D, 2.0D);
        Handler target = new Handler(qi, 0.5D, 1.25D);
        double simulated = AuraHandlerUtil.move(source, target, qi, 1.0D, false, true, null);
        check.accept("partial simulation reports 0.75 and preserves both balances", simulated == 0.75D
                && source.getAmount(qi) == 2.0D && target.getAmount(qi) == 0.5D);
        check.accept("simulation skips commit callbacks", source.store.commits == 0 && target.store.commits == 0);
        check.accept("exact simulation rejects a partial result",
                AuraHandlerUtil.move(source, target, qi, 1.0D, true, true, null) == 0.0D);
        check.accept("exact refusal restores both balances",
                AuraHandlerUtil.move(source, target, qi, 1.0D, true, false, null) == 0.0D
                        && source.getAmount(qi) == 2.0D && target.getAmount(qi) == 0.5D
                        && source.store.commits == 0 && target.store.commits == 0);

        try (Transaction transaction = Transaction.openRoot()) {
            double nested = AuraHandlerUtil.move(source, target, qi, 1.0D, transaction);
            check.accept("partial transfer is visible inside its parent", nested == 0.75D
                    && source.getAmount(qi) == 1.25D && target.getAmount(qi) == 1.25D);
            check.accept("nested transfer defers commit callbacks", source.store.commits == 0 && target.store.commits == 0);
        }
        check.accept("closing the parent restores both balances",
                source.getAmount(qi) == 2.0D && target.getAmount(qi) == 0.5D
                        && source.store.commits == 0 && target.store.commits == 0);
        try (Transaction transaction = Transaction.openRoot()) {
            double moved = AuraHandlerUtil.move(source, target, qi, 0.75D, true, false, transaction);
            check.accept("exact transfer moves the full fraction", moved == 0.75D);
            transaction.commit();
        }
        check.accept("committing the parent persists both balances once",
                source.getAmount(qi) == 1.25D && target.getAmount(qi) == 1.25D
                        && source.store.commits == 1 && target.store.commits == 1);

        Handler alias = new Handler(qi, source.capacity, source.store);
        check.accept("two wrappers of the same physical inventory cannot move aura",
                AuraHandlerUtil.move(source, alias, qi, 0.5D, null) == 0.0D && source.store.commits == 1);
        check.accept("unrelated aura does not move", AuraHandlerUtil.move(source, target, spirit, 0.5D, null) == 0.0D);
        check.accept("zero and unavailable endpoints do not change either balance",
                AuraHandlerUtil.move(source, target, qi, 0.0D, null) == 0.0D
                        && AuraHandlerUtil.move(source, null, qi, 0.5D, null) == 0.0D
                        && source.store.commits == 1 && target.store.commits == 1);
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -0.5D}) {
            boolean rejected = false;
            try {
                AuraHandlerUtil.move(source, target, qi, invalid, null);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check.accept("invalid request rejected: " + invalid, rejected);
        }

        Handler decimal = new Handler(qi, 0.3D, 1.0D);
        Handler buffer = new Handler(qi, 0.0D, 1000.0D);
        check.accept("binary rounding tail is accepted as exact",
                AuraHandlerUtil.move(decimal, buffer, qi, 0.1D + 0.2D, true, false, null) == 0.3D
                        && decimal.getAmount(qi) == 0.0D && buffer.getAmount(qi) == 0.3D);
        Handler smallSource = new Handler(qi, 1000.0D, 1000.0D);
        double small = AuraHandlerUtil.move(smallSource, buffer, qi, 0.0005D, true, false, null);
        check.accept("an exact sub-milliaura transfer tolerates floating-point subtraction",
                Math.abs(small - 0.0005D) <= 1.0E-13D && Math.abs(buffer.getAmount(qi) - 0.3005D) <= 1.0E-13D);

        Handler almost = new Handler(qi, 1.0E12D - 1.0D, 1.0E12D);
        Handler empty = new Handler(qi, 0.0D, Double.POSITIVE_INFINITY);
        check.accept("a one-unit shortage on a large request still fails",
                AuraHandlerUtil.move(almost, empty, qi, 1.0E12D, true, false, null) == 0.0D
                        && almost.getAmount(qi) == 1.0E12D - 1.0D && empty.getAmount(qi) == 0.0D);
        check.accept("a large withdrawal leaves the surplus in stock",
                AuraTransfers.extract(1.0E12D + 1.0D, 1.0E12D, false).stored() == 1.0D);
        Handler huge = new Handler(qi, 1.0E16D, Double.POSITIVE_INFINITY);
        check.accept("an unrepresentable insertion leaves the source unchanged",
                AuraHandlerUtil.move(buffer, huge, qi, 0.01D, null) == 0.0D && huge.getAmount(qi) == 1.0E16D
                        && buffer.store.commits == 2);

        Handler allOrNothing = new Handler(qi, 1.0D, 1.0D) {
            @Override
            public double extract(Holder<Aura> aura, double amount, TransactionContext transaction) {
                return amount == 1.0D ? super.extract(aura, amount, transaction) : 0.0D;
            }
        };
        Handler narrow = new Handler(qi, 0.0D, 0.5D);
        check.accept("source refusal after insertion rolls back the target",
                AuraHandlerUtil.move(allOrNothing, narrow, qi, 1.0D, null) == 0.0D
                        && allOrNothing.getAmount(qi) == 1.0D && narrow.getAmount(qi) == 0.0D
                        && allOrNothing.store.commits == 0 && narrow.store.commits == 0);

        Handler badReport = new Handler(qi, 0.0D, 1.0D) {
            @Override
            public double insert(Holder<Aura> aura, double amount, TransactionContext transaction) {
                return 2.0D * super.insert(aura, amount, transaction);
            }
        };
        check.accept("a false insertion report rolls back both inventories",
                AuraHandlerUtil.move(source, badReport, qi, 0.25D, null) == 0.0D
                        && source.getAmount(qi) == 1.25D && badReport.getAmount(qi) == 0.0D
                        && source.store.commits == 1 && badReport.store.commits == 0);
    }

    static class Handler implements AuraHandler {
        private final Holder<Aura> type;
        private final double capacity;
        private final Store store;

        Handler(Holder<Aura> type, double amount, double capacity) {
            this(type, capacity, new Store(amount));
        }

        private Handler(Holder<Aura> type, double capacity, Store store) {
            this.type = type;
            this.capacity = capacity;
            this.store = store;
        }

        @Override
        public Set<Holder<Aura>> getAuras() {
            return Set.of(this.type);
        }

        @Override
        public double getAmount(Holder<Aura> aura) {
            return aura.equals(this.type) ? this.store.amount : 0.0D;
        }

        @Override
        public double getCapacity(Holder<Aura> aura) {
            return aura.equals(this.type) ? this.capacity : 0.0D;
        }

        @Override
        public Object storageIdentity(Holder<Aura> aura) {
            return this.store;
        }

        @Override
        public double insert(Holder<Aura> aura, double amount, TransactionContext transaction) {
            if (!aura.equals(this.type)) return 0.0D;
            double before = this.store.amount;
            double accepted = Math.min(amount, this.capacity - before);
            this.store.set(before + accepted, transaction);
            return this.store.amount - before;
        }

        @Override
        public double extract(Holder<Aura> aura, double amount, TransactionContext transaction) {
            if (!aura.equals(this.type)) return 0.0D;
            double before = this.store.amount;
            this.store.set(before - Math.min(amount, before), transaction);
            return before - this.store.amount;
        }
    }

    private static final class Store extends SnapshotJournal<Double> {
        private double amount;
        private int commits;

        private Store(double amount) {
            this.amount = amount;
        }

        private void set(double amount, TransactionContext transaction) {
            if (this.amount == amount) return;
            this.updateSnapshots(transaction);
            this.amount = amount;
        }

        @Override
        protected Double createSnapshot() {
            return this.amount;
        }

        @Override
        protected void revertToSnapshot(Double snapshot) {
            this.amount = snapshot;
        }

        @Override
        protected void onRootCommit(Double originalState) {
            this.commits++;
        }
    }
}
