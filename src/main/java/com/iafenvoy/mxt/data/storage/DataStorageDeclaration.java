package com.iafenvoy.mxt.data.storage;

import java.util.List;

/**
 * Content that declares storage: the definition lists the kinds it keeps, and each kind's class is the value it
 * fills. Anything a family wants to keep for its instances is declared here, which is also what lets a generic
 * writer — a command, a script or {@code mxt:modify_storage} — check that a value is legal before writing it.
 *
 * <p>The values themselves live with the instance that owns them, in a {@link DataStorageHolder} carried by that
 * family's attachment.</p>
 */
public interface DataStorageDeclaration {
    /**
     * The declared kinds, in data-pack order. The data-pack key of this list stays {@code components}.
     */
    List<DataStorage> storages();

    /**
     * Whether this host declares a kind of the same class as the given value, which is what makes a write legal.
     */
    default boolean declares(DataStorage value) {
        return this.storages().stream().anyMatch(kind -> kind.getClass() == value.getClass());
    }
}
