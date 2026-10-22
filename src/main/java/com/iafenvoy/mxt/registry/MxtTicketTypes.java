package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.TicketType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtTicketTypes {
    public static final DeferredRegister<TicketType> REGISTRY = DeferredRegister.create(Registries.TICKET_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<TicketType, TicketType> RIFT = REGISTRY.register("rift", () -> new TicketType(300, TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE | TicketType.FLAG_CAN_EXPIRE_IF_UNLOADED));

    private MxtTicketTypes() {
    }
}
