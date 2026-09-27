package com.iafenvoy.mxt.runtime.alchemy;

/**
 * Physical input and output parts. Indices are the unrotated structure cells: facing the north front, the player's
 * left is local x=2, so the main port is 14 and the auxiliary port is 12.
 */
public enum AlchemyInventoryKind {
    MAIN(14, 2),
    AUXILIARY(12, 3),
    OUTPUT(22, 4);

    private final int index;
    private final int slots;

    AlchemyInventoryKind(int index, int slots) {
        this.index = index;
        this.slots = slots;
    }

    public int index() {
        return this.index;
    }

    public int slots() {
        return this.slots;
    }

    public static AlchemyInventoryKind ofIndex(int index) {
        for (AlchemyInventoryKind kind : values()) if (kind.index == index) return kind;
        return null;
    }
}
