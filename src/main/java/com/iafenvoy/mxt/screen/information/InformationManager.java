package com.iafenvoy.mxt.screen.information;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService.BreakthroughStatus;
import com.iafenvoy.mxt.screen.information.InformationCollector.InformationEntry;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap.Entry;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

import static com.iafenvoy.mxt.screen.information.InformationHelper.*;

public final class InformationManager {
    private static final Map<Identifier, RegisteredInformation> INFORMATION = new LinkedHashMap<>();

    static {
        register("health", Side.BASIC, c -> c.add("info.mxt.health", String.format("%.1f / %.1f", c.getPlayer().getHealth(), c.getPlayer().getMaxHealth())));
        register("food", Side.BASIC, c -> c.add("info.mxt.food", Integer.toString(c.getPlayer().getFoodData().getFoodLevel())));
        register("experience", Side.BASIC, c -> c.add("info.mxt.experience", Integer.toString(c.getPlayer().experienceLevel)));
        register("dimension", Side.BASIC, c -> c.add("info.mxt.dimension", c.getPlayer().level().dimension().identifier().getPath()));

        register("realm", Side.CULTIVATION, InformationManager::realmLines);
        register("cultivation_progress", Side.CULTIVATION, InformationManager::progressLines);
        register("cultivating", Side.CULTIVATION, c -> c.add("info.mxt.cultivating", Component.translatable(c.getData(MxtAttachments.CULTIVATION).cultivating() ? "info.mxt.yes" : "info.mxt.no")));
        register("spirit_roots", Side.CULTIVATION, c -> lineWithDefinitions(c, "info.mxt.spirit_roots", c.getData(MxtAttachments.SPIRIT_IDENTITY).spiritRoots(), "spirit_root"));
        register("physiques", Side.CULTIVATION, c -> lineWithDefinitions(c, "info.mxt.physiques", c.getData(MxtAttachments.SPIRIT_IDENTITY).physiques(), "physique"));
        register("techniques", Side.CULTIVATION, c -> lineWithDefinitions(c, "info.mxt.techniques", c.getData(MxtAttachments.SPIRIT_IDENTITY).learnedTechniques(), "cultivation_technique"));
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
        for (Entry<Holder<Resource>> entry : cultivation.cultivationProgresses().object2DoubleEntrySet()) {
            if (!entry.getKey().value().showCultivationInfo()) continue;
            BreakthroughStatus status = CultivationService.breakthroughStatus(collector.getPlayer(), entry.getKey(), context);
            Component tooltip = status.reached()
                    ? Component.translatable(status.conditionsMet() ? "info.mxt.breakthrough.ready" : "info.mxt.breakthrough.conditions_unmet")
                    : null;
            int color = status.conditionsMet() ? 0xFF55FF55 : 0xFFE0E4EC;
            collector.add(first ? Component.translatable("info.mxt.cultivation_progress") : null,
                    Component.literal(DefinitionText.name(entry.getKey(), "resource").getString() + ": " + String.format("%.2f", entry.getDoubleValue())),
                    color, tooltip);
            first = false;
        }
    }

    /**
     * Displays every resource tracked by the player, using Mortal when no realm is assigned.
     */
    private static void realmLines(InformationCollector collector) {
        CultivationAttachment cultivation = collector.getData(MxtAttachments.CULTIVATION);
        Set<Holder<Resource>> resources = new LinkedHashSet<>(cultivation.cultivationProgresses().keySet());
        resources.addAll(cultivation.realmStages().keySet());
        if (resources.isEmpty()) {
            collector.add("info.mxt.realm", Component.translatable("info.mxt.mortal"));
            return;
        }
        boolean first = true;
        for (Holder<Resource> resource : resources) {
            if (!resource.value().showCultivationInfo()) continue;
            Holder<?> realm = cultivation.realmStage(resource);
            Component realmName = realm == null ? Component.translatable("info.mxt.mortal") : DefinitionText.name(realm, "realm_stage");
            collector.add(first ? Component.translatable("info.mxt.realm") : null, DefinitionText.name(resource, "resource").copy().append(": ").append(realmName));
            first = false;
        }
    }

    private record RegisteredInformation(Side side, Consumer<InformationCollector> collector) {
    }
}
