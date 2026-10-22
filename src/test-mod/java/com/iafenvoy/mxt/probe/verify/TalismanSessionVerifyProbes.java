package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer.Point;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingSession;
import com.iafenvoy.mxt.runtime.talisman.TalismanWorkstationService;
import com.iafenvoy.mxt.runtime.talisman.TalismanWorkstationService.StrokeRefusal;
import com.iafenvoy.mxt.runtime.talisman.TalismanWorkstationService.StrokeResult;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The drawing session's own accounting: what it takes when it opens, what it hands back when it is abandoned, and the
 * budgets it holds a stream of strokes to.
 *
 * <p>Both legs go through the real service, because both are about a session that spans ticks - the money it took has
 * to be tracked rather than re-derived from a balance that other systems also write, and the stroke budget has to be
 * enforced per stroke rather than at submit.
 */
public final class TalismanSessionVerifyProbes {
    private TalismanSessionVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/talisman_session_refund", TalismanSessionVerifyProbes::refundOnlyWhatItTook),
                Probe.of("verify/talisman_session_budget", TalismanSessionVerifyProbes::strokeBudget)
        );
    }

    // A cancelled session returns what it charged, not the balance it saw: the account may have been credited or spent
    // by something else while the drawing was open, and neither of those belongs to the session.
    private static void refundOnlyWhatItTook(ProbeContext context) {
        ResourceHolderAttachment resources = context.player().getData(MxtAttachments.RESOURCE_HOLDER);
        Holder<Resource> probe = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.id("trigger_probe"));
        double before = resources.get(probe);
        try {
            resources.set(probe, 100.0D, 0.0D, 10_000.0D, -1L, "probe");
            SimpleContainer slot = paper();
            Optional<TalismanDrawingSession> opened = TalismanWorkstationService.start(context.player(), slot,
                    Identifier.fromNamespaceAndPath("mxt_test", "talisman/drawing_ledger"));
            if (opened.isEmpty()) {
                context.fail("a drawing whose price the player could pay did not open");
                return;
            }
            context.expect("opening the drawing took its ten", resources.get(probe), 90.0D);
            // Something else writes the same account while the drawing is open, in both directions.
            resources.set(probe, 95.0D, 0.0D, 10_000.0D, -1L, "probe");
            TalismanWorkstationService.abandon(context.player(), opened.get());
            context.expect("a cancelled drawing credits its own charge on top of the regeneration", resources.get(probe),
                    105.0D);
            // A spend instead of a credit: the refund still only puts back the ten it took.
            Optional<TalismanDrawingSession> second = TalismanWorkstationService.start(context.player(), paper(),
                    Identifier.fromNamespaceAndPath("mxt_test", "talisman/drawing_ledger"));
            if (second.isEmpty()) {
                context.fail("a second drawing did not open");
                return;
            }
            resources.set(probe, 70.0D, 0.0D, 10_000.0D, -1L, "probe");
            TalismanWorkstationService.abandon(context.player(), second.get());
            context.expect("a cancelled drawing credits its own charge on top of a spend", resources.get(probe), 80.0D);
        } finally {
            resources.set(probe, before);
        }
    }

    // The running totals are the budget: a stroke that would pass either limit is refused before it is recorded, and
    // a refusal counts towards the rejection budget, which ends the session the moment it is exceeded.
    private static void strokeBudget(ProbeContext context) {
        int strokes = MxtServerConfig.INSTANCE.talisman.maxSessionStrokes.getValue();
        int rejected = MxtServerConfig.INSTANCE.talisman.maxRejectedStrokes.getValue();
        int interval = MxtServerConfig.INSTANCE.talisman.minStrokeInterval.getValue();
        try {
            // A brush with pigment on the cursor, which is where a stroke reads it from.
            ItemStack brush = new ItemStack(MxtItems.TALISMAN_BRUSH.get());
            brush.set(MxtDataComponents.BRUSH_PIGMENT, 1_000);
            context.player().containerMenu.setCarried(brush);
            // A tiny stroke budget, and no interval, so the leg is about the totals and not about timing.
            MxtServerConfig.INSTANCE.talisman.maxSessionStrokes.setValue(2);
            MxtServerConfig.INSTANCE.talisman.minStrokeInterval.setValue(0);
            SimpleContainer slot = paper();
            Optional<TalismanDrawingSession> opened = TalismanWorkstationService.start(context.player(), slot,
                    Identifier.fromNamespaceAndPath("mxt_test", "talisman/drawing_sigil"));
            if (opened.isEmpty()) {
                context.fail("a drawing of the free formula did not open");
                return;
            }
            TalismanDrawingSession session = opened.get();
            context.check("the first stroke is within the budget",
                    TalismanWorkstationService.stroke(context.player(), session, stroke()).accepted());
            context.check("the second stroke is within the budget",
                    TalismanWorkstationService.stroke(context.player(), session, stroke()).accepted());
            StrokeResult over = TalismanWorkstationService.stroke(context.player(), session, stroke());
            context.check("the stroke past the stroke budget is refused", !over.accepted());
            context.expect("the refused stroke is refused for the budget", over.refusal(), StrokeRefusal.TOO_MANY_POINTS);
            context.expect("a refused stroke is not recorded", session.strokeCount(), 2);
            context.expect("a refused stroke does not grow the point total", session.pointCount(), 6);

            // A stream of refusals ends the session instead of extending it: the count is judged on every refusal, not
            // only after a stroke was accepted.
            MxtServerConfig.INSTANCE.talisman.minStrokeInterval.setValue(72_000);
            MxtServerConfig.INSTANCE.talisman.maxRejectedStrokes.setValue(1);
            context.player().containerMenu.setCarried(brush.copy());
            Optional<TalismanDrawingSession> second = TalismanWorkstationService.start(context.player(), paper(),
                    Identifier.fromNamespaceAndPath("mxt_test", "talisman/drawing_sigil"));
            if (second.isEmpty()) {
                context.fail("a second drawing did not open");
                return;
            }
            TalismanDrawingSession refused = second.get();
            TalismanWorkstationService.stroke(context.player(), refused, stroke());
            context.check("one refusal is within the rejection budget", !refused.failed());
            TalismanWorkstationService.stroke(context.player(), refused, stroke());
            context.check("the refusal past the budget ends the session", refused.failed());
            context.expect("both refusals were counted", refused.rejected(), 2);
        } finally {
            MxtServerConfig.INSTANCE.talisman.maxSessionStrokes.setValue(strokes);
            MxtServerConfig.INSTANCE.talisman.maxRejectedStrokes.setValue(rejected);
            MxtServerConfig.INSTANCE.talisman.minStrokeInterval.setValue(interval);
        }
    }

    private static SimpleContainer paper() {
        SimpleContainer slot = new SimpleContainer(1);
        slot.setItem(0, new ItemStack(MxtItems.BLANK_TALISMAN.get()));
        return slot;
    }

    // Three points, which is the smallest stroke the scorer accepts and small enough to stay under every budget.
    private static List<Point> stroke() {
        List<Point> points = new ArrayList<>(3);
        points.add(new Point(0.4D, 0.1D));
        points.add(new Point(0.5D, 0.5D));
        points.add(new Point(0.6D, 0.9D));
        return points;
    }
}
