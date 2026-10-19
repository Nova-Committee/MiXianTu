package com.iafenvoy.mxt.probe.formation;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.NoOpAction;
import com.iafenvoy.mxt.data.action.builtin.block.ChangeAuraBlockAction;
import com.iafenvoy.mxt.data.action.builtin.entity.ApplyEffectAction;
import com.iafenvoy.mxt.data.formation.*;
import com.iafenvoy.mxt.data.formation.builtin.*;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.failure.FormationWorldFailure;
import com.iafenvoy.mxt.runtime.formation.*;
import com.iafenvoy.mxt.runtime.formation.FormationWorldService.Result;
import com.iafenvoy.mxt.testmod.FormationProbeActions;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * The formation module dispatch: the four beats every module is offered, on the array and on one entity, and the shape
 * of the modules that replaced the top-level action fields.
 */
public final class FormationProbes {
    private static final List<String> MODULES = List.of("empty", "attack", "buff", "aura", "protection", "particle",
            "send_message", "activate", "tick", "inactivate");
    /**
     * The figures the mod's own pack ships, which used to be the three values of a built-in enum.
     */
    private static final List<String> FIGURES = List.of("five", "six", "eight");

    private FormationProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("formation/beats", FormationProbes::beats),
                Probe.of("formation/modules", FormationProbes::modules),
                Probe.of("formation/shape", FormationProbes::shape),
                Probe.of("formation/structure", FormationProbes::structure));
    }

    // A module of the test pack writes down every beat it is offered, so the dispatch is read directly: what a pack
    // used to write as a top-level field and what it writes as a module now run through the same four calls, and the
    // module's own interval decides which beats the third one lands on.
    private static void beats(ProbeContext context) {
        Formation definition = formation(context, """
                {"radius": 8, "structure_check": "always", "actions": [
                  {"type": "mxt_test:beats", "tag": "slow", "interval_ticks": 30}]}""");
        if (definition == null) return;
        FormationInstance instance = instance(context);
        BlockPos controller = context.playerSpot();
        FormationActionContext array = FormationActionContext.array(context.level(), controller, instance);
        FormationActionContext entity = new FormationActionContext(context.level(),
                FormationActionContext.carrier(instance, controller), context.player());
        FormationProbeActions.reset();
        FormationActionRunner.activate(definition, array);
        FormationActionRunner.activate(definition, entity);
        // Four beats over two clocks: a 30-tick interval lands on the beats the world clock puts on a third boundary,
        // while a tick beat only lands on the upkeep period. Beat three is the one that shows the difference - it is
        // an interval without a tick.
        FormationActionRunner.periodic(definition, array, 0L);
        FormationActionRunner.periodic(definition, array, 10L);
        FormationActionRunner.periodic(definition, array, 20L);
        FormationActionRunner.periodic(definition, array, 30L);
        FormationActionRunner.periodic(definition, entity, 30L);
        FormationActionRunner.inactivate(definition, array);
        FormationActionRunner.inactivate(definition, entity);
        context.expect("formation probe: the four beats reach both scopes in order", FormationProbeActions.calls(),
                List.of("slow:activate:array", "slow:activate:entity", "slow:tick:array", "slow:interval:array",
                        "slow:tick:array", "slow:interval:array", "slow:interval:entity",
                        "slow:inactivate:array", "slow:inactivate:entity"));
        // The two clocks themselves: the module beat is the finer one and a period is a whole number of beats, which is
        // what keeps a bill from landing between two of them.
        context.check("formation probe: the module beat is finer than the upkeep period",
                FormationIntervals.beatDue(30L) && !FormationIntervals.periodDue(30L));
        context.check("formation probe: the upkeep period is a whole number of beats",
                FormationIntervals.PERIOD % FormationIntervals.TICK == 0);
        // A value the beat grid cannot honour is refused rather than silently rounded onto it.
        context.check("formation probe: an interval off the beat grid is refused",
                module(context, "mxt_test:beats", "{\"tag\": \"off\", \"interval_ticks\": 15}") == null);
        // The array's own beat sees nothing but the level; an entity's beat adds the array's numbers, which is what
        // the conditions about the array read.
        context.check("formation probe: the array's own formula carries no array numbers",
                Double.isNaN(array.formula().explicit("formation_radius")));
        context.expect("formation probe: an entity's formula carries the radius",
                entity.formula().explicit("formation_radius"), 8.0D);
        context.expect("formation probe: an entity's formula carries its distance",
                entity.formula().explicit("distance"),
                Math.sqrt(context.player().distanceToSqr(Vec3.atCenterOf(controller))));
    }

    // The module table: the ten ids, the empty module as the dispatch default, the generic three's no-op halves, and
    // the aura module the buff module gave up.
    private static void modules(ProbeContext context) {
        for (String path : MODULES)
            context.check("formation probe: the registry has mxt:" + path,
                    MxtRegistries.FORMATION_ACTION_TYPE.containsKey(Identifier.fromNamespaceAndPath("mxt", path)));
        // The declared default key is `empty`, which is what "the dispatch table's default entry" means here. The
        // default id itself is namespaced by the registry (`minecraft:empty`), where nothing is registered, so an
        // unknown `type` still fails the load instead of silently becoming an empty module.
        context.expect("formation probe: the empty module holds the dispatch table's default key",
                MxtRegistries.FORMATION_ACTION_TYPE.getDefaultKey().getPath(), "empty");
        // The generic three carry both halves and default each to the no-op: a beat module with no field is what the
        // absence of the removed top-level field used to mean.
        FormationActionType activate = module(context, "mxt:activate", "{}");
        FormationActionType tick = module(context, "mxt:tick", "{}");
        FormationActionType inactivate = module(context, "mxt:inactivate", "{}");
        context.check("formation probe: an empty activate module is a pair of no-ops",
                activate instanceof ActivateFormationAction(
                        BlockAction blockAction,
                        EntityAction entityAction
                )
                        && blockAction == NoOpAction.INSTANCE && entityAction == NoOpAction.INSTANCE);
        context.check("formation probe: an empty tick module is a pair of no-ops",
                tick instanceof TickFormationAction(
                        BlockAction blockAction,
                        EntityAction entityAction
                )
                        && blockAction == NoOpAction.INSTANCE && entityAction == NoOpAction.INSTANCE);
        context.check("formation probe: an empty inactivate module is a pair of no-ops",
                inactivate instanceof InactivateFormationAction(
                        BlockAction blockAction,
                        EntityAction entityAction
                )
                        && blockAction == NoOpAction.INSTANCE && entityAction == NoOpAction.INSTANCE);
        // The zone is required: a bonus is applied to the resources of the zone the override selected, so a module
        // declaring only max_bonus would be silently worthless.
        FormationActionType aura = module(context, "mxt:aura",
                "{\"aura_zone\": \"mxt_test:spirit_gathering\", \"max_bonus\": {\"mxt_test:spirit_power\": 37.5}}");
        context.expect("formation probe: the aura module read its zone",
                aura instanceof AuraFormationAction zone ? HolderHelper.id(zone.auraZone()).toString() : null,
                "mxt_test:spirit_gathering");
        context.check("formation probe: a bonus without a zone is refused",
                module(context, "mxt:aura", "{\"max_bonus\": {}}") == null);
        // What the aura left behind: the buff module still reads its own fields and no longer has the zone.
        FormationActionType buff = module(context, "mxt:buff", "{\"aura_zone\": \"mxt_test:spirit_gathering\"}");
        context.check("formation probe: the buff module no longer reads the aura zone",
                buff instanceof BuffFormationAction module && module.abilities().isEmpty());
        // The pack's own fixtures, converted: the array the kit hands out declares its aura and its beats as modules,
        // and the block half of a beat is read from `block_action` rather than from the old top-level field.
        Formation gathering = context.require(MxtResourceKeys.FORMATION, ProbeDefinitions.FORMATION).value();
        context.check("formation probe: the kit's array declares its aura as a module",
                gathering.actions().stream().anyMatch(AuraFormationAction.class::isInstance));
        context.check("formation probe: the kit's array declares its beats as modules",
                gathering.actions().stream().anyMatch(ActivateFormationAction.class::isInstance)
                        && gathering.actions().stream().anyMatch(TickFormationAction.class::isInstance)
                        && gathering.actions().stream().anyMatch(InactivateFormationAction.class::isInstance));
        context.check("formation probe: a beat reads its block half from block_action",
                gathering.actions().stream().anyMatch(module -> module instanceof ActivateFormationAction pair
                        && pair.blockAction() instanceof ChangeAuraBlockAction)
                        && gathering.actions().stream().anyMatch(module -> module instanceof TickFormationAction pair
                        && pair.entityAction() instanceof ApplyEffectAction));
    }

    // The display module and the figure it draws. The figure is the registry's, so what is checked here is the
    // module's own fields, the definitions the mod ships as JSON, and the sampler that walks them: the layout is pure
    // arithmetic, and a count that does not follow the length shows up in play only as an outline with holes in it.
    private static void shape(ProbeContext context) {
        FormationActionType module = module(context, "mxt:particle",
                "{\"shape\": \"mxt:eight\", \"duration_ticks\": 60, \"interval_ticks\": 30}");
        context.check("formation probe: the display module reads its own fields",
                module instanceof ParticleFormationAction(
                        Holder<FormationShape> shape, int durationTicks, int intervalTicks, OptionalDouble displayDistance
                )
                        && HolderHelper.id(shape).equals(Identifier.parse("mxt:eight"))
                        && durationTicks == 60
                        && intervalTicks == 30
                        && displayDistance.isEmpty());
        // The reach a figure may tighten is its own field, so a formation that wants to be seen from up close says so
        // on the module; outside one block to 4096 the definition is refused rather than clamped.
        FormationActionType limited = module(context, "mxt:particle",
                "{\"shape\": \"mxt:eight\", \"display_distance\": 64}");
        context.check("formation probe: the display module reads its own draw distance",
                limited instanceof ParticleFormationAction display
                        && display.displayDistance().orElseThrow() == 64.0D);
        context.check("formation probe: a draw distance below one block is refused",
                module(context, "mxt:particle", "{\"shape\": \"mxt:eight\", \"display_distance\": 0}") == null);
        // The figure is the whole of what the module draws, so there is no meaningful default for it; a figure no pack
        // provides is a load error rather than an empty outline.
        context.check("formation probe: the display module requires a shape",
                module(context, "mxt:particle", "{}") == null);
        context.check("formation probe: a figure no pack provides is refused",
                module(context, "mxt:particle", "{\"shape\": \"mxt:nine\"}") == null);
        // How a figure is laid out belongs to its strokes, so the module reads neither of the two: writing them is an
        // unknown key that is quietly ignored rather than a module-wide density.
        FormationActionType legacy = module(context, "mxt:particle",
                "{\"shape\": \"mxt:five\", \"size\": 0.5, \"spacing\": 1.5}");
        context.check("formation probe: the module no longer carries a size or a spacing",
                legacy instanceof ParticleFormationAction display
                        && display.durationTicks() == 0 && display.intervalTicks() == FormationIntervals.TICK);
        // Every field is optional on its own: a bare shape is drawn complete on the first beat.
        FormationActionType bare = module(context, "mxt:particle", "{\"shape\": \"mxt:five\"}");
        context.check("formation probe: a bare display module is a complete outline",
                bare instanceof ParticleFormationAction display
                        && display.durationTicks() == 0 && display.intervalTicks() == FormationIntervals.TICK);
        // The shipped definitions: the three stars the mod used to build in code, now three files of its own pack. They
        // say nothing about their layout, so every stroke takes the standard density and dust size.
        for (String name : FIGURES) {
            FormationShape figure = figure(context, name);
            context.check("formation probe: " + name + " begins with the ring and paints every stroke",
                    figure != null && figure.strokes().size() >= 2
                            && figure.strokes().getFirst() instanceof FormationStroke.Circle ring
                            && ring.radius() == 1.0D && ring.color().left().orElse(0) == 0xFFFFFF
                            && figure.strokes().stream().allMatch(stroke -> stroke.color().left().isPresent()));
            context.check("formation probe: " + name + " takes the standard layout",
                    figure != null && figure.strokes().stream()
                            .allMatch(stroke -> stroke.spacing() == FormationStroke.Common.DEFAULT_SPACING
                                    && stroke.size() == FormationStroke.Common.DEFAULT_SIZE));
        }
        context.check("formation probe: five is one pentagram stroke on the ring",
                figure(context, "five").strokes().size() == 2
                        && figure(context, "five").strokes().get(1) instanceof FormationStroke.Polyline pentagram
                        && pentagram.points().size() == 6);
        context.check("formation probe: six is two triangles and eight is two squares",
                figure(context, "six").strokes().size() == 3 && figure(context, "eight").strokes().size() == 3
                        && figure(context, "six").strokes().get(1) instanceof FormationStroke.Polyline triangle
                        && triangle.points().size() == 4
                        && figure(context, "eight").strokes().get(1) instanceof FormationStroke.Polyline square
                        && square.points().size() == 5);
        // The fixture itself, not just a string here: this file is what a server loads at startup, and a field the
        // codec does not read is silently ignored rather than reported.
        Formation ranged = context.require(MxtResourceKeys.FORMATION,
                Identifier.fromNamespaceAndPath("mxt_test", "formation_range_probe")).value();
        context.check("formation probe: the range fixture reads its shape and duration",
                ranged.actions().stream().anyMatch(action -> action instanceof ParticleFormationAction display
                        && HolderHelper.id(display.shape()).equals(Identifier.parse("mxt:five"))
                        && display.durationTicks() == 60));
        // The three look-at fixtures: one per figure, raised anywhere, so a client can be pointed at each star without
        // editing a pack first.
        for (String name : FIGURES) {
            Formation demo = context.require(MxtResourceKeys.FORMATION,
                    Identifier.fromNamespaceAndPath("mxt_test", "star_" + name)).value();
            context.check("formation probe: the " + name + " fixture draws that figure",
                    demo.actions().stream().anyMatch(action -> action instanceof ParticleFormationAction display
                            && HolderHelper.id(display.shape()).equals(Identifier.parse("mxt:" + name))));
        }
        // What one stroke may say about its own paint, and what it may not: one field, written flat or as a ramp.
        FormationShape ramped = definition(context, """
                {"strokes": [{"type": "mxt:polyline", "points": [[0, 0], [1, 0]],
                  "color": [{"at": 0, "color": "#ff0000"}, {"at": 1, "color": "#0000ff"}]}]}""");
        context.check("formation probe: a ramp is read along the stroke it is on",
                ramped != null && ramped.strokes().getFirst().color().right().isPresent()
                        && ramped.strokes().getFirst().colorAt(0.0D) == 0xFF0000
                        && ramped.strokes().getFirst().colorAt(0.5D) == 0x800080
                        && ramped.strokes().getFirst().colorAt(1.0D) == 0x0000FF);
        // Paint has no fallback: the module carries no colour of its own, so a stroke that writes none is refused
        // rather than drawn in a colour nobody asked for.
        context.check("formation probe: a stroke without paint is refused",
                definition(context, "{\"strokes\": [{\"type\": \"mxt:circle\", \"radius\": 1}]}") == null
                        && definition(context, """
                        {"strokes": [{"type": "mxt:polyline", "points": [[0, 0], [1, 0]]}]}""") == null);
        context.check("formation probe: a figure cannot be empty, have a circle with no radius or a one-point stroke",
                definition(context, "{\"strokes\": []}") == null
                        && definition(context, """
                        {"strokes": [{"type": "mxt:circle", "radius": 0, "color": "#ffffff"}]}""") == null
                        && definition(context, """
                        {"strokes": [{"type": "mxt:polyline", "points": [[0, 0]], "color": "#ffffff"}]}""") == null
                        && definition(context, "{\"strokes\": [{\"type\": \"mxt:hexagon\", \"color\": \"#ffffff\"}]}") == null
                        && definition(context, "{\"strokes\": [{\"type\": \"hexagon\", \"color\": \"#ffffff\"}]}") == null);
        // The window: a stroke says which part of the figure's progress it belongs to, so a figure can be drawn one
        // stroke at a time. The window has to open, and both ends live inside the progress a formation animates over.
        FormationShape windowed = definition(context, """
                {"strokes": [
                  {"type": "mxt:circle", "radius": 1, "color": "#ffffff", "start": 0.5, "end": 1},
                  {"type": "mxt:polyline", "points": [[0, 0], [1, 0]], "color": "#ffffff", "end": 0.5}
                ]}""");
        context.check("formation probe: a stroke reads its own window",
                windowed != null && windowed.strokes().getFirst().start() == 0.5D
                        && windowed.strokes().getFirst().end() == 1.0D
                        && windowed.strokes().get(1).start() == 0.0D && windowed.strokes().get(1).end() == 0.5D);
        context.check("formation probe: a window that never opens is refused",
                definition(context, """
                        {"strokes": [{"type": "mxt:circle", "radius": 1, "color": "#ffffff",
                          "start": 0.5, "end": 0.5}]}""") == null
                        && definition(context, """
                        {"strokes": [{"type": "mxt:circle", "radius": 1, "color": "#ffffff",
                          "start": 0.75, "end": 0.25}]}""") == null
                        && definition(context, """
                        {"strokes": [{"type": "mxt:circle", "radius": 1, "color": "#ffffff",
                          "start": -0.5}]}""") == null
                        && definition(context, """
                        {"strokes": [{"type": "mxt:circle", "radius": 1, "color": "#ffffff", "end": 1.5}]}""") == null);
        // The shape the figure tool writes, so the two cannot drift apart: it is the file a pack is handed.
        FormationShape written = definition(context, """
                {"strokes": [
                  {"type": "mxt:circle", "radius": 1, "color": "#ffffff"},
                  {"type": "mxt:polyline", "points": [[1, 0], [-0.809, 0.588], [0.309, -0.951], [0.309, 0.951],
                    [-0.809, -0.588], [1, 0]], "color": "#ffd76a", "spacing": 1, "size": 1.5}
                ]}""");
        context.check("formation probe: what the figure tool writes is what the codec reads",
                written != null && written.strokes().size() == 2
                        && written.strokes().getFirst().color().left().orElse(0) == 0xFFFFFF
                        && written.strokes().getFirst().spacing() == FormationStroke.Common.DEFAULT_SPACING
                        && written.strokes().get(1).color().left().orElse(0) == 0xFFD76A
                        && written.strokes().get(1).spacing() == 1.0D
                        && written.strokes().get(1).size() == 1.5F);
        // A stroke's own layout is bounded the way the module's was: past it a figure stops being drawable at all.
        context.check("formation probe: a stroke's own spacing and size are bounded",
                definition(context, """
                        {"strokes": [{"type": "mxt:circle", "radius": 1, "color": "#ffffff", "spacing": 0}]}""") == null
                        && definition(context, """
                        {"strokes": [{"type": "mxt:circle", "radius": 1, "color": "#ffffff", "spacing": 65}]}""") == null
                        && definition(context, """
                        {"strokes": [{"type": "mxt:circle", "radius": 1, "color": "#ffffff", "size": 0}]}""") == null
                        && definition(context, """
                        {"strokes": [{"type": "mxt:circle", "radius": 1, "color": "#ffffff", "size": 5}]}""") == null);
        Vec3 origin = new Vec3(0.0D, 0.0D, 0.0D);
        double radius = 8.0D;
        // The test pack's own sample figure: it is the one a player or a look-at fixture raises to see every field at
        // once (a ramp, a window, off-centre circles), so it is worth pinning that the pack ships something walkable.
        FormationShape sample = context.require(MxtResourceKeys.FORMATION_SHAPE,
                Identifier.fromNamespaceAndPath("mxt_test", "test1")).value();
        context.check("formation probe: the test pack's sample figure walks its nine strokes",
                sample.strokes().size() == 9
                        && sample.strokes().getFirst().color().right().isPresent()
                        && sample.strokes().get(3) instanceof FormationStroke.Circle offCentre
                        && offCentre.center().x == 0.5F
                        && sample.strokes().get(7).start() == 0.5D
                        && !FormationShapePath.outline(sample, origin, radius, 1.0F).isEmpty());
        FormationShape five = figure(context, "five");
        List<FormationShapePath.Dot> full = FormationShapePath.outline(five, origin, radius, 1.0F);
        context.check("formation probe: nothing is drawn before any progress",
                FormationShapePath.outline(five, origin, radius, 0.0F).isEmpty());
        // Vertex zero is due east, and the ring starts there, so the very first point is the +X one.
        context.check("formation probe: the figure starts due east on the ring",
                full.getFirst().position().equals(new Vec3(radius, 0.0D, 0.0D)));
        context.check("formation probe: a stroke with one colour hands that colour and its size to every point",
                five.strokes().getFirst().colorAt(0.5D) == 0xFFFFFF
                        && full.stream().allMatch(dot -> dot.color() == 0xFFFFFF)
                        && full.stream().allMatch(dot -> dot.size() == FormationStroke.Common.DEFAULT_SIZE));
        // A circle may begin past due east: the angle is written in degrees, counted the way every other angle here is.
        FormationShape turned = definition(context, """
                {"strokes": [{"type": "mxt:circle", "radius": 1, "color": "#ffffff", "start_angle": 90}]}""");
        List<FormationShapePath.Dot> turnedDots = turned == null ? List.of()
                : FormationShapePath.outline(turned, origin, radius, 1.0F);
        context.check("formation probe: a circle begins at its own angle",
                turned != null && turned.strokes().getFirst() instanceof FormationStroke.Circle circle
                        && circle.startAngle() == 90.0D && !turnedDots.isEmpty()
                        && turnedDots.getFirst().position().distanceToSqr(new Vec3(0.0D, 0.0D, radius)) < 1.0E-9D);
        context.check("formation probe: an angle past one turn either way is refused",
                definition(context, """
                        {"strokes": [{"type": "mxt:circle", "radius": 1, "color": "#ffffff", "start_angle": 361}]}""") == null
                        && definition(context, """
                        {"strokes": [{"type": "mxt:circle", "radius": 1, "color": "#ffffff", "start_angle": -361}]}""") == null);
        // A figure is not drawn all at once unless every stroke says so: a stroke waits for its window to open, and a
        // later progress picks up the one after it without the earlier one being laid out again.
        double unit = 4.0D;
        List<FormationShapePath.Dot> early = FormationShapePath.outline(windowed, origin, unit, 0.25F);
        List<FormationShapePath.Dot> half = FormationShapePath.outline(windowed, origin, unit, 0.5F);
        List<FormationShapePath.Dot> late = FormationShapePath.outline(windowed, origin, unit, 0.75F);
        context.check("formation probe: a stroke waits for its own window before it is drawn",
                !early.isEmpty() && early.stream().allMatch(dot -> dot.position().z == 0.0D)
                        && early.stream().allMatch(dot -> dot.position().x >= 0.0D && dot.position().x <= unit));
        context.check("formation probe: one window closing and the next opening is a hand-over",
                half.size() > early.size() && half.stream().allMatch(dot -> dot.position().z == 0.0D)
                        && late.size() > half.size()
                        && late.stream().anyMatch(dot -> Math.abs(dot.position().z) > 1.0D));
        context.check("formation probe: the star stays inside the ring and the whole figure is at one height",
                full.stream().allMatch(dot -> dot.position().y == 0.0D
                        && dot.position().distanceToSqr(origin) <= radius * radius * (1.0D + 1.0E-6D)));
        context.check("formation probe: a half-drawn figure has fewer points than a complete one",
                FormationShapePath.outline(five, origin, radius, 0.5F).size() < full.size());
        // One stroke of the same length at two densities: the count follows the stroke's own spacing, and the endpoint
        // that makes the drawn line land exactly on the progress it was asked for is the extra one.
        FormationShape fine = definition(context, """
                {"strokes": [{"type": "mxt:polyline", "points": [[0, 0], [1, 0]], "color": "#ffffff",
                  "spacing": 1, "size": 1.5}]}""");
        FormationShape coarse = definition(context, """
                {"strokes": [{"type": "mxt:polyline", "points": [[0, 0], [1, 0]], "color": "#ffffff",
                  "spacing": 2}]}""");
        List<FormationShapePath.Dot> fineDots = fine == null ? List.of()
                : FormationShapePath.outline(fine, origin, radius, 1.0F);
        List<FormationShapePath.Dot> coarseDots = coarse == null ? List.of()
                : FormationShapePath.outline(coarse, origin, radius, 1.0F);
        // Eight blocks of line: one point every block is nine points, one every two blocks is five.
        context.check("formation probe: the point count follows the stroke's own spacing",
                fineDots.size() == 9 && coarseDots.size() == 5);
        context.check("formation probe: a stroke's own dust size reaches every point of it",
                fineDots.stream().allMatch(dot -> dot.size() == 1.5F)
                        && coarseDots.stream().allMatch(dot -> dot.size() == FormationStroke.Common.DEFAULT_SIZE));
        // Two strokes of one figure may be laid out differently: the gap between two points of the fine one is half the
        // coarse one's, which is what lets a dense outline and a sparse detail live in the same figure.
        FormationShape mixed = definition(context, """
                {"strokes": [
                  {"type": "mxt:polyline", "points": [[0, 0], [1, 0]], "color": "#ffffff", "spacing": 1},
                  {"type": "mxt:polyline", "points": [[0, 0], [0, 1]], "color": "#ffffff", "spacing": 4}
                ]}""");
        List<FormationShapePath.Dot> twoSpeeds = mixed == null ? List.of()
                : FormationShapePath.outline(mixed, origin, radius, 1.0F);
        context.check("formation probe: one figure can lay two strokes out at two densities",
                twoSpeeds.size() == 12
                        && twoSpeeds.stream().filter(dot -> dot.position().x > 0.0D).count() == 8
                        && twoSpeeds.stream().filter(dot -> dot.position().z > 0.0D).count() == 2);
        // The ceiling exists so a tiny spacing on a huge radius cannot flood one frame; the eight-figure has three
        // strokes to cap, so three times the per-stroke limit is the most any frame can ask for.
        context.check("formation probe: one frame is capped",
                FormationShapePath.outline(figure(context, "eight"), origin, 4096.0D, 1.0F).size()
                        <= 3 * FormationShapePath.MAX_POINTS_PER_STROKE);
        // A growing figure reaches the client as a band per tick: a band is the piece between two progress values, so
        // the leading edge moves without the outline behind it being laid out again.
        List<FormationShapePath.Dot> firstHalf = FormationShapePath.band(five, origin, radius, 0.0F, 0.5F);
        List<FormationShapePath.Dot> secondHalf = FormationShapePath.band(five, origin, radius, 0.5F, 1.0F);
        // A band is measured from its own start, so the second half begins at the ring's antipode rather than at due
        // east, and it holds less than the whole figure.
        context.check("formation probe: a band holds only the part between two progress values",
                !firstHalf.isEmpty() && firstHalf.size() < full.size()
                        && secondHalf.getFirst().position().distanceToSqr(new Vec3(-radius, 0.0D, 0.0D)) < 1.0E-9D);
        context.check("formation probe: a band that adds nothing draws nothing",
                FormationShapePath.band(five, origin, radius, 0.5F, 0.5F).isEmpty()
                        && FormationShapePath.band(five, origin, radius, 0.5F, 0.25F).isEmpty());
        // Two halves cover the figure again. Each half rounds its own ends up and the two share the seam point, so
        // together they hold a couple of points per stroke more than the whole figure - nowhere near twice it.
        int halves = firstHalf.size() + secondHalf.size();
        context.check("formation probe: consecutive bands cover the whole figure",
                halves >= full.size() && halves <= full.size() + 2 * five.strokes().size());
        // The sampler carries each point's colour, which is what lets one stroke of a figure ramp while another is flat.
        List<FormationShapePath.Dot> rampDots = ramped == null ? List.of()
                : FormationShapePath.outline(ramped, origin, radius, 1.0F);
        context.check("formation probe: the sampler hands over the colour of each point",
                !rampDots.isEmpty() && rampDots.getFirst().color() == 0xFF0000
                        && rampDots.getLast().color() == 0x0000FF);
        // An order is what the server tells a client about a figure's clock: which raise it belongs to, how much of the
        // figure already exists, how long the rest takes on the client's own clock, and how long the order stays good
        // for without another one.
        FormationDrawIn opening = FormationDrawIn.at(1000L, 1000L, 600, 10);
        context.check("formation probe: the first order of a draw-in carries nothing drawn",
                opening.raisedAt() == 1000L && opening.drawn() == 0.0F && opening.drawInTicks() == 600);
        FormationDrawIn beat = FormationDrawIn.at(1000L, 1300L, 600, 10);
        context.check("formation probe: a later order carries the rest of the draw-in",
                Math.abs(beat.drawn() - 0.5F) < 1.0E-6F && beat.drawInTicks() == 300);
        // This is the whole reason the client can own the clock: its ramp and the server's have one slope, so starting
        // from `drawn` and growing for `drawInTicks` lands on the figure at the moment the server's own ramp would.
        context.check("formation probe: the client's ramp lands where the server's does",
                Math.abs((1.0F - beat.drawn()) * 600 - beat.drawInTicks()) < 1.0E-3F);
        context.check("formation probe: a finished draw-in is a still picture",
                FormationDrawIn.at(1000L, 2000L, 600, 10).drawn() == 1.0F
                        && FormationDrawIn.at(1000L, 2000L, 600, 10).drawInTicks() == 0);
        context.check("formation probe: an array with no draw-in never animates",
                FormationDrawIn.at(1000L, 1000L, 0, 10).drawInTicks() == 0
                        && FormationDrawIn.at(0L, 60000L, 600, 10).drawInTicks() == 0);
        // The hold window follows how often orders come, so a slow interval is not a figure that blinks out between two
        // of them - and a huge one is not a figure that stands for half an hour after its array is gone.
        context.check("formation probe: the hold window follows the order interval",
                FormationDrawIn.at(0L, 0L, 600, 10).holdTicks() == 30
                        && FormationDrawIn.at(0L, 0L, 600, 60).holdTicks() == 120
                        && FormationDrawIn.at(0L, 0L, 600, 24000).holdTicks() == 200);
        boolean ordered = true;
        for (int duration : new int[]{10, 55, 600, 24000}) {
            for (int interval : new int[]{10, 30, 24000}) {
                for (long time = 0L; time <= duration + 2L * interval; time += interval) {
                    FormationDrawIn order = FormationDrawIn.at(0L, time, duration, interval);
                    ordered &= order.drawn() >= 0.0F && order.drawn() <= 1.0F
                            && order.drawInTicks() >= 0 && order.drawInTicks() <= duration
                            && order.holdTicks() > 0
                            && Math.abs((1.0F - order.drawn()) * duration - order.drawInTicks()) <= 1.0F;
                }
            }
        }
        context.check("formation probe: every order agrees with the server's own ramp", ordered);
        // The animation is timed from the instance rather than from a counter, so the stamp has to survive a save; a
        // row without one reads as raised at time zero, which draws an older array complete instead of replaying it.
        FormationInstance stamped = FormationInstance.CODEC
                .parse(ops(context), JsonParser.parseString("{\"formation\": \"" + ProbeDefinitions.FORMATION
                        + "\", \"radius\": 8, \"activated_at\": 1234}"))
                .result().orElse(null);
        context.check("formation probe: an instance keeps the time it was raised",
                stamped != null && stamped.activatedAt() == 1234L);
        context.expect("formation probe: an instance without a raising time reads as time zero",
                instance(context).activatedAt(), 0L);
        // Where the figure sits: the height is not a data pack field, so a probe is the only thing that can pin it.
        // The centre is still the middle of the controller block in x and z, and 0.1 above its bottom in y.
        context.check("formation probe: the figure is drawn just above the ground",
                FormationShapeDisplay.center(new BlockPos(3, 64, -5)).distanceToSqr(new Vec3(3.5D, 64.1D, -4.5D)) < 1.0E-9D);
        context.note("formation probe: " + full.size() + " points on the whole figure, " + halves + " on the two halves,"
                + " one stroke at spacing 1 is " + fineDots.size() + " points and at 2 is " + coarseDots.size()
                + ", an order half way through a 600 tick draw-in carries drawn=" + beat.drawn()
                + " and " + beat.drawInTicks() + " ticks left");
    }

    // Where an array may stand. Both definitions are tried at the same position, so the pair says the difference is
    // what the definition declares and not what the ground happens to be.
    private static void structure(ProbeContext context) {
        BlockPos spot = context.playerSpot();
        Formation bare = formation(context, """
                {"radius": 8, "structure_check": "always", "actions": [{"type": "mxt:empty"}]}""");
        Formation declared = formation(context, """
                {"radius": 8, "structure": [{"offset": [0, 0, 0], "state": "minecraft:bedrock"}],
                  "actions": [{"type": "mxt:empty"}]}""");
        if (bare == null || declared == null) return;
        ResourceHolderAttachment resources = context.player().getData(MxtAttachments.RESOURCE_HOLDER);
        FormulaContext formula = FormulaContext.of(context.player());
        UUID owner = context.player().getUUID();
        Result refused = FormationWorldService.activate(context.level(), spot,
                ProbeDefinitions.id("probe_declared_structure"), declared, resources, formula, owner);
        context.expect("formation probe: a declared structure is checked when the array is raised",
                refused.failure(), FormationWorldFailure.INVALID_STRUCTURE);
        // Whatever the first attempt left behind, the position has to be free before the second one is judged.
        FormationWorldService.deactivate(context.level(), spot);
        Result raised = FormationWorldService.activate(context.level(), spot,
                ProbeDefinitions.id("probe_bare_structure"), bare, resources, formula, owner);
        context.check("formation probe: a formation that declares no structure goes up on bare ground",
                raised.active());
        FormationWorldService.deactivate(context.level(), spot);
        context.check("formation probe: taking it down frees the position",
                context.level().getData(MxtAttachments.FORMATION_WORLD).get(spot).isEmpty());
        // The three look-at fixtures carry a structure of their own, so an unbound plate can tell them apart.
        for (String name : FIGURES) {
            Formation demo = context.require(MxtResourceKeys.FORMATION,
                    Identifier.fromNamespaceAndPath("mxt_test", "star_" + name)).value();
            context.check("formation probe: the " + name + " fixture declares a structure",
                    !demo.structure().isEmpty());
        }
    }

    // A figure of the mod's own pack, read the way the registry reads it: a missing or refused definition throws, which
    // is the point - these three files are what every formation without a pack of its own draws.
    private static FormationShape figure(ProbeContext context, String name) {
        return context.require(MxtResourceKeys.FORMATION_SHAPE, Identifier.parse("mxt:" + name)).value();
    }

    // A figure decoded straight from JSON, for the definitions that are not a file of any pack. Null is a refusal.
    private static FormationShape definition(ProbeContext context, String json) {
        return FormationShape.DIRECT_CODEC.parse(ops(context), JsonParser.parseString(json)).result().orElse(null);
    }

    private static Formation formation(ProbeContext context, String json) {
        Formation definition = Formation.DIRECT_CODEC.parse(ops(context), JsonParser.parseString(json)).result().orElse(null);
        context.check("formation probe: the fixture decodes: " + json, definition != null);
        return definition;
    }

    private static FormationInstance instance(ProbeContext context) {
        return FormationInstance.CODEC
                .parse(ops(context),
                        JsonParser.parseString("{\"formation\": \"" + ProbeDefinitions.FORMATION + "\", \"radius\": 8}"))
                .result().orElseThrow(() -> new IllegalStateException("The formation instance fixture does not decode"));
    }

    // A module is decoded the way a definition decodes one: through the dispatch table's own codec for its id.
    private static FormationActionType module(ProbeContext context, String id, String json) {
        MapCodec<? extends FormationActionType> codec = MxtRegistries.FORMATION_ACTION_TYPE.getValue(Identifier.parse(id));
        return codec.decoder().parse(ops(context), JsonParser.parseString(json)).result().orElse(null);
    }

    // A zone and an ability are named through holders, so every decode here needs the level's registries.
    private static RegistryOps<JsonElement> ops(ProbeContext context) {
        return RegistryOps.create(JsonOps.INSTANCE, context.registries());
    }
}
