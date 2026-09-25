package com.iafenvoy.mxt.screen.information;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment.State;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService.BreakthroughStatus;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.runtime.cultivation.LifeSpanService;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.screen.information.InformationCollector.InformationEntry;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.TooltipText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

import static com.iafenvoy.mxt.screen.information.InformationHelper.lineWithDefinitions;

public final class InformationManager {
    private static final int CURSE_COLOR = 0xFFD98A8A;
    private static final int VALUE_COLOR = 0xFFE0E4EC;
    // Drawn for a held definition that is switched off: still listed, visibly not counting.
    private static final int SWITCHED_OFF_COLOR = 0xFF6E7681;
    private static final Map<Identifier, RegisteredInformation> INFORMATION = new LinkedHashMap<>();

    static {
        register("health", Side.BASIC, c -> c.add("info.mxt.health", String.format("%.1f / %.1f", c.getPlayer().getHealth(), c.getPlayer().getMaxHealth())));
        register("food", Side.BASIC, c -> c.add("info.mxt.food", Integer.toString(c.getPlayer().getFoodData().getFoodLevel())));
        register("experience", Side.BASIC, c -> c.add("info.mxt.experience", Integer.toString(c.getPlayer().experienceLevel)));
        register("dimension", Side.BASIC, c -> c.add("info.mxt.dimension", c.getPlayer().level().dimension().identifier().getPath()));

        register("realm", Side.CULTIVATION, InformationManager::realmLines);
        register("lifespan", Side.CULTIVATION, InformationManager::lifespanLine);
        register("cultivation_progress", Side.CULTIVATION, InformationManager::progressLines);
        register("cultivating", Side.CULTIVATION, c -> c.add("info.mxt.cultivating", Component.translatable(c.getData(MxtAttachments.CULTIVATION).cultivating() ? "info.mxt.yes" : "info.mxt.no")));
        register("spirit_roots", Side.CULTIVATION, InformationManager::spiritRootLines);
        register("physiques", Side.CULTIVATION, InformationManager::physiqueLines);
        register("techniques", Side.CULTIVATION, c -> lineWithDefinitions(c, "info.mxt.techniques", c.getData(MxtAttachments.SPIRIT_IDENTITY).learnedTechniques(), "technique"));
        register("curses", Side.CULTIVATION, InformationManager::curseLines);
    }

