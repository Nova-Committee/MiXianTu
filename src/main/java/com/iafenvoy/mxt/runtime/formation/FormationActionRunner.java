package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.action.builtin.entity.ApplyEffectAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.formation.FormationIntervals;
import com.iafenvoy.mxt.data.formation.builtin.AttackFormationAction;
import com.iafenvoy.mxt.data.formation.builtin.BuffFormationAction;
import com.iafenvoy.mxt.data.formation.builtin.BuffFormationAction.TargetMode;
import com.iafenvoy.mxt.data.formation.builtin.RangeDisplayFormationAction;
import com.iafenvoy.mxt.data.formation.builtin.SendMessageFormationAction;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.Sources;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.runtime.damage.DamageCalculationService;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Runs a formation's function modules against one entity, called from the periodic pass once per entity the
 * formation affects. Modules run before the definition's own {@code entity_tick_action}, so a hook sees the
 * state they left behind. A module that declares {@code interval_periods} is skipped on the periods it is not
 * due for, and the phase is read from the level clock, so the whole array fires together.
 */
public final class FormationActionRunner {
    private FormationActionRunner() {
    }

    // Runs the modules that act on the formation itself, once per period. Only the display module lives here;
    // the stock has no period of its own, because it is settled where the upkeep is charged. The return is how
    // the audit tells a module that ran from one that is declared and never read; the ticker ignores it.
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
                                 Entity entity, EntityActionContext context, FormationOwners owners) {
        // The phase comes from the level clock rather than from a counter kept per module, so every entity covered
        // by one formation, and every instance of one definition, throttle in step: a throttled message arrives for
        // the whole array at once instead of trickling out per entity.
        long gameTime = entity.level().getGameTime();
        for (FormationActionType module : definition.actions()) {
            switch (module) {
                case AttackFormationAction attack -> {
                    if (due(attack.intervalPeriods(), gameTime))
                        attack(attack, entity, context, owners.primaryEntity(entity.level()));
                }
                // Re-applying the grant is idempotent and the grant outlives a skipped period - only leaving the
                // formation releases it - so a throttle here only makes the array react more slowly to a target
                // becoming eligible, never drops a gift it already handed out.
                case BuffFormationAction buff -> {
                    if (due(buff.intervalPeriods(), gameTime)) buff(buff, entity, context, instance, owners);
                }
                case SendMessageFormationAction message -> {
                    if (due(message.intervalPeriods(), gameTime)) message(message, entity, context);
                }
                // The terrain ward is not per-entity work: it answers block events instead, and there is
                // no entity to act on. The default module declares nothing at all.
                case null, default -> {
                }
            }
        }
    }

    private static boolean due(int intervalPeriods, long gameTime) {
        return FormationIntervals.due(intervalPeriods, gameTime, FormationWorldTicker.PERIOD);
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

    // Addressed to the entity the formation is being run for, which is why this is per-entity work and not a
    // whole-level broadcast: each covered entity is told in turn.
    private static void message(SendMessageFormationAction module, Entity entity, EntityActionContext context) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (!module.recipients().test(player, context)) return;
        player.sendSystemMessage(module.message(), module.channel().actionBar());
    }

    // Grants under the source convention, which is also what releases them: nothing here does, because the
    // ticker drops every ability of that source for an entity that leaves.
    private static void buff(BuffFormationAction buff, Entity entity, EntityActionContext context,
                             FormationInstance instance, FormationOwners owners) {
        Identifier source = Sources.formation(instance.formation());
        if (!targets(buff.target(), entity, owners)) {
            // An entity the module no longer has anything for must not keep the formation's grant, or losing
            // friend status while standing still leaves the gift behind. Releasing an unused source is cheap.
            FormationEntityActions.release(entity, source);
            return;
        }
        for (Holder<Ability> ability : buff.abilities()) {
            AbilityAttachment granted = entity.getData(MxtAttachments.ABILITY_HOLDER);
            if (granted.grant(HolderHelper.id(ability), source) && entity instanceof LivingEntity living)
                AbilityEventBridge.rebuildTriggerSubscriptions(living);
        }
    }

    // ALLIES asks the friend system and leaves out an unidentifiable entity, because handing a stranger the
    // owner's bonus is the failure this avoids.
    private static boolean targets(TargetMode mode, Entity entity, FormationOwners owners) {
        return switch (mode) {
            case ALL -> true;
            case OWNER -> owners.contains(entity);
            case ALLIES -> owners.identify(entity.level(), entity) == TriState.TRUE;
        };
    }
}
