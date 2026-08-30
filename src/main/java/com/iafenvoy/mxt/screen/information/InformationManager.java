package com.iafenvoy.mxt.screen.information;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.DefinitionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.iafenvoy.mxt.screen.information.InformationHelper.*;

public final class InformationManager {
    private static final Map<Identifier, RegisteredInformation> INFORMATION = new LinkedHashMap<>();

    static {
        register("health", Side.BASIC, p -> line("info.mxt.health", String.format("%.1f / %.1f", p.getHealth(), p.getMaxHealth())));
        register("food", Side.BASIC, p -> line("info.mxt.food", Integer.toString(p.getFoodData().getFoodLevel())));
        register("experience", Side.BASIC, p -> line("info.mxt.experience", Integer.toString(p.experienceLevel)));
        register("position", Side.BASIC, p -> line("info.mxt.position", String.format("%d, %d, %d", p.getBlockX(), p.getBlockY(), p.getBlockZ())));
        register("dimension", Side.BASIC, p -> line("info.mxt.dimension", p.level().dimension().identifier().getPath()));

        register("realm", Side.CULTIVATION, p -> line("info.mxt.realm", joinDefinitions(p.getData(MxtAttachments.CULTIVATION).realmStages(), "realm_stage")));
        register("cultivation_progress", Side.CULTIVATION, p -> line("info.mxt.cultivation_progress", formatProgress(p.getData(MxtAttachments.CULTIVATION))));
        register("cultivating", Side.CULTIVATION, p -> line("info.mxt.cultivating", Component.translatable(p.getData(MxtAttachments.CULTIVATION).cultivating() ? "info.mxt.yes" : "info.mxt.no")));
        register("spirit_roots", Side.CULTIVATION, p -> lineWithDefinitions("info.mxt.spirit_roots", p.getData(MxtAttachments.SPIRIT_IDENTITY).spiritRoots(), "spirit_root"));
        register("physiques", Side.CULTIVATION, p -> lineWithDefinitions("info.mxt.physiques", p.getData(MxtAttachments.SPIRIT_IDENTITY).physiques(), "physique"));
        register("techniques", Side.CULTIVATION, p -> lineWithDefinitions("info.mxt.techniques", p.getData(MxtAttachments.SPIRIT_IDENTITY).learnedTechniques(), "cultivation_technique"));
    }

    public static void register(@NotNull String id, @NotNull Side side, @NotNull InformationProvider supplier) {
        register(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, id), side, supplier);
    }

    public static void register(@NotNull Identifier id, @NotNull Side side, @NotNull InformationProvider supplier) {
        INFORMATION.put(id, new RegisteredInformation(side, supplier));
    }

    public static List<InformationEntry> entries(Player player, Side side) {
        if (player == null) return List.of();
        List<InformationEntry> result = new ArrayList<>();
        for (RegisteredInformation information : INFORMATION.values()) {
            if (information.side() != side) continue;
            try {
                information.supplier().get(player).filter(InformationEntry::fulfilled).ifPresent(result::add);
            } catch (RuntimeException exception) {
                MiXianTu.LOGGER.warn("Information supplier failed for {}", side, exception);
            }
        }
        return result;
    }

    @FunctionalInterface
    public interface InformationProvider {
        Optional<InformationEntry> get(Player player);
    }

    public enum Side {
        BASIC, CULTIVATION
    }

    private static String formatProgress(CultivationAttachment spirit) {
        if (spirit.cultivationProgresses().isEmpty()) return "-";
        return spirit.cultivationProgresses().object2DoubleEntrySet().stream()
                .map(entry -> DefinitionText.name(entry.getKey(), "resource").getString() + ": " + String.format("%.2f", entry.getDoubleValue()))
                .reduce((a, b) -> a + ", " + b).orElse("-");
    }

    private record RegisteredInformation(Side side, InformationProvider supplier) {
    }
}
