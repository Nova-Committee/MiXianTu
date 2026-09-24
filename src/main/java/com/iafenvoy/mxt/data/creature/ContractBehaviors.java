package com.iafenvoy.mxt.data.creature;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The orders the framework defines, and the one place an order is looked up by id.
 *
 * <p>There is no enum to extend: a content mod makes a {@link ContractBehavior} of its own and registers it here
 * (from its mod setup), and its creatures answer {@code ContractOperations#behaviors} with it. Registration is
 * what lets a stored order be read back - both a creature's contract record and a bell keep the id, and both
 * sides resolve it here.</p>
 */
public final class ContractBehaviors {
    /** Walk to the owner and keep up with them. The order every contract starts under. */
    public static final ContractBehavior FOLLOW = new ContractBehavior(id("follow"), false);
    /** Stroll around the owner without following them step for step. */
    public static final ContractBehavior WANDER = new ContractBehavior(id("wander"), false);
    /** Hold position: no path, no quarry. */
    public static final ContractBehavior STAY = new ContractBehavior(id("stay"), false);
    /** Come back to the owner once; the record keeps the order that was in force. */
    public static final ContractBehavior RECALL = new ContractBehavior(id("recall"), true);
    /** What a creature takes when it declares nothing of its own. */
    public static final List<ContractBehavior> BUILT_IN = List.of(FOLLOW, WANDER, STAY, RECALL);
    // Registered during mod setup and only read from the game thread afterwards, so it needs no lock; the first
    // registration of an id wins, which keeps what an id means stable across a reload.
    private static final Map<Identifier, ContractBehavior> REGISTERED = new LinkedHashMap<>();

    static {
        for (ContractBehavior behavior : BUILT_IN) REGISTERED.put(behavior.id(), behavior);
    }

    private ContractBehaviors() {
    }

    public static void register(ContractBehavior behavior) {
        REGISTERED.putIfAbsent(behavior.id(), behavior);
    }

    public static Optional<ContractBehavior> byId(Identifier id) {
        return Optional.ofNullable(REGISTERED.get(id));
    }

    public static boolean isKnown(Identifier id) {
        return REGISTERED.containsKey(id);
    }

    public static List<ContractBehavior> known() {
        return List.copyOf(REGISTERED.values());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path);
    }
}
