package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.data.forging.ForgingMaterial;
import com.iafenvoy.mxt.runtime.forging.ForgingWorkstationService.StartupMaterials;
import com.mojang.serialization.Codec;
import net.minecraft.world.Container;

import java.util.List;

/**
 * Exposes package-private forging rules to the server audit in {@code mxt_test}.
 *
 * <p>Mirrors {@code AuraZonePriorityProbe}: the audit must be able to exercise the real ordering
 * and matching helpers rather than a reimplementation, and those helpers are intentionally not
 * part of the public API.</p>
 */
public final class ForgingProbe {
    private ForgingProbe() {
    }

    /**
     * Whether the container covers the requirement as an order-independent multiset.
     */
    public static boolean materialsCovered(Container container, List<ForgingMaterial> requirement) {
        return StartupMaterials.resolve(container, requirement) != null;
    }

    /**
     * Applies the same removal the workstation performs, for atomicity checks.
     */
    public static boolean consumeMaterials(Container container, List<ForgingMaterial> requirement) {
        StartupMaterials materials = StartupMaterials.resolve(container, requirement);
        if (materials == null) return false;
        materials.consume(container);
        return true;
    }

    /**
     * The codec used by a {@code ForgingMaterial} entry, for round-trip checks.
     */
    public static Codec<ForgingMaterial> materialCodec() {
        return ForgingMaterial.CODEC;
    }

    /**
     * Normalises a blueprint step limit the same way {@code ForgingBlueprint#plan} does.
     */
    public static int planMaxSteps(int blueprintMaxSteps) {
        return blueprintMaxSteps == 0 ? Integer.MAX_VALUE : blueprintMaxSteps;
    }
}
