package com.iafenvoy.mxt.screen.information;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment.State;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.attachment.TechniqueAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.data.information.getter.MxtInformationGetter;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService.BreakthroughStatus;
import com.iafenvoy.mxt.runtime.cultivation.LifeSpanService;
import com.iafenvoy.mxt.runtime.item.QualityService;
import com.iafenvoy.saturn.data.information.InformationRow;
import com.iafenvoy.saturn.runtime.resource.ResourceService;
import com.iafenvoy.saturn.util.DefinitionText;
import com.iafenvoy.saturn.util.TooltipText;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.saturn.util.formula.FormulaContexts;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Captures the rows MiXianTu contributes to the shared player information view. The rows are rendered here, on the
 * owning thread and from read-only attachment reads, because the display side only ever holds the encoded snapshot.
 *
 * <p>The row builders also take an already decoded attachment instead of a player: {@link MxtSavedPlayerView} feeds
 * them from a player's saved data. Anything that needs the live body - breakthrough conditions, the current minor
 * stage, a curse's display condition - is skipped when no player is passed rather than guessed from the save.
 */
public final class MxtPlayerView {
    public static final MxtInformationGetter INFORMATION = MxtInformationGetter.INSTANCE;
    private static final int VALUE_COLOR = 0xFFE0E4EC;
    // A held definition that is switched off is still listed, visibly not counting.
    private static final int SWITCHED_OFF_COLOR = 0xFF6E7681;
    private static final int READY_COLOR = 0xFF55FF55;

    private MxtPlayerView() {
    }

    static void realm(@Nullable CultivationAttachment cultivation, @Nullable Player player, Map<String, Row> rows) {
        Set<Holder<Aura>> chains = new LinkedHashSet<>();
        if (cultivation != null) {
            chains.addAll(cultivation.cultivationProgresses().keySet());
            chains.addAll(cultivation.realmStages().keySet());
        }
        if (chains.isEmpty()) {
            rows.put(MxtPlayerInformation.REALM, new Row(List.of(Component.translatable("info.mxt.mortal")), Optional.empty(), InformationRow.Tone.NORMAL));
            return;
        }
        FormulaContext context = player == null ? null : FormulaContexts.forEntity(player);
        List<Component> values = new ArrayList<>();
        for (Holder<Aura> chain : chains) {
            if (!chain.value().showCultivationInfo()) continue;
            Holder<RealmStage> stage = cultivation.realmStage(chain);
            Component name = stage == null ? Component.translatable("info.mxt.mortal") : DefinitionText.nameOrId(stage);
            Component minor = player == null ? null : minorStage(player, chain, cultivation, stage, context);
            if (minor != null) name = name.copy().append(" ").append(minor);
            values.add(DefinitionText.nameOrId(chain.value().resource()).copy().append(": ").append(name));
        }
        if (!values.isEmpty())
            rows.put(MxtPlayerInformation.REALM, new Row(values, Optional.empty(), InformationRow.Tone.NORMAL));
    }

    // A mortal, a stage that declares no minor stages, and a name no pack translated all add nothing to the row.
    // The segment arithmetic goes through the same chain entry point the breakthrough runtime uses, so the row
    // cannot disagree with what the body actually needs.
    @Nullable
    private static Component minorStage(Player player, Holder<Aura> chain, CultivationAttachment cultivation,
                                        @Nullable Holder<RealmStage> stage, FormulaContext context) {
        if (stage == null) return null;
        FormulaContext resourceContext = ResourceService.formulaContext(player, chain.value().resource(), context);
        double index = CultivationService.minorStage(chain, cultivation, resourceContext);
        return Double.isNaN(index) ? null : DefinitionText.resolved(DefinitionText.minorStage(stage, (int) index)).orElse(null);
    }

