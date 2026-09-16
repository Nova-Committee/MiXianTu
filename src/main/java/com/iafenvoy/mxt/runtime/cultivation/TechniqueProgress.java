package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.data.cultivation.SkillStage;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import net.minecraft.core.Holder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The current level and mastery progress of each learned technique, as a plain display model. Nothing here
 * is client-only: the learned techniques and their mastery values are both synchronized attachments, so a
 * client builds the same rows a server would. The chain order comes from the links rather than from
 * {@code ServerCache}, which is bound to a running server; chains are short and validated, so walking the
 * links per row is cheap and cannot loop.
 */
public final class TechniqueProgress {
    /**
     * Bound on a chain walk, so a chain that somehow escaped validation cannot stall a render.
     */
    private static final int MAX_CHAIN_LENGTH = 512;

    private TechniqueProgress() {
    }

    /**
     * Which numbers the progress display uses: the stored mastery against the next requirement, or only what
     * was gained since the current level.
     */
    public enum Mode {
        /** The stored mastery against the next level's requirement, so the bar spans the whole climb. */
        ABSOLUTE,
        /** Only what was gained since the current level, so every level starts from an empty bar. */
        WITHIN_LEVEL
    }

    /**
     * One learned technique and where its holder stands in the technique's chain.
     *
     * @param stage             the level the holder stands on, or {@code null} when there is no chain
     * @param rank              the zero-based rank of {@code stage} in its chain, or {@code -1}
     * @param total             how many levels the technique's chain has, or {@code 0} without one
     * @param currentRequirement what the level the holder stands on asked for, or {@code 0}
     * @param hasMastery        whether the technique names a resource that measures mastery
     * @param required          what the next level asks for, or {@code NaN} at the top of the chain
     */
    public record Entry(Holder<CultivationTechnique> technique, @Nullable Holder<SkillStage> stage, int rank,
                        int total, double currentRequirement, boolean hasMastery, double mastery, double required) {
        /**
         * Whether the technique has a level to display at all.
         */
        public boolean hasStage() {
            return this.stage != null && this.rank >= 0;
        }

        /**
         * Whether the holder can still climb: it has a chain, a measured value, and a level above it.
         */
        public boolean hasNextLevel() {
            return this.hasStage() && this.hasMastery && Double.isFinite(this.required);
        }
    }

    /**
     * How full a progress bar is, together with the numbers it shows.
     *
     * @param done     the value the bar counts from zero
     * @param span     the value the bar counts up to
     * @param fraction {@code done / span} clamped to {@code [0, 1]}
     */
    public record Progress(double done, double span, double fraction) {
    }

    /**
     * One row per learned technique, in the order the holder learned them.
     */
    public static List<Entry> rows(SpiritIdentityAttachment spirit, ResourceHolderAttachment resources, FormulaContext context) {
        List<Entry> rows = new ArrayList<>(spirit.learnedTechniques().size());
        for (Holder<CultivationTechnique> technique : spirit.learnedTechniques())
            rows.add(row(technique, spirit, resources, context));
        return rows;
    }

    private static Entry row(Holder<CultivationTechnique> technique, SpiritIdentityAttachment spirit,
                             ResourceHolderAttachment resources, FormulaContext context) {
        CultivationTechnique definition = technique.value();
        Holder<SkillStage> stage = SkillStageService.currentStage(spirit, technique).orElse(null);
        // A level of another chain (a data pack changed the technique's entry level) is not part of
        // this technique's climb, so it is reported as no level rather than as rank -1 of this one.
        int rank = rankOf(definition, stage);
        if (rank < 0) stage = null;
        Holder<Resource> mastery = definition.masteryResource().orElse(null);
        Holder<SkillStage> next = stage == null ? null : SkillStageService.nextStage(definition, stage).orElse(null);
        return new Entry(technique, stage, rank, chainLength(definition),
                stage == null ? 0.0D : evaluate(stage.value().mastery(), context),
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

    /**
     * How many levels the technique's chain has. The chain is validated when the server cache is
     * built, so this only walks the links.
     */
    private static int chainLength(CultivationTechnique definition) {
        Holder<SkillStage> current = definition.defaultStage().orElse(null);
        int count = 0;
        while (current != null && count < MAX_CHAIN_LENGTH) {
            count++;
            current = SkillStageService.nextStage(definition, current).orElse(null);
        }
        return count;
    }

    /**
     * The zero-based rank of a level in this technique's chain, or {@code -1} for another chain's level.
     */
    private static int rankOf(CultivationTechnique definition, @Nullable Holder<SkillStage> stage) {
        if (stage == null) return -1;
        Holder<SkillStage> current = definition.defaultStage().orElse(null);
        for (int rank = 0; current != null && rank < MAX_CHAIN_LENGTH; rank++) {
            if (current.equals(stage)) return rank;
            current = SkillStageService.nextStage(definition, current).orElse(null);
        }
        return -1;
    }
}
