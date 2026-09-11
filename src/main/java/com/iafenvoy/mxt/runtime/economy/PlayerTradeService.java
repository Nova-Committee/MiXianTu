package com.iafenvoy.mxt.runtime.economy;

import com.iafenvoy.mxt.network.payload.PlayerTradeActionC2SPayload.PlayerTradeAction;
import com.iafenvoy.mxt.screen.menu.PlayerTradeMenu;
import com.iafenvoy.mxt.util.InventoryUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-owned request and settlement state for direct player-to-player trades.
 *
 * <p>Session and request state is append-only while a trade is live: entries are added on request
 * or on session creation, and only removed when a session reaches a terminal transition, a player
 * disconnects, or a request expires. Every terminal transition is one-shot through
 * {@link Session#closed}, so a stale action that still holds an old session reference can never
 * move items twice.</p>
 */
@EventBusSubscriber
public final class PlayerTradeService {
    private static final long REQUEST_LIFETIME_TICKS = 20L * 60L;
    /**
     * How often the module re-checks live state for the sessions and requests it still holds.
     */
    private static final long STALE_CHECK_INTERVAL_TICKS = 20L;
    private static final Map<UUID, Request> REQUESTS = new HashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private PlayerTradeService() {
    }

    public enum RequestResult {
        SENT,
        STARTED,
        TOO_FAR,
        SELF,
        BUSY
    }

    public static RequestResult request(ServerPlayer requester, ServerPlayer target) {
        if (requester == target) return RequestResult.SELF;
        if (requester.distanceToSqr(target) > 25.0D) return RequestResult.TOO_FAR;
        if (SESSIONS.containsKey(requester.getUUID()) || SESSIONS.containsKey(target.getUUID()))
            return RequestResult.BUSY;
        long now = requester.level().getGameTime();
        removeExpiredRequests(now);
        Request reciprocal = REQUESTS.get(target.getUUID());
        if (reciprocal != null && reciprocal.target().equals(requester.getUUID())) {
            REQUESTS.remove(target.getUUID());
            REQUESTS.remove(requester.getUUID());
            new Session(requester, target).open();
            return RequestResult.STARTED;
        }
        REQUESTS.put(requester.getUUID(), new Request(target.getUUID(), now + REQUEST_LIFETIME_TICKS));
        return RequestResult.SENT;
    }

    public static void handleAction(ServerPlayer player, PlayerTradeAction action) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        Side side = session.side(player);
        if (side == null || (action != PlayerTradeAction.CLOSE && player.containerMenu != side.menu))
            return;
        switch (action) {
            case ACCEPT -> session.setAccepted(side, true);
            case CANCEL_ACCEPT -> session.setAccepted(side, false);
            case CLOSE -> session.cancel(player.getDisplayName());
        }
    }

    /**
     * Reclaims state this module still holds. Requests already expire lazily on
     * {@link #request(ServerPlayer, ServerPlayer)}; this pass also drops sessions whose sides are no
     * longer connected, which lazy expiry cannot observe.
     */
    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % STALE_CHECK_INTERVAL_TICKS != 0L) return;
        for (Session session : new LinkedHashSet<>(SESSIONS.values())) {
            if (session.isLive()) continue;
            session.discard(null);
        }
        removeExpiredRequests(level.getGameTime());
    }

    /**
     * A disconnect must not leave partner items inside an offer container that nobody can reach.
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        REQUESTS.remove(player.getUUID());
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) session.discard(session.side(player));
    }

    /**
     * Death drops the inventory, so a settled session is returned before it can move items into an
     * inventory that is about to be emptied.
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        REQUESTS.remove(player.getUUID());
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) session.discard(session.side(player));
    }

    private static void removeExpiredRequests(long now) {
        REQUESTS.values().removeIf(request -> request.expiresAt() < now);
    }

    /**
     * Removes one side's session entry only when it still points at the given session, so a newer
     * session registered under the same UUID is never evicted by an older close.
     */
    private static void forgetSession(UUID player, Session session) {
        SESSIONS.remove(player, session);
    }

    private record Request(UUID target, long expiresAt) {
    }

    /**
     * One live trade between two connected players.
     */
    private static final class Session {
        private final Side first;
        private final Side second;
        private boolean closed;

        private Session(ServerPlayer first, ServerPlayer second) {
            this.first = new Side(first);
            this.second = new Side(second);
            SESSIONS.put(first.getUUID(), this);
            SESSIONS.put(second.getUUID(), this);
        }

        private void open() {
            this.open(this.first, this.second);
            this.open(this.second, this.first);
        }

        private void open(Side current, Side partner) {
            current.player.openMenu(new MenuProvider() {
                @Override
                public @NonNull Component getDisplayName() {
                    return Component.translatable("screen.mxt.player_trade");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, @NonNull Inventory inventory, @NonNull Player player) {
                    current.menu = new PlayerTradeMenu(containerId, inventory, current.offer, partner.offer, partner.player.getDisplayName());
                    return current.menu;
                }
            }, buffer -> writePartnerName(buffer, partner.player.getDisplayName()));
        }

        private void setAccepted(Side side, boolean accepted) {
            if (this.closed) return;
            side.accepted = accepted;
            Side partner = this.partner(side);
            // A side that already closed its menu no longer has a slot to notify; the pending
            // acceptance is kept so the trade still resolves once its own menu is gone.
            if (partner.menu != null) partner.menu.setPartnerAccepted(accepted);
            if (this.first.accepted && this.second.accepted) this.complete();
        }

        /**
         * Settles both offers by moving each offer into the partner's inventory. Overflow is
         * committed to the ground next to its own owner.
         */
        private void complete() {
            if (this.closed) return;
            this.swap(this.first, this.second);
            this.swap(this.second, this.first);
            this.first.player.sendSystemMessage(Component.translatable("command.mxt.trade.success"));
            this.second.player.sendSystemMessage(Component.translatable("command.mxt.trade.success"));
            this.close();
        }

        /**
         * Moves {@code source.offer} into {@code receiver}'s inventory, then clears the offer so no
         * item can be handed out twice. Anything the inventory cannot hold is appended to
         * {@code receiver}'s own overflow list.
         */
        private void swap(Side receiver, Side source) {
            for (int index = 0; index < source.offer.getContainerSize(); index++) {
                ItemStack stack = source.offer.getItem(index);
                if (stack.isEmpty()) continue;
                ItemStack remainder = stack.copy();
                receiver.player.getInventory().add(remainder);
                receiver.overflow.add(remainder);
            }
            for (int index = 0; index < source.offer.getContainerSize(); index++)
                source.offer.setItem(index, ItemStack.EMPTY);
            source.offer.setChanged();
        }

        /**
         * Commits one side's overflow to the ground. {@code Inventory.add} empties the stack it
         * successfully inserts, so the common path has nothing left to drop.
         */
        private void settleOverflow(Side side) {
            for (ItemStack stack : side.overflow) {
                if (!stack.isEmpty()) side.player.drop(stack, false);
            }
            side.overflow.clear();
        }

        private void cancel(Component canceller) {
            if (this.closed) return;
            InventoryUtil.insertItems(this.first.player.getInventory(), this.first.offer);
            InventoryUtil.insertItems(this.second.player.getInventory(), this.second.offer);
            this.first.player.sendSystemMessage(Component.translatable("command.mxt.trade.cancel", canceller));
            this.second.player.sendSystemMessage(Component.translatable("command.mxt.trade.cancel", canceller));
            this.close();
        }

        /**
         * Disconnecting or dying must never leave partner items inside an unreachable offer
         * container, so both sides are returned before the session is dropped.
         */
        private void discard(Side leaving) {
            if (this.closed) return;
            InventoryUtil.insertItems(this.first.player.getInventory(), this.first.offer);
            InventoryUtil.insertItems(this.second.player.getInventory(), this.second.offer);
            Side partner = leaving == null ? null : this.partner(leaving);
            // Only notify a player who is still connected: the stale pass can discard a session
            // whose remaining side has also gone offline.
            if (partner != null && this.isOnline(partner.player))
                partner.player.sendSystemMessage(Component.translatable("command.mxt.trade.partner_left"));
            this.close();
        }

        private void close() {
            if (this.closed) return;
            this.closed = true;
            this.settleOverflow(this.first);
            this.settleOverflow(this.second);
            forgetSession(this.first.player.getUUID(), this);
            forgetSession(this.second.player.getUUID(), this);
            this.first.player.closeContainer();
            this.second.player.closeContainer();
        }

        private Side side(ServerPlayer player) {
            if (this.first.player == player) return this.first;
            return this.second.player == player ? this.second : null;
        }

        /**
         * A session stays usable only while both sides are connected.
         */
        private boolean isLive() {
            return this.isOnline(this.first.player) && this.isOnline(this.second.player);
        }

        private boolean isOnline(ServerPlayer player) {
            return player.level().getServer().getPlayerList().getPlayer(player.getUUID()) != null;
        }

        private Side partner(Side side) {
            return side == this.first ? this.second : this.first;
        }
    }

    private static final class Side {
        private final ServerPlayer player;
        private final Container offer = new SimpleContainer(20);
        /**
         * Items that could not fit when this side received a partner offer. They are dropped next to
         * this side when the session closes.
         */
        private final List<ItemStack> overflow = new ArrayList<>();
        private PlayerTradeMenu menu;
        private boolean accepted;

        private Side(ServerPlayer player) {
            this.player = player;
        }
    }

    private static void writePartnerName(RegistryFriendlyByteBuf buffer, Component name) {
        ComponentSerialization.STREAM_CODEC.encode(buffer, name);
    }
}
