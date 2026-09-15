package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.action.builtin.entity.ApplyEffectAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.formation.AttackFormationAction;
import com.iafenvoy.mxt.data.formation.BuffFormationAction;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.runtime.friend.FriendService;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.TriState;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Runs a formation's function modules against one entity.
 *
 * <p>Called from the periodic pass, once per entity the formation affects, and the modules run before the
 * definition's own {@code entity_tick_action}: a hook is the place to react to what the array did, so it
 * should see the state the modules left behind rather than the state before them.</p>
 *
 * <p>Both halves of the judgement are already spent by the time this runs. Whether the entity is affected
 * at all — the friend rule, the stand-down — belongs to {@link FormationRelations} and gates this call;
 * a module only decides whether it has anything to do with an entity that is already in range.</p>
 */
public final class FormationActionRunner {
    private FormationActionRunner() {
    }

    public static void perEntity(ServerLevel level, Formation definition, FormationInstance instance,
                                 Entity entity, EntityActionContext context,
                                 @Nullable Entity owner, @Nullable UUID ownerId) {
        for (FormationActionType module : definition.actions()) {
            switch (module) {
                case AttackFormationAction attack -> attack(level, attack, entity, context, owner);
                case BuffFormationAction buff -> buff(buff, entity, context, instance, owner, ownerId);
                // The terrain ward is not per-entity work: it answers block events instead, and there is
                // no entity to act on. The default module declares nothing at all.
                case null, default -> {
                }
            }
        }
    }

    private static void attack(ServerLevel level, AttackFormationAction attack, Entity entity,
                               EntityActionContext context, @Nullable Entity owner) {
        if (!attack.targetCondition().test(entity, context)) return;
        double amount = attack.damage().evaluate(context.formula());
        if (Double.isFinite(amount) && amount > 0.0D)
            entity.hurtServer(level, damageSource(level, attack, owner), (float) amount);
        for (ApplyEffectAction effect : attack.effects()) effect.execute(context);
    }

    /**
     * The damage source a strike is attributed to.
     *
     * <p>Without a declared type this is the vanilla reading of "the owner hit it": {@code playerAttack}
     * carries the player as attacker, which is what makes a kill count as theirs. With no owner loaded
     * there is nobody to attribute to, so it falls back to the anonymous generic source — the same reading
     * {@code mxt:damage} has, and the only honest one when the array outlives its owner.</p>
     *
     * <p>{@code attribute_to_owner} only matters where there is a type to build from: the untyped branch
     * already chooses between attributed and generic by whether an owner exists, so the two never
     * disagree about which of them is in charge.</p>
     */
    private static DamageSource damageSource(ServerLevel level, AttackFormationAction attack, @Nullable Entity owner) {
        Entity cause = attack.attributeToOwner() ? owner : null;
        return attack.damageType()
                .map(type -> (DamageSource) new DamageSource(type, cause))
                .orElseGet(() -> switch (cause) {
                    case Player player -> level.damageSources().playerAttack(player);
                    // An owner is a player in play, but the rule is written for any living attacker: a
                    // mob attack carries the attacker too, which is what a non-player owner has to use to
                    // be credited at all.
                    case LivingEntity living -> level.damageSources().mobAttack(living);
                    case null, default -> level.damageSources().generic();
                });
    }

    /**
     * Grants the module's abilities, under the source a formation's grants are expected to use.
     *
     * <p>Nothing here releases them: that is what the source convention buys. The ticker already drops
     * every ability of that source for an entity that leaves, for one that was tracked when the formation
     * was torn down, and for a player the sweep finds outside — so a grant cannot outlive its array even
     * though the ability attachment is persistent.</p>
     */
    private static void buff(BuffFormationAction buff, Entity entity, EntityActionContext context,
                             FormationInstance instance, @Nullable Entity owner, @Nullable UUID ownerId) {
        Identifier source = FormationSources.of(instance.formation());
        if (!targets(buff.target(), entity, owner, ownerId)) {
            // An entity the module no longer has anything for must not keep the formation's grant: losing
            // friend status while standing still would otherwise leave the gift behind until the entity
            // happened to walk out. Releasing an unused source matches nothing, so a stranger costs one map
            // scan per period.
            //
            // A pack that also grants through the same source by hand is unaffected in practice: modules
            // run before the definition's own hook, so the hook's grant is re-made in the same period.
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
     * Whether the module's benefit reaches this entity.
     *
     * <p>{@code ALLIES} asks the friend system rather than assuming, and an unidentifiable entity is left
     * out: handing a stranger the owner's cultivation bonus is the failure this avoids, while the cost of
     * the other guess is that a friend no source knows about simply receives nothing. The owner is
     * included because the friend system answers yes for an entity and itself — {@code OWNER} is a
     * narrower way to say the same thing, not a different rule.</p>
     */
    private static boolean targets(BuffFormationAction.TargetMode mode, Entity entity,
                                   @Nullable Entity owner, @Nullable UUID ownerId) {
        return switch (mode) {
            case ALL -> true;
            case OWNER -> ownerId != null && ownerId.equals(entity.getUUID());
            case ALLIES -> ownerId != null && FriendService.identify(ownerId, owner, entity) == TriState.TRUE;
        };
    }
}
