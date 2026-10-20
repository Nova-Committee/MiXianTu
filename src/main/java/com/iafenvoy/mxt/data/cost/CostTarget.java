package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The physical store a bound fee comes out of, used for duplicate diagnostics and re-entrancy guards.
 * VALUE aura and its resource name one account balance; pools, banks and accounts remain separate targets.
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
     * One demand on one container, identified by the items it accepts. Different matchers remain distinct targets;
     * duplicate diagnostics do not detect arbitrary overlaps between them.
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
     * One script call, kept distinct by its entry number. Duplicate diagnostics compare script id and payer instead;
     * the re-entrancy guard is the payer and the script together, so one payer's call is never mistaken for another's.
     */
    record Script(String id, long entry, @Nullable UUID payer) implements CostTarget {
        @Override
        public String label() {
            return "script " + this.id;
        }

        @Override
        public Object guard() {
            return new Scope(this.payer, this.id);
        }

        /**
         * Who is really running one script: the script's own id and the payer it was bound to.
         */
        record Scope(@Nullable UUID payer, String id) {
        }
    }

    /**
     * A section of one chunk's pooled aura. Two positions inside one section name the same stock for diagnostics
     * and guards; the fees are still simulated and charged separately.
     */
    record Section(ResourceKey<Level> dimension, int chunkX, int chunkZ, int sectionY) {
        public static Section of(ResourceKey<Level> dimension, BlockPos pos) {
            return new Section(dimension, SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ()), SectionPos.blockToSectionCoord(pos.getY()));
        }
    }

    /**
     * How this target is named in a log line. Not for players.
     */
    String label();

    /**
     * What the re-entrancy guard holds while this target is being written. Equal guards are the same store: two fees of
     * one payment that would write one store must not run inside each other. It is the target itself except where the
     * identity is deliberately per-entry and the underlying store is not - a script call, whose entry number keeps two
     * calls apart but must not hide a call from itself.
     */
    default Object guard() {
        return this;
    }
}
