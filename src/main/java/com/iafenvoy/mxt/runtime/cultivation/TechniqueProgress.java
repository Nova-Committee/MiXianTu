package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.ProgressionAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.runtime.progression.ProgressionService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import net.minecraft.core.Holder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The current level and mastery progress of each learned technique, as a plain display model. Nothing here is
 * client-only: the learned techniques, their progression levels and their mastery values are all synchronised
 * attachments, so a client builds the same rows a server would. Chain order comes from the links rather than from
 * {@link ServerCache}, which is bound to a running server; chains are short and validated, so walking them per row
 * is cheap.
 */
public final class TechniqueProgress {
    /**
     * Bound on a chain walk, so a chain that somehow escaped validation cannot stall a render.
     */
    private static final int MAX_CHAIN_LENGTH = 512;

    private TechniqueProgress() {
    }

    public enum Mode {
        // The stored mastery against the next level's requirement, so the bar spans the whole climb.
        ABSOLUTE,
        // Only what was gained since the current level, so every level starts from an empty bar.
        WITHIN_LEVEL
    }

    // One learned technique and where its holder stands in its chain. The magic values are conventions a caller
    // reads: no chain means a null level, rank -1, total 0 and a currentRequirement of 0, and `required` is NaN
    // whenever nothing sits above - which is how `hasNextLevel` tells "nothing above" from "asks for nothing".
    public record Entry(Holder<Technique> technique, @Nullable Holder<Progression> level, int rank,
                        int total, double currentRequirement, boolean hasMastery, double mastery, double required) {
        public boolean hasLevel() {
            return this.level != null && this.rank >= 0;
        }

        public boolean hasNextLevel() {
            return this.hasLevel() && this.hasMastery && Double.isFinite(this.required);
        }
    }

    // How full a progress bar is; fraction is done / span clamped to [0, 1].
    public record Progress(double done, double span, double fraction) {
    }

    public static List<Entry> rows(SpiritIdentityAttachment spirit, ProgressionAttachment progress,
                                   ResourceHolderAttachment resources, FormulaContext context) {
        List<Entry> rows = new ArrayList<>(spirit.learnedTechniques().size());
        for (Holder<Technique> technique : spirit.learnedTechniques())
            rows.add(row(technique, progress, resources, context));
        return rows;
    }

    private static Entry row(Holder<Technique> technique, ProgressionAttachment progress,
                             ResourceHolderAttachment resources, FormulaContext context) {
        Technique definition = technique.value();
        Holder<Progression> level = ProgressionService.currentLevel(progress, HolderHelper.id(technique), definition).orElse(null);
        // A level the technique's own climb cannot reach - a pack changed its entry level - is reported as no
        // level rather than as rank -1 of this one.
        int rank = rankOf(definition, level);
        if (rank < 0) level = null;
        Holder<Resource> mastery = definition.masteryResource().orElse(null);
        Holder<Progression> next = level == null ? null : ProgressionService.nextLevel(level).orElse(null);
        return new Entry(technique, level, rank, chainLength(definition),
                level == null ? 0.0D : evaluate(level.value().mastery(), context),
                mastery != null, mastery == null ? 0.0D : resources.get(mastery),
                next == null ? Double.NaN : evaluate(next.value().mastery(), context));
    }

    /**
     * The progress bar for one row under the given mode.
     */
    public static Progress progress(Entry entry, Mode mode) {
        if (!entry.hasMastery() || !Double.isFinite(entry.mastery())) return new Progress(0.0D, 0.0D, 0.0D);
        // Nothing above the current level: the climb is finished, so the bar is full.
        if (!entry.hasNextLevel()) return new Progress(entry.mastery(), entry.mastery(), 1.0D);
        if (mode == Mode.WITHIN_LEVEL && entry.required() > entry.currentRequirement()) {
            double done = Math.max(0.0D, entry.mastery() - entry.currentRequirement());
            return new Progress(done, entry.required() - entry.currentRequirement(),
                    fraction(done, entry.required() - entry.currentRequirement()));
        }
        return new Progress(entry.mastery(), entry.required(), fraction(entry.mastery(), entry.required()));
    }

    private static double fraction(double done, double span) {
        if (!Double.isFinite(done) || !Double.isFinite(span)) return 0.0D;
        // A level that asks for nothing is already satisfied.
        if (span <= 0.0D) return 1.0D;
        return Math.max(0.0D, Math.min(1.0D, done / span));
    }

    private static double evaluate(NumberProvider provider, FormulaContext context) {
        double value = provider.evaluate(context);
        return Double.isFinite(value) ? value : 0.0D;
    }

    // The chain is validated when the server cache is built, so this only walks the links.
    private static int chainLength(Technique definition) {
        Holder<Progression> current = definition.entryLevel().orElse(null);
        int count = 0;
        while (current != null && count < MAX_CHAIN_LENGTH) {
            count++;
            current = ProgressionService.nextLevel(current).orElse(null);
        }
        return count;
    }

    private static int rankOf(Technique definition, @Nullable Holder<Progression> level) {
        if (level == null) return -1;
        Holder<Progression> current = definition.entryLevel().orElse(null);
        for (int rank = 0; current != null && rank < MAX_CHAIN_LENGTH; rank++) {
            if (current.equals(level)) return rank;
            current = ProgressionService.nextLevel(current).orElse(null);
        }
        return -1;
    }
}
