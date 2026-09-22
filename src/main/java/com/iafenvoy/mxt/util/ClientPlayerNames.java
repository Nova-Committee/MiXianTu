package com.iafenvoy.mxt.util;

import com.iafenvoy.mxt.network.payload.OwnerNameC2SPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client half of {@link PlayerNames}: the players the current connection lists, plus whatever the server has
 * told this client about the rest, asked once and then remembered because a tooltip cannot wait for an answer.
 * Kept apart so a dedicated server never loads it, where {@code Minecraft} does not exist.
 */
public final class ClientPlayerNames {
    /** What the server has answered so far, including "no such player" - a question is not asked twice. */
    private static final Map<UUID, Optional<String>> ANSWERED = new ConcurrentHashMap<>();
    /** The ids this session has already asked about, so a frame that cannot be answered does not ask again. */
    private static final Set<UUID> ASKED = ConcurrentHashMap.newKeySet();

    private ClientPlayerNames() {
    }

    public static Optional<String> lookup(UUID id) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return Optional.empty();
        PlayerInfo info = connection.getPlayerInfo(id);
        if (info != null) return Optional.of(info.getProfile().name());
        Optional<String> answered = ANSWERED.get(id);
        if (answered != null) return answered;
        if (ASKED.add(id)) ClientPacketDistributor.sendToServer(new OwnerNameC2SPayload(id));
        return Optional.empty();
    }

    /** What the server answered for one id; empty means it has no name for that player. */
    public static void remember(UUID id, Optional<String> name) {
        ANSWERED.put(id, name);
    }
}
