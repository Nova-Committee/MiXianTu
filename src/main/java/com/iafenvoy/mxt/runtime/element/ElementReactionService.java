package com.iafenvoy.mxt.runtime.element;

import com.iafenvoy.mxt.attachment.ElementAttachment;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.ElementReaction;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Set;

/**
 * The element accumulation pipeline: what builds up on a body, what wears it off, and which reaction answers.
 *
 * <p>Three things are kept apart on purpose. The <em>amount</em> lives in an attachment and knows nothing about
 * rules ({@link ElementAttachment}). The <em>demand</em> lives in a {@code mxt:element_reaction} definition, so
 * a pack can add a reaction without touching an element, and can write a two-element one by listing two. The
 * <em>buildup</em> comes from the element itself ({@code damage_attachment} for a strike of it,
 * {@code attachment_decay} for what wears off), which is where the numbers belong because they are properties
 * of the element and not of any one reaction.</p>
 *
 * <p>A reaction is answered as soon as its demand is met, which is how a pack writes "ten fire on a body and it
 * ignites" without a second mechanism for thresholds: the reaction fires, takes what it asked for, and the
 * pipeline keeps looking while another reaction still qualifies. The chain is capped, because a reaction whose
 * action applies the same element again would otherwise never end.</p>
 */
@EventBusSubscriber
public final class ElementReactionService {
    /**
     * How many reactions one application may set off. A reaction that applies the element it consumes is legal
     * and useful, and is exactly why this bound exists.
     */
    private static final int MAX_CHAIN = 8;

    private ElementReactionService() {
    }

    /**
     * Adds to one element's buildup on an entity and answers the new total, then answers whatever reaction the
     * new total makes due. A zero or non-finite amount does nothing, a negative one takes away, and both are
     * silent: applying an element is not a transaction that can fail.
     */
    public static double apply(Entity entity, Holder<Element> element, double amount, FormulaContext context) {
        if (!(entity.level() instanceof ServerLevel)) return 0.0D;
        if (!Double.isFinite(amount) || amount == 0.0D || !Elements.enabled(element)) return 0.0D;
        ElementAttachment attachment = entity.getData(MxtAttachments.ELEMENT_ATTACHMENT);
        double total = attachment.add(element, amount);
        trigger(entity, context);
        return total;
    }

    /**
     * What a strike of these elements leaves behind on the target, as each element's own
     * {@code damage_attachment} says. An element that writes nothing builds up nothing, which keeps a pack's
     * elements pure relations until it asks for more.
     */
    public static void applyFromStrike(LivingEntity target, Set<Holder<Element>> strike, FormulaContext context) {
        for (Holder<Element> element : strike) {
            double amount = element.value().damageAttachment();
            if (amount > 0.0D) apply(target, element, amount, context);
        }
    }

    public static double amount(Entity entity, Holder<Element> element) {
        return entity.getExistingData(MxtAttachments.ELEMENT_ATTACHMENT)
                .map(attachment -> attachment.amount(element)).orElse(0.0D);
    }

    /**
     * The reaction that answers this body right now, or null when none does: the highest priority whose demand
     * every listed element meets and whose condition passes, registry id breaking ties.
     */
    @Nullable
    public static Holder<ElementReaction> match(Entity entity, FormulaContext context) {
        ElementAttachment attachment = entity.getExistingData(MxtAttachments.ELEMENT_ATTACHMENT).orElse(null);
        if (attachment == null || attachment.isEmpty()) return null;
        return MxtDatapackRegistries.holders(MxtResourceKeys.ELEMENT_REACTION)
                .sorted(Comparator.comparingInt((Reference<ElementReaction> holder) -> holder.value().priority()).reversed()
                        .thenComparing(holder -> holder.key().identifier()))
                .filter(holder -> satisfied(holder.value(), attachment, entity, context))
                .findFirst().orElse(null);
    }

    /**
     * Answers reactions while any still qualifies, and returns how many fired.
     */
    public static int trigger(Entity entity, FormulaContext context) {
        if (!(entity.level() instanceof ServerLevel)) return 0;
        int fired = 0;
        while (fired < MAX_CHAIN) {
            Holder<ElementReaction> holder = match(entity, context);
            if (holder == null) break;
            ElementReaction reaction = holder.value();
            ElementAttachment attachment = entity.getData(MxtAttachments.ELEMENT_ATTACHMENT);
            for (Entry<Holder<Element>, NumberProvider> entry : reaction.consumption().entrySet()) {
                double taken = entry.getValue().evaluate(context);
                if (Double.isFinite(taken) && taken > 0.0D) attachment.add(entry.getKey(), -taken);
            }
            reaction.action().ifPresent(action -> action.execute(entity, context));
            fired++;
        }
        return fired;
    }

    /**
     * Wears every element's buildup down by its own decay. Run once per entity per tick, on the server, and it
     * never answers a reaction: decay can only lower a total, so a demand that was unmet stays unmet.
     */
    @SubscribeEvent
    public static void onEntityTick(Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        ElementAttachment attachment = entity.getExistingData(MxtAttachments.ELEMENT_ATTACHMENT).orElse(null);
        if (attachment == null || attachment.isEmpty()) return;
        for (Entry<Holder<Element>, Double> entry : attachment.amounts().entrySet()) {
            double decay = entry.getKey().value().attachmentDecay();
            if (decay > 0.0D) attachment.add(entry.getKey(), -decay);
        }
    }

    private static boolean satisfied(ElementReaction reaction, ElementAttachment attachment, Entity entity, FormulaContext context) {
        for (Entry<Holder<Element>, NumberProvider> entry : reaction.amounts().entrySet()) {
            if (!Elements.enabled(entry.getKey())) return false;
            double required = entry.getValue().evaluate(context);
            if (!Double.isFinite(required) || required < 0.0D) return false;
            if (attachment.amount(entry.getKey()) < required) return false;
        }
        return reaction.condition().test(entity, context);
    }
}
