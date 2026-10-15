package com.iafenvoy.mxt.probe.artifact;

import com.iafenvoy.mxt.data.artifact.ArtifactDescription;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.runtime.artifact.ArtifactHoldService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.util.PlayerNames;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * What a definition earns in a tooltip, and who a claimed stack is said to belong to. Both are read through the same
 * builders a client uses, so the line keys can be asserted here without one.
 */
public final class ArtifactTooltipProbes {
    private ArtifactTooltipProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("artifact/tooltip", ArtifactTooltipProbes::tooltip),
                Probe.of("artifact/owner_name", ArtifactTooltipProbes::ownerName));
    }

    // ArtifactDescription builds the tooltip so the same list is readable here without a client: the rendered words
    // belong to a language file, but how many lines a definition earns belongs to this module.
    private static void tooltip(ProbeContext context) {
        Provider access = context.registries();
        ServerPlayer player = context.player();
        ItemStack flightStack = new ItemStack(Items.IRON_SWORD);
        ItemStack jadeStack = new ItemStack(Items.AMETHYST_SHARD);
        ArtifactService.refine(jadeStack, player);
        ItemStack wardStack = new ItemStack(Items.PRISMARINE_SHARD);
        List<String> flightLines = ArtifactDescription.keys(ArtifactDescription.describe(access, flightStack, player, false));
        List<String> jadeLines = ArtifactDescription.keys(ArtifactDescription.describe(access, new ItemStack(Items.AMETHYST_SHARD), player, false));
        List<String> fedLines = ArtifactDescription.keys(ArtifactDescription.describe(access, jadeStack, player, false));
        List<String> wardLines = ArtifactDescription.keys(ArtifactDescription.describe(access, wardStack, player, false));
        // Anything a definition's own action charges is reported as a price; the blood-priced probe is the one
        // fixture charging health, and the only one whose claim line names a number.
        List<String> bloodLines = ArtifactDescription.keys(ArtifactDescription.describe(access, new ItemStack(Items.BLAZE_ROD), player, false));
        List<String> upkeepLines = ArtifactDescription.keys(ArtifactDescription.describe(access, new ItemStack(Items.IRON_SHOVEL), player, false));
        // The long press closes every tooltip with a gesture, and an unowned artifact is offered the claim while
        // one the reader owns is offered the pour - the two halves never appear together.
        boolean tooltip = flightLines.equals(List.of(tooltipKey("header"), tooltipKey("unowned"),
                // One entry, one line: the speed and what riding costs share a line, so a definition with
                // four abilities produces four lines rather than a paragraph.
                tooltipKey("aura"), tooltipKey("mount_costs"), tooltipKey("hold_claim")))
                // The jade does not require an owner, so a fresh one says nothing about ownership at all, and its
                // action charges aura rather than health, so its claim is free.
                && jadeLines.equals(List.of(tooltipKey("header"),
                tooltipKey("aura"), tooltipKey("aura"), tooltipKey("aura"), tooltipKey("storage"), tooltipKey("hold_claim_free")))
                && fedLines.equals(List.of(tooltipKey("header"), tooltipKey("owned"),
                tooltipKey("aura"), tooltipKey("aura"), tooltipKey("aura"), tooltipKey("nourishment"),
                tooltipKey("storage"), tooltipKey("hold_pour")))
                // The ward says nothing about a price, so the default two hearts are what it reports.
                && wardLines.equals(List.of(tooltipKey("header"),
                tooltipKey("aura"), tooltipKey("passive"), tooltipKey("passive"), tooltipKey("hold_claim")))
                && bloodLines.equals(List.of(tooltipKey("header"),
                tooltipKey("aura"), tooltipKey("hold_claim")))
                // An upkeep entry is a line of its own, and the fixture's free price keeps its claim line free.
                && upkeepLines.equals(List.of(tooltipKey("header"),
                tooltipKey("aura"), tooltipKey("upkeep"), tooltipKey("hold_claim_free")))
                // A stack no definition claims gets nothing at all, and advanced tooltips add the id under the name.
                && ArtifactDescription.describe(access, new ItemStack(Items.DIAMOND), player, false).isEmpty()
                && ArtifactDescription.keys(ArtifactDescription.describe(access, flightStack, player, true)).size() == flightLines.size() + 1;
        context.check("artifact roster tooltip lines flight=" + flightLines.size() + " jade=" + jadeLines.size()
                + " fed=" + fedLines.size() + " ward=" + wardLines.size() + " blood=" + bloodLines.size()
                + " upkeep=" + upkeepLines.size(), tooltip);
    }

    // Whose a stack is is reported by name: a claim writes this player's name next to their UUID and the tooltip
    // line carries that name rather than the id. The id stays reachable, but never as the only answer.
    private static void ownerName(ProbeContext context) {
        Provider access = context.registries();
        ServerPlayer player = context.player();
        ItemStack claimBlood = new ItemStack(Items.BLAZE_ROD);
        ArtifactHoldService.claim(player, claimBlood, access);
        boolean ownerNamed = ArtifactService.state(claimBlood).ownerName()
                .filter(player.getGameProfile().name()::equals).isPresent()
                && ArtifactDescription.describe(access, claimBlood, player, false).stream()
                .anyMatch(line -> ownedLineNames(line, player.getGameProfile().name()));
        // The name behind an id is answerable on demand, which is what a client holding a stack claimed before
        // names were kept asks for: the server answers from its player list, or admits it has never seen the player.
        boolean ownerLookup = PlayerNames.knownToServer(player.getUUID())
                .filter(player.getGameProfile().name()::equals).isPresent()
                && PlayerNames.knownToServer(UUID.randomUUID()).isEmpty();
        context.check("artifact owner shown by name=" + ownerNamed + " lookup=" + ownerLookup,
                ownerNamed && ownerLookup);
    }

    // One artifact tooltip key, so the tooltip can spell out the lines a definition must produce for a stack.
    private static String tooltipKey(String path) {
        return "tooltip.mxt.artifact." + path;
    }

    // The rendered words belong to a language file, so the check reads the component's own argument. The line is
    // a mark with the sentence appended, so its siblings are searched too.
    private static boolean ownedLineNames(Component line, String owner) {
        if (line.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(tooltipKey("owned"))
                && Arrays.stream(contents.getArgs()).anyMatch(argument -> argument instanceof String value && value.equals(owner)))
            return true;
        return line.getSiblings().stream().anyMatch(sibling -> ownedLineNames(sibling, owner));
    }
}