    static void progress(@Nullable CultivationAttachment cultivation, @Nullable Player player, Map<String, Row> rows) {
        if (cultivation == null || cultivation.cultivationProgresses().isEmpty()) {
            rows.put(MxtPlayerInformation.PROGRESS, new Row(List.of(Component.literal("-")), Optional.empty(), InformationRow.Tone.NORMAL));
            return;
        }
        FormulaContext context = player == null ? null : FormulaContexts.forEntity(player);
        List<Component> values = new ArrayList<>();
        List<Component> notes = new ArrayList<>();
        boolean ready = false;
        for (Object2DoubleMap.Entry<Holder<Aura>> entry : cultivation.cultivationProgresses().object2DoubleEntrySet()) {
            Holder<Aura> chain = entry.getKey();
            if (!chain.value().showCultivationInfo()) continue;
            // Breakthrough readiness is a live fact: without the body the number is shown as it was saved.
            boolean met = false;
            if (player != null) {
                BreakthroughStatus status = CultivationService.breakthroughStatusForChain(player, chain, context);
                met = status.conditionsMet();
                ready |= met;
                if (status.reached())
                    notes.add(Component.translatable(met
                            ? "info.mxt.breakthrough.ready" : "info.mxt.breakthrough.conditions_unmet"));
            }
            values.add(DefinitionText.nameOrId(chain.value().resource()).copy()
                    .append(": " + String.format("%.2f", entry.getDoubleValue()))
                    .withColor(met ? READY_COLOR : VALUE_COLOR));
        }
        if (!values.isEmpty())
            rows.put(MxtPlayerInformation.PROGRESS, new Row(values, joined(notes),
                    ready ? InformationRow.Tone.POSITIVE : InformationRow.Tone.NORMAL));
    }

    static void lifespan(long remaining, long total, Map<String, Row> rows) {
        MxtServerConfig.Lifespan settings = MxtServerConfig.INSTANCE.lifespan;
        if (!settings.enabled.getValue()) return;
        rows.put(MxtPlayerInformation.LIFESPAN, new Row(List.of(LifeSpanService.display(remaining, total,
                settings.ticksPerYear.getValue())), Optional.empty(), InformationRow.Tone.NORMAL));
    }

    // A root is listed with the element it is bound to, because its own name does not say what it cultivates.
    static void spiritRoots(@Nullable SpiritIdentityAttachment identity, Map<String, Row> rows) {
        if (identity == null || identity.spiritRoots().isEmpty()) return;
        MutableComponent line = Component.empty();
        List<Component> notes = new ArrayList<>();
        List<Holder<SpiritRoot>> roots = identity.spiritRoots();
        for (int index = 0; index < roots.size(); index++) {
            Holder<SpiritRoot> root = roots.get(index);
            if (index > 0) line.append(", ");
            boolean active = identity.isSpiritRootEnabled(root);
            line.append(heldName(DefinitionText.nameOrId(root), active));
            List<SpiritRoot.ElementWeight> elements = root.value().elements();
            if (!elements.isEmpty()) {
                MutableComponent names = Component.empty();
                for (int elementIndex = 0; elementIndex < elements.size(); elementIndex++) {
                    SpiritRoot.ElementWeight element = elements.get(elementIndex);
                    if (elementIndex > 0) names.append("/");
                    names.append(Component.empty().append(DefinitionText.nameOrId(element.element()))
                            .append(element.weight() == 1.0D ? Component.empty()
                                    : Component.literal(" " + TooltipText.number(element.weight())))
                            .withColor(active ? element.element().value().color() : SWITCHED_OFF_COLOR));
                }
                line.append(Component.literal("(").append(names).append(")"));
            }
            notes.add(identityNote(root, root.value().quality(), active));
        }
        rows.put(MxtPlayerInformation.SPIRIT_ROOTS, new Row(List.of(line), joined(notes), InformationRow.Tone.NORMAL));
    }

    // Duplicates collapse into one entry carrying a ×N count: allow_stacking makes them legal, and a repeated name
    // would read like a rendering mistake.
    static void physiques(@Nullable SpiritIdentityAttachment identity, Map<String, Row> rows) {
        if (identity == null || identity.physiques().isEmpty()) return;
        List<Holder<Physique>> held = identity.physiques();
        List<Holder<Physique>> distinct = new ArrayList<>(new LinkedHashSet<>(held));
        MutableComponent line = Component.empty();
        List<Component> notes = new ArrayList<>();
        for (int index = 0; index < distinct.size(); index++) {
            Holder<Physique> physique = distinct.get(index);
            if (index > 0) line.append(", ");
            boolean active = identity.isPhysiqueEnabled(physique);
            line.append(heldName(DefinitionText.nameOrId(physique), active));
            int stacks = Collections.frequency(held, physique);
            if (stacks > 1) line.append(" ×" + stacks);
            notes.add(identityNote(physique, physique.value().quality(), active));
        }
        rows.put(MxtPlayerInformation.PHYSIQUES, new Row(List.of(line), joined(notes), InformationRow.Tone.NORMAL));
    }

    static void techniques(@Nullable TechniqueAttachment techniques, Map<String, Row> rows) {
        if (techniques == null || techniques.learnedTechniques().isEmpty()) return;
        MutableComponent names = Component.empty();
        for (int index = 0; index < techniques.learnedTechniques().size(); index++) {
            if (index > 0) names.append(", ");
            names.append(DefinitionText.nameOrId(techniques.learnedTechniques().get(index)));
        }
        rows.put(MxtPlayerInformation.TECHNIQUES, new Row(List.of(names), Optional.empty(), InformationRow.Tone.NORMAL));
    }

