package com.iafenvoy.mxt.probe.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.api.AlchemyWorkstation;
import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtKubeJsDataCodec;
import com.iafenvoy.mxt.data.ItemPickerManager;
import com.iafenvoy.mxt.data.alchemy.AlchemyFurnaceDefinition;
import com.iafenvoy.mxt.data.alchemy.MedicinalProperty;
import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.condition.builtin.item.ItemMatcherCondition;
import com.iafenvoy.mxt.data.condition.builtin.item.ItemQualityCondition;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.QualityIngredient;
import com.iafenvoy.mxt.data.quality.QualityLadders;
import com.iafenvoy.mxt.data.quality.QualityRequirement;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.recipe.AlchemyRecipe;
import com.iafenvoy.mxt.recipe.TalismanDrawingRecipe;
import com.iafenvoy.mxt.registry.*;
import com.iafenvoy.mxt.runtime.alchemy.*;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService.AlchemyPreview;
import com.iafenvoy.mxt.runtime.failure.AlchemyFailure;
import com.iafenvoy.mxt.runtime.forging.ForgingSurface;
import com.iafenvoy.mxt.runtime.forging.ForgingWorkstationService;
import com.iafenvoy.mxt.runtime.item.QualityRequirements;
import com.iafenvoy.mxt.runtime.item.QualityService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.iafenvoy.mxt.util.matcher.builtin.IngredientEntry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The quality gate where a pack writes it: the three adapters are decoded from JSON exactly as a pack writes them,
 * against the fixtures this pack ships (the {@code default_quality} table, its tag key, a tier named on a stack by a
 * component, and one deliberately broken ladder), and then asked about real stacks.
 */
