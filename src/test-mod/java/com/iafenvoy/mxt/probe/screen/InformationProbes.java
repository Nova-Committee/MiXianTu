package com.iafenvoy.mxt.probe.screen;

import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.screen.information.InformationCollector.InformationEntry;
import com.iafenvoy.mxt.screen.information.InformationManager;
import com.iafenvoy.mxt.screen.information.InformationManager.Side;

import java.util.ArrayList;
import java.util.List;

/**
 * The character panel's own line model, called the way the screen calls it: it used to be printed for an operator to
 * read, and is now checked as the rows themselves.
 */
public final class InformationProbes {
    private InformationProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("screen/information", InformationProbes::information));
    }

    // It calls the very same InformationManager.collectEntries the screen does, so the rows the server sees are
    // the rows the panel would draw.
    private static void information(ProbeContext context) {
        for (Side side : Side.values()) {
            List<InformationEntry> entries = InformationManager.collectEntries(context.player(), side);
            context.check("the " + side + " side collects entries", !entries.isEmpty());
            List<String> printed = new ArrayList<>();
            for (InformationEntry entry : entries) {
                context.check("a " + side + " entry has a name or a value", entry.name() != null || entry.value() != null);
                context.check("a " + side + " entry is fulfilled", entry.fulfilled());
                printed.add(line(entry));
            }
            context.note("[" + side + "] " + entries.size() + " entries");
            printed.forEach(context::note);
            List<String> again = new ArrayList<>();
            for (InformationEntry entry : InformationManager.collectEntries(context.player(), side))
                again.add(line(entry));
            context.check("the printed " + side + " lines match what collectEntries returned", printed.equals(again));
        }
    }

    // The line the old command printed for one entry, in the same shape: a row with no name prints an empty one.
    private static String line(InformationEntry entry) {
        String name = entry.name() == null ? "" : entry.name().getString();
        String value = entry.value().getString();
        return "- " + name + " = " + value;
    }
}
