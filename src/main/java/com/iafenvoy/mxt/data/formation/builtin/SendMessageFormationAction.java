package com.iafenvoy.mxt.data.formation.builtin;

import com.iafenvoy.mxt.data.MessageChannel;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;

/**
 * Tells the entities a formation covers something. Runs per covered entity, so the message is naturally one per
 * person rather than one per array tick.
 * <p>
 * The body is a bare string read as a translation key or an object read as a full component, the same convention
 * item names use. {@code recipients} is asked per entity, so a formation can warn only the people it is about to
 * hurt, or only the ones it is not helping.
 */
public record SendMessageFormationAction(Component message, MessageChannel channel,
                                         EntityCondition recipients) implements FormationActionType {
    public static final MapCodec<SendMessageFormationAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("message").forGetter(SendMessageFormationAction::message),
            MessageChannel.CODEC.optionalFieldOf("channel", MessageChannel.CHAT).forGetter(SendMessageFormationAction::channel),
            EntityCondition.optionalCodec("recipients").forGetter(SendMessageFormationAction::recipients)
    ).apply(i, SendMessageFormationAction::new));

    @Override
    public MapCodec<SendMessageFormationAction> codec() {
        return CODEC;
    }
}
