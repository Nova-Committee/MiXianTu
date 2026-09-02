package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

public record SwingHandAction(InteractionHand hand) implements EntityAction {
    private static final Codec<InteractionHand> HAND_CODEC = Codec.STRING.xmap(
            value -> InteractionHand.valueOf(value.toUpperCase(Locale.ROOT)),
            value -> value.name().toLowerCase(Locale.ROOT)
    );
    public static final MapCodec<SwingHandAction> CODEC = HAND_CODEC.fieldOf("hand").xmap(SwingHandAction::new, SwingHandAction::hand);

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        if (ctx.entity() instanceof LivingEntity living) living.swing(this.hand, true);
    }

    @Override
    public @NonNull MapCodec<SwingHandAction> codec() {
        return CODEC;
    }
}
