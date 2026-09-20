package com.iafenvoy.mxt.runtime.damage;

import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.runtime.element.ElementReactionService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Set;

/**
 * Where the reduction layer of the damage pipeline lives.
 *
 * <p>{@link LivingIncomingDamageEvent} is fired once per damage sequence, before any mitigation, and is the
 * only place that sees damage the mod did not deal - a mob's swing, a fall, another mod's hit. Running the
 * target's own element relations here is therefore what makes them a property of the target rather than of
 * the mod's own attacks, and it is also why no dealing path applies them again.</p>
 *
 * <p>The event is cancellable and this listener never cancels it: refusing a hit outright belongs to the
 * protection and invulnerability rules that already answer that question, while a relation only changes what
 * the hit is worth.</p>
 *
 * <p>It is also where an elemental strike leaves its element behind on the target: the strike's elements are
 * read once, the reduction is computed from them, and the same set builds up the accumulation a reaction
 * answers. Doing it here rather than in the dealing paths is what makes a lava bath and another mod's fire
 * spell feed the same system as this mod's own fireball.</p>
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
        Set<Holder<Element>> strike = DamageElements.strike(event.getSource());
        double reduced = DamageCalculationService.incoming(target, strike, incoming);
        if (reduced != incoming) event.setAmount((float) reduced);
        ElementReactionService.applyFromStrike(target, strike, FormulaContext.of(target));
    }
}
