package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.network.payload.ProfilerGuiS2CPayload;
import com.iafenvoy.mxt.network.payload.ProfilerSnapshotS2CPayload;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.util.profiling.ProfilerFrame;
import com.iafenvoy.mxt.util.profiling.ProfilerSubscriptions;
import com.iafenvoy.mxt.util.profiling.ServerProfiling;
import com.mojang.brigadier.tree.CommandNode;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.ServerOpList;
import net.minecraft.server.players.ServerOpListEntry;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ProfilerGuiVerifyProbes {
    private ProfilerGuiVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/profiling/gui_permissions", ProfilerGuiVerifyProbes::permissions),
                Probe.of("verify/profiling/gui_subscription", ProfilerGuiVerifyProbes::subscription));
    }

    private static void permissions(ProbeContext context) {
        expect(context, "level 2 cannot receive profiling", ProfilerSubscriptions.mayView(LevelBasedPermissionSet.GAMEMASTER), false);
        expect(context, "level 3 can receive profiling", ProfilerSubscriptions.mayView(LevelBasedPermissionSet.ADMIN), true);
        expect(context, "level 4 can receive profiling", ProfilerSubscriptions.mayView(LevelBasedPermissionSet.OWNER), true);
        CommandNode<CommandSourceStack> root = context.server().getCommands().getDispatcher().getRoot().getChild("mxt").getChild("debug");
        CommandSourceStack source = context.server().createCommandSourceStack();
        expect(context, "the whole debug subtree rejects level 2", root.canUse(source.withPermission(LevelBasedPermissionSet.GAMEMASTER)), false);
        expect(context, "the whole debug subtree accepts level 3", root.canUse(source.withPermission(LevelBasedPermissionSet.ADMIN)), true);
    }

    private static void subscription(ProbeContext context) {
        ServerPlayer player = context.player();
        ServerOpList ops = context.server().getPlayerList().getOps();
        NameAndId identity = new NameAndId(player.getGameProfile());
        ServerOpListEntry previousOp = ops.get(identity);
        ServerGamePacketListenerImpl previousConnection = player.connection;
        boolean previousEnabled = ServerProfiling.enabled(context.server());
        boolean local = ProfilerSubscriptions.local(context.server());
        UUID[] token = new UUID[1];
        long[] localSequence = new long[1];
        List<CustomPacketPayload> sent = new ArrayList<>();
        // The replacement listener needs a real channel: the post-tick config sync asks every player's connection
        // which channels it negotiated, and a bare connection answers null.
        Connection probeConnection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(probeConnection);
        NetworkRegistry.configureMockConnection(probeConnection);
        player.connection = new ServerGamePacketListenerImpl(context.server(), probeConnection, player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override
            public void send(@NonNull Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket(CustomPacketPayload payload)
                        && (payload instanceof ProfilerGuiS2CPayload || payload instanceof ProfilerSnapshotS2CPayload))
                    sent.add(payload);
                else previousConnection.send(packet);
            }

            @Override
            public boolean isAcceptingMessages() {
                return previousConnection.isAcceptingMessages();
            }
        };
        player.connection.markClientLoaded();
        Runnable cleanup = () -> {
            if (token[0] != null) ProfilerSubscriptions.update(player, token[0], false);
            player.connection = previousConnection;
            if (previousOp == null) ops.remove(identity);
            else ops.add(previousOp);
            ServerProfiling.setEnabled(context.server(), previousEnabled);
        };
        try {
            ServerProfiling.setEnabled(context.server(), false);
            ops.add(new ServerOpListEntry(identity, LevelBasedPermissionSet.GAMEMASTER, false));
            expect(context, "level 2 cannot obtain a GUI grant", ProfilerSubscriptions.open(player), false);
            ops.add(new ServerOpListEntry(identity, LevelBasedPermissionSet.ADMIN, false));
            expect(context, "level 3 obtains a GUI grant", ProfilerSubscriptions.open(player), true);
            UUID first = ((ProfilerGuiS2CPayload) sent.getLast()).token();
            token[0] = first;
            context.after(6L, () -> {
                expect(context, "a command grant alone sends no samples", snapshots(sent), 0L);
                expect(context, "a command grant alone publishes no local frame",
                        ProfilerSubscriptions.readLocal(context.server(), player.getUUID(), first), null);
                ProfilerSubscriptions.update(player, UUID.randomUUID(), true);
                expect(context, "unknown tokens send no samples", snapshots(sent), 0L);
                ProfilerSubscriptions.update(player, first, true);
            });
            for (long delay = 7L; delay <= 11L; delay++)
                context.after(delay, () -> {
                    if (!local) return;
                    ProfilerFrame frame = ProfilerSubscriptions.readLocal(context.server(), player.getUUID(), first);
                    expect(context, "singleplayer publishes on every server tick", frame != null && frame.sequence() > localSequence[0], true);
                    if (frame != null) localSequence[0] = frame.sequence();
                    expect(context, "singleplayer tick updates send no snapshot packets", snapshots(sent), 0L);
                });
            context.after(12L, () -> {
                expect(context, "opening GUI starts delivery", local
                        ? ProfilerSubscriptions.readLocal(context.server(), player.getUUID(), first) != null : snapshots(sent) > 0L, true);
                ProfilerSubscriptions.update(player, first, false);
                expect(context, "closing GUI clears the local publication",
                        ProfilerSubscriptions.readLocal(context.server(), player.getUUID(), first), null);
                sent.clear();
            });
            context.after(18L, () -> {
                expect(context, "closing GUI stops delivery", snapshots(sent), 0L);
                ProfilerSubscriptions.open(player);
                UUID second = ((ProfilerGuiS2CPayload) sent.getLast()).token();
                token[0] = second;
                ProfilerSubscriptions.update(player, second, true);
                ProfilerSubscriptions.update(player, first, false);
                sent.clear();
            });
            context.after(24L, () -> {
                expect(context, "stale close cannot cancel a newer GUI", local
                        ? ProfilerSubscriptions.readLocal(context.server(), player.getUUID(), token[0]) != null : snapshots(sent) > 0L, true);
                ops.add(new ServerOpListEntry(identity, LevelBasedPermissionSet.GAMEMASTER, false));
                sent.clear();
            });
            context.after(30L, () -> {
                expect(context, "revoked permission stops delivery", snapshots(sent), 0L);
                expect(context, "revoked permission closes the GUI", sent.stream().anyMatch(value -> value instanceof ProfilerGuiS2CPayload gui && !gui.open()), true);
                ops.add(new ServerOpListEntry(identity, LevelBasedPermissionSet.ADMIN, false));
                ProfilerSubscriptions.open(player);
                UUID third = ((ProfilerGuiS2CPayload) sent.getLast()).token();
                token[0] = third;
                ProfilerSubscriptions.update(player, third, true);
            });
            context.after(92L, sent::clear);
            context.after(98L, () -> {
                try {
                    expect(context, "an expired GUI lease stops delivery", snapshots(sent), 0L);
                    expect(context, "an expired GUI lease clears the local publication",
                            ProfilerSubscriptions.readLocal(context.server(), player.getUUID(), token[0]), null);
                } finally {
                    cleanup.run();
                }
                context.done();
            });
        } catch (RuntimeException | Error exception) {
            cleanup.run();
            throw exception;
        }
    }

    private static long snapshots(List<CustomPacketPayload> sent) {
        return sent.stream().filter(ProfilerSnapshotS2CPayload.class::isInstance).count();
    }

    private static void expect(ProbeContext context, String message, Object actual, Object expected) {
        context.expect(message, actual, expected);
        context.note(message + ": " + (Objects.equals(actual, expected) ? "OK" : "MISMATCH")
                + " actual=" + actual + " expected=" + expected);
    }
}