public final class QualityProbes {
    private QualityProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("item/quality/table", QualityProbes::table),
                Probe.of("item/quality/tag_key", QualityProbes::tagKey),
                Probe.of("item/quality/component_first", QualityProbes::componentFirst),
                Probe.of("item/quality/membership", QualityProbes::membership),
                Probe.of("item/quality/min_same_chain", QualityProbes::minSameChain),
                Probe.of("item/quality/cross_chain", QualityProbes::crossChain),
                Probe.of("item/quality/compare_same_chain", QualityProbes::compareSameChain),
                Probe.of("item/quality/compare_cross_chain", QualityProbes::compareCrossChain),
                Probe.of("item/quality/compare_refused_ladder", QualityProbes::compareRefusedLadder),
                Probe.of("item/quality/empty_refused", QualityProbes::emptyRefused),
                Probe.of("item/quality/matcher_entry", QualityProbes::matcherEntry),
                Probe.of("item/quality/matcher_needs_items", QualityProbes::matcherNeedsItems),
                Probe.of("item/quality/ingredient", QualityProbes::ingredient),
                Probe.of("item/quality/ingredient_needs_items", QualityProbes::ingredientNeedsItems),
                Probe.of("item/quality/blueprint_migrated", QualityProbes::blueprintMigrated),
                Probe.of("item/quality/forge_input", QualityProbes::forgeInput),
                Probe.of("item/quality/talisman_paper", QualityProbes::talismanPaper),
                Probe.of("item/quality/ingredient_entry", QualityProbes::ingredientEntry),
                Probe.of("item/quality/formula_paper", QualityProbes::formulaPaper),
                Probe.of("item/quality/paper_rank_variable", QualityProbes::paperRankVariable),
                Probe.of("item/quality/tier_source_layers", QualityProbes::tierSourceLayers),
                Probe.of("item/quality/empty_requirement", QualityProbes::emptyRequirement),
                Probe.of("item/quality/default_quality_priority", QualityProbes::defaultQualityPriority),
                Probe.of("item/quality/tooltip_order", QualityProbes::tooltipOrder),
                Probe.of("item/quality/picker_catalogue", QualityProbes::pickerCatalogue),
                Probe.of("item/quality/kubejs_requirement", QualityProbes::kubejsRequirement),
                Probe.of("item/quality/alchemy_core_gate", QualityProbes::alchemyCoreGate),
                Probe.of("item/quality/alchemy_input_gate", QualityProbes::alchemyInputGate),
                Probe.of("item/quality/gates", QualityProbes::gates));
    }

    private static void table(ProbeContext context) {
        RegistryAccess access = context.registries();
        ItemStack clay = new ItemStack(Items.CLAY_BALL);         // table: normal
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);     // table: poor
        leg(context, "table", read(access, clay).equals("mxt_test:normal") && read(access, cobble).equals("mxt_test:poor"),
                "clay=" + read(access, clay) + " cobble=" + read(access, cobble), "clay=normal cobble=poor");
    }

    // A tag key answers for every item the tag holds, which is the point of writing one.
    private static void tagKey(ProbeContext context) {
        RegistryAccess access = context.registries();
        ItemStack star = new ItemStack(Items.NETHER_STAR);       // table: poor, written as a tag key
        ItemStack dirt = new ItemStack(Items.DIRT);              // no tier anywhere
        leg(context, "tag_key", read(access, star).equals("mxt_test:poor") && read(access, dirt).equals("none"),
                "tagged=" + read(access, star) + " untagged=" + read(access, dirt), "tagged=poor untagged=none");
    }

    private static void componentFirst(ProbeContext context) {
        RegistryAccess access = context.registries();
        ItemStack graded = named(Items.DIRT, tier(access, "mxt_test:excellent"));
        leg(context, "component_first", read(access, graded).equals("mxt_test:excellent"),
                "graded=" + read(access, graded), "graded=excellent");
    }

    private static void membership(ProbeContext context) {
        RegistryAccess access = context.registries();
        RegistryOps<JsonElement> ops = ops(context);
        ItemStack clay = new ItemStack(Items.CLAY_BALL);         // table: normal
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);     // table: poor
        ItemStack dirt = new ItemStack(Items.DIRT);              // no tier anywhere
        ItemQualityCondition membership = quality(ops, "{\"type\": \"mxt:item_quality\", \"quality\": [\"mxt_test:normal\"]}");
        leg(context, "membership",
                membership != null && ask(access, membership.requirement(), clay)
                        && !ask(access, membership.requirement(), cobble) && !ask(access, membership.requirement(), dirt),
                "clay=" + ask(access, membership.requirement(), clay) + " cobble=" + ask(access, membership.requirement(), cobble)
                        + " dirt=" + ask(access, membership.requirement(), dirt),
                "clay=true cobble=false dirt=false");
    }

    // poor < normal < excellent on mxt_test:forged, so the middle and the top pass and the bottom does not.
    private static void minSameChain(ProbeContext context) {
        RegistryAccess access = context.registries();
        RegistryOps<JsonElement> ops = ops(context);
        ItemStack clay = new ItemStack(Items.CLAY_BALL);         // table: normal
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);     // table: poor
        ItemStack star = new ItemStack(Items.NETHER_STAR);       // table: poor, written as a tag key
        ItemStack graded = named(Items.DIRT, tier(access, "mxt_test:excellent"));
        ItemQualityCondition minimum = quality(ops, "{\"type\": \"mxt:item_quality\", \"min_quality\": \"mxt_test:normal\"}");
        leg(context, "min_same_chain",
                minimum != null && ask(access, minimum.requirement(), clay) && ask(access, minimum.requirement(), graded)
                        && !ask(access, minimum.requirement(), cobble) && !ask(access, minimum.requirement(), star),
                "normal=" + ask(access, minimum.requirement(), clay) + " excellent=" + ask(access, minimum.requirement(), graded)
                        + " poor=" + ask(access, minimum.requirement(), cobble) + " tagged_poor=" + ask(access, minimum.requirement(), star),
                "normal=true excellent=true poor=false tagged_poor=false");
    }

    // kiln_a sits on a chain of its own, so a minimum from another chain can never be reached.
    private static void crossChain(ProbeContext context) {
        RegistryAccess access = context.registries();
        RegistryOps<JsonElement> ops = ops(context);
        ItemStack clay = new ItemStack(Items.CLAY_BALL);         // table: normal
        ItemStack kiln = named(Items.DIRT, tier(access, "mxt_test:alchemy/kiln_a"));
        ItemQualityCondition minimum = quality(ops, "{\"type\": \"mxt:item_quality\", \"min_quality\": \"mxt_test:normal\"}");
        ItemQualityCondition kilnMinimum = quality(ops, "{\"type\": \"mxt:item_quality\", \"min_quality\": \"mxt_test:alchemy/kiln_a\"}");
        leg(context, "cross_chain",
                kilnMinimum != null && !ask(access, minimum.requirement(), kiln) && ask(access, kilnMinimum.requirement(), kiln) && !ask(access, kilnMinimum.requirement(), clay),
                "kiln_vs_normal=" + ask(access, minimum.requirement(), kiln)
                        + " kiln_vs_kiln=" + ask(access, kilnMinimum.requirement(), kiln)
                        + " clay_vs_kiln=" + ask(access, kilnMinimum.requirement(), clay),
                "kiln_vs_normal=false kiln_vs_kiln=true clay_vs_kiln=false");
    }

    // Comparing two tiers: positions are the only order, and they only exist on the one ladder that holds both.
    // poor(0) < normal(1) < excellent(2) on mxt_test:forged; kiln_a is a ladder of its own.
    private static void compareSameChain(ProbeContext context) {
        RegistryAccess access = context.registries();
        Holder<ItemQuality> poorTier = tier(access, "mxt_test:poor");
        Holder<ItemQuality> normalTier = tier(access, "mxt_test:normal");
        Holder<ItemQuality> excellentTier = tier(access, "mxt_test:excellent");
        OptionalInt upward = QualityLadders.compare(excellentTier, poorTier);
        OptionalInt downward = QualityLadders.compare(poorTier, excellentTier);
        OptionalInt level = QualityLadders.compare(normalTier, normalTier);
        leg(context, "compare_same_chain",
                upward.orElse(99) == 2 && downward.orElse(99) == -2 && level.orElse(99) == 0
                        && QualityLadders.rank(poorTier).orElse(-1) == 0
                        && QualityLadders.rank(excellentTier).orElse(-1) == 2
                        && QualityLadders.atLeast(excellentTier, normalTier)
                        && !QualityLadders.atLeast(poorTier, normalTier),
                "up=" + upward.orElse(99) + " down=" + downward.orElse(99) + " same=" + level.orElse(99)
                        + " poor_rank=" + QualityLadders.rank(poorTier).orElse(-1)
                        + " excellent_rank=" + QualityLadders.rank(excellentTier).orElse(-1)
                        + " excellent_at_least_normal=" + QualityLadders.atLeast(excellentTier, normalTier)
                        + " poor_at_least_normal=" + QualityLadders.atLeast(poorTier, normalTier),
                "up=2 down=-2 same=0 poor_rank=0 excellent_rank=2 excellent_at_least_normal=true poor_at_least_normal=false");
    }

    // Two ladders' positions mean nothing to each other, so the answer is "cannot be compared" rather than a
    // direction; the requirement layer reads the same emptiness as "no".
    private static void compareCrossChain(ProbeContext context) {
        RegistryAccess access = context.registries();
        Holder<ItemQuality> normalTier = tier(access, "mxt_test:normal");
        Holder<ItemQuality> excellentTier = tier(access, "mxt_test:excellent");
        Holder<ItemQuality> kilnTier = tier(access, "mxt_test:alchemy/kiln_a");
        leg(context, "compare_cross_chain",
                QualityLadders.compare(excellentTier, kilnTier).isEmpty()
                        && QualityLadders.compare(kilnTier, excellentTier).isEmpty()
                        && !QualityLadders.atLeast(kilnTier, normalTier)
                        && QualityLadders.atLeast(kilnTier, kilnTier),
                "excellent_vs_kiln=" + QualityLadders.compare(excellentTier, kilnTier).orElse(99)
                        + " kiln_vs_excellent=" + QualityLadders.compare(kilnTier, excellentTier).orElse(99)
                        + " kiln_at_least_normal=" + QualityLadders.atLeast(kilnTier, normalTier)
                        + " kiln_at_least_kiln=" + QualityLadders.atLeast(kilnTier, kilnTier),
                "excellent_vs_kiln=99 kiln_vs_excellent=99 kiln_at_least_normal=false kiln_at_least_kiln=true");
    }

    // The refused ladder: two tiers point at each other, so the walk has no entry to start from, no walked
    // ladder holds either of them, and a requirement naming one can never pass. A next pointing at a tier
    // the pack does not provide cannot be written at all - the registry refuses to load - so a cycle is the
    // one shape a pack can actually reach.
    private static void compareRefusedLadder(ProbeContext context) {
        RegistryAccess access = context.registries();
        Holder<ItemQuality> refusedTier = tier(access, "mxt_test:refused_probe");
        ItemStack refused = named(Items.DIRT, refusedTier);
        QualityRequirement refusedFloor = new QualityRequirement(List.of(), Optional.of(refusedTier));
        // What the walk found about this cycle is logged rather than collected, so all that is checked here is
        // that no reading of the ladder answers anything for either tier of it.
        leg(context, "compare_refused_ladder",
                QualityLadders.rank(refusedTier).isEmpty()
                        && QualityLadders.compare(refusedTier, refusedTier).isEmpty()
                        && !QualityLadders.atLeast(refusedTier, refusedTier)
                        && !QualityRequirements.test(access, refused, refusedFloor),
                "rank=" + QualityLadders.rank(refusedTier).orElse(99)
                        + " self=" + QualityLadders.compare(refusedTier, refusedTier).orElse(99)
                        + " at_least_self=" + QualityLadders.atLeast(refusedTier, refusedTier)
                        + " requirement=" + QualityRequirements.test(access, refused, refusedFloor),
                "rank=99 self=99 at_least_self=false requirement=false");
    }

    private static void emptyRefused(ProbeContext context) {
        RegistryOps<JsonElement> ops = ops(context);
        leg(context, "empty_refused",
                condition(ops, "{\"type\": \"mxt:item_quality\"}") == null
                        && condition(ops, "{\"type\": \"mxt:item_quality\", \"quality\": []}") == null,
                "neither=" + (condition(ops, "{\"type\": \"mxt:item_quality\"}") == null)
                        + " empty_list=" + (condition(ops, "{\"type\": \"mxt:item_quality\", \"quality\": []}") == null),
                "neither=true empty_list=true");
    }

    private static void matcherEntry(ProbeContext context) {
        RegistryAccess access = context.registries();
        RegistryOps<JsonElement> ops = ops(context);
        ItemStack clay = new ItemStack(Items.CLAY_BALL);         // table: normal
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);     // table: poor
        ItemStack dirt = new ItemStack(Items.DIRT);              // no tier anywhere
        ItemStack clayGraded = named(Items.CLAY_BALL, tier(access, "mxt_test:excellent"));
        ItemMatcherCondition matcher = matcher(ops, "{\"type\": \"mxt:item_matcher\", \"items\": "
                + "[{\"type\": \"mxt:quality\", \"items\": \"minecraft:clay_ball\", \"min_quality\": \"mxt_test:normal\"}]}");
        boolean matcherShape = matcher != null && matcher.entries().getFirst() instanceof ItemMatcher.Entry entry && !entry.itemLevel();
        leg(context, "matcher_entry",
                matcherShape && matches(matcher, clay) && matches(matcher, clayGraded) && !matches(matcher, cobble) && !matches(matcher, dirt),
                "shape=" + matcherShape + " clay=" + matches(matcher, clay) + " graded_clay=" + matches(matcher, clayGraded)
                        + " cobble=" + matches(matcher, cobble) + " dirt=" + matches(matcher, dirt),
                "shape=true clay=true graded_clay=true cobble=false dirt=false");
    }

    private static void matcherNeedsItems(ProbeContext context) {
        RegistryOps<JsonElement> ops = ops(context);
        leg(context, "matcher_needs_items",
                decode(ops, ItemMatcher.Entry.CODEC, qualityEntry("")) == null
                        && decode(ops, ItemMatcher.Entry.CODEC, qualityEntry("[]")) == null,
                "no_items=" + (decode(ops, ItemMatcher.Entry.CODEC, qualityEntry("")) == null)
                        + " empty_items=" + (decode(ops, ItemMatcher.Entry.CODEC, qualityEntry("[]")) == null),
                "no_items=true empty_items=true");
    }

    // The ingredient adapter is the one that reaches every recipe, so it is decoded through the same vanilla
    // codec a recipe file uses.
    private static void ingredient(ProbeContext context) {
        RegistryOps<JsonElement> ops = ops(context);
        ItemStack clay = new ItemStack(Items.CLAY_BALL);         // table: normal
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);     // table: poor
        ItemStack dirt = new ItemStack(Items.DIRT);              // no tier anywhere
        Ingredient ingredient = decode(ops, Ingredient.CODEC, "{\"neoforge:ingredient_type\": \"mxt:quality\", "
                + "\"items\": \"minecraft:clay_ball\", \"min_quality\": \"mxt_test:normal\"}");
        boolean ingredientShape = ingredient.isCustom() && ingredient.getCustomIngredient() instanceof QualityIngredient quality && quality.items().findAny().isPresent() && !quality.isSimple();
        leg(context, "ingredient",
                ingredientShape && ingredient.test(clay) && !ingredient.test(cobble) && !ingredient.test(dirt),
                "shape=" + ingredientShape + " clay=" + ingredient.test(clay)
                        + " cobble=" + ingredient.test(cobble)
                        + " dirt=" + ingredient.test(dirt),
                "shape=true clay=true cobble=false dirt=false");
    }

    private static void ingredientNeedsItems(ProbeContext context) {
        // The command probe hardcoded the verdict here (`leg(..., false, "no_items=" + false, ...)`), so it reported
        // MISMATCH without asking anything; the two decodes above it were the assertion it meant to make.
        RegistryOps<JsonElement> ops = ops(context);
        boolean noItems = decode(ops, Ingredient.CODEC,
                "{\"neoforge:ingredient_type\": \"mxt:quality\", \"min_quality\": \"mxt_test:normal\"}") == null;
        boolean emptyItems = decode(ops, Ingredient.CODEC,
                "{\"neoforge:ingredient_type\": \"mxt:quality\", \"items\": [], \"min_quality\": \"mxt_test:normal\"}") == null;
        leg(context, "ingredient_needs_items", noItems && emptyItems,
                "no_items=" + noItems + " empty_items=" + emptyItems, "no_items=true empty_items=true");
    }

    // The forging side: a blueprint entry is a NeoForge sized ingredient now, so a requirement can read the
    // stack. The fixtures are read back through the registry, which is also the evidence that both shapes
    // load from a data pack - the two migrated ones and the one written with the mxt:quality ingredient.
    private static void blueprintMigrated(ProbeContext context) {
        RegistryAccess access = context.registries();
        ForgingBlueprint migrated = blueprint(access, "pickaxe");
        leg(context, "blueprint_migrated",
                migrated != null && migrated.input().size() == 2 && migrated.input().getFirst().count() == 3
                        && migrated.input().getLast().count() == 2,
                migrated == null ? "blueprint missing"
                        : "entries=" + migrated.input().size() + " counts=" + migrated.input().getFirst().count()
                        + "/" + migrated.input().getLast().count(),
                "entries=2 counts=3/2");
    }

    private static void forgeInput(ProbeContext context) {
        RegistryAccess access = context.registries();
        ItemStack clay = new ItemStack(Items.CLAY_BALL);         // table: normal
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);     // table: poor
        ForgingBlueprint gate = blueprint(access, "quality_gate");
        SizedIngredient entry = gate == null || gate.input().isEmpty() ? null : gate.input().getFirst();
        SimpleContainer forge = new SimpleContainer(ForgingSurface.TOTAL_SLOTS);
        forge.setItem(ForgingSurface.INPUT_START, clay.copy());
        boolean covered = entry != null && ForgingWorkstationService.materialsCovered(forge, gate.input());
        int have = entry == null ? -1 : ForgingWorkstationService.availableCount(forge, entry);
        forge.setItem(ForgingSurface.INPUT_START, cobble.copy());
        leg(context, "forge_input",
                entry != null && entry.count() == 1 && entry.ingredient().isCustom() && covered && have == 1
                        && !ForgingWorkstationService.materialsCovered(forge, gate.input()),
                entry == null ? "blueprint missing"
                        : "custom=" + entry.ingredient().isCustom() + " needs=" + entry.count() + " covered=" + covered
                        + " graded=" + have + " poor_covered=" + ForgingWorkstationService.materialsCovered(forge, gate.input()),
                "custom=true needs=1 covered=true graded=1 poor_covered=false");
    }

    // The paper the drawing station takes is a tag now, so a pack adds its own paper without code; the
    // built-in blank talisman is its only member, which keeps the old behaviour.
    private static void talismanPaper(ProbeContext context) {
        ItemStack blank = new ItemStack(MxtItems.BLANK_TALISMAN.get());
        ItemStack plainPaper = new ItemStack(Items.PAPER);
        leg(context, "talisman_paper",
                blank.is(TalismanDrawingRecipe.paperTag()) && !plainPaper.is(TalismanDrawingRecipe.paperTag()),
                "blank=" + blank.is(TalismanDrawingRecipe.paperTag()) + " paper=" + plainPaper.is(TalismanDrawingRecipe.paperTag()),
                "blank=true paper=false");
    }

    // The matcher entry that asks a whole ingredient: it is how a cost - the drawing's paper among them -
    // carries an ingredient type at all.
    private static void ingredientEntry(ProbeContext context) {
        RegistryAccess access = context.registries();
        RegistryOps<JsonElement> ops = ops(context);
        ItemStack clay = new ItemStack(Items.CLAY_BALL);         // table: normal
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);     // table: poor
        ItemMatcherCondition viaIngredient = matcher(ops, "{\"type\": \"mxt:item_matcher\", \"items\": "
                + "[{\"type\": \"mxt:ingredient\", \"ingredient\": {\"neoforge:ingredient_type\": \"mxt:quality\", "
                + "\"items\": \"minecraft:clay_ball\", \"min_quality\": \"mxt_test:normal\"}}]}");
        boolean ingredientEntryShape = viaIngredient != null
                && viaIngredient.entries().getFirst() instanceof IngredientEntry ingredientEntry && !ingredientEntry.itemLevel();
        leg(context, "ingredient_entry",
                ingredientEntryShape && matches(viaIngredient, clay) && !matches(viaIngredient, cobble),
                "shape=" + ingredientEntryShape + " clay=" + matches(viaIngredient, clay)
                        + " cobble=" + matches(viaIngredient, cobble),
                "shape=true clay=true cobble=false");
    }

    // A formula's own paper field, read back from the recipes the data pack actually loaded: the ward asks
    // for at least mxt_test:normal, so only a graded paper passes there, and the sigil names none and falls
    // back to the tag.
    private static void formulaPaper(ProbeContext context) {
        RegistryAccess access = context.registries();
        TalismanDrawingRecipe ward = drawing(context, "drawing_ward");
        TalismanDrawingRecipe sigil = drawing(context, "drawing_sigil");
        Ingredient wardPaper = ward == null ? null : ward.paper().orElse(null);
        ItemStack blank = new ItemStack(MxtItems.BLANK_TALISMAN.get());
        ItemStack plainPaper = new ItemStack(Items.PAPER);
        ItemStack gradedBlank = named(MxtItems.BLANK_TALISMAN.get(), tier(access, "mxt_test:excellent"));
        ItemStack poorBlank = named(MxtItems.BLANK_TALISMAN.get(), tier(access, "mxt_test:poor"));
        leg(context, "formula_paper",
                wardPaper != null && wardPaper.isCustom() && sigil != null && sigil.paper().isEmpty()
                        && wardPaper.test(gradedBlank) && !wardPaper.test(poorBlank)
                        && !wardPaper.test(blank) && !wardPaper.test(plainPaper),
                "ward_custom=" + (wardPaper != null && wardPaper.isCustom())
                        + " sigil_default=" + (sigil != null && sigil.paper().isEmpty())
                        + " excellent=" + (wardPaper != null && wardPaper.test(gradedBlank))
                        + " poor=" + (wardPaper != null && wardPaper.test(poorBlank))
                        + " none=" + (wardPaper != null && wardPaper.test(blank))
                        + " vanilla_paper=" + (wardPaper != null && wardPaper.test(plainPaper)),
                "ward_custom=true sigil_default=true excellent=true poor=false none=false vanilla_paper=false");
    }

    // paper_rank is the second variable a settlement formula may read; a name that is neither it nor
    // percentage nor a registry variable is still refused.
    private static void paperRankVariable(ProbeContext context) {
        RegistryOps<JsonElement> ops = ops(context);
        Object ranked = decode(ops, TalismanDrawingRecipe.Grade.CODEC,
                "{\"min_completion\": 0.5, \"max_damage\": \"paper_rank * 100\"}");
        Object unknownRank = decode(ops, TalismanDrawingRecipe.Grade.CODEC,
                "{\"min_completion\": 0.5, \"max_damage\": \"paper_tier * 100\"}");
        leg(context, "paper_rank_variable", ranked != null && unknownRank == null,
                "paper_rank=" + (ranked != null) + " unknown=" + (unknownRank == null),
                "paper_rank=true unknown=true");
    }

    // Which source answered decides the membership answer: the component on a graded clay ball makes it
    // excellent, while a plain one reads the default_quality table and is normal, so the same requirement
    // takes one and refuses the other.
    private static void tierSourceLayers(ProbeContext context) {
        RegistryAccess access = context.registries();
        RegistryOps<JsonElement> ops = ops(context);
        ItemStack clay = new ItemStack(Items.CLAY_BALL);         // table: normal
        ItemStack clayGraded = named(Items.CLAY_BALL, tier(access, "mxt_test:excellent"));
        ItemQualityCondition membership = quality(ops, "{\"type\": \"mxt:item_quality\", \"quality\": [\"mxt_test:normal\"]}");
        ItemQualityCondition gradedMembership = quality(ops, "{\"type\": \"mxt:item_quality\", \"quality\": [\"mxt_test:excellent\"]}");
        leg(context, "tier_source_layers",
                gradedMembership != null
                        && !ask(access, gradedMembership.requirement(), clay) && ask(access, gradedMembership.requirement(), clayGraded)
                        && ask(access, membership.requirement(), clay) && !ask(access, membership.requirement(), clayGraded),
                "table_as_excellent=" + ask(access, gradedMembership.requirement(), clay)
                        + " graded_as_excellent=" + ask(access, gradedMembership.requirement(), clayGraded)
                        + " graded_as_normal=" + ask(access, membership.requirement(), clayGraded),
                "table_as_excellent=false graded_as_excellent=true graded_as_normal=false");
    }

    // Nothing asked passes whatever the stack is: neither half written means "any tier, including none".
    private static void emptyRequirement(ProbeContext context) {
        RegistryAccess access = context.registries();
        ItemStack clay = new ItemStack(Items.CLAY_BALL);         // table: normal
        ItemStack dirt = new ItemStack(Items.DIRT);              // no tier anywhere
        leg(context, "empty_requirement",
                QualityRequirements.test(access, clay, QualityRequirement.of(List.of()))
                        && QualityRequirements.test(access, dirt, QualityRequirement.of(List.of())),
                "clay=" + QualityRequirements.test(access, clay, QualityRequirement.of(List.of()))
                        + " dirt=" + QualityRequirements.test(access, dirt, QualityRequirement.of(List.of())),
                "clay=true dirt=true");
    }

    // The table takes a priority the way every other item table does, and the bare tier id is still the whole
    // value: gravel is named by a tag at priority 0 and by itself at 5, flint the other way round, so both
    // directions of the merger are exercised by the fixtures.
    private static void defaultQualityPriority(ProbeContext context) {
        RegistryAccess access = context.registries();
        ItemStack gravel = new ItemStack(Items.GRAVEL);
        ItemStack flint = new ItemStack(Items.FLINT);
        leg(context, "default_quality_priority",
                read(access, gravel).equals("mxt_test:normal") && read(access, flint).equals("mxt_test:excellent"),
                "gravel=" + read(access, gravel) + " flint=" + read(access, flint),
                "gravel=mxt_test:normal flint=mxt_test:excellent");
    }

    // The catalogue's own order: the tooltip-order tag first, in the order it writes, then every remaining
    // tier once. The picker's quality page is that same list, which is what gives the tag its consumer.
    private static void tooltipOrder(ProbeContext context) {
        RegistryAccess access = context.registries();
        List<Holder<ItemQuality>> catalogue = QualityService.ordered(access);
        List<String> order = catalogue.stream().map(HolderHelper::id).map(Identifier::toString).toList();
        long tiers = MxtDatapackRegistries.holders(access, MxtResourceKeys.ITEM_QUALITY).count();
        leg(context, "tooltip_order",
                order.size() == tiers && order.getFirst().equals("mxt_test:excellent")
                        && order.get(1).equals("mxt_test:normal") && order.get(2).equals("mxt_test:poor"),
                "size=" + order.size() + " first=" + order.getFirst() + " second=" + order.get(1) + " third=" + order.get(2),
                "size=" + tiers + " first=mxt_test:excellent second=mxt_test:normal third=mxt_test:poor");
    }

    private static void pickerCatalogue(ProbeContext context) {
        RegistryAccess access = context.registries();
        List<Holder<ItemQuality>> catalogue = QualityService.ordered(access);
        List<String> order = catalogue.stream().map(HolderHelper::id).map(Identifier::toString).toList();
        List<ItemPickerManager.PickerItem> rows = qualityRows(access);
        String firstRow = rows.isEmpty() ? "none" : rows.getFirst().names().getLast().getString();
        leg(context, "picker_catalogue",
                rows.size() == order.size() && firstRow.equals(order.getFirst())
                        && ItemPickerManager.category(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "default_quality")).isPresent()
                        && ItemPickerManager.category(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "heat_source")).isPresent(),
                "rows=" + rows.size() + " first=" + firstRow
                        + " default_quality=" + ItemPickerManager.category(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "default_quality")).isPresent()
                        + " heat_source=" + ItemPickerManager.category(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "heat_source")).isPresent(),
                "rows=" + order.size() + " first=" + order.getFirst() + " default_quality=true heat_source=true");
    }

    // The script-side entry points, called the way a script's binding calls them: a requirement decoded from
    // the same JSON a pack writes, and a tier id, both answered by the same single point. The test server
    // loads no KubeJS, so what is checked here is the validated operation the binding delegates to.
    private static void kubejsRequirement(ProbeContext context) {
        RegistryAccess access = context.registries();
        ItemStack clay = new ItemStack(Items.CLAY_BALL);         // table: normal
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);     // table: poor
        ItemStack kiln = named(Items.DIRT, tier(access, "mxt_test:alchemy/kiln_a"));
        QualityRequirement fromJson = MxtKubeJsDataCodec.decodeCached(QualityRequirement.CODEC.codec(),
                JsonParser.parseString("{\"min_quality\": \"mxt_test:normal\"}"), access);
        Identifier normalId = Identifier.parse("mxt_test:normal");
        leg(context, "kubejs_requirement",
                fromJson != null && MxtKubeJsApi.satisfiesQualityRequirement(context.player(), clay, fromJson)
                        && !MxtKubeJsApi.satisfiesQualityRequirement(context.player(), cobble, fromJson)
                        && MxtKubeJsApi.qualityAtLeast(context.player(), clay, normalId)
                        && !MxtKubeJsApi.qualityAtLeast(context.player(), cobble, normalId)
                        && !MxtKubeJsApi.qualityAtLeast(context.player(), kiln, normalId),
                "decoded=" + (fromJson != null)
                        + " normal=" + (fromJson != null && MxtKubeJsApi.satisfiesQualityRequirement(context.player(), clay, fromJson))
                        + " poor=" + (fromJson != null && MxtKubeJsApi.satisfiesQualityRequirement(context.player(), cobble, fromJson))
                        + " at_least_normal=" + MxtKubeJsApi.qualityAtLeast(context.player(), clay, normalId)
                        + " poor_at_least=" + MxtKubeJsApi.qualityAtLeast(context.player(), cobble, normalId)
                        + " cross_ladder=" + MxtKubeJsApi.qualityAtLeast(context.player(), kiln, normalId),
                "decoded=true normal=true poor=false at_least_normal=true poor_at_least=false cross_ladder=false");
    }

    // The alchemy side asks about its machine rather than its slots: the loaded formula names the core tier
    // it needs, the rank is a plain value its formulas can read, and a written but empty requirement is
    // refused instead of silently always passing.
    private static void alchemyCoreGate(ProbeContext context) {
        RegistryOps<JsonElement> ops = ops(context);
        String recipeBase = recipeBase();
        AlchemyRecipe formula = alchemy(context, "hot");
        Object emptyGate = decode(ops, AlchemyRecipe.CODEC.codec(), recipeBase + "\"furnace_quality\": {}}");
        Object gradedGate = decode(ops, AlchemyRecipe.CODEC.codec(),
                recipeBase + "\"furnace_quality\": {\"min_quality\": \"mxt_test:alchemy/kiln_a\"}}");
        boolean gateFloor = formula != null && formula.furnaceQuality()
                .flatMap(QualityRequirement::minQuality)
                .map(holder -> HolderHelper.id(holder).toString().equals("mxt_test:alchemy/kiln_a")).orElse(false);
        double entryDuration = formula == null ? -1.0D
                : formula.duration().evaluate(FormulaContext.EMPTY.with(AlchemyRecipe.FURNACE_RANK, 0.0D));
        double upperDuration = formula == null ? -1.0D
                : formula.duration().evaluate(FormulaContext.EMPTY.with(AlchemyRecipe.FURNACE_RANK, 1.0D));
        leg(context, "alchemy_core_gate",
                gateFloor && emptyGate == null && gradedGate != null
                        && entryDuration == 4.0D && upperDuration == 5.0D,
                "floor=" + gateFloor + " empty_refused=" + (emptyGate == null) + " graded=" + (gradedGate != null)
                        + " duration_at_0=" + entryDuration + " duration_at_1=" + upperDuration,
                "floor=true empty_refused=true graded=true duration_at_0=4.0 duration_at_1=5.0");
    }

    // The input side asks the same question of every non-empty slot. The gated formula is read back from the
    // recipe manager, so what a pack writes is what the runtime sees.
    private static void alchemyInputGate(ProbeContext context) {
        RegistryOps<JsonElement> ops = ops(context);
        String recipeBase = recipeBase();
        Object emptyInput = decode(ops, AlchemyRecipe.CODEC.codec(), recipeBase + "\"input_quality\": {}}");
        Object gradedInput = decode(ops, AlchemyRecipe.CODEC.codec(),
                recipeBase + "\"input_quality\": {\"min_quality\": \"mxt_test:alchemy/kiln_a\"}}");
        AlchemyRecipe gated = alchemy(context, "zz_input_gate");
        boolean inputFloor = gated != null && gated.inputQuality()
                .flatMap(QualityRequirement::minQuality)
                .map(holder -> HolderHelper.id(holder).toString().equals("mxt_test:alchemy/kiln_a")).orElse(false);
        leg(context, "alchemy_input_gate",
                inputFloor && emptyInput == null && gradedInput != null,
                "floor=" + inputFloor + " empty_refused=" + (emptyInput == null) + " graded=" + (gradedInput != null),
                "floor=true empty_refused=true graded=true");
    }

    // Both gates again from a stub controller and a real player: those legs need no placed multiblock, so
    // they run wherever this probe does.
    private static void gates(ProbeContext context) {
        Generic core = new Generic(standard(), context.at(4, 0, 4), spec(context), core(context));
        AlchemyPreview entry = AlchemyWorkstationService.preview(context.player(), core);
        expect(context, "gate-core-entry-recipe", resolved(entry), id("alchemy/hot"));
        expect(context, "gate-core-entry-blocker", entry.blocker().orElse(null), AlchemyFailure.TEMPERATURE);
        expect(context, "gate-core-entry-duration", duration(entry), 4L);

        // A tier from another ladder is neither above nor below the one the formula names, so the gate refuses and
        // the batch is left untouched.
        Generic crossed = new Generic(standard(), context.at(4, 0, 4), spec(context), core(context, id("poor")));
        AlchemyPreview crossPreview = AlchemyWorkstationService.preview(context.player(), crossed);
        expect(context, "gate-core-cross-ladder", crossPreview.blocker().orElse(null), AlchemyFailure.FURNACE_TIER);
        expect(context, "gate-core-cross-started", AlchemyWorkstationService.start(context.player(), crossed).started(), false);
        expect(context, "gate-core-cross-kept", crossed.container().getItem(0).getCount(), 2);

        // One tier up the same ladder satisfies the same floor, and that tier's own alchemy_modifier is what makes the
        // batch shorter: hot's own numbers read the rank (4 + 1), the tier halves the result. The mixture is untouched
        // by the core, which is the whole point - a better furnace never changes what the herbs add up to.
        Generic upper = new Generic(standard(), context.at(4, 0, 4), spec(context), core(context, id("alchemy/kiln_b")));
        AlchemyPreview higher = AlchemyWorkstationService.preview(context.player(), upper);
        expect(context, "gate-core-upper-recipe", resolved(higher), id("alchemy/hot"));
        expect(context, "gate-core-upper-blocker", higher.blocker().orElse(null), AlchemyFailure.TEMPERATURE);
        expect(context, "core-tier-speed", duration(higher), 3L);
        expect(context, "core-tier-mixture", property(context, higher, "alchemy/heat"), 2.0D);

        // The material side of the same rule, on the entry core: one input tier whose own alchemy_modifier is 2 halves
        // this batch as well, which is the factor the core's is multiplied by.
        Generic quick = new Generic(standard(), context.at(4, 0, 4), spec(context), core(context));
        grade(context, quick.container().getItem(0), id("alchemy/quick"));
        AlchemyPreview quickened = AlchemyWorkstationService.preview(context.player(), quick);
        expect(context, "material-tier-speed", duration(quickened), 2L);

        // The input gate, on a formula that only this probe can reach: doubling every herb picks the gated recipe
        // because it is the only candidate that strictly covers the two ungated ones. Every input has to answer, so
        // the batch is refused while the herbs carry no tier of their own.
        Generic refused = new Generic(doubled(), context.at(4, 0, 4), spec(context), core(context));
        AlchemyPreview gated = AlchemyWorkstationService.preview(context.player(), refused);
        expect(context, "gate-input-recipe", resolved(gated), id("alchemy/zz_input_gate"));
        expect(context, "gate-input-refused", gated.blocker().orElse(null), AlchemyFailure.INPUT_TIER);
        expect(context, "gate-input-started", AlchemyWorkstationService.start(context.player(), refused).started(), false);
        expect(context, "gate-input-kept", refused.container().getItem(0).getCount(), 4);

        // Marked on the ladder the formula names, the same batch passes the gate and starts. Every non-empty slot
        // answers the requirement, so every one of them has to be marked.
        Generic marked = new Generic(doubled(), context.at(4, 0, 4), spec(context), core(context));
        for (int index = 0; index < AlchemySlots.OUTPUT_START; index++)
            if (!marked.container().getItem(index).isEmpty())
                grade(context, marked.container().getItem(index), id("alchemy/kiln_a"));
        AlchemyPreview markedPreview = AlchemyWorkstationService.preview(context.player(), marked);
        expect(context, "gate-input-marked-blocker", markedPreview.blocker().orElse(null), null);
        expect(context, "gate-input-marked-started", AlchemyWorkstationService.start(context.player(), marked).started(), true);

        // The temperature ceiling is a minimum of three, and the specification is only one of them: the capped
        // fixture answers with its own number even where the walls and the heat block allow far more, a specification
        // that declares nothing lets the walls and the heat block decide, and either of those being unavailable reads
        // as a furnace that cannot run at all.
        AlchemyFurnaceDefinition capped = spec(context, "alchemy/capped");
        AlchemyFurnaceDefinition wide = spec(context, "alchemy/wide");
        expect(context, "temperature-limit-wall-binds",
                AlchemyFurnaceDefinition.temperatureLimit(60.0D, 150.0D, wide), 60.0D);
        expect(context, "temperature-limit-heat-binds",
                AlchemyFurnaceDefinition.temperatureLimit(200.0D, 80.0D, wide), 80.0D);
        expect(context, "temperature-limit-core-binds",
                AlchemyFurnaceDefinition.temperatureLimit(200.0D, 150.0D, capped), 60.0D);
        expect(context, "temperature-limit-no-core",
                AlchemyFurnaceDefinition.temperatureLimit(200.0D, 150.0D, null), 150.0D);
        expect(context, "temperature-limit-unavailable",
                AlchemyFurnaceDefinition.temperatureLimit(0.0D, 150.0D, capped), 0.0D);
        expect(context, "temperature-limit-loaded", capped.maxTemperature().orElse(-1.0D), 60.0D);

        // The wall limit counts the structure's own wall list, so the list itself is worth one leg: 22 cells are
        // validated, and the core, the two ports, the output and the bottom centre are not walls among them.
        int[] walls = AlchemyFurnaceStructure.walls();
        expect(context, "wall-count", walls.length == 18
                        && !contains(walls, AlchemyFurnaceStructure.CONTROLLER_INDEX)
                        && !contains(walls, AlchemyFurnaceStructure.MAIN_INDEX)
                        && !contains(walls, AlchemyFurnaceStructure.AUXILIARY_INDEX)
                        && !contains(walls, AlchemyFurnaceStructure.OUTPUT_INDEX)
                        && !contains(walls, AlchemyFurnaceStructure.HEAT_INDEX),
                true);
    }

    private static String recipeBase() {
        return "{\"main_requirements\": {\"mxt_test:herb/main\": 1}, \"catalyst_requirement\": 1, "
                + "\"target_temperature\": 50, \"duration\": 4, "
                + "\"success_outputs\": [{\"id\": \"minecraft:stick\", \"count\": 1}], ";
    }

    private static RegistryOps<JsonElement> ops(ProbeContext context) {
        return RegistryOps.create(JsonOps.INSTANCE, context.registries());
    }

    // The picker's own provider, asked the way its screen asks it: the client's registry access is the only argument
    // that differs there.
    private static List<ItemPickerManager.PickerItem> qualityRows(RegistryAccess access) {
        ResourceKey<Registry<?>> key = ItemPickerManager.category(MxtResourceKeys.ITEM_QUALITY.identifier()).orElse(null);
        ItemPickerManager.ItemProvider<?> provider = key == null ? null : ItemPickerManager.provider(key);
        return provider == null ? List.of() : provider.items().apply(access, ignored -> true);
    }

    private static String qualityEntry(String items) {
        return items.isEmpty()
                ? "{\"type\": \"mxt:quality\", \"min_quality\": \"mxt_test:normal\"}"
                : "{\"type\": \"mxt:quality\", \"items\": " + items + ", \"min_quality\": \"mxt_test:normal\"}";
    }

    private static ItemQualityCondition quality(RegistryOps<JsonElement> ops, String json) {
        return condition(ops, json) instanceof ItemQualityCondition condition ? condition : null;
    }

    private static ItemMatcherCondition matcher(RegistryOps<JsonElement> ops, String json) {
        return condition(ops, json) instanceof ItemMatcherCondition condition ? condition : null;
    }

    private static Object condition(RegistryOps<JsonElement> ops, String json) {
        return ItemCondition.CODEC.parse(ops, JsonParser.parseString(json))
                .result().orElse(null);
    }

    private static <T> T decode(RegistryOps<JsonElement> ops, Codec<T> codec, String json) {
        return codec.parse(ops, JsonParser.parseString(json)).result().orElse(null);
    }

    // Asked through the runtime's own entry point rather than through the condition, because a condition also wants
    // the holder entity while the question here is the tier itself.
    private static boolean ask(RegistryAccess access, QualityRequirement requirement, ItemStack stack) {
        return QualityRequirements.test(access, stack, requirement);
    }

    private static boolean matches(ItemMatcherCondition condition, ItemStack stack) {
        return condition != null && ItemMatcher.matches(condition, stack);
    }

    private static ItemStack named(Item item, Holder<ItemQuality> quality) {
        ItemStack stack = new ItemStack(item);
        stack.set(MxtDataComponents.QUALITY.get(), quality);
        return stack;
    }

    private static String read(RegistryAccess access, ItemStack stack) {
        return QualityService.find(access, stack).map(HolderHelper::id).map(Identifier::toString).orElse("none");
    }

    private static Holder<ItemQuality> tier(RegistryAccess access, String id) {
        return MxtDatapackRegistries.holder(access, MxtResourceKeys.ITEM_QUALITY, Identifier.parse(id))
                .orElseThrow();
    }

    private static ForgingBlueprint blueprint(RegistryAccess access, String path) {
        return MxtDatapackRegistries.holder(access, MxtResourceKeys.FORGING_BLUEPRINT, Identifier.fromNamespaceAndPath("mxt_test", path))
                .map(Holder::value).orElse(null);
    }

    // Through the recipe manager rather than the codec: what a formula's paper field is has to be what the data
    // pack loaded, not what the same JSON decodes to a second time.
    private static TalismanDrawingRecipe drawing(ProbeContext context, String path) {
        return context.server().getRecipeManager()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("mxt_test", "talisman/" + path)))
                .filter(holder -> holder.value() instanceof TalismanDrawingRecipe)
                .map(holder -> (TalismanDrawingRecipe) holder.value())
                .orElse(null);
    }

    private static AlchemyRecipe alchemy(ProbeContext context, String path) {
        return context.server().getRecipeManager()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("mxt_test", "alchemy/" + path)))
                .filter(holder -> holder.value() instanceof AlchemyRecipe)
                .map(holder -> (AlchemyRecipe) holder.value())
                .orElse(null);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("mxt_test", path);
    }

    // One leg of the old command: the value is asserted, and the line it printed is kept, on the branch it printed it.
    private static boolean leg(ProbeContext context, String name, boolean ok, String actual, String expected) {
        String line = "quality probe: " + name + " actual=" + actual + " expected=" + expected + (ok ? " OK" : " MISMATCH");
        if (ok) context.note(line);
        else context.fail(line);
        return ok;
    }

    // The gate legs asserted with the old command's own helper, which reported both values.
    private static void expect(ProbeContext context, String name, Object actual, Object expected) {
        boolean ok = Objects.equals(expected, actual);
        String line = "alchemy gate probe: " + (ok ? "OK" : "MISMATCH") + " " + name
                + " actual=" + actual + " expected=" + expected;
        if (ok) context.note(line);
        else context.fail(line);
    }

    private static SimpleContainer standard() {
        SimpleContainer container = new SimpleContainer(AlchemySlots.TOTAL);
        container.setItem(0, new ItemStack(Items.ALLIUM, 2));
        container.setItem(1, new ItemStack(Items.POPPY));
        container.setItem(2, new ItemStack(Items.CORNFLOWER, 2));
        container.setItem(4, new ItemStack(Items.OXEYE_DAISY));
        return container;
    }

    // The same mixture doubled, which is what makes the gated recipe dominate instead of tying with the ungated two.
    private static SimpleContainer doubled() {
        SimpleContainer container = new SimpleContainer(AlchemySlots.TOTAL);
        container.setItem(0, new ItemStack(Items.ALLIUM, 4));
        container.setItem(1, new ItemStack(Items.POPPY));
        container.setItem(2, new ItemStack(Items.CORNFLOWER, 4));
        container.setItem(4, new ItemStack(Items.OXEYE_DAISY));
        return container;
    }

    private static ItemStack core(ProbeContext context) {
        ItemStack stack = MxtBlocks.ALCHEMY_FURNACE.toStack();
        stack.set(MxtDataComponents.ALCHEMY_FURNACE.get(), spec(context));
        return stack;
    }

    private static ItemStack core(ProbeContext context, Identifier quality) {
        ItemStack stack = core(context);
        grade(context, stack, quality);
        return stack;
    }

    private static void grade(ProbeContext context, ItemStack stack, Identifier quality) {
        Holder<ItemQuality> holder = MxtDatapackRegistries
                .holder(context.registries(), MxtResourceKeys.ITEM_QUALITY, quality).orElseThrow();
        stack.set(MxtDataComponents.QUALITY.get(), holder);
    }

    private static Holder<AlchemyFurnaceDefinition> spec(ProbeContext context) {
        return MxtDatapackRegistries.holder(context.registries(), MxtResourceKeys.ALCHEMY_FURNACE, id("alchemy/wide")).orElseThrow();
    }

    private static AlchemyFurnaceDefinition spec(ProbeContext context, String path) {
        return MxtDatapackRegistries.holder(context.registries(), MxtResourceKeys.ALCHEMY_FURNACE, id(path))
                .map(Holder::value).orElseThrow();
    }

    private static boolean contains(int[] values, int wanted) {
        for (int value : values) if (value == wanted) return true;
        return false;
    }

    private static Identifier resolved(AlchemyPreview preview) {
        return preview.resolvedRecipe().map(ResourceKey::identifier).orElse(null);
    }

    private static long duration(AlchemyPreview preview) {
        return preview.parameters().map(AlchemyWorkstationService.Parameters::durationTicks).orElse(-1L);
    }

    private static double property(ProbeContext context, AlchemyPreview preview, String property) {
        Holder<MedicinalProperty> holder = MxtDatapackRegistries
                .holder(context.registries(), MxtResourceKeys.MEDICINAL_PROPERTY, id(property)).orElseThrow();
        return preview.mixture().main().getOrDefault(holder, -1.0D);
    }

    /**
     * A controller that owns its own slots: the gates under test read the recipe, the core item and the mixture, so
     * the structure and the heat cell are answers this stub simply states.
     */
    private static final class Generic implements AlchemyWorkstation {
        private static final double STATION_TEMPERATURE = 50.0D;
        private static final double TEMPERATURE_LIMIT = 200.0D;
        private final Container container;
        private final BlockPos pos;
        private final Holder<AlchemyFurnaceDefinition> spec;
        private final AlchemyWorkstationState state = new AlchemyWorkstationState();
        private final ItemStack core;
        private double temperature;

        private Generic(Container container, BlockPos pos, Holder<AlchemyFurnaceDefinition> spec, ItemStack core) {
            this.container = container;
            this.pos = pos;
            this.spec = spec;
            this.core = core;
            this.state.setTargetTemperature(STATION_TEMPERATURE);
        }

        @Override
        public Container container() {
            return this.container;
        }

        @Override
        public AlchemyWorkstationState state() {
            return this.state;
        }

        @Override
        public BlockPos getBlockPos() {
            return this.pos;
        }

        @Override
        public ItemStack furnaceItem() {
            return this.core;
        }

        @Override
        public Optional<Holder<AlchemyFurnaceDefinition>> furnaceDefinition() {
            return Optional.of(this.spec);
        }

        @Override
        public double temperature() {
            return this.temperature;
        }

        @Override
        public double targetTemperature() {
            return this.state.targetTemperature();
        }

        @Override
        public boolean setTargetTemperature(double temperature) {
            this.state.setTargetTemperature(temperature);
            return true;
        }

        @Override
        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        @Override
        public BlockPos heatSourcePos() {
            return this.pos;
        }

        @Override
        public double wallTemperatureLimit() {
            return TEMPERATURE_LIMIT;
        }

        @Override
        public double heatTemperatureLimit() {
            return TEMPERATURE_LIMIT;
        }

        @Override
        public double maximumTemperature() {
            return TEMPERATURE_LIMIT;
        }

        @Override
        public AlchemyFurnaceStructure.Status structureStatus() {
            return new AlchemyFurnaceStructure.Status(true, true, List.of(), List.of(), List.of(), this.pos, Direction.NORTH);
        }

        @Override
        public AlchemyPhase phase() {
            return this.state.phase();
        }

        @Override
        public void setChanged() {
        }
    }
}
