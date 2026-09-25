package com.iafenvoy.mxt.data.cost.context;

import com.iafenvoy.mxt.api.AuraAccess;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Who is paying, where, and which channels this payment may use. The payer is a {@link LivingEntity} and not a
 * player: the resource attachment is an entity attachment, so any living entity can pay one. The inventory and
 * script channels are only offered when the payer happens to be a {@link Player}, which is what a cost entry that
 * needs them is checked against - a missing channel is a plain "cannot pay", not an error.
 * <p>
 * A resource account can also be named without a payer at all, which is how a formation pays its upkeep while its
 * owner is offline: there is an account to charge and nobody to ask.
 */
public record CostContext(@Nullable LivingEntity payer, FormulaContext formula, CostOrigin origin,
                          @Nullable Level level, @Nullable BlockPos pos, @Nullable AuraAccess bank,
                          @Nullable ResourceHolderAttachment account, AuraTarget auraTarget) {
    /**
     * Where an {@code mxt:aura} entry takes its aura from.
     */
    public enum AuraTarget {
        /**
         * As the resource that aura is measured in, out of the context's resource account.
         */
        VALUE,
        /**
         * The shared aura pool at the context position.
         */
        POOL,
        /**
         * A block entity's own store.
         */
        BANK
    }

    /**
     * A payer and nothing else: aura costs become the resource that aura is counted in.
     */
    public static CostContext of(@Nullable LivingEntity payer, FormulaContext formula, CostOrigin origin) {
        return new CostContext(payer, formula, origin, null, null, null, null, AuraTarget.VALUE);
    }

    public static CostContext of(@Nullable LivingEntity payer, CostOrigin origin) {
        return of(payer, payer == null ? FormulaContext.EMPTY : FormulaContext.of(payer), origin);
    }

    /**
     * An explicit resource account, optionally with a payer that owns it.
     */
    public static CostContext account(ResourceHolderAttachment account, @Nullable LivingEntity payer,
                                      FormulaContext formula, CostOrigin origin) {
        return new CostContext(payer, formula, origin, null, null, null, account, AuraTarget.VALUE);
    }

    /**
     * The shared aura pool at a position (cultivation cycles).
     */
    public static CostContext pool(@Nullable LivingEntity payer, Level level, BlockPos pos, FormulaContext formula,
                                   CostOrigin origin) {
        return new CostContext(payer, formula, origin, level, pos.immutable(), null, null, AuraTarget.POOL);
    }

    /**
     * A block entity's own store (spirit crafting, formation upkeep).
     */
    public static CostContext bank(AuraAccess bank, @Nullable LivingEntity payer, FormulaContext formula,
                                   CostOrigin origin) {
        return new CostContext(payer, formula, origin, null, null, bank, null, AuraTarget.BANK);
    }

    public CostContext withPayer(@Nullable LivingEntity payer) {
        return new CostContext(payer, this.formula, this.origin, this.level, this.pos, this.bank, this.account,
                this.auraTarget);
    }

    public CostContext withFormula(FormulaContext formula) {
        return new CostContext(this.payer, formula, this.origin, this.level, this.pos, this.bank, this.account,
                this.auraTarget);
    }

    public CostContext withAuraTarget(AuraTarget auraTarget) {
        return new CostContext(this.payer, this.formula, this.origin, this.level, this.pos, this.bank, this.account,
                auraTarget);
    }

    public @Nullable Player player() {
        return this.payer instanceof Player player ? player : null;
    }

    /**
     * The account resource amounts come out of: the named one, or the payer's own attachment.
     */
    public @Nullable ResourceHolderAttachment resourceTarget() {
        if (this.account != null) return this.account;
        return this.payer == null ? null : this.payer.getData(MxtAttachments.RESOURCE_HOLDER);
    }

    public boolean hasChannel(CostChannel channel) {
        return switch (channel) {
            case RESOURCE_ACCOUNT -> this.resourceTarget() != null;
            case PLAYER_INVENTORY, SCRIPT -> this.player() != null;
            case WORLD_AURA -> this.level != null && this.pos != null;
            case AURA_BANK -> this.bank != null;
        };
    }
}
