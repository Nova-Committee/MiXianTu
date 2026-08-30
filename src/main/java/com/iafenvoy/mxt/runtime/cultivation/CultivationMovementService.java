package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Synchronizes the server-authoritative cultivation movement rule through vanilla attributes.
 */
public final class CultivationMovementService {
    public static final Identifier MODIFIER_ID = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "cultivation/movement_speed");
    private static final double SPEED_MULTIPLIER = -0.75D;

    private CultivationMovementService() {
    }

    /**
     * Applies or removes the temporary movement modifier from the server's current rule.
     */
    public static void reconcile(Player player) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) return;
        boolean shouldAllowMovement = player.getData(MxtAttachments.CULTIVATION).cultivating()
                && MxtServerConfig.allowCultivationMovement();
        if (shouldAllowMovement) {
            AttributeModifier current = movementSpeed.getModifier(MODIFIER_ID);
            if (current == null || current.amount() != SPEED_MULTIPLIER
                    || current.operation() != Operation.ADD_MULTIPLIED_TOTAL) {
                movementSpeed.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER_ID, SPEED_MULTIPLIER,
                        Operation.ADD_MULTIPLIED_TOTAL));
            }
        } else {
            movementSpeed.removeModifier(MODIFIER_ID);
        }
    }

    /**
     * Uses the synchronized vanilla attribute rather than a local config value, so remote
     * clients predict the server's rule correctly.
     */
    public static boolean isMovementAllowed(Player player) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        return movementSpeed != null && movementSpeed.getModifier(MODIFIER_ID) != null;
    }

    public static void clear(Player player) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) movementSpeed.removeModifier(MODIFIER_ID);
    }
}