    // A curse whose display_condition fails leaves no row behind at all, not an empty one: that is the point of
    // the field. Without a live body the condition cannot be evaluated, so every held instance is listed.
    static void curses(@Nullable CurseHolderAttachment holder, @Nullable Player player, @Nullable Long gameTime,
                       Map<String, Row> rows) {
        if (holder == null) return;
        FormulaContext context = player == null ? null : FormulaContext.of(player);
        List<Component> values = new ArrayList<>();
        List<Component> notes = new ArrayList<>();
        for (Map.Entry<Holder<Curse>, State> entry : holder.instances().entrySet()) {
            if (player != null && !entry.getKey().value().displayCondition().test(player, context)) continue;
            Component name = DefinitionText.nameOrId(entry.getKey());
            if (entry.getValue().stacks() > 1) name = name.copy().append(" ×" + entry.getValue().stacks());
            values.add(name);
            notes.add(curseTooltip(holder.sources().of(entry.getKey()), entry.getValue(), gameTime));
        }
        if (!values.isEmpty())
            rows.put(MxtPlayerInformation.CURSES, new Row(values, joined(notes), InformationRow.Tone.WARNING));
    }

    private static Component curseTooltip(Set<Identifier> sources, State state, @Nullable Long gameTime) {
        String from = sources.stream().map(Identifier::toString).sorted().reduce((a, b) -> a + ", " + b).orElse("-");
        Component line = Component.translatable("info.mxt.curse.source", from);
        if (state.expiresAt() < 0L)
            return line.copy().append("\n").append(Component.translatable("info.mxt.curse.permanent"));
        // The remaining time is relative to the reading, which a saved view does not have unless the caller passes it.
        return gameTime == null ? line
                : line.copy().append("\n").append(Component.translatable("info.mxt.curse.remaining",
                Math.max(0L, state.expiresAt() - gameTime)));
    }

    // The tier is shown only here, in the row's tooltip: the panel is the one consumer every definition has.
    private static Component identityNote(Holder<?> holder, Optional<Holder<ItemQuality>> quality, boolean active) {
        MutableComponent note = DefinitionText.nameOrId(holder);
        quality.ifPresent(tier -> note.append(" · ").append(QualityService.displayName(tier)));
        if (!active) note.append(" · ").append(Component.translatable("info.mxt.switched_off"));
        return note.withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent heldName(MutableComponent name, boolean active) {
        return active ? name : name.withStyle(ChatFormatting.DARK_GRAY);
    }

    private static Optional<Component> joined(List<Component> notes) {
        if (notes.isEmpty()) return Optional.empty();
        MutableComponent result = Component.empty();
        for (int index = 0; index < notes.size(); index++) {
            if (index > 0) result.append("\n");
            result.append(notes.get(index));
        }
        return Optional.of(result);
    }

    /**
     * One logical row's encoded content: the value lines, the tooltip shared by them, and the display tone.
     */
    public record Row(List<Component> values, Optional<Component> tooltip, InformationRow.Tone tone) {
        public static final Codec<Row> CODEC = RecordCodecBuilder.create(i -> i.group(
                ComponentSerialization.CODEC.listOf().fieldOf("values").forGetter(Row::values),
                ComponentSerialization.CODEC.optionalFieldOf("tooltip").forGetter(Row::tooltip),
                InformationRow.Tone.CODEC.optionalFieldOf("tone", InformationRow.Tone.NORMAL).forGetter(Row::tone)
        ).apply(i, Row::new));

        public Row {
            values = List.copyOf(values);
            if (values.isEmpty()) throw new IllegalArgumentException("An information row needs at least one value");
        }
    }

    public static Map<String, Row> capture(Player player) {
        Map<String, Row> rows = new LinkedHashMap<>();
        CultivationAttachment cultivation = player.getExistingData(MxtAttachments.CULTIVATION).orElse(null);
        realm(cultivation, player, rows);
        lifespan(LifeSpanService.remaining(player), LifeSpanService.total(player), rows);
        progress(cultivation, player, rows);
        SpiritIdentityAttachment identity = player.getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
        spiritRoots(identity, rows);
        physiques(identity, rows);
        techniques(player.getExistingData(MxtAttachments.TECHNIQUE).orElse(null), rows);
        curses(player.getExistingData(MxtAttachments.CURSE_HOLDER).orElse(null), player, player.level().getGameTime(), rows);
        return rows;
    }
}
