package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.data.cost.builtin.AuraCost;
import com.iafenvoy.mxt.data.cost.builtin.ItemCost;
import com.iafenvoy.mxt.data.cost.builtin.ResourceCost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Knows which draft charges which entry type, and makes the drafts a payment asks it for. It does nothing with them:
 * loading, testing and writing are {@link CostPayment}'s.
 *
 * <p>The type is the key: entries of one type all load through the one draft of that type, and a type with no draft
 * here - {@code mxt:js}, and anything an addon registers without one - is charged by calling {@link Cost#test} and
 * {@link Cost#commit} on the entry itself, because a script owns state this mod can neither merge nor put back.
 */
public final class CostDraftManager {
    private static final Map<Class<? extends Cost>, CostDraftFactory<?>> FACTORIES = new LinkedHashMap<>();

    static {
        register(ResourceCost.class, ResourceCostDraft::new);
        register(AuraCost.class, AuraCostDraft::new);
        register(ItemCost.class, (context, account) -> new ItemCostDraft(context));
    }

    private CostDraftManager() {
    }

    /**
     * The draft half of an entry type, registered next to its {@code mxt:cost_type} codec.
     */
    public static <T extends Cost> void register(Class<T> type, CostDraftFactory<T> factory) {
        FACTORIES.put(type, factory);
    }

    /**
     * Builds the draft of one entry type for one payment. The account table is handed in rather than made here: it is
     * the payment's resource charge, and an aura entry charged as the resource it is measured in adds to that one.
     */
    @FunctionalInterface
    public interface CostDraftFactory<T extends Cost> {
        CostDraft<T> create(CostContext context, Map<Identifier, Double> account);
    }

    /**
     * One draft of that type, or null when the type has none. A payment keeps the instance it is given: every entry
     * of the type loads through it.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Cost> @Nullable CostDraft<T> create(Class<T> type, CostContext context,
                                                                 Map<Identifier, Double> account) {
        CostDraftFactory<T> factory = (CostDraftFactory<T>) FACTORIES.get(type);
        return factory == null ? null : factory.create(context, account);
    }

    /**
     * The types that have a draft, in registration order: what a payment builds its drafts from.
     */
    public static List<Class<? extends Cost>> types() {
        return List.copyOf(FACTORIES.keySet());
    }
}
