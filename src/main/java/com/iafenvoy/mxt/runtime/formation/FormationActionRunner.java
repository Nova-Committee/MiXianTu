package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.action.builtin.entity.ApplyEffectAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.formation.AttackFormationAction;
import com.iafenvoy.mxt.data.formation.BuffFormationAction;
import com.iafenvoy.mxt.data.formation.BuffFormationAction.TargetMode;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.formation.RangeDisplayFormationAction;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.runtime.damage.DamageCalculationService;
import com.iafenvoy.mxt.runtime.friend.FriendService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Runs a formation's function modules against one entity, called from the periodic pass once per entity the
 * formation affects. Modules run before the definition's own {@code entity_tick_action}, so a hook sees the
 * state they left behind.
 */
public final class FormationActionRunner {
    private FormationActionRunner() {
    }

    /**
     * Runs the modules that act on the formation itself, once per period. Only the display module lives here;
     * the stock has no period of its own, because it is settled where the upkeep is charged.
     *
     * @return how many modules of that kind were reached, which is how the audit tells a module that ran from
     * one that is declared and never read; the ticker ignores it
     */
    public static int perPeriod(ServerLevel level, Formation definition, FormationInstance instance, BlockPos controller) {
        int reached = 0;
        for (FormationActionType module : definition.actions()) {
            if (module instanceof RangeDisplayFormationAction display) {
                FormationRangeDisplay.draw(level, display, controller, instance.radius());
                reached++;
            }
        }
        return reached;
    }

    public static void perEntity(Formation definition, FormationInstance instance,
                                 Entity entity, EntityActionContext context,
                                 @Nullable Entity owner, @Nullable UUID ownerId) {
        for (FormationActionType module : definition.actions()) {
            switch (module) {
                case AttackFormationAction attack -> attack(attack, entity, context, owner);
                case BuffFormationAction buff -> buff(buff, entity, context, instance, owner, ownerId);
                // The terrain ward is not per-entity work: it answers block events instead, and there is
                // no entity to act on. The default module declares nothing at all.
                case null, default -> {
                }
            }
        }
    }

    private static void attack(AttackFormationAction attack, Entity entity,
                               EntityActionContext context, @Nullable Entity owner) {
        if (!attack.targetCondition().test(entity, context)) return;
        double amount = attack.damage().evaluate(context.formula());
        if (Double.isFinite(amount) && amount > 0.0D)
            DamageCalculationService.deal(attack.attributeToOwner() ? owner : null, entity, amount,
                    attack.damageType(), context.formula());
        for (ApplyEffectAction effect : attack.effects()) effect.execute(context);
    }

    /**
     * Grants the module's abilities under the source convention, which is also what releases them: nothing
     * here does, because the ticker drops every ability of that source for an entity that leaves.
     */
    private static void buff(BuffFormationAction buff, Entity entity, EntityActionContext context,
                             FormationInstance instance, @Nullable Entity owner, @Nullable UUID ownerId) {
        Identifier source = FormationSources.of(instance.formation());
        if (!targets(buff.target(), entity, owner, ownerId)) {
            // An entity the module no longer has anything for must not keep the formation's grant, or losing
            // friend status while standing still leaves the gift behind. Releasing an unused source is cheap.
            FormationEntityActions.release(entity, source);
            return;
        }
        for (Holder<Ability> ability : buff.abilities()) {
            AbilityAttachment granted = entity.getData(MxtAttachments.ABILITY_HOLDER);
            if (granted.grant(ability, source) && entity instanceof LivingEntity living)
                AbilityEventBridge.rebuildTriggerSubscriptions(living);
        }
    }

    /**
     * Whether the module's benefit reaches this entity. {@code ALLIES} asks the friend system and leaves out an
     * unidentifiable entity, because handing a stranger the owner's bonus is the failure this avoids.
     */
    private static boolean targets(TargetMode mode, Entity entity,
                                   @Nullable Entity owner, @Nullable UUID ownerId) {
        return switch (mode) {
            case ALL -> true;
            case OWNER -> ownerId != null && ownerId.equals(entity.getUUID());
            case ALLIES -> ownerId != null && FriendService.identify(ownerId, owner, entity) == TriState.TRUE;
        };
    }
}
