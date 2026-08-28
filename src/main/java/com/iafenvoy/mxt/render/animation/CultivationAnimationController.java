package com.iafenvoy.mxt.render.animation;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.mixin.ClientInputAccessor;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.cultivation.CultivationMovementService;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client PAL layer and input suppression for the synchronized cultivation mode.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class CultivationAnimationController extends PlayerAnimationController {
    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "cultivation");
    private static final Identifier ANIMATION_ID = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "animation.mxt.cultivate");
    private static final Map<UUID, Boolean> LAST_MODE = new HashMap<>();
    private boolean cultivating;

    public CultivationAnimationController(Avatar avatar) {
        super(avatar, (controller, state, setter) -> PlayState.STOP);
    }

    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, 50, CultivationAnimationController::new);
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (event.getEntity() != Minecraft.getInstance().player || !isCultivating(event.getEntity())
                || CultivationMovementService.isMovementAllowed(event.getEntity())) return;
        event.getInput().keyPresses = Input.EMPTY;
        ((ClientInputAccessor) event.getInput()).mxt$setMoveVector(Vec2.ZERO);
        event.getEntity().setDeltaMovement(0.0D, event.getEntity().getDeltaMovement().y(), 0.0D);
    }

    @SubscribeEvent
    public static void onClientTick(Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            LAST_MODE.clear();
            return;
        }
        for (Player player : minecraft.level.players()) {
            boolean active = isCultivating(player);
            Boolean previous = LAST_MODE.put(player.getUUID(), active);
            if (previous == null || previous != active) player.refreshDimensions();
            if (PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER_ID) instanceof CultivationAnimationController controller)
                controller.update(active);
        }
    }

    private static boolean isCultivating(Player player) {
        return player.getData(MxtAttachments.SPIRIT_DATA).cultivating();
    }

    private void update(boolean active) {
        if (this.cultivating == active) return;
        this.cultivating = active;
        if (active && PlayerAnimResources.hasAnimation(ANIMATION_ID)) this.triggerAnimation(ANIMATION_ID);
        else this.stopTriggeredAnimation();
    }
}
