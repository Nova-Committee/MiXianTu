package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.event.FormationEvent.Activate;
import com.iafenvoy.mxt.event.FormationEvent.Deactivate;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.formation.FormationService.ActivateResult;
import com.iafenvoy.mxt.runtime.world.AuraChunkTicker;
import com.iafenvoy.mxt.util.formula.FormulaContext;
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

    public static Result activate(ServerLevel level, BlockPos controller, Identifier id, Formation definition,
                                  ResourceHolderAttachment resources, FormulaContext context) {
        return activate(level, controller, id, definition, resources, context, null);
    }

    public static Result activate(ServerLevel level, BlockPos controller, Identifier id, Formation definition,
                                  ResourceHolderAttachment resources, FormulaContext context, UUID owner) {
        FormationWorldAttachment world = level.getData(MxtAttachments.FORMATION_WORLD);
        if (world.get(controller).isPresent()) return Result.rejected(Failure.OCCUPIED, null);
        if (!FormationStructureValidator.of(definition).matches(level, controller, definition))
            return Result.rejected(Failure.INVALID_STRUCTURE, null);
        // Jurisdiction before payment: a ward this server will not allow here must not charge for the
        // attempt. Both rules apply only to formations that carry a ward at all.
        if (FormationProtection.hasProtection(definition)) {
            if (FormationProtection.claimsOnlyRefuses(level, controller))
                return Result.rejected(Failure.NOT_CLAIMED, null);
            if (FormationProtection.foreignClaimRefuses(level, controller, owner))
                return Result.rejected(Failure.FOREIGN_CLAIM, null);
        }
        double radius = definition.radius().evaluate(context);
        if (!Double.isFinite(radius) || radius <= 0.0D) return Result.rejected(Failure.ACTIVATION_FAILED, null);
        FormationInstance preview = owner == null ? new FormationInstance(id, radius) : new FormationInstance(id, radius, owner);
        if (NeoForge.EVENT_BUS.post(new Activate(level, controller, preview)).isCanceled())
            return Result.rejected(Failure.CANCELLED, null);
        ActivateResult activated = FormationService.activate(id, definition, resources, context, owner);
        if (!activated.active()) return Result.rejected(Failure.ACTIVATION_FAILED, activated.failedResource());
        if (!world.put(controller, activated.instance()))
            throw new IllegalStateException("Formation controller became occupied during activation");
        // A ward whose delegation cannot be honoured falls back to its own flags; the report belongs here,
        // where the definition was just chosen.
        FormationProtection.warnIfDelegationFallsBack(id, definition);
        // The formation now absorbs the block emitters inside its radius, so every chunk it reaches has to
        // rebuild its block aura, or the absorbed ones would be counted in the shared stock as well.
        invalidateAura(level, controller, activated.instance().radius());
        definition.activateAction().execute(level, controller, context);
        return Result.activated(activated.instance());
    }

    // The single teardown path, so the deactivate action and the Deactivate event cannot drift apart between
    // callers. Entities the formation was tracking are released first, because deactivate_action is a block
    // action and cannot see them.
    public static boolean deactivate(ServerLevel level, BlockPos controller) {
        FormationInstance instance = level.getData(MxtAttachments.FORMATION_WORLD).remove(controller).orElse(null);
        if (instance == null) return false;
        // The emitters this formation was absorbing return to the environment, so the chunks it covered
        // have to rebuild their block aura.
        invalidateAura(level, controller, instance.radius());
        FormationEntityActions.releaseTracked(level, controller, instance);
        MxtDatapackRegistries.get(MxtResourceKeys.FORMATION, instance.formation())
                .ifPresent(definition -> definition.deactivateAction().execute(level, controller, FormulaContext.of(level)));
        NeoForge.EVENT_BUS.post(new Deactivate(level, controller, instance));
        return true;
    }

    // Queues a block-aura rebuild for every chunk a formation's radius reaches, on both ends of its life, since
    // which emitters are absorbed is a property of the rebuilt cache. A dirty mark rather than a scan, so
    // activation never reads chunks during the click.
    private static void invalidateAura(ServerLevel level, BlockPos controller, double radius) {
        int minChunkX = (int) Math.floor((controller.getX() - radius) / 16.0D);
        int maxChunkX = (int) Math.floor((controller.getX() + radius) / 16.0D);
        int minChunkZ = (int) Math.floor((controller.getZ() - radius) / 16.0D);
        int maxChunkZ = (int) Math.floor((controller.getZ() + radius) / 16.0D);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                AuraChunkTicker.markDirty(level, new BlockPos(chunkX << 4, controller.getY(), chunkZ << 4));
            }
        }
    }

    public enum Failure {OCCUPIED, INVALID_STRUCTURE, NOT_CLAIMED, FOREIGN_CLAIM, ACTIVATION_FAILED, CANCELLED}

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
