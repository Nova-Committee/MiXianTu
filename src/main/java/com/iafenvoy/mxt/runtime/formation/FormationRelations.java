package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.formation.AttackFormationAction;
import com.iafenvoy.mxt.runtime.friend.FriendService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Who a formation treats as friendly.
 *
 * <p>Every friendly-fire decision goes through here so that the answer has exactly one home.</p>
 *
 * <p>Where the formation <em>is</em> friendly is the formation's own declaration: {@link Formation#hostile()}
 * says whether its per-entity actions are meant to hurt. The alternative — inferring hostility from which
 * actions a pack used — cannot work, because the same damage can arrive through a builtin action, a nested
 * condition or a script, and a wrong guess either stops a healing array from healing or lets a damage
 * array through. <em>Who</em> is friendly is the friend system: {@link FriendService} asks
 * {@code FriendEvent.Relation} first, so another mod can answer, and falls back to the owner's own lists.
 * {@code config.mxt.server.formation.respect_friends} is the server's override: it turns the whole
 * judgement off without touching a single definition.</p>
 *
 * <p><b>约束</b>：不要改 {@code mxt:formation_owner} 的语义 —— 它只回答"是不是阵主"。队友保护是它旁边
 * 的另一个判定，数据包因此可以分别表达"只排除阵主"和"排除所有友方"。</p>
 *
 * <p><b>TODO(队友保护): 拆除权限尚未决定。</b> 好友是否也应当能拆掉别人的阵法，是另一个问题：那给的是破
 * 坏权而不是保护，且与"阵主或管理员"的现有语义冲突，所以这里没有跟着放宽。设计落定后在
 * {@link #canDismantle} 旁边补一条，不要放宽那里的语义。</p>
 */
public final class FormationRelations {
    private FormationRelations() {
    }

    /**
     * Whether the entity owns the formation under evaluation.
     */
    public static boolean isOwner(FormationCarrier carrier, Entity entity) {
        return carrier.owner().filter(entity.getUUID()::equals).isPresent();
    }

    /**
     * Whether a formation's per-entity actions apply to this entity.
     *
     * <p>A hostile formation spares whoever its owner counts as his own — that is the point of the flag,
     * and a formation that hurts its owner's friends is the friendly fire this exists to stop. A formation
     * that is not hostile affects everyone, friends included, because there is nothing to protect anybody
     * from.</p>
     *
     * <p>The owner needs no special case: {@link FriendService} answers yes for an entity and itself, so a
     * hostile formation spares the person who built it as well.</p>
     *
     * <p>The judgement is asked by owner <em>id</em> as well as owner entity, so an owner who is offline can
     * still be answered for by a source that keeps its own per-player data. Only when nobody can answer at
     * all — no owner recorded, or an absent owner and no source that knows the pair — does a hostile
     * formation stand down and affect nobody. That is the safe reading of "cannot tell": firing at everyone
     * would hit precisely the people the flag exists to protect, and picking a set to spare would be
     * guessing. An array that cannot tell a friend from a stranger does not fire at all, and the per-entity
     * half of the formation stops, which also releases anything it granted.</p>
     *
     * <p>A pack that would rather keep firing in that situation has two ways to say so: leave {@code hostile}
     * unset and write the judgement itself with {@code mxt:formation_ally}, whose out-of-context answer is
     * {@code false} and therefore takes the "not an ally" branch, or turn the server option off, which
     * restores the unconditional behaviour for every formation.</p>
     *
     * @param ownerId the owner's id, or null when the formation records no owner at all
     * @param owner   that player's entity, or null while they are not loaded
     */
    public static boolean affects(Formation definition, @Nullable UUID ownerId, @Nullable Entity owner, Entity entity) {
        if (!isHostile(definition) || !MxtServerConfig.formationRespectsFriends()) return true;
        if (ownerId == null) return false;
        return switch (FriendService.identify(ownerId, owner, entity)) {
            // A friend of the owner is spared.
            case TRUE -> false;
            // A stranger is affected.
            case FALSE -> true;
            // Nobody could identify this entity: stand down rather than fire blind.
            case DEFAULT -> false;
        };
    }

    /**
     * Whether a formation's per-entity work is meant to hurt.
     *
     * <p>Two ways to say yes, and they are not redundant. {@link Formation#hostile()} is how a definition
     * built out of the raw hooks declares intent, since nothing can recover it from an action tree. An
     * {@link AttackFormationAction} module declares the same thing by being what it is: a formation that
     * carries one exists to hurt people, and requiring a flag beside it would let the two disagree — a
     * module firing at everyone while the flag says to spare friends.</p>
     *
     * <p>The consequence is worth stating: a formation with an attack module spares friends from all of
     * its per-entity work, the hand-written hooks included, not only from the module's own damage. That is
     * the same reading {@code hostile} has always had, and the alternative — one flag per hook — is how a
     * definition ends up guarding half of itself.</p>
     */
    public static boolean isHostile(Formation definition) {
        if (definition.hostile()) return true;
        return definition.actions().stream().anyMatch(AttackFormationAction.class::isInstance);
    }

    /**
     * Whether the player may dismantle the formation at the controller.
     *
     * <p>An owner may always take down their own formation and an operator may take down any. An
     * ownerless instance belongs to nobody, so it is left open to anyone rather than stranded —
     * nothing is being taken from a player who never claimed it.</p>
     *
     * <p>Deliberately stricter than "no check at all": before this existed the plate simply refused
     * an occupied controller, and turning that refusal into an unconditional dismantle would have
     * handed every player a way to destroy anyone's formation.</p>
     *
     * <p><b>TODO(队友保护):</b> 好友 / 宗门成员是否也算"可以拆除"尚未决定，见类注释。落定后在
     * {@link #isOwner} 旁边补一条，不要放宽这里的语义。</p>
     */
    public static boolean canDismantle(FormationInstance instance, ServerPlayer player) {
        if (instance.owner().isEmpty()) return true;
        return instance.owner().filter(player.getUUID()::equals).isPresent()
                || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
}
