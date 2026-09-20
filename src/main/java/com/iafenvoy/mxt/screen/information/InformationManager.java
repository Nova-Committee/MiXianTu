package com.iafenvoy.mxt.screen.information;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment.State;
import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService.BreakthroughStatus;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.screen.information.InformationCollector.InformationEntry;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap.Entry;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

import static com.iafenvoy.mxt.screen.information.InformationHelper.lineWithDefinitions;

public final class InformationManager {
    private static final int CURSE_COLOR = 0xFFD98A8A;
    private static final Map<Identifier, RegisteredInformation> INFORMATION = new LinkedHashMap<>();

    static {
        register("health", Side.BASIC, c -> c.add("info.mxt.health", String.format("%.1f / %.1f", c.getPlayer().getHealth(), c.getPlayer().getMaxHealth())));
        register("food", Side.BASIC, c -> c.add("info.mxt.food", Integer.toString(c.getPlayer().getFoodData().getFoodLevel())));
        register("experience", Side.BASIC, c -> c.add("info.mxt.experience", Integer.toString(c.getPlayer().experienceLevel)));
        register("dimension", Side.BASIC, c -> c.add("info.mxt.dimension", c.getPlayer().level().dimension().identifier().getPath()));

        register("realm", Side.CULTIVATION, InformationManager::realmLines);
        register("cultivation_progress", Side.CULTIVATION, InformationManager::progressLines);
        register("cultivating", Side.CULTIVATION, c -> c.add("info.mxt.cultivating", Component.translatable(c.getData(MxtAttachments.CULTIVATION).cultivating() ? "info.mxt.yes" : "info.mxt.no")));
        register("spirit_roots", Side.CULTIVATION, InformationManager::spiritRootLines);
        register("physiques", Side.CULTIVATION, c -> lineWithDefinitions(c, "info.mxt.physiques", c.getData(MxtAttachments.SPIRIT_IDENTITY).physiques(), "physique"));
        register("techniques", Side.CULTIVATION, c -> lineWithDefinitions(c, "info.mxt.techniques", c.getData(MxtAttachments.SPIRIT_IDENTITY).learnedTechniques(), "technique"));
        register("curses", Side.CULTIVATION, InformationManager::curseLines);
    }

    public static void register(@NotNull String id, @NotNull Side side, Consumer<InformationCollector> collector) {
        register(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, id), side, collector);
    }

    public static void register(@NotNull Identifier id, @NotNull Side side, Consumer<InformationCollector> collector) {
        INFORMATION.put(id, new RegisteredInformation(side, collector));
    }

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

    private static void progressLines(InformationCollector collector) {
        CultivationAttachment cultivation = collector.getData(MxtAttachments.CULTIVATION);
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

    /**
     * Lists the held spirit roots, each with the element it is bound to. The root's own name does not say what
     * it cultivates, and the element is the one thing a player reads a root for - so it is shown here, in the
     * element's own colour, rather than left to be looked up elsewhere. A root whose element a pack disabled
     * still appears, because the player holds it; it simply has no element to show.
     */
    private static void spiritRootLines(InformationCollector collector) {
        List<Holder<SpiritRoot>> roots = collector.getData(MxtAttachments.SPIRIT_IDENTITY).spiritRoots();
        if (roots.isEmpty()) return;
        MutableComponent line = Component.empty();
        for (int index = 0; index < roots.size(); index++) {
            Holder<SpiritRoot> root = roots.get(index);
            if (index > 0) line.append(", ");
            line.append(DefinitionText.name(root, "spirit_root"));
            Holder<Element> element = root.value().element();
            if (!Elements.enabled(element)) continue;
            line.append(Component.literal("(").append(DefinitionText.name(element, "element")).append(")")
                    .withColor(element.value().color()));
        }
        collector.add("info.mxt.spirit_roots", line);
    }

    /**
     * Displays every chain tracked by the player, using Mortal when no realm is assigned.
     */
    private static void realmLines(InformationCollector collector) {
        CultivationAttachment cultivation = collector.getData(MxtAttachments.CULTIVATION);
        Set<Holder<Aura>> chains = new LinkedHashSet<>(cultivation.cultivationProgresses().keySet());
        chains.addAll(cultivation.realmStages().keySet());
        if (chains.isEmpty()) {
            collector.add("info.mxt.realm", Component.translatable("info.mxt.mortal"));
            return;
        }
        boolean first = true;
        for (Holder<Aura> chain : chains) {
            if (!chain.value().showCultivationInfo()) continue;
            Holder<?> realm = cultivation.realmStage(chain);
            Component realmName = realm == null ? Component.translatable("info.mxt.mortal") : DefinitionText.name(realm, "realm_stage");
            collector.add(first ? Component.translatable("info.mxt.realm") : null,
                    DefinitionText.name(chain.value().resource(), "resource").copy().append(": ").append(realmName));
            first = false;
        }
    }

    /**
     * Lists the held curses whose own {@code display_condition} passes. A curse that keeps itself out of sight
     * leaves no row behind at all - not an empty one - which is the whole point of the field.
     */
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

    /**
     * Which sources keep the curse alive, and how long it has left, so a row can be read without opening anything.
     */
    private static Component curseTooltip(Set<Identifier> sources, State state, long gameTime) {
        String from = sources.stream().map(Identifier::toString).sorted().reduce((a, b) -> a + ", " + b).orElse("-");
        Component line = Component.translatable("info.mxt.curse.source", from);
        return state.expiresAt() < 0L
                ? line.copy().append("\n").append(Component.translatable("info.mxt.curse.permanent"))
                : line.copy().append("\n").append(Component.translatable("info.mxt.curse.remaining", Math.max(0L, state.expiresAt() - gameTime)));
    }

    private record RegisteredInformation(Side side, Consumer<InformationCollector> collector) {
    }}
