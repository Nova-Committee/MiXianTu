package com.iafenvoy.mxt.probe.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.saturn.compat.kubejs.codec.SaturnKubeJsDataCodec;
import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.condition.builtin.item.ItemMatcherCondition;
import com.iafenvoy.mxt.data.condition.builtin.item.ItemQualityCondition;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.QualityIngredient;
import com.iafenvoy.mxt.data.quality.QualityLadders;
import com.iafenvoy.mxt.data.quality.QualityRequirement;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.*;
import com.iafenvoy.mxt.registry.cache.RegistryCaches;
import com.iafenvoy.mxt.runtime.alchemy.*;
import com.iafenvoy.mxt.runtime.item.QualityRequirements;
import com.iafenvoy.mxt.runtime.item.QualityService;
import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.saturn.util.matcher.ItemMatcher;
import com.iafenvoy.saturn.util.matcher.builtin.IngredientEntry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;
import java.util.Optional;

public final class QualityProbes {
    private QualityProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("item/quality/table", QualityProbes::table),
                Probe.of("item/quality/tag_key", QualityProbes::tagKey),
                Probe.of("item/quality/membership", QualityProbes::membership),
                Probe.of("item/quality/min_same_chain", QualityProbes::minSameChain),
                Probe.of("item/quality/cross_chain", QualityProbes::crossChain),
                Probe.of("item/quality/compare_refused_ladder", QualityProbes::compareRefusedLadder),
                Probe.of("item/quality/domain_gate", QualityProbes::domainGate),
                Probe.of("item/quality/matcher_entry", QualityProbes::matcherEntry),
                Probe.of("item/quality/ingredient", QualityProbes::ingredient),
                Probe.of("item/quality/ingredient_entry", QualityProbes::ingredientEntry),
                Probe.of("item/quality/tier_source_layers", QualityProbes::tierSourceLayers),
                Probe.of("item/quality/empty_requirement", QualityProbes::emptyRequirement),
                Probe.of("item/quality/default_quality_priority", QualityProbes::defaultQualityPriority),
                Probe.of("item/quality/kubejs_requirement", QualityProbes::kubejsRequirement));
    }

    private static void table(ProbeContext context) {
        RegistryAccess access = context.registries();
        ItemStack clay = new ItemStack(Items.CLAY_BALL);         // table: normal
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);     // table: poor
        leg(context, "table", read(access, clay).equals("mxt_test:normal") && read(access, cobble).equals("mxt_test:poor"),
                "clay=" + read(access, clay) + " cobble=" + read(access, cobble), "clay=normal cobble=poor");
    }

    private static void tagKey(ProbeContext context) {
        RegistryAccess access = context.registries();
        ItemStack star = new ItemStack(Items.NETHER_STAR);       // table: poor, written as a tag key
        ItemStack dirt = new ItemStack(Items.DIRT);              // no tier anywhere
        leg(context, "tag_key", read(access, star).equals("mxt_test:poor") && read(access, dirt).equals("none"),
                "tagged=" + read(access, star) + " untagged=" + read(access, dirt), "tagged=poor untagged=none");
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

    private static void domainGate(ProbeContext context) {
        RegistryAccess access = context.registries();
        Holder<ItemQuality> inheritLow = tier(access, "mxt_test:inherit_low");
        Holder<ItemQuality> inheritHigh = tier(access, "mxt_test:inherit_high");
        Holder<ItemQuality> conflictLow = tier(access, "mxt_test:conflict_low");
        Holder<ItemQuality> conflictTop = tier(access, "mxt_test:conflict_top");
        leg(context, "domain_gate_inherited_name",
                QualityLadders.compare(inheritHigh, inheritLow).orElse(99) == 1,
                "low_rank=" + QualityLadders.rank(inheritLow).orElse(-1)
                        + " high_rank=" + QualityLadders.rank(inheritHigh).orElse(-1)
                        + " step=" + QualityLadders.compare(inheritHigh, inheritLow).orElse(99)
                        + " high_name=" + QualityLadders.name(inheritHigh).map(Identifier::toString).orElse("none"),
                "low_rank=0 high_rank=1 step=1 high_name=mxt_test:inherited");
        // A conflicting name is a domain failure, not a per-link one: the whole line is dropped, so no tier of it has
        // a next step and an upgrade cannot climb out of the group the pack actually declared.
        boolean conflictPresent = QualityLadders.cache().contains(conflictLow);
        leg(context, "domain_gate_conflicting_name",
                !conflictPresent && !QualityLadders.cache().contains(conflictTop)
                        && QualityLadders.cache().next(conflictLow).isEmpty()
                        && QualityLadders.cache().next(conflictTop).isEmpty(),
                "low_indexed=" + conflictPresent + " top_indexed=" + QualityLadders.cache().contains(conflictTop)
                        + " low_next=" + QualityLadders.cache().next(conflictLow).map(HolderHelper::id).map(Identifier::toString).orElse("none"),
                "low_indexed=false top_indexed=false low_next=none");
        // A realm line that climbs from one aura into another is not one ladder either.
        leg(context, "domain_gate_realm_aura",
                QualityLadders.cache().size() > 0 && RegistryCaches.REALM_CHAIN.chainOf(
                        context.require(MxtResourceKeys.REALM_STAGE, "cross_aura_low")).isEmpty()
                        && RegistryCaches.REALM_CHAIN.chainOf(context.require(MxtResourceKeys.REALM_STAGE,
                        "cross_aura_high")).isEmpty(),
                "low_indexed=" + RegistryCaches.REALM_CHAIN.chainOf(context.require(MxtResourceKeys.REALM_STAGE,
                        "cross_aura_low")).isPresent()
                        + " high_indexed=" + RegistryCaches.REALM_CHAIN.chainOf(context.require(MxtResourceKeys.REALM_STAGE,
                        "cross_aura_high")).isPresent(),
                "low_indexed=false high_indexed=false");
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

    private static void defaultQualityPriority(ProbeContext context) {
        RegistryAccess access = context.registries();
        ItemStack gravel = new ItemStack(Items.GRAVEL);
        ItemStack flint = new ItemStack(Items.FLINT);
        leg(context, "default_quality_priority",
                read(access, gravel).equals("mxt_test:normal") && read(access, flint).equals("mxt_test:excellent"),
                "gravel=" + read(access, gravel) + " flint=" + read(access, flint),
                "gravel=mxt_test:normal flint=mxt_test:excellent");
    }

    private static void kubejsRequirement(ProbeContext context) {
        RegistryAccess access = context.registries();
        ItemStack clay = new ItemStack(Items.CLAY_BALL);         // table: normal
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);     // table: poor
        ItemStack kiln = named(Items.DIRT, tier(access, "mxt_test:alchemy/kiln_a"));
        QualityRequirement fromJson = SaturnKubeJsDataCodec.decodeCached(QualityRequirement.CODEC.codec(),
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

    private static RegistryOps<JsonElement> ops(ProbeContext context) {
        return RegistryOps.create(JsonOps.INSTANCE, context.registries());
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

    private static boolean ask(RegistryAccess access, QualityRequirement requirement, ItemStack stack) {
        return QualityRequirements.test(access, stack, requirement);
    }

    private static boolean matches(ItemMatcherCondition condition, ItemStack stack) {
        return condition != null && ItemMatcher.matches(condition, stack);
    }

    private static ItemStack named(Item item, Holder<ItemQuality> quality) {
        ItemStack stack = new ItemStack(item);
        stack.set(MxtDataComponents.QUALITY, quality);
        return stack;
    }

    private static String read(RegistryAccess access, ItemStack stack) {
        return QualityService.find(access, stack).map(HolderHelper::id).map(Identifier::toString).orElse("none");
    }

    private static Holder<ItemQuality> tier(RegistryAccess access, String id) {
        return MxtDatapackRegistries.holder(access, MxtResourceKeys.ITEM_QUALITY, Identifier.parse(id))
                .orElseThrow();
    }

    private static boolean leg(ProbeContext context, String name, boolean ok, String actual, String expected) {
        String line = "quality probe: " + name + " actual=" + actual + " expected=" + expected + (ok ? " OK" : " MISMATCH");
        if (ok) context.note(line);
        else context.fail(line);
        return ok;
    }
}
