package com.iafenvoy.mxt.probe;

import com.iafenvoy.mxt.testmod.MxtTestMod;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The server side of one probe: the helper, a real player standing in the arena, and the ledger of checks the body
 * ran.
 *
 * <p>The player is a genuine {@link ServerPlayer} in the level, not a {@code FakePlayer}: it is ticked, it is in
 * {@code level.players()}, and opening a menu really opens one. Those are exactly the parts the old command driven
 * probes could not reach. The next probe retires the players the previous one left behind, so a run never has two
 * probes' players alive at once.
 *
 * <p>Checks are collected rather than thrown, so one failing probe reports every disagreement it found instead of
 * stopping at the first. The body must be synchronous, or must schedule all of its later steps with {@link #after}
 * while it is still running: {@code GameTestInfo} iterates its tick callbacks while running them, so a step that
 * schedules another step would corrupt that iteration.
 */
public final class ProbeContext {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<ServerPlayer> LIVE = new ArrayList<>();
    private static final AtomicInteger PLAYER_SEQUENCE = new AtomicInteger();
    /**
     * Arena relative feet position of the probe's own player: near the north west corner, leaving the body room.
     */
    private static final Vec3 PLAYER_SPOT = new Vec3(4.5D, 0.0D, 4.5D);
    private static final int REPORT_LIMIT = 4000;

    private final GameTestHelper helper;
    private final ServerLevel level;
    private final ServerPlayer player;
    private final List<String> failures = new ArrayList<>();
    private boolean deferred;

    ProbeContext(GameTestHelper helper) {
        this.helper = helper;
        this.level = helper.getLevel();
        retireLive();
        this.player = this.join(PLAYER_SPOT);
    }

    static void run(Probe probe, GameTestHelper helper) {
        ProbeContext context = new ProbeContext(helper);
        if (probe.kit()) ProbeKit.scenario(context);
        probe.body().accept(context);
        if (!context.deferred) context.done();
    }

    public GameTestHelper helper() {
        return this.helper;
    }

    public ServerLevel level() {
        return this.level;
    }

    public MinecraftServer server() {
        return this.level.getServer();
    }

    public ServerPlayer player() {
        return this.player;
    }

    public RegistryAccess registries() {
        return this.level.registryAccess();
    }

    /**
     * Arena relative to absolute: relative {@code (0, 0, 0)} is the north west bottom corner of the arena.
     */
    public BlockPos at(int x, int y, int z) {
        return this.helper.absolutePos(new BlockPos(x, y, z));
    }

    public BlockPos at(BlockPos relative) {
        return this.helper.absolutePos(relative);
    }

    /**
     * The arena relative spot {@code index} of the twelve block grid the old probes laid beings out on.
     */
    public BlockPos grid(int index) {
        return this.at(16 + index % 4 * 12, 2, 16 + index / 4 * 12);
    }

    public BlockPos playerSpot() {
        return this.player.blockPosition();
    }

    public void playerAt(BlockPos relative) {
        this.playerAt(Vec3.atLowerCornerOf(relative));
    }

    /**
     * Moves the probe's player to an arena relative position; {@code y} is the feet level, so 0 stands on the floor.
     */
    public void playerAt(Vec3 relative) {
        this.place(this.player, relative);
    }

    public void look(float yRot, float xRot) {
        this.player.setYRot(yRot);
        this.player.setXRot(xRot);
        this.player.setYHeadRot(yRot);
    }

    /**
     * A second real player, for the probes that need two people. Retired with the first at the end of the probe.
     */
    public ServerPlayer join(Vec3 relative) {
        ServerPlayer joined = this.makePlayer();
        this.place(joined, relative);
        return joined;
    }

    public ServerPlayer joinBeside(ServerPlayer anchor, double dx, double dz) {
        return this.join(new Vec3(anchor.getX() - this.originX() + dx, anchor.getY() - this.originY(), anchor.getZ() - this.originZ() + dz));
    }

    public boolean check(String message, boolean value) {
        if (!value) this.failures.add(message);
        return value;
    }

    public void fail(String message) {
        this.failures.add(message);
    }

    /**
     * Records a disagreement as {@code name: actual != expected}; for the checks the old probes spelled out by hand.
     */
    public void expect(String name, Object actual, Object expected) {
        if (!Objects.equals(actual, expected)) this.failures.add(name + ": " + actual + " != " + expected);
    }

    /**
     * Progress output: it goes to the run log, never to the verdict.
     */
    public void note(String message) {
        LOGGER.info("probe {}: {}", this.helper.testInfo.id(), message);
    }

    /**
     * Schedules a step {@code ticks} later. All steps must be scheduled from the body, see the class comment.
     */
    public void after(long ticks, Runnable step) {
        this.deferred = true;
        this.helper.runAfterDelay(ticks, step);
    }

    /**
     * Ends a body that used {@link #after}: the last step has to call this.
     */
    public void done() {
        if (!this.failures.isEmpty()) {
            List<String> reported = this.failures;
            this.helper.fail(Component.literal(this.report(reported)));
        } else {
            this.helper.succeed();
        }
    }

    public Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, path);
    }

    public <T> Holder<T> require(ResourceKey<? extends Registry<T>> registry, String path) {
        return this.require(registry, this.id(path));
    }

    public <T> Holder<T> require(ResourceKey<? extends Registry<T>> registry, Identifier id) {
        return this.registries().lookupOrThrow(registry).get(id)
                .orElseThrow(() -> new IllegalStateException("the test pack is missing " + id + " in " + registry.identifier()));
    }

    private String report(List<String> reported) {
        StringBuilder builder = new StringBuilder();
        for (String failure : reported) {
            if (builder.length() > 0) builder.append("; ");
            builder.append(failure);
        }
        return builder.length() > REPORT_LIMIT ? builder.substring(0, REPORT_LIMIT) + "..." : builder.toString();
    }

    /**
     * A real player in the level, built the way {@code GameTestHelper.makeMockServerPlayerInLevel} builds one, with two
     * extra steps that make it usable here:
     *
     * <p>{@code NetworkRegistry.configureMockConnection} tells NeoForge the connection negotiated every channel,
     * because the login events a place fires (Curios and JEI both answer {@code OnDatapackSyncEvent} with a payload) are
     * refused outright on a connection that never handshaked.
     *
     * <p>The body is an anonymous subclass in survival. The subclass matters to the platform:
     * {@code ConfigSync.syncPendingConfigs} throws for a player whose class is exactly {@code ServerPlayer} and skips
     * one that is a subclass, because a game test player never goes through the configuration phase and so never has a
     * pending config entry. Survival matters to the probes: a creative body is invulnerable through its abilities and
     * never consumes what it uses, so every probe that prices something would pass its own price by.
     */
    private ServerPlayer makePlayer() {
        MinecraftServer server = this.level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "probe-" + Math.abs(PLAYER_SEQUENCE.incrementAndGet()));
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer created = new ServerPlayer(server, this.level, profile, cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, created, cookie);
        // A mock client never sends `ServerboundPlayerLoadedPacket`, and `ServerPlayer.isInvulnerableTo` reads
        // `connection.hasClientLoaded()` - whose timer starts at 60 ticks - as a third reason to be invulnerable. Left
        // alone, every probe that expects the body to be hurt would silently see nothing happen.
        created.connection.markClientLoaded();
        created.getAbilities().instabuild = false;
        created.getAbilities().invulnerable = false;
        created.onUpdateAbilities();
        LIVE.add(created);
        return created;
    }

    private void place(ServerPlayer target, Vec3 relative) {
        BlockPos corner = this.helper.absolutePos(BlockPos.ZERO);
        target.teleportTo(this.level, corner.getX() + relative.x, corner.getY() + relative.y, corner.getZ() + relative.z,
                Set.of(), 0.0F, 0.0F, false);
    }

    private double originX() {
        return this.helper.absolutePos(BlockPos.ZERO).getX();
    }

    private double originY() {
        return this.helper.absolutePos(BlockPos.ZERO).getY();
    }

    private double originZ() {
        return this.helper.absolutePos(BlockPos.ZERO).getZ();
    }

    private static void retireLive() {
        for (ServerPlayer retired : LIVE) {
            MinecraftServer server = retired.level().getServer();
            if (server != null) server.getPlayerList().remove(retired);
            retired.discard();
        }
        LIVE.clear();
    }
}
