package com.iafenvoy.mxt.data.cost.context;

import com.iafenvoy.mxt.api.AuraAccess;
import com.iafenvoy.mxt.api.AuraCostSource;
import com.iafenvoy.saturn.data.cost.context.CostContext;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The aura payment channel, carried in {@link CostContext#data()} under one namespaced key because Saturn charges
 * resources and items itself and lets a dependent mod name any other store. A context that names no store spends the
 * account its resource amounts come out of, which is the ordinary player, formation and recipe case; an
 * {@code mxt:aura} entry reports {@code no_channel} only when that account is absent.
 */
public final class AuraCostContexts {
    public static final String KEY = "mxt:aura_source";

    private AuraCostContexts() {
    }

    public static CostContext withAuraSource(CostContext context, AuraCostSource source) {
        return context.with(KEY, source);
    }

    /**
     * A pool at one position: the shared stock a cultivation tick spends the ground's aura out of.
     */
    public static CostContext pool(@Nullable LivingEntity payer, Level level, BlockPos pos, FormulaContext formula, String origin) {
        return withAuraSource(CostContext.of(payer, formula, origin), AuraCostSources.pool(level, pos));
    }

    /**
     * A store that is not a world pool - a block entity holding its own aura, such as the spirit crafting table.
     */
    public static CostContext bank(AuraAccess bank, @Nullable LivingEntity payer, FormulaContext formula, String origin) {
        return withAuraSource(CostContext.of(payer, formula, origin), AuraCostSources.bank(bank));
    }

    public static AuraCostSource source(CostContext context) {
        return context.get(KEY).map(AuraCostSource.class::cast).orElse(AuraCostSources.ACCOUNT);
    }
}
