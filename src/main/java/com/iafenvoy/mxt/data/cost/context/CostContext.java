package com.iafenvoy.mxt.data.cost.context;

import com.iafenvoy.mxt.api.AuraAccess;
import com.iafenvoy.mxt.api.AuraCostSource;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public record CostContext(@Nullable LivingEntity payer, FormulaContext formula, String origin,
                          @Nullable ResourceHolderAttachment account, AuraCostSource auraSource,
                          @Nullable Container itemTarget) {
    public HolderLookup.Provider registries() {
        return this.formula.registries();
    }

    public static CostContext of(@Nullable LivingEntity payer, FormulaContext formula, String origin) {
        return new CostContext(payer, formula, origin, null, AuraCostSources.ACCOUNT, null);
    }

    public static CostContext of(@Nullable LivingEntity payer, String origin) {
        return of(payer, payer == null ? FormulaContext.EMPTY : FormulaContext.of(payer), origin);
    }

    public static CostContext account(@Nullable ResourceHolderAttachment account, @Nullable LivingEntity payer, FormulaContext formula, String origin) {
        return new CostContext(payer, formula, origin, account, AuraCostSources.ACCOUNT, null);
    }

    public static CostContext pool(@Nullable LivingEntity payer, Level level, BlockPos pos, FormulaContext formula, String origin) {
        return new CostContext(payer, formula, origin, null, AuraCostSources.pool(level, pos), null);
    }

    public static CostContext bank(AuraAccess bank, @Nullable LivingEntity payer, FormulaContext formula, String origin) {
        return new CostContext(payer, formula, origin, null, AuraCostSources.bank(bank), null);
    }

    public CostContext withPayer(@Nullable LivingEntity payer) {
        return new CostContext(payer, this.formula, this.origin, this.account, this.auraSource, this.itemTarget);
    }

    public CostContext withFormula(FormulaContext formula) {
        return new CostContext(this.payer, formula, this.origin, this.account, this.auraSource, this.itemTarget);
    }

    public CostContext withAuraSource(AuraCostSource source) {
        return new CostContext(this.payer, this.formula, this.origin, this.account, source, this.itemTarget);
    }

    public CostContext withItemTarget(Container itemTarget) {
        return new CostContext(this.payer, this.formula, this.origin, this.account, this.auraSource, itemTarget);
    }

    public @Nullable Player player() {
        return this.payer instanceof Player player ? player : null;
    }

    public @Nullable ResourceHolderAttachment resourceTarget() {
        if (this.account != null) return this.account;
        return this.payer == null ? null : this.payer.getData(MxtAttachments.RESOURCE_HOLDER);
    }
}
