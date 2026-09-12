package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.data.forging.ForgingMaterial;
import com.iafenvoy.mxt.event.ForgingEvent;
import com.iafenvoy.mxt.runtime.forging.ForgingService.Failure;
import com.iafenvoy.mxt.runtime.forging.ForgingWorkstationService.StartupMaterials;
import com.mojang.serialization.Codec;
import net.minecraft.world.Container;
import net.neoforged.bus.api.ICancellableEvent;

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

    /**
     * Posts a deciding event exactly as the service does, for the listener-failure rule.
     *
     * <p>The audit needs a real dispatch: whether a throwing listener becomes a refusal rather than an
     * escaping exception is a property of the post site, not of anything it could compute. The event it
     * passes may carry a hollow payload - only the dispatch is under test - but the bus, the listener list
     * and the handler are the live ones.</p>
     *
     * @return the refusal the service would report, or {@code null} when the operation may continue
     */
    public static <T extends ForgingEvent & ICancellableEvent> Failure postRefusalForAudit(T event) {
        return ForgingService.postEvent(event);
    }

    /**
     * Posts a notification event exactly as the service does, for the other half of the same rule.
     */
    public static void postNotificationForAudit(ForgingEvent event) {
        ForgingService.notifyListeners(event);
    }
}
