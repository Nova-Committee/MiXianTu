package com.iafenvoy.mxt.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Where one wheel page's contents come from, and therefore which page it is: the configured page is the player's own
 * saved cells, every other page is derived from what the player carries right now.
 *
 * <p>A content mod adds a page by implementing this and registering it (from its mod setup) with
 * {@code WheelSourceTypes#register}; the page then takes part in the wheel's numbering and page turning like a
 * built-in one. The id is what the client and the server name the page by, so the two sides only have to agree on the
 * registration, never on an order. The first registration of an id wins.
 *
 * <p>Two sides of the same question: {@link #equipment} is what an item-side ability is read from, and
 * {@link #offers} is whether an entry is reachable through this page right now - the check every trigger passes
 * before anything is activated.
 */
public interface WheelSource {
    Identifier id();

    /**
     * The page's name on the wheel. A built-in page returns its own translation key; a content mod may return
     * whatever component it likes.
     */
    Component displayName();

    /**
     * Whether this page is the player's saved layout rather than a reading of what they carry. A configured page is
     * always shown, even while it holds nothing, because it is the page the player arranges.
     */
    boolean configured();

    /**
     * The grant sources this page is made of, or an empty list for a page that is not read from the grant ledger.
     * These are the ids an equipped item recorded its grants under, which is what makes the page follow the item.
     */
    List<Identifier> grantSources(LivingEntity entity);

    /**
     * The stacks this page reads carriers from, in the order a press should prefer them.
     */
    List<ItemStack> equipment(LivingEntity entity);

    /**
     * Whether an entry of that kind, by that id, is reachable through this page right now. Answering yes does not
     * authorise anything: the pipeline behind the entry re-checks grant, cost and cooldown on its own.
     */
    boolean offers(LivingEntity entity, WheelEntryKind kind, Identifier id);
}
