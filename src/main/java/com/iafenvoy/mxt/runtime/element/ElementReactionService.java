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

import java.util.*;
import java.util.Map.Entry;

/**
 * The element accumulation pipeline: amount in the attachment, demand in the reaction definition, buildup and
 * decay rates on the element itself. A chain answers while any reaction still qualifies, capped, and an
 * application made from inside a chain joins the running one instead of opening a second.
 */
@EventBusSubscriber
public final class ElementReactionService {
    // How many reactions one application may set off. A reaction that applies the element it consumes is legal
    // and wanted ("it keeps burning until something puts it out"), which is exactly why this bound exists.
    private static final int MAX_CHAIN = 8;

    // The bodies a chain is currently resolving for. MAX_CHAIN cannot bound a chain started from inside one: an
    // action that applies an element again goes back through apply() and would open a second chain, recursing
    // until the thread dies. A body already in here only takes the amount - the running chain sees the new total
    // on its next pass - so at most MAX_CHAIN reactions fire per body per application. Identity, not equality,
    // and per thread because a chain never crosses one.
    private static final ThreadLocal<Set<Entity>> IN_CHAIN =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    // The walk order is cached per reaction registry instance, which a reloaded pack may keep: ServerCache drops
    // it on every datapack load, so an edited priority is never served from the previous pack.
    private static final int MAX_CACHED_REGISTRIES = 4;
    private static final Object LOCK = new Object();
    private static volatile Map<Registry<ElementReaction>, List<Reference<ElementReaction>>> orders = Map.of();

    private ElementReactionService() {
    }

    // A negative amount takes away. Zero, non-finite and a disabled element are silent no-ops: applying an
    // element is not a transaction that can fail.
    public static double apply(Entity entity, Holder<Element> element, double amount, FormulaContext context) {
        if (!(entity.level() instanceof ServerLevel)) return 0.0D;
        if (!Double.isFinite(amount) || amount == 0.0D || !Elements.enabled(element)) return 0.0D;
        ElementAttachment attachment = entity.getData(MxtAttachments.ELEMENT_ATTACHMENT);
        double total = attachment.add(element, amount);
        trigger(entity, context);
        return total;
    }

    // An element that writes nothing builds up nothing, which keeps a pack's elements pure relations until it
    // asks for more. This attaches whatever it is handed; which strikes reach it is the pipeline's decision.
    public static void applyFromStrike(LivingEntity target, Set<Holder<Element>> strike, FormulaContext context) {
        applyFromStrike(target, strike, context, 1.0D);
    }

    // Scaled first: a carrier whose gear resists buildup simply receives less and needs more strikes before a
    // reaction answers. 1.0 is "nothing resists"; a non-finite or negative multiplier contributes nothing.
    public static void applyFromStrike(LivingEntity target, Set<Holder<Element>> strike, FormulaContext context,
                                       double multiplier) {
        Map<Holder<Element>, Double> amounts = new LinkedHashMap<>();
        for (Holder<Element> element : strike) amounts.put(element, element.value().damageAttachment());
        applyFromStrike(target, amounts, context, multiplier);
    }

    // For a caller that already knows what each element leaves: a damage type's claim may carry a number of its
    // own, so a lava bath and a fireball can be the same element and still build up at different rates.
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

    // The highest priority whose demand every listed element meets and whose condition passes, registry id
    // breaking ties.
    @Nullable
    public static Holder<ElementReaction> match(Entity entity, FormulaContext context) {
        ElementAttachment attachment = entity.getExistingData(MxtAttachments.ELEMENT_ATTACHMENT).orElse(null);
        if (attachment == null || attachment.isEmpty()) return null;
        return ordered().stream()
                .filter(holder -> satisfied(holder.value(), attachment, entity, context))
                .findFirst().orElse(null);
    }

    // Asked once per element per strike and once per link of a chain, and the answer only changes when the
    // registry does, so the registry instance is the cache key.
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

    // Called on every datapack load, before anything can rebuild the order.
    public static void invalidate() {
        synchronized (LOCK) {
            orders = Map.of();
        }
    }

    // An application made while this body is already part way through a chain does not start a chain of its own:
    // the amount lands and the running chain answers it, which is what makes MAX_CHAIN bound the whole rule.
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

    // Once per entity per tick on the server. Never answers a reaction: decay can only lower a total, so a
    // demand that was unmet stays unmet.
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
