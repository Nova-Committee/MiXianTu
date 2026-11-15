package com.iafenvoy.mxt.screen.ponder;

import com.iafenvoy.mxt.data.formation.Formation.RequiredBlock;
import com.iafenvoy.mxt.runtime.formation.FormationPreview;
import net.createmod.ponder.api.client.level.PonderLevel;
import net.createmod.ponder.api.client.scene.SceneBuilder;
import net.createmod.ponder.api.client.scene.SceneBuildingUtil;
import net.createmod.ponder.api.client.scene.Selection;
import net.createmod.ponder.impl.client.element.TextWindowElement;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import java.util.TreeMap;

public record FormationPonderScene(Component title, FormationPreview preview) {
    public void program(SceneBuilder builder, SceneBuildingUtil util) {
        builder.title("formation_preview", "Formation Structure Preview");
        PonderLevel world = builder.getScene().getWorld();
        if (world != null) {
            for (RequiredBlock block : this.preview.displayBlocks())
                world.setBlock(block.offset(), block.state(), Block.UPDATE_CLIENTS);
            world.createBackup();
        }
        builder.configureBasePlate(-FormationPreview.BASE_PLATE_BORDER, -FormationPreview.BASE_PLATE_BORDER,
                this.preview.basePlateSize());
        builder.scaleSceneView(Math.min(1, 6f / Math.max(this.preview.basePlateSize(), this.preview.height())));
        builder.setSceneOffsetY(-this.preview.height() / 2f);
        builder.removeShadow();
        builder.setNextUpEnabled(false);
        builder.showBasePlate();
        TreeMap<Integer, Selection> layers = new TreeMap<Integer, Selection>();
        for (RequiredBlock block : this.preview.blocks())
            layers.merge(block.offset().getY(), util.select().position(block.offset()), Selection::add);
        TextWindowElement description = new TextWindowElement();
        description.builder(builder.getScene()).independent(65).text("Structure: %s", this.title.getString());
        builder.addInstruction(scene -> {
            // Set both interpolation samples; no timed instruction removes this element.
            description.setFade(1);
            description.setFade(1);
            scene.addElement(description);
        });
        builder.idle(5);
        for (Selection layer : layers.values()) {
            builder.addKeyframe();
            builder.world().showSection(layer, Direction.DOWN);
            builder.idle(20);
        }
        builder.idle(40);
        builder.markAsFinished();
    }
}
