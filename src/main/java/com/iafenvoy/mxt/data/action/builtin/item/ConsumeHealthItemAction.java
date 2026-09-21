package com.iafenvoy.mxt.data.action.builtin.item;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.context.action.ItemActionContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * Takes health from the acting entity, which is the shape a price is paid in: binding an artifact, and anything
 * else a pack would rather charge in blood than in items.
 *
 * <p>Charged as ordinary magic damage rather than subtracted from the health value, so resistances, absorption,
 * death and an untouchable holder all behave the way they do everywhere else - and so an invulnerable holder
 * pays nothing at all. {@code amount} is required: a price with no number is a mistake, not a free effect.</p>
 *
 * <p>An action is an effect and never a gate: nothing here asks whether the holder can afford it, and nothing
 * refuses on its behalf. That is what lets it be the default of an artifact's {@code claim_action} - this
 * action for {@link com.iafenvoy.mxt.data.artifact.Artifact#DEFAULT_CLAIM_HEALTH} points - and what makes a
 * claim able to kill the holder who just made it.</p>
 */
public record ConsumeHealthItemAction(NumberProvider amount) implements ItemAction {
    public static final MapCodec<ConsumeHealthItemAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("amount").forGetter(ConsumeHealthItemAction::amount)
    ).apply(i, ConsumeHealthItemAction::new));

    @Override
    public void execute(@NonNull ItemActionContext ctx) {
        // Health is a server decision, and it belongs to a living holder: an action run for a stack with no
        // actor, or one run on a client level, has nobody to charge.
        if (!(ctx.holder() instanceof LivingEntity holder)) return;
        if (!(holder.level() instanceof ServerLevel level)) return;
        double amount = this.amount.evaluate(ctx.formula());
        if (!Double.isFinite(amount) || amount <= 0.0D) return;
        holder.hurtServer(level, holder.damageSources().magic(), (float) amount);
    }

    @Override
    public @NonNull MapCodec<ConsumeHealthItemAction> codec() {
        return CODEC;
    }
}
