package com.iafenvoy.mxt.runtime.formation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.permissions.Permissions;

/**
 * Who a formation treats as friendly.
 *
 * <p>Every friendly-fire decision goes through here so that the answer has exactly one home.</p>
 *
 * <p><b>TODO(队友保护): 队友保护尚未设计，后续再设计。</b> 现在唯一的友方就是阵法所有者本人。设计落定后在这里接入，
 * 待定要素：</p>
 * <ul>
 *   <li><b>关系来源</b>：原版队伍（{@code mxt:team} 条件已有）、宗门（{@code SectAttachment}）、自定义友好关系，
 *       还是三者组合；</li>
 *   <li><b>暴露方式</b>：新增一个 {@code mxt:formation_ally} 条件，还是让伤害/效果路径自动过滤友方；</li>
 *   <li><b>可配置性</b>：是否给 {@code formation} 加一个 {@code friendly_fire} 之类字段让数据包自己选。</li>
 * </ul>
 *
 * <p><b>约束</b>：不要改 {@code mxt:formation_owner} 的语义 —— 它只回答"是不是阵主"。队友保护应当是它旁边的
 * 另一个判定，否则数据包无法分别表达"只排除阵主"和"排除所有友方"。</p>
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
     * <p><b>TODO(队友保护):</b> 队友 / 宗门成员应当也算"可以拆除"。设计落定后在 {@link #isOwner} 旁边补一条，
     * 不要放宽这里的语义。</p>
     */
    public static boolean canDismantle(FormationInstance instance, ServerPlayer player) {
        if (instance.owner().isEmpty()) return true;
        return instance.owner().filter(player.getUUID()::equals).isPresent()
                || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
}
