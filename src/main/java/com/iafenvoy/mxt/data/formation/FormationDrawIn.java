package com.iafenvoy.mxt.data.formation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * What one order tells a client about a figure: which raise it belongs to, how much of the figure exists already, how
 * long a client that is not drawing it yet should take to finish it, and how long the order stays good for. The clock
 * that grows the figure is the client's own, so a draw-in is as smooth as the client's tick rate rather than as coarse
 * as the beats orders are sent on.
 */
public record FormationDrawIn(long raisedAt, float drawn, int drawInTicks, int holdTicks) {
    /**
     * How long a figure goes on being drawn after the last order, in client ticks. The floor keeps a figure alive
     * across a server that is running late; the ceiling keeps a pack that writes a huge {@code interval_ticks} from
     * leaving figures standing for minutes after their array is gone.
     */
    private static final int MIN_HOLD = 30;
    private static final int MAX_HOLD = 200;
    private static final int HOLD_PER_BEAT = 2;
    public static final Codec<FormationDrawIn> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("raised_at").forGetter(FormationDrawIn::raisedAt),
            Codec.floatRange(0.0F, 1.0F).fieldOf("drawn").forGetter(FormationDrawIn::drawn),
            Codec.intRange(0, FormationIntervals.MAX_TICKS).fieldOf("draw_in_ticks").forGetter(FormationDrawIn::drawInTicks),
            Codec.intRange(0, MAX_HOLD).fieldOf("hold_ticks").forGetter(FormationDrawIn::holdTicks)
    ).apply(i, FormationDrawIn::new));
    public static final StreamCodec<ByteBuf, FormationDrawIn> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, FormationDrawIn::raisedAt,
            ByteBufCodecs.FLOAT, FormationDrawIn::drawn,
            ByteBufCodecs.VAR_INT, FormationDrawIn::drawInTicks,
            ByteBufCodecs.VAR_INT, FormationDrawIn::holdTicks,
            FormationDrawIn::new
    );

    // An order taken at the moment a beat is dispatched. `drawInTicks` is the time the server's own ramp has left, and
    // `(1 - drawn) * duration` is the same number, so a client that starts from `drawn` lands on the whole figure at the
    // moment the server would have: the two ramps have one slope and only one of them is sent.
    public static FormationDrawIn at(long raisedAt, long gameTime, int durationTicks, int intervalTicks) {
        long hold = Math.max(MIN_HOLD, Math.min(MAX_HOLD, (long) HOLD_PER_BEAT * intervalTicks));
        if (durationTicks <= 0) return new FormationDrawIn(raisedAt, 1.0F, 0, (int) hold);
        long elapsed = Math.max(0L, gameTime - raisedAt);
        float drawn = Mth.clamp((float) elapsed / durationTicks, 0.0F, 1.0F);
        long left = Math.max(0L, Math.min(durationTicks, (long) durationTicks - elapsed));
        return new FormationDrawIn(raisedAt, drawn, (int) left, (int) hold);
    }
}
