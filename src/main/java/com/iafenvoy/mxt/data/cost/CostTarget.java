package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The one store a bound fee comes out of, as a value: two fees of one payment that name the same target are the same
 * store, so the payment sums them and charges once. The identity is physical, not by cost type - an {@code mxt:aura}
 * charged as the resource it is measured in and the {@code mxt:resource} naming that same value are one target, while
 * a pool, a bank and an account stay apart even when they hold the same aura.
 *
 * <p>Stores that have a name of their own (a section of a chunk, a resource id) are equal by that name; stores handed
 * in by the caller (an attachment, a container, a bank) are equal by identity, because two of them are two stores.
 */
public sealed interface CostTarget {
    /**
     * One resource balance of one account.
     */
    record Account(@Nullable ResourceHolderAttachment account, Holder<Resource> resource) implements CostTarget {
        @Override
        public String label() {
            return HolderHelper.id(this.resource).toString();
        }
    }

    /**
     * One aura of one store: a section of a world pool, or a block entity's own bank.
     */
    record AuraStore(@Nullable Object store, Holder<Aura> aura) implements CostTarget {
        @Override
        public String label() {
            return HolderHelper.id(this.aura).toString();
        }
    }

    /**
     * One demand on one container, identified by the items it accepts: two entries that accept exactly the same items
     * are one demand that can be summed, while two different matchers stay two demands.
     */
    record Items(@Nullable Container container, List<ItemMatcher.Entry> entries) implements CostTarget {
        public Items {
            entries = List.copyOf(entries);
        }

        @Override
        public String label() {
            return "an item demand";
        }
    }

    /**
     * One script call. Never merged with another: what a script takes is its own business, so it is not a share of a
     * number that could be added up. The entry number is what keeps two calls of one script apart.
     */
    record Script(String id, long entry) implements CostTarget {
        @Override
        public String label() {
            return "script " + this.id;
        }
    }

    /**
     * A section of one chunk's pooled aura: the store a world pool pays from.
     */
    record Section(ResourceKey<Level> dimension, BlockPos pos, int sectionY) {
    }

    /**
     * How this target is named in a log line. Not for players.
     */
    String label();
}
