package com.iafenvoy.mxt.runtime.creature;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The one place a contract failure becomes text: the scroll, the bell, the bag and the command all report
 * through it, so a new failure is one enum value and one pair of lang keys instead of a message per caller.
 */
public final class ContractFeedback {
    private ContractFeedback() {
    }

    public static Component of(ContractService.Failure failure) {
        return Component.translatable("contract.mxt.failure." + failure.name().toLowerCase(Locale.ROOT));
    }
}
