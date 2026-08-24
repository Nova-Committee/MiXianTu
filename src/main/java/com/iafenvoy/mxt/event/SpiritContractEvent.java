package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.ContractAttachment;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative lifecycle event for a creature contract attachment.
 */
public abstract class SpiritContractEvent extends Event {
    private final ContractAttachment contract;
    private final Optional<Identifier> contractType;
    private final UUID requester;
    private final Action action;

    protected SpiritContractEvent(@NotNull ContractAttachment contract, @NotNull Optional<Identifier> contractType, @NotNull UUID requester, @NotNull Action action) {
        this.contract = contract;
        this.contractType = contractType;
        this.requester = requester;
        this.action = action;
    }

    public ContractAttachment contract() {
        return this.contract;
    }

    public Optional<Identifier> contractType() {
        return this.contractType;
    }

    public UUID requester() {
        return this.requester;
    }

    public Action action() {
        return this.action;
    }

    public static final class Pre extends SpiritContractEvent implements ICancellableEvent {
        public Pre(ContractAttachment contract, Optional<Identifier> contractType, UUID requester, Action action) {
            super(contract, contractType, requester, action);
        }
    }

    public static final class Post extends SpiritContractEvent {
        public Post(ContractAttachment contract, Optional<Identifier> contractType, UUID requester, Action action) {
            super(contract, contractType, requester, action);
        }
    }

    public enum Action {BIND, BREAK, RECALL, RELEASE}
}
