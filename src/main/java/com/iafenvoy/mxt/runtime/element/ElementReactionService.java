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
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * pipeline keeps looking while another reaction still qualifies. The chain is capped, and an application made
 * from inside a chain joins the one already running rather than opening another, because a reaction whose
 * action applies the same element again - legal, and the point of a lingering burn - would otherwise never
 * end.</p>
 */
@EventBusSubscriber
public final class ElementReactionService {
    /**
     * How many reactions one application may set off. A reaction that applies the element it consumes is legal
     * and useful, and is exactly why this bound exists.
     */
    private static final int MAX_CHAIN = 8;

    /**
     * The bodies one chain is currently resolving reactions for.
     *
     * <p>{@link #MAX_CHAIN} bounds one chain, but it cannot bound a chain that is started from inside one: an
     * action that applies an element again goes back through {@link #apply} and would open a second chain,
     * whose own actions open a third. A reaction that feeds itself - "it keeps burning until something puts it
     * out" is a legal and wanted way to write an element - would then recurse until the thread dies, which is
     * the very case the bound was written for. A body already in this set therefore only takes the amount: the
     * chain that is running sees the new total on its next pass and answers it there, so at most
     * {@code MAX_CHAIN} reactions fire per body per application and the loop terminates.</p>
     *
     * <p>Identity, not equality, because the body is the entity instance the chain was entered with, and the
     * set is per thread because a chain never crosses one.</p>
     */
    private static final ThreadLocal<Set<Entity>> IN_CHAIN =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    /**
     * How many reaction registries the walk order is kept for. A reloaded data pack makes a new registry
     * instance rather than changing the old one, so the cache is bounded the same way the damage-type index is.
     */
    private static final int MAX_CACHED_REGISTRIES = 4;
    private static final Object LOCK = new Object();
    private static volatile Map<Registry<ElementReaction>, List<Reference<ElementReaction>>> orders = Map.of();

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
     *
     * <p>This is the mechanical half of the rule: it attaches whatever it is handed. Which strikes are handed to
     * it is the pipeline's decision - {@code DamageEventBridge} only does so for elements a damage type claimed,
     * so an element read off the attacker's spirit roots reduces the hit without rubbing off.</p>
     */
    public static void applyFromStrike(LivingEntity target, Set<Holder<Element>> strike, FormulaContext context) {
        applyFromStrike(target, strike, context, 1.0D);
    }

    /**
     * {@link #applyFromStrike(LivingEntity, Set, FormulaContext)} with what the target lets through: every
     * element's own {@code damage_attachment} is scaled by {@code multiplier} first, so a carrier whose gear
     * resists elemental buildup simply receives less of it and needs more strikes before a reaction answers.
     *
     * <p>{@code 1.0} is "nothing resists", and a non-finite or negative multiplier contributes nothing rather
     * than poisoning the attachment: the same rule the definitions themselves are validated under.</p>
     */
    public static void applyFromStrike(LivingEntity target, Set<Holder<Element>> strike, FormulaContext context,
                                       double multiplier) {
        Map<Holder<Element>, Double> amounts = new LinkedHashMap<>();
        for (Holder<Element> element : strike) amounts.put(element, element.value().damageAttachment());
        applyFromStrike(target, amounts, context, multiplier);
    }

    /**
     * The same, for a caller that already knows what each element leaves. The pipeline takes this entry point,
     * because a damage type's claim may carry a number of its own: a lava bath and a fireball can be the same
     * element and still build up at different rates.
     *
     * <p>The amounts are read from the strike's own reading rather than from the element definitions, so
     * "how much this kind of hit leaves" is answered once, where the hit was classified.</p>
     */
    public static void applyFromStrike(LivingEntity target, Map<Holder<Element>, Double> amounts,
                                       FormulaContext context, double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier <= 0.0D) return;
        amounts.forEach((element, amount) -> {
            double scaled = amount * multiplier;
            if (scaled > 0.0D) apply(target, element, scaled, context);
        });
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
        return ordered().stream()
                .filter(holder -> satisfied(holder.value(), attachment, entity, context))
                .findFirst().orElse(null);
    }

    /**
     * Every enabled reaction, priority first and registry id where the priorities tie, in the order an
     * application walks them.
     *
     * <p>This is asked once per element per strike and once per link of a chain, and the answer only changes
     * when the registry does: a data pack reload swaps the registry instance (and {@code /reload} does not
     * touch it), so the instance is the cache key. Same shape and same bound as the damage-type index, which is
     * the other hot reverse lookup in this pipeline.</p>
     */
    private static List<Reference<ElementReaction>> ordered() {
        Registry<ElementReaction> registry = MxtDatapackRegistries.registry(MxtResourceKeys.ELEMENT_REACTION);
        List<Reference<ElementReaction>> cached = orders.get(registry);
        if (cached != null) return cached;
        synchronized (LOCK) {
            cached = orders.get(registry);
            if (cached != null) return cached;
            List<Reference<ElementReaction>> built = MxtDatapackRegistries.holders(MxtResourceKeys.ELEMENT_REACTION)
                    .sorted(Comparator.comparingInt((Reference<ElementReaction> holder) -> holder.value().priority()).reversed()
                            .thenComparing(holder -> holder.key().identifier()))
                    .toList();
            Map<Registry<ElementReaction>, List<Reference<ElementReaction>>> updated =
                    orders.size() + 1 > MAX_CACHED_REGISTRIES ? new HashMap<>() : new HashMap<>(orders);
            updated.put(registry, built);
            orders = Map.copyOf(updated);
            return built;
        }
    }

    /**
     * Answers reactions while any still qualifies, and returns how many fired.
     *
     * <p>An application made while this body is already part way through a chain does not start a chain of its
     * own: the amount lands, and the running chain answers it. That is what keeps the bound above the whole of
     * the rule rather than only half of it.</p>
     */
    public static int trigger(Entity entity, FormulaContext context) {
        if (!(entity.level() instanceof ServerLevel)) return 0;
        if (!IN_CHAIN.get().add(entity)) return 0;
        try {
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
        } finally {
            Set<Entity> chain = IN_CHAIN.get();
            chain.remove(entity);
            if (chain.isEmpty()) IN_CHAIN.remove();
        }
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
