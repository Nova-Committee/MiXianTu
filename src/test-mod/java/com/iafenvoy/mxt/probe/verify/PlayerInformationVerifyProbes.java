package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.screen.information.MxtPlayerInformation;
import com.iafenvoy.mxt.screen.information.MxtPlayerView;
import com.iafenvoy.saturn.api.playerview.PlayerView;
import com.iafenvoy.saturn.view.PlayerInformation;
import com.iafenvoy.saturn.view.PlayerViewSnapshot;
import com.iafenvoy.saturn.view.PlayerViews;
import com.iafenvoy.saturn.view.model.InformationRow;
import com.iafenvoy.saturn.view.model.InformationValue;
import com.iafenvoy.saturn.view.model.PlayerViewMeta;
import com.iafenvoy.saturn.view.model.PlayerViewRef;
import com.iafenvoy.saturn.view.model.SavedPlayerViewContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The mxt rows have to reach the shared panel through the snapshot, not by asking a live player at display time, and
 * the same rows have to come back from a player's saved attachments. A body nobody has touched yet is what proves the
 * first half: the rows that need no attachment are produced from the capture, and the rest stay hidden rather than
 * rendering empty. The second half saves a body that does have data and compares the two row sets.
 */
public final class PlayerInformationVerifyProbes {
    private PlayerInformationVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/player_information/rows", PlayerInformationVerifyProbes::rows),
                Probe.of("verify/player_information/saved", PlayerInformationVerifyProbes::saved));
    }

    private static void rows(ProbeContext context) {
        context.check("the mxt group is registered", PlayerInformation.groups().stream()
                .anyMatch(group -> group.id().equals(MxtPlayerInformation.GROUP_ID)));
        // A second body, so the rows reflect this player's own attachments and not the arena player's history.
        ServerPlayer subject = context.joinBeside(context.player(), 2.0D, 0.0D);
        PlayerViewSnapshot view = PlayerViews.parse(subject);
        List<InformationRow> rows = mxtRows(view);
        Set<Identifier> ids = ids(rows);
        context.check("an untouched body answers the realm row", ids.contains(id(MxtPlayerInformation.REALM)));
        context.check("an untouched body answers the progress row", ids.contains(id(MxtPlayerInformation.PROGRESS)));
        context.check("rows without data stay hidden", !ids.contains(id(MxtPlayerInformation.SPIRIT_ROOTS))
                && !ids.contains(id(MxtPlayerInformation.PHYSIQUES))
                && !ids.contains(id(MxtPlayerInformation.TECHNIQUES))
                && !ids.contains(id(MxtPlayerInformation.CURSES)));
        context.check("every mxt row is sourced from the captured field",
                rows.stream().allMatch(row -> row.content().sourceFields().equals(Set.of(MxtPlayerView.INFORMATION.id()))));
    }

    private static void saved(ProbeContext context) {
        ServerPlayer subject = context.joinBeside(context.player(), 2.0D, 0.0D);
        Holder<Aura> aura = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        subject.getData(MxtAttachments.CULTIVATION).setCultivationProgress(aura, 0.25D);
        List<InformationRow> live = mxtRows(PlayerViews.parse(subject));
        List<InformationRow> fromSave = mxtRows(PlayerViews.parse(savedContext(context, subject)));
        context.check("the saved view answers the progress row",
                ids(fromSave).contains(id(MxtPlayerInformation.PROGRESS)));
        context.expect("the saved view produces the same rows as the live one", ids(fromSave), ids(live));
        context.expect("the saved progress keeps the saved number",
                valueText(fromSave, MxtPlayerInformation.PROGRESS), valueText(live, MxtPlayerInformation.PROGRESS));
    }

    private static SavedPlayerViewContext savedContext(ProbeContext context, ServerPlayer subject) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, context.registries());
        subject.saveWithoutId(output);
        return new SavedPlayerViewContext(
                new PlayerViewMeta(PlayerViewRef.current(subject.getUUID()), UUID.randomUUID(), PlayerViewMeta.SourceKind.SAVED,
                        Optional.empty(), Optional.of(context.level().getGameTime()), System.currentTimeMillis()),
                context.registries(), output.buildResult(), Optional.empty(), Optional.empty());
    }

    private static List<InformationRow> mxtRows(PlayerView view) {
        return PlayerInformation.collect(view, MxtPlayerInformation.group()).rows();
    }

    private static Set<Identifier> ids(List<InformationRow> rows) {
        return rows.stream().map(InformationRow::id).collect(Collectors.toSet());
    }

    // The first value line of a row, as the display side would read it.
    private static String valueText(List<InformationRow> rows, String key) {
        return rows.stream().filter(row -> row.id().equals(id(key))).findFirst().map(row -> switch (row.content().value()) {
            case InformationValue.Text text -> text.text().getString();
            case InformationValue.Values values -> values.values().isEmpty() ? ""
                    : values.values().getFirst() instanceof InformationValue.Text text ? text.text().getString() : "";
            default -> "";
        }).orElse("");
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("mxt", path);
    }
}
