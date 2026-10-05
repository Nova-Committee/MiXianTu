package com.iafenvoy.mxt.compat.jei;

import com.iafenvoy.mxt.data.alchemy.MedicinalProperty;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.QualityRequirement;
import com.iafenvoy.mxt.recipe.AlchemyRecipe;
import com.iafenvoy.mxt.runtime.item.QualityService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The alchemy requirement panel. Everything the furnace asks for is a threshold on a medicinal property, a number,
 * or a tier requirement, so none of it can be a slot: the panel states the numbers and leaves the pack's own
 * {@code guide} to say what an example composition looks like.
 */
final class AlchemyJeiText {
    private static final int LABEL_COLOR = 0xFF404040;
    private static final int VALUE_COLOR = 0xFF666666;
    private static final String SAME_LINE = " | ";
    private static final String LIST_SEPARATOR = ", ";

    private AlchemyJeiText() {
    }

    static List<Component> requirementLines(AlchemyRecipe recipe, Font font, int maxWidth, int maxLines) {
        List<List<Piece>> blocks = new ArrayList<>();
        blocks.add(line(Component.translatable("jei.mxt.alchemy.main"), thresholds(recipe.mainRequirements())));
        if (!recipe.auxiliaryRequirements().isEmpty())
            blocks.add(line(Component.translatable("jei.mxt.alchemy.auxiliary"), thresholds(recipe.auxiliaryRequirements())));
        blocks.add(pair(Component.translatable("jei.mxt.alchemy.catalyst"), atLeast(recipe.catalystRequirement()),
                Component.translatable("jei.mxt.alchemy.balance"), value(number(recipe.balanceTolerance()))));
        blocks.add(line(Component.translatable("jei.mxt.alchemy.temperature"), range(recipe.targetTemperature(), recipe.temperatureTolerance())));
        blocks.add(line(Component.translatable("jei.mxt.alchemy.duration"), duration(recipe.duration())));
        blocks.add(line(Component.translatable("jei.mxt.alchemy.bad_ticks"), ticks(Integer.toString(recipe.maxBadTicks()))));
        if (!recipe.minimumAura().isEmpty())
            blocks.add(line(Component.translatable("jei.mxt.alchemy.aura"), auras(recipe.minimumAura())));
        recipe.furnaceQuality().ifPresent(requirement ->
                blocks.add(line(Component.translatable("jei.mxt.alchemy.furnace_quality"), qualities(requirement))));
        recipe.inputQuality().ifPresent(requirement ->
                blocks.add(line(Component.translatable("jei.mxt.alchemy.input_quality"), qualities(requirement))));

        List<Component> lines = new ArrayList<>();
        for (List<Piece> block : blocks) wrap(block, font, maxWidth, lines);
        if (lines.size() <= maxLines) return lines;
        List<Component> capped = new ArrayList<>(lines.subList(0, maxLines));
        capped.set(maxLines - 1, Component.translatable("jei.mxt.alchemy.overflow"));
        return capped;
    }

    private static List<Piece> thresholds(Map<Holder<MedicinalProperty>, NumberProvider> requirements) {
        List<Piece> pieces = new ArrayList<>();
        boolean first = true;
        for (Map.Entry<Holder<MedicinalProperty>, NumberProvider> entry : requirements.entrySet()) {
            if (!first) pieces.add(piece(LIST_SEPARATOR));
            pieces.add(new Piece(DefinitionText.name(entry.getKey(), "medicinal_property"), VALUE_COLOR));
            pieces.add(piece(" ≥ " + number(entry.getValue())));
            first = false;
        }
        return pieces;
    }

    private static List<Piece> auras(Map<Holder<Aura>, NumberProvider> requirements) {
        List<Piece> pieces = new ArrayList<>();
        boolean first = true;
        for (Map.Entry<Holder<Aura>, NumberProvider> entry : requirements.entrySet()) {
            if (!first) pieces.add(piece(LIST_SEPARATOR));
            int color = entry.getKey().value().auraType().map(type -> type.value().color()).orElse(0xFFFFFF);
            pieces.add(new Piece(DefinitionText.name(entry.getKey(), "aura"),
                    SpiritJeiText.readableTextColor(color, SpiritJeiText.PANEL_BACKGROUND)));
            pieces.add(piece(" ≥ " + number(entry.getValue())));
            first = false;
        }
        return pieces;
    }

