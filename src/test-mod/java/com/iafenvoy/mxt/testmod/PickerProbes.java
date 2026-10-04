package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.data.CreativeTabHelper;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.screen.picker.ItemPickerManager;
import com.iafenvoy.mxt.screen.picker.ItemPickerManager.PickerItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;

import java.util.List;
import java.util.Set;

/**
 * {@code CreativeTabHelper}'s queries against the real packs: the aura category is the fixture because every one
 * of its rows is the same stand-in item, which is what separates the row view from the stack view. Needs no player -
 * it only reads the level's registry access - so the probe also runs from a server console.
 */
public final class PickerProbes {
    private static final String MOD = "mxt_test";
    private static final String NO_SUCH_PACK = "mxt_test_no_such_pack";

    private PickerProbes() {
    }

    public static int run(CommandSourceStack source) {
        RegistryAccess access = source.getLevel().registryAccess();
        boolean ok = true;
        try {
            List<PickerItem> rows = CreativeTabHelper.itemsOf(access, MxtResourceKeys.AURA);
            List<PickerItem> mine = CreativeTabHelper.itemsOfMod(access, MxtResourceKeys.AURA, "mxt");
            List<PickerItem> test = CreativeTabHelper.itemsOfMod(access, MxtResourceKeys.AURA, MOD);
            List<PickerItem> absent = CreativeTabHelper.itemsOfMod(access, MxtResourceKeys.AURA, NO_SUCH_PACK);
            List<ItemStack> stacks = CreativeTabHelper.stacksOf(access, MxtResourceKeys.AURA);

            ok &= leg(source, "category",
                    ItemPickerManager.category(MxtResourceKeys.AURA.identifier()).isPresent()
                            && ItemPickerManager.category(Identifier.fromNamespaceAndPath("mxt", "not_a_registry")).isEmpty(),
                    "aura=" + ItemPickerManager.category(MxtResourceKeys.AURA.identifier()).isPresent()
                            + " unknown=" + ItemPickerManager.category(Identifier.fromNamespaceAndPath("mxt", "not_a_registry")).isPresent(),
                    "aura=true unknown=false");

            ok &= leg(source, "rows", rows.size() > 1 && rows.stream().noneMatch(row -> row.stack().isEmpty()),
                    "rows=" + rows.size() + " empty=" + rows.stream().filter(row -> row.stack().isEmpty()).count(),
                    "rows>1 empty=0");

            Set<ItemStack> all = distinct(rows);
            ok &= leg(source, "modid",
                    !mine.isEmpty() && !test.isEmpty() && mine.size() < rows.size() && test.size() < rows.size()
                            && all.containsAll(distinct(mine)) && all.containsAll(distinct(test)) && absent.isEmpty(),
                    "mxt=" + mine.size() + " mxt_test=" + test.size() + " absent=" + absent.size() + " rows=" + rows.size(),
                    "both>0 both<" + rows.size() + " absent=0");

            ok &= leg(source, "predicate",
                    CreativeTabHelper.itemsOf(access, MxtResourceKeys.AURA, _ -> true).size() == rows.size()
                            && CreativeTabHelper.itemsOf(access, MxtResourceKeys.AURA, _ -> false).isEmpty()
                            && CreativeTabHelper.itemsOf(access, MxtResourceKeys.AURA, id -> MOD.equals(namespace(id))).size() == test.size(),
                    "all=" + CreativeTabHelper.itemsOf(access, MxtResourceKeys.AURA, _ -> true).size()
                            + " none=" + CreativeTabHelper.itemsOf(access, MxtResourceKeys.AURA, _ -> false).size()
                            + " byId=" + CreativeTabHelper.itemsOf(access, MxtResourceKeys.AURA, id -> MOD.equals(namespace(id))).size(),
                    "all=" + rows.size() + " none=0 byId=" + test.size());

            ok &= leg(source, "unknown_category",
                    CreativeTabHelper.itemsOf(access, MxtResourceKeys.ELEMENT).isEmpty()
                            && CreativeTabHelper.stacksOf(access, MxtResourceKeys.ELEMENT).isEmpty(),
                    "rows=" + CreativeTabHelper.itemsOf(access, MxtResourceKeys.ELEMENT).size()
                            + " stacks=" + CreativeTabHelper.stacksOf(access, MxtResourceKeys.ELEMENT).size(),
                    "rows=0 stacks=0");

            // Distinct is the whole reason a stack view exists: the tab's own `accept` refuses a repeated stack.
            ok &= leg(source, "distinct", !stacks.isEmpty() && stacks.size() == all.size() && stacks.size() < rows.size(),
                    "stacks=" + stacks.size() + " distinctRows=" + all.size() + " rows=" + rows.size(),
                    "stacks=1 distinctRows=1 rows=" + rows.size());

            int count = stacks.getFirst().getCount();
            stacks.getFirst().setCount(count + 1);
            ok &= leg(source, "copy",
                    CreativeTabHelper.stacksOf(access, MxtResourceKeys.AURA).getFirst().getCount() == count
                            && rows.getFirst().stack().getCount() == count,
                    "again=" + CreativeTabHelper.stacksOf(access, MxtResourceKeys.AURA).getFirst().getCount()
                            + " row=" + rows.getFirst().stack().getCount(),
                    "again=" + count + " row=" + count);

            // The cross-category query is what fills a mod's own tab: it must reach past one category, and the
            // stand-ins of the aura categories duplicate each other across categories, so the stack view collapses them.
            List<PickerItem> everywhere = CreativeTabHelper.itemsOfMod(access, MOD);
            List<ItemStack> everywhereStacks = CreativeTabHelper.stacksOfMod(access, MOD);
            Set<ItemStack> everywhereDistinct = distinct(everywhere);
            ok &= leg(source, "modid_everywhere",
                    everywhere.size() > test.size() && everywhereStacks.size() == everywhereDistinct.size()
                            && everywhereStacks.size() < everywhere.size()
                            && CreativeTabHelper.itemsOfMod(access, NO_SUCH_PACK).isEmpty()
                            && CreativeTabHelper.stacksOfMod(access, NO_SUCH_PACK).isEmpty(),
                    "rows=" + everywhere.size() + " distinct=" + everywhereDistinct.size() + " stacks=" + everywhereStacks.size(),
                    "rows>" + test.size() + " stacks=distinct<rows absent=0");
        } catch (RuntimeException failure) {
            ok = false;
            source.sendFailure(Component.literal("picker probe: " + failure.getClass().getSimpleName() + " " + failure.getMessage()));
        }
        if (ok) source.sendSuccess(() -> Component.literal("picker probe: OK"), false);
        else source.sendFailure(Component.literal("picker probe: MISMATCH"));
        return ok ? 1 : 0;
    }

    private static Set<ItemStack> distinct(List<PickerItem> items) {
        Set<ItemStack> stacks = ItemStackLinkedSet.createTypeAndComponentsSet();
        for (PickerItem item : items) stacks.add(item.stack());
        return stacks;
    }

    private static String namespace(Identifier id) {
        return id == null ? "" : id.getNamespace();
    }

    private static boolean leg(CommandSourceStack source, String name, boolean ok, String actual, String expected) {
        String line = "picker probe: " + name + " actual=" + actual + " expected=" + expected + (ok ? " OK" : " MISMATCH");
        if (ok) source.sendSuccess(() -> Component.literal(line), false);
        else source.sendFailure(Component.literal(line));
        return ok;
    }
}
