package com.iafenvoy.mxt.runtime.damage;

import com.iafenvoy.mxt.runtime.element.ElementReactionService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The reduction layer of the damage pipeline, on {@link LivingIncomingDamageEvent} - the only hook that sees a
 * mob's swing, a fall or another mod's hit. Elemental buildup happens here too, so a lava bath feeds the same
 * system as our own fireball.
 *
 * <p>The event fires exactly once per damage sequence, which is why no dealing path may apply this layer again.
 * A {@link DamageCalculationService#NO_BONUS} strike is left exactly as it arrived, and is not remembered as an
 * element either. The listener never cancels: refusing a hit belongs to the protection rules.
 */
@EventBusSubscriber
public final class DamageEventBridge {
    private DamageEventBridge() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        // A cancelled sequence applies nothing, so changing its amount would only rewrite a number nobody reads.
        if (event.isCanceled()) return;
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        float incoming = event.getAmount();
        if (!(incoming > 0.0F)) return;
        DamageSource source = event.getSource();
        // Nothing to answer for: the shaping layer skipped this strike for the same reason, and reading its
        // elements here would leave a mark on the target that the number never asked for.
        if (DamageCalculationService.bypasses(source)) return;
        DamageElements.Strike strike = DamageElements.reading(source);
        double reduced = DamageCalculationService.incoming(target, strike.elements(), incoming);
        if (reduced != incoming) event.setAmount((float) reduced);
        // Only a strike that declared what it was made of rubs off. The amounts come from the same reading, and
        // what the target carries decides how much gets through.
        if (strike.origin().attaches())
            ElementReactionService.applyFromStrike(target, strike.attachment(), FormulaContext.of(target),
                    DamageCalculationService.attachmentMultiplier(target));
    }
}
