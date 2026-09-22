package com.iafenvoy.mxt.data.storage;

import java.util.List;

/**
 * Content that declares storage: the definition lists the kinds it keeps, and each kind's class is the value it
 * fills. The values themselves live with the instance that owns them, in a {@link DataStorageHolder} carried by
 * that family's attachment.
 */
public interface DataStorageDeclaration {
    // The data-pack key of this list stays "components".
    List<DataStorage> storages();

    default boolean declares(DataStorage value) {
        return this.storages().stream().anyMatch(kind -> kind.getClass() == value.getClass());
    }
}
