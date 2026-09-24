package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The only reader and writer of the owner-side bound beast index. An entry is a name, not the truth: the record
 * that decides anything lives on the creature, so every use that acts on a row resolves it back to the entity
 * and checks the record before trusting it.
 */
public final class BoundBeastService {
    private BoundBeastService() {
    }

    public static void add(MinecraftServer server, ServerPlayer owner, Mob beast, Holder<ContractType> type) {
        if (server == null) return;
        index(server).add(new BoundBeastsAttachment.Entry(owner.getUUID(), beast.getUUID(), type,
                beast.level().dimension(), beast.level().getGameTime()));
    }

    public static void remove(MinecraftServer server, UUID beast) {
        if (server == null) return;
        index(server).remove(beast);
    }

    public static boolean atLimit(MinecraftServer server, UUID owner, Holder<ContractType> type) {
        int limit = type.value().maxOwned();
        return server != null && limit > 0 && index(server).count(owner, type) >= limit;
    }

    public static List<BoundBeastsAttachment.Entry> of(MinecraftServer server, UUID owner) {
        return server == null ? List.of() : index(server).of(owner);
    }

    // The operator's "forget everything this owner had": the rows go, the records on the creatures do not, so a
    // beast that is still alive and bound simply reappears here the next time it is bound or listed.
    public static void clear(MinecraftServer server, UUID owner) {
        if (server == null) return;
        index(server).removeOwner(owner);
    }

    // Only a row whose creature is loaded can be judged, so an offline owner's dead beast keeps its row until the
    // creature is loaded again - which is why binding asks for the count rather than this alone.
    public static Optional<Mob> resolve(MinecraftServer server, BoundBeastsAttachment.Entry entry) {
        ServerLevel level = server.getLevel(entry.dimension());
        if (level == null) return Optional.empty();
        return level.getEntity(entry.beast()) instanceof Mob mob ? Optional.of(mob) : Optional.empty();
    }

    // Drops rows whose creature is loaded and no longer bound: the creature is the record, so a row that
    // disagrees with it is what is wrong.
    public static void prune(MinecraftServer server, UUID owner) {
        if (server == null) return;
        for (BoundBeastsAttachment.Entry entry : index(server).of(owner)) {
            Optional<Mob> beast = resolve(server, entry);
            if (beast.isEmpty() || beast.get().getData(MxtAttachments.CONTRACT).bound()) continue;
            index(server).remove(entry.beast());
        }
    }

    // The overworld copy is the server-wide one: a secret realm dimension comes and goes, and an offline owner
    // still has to lose a row when their beast dies somewhere else.
    private static BoundBeastsAttachment index(MinecraftServer server) {
        return server.overworld().getData(MxtAttachments.BOUND_BEASTS);
    }
}
