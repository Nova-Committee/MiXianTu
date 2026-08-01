package com.iafenvoy.mxt.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

/**
 * Shared bounded wire primitives for MXT request payloads.
 */
final class PayloadCodecs {
    static final int MAX_IDENTIFIER_LENGTH = 256;

    private PayloadCodecs() {
    }

    static Identifier readIdentifier(RegistryFriendlyByteBuf buffer) {
        return Identifier.parse(buffer.readUtf(MAX_IDENTIFIER_LENGTH));
    }

    static void writeIdentifier(RegistryFriendlyByteBuf buffer, Identifier value) {
        buffer.writeUtf(value.toString(), MAX_IDENTIFIER_LENGTH);
    }
}
