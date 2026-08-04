package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.event.FormationEvent.Activate;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.formation.FormationService.ActivateResult;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;

import java.util.UUID;

/**
 * Bridges formation transactions to the persistent ServerLevel formation attachment.
 */
public final class FormationWorldService {
    private FormationWorldService() {
    }

    public static Result activate(ServerLevel level, BlockPos controller, Identifier id, Formation definition,
                                  ResourceHolderData resources, FormulaContext context) {
        return activate(level, controller, id, definition, resources, context, null);
    }

    public static Result activate(ServerLevel level, BlockPos controller, Identifier id, Formation definition,
                                  ResourceHolderData resources, FormulaContext context, UUID owner) {
        FormationWorldData world = level.getData(MxtAttachments.FORMATION_WORLD);
        if (world.get(controller).isPresent()) return Result.rejected(Failure.OCCUPIED, null);
        if (!FormationStructureValidator.TEMPLATE.matches(level, controller, definition))
            return Result.rejected(Failure.INVALID_STRUCTURE, null);
        double radius = definition.radius().evaluate(context);
        if (!Double.isFinite(radius) || radius <= 0.0D) return Result.rejected(Failure.ACTIVATION_FAILED, null);
        FormationInstance preview = owner == null ? new FormationInstance(id, radius) : new FormationInstance(id, radius, owner);
        if (NeoForge.EVENT_BUS.post(new Activate(level, controller, id, preview)).isCanceled())
            return Result.rejected(Failure.CANCELLED, null);
        ActivateResult activated = FormationService.activate(id, definition, resources, context, owner);
        if (!activated.active()) return Result.rejected(Failure.ACTIVATION_FAILED, activated.failedResource());
        if (!world.put(controller, activated.instance()))
            throw new IllegalStateException("Formation controller became occupied during activation");
        definition.activateAction().execute(level, controller, context);
        return Result.activated(activated.instance());
    }

    public static MaintainResult maintain(ServerLevel level, BlockPos controller, Formation definition,
                                          ResourceHolderData resources, FormulaContext context) {
        FormationWorldData world = level.getData(MxtAttachments.FORMATION_WORLD);
        FormationInstance instance = world.get(controller).map(FormationInstance::restore).orElse(null);
        if (instance == null) return MaintainResult.missingResult();
        FormationService.MaintainResult result = FormationService.maintain(instance, definition, resources, context);
        if (instance.active()) world.replace(controller, instance);
        else {
            world.remove(controller);
            definition.deactivateAction().execute(level, controller, context);
        }
        return new MaintainResult(result.maintained(), result.deactivated(), false, result.failedResource());
    }

    public enum Failure {OCCUPIED, INVALID_STRUCTURE, ACTIVATION_FAILED, CANCELLED}

    public record Result(FormationInstance instance, Failure failure, Identifier failedResource) {
        private static Result activated(FormationInstance instance) {
            return new Result(instance, null, null);
        }

        private static Result rejected(Failure failure, Identifier resource) {
            return new Result(null, failure, resource);
        }

        public boolean active() {
            return this.instance != null;
        }
    }

    public record MaintainResult(boolean maintained, boolean deactivated, boolean missing, Identifier failedResource) {
        private static MaintainResult missingResult() {
            return new MaintainResult(false, false, true, null);
        }
    }
}
