package com.iafenvoy.mxt.runtime.damage;

import com.iafenvoy.mxt.runtime.element.ElementReactionService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

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
 *
 * <p>A damage type the pack listed in {@link DamageCalculationService#NO_BONUS} is not the mod's to answer
 * for: such a strike is left exactly as it arrived - not reduced and not remembered as an element on the
 * target either, so an exempted hit cannot feed a reaction on its way through.</p>
 *
 * <p>Which half of {@link DamageElements} the strike was read from decides whether it is remembered at all: a
 * claimed damage type is, and an element that only answered because nobody claimed the type is not. A body's own
 * element therefore reduces what it deals without ever starting a reaction on whoever it hits - see
 * {@link DamageElements.Origin}.</p>
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
        // Only a strike that declared what it was made of rubs off: an element read off the attacker's roots
        // reduced the hit above and stops there, so a body's own element never starts a reaction on the target.
        // The amounts come from the same reading - a claimed damage type may build up at its own rate - and what
        // the target carries decides how much of it gets through, which is the item-side answer to resisting an
        // elemental reaction.
        if (strike.origin().attaches())
            ElementReactionService.applyFromStrike(target, strike.attachment(), FormulaContext.of(target),
                    DamageCalculationService.attachmentMultiplier(target));
    }
}
