package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.formation.Formation;
import com.iafenvoy.mxt.event.FormationEvent.Activate;
import com.iafenvoy.mxt.event.FormationEvent.Deactivate;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.failure.FormationWorldFailure;
import com.iafenvoy.mxt.runtime.formation.FormationService.ActivateResult;
import com.iafenvoy.mxt.util.MiscUtil;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;

import java.util.UUID;
import com.iafenvoy.mxt.runtime.failure.Failure;
import com.iafenvoy.mxt.runtime.failure.Failures;

/**
 * The two ends of a formation's life in the world: bringing one into the level index, and taking it out. Neither
 * half reads or writes resources - the payment and activation costs live in {@link FormationService} - and a
 * period's upkeep belongs to {@link FormationWorldTicker}.
 */
public final class FormationWorldService {
    private FormationWorldService() {
    }

    public static Result activate(ServerLevel level, BlockPos controller, Identifier id, Formation definition,
                                  ResourceHolderAttachment resources, FormulaContext context) {
        return activate(level, controller, id, definition, resources, context, null);
    }

    public static Result activate(ServerLevel level, BlockPos controller, Identifier id, Formation definition,
                                  ResourceHolderAttachment resources, FormulaContext context, UUID owner) {
        FormationWorldAttachment world = level.getData(MxtAttachments.FORMATION_WORLD);
        if (world.get(controller).isPresent()) return Result.rejected(FormationWorldFailure.OCCUPIED, null);
        if (!FormationStructureValidator.of(definition).matches(level, controller, definition))
            return Result.rejected(FormationWorldFailure.INVALID_STRUCTURE, null);
        // Jurisdiction before payment: a ward this server will not allow here must not charge for the
        // attempt. Both rules apply only to formations that carry a ward at all.
        if (FormationProtection.hasProtection(definition)) {
            if (FormationProtection.claimsOnlyRefuses(level, controller))
                return Result.rejected(FormationWorldFailure.NOT_CLAIMED, null);
            if (FormationProtection.foreignClaimRefuses(level, controller, owner))
                return Result.rejected(FormationWorldFailure.FOREIGN_CLAIM, null);
        }
        double radius = MiscUtil.positive(definition.radius().evaluate(context));
        if (radius <= 0.0D) return Result.rejected(FormationWorldFailure.ACTIVATION_FAILED, null);
        FormationInstance preview = owner == null ? new FormationInstance(id, radius) : new FormationInstance(id, radius, owner);
        if (NeoForge.EVENT_BUS.post(new Activate(level, controller, preview)).isCanceled())
            return Result.rejected(Failures.CANCELLED, null);
        ActivateResult activated = FormationService.activate(id, definition, resources, context, owner);
        if (!activated.active()) return Result.rejected(FormationWorldFailure.ACTIVATION_FAILED, activated.failedResource());
        if (!world.put(controller, activated.instance()))
            throw new IllegalStateException("Formation controller became occupied during activation");
        // Stamped before the opening beat, so a display module that animates from the moment of raising measures from
        // this one rather than from whenever its own first beat happens to land.
        activated.instance().activated(level.getGameTime());
        // A ward whose delegation cannot be honoured falls back to its own flags; the report belongs here,
        // where the definition was just chosen.
        FormationProtection.warnIfDelegationFallsBack(id, definition);
        FormationActionRunner.activate(definition, FormationActionContext.array(level, controller, activated.instance()));
        return Result.activated(activated.instance());
    }

    // The single teardown path, so the closing beat and the Deactivate event cannot drift apart between callers.
    // The entities the formation was tracking are released first, because its own closing beat is a block action and
    // cannot see them.
    public static boolean deactivate(ServerLevel level, BlockPos controller) {
        FormationInstance instance = level.getData(MxtAttachments.FORMATION_WORLD).remove(controller).orElse(null);
        if (instance == null) return false;
        Formation definition = MxtDatapackRegistries.get(level.registryAccess(), MxtResourceKeys.FORMATION, instance.formation())
                .orElse(null);
        FormationEntityActions.releaseTracked(level, controller, instance, definition);
        if (definition != null)
            FormationActionRunner.inactivate(definition, FormationActionContext.array(level, controller, instance));
        NeoForge.EVENT_BUS.post(new Deactivate(level, controller, instance));
        return true;
    }

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
}
