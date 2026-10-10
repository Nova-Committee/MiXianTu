package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.MessageChannel;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.Context;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.iafenvoy.mxt.data.context.action.BlockActionContext;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.context.action.ItemActionContext;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Sends one message to players. The body is a bare string read as a translation key or an object read as a full
 * component, the same convention item names use, so a pack that ships no lang writes plain text.
 * <p>
 * One class covers all four action kinds the way {@code NoOpAction} does, because the kinds differ only in who the
 * message can be addressed to. {@code target} names that addressee, and it has to be a choice rather than a fixed
 * rule: an entity or item action has one entity at hand, a bi-entity action has two, and a block action has only a
 * place. {@code ALL} is the one addressee every kind can honour, because it asks the level for its players rather
 * than the context for an entity.
 * <p>
 * {@code recipients} is then asked per candidate player and not once for the whole send, which is what makes
 * "tell only my allies" expressible; the addressee is a candidate like any other unless the condition drops it.
 * Server only: a client level has no player list and must not pretend it delivered anything.
 */
public record SendMessageAction(Component message, MessageChannel channel, MessageTarget target,
                                EntityCondition recipients) implements EntityAction, BlockAction, ItemAction, BiEntityAction {
    public static final MapCodec<SendMessageAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("message").forGetter(SendMessageAction::message),
            MessageChannel.CODEC.optionalFieldOf("channel", MessageChannel.CHAT).forGetter(SendMessageAction::channel),
            MessageTarget.CODEC.optionalFieldOf("target", MessageTarget.SELF).forGetter(SendMessageAction::target),
            EntityCondition.optionalCodec("recipients").forGetter(SendMessageAction::recipients)
    ).apply(i, SendMessageAction::new));

    @Override
    public void execute(@NotNull EntityActionContext context) {
        send(context, context.entity().level(), context.entity(), null);
    }

    @Override
    public void execute(@NotNull ItemActionContext context) {
        send(context, context.holder().level(), context.holder(), null);
    }

    @Override
    public void execute(@NotNull BlockActionContext context) {
        // A place has no addressee of its own, so only ALL can find anyone.
        send(context, context.level(), null, null);
    }

    @Override
    public void execute(@NotNull BiEntityActionContext context) {
        send(context, context.actor().level(), context.actor(), context.target());
    }

    private void send(Context context, Level level, @Nullable Entity actor, @Nullable Entity other) {
        if (!(level instanceof ServerLevel server)) return;
        // Only ALL is answered by the level's player list. A named addressee that turned out not to be a player
        // must send nothing: falling back to the whole level would turn "tell the other side" into a broadcast.
        List<ServerPlayer> candidates;
        if (this.target == MessageTarget.ALL) candidates = server.players();
        else candidates = this.target.player(actor, other).<List<ServerPlayer>>map(List::of).orElseGet(List::of);
        for (ServerPlayer player : candidates)
            if (this.recipients.test(player, context)) player.sendSystemMessage(this.message, this.channel.actionBar());
    }

    @Override
    public @NotNull MapCodec<SendMessageAction> codec() {
        return CODEC;
    }

    /** Who the message is addressed to. ALL resolves through the level, so it works even for a place. */
    public enum MessageTarget implements StringRepresentable {
        SELF,
        OTHER,
        ALL;

        public static final Codec<MessageTarget> CODEC = StringRepresentable.fromEnum(MessageTarget::values);

        // The player a target names: the acting entity for SELF, the far side of a pair for OTHER, and nobody for
        // ALL because that one is answered by the level's player list instead.
        public Optional<ServerPlayer> player(@Nullable Entity actor, @Nullable Entity other) {
            return switch (this) {
                case SELF -> actor instanceof ServerPlayer server ? Optional.of(server) : Optional.empty();
                case OTHER -> other instanceof ServerPlayer server ? Optional.of(server) : Optional.empty();
                case ALL -> Optional.empty();
            };
        }

        @Override
        public @NotNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
