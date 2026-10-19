package com.iafenvoy.mxt.probe.ability;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.PickerCategories;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.picker.PickerCatalogue;
import com.iafenvoy.picker.PickerContents;
import com.iafenvoy.picker.api.PickerRow;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * What this mod hands the item picker: the picker itself owns the screen, the command and the reading, so what is
 * checked here is that {@link PickerCategories}' registration is what the picker's own reader sees - our categories
 * exist in its catalogue, our rows come out of it, and a row that carries a definition really carries it.
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
                Probe.of("ability/picker/component", PickerProbes::component),
                Probe.of("ability/picker/modid", PickerProbes::modid));
    }

    // A category is our registry key as the picker's catalogue knows it, and an id nothing registered is not one.
    private static void category(ProbeContext context) {
        RegistryAccess access = context.registries();
        boolean aura = PickerCatalogue.category(MxtResourceKeys.AURA.identifier()).isPresent();
        boolean unknown = PickerCatalogue.category(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "not_a_registry")).isEmpty();
        boolean empty = PickerContents.itemsOf(access, MxtResourceKeys.ELEMENT).isEmpty();
        leg(context, "category", aura && unknown && empty,
                "aura=" + aura + " unknown=" + unknown + " unregistered_is_empty=" + empty,
                "aura=true unknown=true unregistered_is_empty=true");
    }

    // One row per definition of the aura registry: each is the same stand-in item, which is why the row count is the
    // registry's size rather than a stack count.
    private static void rows(ProbeContext context) {
        RegistryAccess access = context.registries();
        List<PickerRow> rows = PickerContents.itemsOf(access, MxtResourceKeys.AURA);
        long definitions = MxtDatapackRegistries.holders(access, MxtResourceKeys.AURA).count();
        leg(context, "rows",
                rows.size() == definitions && definitions > 1 && rows.stream().noneMatch(row -> row.stack().isEmpty()),
                "rows=" + rows.size() + " definitions=" + definitions
                        + " empty=" + rows.stream().filter(row -> row.stack().isEmpty()).count(),
                "rows=" + definitions + " empty=0");
    }

    // A row for a definition that is carried by a dedicated item carries that definition: taking it out is what
    // grants the spirit root, so the component has to be on the stack.
    private static void component(ProbeContext context) {
        RegistryAccess access = context.registries();
        Holder<SpiritRoot> root = context.require(MxtResourceKeys.SPIRIT_ROOT, ProbeDefinitions.ROOT);
        long carrying = PickerContents.itemsOf(access, MxtResourceKeys.SPIRIT_ROOT).stream()
                .filter(row -> root.equals(row.stack().get(MxtDataComponents.SPIRIT_ROOT.get())))
                .count();
        leg(context, "component", carrying == 1, "carrying=" + carrying, "carrying=1");
    }

    // The namespace query a pack's own creative tab uses: our rows and the test pack's rows partition the category,
    // and a namespace nobody shipped is empty.
    private static void modid(ProbeContext context) {
        RegistryAccess access = context.registries();
        List<PickerRow> rows = PickerContents.itemsOf(access, MxtResourceKeys.AURA);
        List<PickerRow> mine = PickerContents.itemsOfMod(access, MxtResourceKeys.AURA, MiXianTu.MOD_ID);
        List<PickerRow> test = PickerContents.itemsOfMod(access, MxtResourceKeys.AURA, MOD);
        List<PickerRow> absent = PickerContents.itemsOfMod(access, MxtResourceKeys.AURA, NO_SUCH_PACK);
        leg(context, "modid",
                !mine.isEmpty() && !test.isEmpty() && mine.size() + test.size() == rows.size() && absent.isEmpty(),
                "mxt=" + mine.size() + " mxt_test=" + test.size() + " absent=" + absent.size() + " rows=" + rows.size(),
                "both>0 sum=" + rows.size() + " absent=0");
    }

    private static void leg(ProbeContext context, String name, boolean ok, String actual, String expected) {
        String line = "picker probe: " + name + " actual=" + actual + " expected=" + expected + (ok ? " OK" : " MISMATCH");
        if (ok) context.note(line);
        else context.fail(line);
    }
}
