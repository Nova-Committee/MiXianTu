package com.iafenvoy.mxt.probe.ability;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.CreativeTabHelper;
import com.iafenvoy.mxt.data.ItemPickerManager;
import com.iafenvoy.mxt.data.ItemPickerManager.PickerItem;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;

import java.util.List;
import java.util.Set;

/**
 * {@code CreativeTabHelper}'s queries against the real packs. The aura category is the fixture because every one of
 * its rows is the same stand-in item, which is what separates the row view from the stack view.
 */
public final class PickerProbes {
    private static final String MOD = "mxt_test";
    private static final String NO_SUCH_PACK = "mxt_test_no_such_pack";

    private PickerProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("ability/picker/category", PickerProbes::category),
                Probe.of("ability/picker/rows", PickerProbes::rows),
                Probe.of("ability/picker/modid", PickerProbes::modid),
                Probe.of("ability/picker/predicate", PickerProbes::predicate),
                Probe.of("ability/picker/unknown_category", PickerProbes::unknownCategory),
                Probe.of("ability/picker/distinct", PickerProbes::distinctStacks),
                Probe.of("ability/picker/copy", PickerProbes::copy),
                Probe.of("ability/picker/modid_everywhere", PickerProbes::modidEverywhere));
    }

    private static void category(ProbeContext context) {
        leg(context, "category",
                ItemPickerManager.category(MxtResourceKeys.AURA.identifier()).isPresent()
                        && ItemPickerManager.category(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "not_a_registry")).isEmpty(),
                "aura=" + ItemPickerManager.category(MxtResourceKeys.AURA.identifier()).isPresent()
                        + " unknown=" + ItemPickerManager.category(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "not_a_registry")).isPresent(),
                "aura=true unknown=false");
    }

    private static void rows(ProbeContext context) {
        RegistryAccess access = context.registries();
        List<PickerItem> rows = CreativeTabHelper.itemsOf(access, MxtResourceKeys.AURA);
        leg(context, "rows", rows.size() > 1 && rows.stream().noneMatch(row -> row.stack().isEmpty()),
                "rows=" + rows.size() + " empty=" + rows.stream().filter(row -> row.stack().isEmpty()).count(),
                "rows>1 empty=0");
    }

    private static void modid(ProbeContext context) {
        RegistryAccess access = context.registries();
        List<PickerItem> rows = CreativeTabHelper.itemsOf(access, MxtResourceKeys.AURA);
        List<PickerItem> mine = CreativeTabHelper.itemsOfMod(access, MxtResourceKeys.AURA, MiXianTu.MOD_ID);
        List<PickerItem> test = CreativeTabHelper.itemsOfMod(access, MxtResourceKeys.AURA, MOD);
        List<PickerItem> absent = CreativeTabHelper.itemsOfMod(access, MxtResourceKeys.AURA, NO_SUCH_PACK);
        Set<ItemStack> all = distinct(rows);
        leg(context, "modid",
                !mine.isEmpty() && !test.isEmpty() && mine.size() < rows.size() && test.size() < rows.size()
                        && all.containsAll(distinct(mine)) && all.containsAll(distinct(test)) && absent.isEmpty(),
                "mxt=" + mine.size() + " mxt_test=" + test.size() + " absent=" + absent.size() + " rows=" + rows.size(),
                "both>0 both<" + rows.size() + " absent=0");
    }

    private static void predicate(ProbeContext context) {
        RegistryAccess access = context.registries();
        List<PickerItem> rows = CreativeTabHelper.itemsOf(access, MxtResourceKeys.AURA);
        List<PickerItem> test = CreativeTabHelper.itemsOfMod(access, MxtResourceKeys.AURA, MOD);
        leg(context, "predicate",
                CreativeTabHelper.itemsOf(access, MxtResourceKeys.AURA, _ -> true).size() == rows.size()
                        && CreativeTabHelper.itemsOf(access, MxtResourceKeys.AURA, _ -> false).isEmpty()
                        && CreativeTabHelper.itemsOf(access, MxtResourceKeys.AURA, id -> MOD.equals(namespace(id))).size() == test.size(),
                "all=" + CreativeTabHelper.itemsOf(access, MxtResourceKeys.AURA, _ -> true).size()
                        + " none=" + CreativeTabHelper.itemsOf(access, MxtResourceKeys.AURA, _ -> false).size()
                        + " byId=" + CreativeTabHelper.itemsOf(access, MxtResourceKeys.AURA, id -> MOD.equals(namespace(id))).size(),
                "all=" + rows.size() + " none=0 byId=" + test.size());
    }

    private static void unknownCategory(ProbeContext context) {
        RegistryAccess access = context.registries();
        leg(context, "unknown_category",
                CreativeTabHelper.itemsOf(access, MxtResourceKeys.ELEMENT).isEmpty()
                        && CreativeTabHelper.stacksOf(access, MxtResourceKeys.ELEMENT).isEmpty(),
                "rows=" + CreativeTabHelper.itemsOf(access, MxtResourceKeys.ELEMENT).size()
                        + " stacks=" + CreativeTabHelper.stacksOf(access, MxtResourceKeys.ELEMENT).size(),
                "rows=0 stacks=0");
    }

    // Distinct is the whole reason a stack view exists: the tab's own `accept` refuses a repeated stack.
    private static void distinctStacks(ProbeContext context) {
        RegistryAccess access = context.registries();
        List<PickerItem> rows = CreativeTabHelper.itemsOf(access, MxtResourceKeys.AURA);
        Set<ItemStack> all = distinct(rows);
        List<ItemStack> stacks = CreativeTabHelper.stacksOf(access, MxtResourceKeys.AURA);
        leg(context, "distinct", !stacks.isEmpty() && stacks.size() == all.size() && stacks.size() < rows.size(),
                "stacks=" + stacks.size() + " distinctRows=" + all.size() + " rows=" + rows.size(),
                "stacks=1 distinctRows=1 rows=" + rows.size());
    }

    private static void copy(ProbeContext context) {
        RegistryAccess access = context.registries();
        List<PickerItem> rows = CreativeTabHelper.itemsOf(access, MxtResourceKeys.AURA);
        List<ItemStack> stacks = CreativeTabHelper.stacksOf(access, MxtResourceKeys.AURA);
        int count = stacks.getFirst().getCount();
        stacks.getFirst().setCount(count + 1);
        leg(context, "copy",
                CreativeTabHelper.stacksOf(access, MxtResourceKeys.AURA).getFirst().getCount() == count
                        && rows.getFirst().stack().getCount() == count,
                "again=" + CreativeTabHelper.stacksOf(access, MxtResourceKeys.AURA).getFirst().getCount()
                        + " row=" + rows.getFirst().stack().getCount(),
                "again=" + count + " row=" + count);
    }

    // The cross-category query is what fills a mod's own tab: it must reach past one category, and the stand-ins of
    // the aura categories duplicate each other across categories, so the stack view collapses them.
    private static void modidEverywhere(ProbeContext context) {
        RegistryAccess access = context.registries();
        List<PickerItem> test = CreativeTabHelper.itemsOfMod(access, MxtResourceKeys.AURA, MOD);
        List<PickerItem> everywhere = CreativeTabHelper.itemsOfMod(access, MOD);
        List<ItemStack> everywhereStacks = CreativeTabHelper.stacksOfMod(access, MOD);
        Set<ItemStack> everywhereDistinct = distinct(everywhere);
        leg(context, "modid_everywhere",
                everywhere.size() > test.size() && everywhereStacks.size() == everywhereDistinct.size()
                        && everywhereStacks.size() < everywhere.size()
                        && CreativeTabHelper.itemsOfMod(access, NO_SUCH_PACK).isEmpty()
                        && CreativeTabHelper.stacksOfMod(access, NO_SUCH_PACK).isEmpty(),
                "rows=" + everywhere.size() + " distinct=" + everywhereDistinct.size() + " stacks=" + everywhereStacks.size(),
                "rows>" + test.size() + " stacks=distinct<rows absent=0");
    }

    private static Set<ItemStack> distinct(List<PickerItem> items) {
        Set<ItemStack> stacks = ItemStackLinkedSet.createTypeAndComponentsSet();
        for (PickerItem item : items) stacks.add(item.stack());
        return stacks;
    }

    private static String namespace(Identifier id) {
        return id == null ? "" : id.getNamespace();
    }

    private static void leg(ProbeContext context, String name, boolean ok, String actual, String expected) {
        String line = "picker probe: " + name + " actual=" + actual + " expected=" + expected + (ok ? " OK" : " MISMATCH");
        if (ok) context.note(line);
        else context.fail(line);
    }
}
