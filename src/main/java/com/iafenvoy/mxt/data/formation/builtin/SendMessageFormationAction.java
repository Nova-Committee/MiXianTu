package com.iafenvoy.mxt.data.formation.builtin;

import com.iafenvoy.mxt.data.MessageChannel;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.formation.FormationIntervals;
import com.iafenvoy.mxt.runtime.formation.FormationActionContext;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells the entities a formation covers something. Runs per covered entity, so the message is naturally one per
 * person rather than one per array period.
 * <p>
 * The body is a bare string read as a translation key or an object read as a full component, the same convention
 * item names use. {@code recipients} is asked per entity, so a formation can warn only the people it is about to
 * hurt, or only the ones it is not helping.
 */
public record SendMessageFormationAction(Component message, MessageChannel channel, EntityCondition recipients,
                                         int intervalTicks) implements FormationActionType {
    public static final MapCodec<SendMessageFormationAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("message").forGetter(SendMessageFormationAction::message),
            MessageChannel.CODEC.optionalFieldOf("channel", MessageChannel.CHAT).forGetter(SendMessageFormationAction::channel),
            EntityCondition.optionalCodec("recipients").forGetter(SendMessageFormationAction::recipients),
            FormationIntervals.CODEC.optionalFieldOf("interval_ticks", FormationIntervals.PERIOD).forGetter(SendMessageFormationAction::intervalTicks)
    ).apply(i, SendMessageFormationAction::new));

    @Override
    public MapCodec<SendMessageFormationAction> codec() {
        return CODEC;
    }

    // The array's own beat has nobody to address: a message names the entity it is about, and only a player can be
    // its recipient.
    @Override
    public void interval(FormationActionContext context) {
        if (!(context.entity() instanceof ServerPlayer player)) return;
        if (!this.recipients.test(player, context.entityContext())) return;
        player.sendSystemMessage(this.message, this.channel.actionBar());
    }
}
