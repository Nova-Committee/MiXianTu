package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.compat.ponder.PonderPickingCompatibility;
import com.iafenvoy.mxt.compat.ponder.PonderTooltipCompatibility;
import com.iafenvoy.mxt.data.formation.Formation.RequiredBlock;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.runtime.formation.FormationPreview;
import com.iafenvoy.mxt.screen.ponder.FormationPonderScene;
import net.createmod.catnip.api.data.Pair;
import net.createmod.ponder.api.client.level.PonderLevel;
import net.createmod.ponder.api.client.scene.PonderScene;
import net.createmod.ponder.impl.client.element.TextWindowElement;
import net.createmod.ponder.impl.client.registration.PonderLocalization;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import org.joml.Vector2i;
import org.joml.Vector2ic;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

// These probes need the physical client classes and are never registered on a dedicated server.
public final class PonderSceneVerifyProbes {
    private PonderSceneVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/formation_preview/client_scene", PonderSceneVerifyProbes::scene),
                Probe.of("verify/formation_preview/client_picking", PonderSceneVerifyProbes::picking),
                Probe.of("verify/formation_preview/client_tooltip", PonderSceneVerifyProbes::tooltip));
    }

    private static PonderScene fixture(ProbeContext context) {
        PonderLevel world = new PonderLevel(BlockPos.ZERO, context.level());
        PonderScene scene = new PonderScene(world, new PonderLocalization(), "mxt",
                Identifier.fromNamespaceAndPath("mxt", "formation_plate"), List.of(), List.of());
        FormationPreview preview = FormationPreview.prepare(List.of(
                new RequiredBlock(new BlockPos(-1, 0, -1), Blocks.STONE.defaultBlockState()),
                new RequiredBlock(new BlockPos(1, 2, 1), Blocks.GOLD_BLOCK.defaultBlockState()))).orElseThrow();
        new FormationPonderScene(Component.literal("Probe formation"), preview).program(scene.builder(), scene.getSceneBuildingUtil());
        scene.begin();
        return scene;
    }

    private static void scene(ProbeContext context) {
        PonderScene scene = fixture(context);
        for (int i = 0; i < 2000; i++) scene.tick();
        leg(context, "description does not extend timeline", scene.isFinished() && scene.getTotalTime() == 85);
        leg(context, "description remains visible after playback", descriptionVisible(scene));
        leg(context, "floor remains in replay backup", scene.getWorld().getBlockState(BlockPos.ZERO).is(Blocks.SNOW_BLOCK)
                && scene.getWorld().getBlockState(new BlockPos(1, 0, 0)).is(Blocks.WHITE_CONCRETE));
        scene.begin();
        scene.tick();
        leg(context, "replay restores a single visible description", descriptionVisible(scene));
        for (int i = 0; i < 2000; i++) scene.tick();
        leg(context, "replayed description has no timeout", descriptionVisible(scene));
    }

    private static boolean descriptionVisible(PonderScene scene) {
        List<TextWindowElement> descriptions = scene.getElements().stream().filter(TextWindowElement.class::isInstance)
                .map(TextWindowElement.class::cast).toList();
        return descriptions.size() == 1 && descriptions.getFirst().isVisible()
                && descriptions.getFirst().getFade(0) == 1 && descriptions.getFirst().getFade(1) == 1;
    }

    private static void picking(ProbeContext context) {
        PonderScene scene = fixture(context);
        for (int i = 0; i < 100; i++) scene.tick();
        BlockPos target = new BlockPos(2, 3, 2);
        PonderScene.SceneTransform transform = scene.getTransform();
        for (int guiScale : new int[]{1, 2, 4}) {
            transform.updateScreenParams(641, 359, 0, guiScale);
            Vec2 projected = transform.sceneToScreen(Vec3.atCenterOf(target), 0);
            AtomicReference<BlockPos> picked = new AtomicReference<BlockPos>();
            PonderPickingCompatibility.withGuiCoordinates(scene, () -> {
                Pair<ItemStack, BlockPos> result = scene.rayTraceScene(transform.screenToScene(projected.x, projected.y, 1000, 0),
                        transform.screenToScene(projected.x, projected.y, -100, 0));
                picked.set(result.getSecond());
                leg(context, "native pick item at GUI scale " + guiScale, result.getFirst().is(Items.GOLD_BLOCK));
            });
            leg(context, "projected block hit at GUI scale " + guiScale, target.equals(picked.get()));
            leg(context, "render dimensions restored at GUI scale " + guiScale, transform.guiScale() == guiScale
                    && transform.width() == 641 * guiScale && transform.height() == 359 * guiScale);
        }
        RuntimeException marker = new IllegalStateException("probe");
        try {
            PonderPickingCompatibility.withGuiCoordinates(scene, () -> { throw marker; });
            leg(context, "picking failure propagates", false);
        } catch (RuntimeException failure) {
            leg(context, "picking failure propagates", failure == marker);
        }
        leg(context, "picking failure restores rendering scale", transform.guiScale() == 4
                && transform.width() == 2564 && transform.height() == 1436);
    }

    private static void tooltip(ProbeContext context) {
        Font font = Minecraft.getInstance().font;
        ItemStack stack = new ItemStack(Items.GOLD_BLOCK);
        List<ClientTooltipComponent> components = List.of(ClientTooltipComponent.create(Component.literal("Gold block").getVisualOrderText()));
        ClientTooltipPositioner positioner = DefaultTooltipPositioner.INSTANCE;
        RenderTooltipEvent.Pre event = new RenderTooltipEvent.Pre(stack, null, 0, 0, 640, 360, font, components, positioner);
        PonderTooltipCompatibility.reposition(event, 240, 120);
        Vector2ic position = positioner.positionTooltip(640, 360, event.getX(), event.getY(), 80, 20);
        leg(context, "deferred tooltip follows mouse", position.x() == 252 && position.y() == 108);
        leg(context, "tooltip contents and styling preserved", event.getItemStack() == stack && event.getFont() == font
                && event.getComponents().equals(components) && event.getTooltipPositioner() == positioner && !event.isCanceled());
        RenderTooltipEvent.Pre moved = new RenderTooltipEvent.Pre(stack, null, 0, 0, 640, 360, font, components, positioner);
        PonderTooltipCompatibility.reposition(moved, 380, 200);
        Vector2ic movedPosition = positioner.positionTooltip(640, 360, moved.getX(), moved.getY(), 80, 20);
        leg(context, "next tooltip follows moved mouse", movedPosition.x() == 392 && movedPosition.y() == 188);
        RenderTooltipEvent.Pre edge = new RenderTooltipEvent.Pre(stack, null, 0, 0, 640, 360, font, components, positioner);
        PonderTooltipCompatibility.reposition(edge, 635, 355);
        Vector2ic edgePosition = positioner.positionTooltip(640, 360, edge.getX(), edge.getY(), 80, 20);
        leg(context, "native tooltip stays inside right and bottom edges", edgePosition.x() == 543 && edgePosition.y() == 337);
        RenderTooltipEvent.Pre anchored = new RenderTooltipEvent.Pre(stack, null, 45, 0, 640, 360, font, components, positioner);
        PonderTooltipCompatibility.reposition(anchored, 240, 120);
        leg(context, "explicit tooltip anchor preserved", anchored.getX() == 45 && anchored.getY() == 0);
        ClientTooltipPositioner custom = (width, height, x, y, tooltipWidth, tooltipHeight) -> new Vector2i(4, 4);
        RenderTooltipEvent.Pre customEvent = new RenderTooltipEvent.Pre(stack, null, 0, 0, 640, 360, font, components, custom);
        PonderTooltipCompatibility.reposition(customEvent, 240, 120);
        leg(context, "custom tooltip positioner preserved", customEvent.getX() == 0 && customEvent.getY() == 0
                && customEvent.getTooltipPositioner() == custom);
    }

    private static void leg(ProbeContext context, String name, boolean success) {
        context.check(name, success);
        context.note("ponder scene " + name + ": " + (success ? "OK" : "MISMATCH"));
    }
}
