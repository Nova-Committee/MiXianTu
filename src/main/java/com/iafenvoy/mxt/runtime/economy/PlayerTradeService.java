package com.iafenvoy.mxt.runtime.economy;

import com.iafenvoy.mxt.network.payload.PlayerTradeAction;
import com.iafenvoy.mxt.screen.menu.PlayerTradeMenu;
import com.iafenvoy.mxt.util.InventoryUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-owned request and settlement state for direct player-to-player trades.
 */
public final class PlayerTradeService {
    private static final long REQUEST_LIFETIME_TICKS = 20L * 60L;
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
        REQUESTS.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
        Request reciprocal = REQUESTS.get(target.getUUID());
        if (reciprocal != null && reciprocal.target.equals(requester.getUUID())) {
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
        if (side == null || (action != PlayerTradeAction.CLOSE && player.containerMenu != side.menu)) return;
        switch (action) {
            case ACCEPT -> session.setAccepted(side, true);
            case CANCEL_ACCEPT -> session.setAccepted(side, false);
            case CLOSE -> session.cancel(player.getDisplayName());
        }
    }

    private record Request(UUID target, long expiresAt) {
    }

    private record Session(Side first, Side second) {
        private Session(ServerPlayer first, ServerPlayer second) {
            this(new Side(first), new Side(second));
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
            side.accepted = accepted;
            Side partner = this.partner(side);
            partner.menu.setPartnerAccepted(accepted);
            if (this.first.accepted && this.second.accepted) this.complete();
        }

        private void complete() {
            Container firstPreview = InventoryUtil.copy(this.first.player.getInventory());
            Container secondPreview = InventoryUtil.copy(this.second.player.getInventory());
            if (!InventoryUtil.insertItems(firstPreview, this.second.offer) || !InventoryUtil.insertItems(secondPreview, this.first.offer)) {
                this.first.accepted = false;
                this.second.accepted = false;
                this.first.menu.setPartnerAccepted(false);
                this.second.menu.setPartnerAccepted(false);
                this.first.player.sendSystemMessage(Component.translatable("mxt.command.trade.no_space"));
                this.second.player.sendSystemMessage(Component.translatable("mxt.command.trade.no_space"));
                return;
            }
            InventoryUtil.insertItems(this.first.player.getInventory(), this.second.offer);
            InventoryUtil.insertItems(this.second.player.getInventory(), this.first.offer);
            this.first.player.sendSystemMessage(Component.translatable("mxt.command.trade.success"));
            this.second.player.sendSystemMessage(Component.translatable("mxt.command.trade.success"));
            this.close();
        }

        private void cancel(Component canceller) {
            InventoryUtil.insertItems(this.first.player.getInventory(), this.first.offer);
            InventoryUtil.insertItems(this.second.player.getInventory(), this.second.offer);
            this.first.player.sendSystemMessage(Component.translatable("mxt.command.trade.cancel", canceller));
            this.second.player.sendSystemMessage(Component.translatable("mxt.command.trade.cancel", canceller));
            this.close();
        }

        private void close() {
            SESSIONS.remove(this.first.player.getUUID());
            SESSIONS.remove(this.second.player.getUUID());
            this.first.player.closeContainer();
            this.second.player.closeContainer();
        }

        private Side side(ServerPlayer player) {
            if (this.first.player == player) return this.first;
            return this.second.player == player ? this.second : null;
        }

        private Side partner(Side side) {
            return side == this.first ? this.second : this.first;
        }
    }

    private static final class Side {
        private final ServerPlayer player;
        private final Container offer = new SimpleContainer(20);
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
