package com.iafenvoy.mxt.runtime.forging;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player, per-table strike rate limit.
 *
 * <p>Prevents both autoclicker spam and a modified client that fires strike packets far faster
 * than a human can. Entries are dropped once their cooldown has elapsed, so the map only ever
 * holds the strikes that are still cooling down.</p>
 */
public final class ForgingRateLimiter {
    private static final Map<Key, Long> LAST_STRIKE = new LinkedHashMap<>();

    private ForgingRateLimiter() {
    }

    /**
     * Returns true and records the strike when the player is allowed to act now.
     *
     * @param cooldownTicks the method cooldown, in ticks; {@code 0} always allows
     */
    public static boolean tryAcquire(ServerPlayer player, ForgingSurface surface, int cooldownTicks, long gameTime) {
        if (cooldownTicks <= 0) return true;
        Key key = key(player, surface);
        if (key == null) return true;
        Long last = LAST_STRIKE.get(key);
        if (last != null && gameTime - last < cooldownTicks) return false;
        LAST_STRIKE.put(key, gameTime);
        return true;
    }

    /**
     * Remaining cooldown ticks, used by the client-facing scene so the button can grey out.
     */
    public static int remaining(ServerPlayer player, ForgingSurface surface, int cooldownTicks, long gameTime) {
        if (cooldownTicks <= 0) return 0;
        Key key = key(player, surface);
        if (key == null) return 0;
        Long last = LAST_STRIKE.get(key);
        if (last == null) return 0;
        long elapsed = gameTime - last;
        if (elapsed >= cooldownTicks) return 0;
        return (int) (cooldownTicks - elapsed);
    }

    /**
     * Forgets a player's entries. Called when the player leaves so a rejoin starts clean.
     */
    public static void forget(UUID player) {
        LAST_STRIKE.keySet().removeIf(key -> key.player().equals(player));
    }

    /**
     * Drops entries that no longer have a live table or player behind them. Runs periodically
     * from the forge event bridge.
     */
    public static void prune(MinecraftServer server, long gameTime) {
        LAST_STRIKE.entrySet().removeIf(entry -> {
            Key key = entry.getKey();
            ServerPlayer player = server.getPlayerList().getPlayer(key.player());
            if (player == null) return true;
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null || !level.hasChunkAt(key.position())) return true;
            BlockEntity blockEntity = level.getBlockEntity(key.position());
            return !(blockEntity instanceof ForgingSurface);
        });
    }

    private static Key key(ServerPlayer player, ForgingSurface surface) {
        if (!(surface instanceof BlockEntity blockEntity) || blockEntity.getLevel() == null) return null;
        return new Key(blockEntity.getLevel().dimension(), blockEntity.getBlockPos(), player.getUUID());
    }

    private record Key(ResourceKey<Level> dimension, BlockPos position,
                       UUID player) {
    }
}
