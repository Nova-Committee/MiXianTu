package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.data.formation.Formation;
import com.iafenvoy.mxt.event.FormationEvent.Activate;
import com.iafenvoy.mxt.event.FormationEvent.Deactivate;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.MxtProfilerSources;
import com.iafenvoy.mxt.runtime.resulting.failures.FormationWorldFailure;
import com.iafenvoy.saturn.attachment.ResourceHolderAttachment;
import com.iafenvoy.saturn.profiling.ProfilerScope;
import com.iafenvoy.saturn.profiling.ServerProfiling;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.Failures;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.iafenvoy.saturn.util.MiscUtil;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;

import java.util.UUID;

/**
 * The two ends of a formation's life in the world: bringing one into the level index, and taking it out. Neither
 * half reads or writes resources - the payment and activation costs live in {@link FormationService} - and a
 * period's upkeep belongs to {@link FormationWorldTicker}.
 */
public final class FormationWorldService {
    private FormationWorldService() {
    }

    public static Result<FormationInstance> activate(ServerLevel level, BlockPos controller, Identifier id, Formation definition,
                                                     ResourceHolderAttachment resources, FormulaContext context) {
        try (ProfilerScope _ = ServerProfiling.scope(MxtProfilerSources.FORMATION)) {
            return activate(level, controller, id, definition, resources, context, null);
        }
    }

    public static Result<FormationInstance> activate(ServerLevel level, BlockPos controller, Identifier id, Formation definition,
                                                     ResourceHolderAttachment resources, FormulaContext context, UUID owner) {
        try (ProfilerScope _ = ServerProfiling.scope(MxtProfilerSources.FORMATION)) {
            FormationWorldAttachment world = level.getData(MxtAttachments.FORMATION_WORLD);
            if (world.get(controller).isPresent()) return Result.failure(FormationWorldFailure.OCCUPIED);
            if (!FormationStructureValidator.of(definition).matches(level, controller, definition))
                return Result.failure(FormationWorldFailure.INVALID_STRUCTURE);
            // Jurisdiction before payment: a ward this server will not allow here must not charge for the
            // attempt. Both rules apply only to formations that carry a ward at all.
            if (FormationProtection.hasProtection(definition)) {
                if (FormationProtection.claimsOnlyRefuses(level, controller))
                    return Result.failure(FormationWorldFailure.NOT_CLAIMED);
                if (FormationProtection.foreignClaimRefuses(level, controller, owner))
                    return Result.failure(FormationWorldFailure.FOREIGN_CLAIM);
            }
            double radius = MiscUtil.positive(definition.radius().evaluate(context));
            if (radius <= 0.0D) return Result.failure(FormationWorldFailure.ACTIVATION_FAILED);
            FormationInstance preview = owner == null ? new FormationInstance(id, radius) : new FormationInstance(id, radius, owner);
            if (NeoForge.EVENT_BUS.post(new Activate(level, controller, preview)).isCanceled())
                return Result.failure(Failures.CANCELLED);
            Result<FormationInstance> activated = FormationService.activate(id, definition, resources, context, owner);
            if (activated instanceof Result.FailureResult<FormationInstance>(
                    Failure failure
            ))
                return Result.failure(FormationWorldFailure.ACTIVATION_FAILED, failure.resource().orElse(null));
            FormationInstance instance = activated.getData().orElseThrow();
            if (!world.put(controller, instance))
                throw new IllegalStateException("Formation controller became occupied during activation");
            // Stamped before the opening beat, so a display module that animates from the moment of raising measures from
            // this one rather than from whenever its own first beat happens to land.
            instance.activated(level.getGameTime());
            // A ward whose delegation cannot be honoured falls back to its own flags; the report belongs here,
            // where the definition was just chosen.
            FormationProtection.warnIfDelegationFallsBack(id, definition);
            FormationActionRunner.activate(definition, FormationActionContext.array(level, controller, instance));
            return Result.success(instance);
        }
    }

    // The single teardown path, so the closing beat and the Deactivate event cannot drift apart between callers.
    // The entities the formation was tracking are released first, because its own closing beat is a block action and
    // cannot see them.
    public static boolean deactivate(ServerLevel level, BlockPos controller) {
        try (ProfilerScope _ = ServerProfiling.scope(MxtProfilerSources.FORMATION)) {
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
    }


}
