package com.iafenvoy.mxt.testmod;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtKubeJsDataCodec;
import com.iafenvoy.mxt.data.condition.builtin.item.ItemMatcherCondition;
import com.iafenvoy.mxt.data.condition.builtin.item.ItemQualityCondition;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.QualityIngredient;
import com.iafenvoy.mxt.data.quality.QualityLadders;
import com.iafenvoy.mxt.data.quality.QualityRequirement;
import com.iafenvoy.mxt.picker.ItemPickerManager;
import com.iafenvoy.mxt.picker.PickerCategory;
import com.iafenvoy.mxt.recipe.AlchemyRecipe;
import com.iafenvoy.mxt.recipe.TalismanDrawingRecipe;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ServerCache;
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
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The quality gate where a pack writes it: the three adapters are decoded from JSON exactly as a pack writes them,
 * against the fixtures this pack ships (the {@code default_quality} table, its tag key, a tier named on a stack by a
 * component, and one deliberately broken ladder), and then asked about real stacks. Needs no player - it only reads
 * the level's registry access - so it also runs from a server console.
 */
public final class QualityProbes {
    private QualityProbes() {
    }

    public static int run(CommandSourceStack source) {
        RegistryAccess access = source.getLevel().registryAccess();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
        boolean ok = true;
        try {
            ItemStack clay = new ItemStack(Items.CLAY_BALL);         // table: normal
            ItemStack cobble = new ItemStack(Items.COBBLESTONE);     // table: poor
            ItemStack star = new ItemStack(Items.NETHER_STAR);       // table: poor, written as a tag key
            ItemStack dirt = new ItemStack(Items.DIRT);              // no tier anywhere
            ItemStack graded = named(Items.DIRT, tier(access, "mxt_test:excellent"));
            ItemStack kiln = named(Items.DIRT, tier(access, "mxt_test:alchemy/kiln_a"));
            ItemStack clayGraded = named(Items.CLAY_BALL, tier(access, "mxt_test:excellent"));

            ok &= leg(source, "table", read(access, clay).equals("mxt_test:normal") && read(access, cobble).equals("mxt_test:poor"),
                    "clay=" + read(access, clay) + " cobble=" + read(access, cobble), "clay=normal cobble=poor");

            // A tag key answers for every item the tag holds, which is the point of writing one.
            ok &= leg(source, "tag_key", read(access, star).equals("mxt_test:poor") && read(access, dirt).equals("none"),
                    "tagged=" + read(access, star) + " untagged=" + read(access, dirt), "tagged=poor untagged=none");

            ok &= leg(source, "component_first", read(access, graded).equals("mxt_test:excellent"),
                    "graded=" + read(access, graded), "graded=excellent");

            ItemQualityCondition membership = quality(ops, "{\"type\": \"mxt:item_quality\", \"quality\": [\"mxt_test:normal\"]}");
            ok &= leg(source, "membership",
                    membership != null && ask(access, membership.requirement(), clay)
                            && !ask(access, membership.requirement(), cobble) && !ask(access, membership.requirement(), dirt),
                    "clay=" + ask(access, membership.requirement(), clay) + " cobble=" + ask(access, membership.requirement(), cobble)
                            + " dirt=" + ask(access, membership.requirement(), dirt),
                    "clay=true cobble=false dirt=false");

            // poor < normal < excellent on mxt_test:forged, so the middle and the top pass and the bottom does not.
            ItemQualityCondition minimum = quality(ops, "{\"type\": \"mxt:item_quality\", \"min_quality\": \"mxt_test:normal\"}");
            ok &= leg(source, "min_same_chain",
                    minimum != null && ask(access, minimum.requirement(), clay) && ask(access, minimum.requirement(), graded)
                            && !ask(access, minimum.requirement(), cobble) && !ask(access, minimum.requirement(), star),
                    "normal=" + ask(access, minimum.requirement(), clay) + " excellent=" + ask(access, minimum.requirement(), graded)
                            + " poor=" + ask(access, minimum.requirement(), cobble) + " tagged_poor=" + ask(access, minimum.requirement(), star),
                    "normal=true excellent=true poor=false tagged_poor=false");

            // kiln_a sits on a chain of its own, so a minimum from another chain can never be reached.
            ItemQualityCondition kilnMinimum = quality(ops, "{\"type\": \"mxt:item_quality\", \"min_quality\": \"mxt_test:alchemy/kiln_a\"}");
            ok &= leg(source, "cross_chain",
                    minimum != null && kilnMinimum != null && !ask(access, minimum.requirement(), kiln)
                            && ask(access, kilnMinimum.requirement(), kiln) && !ask(access, kilnMinimum.requirement(), clay),
                    "kiln_vs_normal=" + ask(access, minimum.requirement(), kiln)
                            + " kiln_vs_kiln=" + ask(access, kilnMinimum.requirement(), kiln)
                            + " clay_vs_kiln=" + ask(access, kilnMinimum.requirement(), clay),
                    "kiln_vs_normal=false kiln_vs_kiln=true clay_vs_kiln=false");

            // Comparing two tiers: positions are the only order, and they only exist on the one ladder that holds
            // both. poor(0) < normal(1) < excellent(2) on mxt_test:forged; kiln_a is a ladder of its own.
            Holder<ItemQuality> poorTier = tier(access, "mxt_test:poor");
            Holder<ItemQuality> normalTier = tier(access, "mxt_test:normal");
            Holder<ItemQuality> excellentTier = tier(access, "mxt_test:excellent");
            Holder<ItemQuality> kilnTier = tier(access, "mxt_test:alchemy/kiln_a");
            OptionalInt upward = QualityLadders.compare(access, excellentTier, poorTier);
            OptionalInt downward = QualityLadders.compare(access, poorTier, excellentTier);
            OptionalInt level = QualityLadders.compare(access, normalTier, normalTier);
            ok &= leg(source, "compare_same_chain",
                    upward.orElse(99) == 2 && downward.orElse(99) == -2 && level.orElse(99) == 0
                            && QualityLadders.rank(access, poorTier).orElse(-1) == 0
                            && QualityLadders.rank(access, excellentTier).orElse(-1) == 2
                            && QualityLadders.atLeast(access, excellentTier, normalTier)
                            && !QualityLadders.atLeast(access, poorTier, normalTier),
                    "up=" + upward.orElse(99) + " down=" + downward.orElse(99) + " same=" + level.orElse(99)
                            + " poor_rank=" + QualityLadders.rank(access, poorTier).orElse(-1)
                            + " excellent_rank=" + QualityLadders.rank(access, excellentTier).orElse(-1)
                            + " excellent_at_least_normal=" + QualityLadders.atLeast(access, excellentTier, normalTier)
                            + " poor_at_least_normal=" + QualityLadders.atLeast(access, poorTier, normalTier),
                    "up=2 down=-2 same=0 poor_rank=0 excellent_rank=2 excellent_at_least_normal=true poor_at_least_normal=false");

            // Two ladders' positions mean nothing to each other, so the answer is "cannot be compared" rather than a
            // direction; the requirement layer reads the same emptiness as "no".
            ok &= leg(source, "compare_cross_chain",
                    QualityLadders.compare(access, excellentTier, kilnTier).isEmpty()
                            && QualityLadders.compare(access, kilnTier, excellentTier).isEmpty()
                            && !QualityLadders.atLeast(access, kilnTier, normalTier)
                            && QualityLadders.atLeast(access, kilnTier, kilnTier),
                    "excellent_vs_kiln=" + QualityLadders.compare(access, excellentTier, kilnTier).orElse(99)
                            + " kiln_vs_excellent=" + QualityLadders.compare(access, kilnTier, excellentTier).orElse(99)
                            + " kiln_at_least_normal=" + QualityLadders.atLeast(access, kilnTier, normalTier)
                            + " kiln_at_least_kiln=" + QualityLadders.atLeast(access, kilnTier, kilnTier),
                    "excellent_vs_kiln=99 kiln_vs_excellent=99 kiln_at_least_normal=false kiln_at_least_kiln=true");

            // The refused ladder: two tiers point at each other, so the walk has no entry to start from, no walked
            // ladder holds either of them, and a requirement naming one can never pass. A next pointing at a tier
            // the pack does not provide cannot be written at all - the registry refuses to load - so a cycle is the
            // one shape a pack can actually reach.
            Holder<ItemQuality> refusedTier = tier(access, "mxt_test:refused_probe");
            ItemStack refused = named(Items.DIRT, refusedTier);
            QualityRequirement refusedFloor = new QualityRequirement(List.of(), Optional.of(refusedTier));
            List<String> qualityProblems = ServerCache.get().map(cache -> cache.problems().stream()
                    .filter(problem -> problem.contains("/mxt/quality/")).toList()).orElse(List.of());
            ok &= leg(source, "compare_refused_ladder",
                    QualityLadders.rank(access, refusedTier).isEmpty()
                            && QualityLadders.compare(access, refusedTier, refusedTier).isEmpty()
                            && !QualityLadders.atLeast(access, refusedTier, refusedTier)
                            && !QualityRequirements.test(access, refused, refusedFloor)
                            && qualityProblems.size() == 2
                            && qualityProblems.stream().allMatch(problem -> problem.contains("refused_probe")),
                    "rank=" + QualityLadders.rank(access, refusedTier).orElse(99)
                            + " self=" + QualityLadders.compare(access, refusedTier, refusedTier).orElse(99)
                            + " at_least_self=" + QualityLadders.atLeast(access, refusedTier, refusedTier)
                            + " requirement=" + QualityRequirements.test(access, refused, refusedFloor)
                            + " problems=" + qualityProblems.size(),
                    "rank=99 self=99 at_least_self=false requirement=false problems=2");

            ok &= leg(source, "empty_refused",
                    condition(ops, "{\"type\": \"mxt:item_quality\"}") == null
                            && condition(ops, "{\"type\": \"mxt:item_quality\", \"quality\": []}") == null,
                    "neither=" + (condition(ops, "{\"type\": \"mxt:item_quality\"}") == null)
                            + " empty_list=" + (condition(ops, "{\"type\": \"mxt:item_quality\", \"quality\": []}") == null),
                    "neither=true empty_list=true");

            ItemMatcherCondition matcher = matcher(ops, "{\"type\": \"mxt:item_matcher\", \"items\": "
                    + "[{\"type\": \"mxt:quality\", \"items\": \"minecraft:clay_ball\", \"min_quality\": \"mxt_test:normal\"}]}");
            boolean matcherShape = matcher != null && matcher.entries().getFirst() instanceof ItemMatcher.Entry entry && !entry.itemLevel();
            ok &= leg(source, "matcher_entry",
                    matcherShape && matches(matcher, clay) && matches(matcher, clayGraded) && !matches(matcher, cobble) && !matches(matcher, dirt),
                    "shape=" + matcherShape + " clay=" + matches(matcher, clay) + " graded_clay=" + matches(matcher, clayGraded)
                            + " cobble=" + matches(matcher, cobble) + " dirt=" + matches(matcher, dirt),
                    "shape=true clay=true graded_clay=true cobble=false dirt=false");

            ok &= leg(source, "matcher_needs_items",
                    decode(ops, ItemMatcher.Entry.CODEC, qualityEntry("")) == null
                            && decode(ops, ItemMatcher.Entry.CODEC, qualityEntry("[]")) == null,
                    "no_items=" + (decode(ops, ItemMatcher.Entry.CODEC, qualityEntry("")) == null)
                            + " empty_items=" + (decode(ops, ItemMatcher.Entry.CODEC, qualityEntry("[]")) == null),
                    "no_items=true empty_items=true");

            // The ingredient adapter is the one that reaches every recipe, so it is decoded through the same vanilla
            // codec a recipe file uses.
            Ingredient ingredient = decode(ops, Ingredient.CODEC, "{\"neoforge:ingredient_type\": \"mxt:quality\", "
                    + "\"items\": \"minecraft:clay_ball\", \"min_quality\": \"mxt_test:normal\"}");
            boolean ingredientShape = ingredient != null && ingredient.isCustom()
                    && ingredient.getCustomIngredient() instanceof QualityIngredient quality
                    && quality.items().findAny().isPresent() && !quality.isSimple();
            ok &= leg(source, "ingredient",
                    ingredientShape && ingredient.test(clay) && !ingredient.test(cobble) && !ingredient.test(dirt),
                    "shape=" + ingredientShape + " clay=" + (ingredient != null && ingredient.test(clay))
                            + " cobble=" + (ingredient != null && ingredient.test(cobble))
                            + " dirt=" + (ingredient != null && ingredient.test(dirt)),
                    "shape=true clay=true cobble=false dirt=false");

            ok &= leg(source, "ingredient_needs_items",
                    decode(ops, Ingredient.CODEC, "{\"neoforge:ingredient_type\": \"mxt:quality\", \"min_quality\": \"mxt_test:normal\"}") == null,
                    "no_items=" + (decode(ops, Ingredient.CODEC,
                            "{\"neoforge:ingredient_type\": \"mxt:quality\", \"min_quality\": \"mxt_test:normal\"}") == null),
                    "no_items=true");

            // The forging side: a blueprint entry is a NeoForge sized ingredient now, so a requirement can read the
            // stack. The fixtures are read back through the registry, which is also the evidence that both shapes
            // load from a data pack - the two migrated ones and the one written with the mxt:quality ingredient.
            ForgingBlueprint migrated = blueprint(access, "pickaxe");
            ok &= leg(source, "blueprint_migrated",
                    migrated != null && migrated.input().size() == 2 && migrated.input().getFirst().count() == 3
                            && migrated.input().getLast().count() == 2,
                    migrated == null ? "blueprint missing"
                            : "entries=" + migrated.input().size() + " counts=" + migrated.input().getFirst().count()
                            + "/" + migrated.input().getLast().count(),
                    "entries=2 counts=3/2");

            ForgingBlueprint gate = blueprint(access, "quality_gate");
            SizedIngredient entry = gate == null || gate.input().isEmpty() ? null : gate.input().getFirst();
            SimpleContainer forge = new SimpleContainer(ForgingSurface.TOTAL_SLOTS);
            forge.setItem(ForgingSurface.INPUT_START, clay.copy());
            boolean covered = entry != null && ForgingWorkstationService.materialsCovered(forge, gate.input());
            int have = entry == null ? -1 : ForgingWorkstationService.availableCount(forge, entry);
            forge.setItem(ForgingSurface.INPUT_START, cobble.copy());
            ok &= leg(source, "forge_input",
                    entry != null && entry.count() == 1 && entry.ingredient().isCustom() && covered && have == 1
                            && !ForgingWorkstationService.materialsCovered(forge, gate.input()),
                    entry == null ? "blueprint missing"
                            : "custom=" + entry.ingredient().isCustom() + " needs=" + entry.count() + " covered=" + covered
                            + " graded=" + have + " poor_covered=" + ForgingWorkstationService.materialsCovered(forge, gate.input()),
                    "custom=true needs=1 covered=true graded=1 poor_covered=false");

            // The paper the drawing station takes is a tag now, so a pack adds its own paper without code; the
            // built-in blank talisman is its only member, which keeps the old behaviour.
            ItemStack blank = new ItemStack(MxtItems.BLANK_TALISMAN.get());
            ItemStack plainPaper = new ItemStack(Items.PAPER);
            ok &= leg(source, "talisman_paper",
                    blank.is(TalismanDrawingRecipe.paperTag()) && !plainPaper.is(TalismanDrawingRecipe.paperTag()),
                    "blank=" + blank.is(TalismanDrawingRecipe.paperTag()) + " paper=" + plainPaper.is(TalismanDrawingRecipe.paperTag()),
                    "blank=true paper=false");

            // The matcher entry that asks a whole ingredient: it is how a cost - the drawing's paper among them -
            // carries an ingredient type at all.
            ItemMatcherCondition viaIngredient = matcher(ops, "{\"type\": \"mxt:item_matcher\", \"items\": "
                    + "[{\"type\": \"mxt:ingredient\", \"ingredient\": {\"neoforge:ingredient_type\": \"mxt:quality\", "
                    + "\"items\": \"minecraft:clay_ball\", \"min_quality\": \"mxt_test:normal\"}}]}");
            boolean ingredientEntryShape = viaIngredient != null
                    && viaIngredient.entries().getFirst() instanceof IngredientEntry ingredientEntry && !ingredientEntry.itemLevel();
            ok &= leg(source, "ingredient_entry",
                    ingredientEntryShape && matches(viaIngredient, clay) && !matches(viaIngredient, cobble),
                    "shape=" + ingredientEntryShape + " clay=" + matches(viaIngredient, clay)
                            + " cobble=" + matches(viaIngredient, cobble),
                    "shape=true clay=true cobble=false");

            // A formula's own paper field, read back from the recipes the data pack actually loaded: the ward asks
            // for at least mxt_test:normal, so only a graded paper passes there, and the sigil names none and falls
            // back to the tag.
            TalismanDrawingRecipe ward = drawing(source, "drawing_ward");
            TalismanDrawingRecipe sigil = drawing(source, "drawing_sigil");
            Ingredient wardPaper = ward == null ? null : ward.paper().orElse(null);
            ItemStack gradedBlank = named(MxtItems.BLANK_TALISMAN.get(), tier(access, "mxt_test:excellent"));
            ItemStack poorBlank = named(MxtItems.BLANK_TALISMAN.get(), tier(access, "mxt_test:poor"));
            ok &= leg(source, "formula_paper",
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

            // paper_rank is the second variable a settlement formula may read; a name that is neither it nor
            // percentage nor a registry variable is still refused.
            Object ranked = decode(ops, TalismanDrawingRecipe.Grade.CODEC,
                    "{\"min_completion\": 0.5, \"max_damage\": \"paper_rank * 100\"}");
            Object unknownRank = decode(ops, TalismanDrawingRecipe.Grade.CODEC,
                    "{\"min_completion\": 0.5, \"max_damage\": \"paper_tier * 100\"}");
            ok &= leg(source, "paper_rank_variable", ranked != null && unknownRank == null,
                    "paper_rank=" + (ranked != null) + " unknown=" + (unknownRank == null),
                    "paper_rank=true unknown=true");

            // Without registry access both halves that need no ladder still answer - the data table for a plain stack
            // and the component for a graded one, which is what a matching path on a pure client has - while a
            // minimum cannot be answered at all, and answers no.
            ItemQualityCondition gradedMembership = quality(ops, "{\"type\": \"mxt:item_quality\", \"quality\": [\"mxt_test:excellent\"]}");
            QualityRequirement floor = new QualityRequirement(List.of(), Optional.of(tier(access, "mxt_test:excellent")));
            ok &= leg(source, "no_access",
                    membership != null && minimum != null && gradedMembership != null
                            && QualityRequirements.test(null, clay, membership.requirement())
                            && QualityRequirements.test(null, clayGraded, gradedMembership.requirement())
                            && !QualityRequirements.test(null, clayGraded, membership.requirement())
                            && !QualityRequirements.test(null, clay, minimum.requirement())
                            && !QualityRequirements.test(null, clayGraded, floor)
                            && QualityRequirements.test(null, dirt, QualityRequirement.of(List.of())),
                    "table_tier=" + (membership != null && QualityRequirements.test(null, clay, membership.requirement()))
                            + " component_tier=" + (gradedMembership != null && QualityRequirements.test(null, clayGraded, gradedMembership.requirement()))
                            + " min=" + (minimum != null && QualityRequirements.test(null, clay, minimum.requirement()))
                            + " nothing_asked=" + QualityRequirements.test(null, dirt, QualityRequirement.of(List.of())),
                    "table_tier=true component_tier=true min=false nothing_asked=true");

            // The table takes a priority the way every other item table does, and the bare tier id is still the whole
            // value: gravel is named by a tag at priority 0 and by itself at 5, flint the other way round, so both
            // directions of the merger are exercised by the fixtures.
            ItemStack gravel = new ItemStack(Items.GRAVEL);
            ItemStack flint = new ItemStack(Items.FLINT);
            ok &= leg(source, "default_quality_priority",
                    read(access, gravel).equals("mxt_test:normal") && read(access, flint).equals("mxt_test:excellent"),
                    "gravel=" + read(access, gravel) + " flint=" + read(access, flint),
                    "gravel=mxt_test:normal flint=mxt_test:excellent");

            // The catalogue's own order: the tooltip-order tag first, in the order it writes, then every remaining
            // tier once. The picker's quality page is that same list, which is what gives the tag its consumer.
            List<Holder<ItemQuality>> catalogue = QualityService.ordered(access);
            List<String> order = catalogue.stream().map(HolderHelper::id).map(Identifier::toString).toList();
            long tiers = MxtDatapackRegistries.holders(access, MxtResourceKeys.ITEM_QUALITY).count();
            List<ItemPickerManager.PickerItem> rows = qualityRows(access);
            String firstRow = rows.isEmpty() ? "none" : rows.getFirst().names().getLast().getString();
            ok &= leg(source, "tooltip_order",
                    order.size() == tiers && order.getFirst().equals("mxt_test:excellent")
                            && order.get(1).equals("mxt_test:normal") && order.get(2).equals("mxt_test:poor"),
                    "size=" + order.size() + " first=" + order.getFirst() + " second=" + order.get(1) + " third=" + order.get(2),
                    "size=" + tiers + " first=mxt_test:excellent second=mxt_test:normal third=mxt_test:poor");
            ok &= leg(source, "picker_catalogue",
                    rows.size() == order.size() && firstRow.equals(order.getFirst())
                            && ItemPickerManager.category(Identifier.fromNamespaceAndPath("mxt", "default_quality")).isPresent()
                            && ItemPickerManager.category(Identifier.fromNamespaceAndPath("mxt", "heat_source")).isPresent(),
                    "rows=" + rows.size() + " first=" + firstRow
                            + " default_quality=" + ItemPickerManager.category(Identifier.fromNamespaceAndPath("mxt", "default_quality")).isPresent()
                            + " heat_source=" + ItemPickerManager.category(Identifier.fromNamespaceAndPath("mxt", "heat_source")).isPresent(),
                    "rows=" + order.size() + " first=" + order.getFirst() + " default_quality=true heat_source=true");

            // The script-side entry points, called the way a script's binding calls them: a requirement decoded from
            // the same JSON a pack writes, and a tier id, both answered by the same single point. The test server
            // loads no KubeJS, so what is checked here is the validated operation the binding delegates to.
            FakePlayer scripted = new FakePlayer(source.getLevel(), new GameProfile(UUID.randomUUID(), "MxtQualityProbe"));
            QualityRequirement fromJson = MxtKubeJsDataCodec.decodeCached(QualityRequirement.CODEC.codec(),
                    JsonParser.parseString("{\"min_quality\": \"mxt_test:normal\"}"), access);
            Identifier normalId = Identifier.parse("mxt_test:normal");
            ok &= leg(source, "kubejs_requirement",
                    fromJson != null && MxtKubeJsApi.satisfiesQualityRequirement(scripted, clay, fromJson)
                            && !MxtKubeJsApi.satisfiesQualityRequirement(scripted, cobble, fromJson)
                            && MxtKubeJsApi.qualityAtLeast(scripted, clay, normalId)
                            && !MxtKubeJsApi.qualityAtLeast(scripted, cobble, normalId)
                            && !MxtKubeJsApi.qualityAtLeast(scripted, kiln, normalId),
                    "decoded=" + (fromJson != null)
                            + " normal=" + (fromJson != null && MxtKubeJsApi.satisfiesQualityRequirement(scripted, clay, fromJson))
                            + " poor=" + (fromJson != null && MxtKubeJsApi.satisfiesQualityRequirement(scripted, cobble, fromJson))
                            + " at_least_normal=" + MxtKubeJsApi.qualityAtLeast(scripted, clay, normalId)
                            + " poor_at_least=" + MxtKubeJsApi.qualityAtLeast(scripted, cobble, normalId)
                            + " cross_ladder=" + MxtKubeJsApi.qualityAtLeast(scripted, kiln, normalId),
                    "decoded=true normal=true poor=false at_least_normal=true poor_at_least=false cross_ladder=false");

            // The alchemy side asks about its machine rather than its slots: the loaded formula names the core tier
            // it needs, the rank is a plain value its formulas can read, and a written but empty requirement is
            // refused instead of silently always passing.
            String recipeBase = "{\"main_requirements\": {\"mxt_test:herb/main\": 1}, \"catalyst_requirement\": 1, "
                    + "\"target_temperature\": 50, \"duration\": 4, "
                    + "\"success_outputs\": [{\"id\": \"minecraft:stick\", \"count\": 1}], ";
            AlchemyRecipe formula = alchemy(source, "hot");
            Object emptyGate = decode(ops, AlchemyRecipe.CODEC.codec(), recipeBase + "\"furnace_quality\": {}}");
            Object gradedGate = decode(ops, AlchemyRecipe.CODEC.codec(),
                    recipeBase + "\"furnace_quality\": {\"min_quality\": \"mxt_test:alchemy/kiln_a\"}}");
            boolean gateFloor = formula != null && formula.furnaceQuality()
                    .flatMap(requirement -> requirement.minQuality())
                    .map(holder -> HolderHelper.id(holder).toString().equals("mxt_test:alchemy/kiln_a")).orElse(false);
            double entryDuration = formula == null ? -1.0D
                    : formula.duration().evaluate(FormulaContext.EMPTY.with(AlchemyRecipe.FURNACE_RANK, 0.0D));
            double upperDuration = formula == null ? -1.0D
                    : formula.duration().evaluate(FormulaContext.EMPTY.with(AlchemyRecipe.FURNACE_RANK, 1.0D));
            ok &= leg(source, "alchemy_core_gate",
                    gateFloor && emptyGate == null && gradedGate != null
                            && entryDuration == 4.0D && upperDuration == 5.0D,
                    "floor=" + gateFloor + " empty_refused=" + (emptyGate == null) + " graded=" + (gradedGate != null)
                            + " duration_at_0=" + entryDuration + " duration_at_1=" + upperDuration,
                    "floor=true empty_refused=true graded=true duration_at_0=4.0 duration_at_1=5.0");

            // The input side asks the same question of every non-empty slot. The gated formula is read back from the
            // recipe manager, so what a pack writes is what the runtime sees.
            Object emptyInput = decode(ops, AlchemyRecipe.CODEC.codec(), recipeBase + "\"input_quality\": {}}");
            Object gradedInput = decode(ops, AlchemyRecipe.CODEC.codec(),
                    recipeBase + "\"input_quality\": {\"min_quality\": \"mxt_test:alchemy/kiln_a\"}}");
            AlchemyRecipe gated = alchemy(source, "zz_input_gate");
            boolean inputFloor = gated != null && gated.inputQuality()
                    .flatMap(requirement -> requirement.minQuality())
                    .map(holder -> HolderHelper.id(holder).toString().equals("mxt_test:alchemy/kiln_a")).orElse(false);
            ok &= leg(source, "alchemy_input_gate",
                    inputFloor && emptyInput == null && gradedInput != null,
                    "floor=" + inputFloor + " empty_refused=" + (emptyInput == null) + " graded=" + (gradedInput != null),
                    "floor=true empty_refused=true graded=true");

            // Both gates again from a stub controller and a fake player: those legs need no placed multiblock, so
            // they run wherever this probe does.
            ok &= AlchemyGateProbes.run(source);
        } catch (RuntimeException failure) {
            ok = false;
            source.sendFailure(Component.literal("quality probe: " + failure.getClass().getSimpleName() + " " + failure.getMessage()));
        }
        if (ok) source.sendSuccess(() -> Component.literal("quality probe: OK"), false);
        else source.sendFailure(Component.literal("quality probe: MISMATCH"));
        return ok ? 1 : 0;
    }

    // The picker's own provider, asked the way its screen asks it: the client's registry access is the only argument
    // that differs there.
    private static List<ItemPickerManager.PickerItem> qualityRows(RegistryAccess access) {
        PickerCategory category = ItemPickerManager.category(MxtResourceKeys.ITEM_QUALITY.identifier()).orElse(null);
        ItemPickerManager.ItemProvider provider = category == null ? null : ItemPickerManager.provider(category);
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
        return com.iafenvoy.mxt.data.condition.ItemCondition.CODEC.parse(ops, JsonParser.parseString(json))
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
    private static TalismanDrawingRecipe drawing(CommandSourceStack source, String path) {
        return source.getServer().getRecipeManager()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("mxt_test", "talisman/" + path)))
                .filter(holder -> holder.value() instanceof TalismanDrawingRecipe)
                .map(holder -> (TalismanDrawingRecipe) holder.value())
                .orElse(null);
    }

    private static AlchemyRecipe alchemy(CommandSourceStack source, String path) {
        return source.getServer().getRecipeManager()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("mxt_test", "alchemy/" + path)))
                .filter(holder -> holder.value() instanceof AlchemyRecipe)
                .map(holder -> (AlchemyRecipe) holder.value())
                .orElse(null);
    }

    private static boolean leg(CommandSourceStack source, String name, boolean ok, String actual, String expected) {
        String line = "quality probe: " + name + " actual=" + actual + " expected=" + expected + (ok ? " OK" : " MISMATCH");
        if (ok) source.sendSuccess(() -> Component.literal(line), false);
        else source.sendFailure(Component.literal(line));
        return ok;
    }
}
