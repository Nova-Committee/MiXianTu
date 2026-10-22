package com.iafenvoy.mxt.probe.world;

import com.iafenvoy.mxt.attachment.SecretRealmTravelAttachment;
import com.iafenvoy.mxt.data.rift.*;
import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.event.SecretRealmEvent;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.resulting.failures.SecretRealmFailure;
import com.iafenvoy.mxt.runtime.rift.RiftLanding;
import com.iafenvoy.mxt.runtime.rift.RiftRecord;
import com.iafenvoy.mxt.runtime.rift.RiftService;
import com.iafenvoy.mxt.runtime.rift.RiftTravelService;
import com.iafenvoy.mxt.runtime.world.SecretRealmRecord;
import com.iafenvoy.mxt.runtime.world.SecretRealmRegistry;
import com.iafenvoy.mxt.runtime.world.SecretRealmService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class SecretRealmTravelProbes {
    private SecretRealmTravelProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("world/secret_realm/trial_landing", SecretRealmTravelProbes::trialLanding),
                Probe.of("world/secret_realm/travel_diagnostics", SecretRealmTravelProbes::diagnostics),
                Probe.of("world/secret_realm/commands", SecretRealmTravelProbes::commands),
                Probe.of("world/secret_realm/flat_landing", SecretRealmTravelProbes::flatLanding),
                Probe.of("world/secret_realm/platform_search", SecretRealmTravelProbes::platformSearch),
                Probe.of("world/secret_realm/platform_guards", SecretRealmTravelProbes::platformGuards),
                Probe.of("world/secret_realm/platform_travel", SecretRealmTravelProbes::platformTravel));
    }

    private static void platformSearch(ProbeContext c) {
        SecretRealmRecord record = open(c);
        ServerLevel level = c.server().getLevel(record.dimension());
        boolean flying = c.player().getAbilities().flying;
        c.player().getAbilities().flying = false;
        try {
            Vec3 target = new Vec3(25.5, 65, 25.5);
            RiftLanding.loadArea(level, c.player(), target, true);
            level.setBlockAndUpdate(BlockPos.containing(target.add(3, -1, 0)), Blocks.STONE.defaultBlockState());
            RiftLanding.Inspection natural = RiftLanding.resolve(level, c.player(), target, true);
            c.check("exhaust natural search before creating platform", natural.isSuccess() && !natural.position().equals(target));
            c.check("natural search leaves requested floor untouched", level.getBlockState(BlockPos.containing(target).below()).isAir());
            Vec3 empty = new Vec3(-20.5, 65.25, -20.5);
            c.expect("inspection identifies missing support", SecretRealmService.checkArrival(level, c.player(), empty, true).failure(), RiftLanding.Reason.LANDING_NO_SUPPORT);
            c.check("inspection does not create floor", level.getBlockState(BlockPos.containing(empty)).isAir());
            RiftLanding.Inspection built = RiftLanding.resolve(level, c.player(), empty, true);
            c.check("platform can be generated", built.isSuccess());
            c.expect("platform aligns fractional foot height", built.position(), new Vec3(-20.5, 66, -20.5));
            long dirt = BlockPos.betweenClosedStream(-22, 65, -22, -20, 65, -20).filter(pos -> level.getBlockState(pos).is(Blocks.DIRT)).count();
            c.expect("player receives full three by three floor", dirt, 9L);
            c.check("generated landing passes same safety inspection", RiftLanding.safe(level, c.player(), built.position()));
            c.expect("repeat uses existing platform", RiftLanding.resolve(level, c.player(), built.position(), true).position(), built.position());
            Vec3 rejected = new Vec3(-38.5, 65, 25.5);
            RiftLanding.loadArea(level, c.player(), rejected, true);
            RiftLanding.Inspection outside = RiftLanding.resolve(level, c.player(), rejected, true, pos -> pos.x > -38);
            c.check("generated return point obeys portal exclusion", outside.isSuccess() && outside.position().x > -38);
            AABB original = c.player().getBoundingBox();
            try {
                c.player().setBoundingBox(original.inflate(1, 0, 1));
                Vec3 wide = new Vec3(40.5, 65, -40.5);
                RiftLanding.loadArea(level, c.player(), wide, false);
                c.check("wide entity receives platform", RiftLanding.resolve(level, c.player(), wide, false).isSuccess());
                long wideDirt = BlockPos.betweenClosedStream(38, 64, -43, 42, 64, -39).filter(pos -> level.getBlockState(pos).is(Blocks.DIRT)).count();
                c.expect("wide entity floor expands to five by five", wideDirt, 25L);
            } finally {
                c.player().setBoundingBox(original);
            }
        } finally {
            c.player().getAbilities().flying = flying;
            cleanup(c, record);
        }
    }

    private static void platformGuards(ProbeContext c) {
        SecretRealmRecord record = open(c);
        ServerLevel level = c.server().getLevel(record.dimension());
        boolean flying = c.player().getAbilities().flying;
        c.player().getAbilities().flying = false;
        try {
            Vec3 target = new Vec3(30.5, 70, 30.5);
            RiftLanding.loadArea(level, c.player(), target, false);
            BlockPos feet = BlockPos.containing(target);
            level.setBlockAndUpdate(feet, Blocks.STONE.defaultBlockState());
            c.expect("solid obstruction rejected", RiftLanding.resolve(level, c.player(), target, false).failure(), RiftLanding.Reason.LANDING_BLOCKED);
            c.expect("existing solid preserved", level.getBlockState(feet).getBlock(), Blocks.STONE);
            c.check("blocked attempt creates no dirt", level.getBlockState(feet.below()).isAir());
            level.setBlockAndUpdate(feet, Blocks.WATER.defaultBlockState());
            c.expect("water rejected", RiftLanding.resolve(level, c.player(), target, false).failure(), RiftLanding.Reason.LANDING_FLUID);
            c.expect("water preserved", level.getBlockState(feet).getBlock(), Blocks.WATER);
            level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(feet.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
            c.expect("hazard rejected", RiftLanding.resolve(level, c.player(), target, false).failure(), RiftLanding.Reason.LANDING_HAZARD);
            c.expect("hazard preserved", level.getBlockState(feet.below()).getBlock(), Blocks.MAGMA_BLOCK);
            level.setBlockAndUpdate(feet.below(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(feet.below().east(), Blocks.STONE.defaultBlockState());
            c.check("platform margin preserves existing blocks", RiftLanding.resolve(level, c.player(), target, false).isFailure());
            c.check("platform preflight leaves other floor cells empty", level.getBlockState(feet.below()).isAir());
            Vec3 edge = new Vec3(62.9, 70, 0.5);
            RiftLanding.loadArea(level, c.player(), edge, false);
            c.check("platform cannot cross border", RiftLanding.resolve(level, c.player(), edge, false).isFailure());
            c.check("border refusal has no partial floor", level.getBlockState(BlockPos.containing(edge).below()).isAir());
            Vec3 cold = new Vec3(-49.5, 70, 49.5);
            c.expect("generation does not implicitly load chunks", RiftLanding.resolve(level, c.player(), cold, false).failure(), RiftLanding.Reason.LANDING_CHUNK_UNLOADED);
            c.expect("invalid input refused", RiftLanding.resolve(level, c.player(), new Vec3(Double.NaN, 70, 0), true).failure(), RiftLanding.Reason.LANDING_NON_FINITE);
            Vec3 bottom = new Vec3(-30.5, level.getMinY(), -30.5);
            RiftLanding.loadArea(level, c.player(), bottom, false);
            RiftLanding.Inspection raised = RiftLanding.resolve(level, c.player(), bottom, false);
            c.check("void floor can be supported", raised.isSuccess());
            c.expect("void arrival raised above minimum build height", raised.position().y, (double) level.getMinY() + 1);
            c.expect("void platform within build bounds", level.getBlockState(BlockPos.containing(raised.position()).below()).getBlock(), Blocks.DIRT);
        } finally {
            c.player().getAbilities().flying = flying;
            cleanup(c, record);
        }
    }

    private static void platformTravel(ProbeContext c) {
        SecretRealmRecord record = open(c);
        ServerLevel target = c.server().getLevel(record.dimension());
        ServerPlayer player = c.player();
        boolean flying = player.getAbilities().flying;
        player.getAbilities().flying = false;
        Vec3 source = player.position().add(0, 24, 0);
        RiftDestination.Position destination = new RiftDestination.Position(Optional.of(record.dimension().identifier()), new Vec3(20.5, 65, 20.5), Optional.empty(), Optional.empty(), false);
        RiftTemplate template = new RiftTemplate("platform", new RiftPlacement(source, new Vec3(0, 0, 1), new Vec3(1, 0, 0)),
                false, RiftShape.DEFAULT, destination, RiftAppearance.DEFAULT, 0, true);
        RiftRecord portal = RiftService.publish(c.level(), null, 0, template, null, true).record();
        try {
            player.setPos(source.add(0, .1, 0));
            RiftLanding.loadArea(target, player, destination.pos(), false);
            Consumer<SecretRealmEvent.EnterPre> deny = event -> {
                if (event.getMember().equals(player.getUUID())) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(deny);
            try {
                RiftTravelService.tryEnter(c.level(), portal, player);
                c.expect("entry veto preserves source dimension", player.level().dimension(), c.level().dimension());
                c.check("entry veto does not generate destination floor", target.getBlockState(new BlockPos(20, 64, 20)).isAir());
                c.check("entry veto leaves membership empty", SecretRealmRegistry.ofMember(player.getUUID()).isEmpty());
            } finally {
                NeoForge.EVENT_BUS.unregister(deny);
            }
            RiftTravelService.tryEnter(c.level(), portal, player);
            c.expect("coordinate gate reaches instance", player.level().dimension(), record.dimension());
            c.expect("coordinate gate platform supports feet", target.getBlockState(new BlockPos(20, 64, 20)).getBlock(), Blocks.DIRT);
            c.check("coordinate gate registers membership", SecretRealmRegistry.ofMember(player.getUUID()).isPresent());
            SecretRealmTravelAttachment travel = player.getData(MxtAttachments.SECRET_REALM_TRAVEL);
            c.check("floating entrance generates safe return point", RiftLanding.safe(c.level(), player, new Vec3(travel.originX(), travel.originY(), travel.originZ())));
            Vec3 lostOrigin = Vec3.atLowerCornerOf(c.at(25, 28, 25)).add(.5, 0, .5);
            travel.begin(record.definition(), c.level().dimension().identifier(), lostOrigin.x, lostOrigin.y, lostOrigin.z, 0, 0);
            c.check("exit synthesizes missing origin support", SecretRealmService.exit(player).isSuccess());
            c.expect("direct exit creates dirt", c.level().getBlockState(BlockPos.containing(lostOrigin).below()).getBlock(), Blocks.DIRT);
            c.expect("exit lands at supported origin", player.position(), lostOrigin);
        } finally {
            cleanup(c, record);
            RiftService.remove(c.level(), portal.id());
            player.getAbilities().flying = flying;
        }
    }

    private static void flatLanding(ProbeContext c) {
        Holder<SecretRealm> definition = c.require(MxtResourceKeys.SECRET_REALM, "flat_realm");
        SecretRealmRecord record = SecretRealmService.open(c.server(), SecretRealmService.plan(definition, 0, 12345,
                c.server().overworld().getGameTime())).orElseThrow();
        ServerLevel level = c.server().getLevel(record.dimension());
        ServerPlayer player = c.player();
        ServerPlayer second = c.join(new Vec3(8.5, 0, 8.5));
        boolean flying = player.getAbilities().flying, secondFlying = second.getAbilities().flying;
        player.getAbilities().flying = second.getAbilities().flying = false;
        Vec3 origin = player.position();
        ResourceKey<Level> originDimension = player.level().dimension();
        try {
            c.expect("flat grass surface", level.getBlockState(new BlockPos(0, 64, 0)).getBlock(), Blocks.GRASS_BLOCK);
            c.expect("flat dirt layer", level.getBlockState(new BlockPos(0, 63, 0)).getBlock(), Blocks.DIRT);
            c.check("walking arrival is safe", RiftLanding.safe(level, player, new Vec3(0.5, 65, 0.5)));
            SecretRealmService.TravelResult entered = SecretRealmService.enterFromPortal(player, definition, origin);
            c.check("flat realm portal entry succeeds", entered.isSuccess());
            c.expect("flat realm exact arrival", player.position(), new Vec3(0.5, 65, 0.5));
            SecretRealmService.TravelResult another = SecretRealmService.enter(second, c.server(), definition);
            c.check("second walking visitor can land", another.isSuccess() && RiftLanding.safe(level, second, second.position()));
            c.expect("spread visitor stays on surface", second.getY(), 65.0);
            c.check("second visitor exits", SecretRealmService.exit(second).isSuccess());
            List<RiftRecord> portals = RiftService.records(level);
            c.expect("one return portal installed", portals.size(), 1);
            RiftRecord portal = portals.getFirst();
            player.setPos(portal.data().placement().origin().add(0.1, 0, 0));
            RiftTravelService.tryEnter(level, portal, player);
            c.expect("return portal restores original dimension", player.level().dimension(), originDimension);
            c.expect("return portal restores original position", player.position(), origin);
            c.check("return portal clears travel state", !player.getData(MxtAttachments.SECRET_REALM_TRAVEL).active());
            c.check("claimed terrain persists while dormant", SecretRealmRegistry.at(record.dimension()).isPresent());
            c.check("flat realm reopens", SecretRealmService.enter(player, c.server(), definition).isSuccess());
            c.expect("reopened realm preserves terrain", player.level().getBlockState(new BlockPos(0, 64, 0)).getBlock(), Blocks.GRASS_BLOCK);
            c.check("reopened visitor exits", SecretRealmService.exit(player).isSuccess());
        } finally {
            cleanup(c, record);
            player.getAbilities().flying = flying;
            second.getAbilities().flying = secondFlying;
        }
    }

    private static SecretRealmRecord open(ProbeContext c) {
        Holder<SecretRealm> definition = c.require(MxtResourceKeys.SECRET_REALM, "trial_realm");
        return SecretRealmService.open(c.server(), SecretRealmService.plan(definition, 0, 12345, c.server().overworld().getGameTime())).orElseThrow();
    }

    private static void cleanup(ProbeContext c, SecretRealmRecord record) {
        SecretRealmRegistry.at(record.dimension()).ifPresent(current -> SecretRealmService.destroy(c.server(), current));
    }

    private static void trialLanding(ProbeContext c) {
        SecretRealmRecord record = open(c);
        ServerLevel level = c.server().getLevel(record.dimension());
        boolean flying = c.player().getAbilities().flying;
        c.player().getAbilities().flying = false;
        try {
            c.check("platform template shipped in datapack", c.server().getResourceManager().getResource(Identifier.fromNamespaceAndPath("mxt_test", "structure/probe/tower.nbt")).isPresent());
            c.expect("shipped template supports entrance without generating a probe template", level.getBlockState(new BlockPos(9, 64, 9)).getBlock(), Blocks.GOLD_BLOCK);
            c.expect("entrance centered on shipped platform", record.anchor().orElseThrow(), new Vec3(9.5, 65, 9.5));
            SecretRealmRegistry.replace(record.withAnchor(new Vec3(0.5, 65, 0.5)));
            RiftLanding.Inspection refused = SecretRealmService.checkArrival(level, c.player(), new Vec3(0.5, 65, 0.5), true);
            c.expect("unsupported entrance reports a specific cause", refused.failure(), RiftLanding.Reason.LANDING_NO_SUPPORT);
            c.check("inspection leaves no travel state", !c.player().getData(MxtAttachments.SECRET_REALM_TRAVEL).active());
            c.check("inspection leaves no membership", SecretRealmRegistry.ofMember(c.player().getUUID()).isEmpty());
            SecretRealmService.TravelResult result = SecretRealmService.enterFromPortal(c.player(), record.definition(), c.player().position());
            c.check("walking through portal generates missing landing", result.isSuccess());
            c.expect("visitor arrives on generated platform", c.player().position(), new Vec3(0.5, 65, 0.5));
            c.expect("missing support becomes dirt", level.getBlockState(new BlockPos(0, 64, 0)).getBlock(), Blocks.DIRT);
            c.check("successful entry records membership", SecretRealmRegistry.ofMember(c.player().getUUID()).isPresent());
            c.check("return succeeds", SecretRealmService.exit(c.player()).isSuccess());
        } finally {
            c.player().getAbilities().flying = flying;
            cleanup(c, record);
        }
    }

    private static void diagnostics(ProbeContext c) {
        SecretRealmRecord record = open(c);
        ServerLevel level = c.server().getLevel(record.dimension());
        Vec3 pos = new Vec3(9.5, 65, 9.5);
        BlockPos feet = BlockPos.containing(pos);
        boolean flying = c.player().getAbilities().flying;
        c.player().getAbilities().flying = false;
        try {
            c.expect("non-finite coordinates", RiftLanding.inspect(level, c.player(), new Vec3(Double.NaN, 65, 9)).failure(), RiftLanding.Reason.LANDING_NON_FINITE);
            c.expect("world border", RiftLanding.inspect(level, c.player(), new Vec3(1000, 65, 9)).failure(), RiftLanding.Reason.LANDING_OUT_OF_BOUNDS);
            level.setBlockAndUpdate(feet, Blocks.STONE.defaultBlockState());
            c.expect("body collision", RiftLanding.inspect(level, c.player(), pos).failure(), RiftLanding.Reason.LANDING_BLOCKED);
            level.setBlockAndUpdate(feet, Blocks.WATER.defaultBlockState());
            RiftLanding.Inspection fluid = RiftLanding.inspect(level, c.player(), pos);
            c.expect("fluid distinct from collision", fluid.failure(), RiftLanding.Reason.LANDING_FLUID);
            c.expect("offending block recorded", fluid.block(), feet);
            level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(feet.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
            c.expect("hazard beneath feet", RiftLanding.inspect(level, c.player(), pos).failure(), RiftLanding.Reason.LANDING_HAZARD);
            level.setBlockAndUpdate(feet.below(), Blocks.GOLD_BLOCK.defaultBlockState());
            RiftLanding.Inspection alternative = RiftLanding.search(level, c.player(), pos, true, candidate -> candidate.x > 10);
            c.check("excluded first candidate does not stop nearby search", alternative.isSuccess() && alternative.position().x > 10);
            Vec3 cold = new Vec3(49.5, 65, 49.5);
            c.expect("cold chunk identified without loading", RiftLanding.inspect(level, c.player(), cold).failure(), RiftLanding.Reason.LANDING_CHUNK_UNLOADED);
            RiftLanding.Inspection prepared = SecretRealmService.checkArrival(level, c.player(), cold, false);
            c.expect("prepared empty terrain reports no support instead of unloaded", prepared.failure(), RiftLanding.Reason.LANDING_NO_SUPPORT);
            Pig denied = new Pig(EntityType.PIG, c.level()) {
                @Override
                public boolean canTeleport(@NonNull Level from, @NonNull Level to) {
                    return false;
                }
            };
            denied.setPos(c.player().position());
            SecretRealmService.TravelResult refused = SecretRealmService.enter(denied, c.server(), record.definition());
            c.expect("platform precheck refusal preserves teleport failure category", refused.failure(), SecretRealmFailure.TELEPORT_FAILED);
            c.check("refusal leaves no membership", SecretRealmRegistry.ofMember(denied.getUUID()).isEmpty());
            denied.discard();
            c.note("Expected diagnostic log: teleport_precheck/entity_cannot_change_dimension");
        } finally {
            c.player().getAbilities().flying = flying;
            cleanup(c, record);
        }
    }

    private static void commands(ProbeContext c) {
        SecretRealmRecord record = open(c);
        CommandDispatcher<CommandSourceStack> dispatcher = c.server().getCommands().getDispatcher();
        CommandSourceStack source = c.server().createCommandSourceStack().withEntity(c.player());
        String dimension = record.dimension().identifier().toString();
        boolean flying = c.player().getAbilities().flying;
        c.player().getAbilities().flying = false;
        try {
            c.check("definitions can be queried", dispatcher.execute("secret_realm definitions", source) > 0);
            c.expect("info uses identifier argument", dispatcher.execute("mxt secret_realm info " + dimension, source), 1);
            c.expect("check verifies safe entry", dispatcher.execute("secret_realm check " + dimension, source), 1);
            c.expect("unsafe entrance edit rejected", dispatcher.execute("secret_realm anchor " + dimension + " 0.5 65 0.5", source), 0);
            c.expect("safe entrance edit persisted", dispatcher.execute("secret_realm anchor " + dimension + " 8.5 65 8.5", source), 1);
            c.expect("edited instance anchor", SecretRealmRegistry.at(record.dimension()).orElseThrow().anchor().orElseThrow(), new Vec3(8.5, 65, 8.5));
            c.check("read commands do not add membership", SecretRealmRegistry.at(record.dimension()).orElseThrow().members().isEmpty() && SecretRealmRegistry.ofMember(c.player().getUUID()).isEmpty());
            c.expect("join targets exact instance", dispatcher.execute("secret_realm join " + dimension, source), 1);
            c.expect("join uses adjusted entrance", c.player().position(), new Vec3(8.5, 65, 8.5));
            c.check("exit preserves lifecycle", SecretRealmService.exit(c.player()).isSuccess());
            c.expect("info supports dormant dimensions", dispatcher.execute("secret_realm info " + dimension, source), 1);
            c.expect("check reopens dormant dimension", dispatcher.execute("secret_realm check " + dimension, source), 1);
            Suggestions suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("secret_realm info ", source)).join();
            c.check("instance dimensions can be completed", suggestions.getList().stream().anyMatch(s -> s.getText().equals(dimension)));
            c.expect("unknown instance rejected without opening it", dispatcher.execute("secret_realm check mxt_test:missing", source), 0);
            boolean rejected = false;
            try {
                dispatcher.execute("secret_realm definitions", source.withPermission(LevelBasedPermissionSet.NO_PERMISSIONS));
            } catch (CommandSyntaxException expected) {
                rejected = true;
            }
            c.check("non-operator rejected", rejected);
        } catch (CommandSyntaxException failure) {
            c.fail("command syntax: " + failure.getMessage());
        } finally {
            c.player().getAbilities().flying = flying;
            cleanup(c, record);
        }
    }
}