    private static List<Piece> qualities(QualityRequirement requirement) {
        List<Piece> pieces = new ArrayList<>();
        for (Either<Holder<ItemQuality>, TagKey<ItemQuality>> entry : requirement.qualities()) {
            if (!pieces.isEmpty()) pieces.add(piece(" / "));
            // The tier's own colour is the definition's, so this piece keeps whatever it carries.
            pieces.add(new Piece(entry.map(QualityService::displayName, tag -> Component.literal("#" + tag.location())), null));
        }
        requirement.minQuality().ifPresent(minimum -> {
            if (!pieces.isEmpty()) pieces.add(piece(" / "));
            pieces.add(piece("≥ "));
            pieces.add(new Piece(QualityService.displayName(minimum), null));
        });
        return pieces;
    }

    private static List<Piece> line(Component label, List<Piece> values) {
        List<Piece> pieces = new ArrayList<>();
        pieces.add(new Piece(label, LABEL_COLOR));
        pieces.addAll(values);
        return pieces;
    }

    private static List<Piece> pair(Component firstLabel, List<Piece> first, Component secondLabel, List<Piece> second) {
        List<Piece> pieces = line(firstLabel, first);
        pieces.add(new Piece(Component.literal(SAME_LINE), LABEL_COLOR));
        pieces.add(new Piece(secondLabel, LABEL_COLOR));
        pieces.addAll(second);
        return pieces;
    }

    private static List<Piece> atLeast(NumberProvider provider) {
        return value("≥ " + number(provider));
    }

    private static List<Piece> range(NumberProvider target, NumberProvider tolerance) {
        return value(number(target) + " ± " + number(tolerance));
    }

    // Only a written constant converts: a formula is the pack's own tick expression, and the batch divides it
    // further by the materials' and the core's alchemy modifiers, so no single second count exists to print.
    private static List<Piece> duration(NumberProvider provider) {
        if (provider instanceof Constant(double ticks))
            return List.of(new Piece(Component.translatable("jei.mxt.alchemy.seconds", seconds(ticks)), VALUE_COLOR));
        return ticks(number(provider));
    }

    private static String seconds(double ticks) {
        double value = ticks / 20.0D;
        if (value == Math.rint(value)) return Long.toString((long) value);
        String text = String.format(Locale.ROOT, "%.2f", value);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static List<Piece> ticks(String amount) {
        return List.of(new Piece(Component.translatable("jei.mxt.alchemy.ticks", amount), VALUE_COLOR));
    }

    private static List<Piece> value(String text) {
        return List.of(piece(text));
    }

    private static Piece piece(String text) {
        return new Piece(Component.literal(text), VALUE_COLOR);
    }

    private static String number(NumberProvider provider) {
        return SpiritJeiText.providerName(provider);
    }

    // Breaks at piece boundaries only, so a property name and its threshold never split apart; a piece wider than
    // the panel is clipped instead of pushing the rest off the edge.
    private static void wrap(List<Piece> pieces, Font font, int maxWidth, List<Component> out) {
        MutableComponent current = Component.empty();
        int width = 0;
        for (Piece piece : pieces) {
            Component text = piece.color() == null ? piece.text().copy() : piece.text().copy().withColor(piece.color());
            int pieceWidth = font.width(text);
            if (width > 0 && width + pieceWidth > maxWidth) {
                out.add(current);
                current = Component.empty();
                width = 0;
            }
            if (pieceWidth > maxWidth) {
                out.add(Component.literal(clip(font, text.getString(), maxWidth))
                        .withColor(piece.color() == null ? VALUE_COLOR : piece.color()));
                continue;
            }
            current.append(text);
            width += pieceWidth;
        }
        if (width > 0) out.add(current);
    }

    private static String clip(Font font, String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        return font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    /**
     * One run of text on a line. A colour of {@code null} keeps whatever the text already carries, which is how a
     * tier's or an aura's own colour survives into the panel.
     */
    private record Piece(Component text, Integer color) {
    }
}