    public static void register(@NotNull String id, @NotNull Side side, Consumer<InformationCollector> collector) {
        register(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, id), side, collector);
    }

    public static void register(@NotNull Identifier id, @NotNull Side side, Consumer<InformationCollector> collector) {
        INFORMATION.put(id, new RegisteredInformation(side, collector));
    }

    // Reserved extension point: the panel describes the local player only. Showing another entity means widening
    // the collector's subject to LivingEntity (guarding the four player-only rows) plus a target for the screen.
    public static List<InformationEntry> collectEntries(Player player, Side side) {
        if (player == null) return List.of();
        InformationCollector collector = new InformationCollector(player);
        for (RegisteredInformation information : INFORMATION.values()) {
            if (information.side() != side) continue;
            try {
                information.collector().accept(collector);
            } catch (RuntimeException exception) {
                MiXianTu.LOGGER.warn("Information collector failed for {}", side, exception);
            }
        }
        return collector.getEntries();
    }

    public enum Side {
        BASIC, CULTIVATION
    }

    // Not drawn while the system is off: a row would describe a rule this server does not run. A spent life is
    // still drawn, because "0.0 / 120.0" is exactly what a player waiting to be saved needs to see.
    private static void lifespanLine(InformationCollector collector) {
        MxtServerConfig.Lifespan settings = MxtServerConfig.INSTANCE.lifespan;
        if (!settings.enabled.getValue()) return;
        Player player = collector.getPlayer();
        collector.add("info.mxt.lifespan", LifeSpanService.display(LifeSpanService.remaining(player),
                LifeSpanService.total(player), settings.ticksPerYear.getValue()));
    }

    private static void progressLines(InformationCollector collector) {        CultivationAttachment cultivation = collector.getData(MxtAttachments.CULTIVATION);
        if (cultivation.cultivationProgresses().isEmpty()) {
            collector.add("info.mxt.cultivation_progress", "-");
            return;
        }
        boolean first = true;
        FormulaContext context = FormulaContexts.forEntity(collector.getPlayer());
        for (Entry<Holder<Aura>> entry : cultivation.cultivationProgresses().object2DoubleEntrySet()) {
            // The state is keyed by the chain itself, so nothing has to be looked up again here.
            if (!entry.getKey().value().showCultivationInfo()) continue;
            BreakthroughStatus status = CultivationService.breakthroughStatusForChain(collector.getPlayer(), entry.getKey(), context);
            Component tooltip = status.reached()
                    ? Component.translatable(status.conditionsMet() ? "info.mxt.breakthrough.ready" : "info.mxt.breakthrough.conditions_unmet")
                    : null;
            int color = status.conditionsMet() ? 0xFF55FF55 : 0xFFE0E4EC;
            collector.add(first ? Component.translatable("info.mxt.cultivation_progress") : null,
                    Component.literal(DefinitionText.name(entry.getKey().value().resource(), "resource").getString() + ": " + String.format("%.2f", entry.getDoubleValue())),
                    color, tooltip);
            first = false;
        }
    }

    // A root is listed with the element it is bound to, because its own name does not say what it cultivates.
    // A root whose element a pack disabled still appears, because the player holds it; it simply shows no element.
    private static void spiritRootLines(InformationCollector collector) {
        SpiritIdentityAttachment identity = collector.getData(MxtAttachments.SPIRIT_IDENTITY);
        List<Holder<SpiritRoot>> roots = identity.spiritRoots();
        if (roots.isEmpty()) return;
        MutableComponent line = Component.empty();
        List<Component> notes = new ArrayList<>();
        for (int index = 0; index < roots.size(); index++) {
            Holder<SpiritRoot> root = roots.get(index);
            if (index > 0) line.append(", ");
            boolean active = identity.isSpiritRootEnabled(root);
            line.append(heldName(DefinitionText.name(root, "spirit_root"), active));
            List<SpiritRoot.ElementWeight> elements = root.value().elements().stream()
                    .filter(entry -> Elements.enabled(entry.element())).toList();
            if (!elements.isEmpty()) {
                MutableComponent names = Component.empty();
                for (int elementIndex = 0; elementIndex < elements.size(); elementIndex++) {
                    SpiritRoot.ElementWeight entry = elements.get(elementIndex);
                    if (elementIndex > 0) names.append("/");
                    names.append(Component.empty().append(DefinitionText.name(entry.element(), "element"))
                            .append(entry.weight() == 1.0D ? Component.empty()
                                    : Component.literal(" " + TooltipText.number(entry.weight())))
                            .withColor(active ? entry.element().value().color() : SWITCHED_OFF_COLOR));
                }
                line.append(Component.literal("(").append(names).append(")"));
            }
            notes.add(identityNote(root, "spirit_root", root.value().rarity(), active));
        }
        collector.add(Component.translatable("info.mxt.spirit_roots"), line, VALUE_COLOR, joined(notes));
    }

    // Duplicates are collapsed into one entry carrying a ×N count: allow_stacking makes them legal, and a
    // repeated name would read like a rendering mistake.
    private static void physiqueLines(InformationCollector collector) {
        SpiritIdentityAttachment identity = collector.getData(MxtAttachments.SPIRIT_IDENTITY);
        List<Holder<Physique>> held = identity.physiques();
        if (held.isEmpty()) return;
        List<Holder<Physique>> distinct = new ArrayList<>(new LinkedHashSet<>(held));
        MutableComponent line = Component.empty();
        List<Component> notes = new ArrayList<>();
        for (int index = 0; index < distinct.size(); index++) {
            Holder<Physique> physique = distinct.get(index);
            if (index > 0) line.append(", ");
            boolean active = identity.isPhysiqueEnabled(physique);
            line.append(heldName(DefinitionText.name(physique, "physique"), active));
            int stacks = Collections.frequency(held, physique);
            if (stacks > 1) line.append(" ×" + stacks);
            notes.add(identityNote(physique, "physique", physique.value().rarity(), active));
        }
        collector.add(Component.translatable("info.mxt.physiques"), line, VALUE_COLOR, joined(notes));
    }

    // Rarity is shown only here, in the row's tooltip: the panel is the one consumer every definition has.
    private static Component identityNote(Holder<?> holder, String category, String rarity, boolean active) {
        MutableComponent note = DefinitionText.name(holder, category).append(" · ").append(DefinitionText.rarity(rarity));
        if (!active) note.append(" · ").append(Component.translatable("info.mxt.switched_off"));
        return note.withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent heldName(MutableComponent name, boolean active) {
        return active ? name : name.withStyle(ChatFormatting.DARK_GRAY);
    }

    private static Component joined(List<Component> notes) {
        MutableComponent result = Component.empty();
        for (int index = 0; index < notes.size(); index++) {
            if (index > 0) result.append("\n");
            result.append(notes.get(index));
        }
        return result;
    }

    private static void realmLines(InformationCollector collector) {
        CultivationAttachment cultivation = collector.getData(MxtAttachments.CULTIVATION);
        Set<Holder<Aura>> chains = new LinkedHashSet<>(cultivation.cultivationProgresses().keySet());
        chains.addAll(cultivation.realmStages().keySet());
        if (chains.isEmpty()) {
            collector.add("info.mxt.realm", Component.translatable("info.mxt.mortal"));
            return;
        }
        FormulaContext context = FormulaContexts.forEntity(collector.getPlayer());
        boolean first = true;
        for (Holder<Aura> chain : chains) {
            if (!chain.value().showCultivationInfo()) continue;
            Holder<RealmStage> realm = cultivation.realmStage(chain);
            Component realmName = realm == null ? Component.translatable("info.mxt.mortal") : DefinitionText.name(realm, "realm_stage");
            Component minorStage = minorStageText(collector.getPlayer(), chain, realm, cultivation.cultivationProgress(chain), context);
            if (minorStage != null) realmName = realmName.copy().append(" ").append(minorStage);
            collector.add(first ? Component.translatable("info.mxt.realm") : null,
                    DefinitionText.name(chain.value().resource(), "resource").copy().append(": ").append(realmName));
            first = false;
        }
    }

    // A mortal, and a stage that declares no minor stages, add nothing to the row. The segment total has to be
    // read in the same resource context the breakthrough runtime uses, or a formula would be evaluated twice
    // with different variables.
    private static Component minorStageText(Player player, Holder<Aura> chain, @Nullable Holder<RealmStage> realm,
                                            double progress, FormulaContext context) {
        if (realm == null) return null;
        FormulaContext resourceContext = ResourceService.formulaContext(player, chain.value().resource(), context);
        double index = CultivationService.minorStage(realm.value(), progress, resourceContext);
        return Double.isNaN(index) ? null : realm.value().minorStages().get((int) index);
    }

    // A curse whose display_condition fails leaves no row behind at all, not an empty one: that is the point
    // of the field.
    private static void curseLines(InformationCollector collector) {
        Player player = collector.getPlayer();
        FormulaContext context = FormulaContext.of(player);
        long gameTime = player.level().getGameTime();
        boolean first = true;
        for (Map.Entry<Holder<Curse>, State> entry : player.getData(MxtAttachments.CURSE_HOLDER).instances().entrySet()) {
            if (!entry.getKey().value().displayCondition().test(player, context)) continue;
            Component name = DefinitionText.name(entry.getKey(), "curse");
            if (entry.getValue().stacks() > 1) name = name.copy().append(" ×" + entry.getValue().stacks());
            collector.add(first ? Component.translatable("info.mxt.curses") : null, name, CURSE_COLOR,
                    curseTooltip(player.getData(MxtAttachments.CURSE_HOLDER).sources().of(entry.getKey()), entry.getValue(), gameTime));
            first = false;
        }
    }

    private static Component curseTooltip(Set<Identifier> sources, State state, long gameTime) {
        String from = sources.stream().map(Identifier::toString).sorted().reduce((a, b) -> a + ", " + b).orElse("-");
        Component line = Component.translatable("info.mxt.curse.source", from);
        return state.expiresAt() < 0L
                ? line.copy().append("\n").append(Component.translatable("info.mxt.curse.permanent"))
                : line.copy().append("\n").append(Component.translatable("info.mxt.curse.remaining", Math.max(0L, state.expiresAt() - gameTime)));
    }

    private record RegisteredInformation(Side side, Consumer<InformationCollector> collector) {
    }
}
