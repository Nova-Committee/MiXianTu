package com.iafenvoy.mxt.api;

import com.iafenvoy.mxt.data.aura.Aura;
import net.minecraft.core.Holder;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Set;

/**
 * Transactional access to a bound aura inventory. Use it on the thread that owns the inventory.
 * Register a stable {@link SnapshotJournal} before each mutation and defer irreversible effects until root commit.
 * Queries must reflect the current transactional state without changing it.
 */
public interface AuraHandler {
    /**
     * Returns a read-only set of the aura types currently stored or supported for insertion.
     *
     * @return the exposed aura types
     */
    Set<Holder<Aura>> getAuras();

    /**
     * @param aura the aura to query
     * @return the finite, non-negative stored amount or zero for an unsupported aura
     */
    double getAmount(Holder<Aura> aura);

    /**
     * The capacity is a hint. Insertion determines how much the inventory accepts in the current transaction.
     *
     * @param aura the aura to query
     * @return the non-negative capacity, positive infinity for an unlimited capacity, or zero for an unsupported aura
     */
    double getCapacity(Holder<Aura> aura);

    /**
     * Returns a non-null identity for the physical storage accessed for this aura. Wrappers addressing the
     * same storage must return equal identities within the operation.
     *
     * @param aura the aura whose storage is addressed
     * @return the storage identity even when that aura is absent
     */
    Object storageIdentity(Holder<Aura> aura);

    /**
     * Tries to insert up to the requested amount.
     *
     * @param aura        the aura to insert
     * @param amount      the finite, non-negative request
     * @param transaction the active transaction that owns the change
     * @return the amount actually added within the precision of the stored balance
     * @throws IllegalArgumentException if the amount is negative or non-finite
     */
    double insert(Holder<Aura> aura, double amount, TransactionContext transaction);

    /**
     * Tries to extract up to the requested amount.
     *
     * @param aura        the aura to extract
     * @param amount      the finite, non-negative request
     * @param transaction the active transaction that owns the change
     * @return the amount actually removed within the precision of the stored balance
     * @throws IllegalArgumentException if the amount is negative or non-finite
     */
    double extract(Holder<Aura> aura, double amount, TransactionContext transaction);
}
