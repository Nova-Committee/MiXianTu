package com.iafenvoy.mxt.data.information.getter;

import com.iafenvoy.mxt.screen.information.MxtPlayerView;
import com.iafenvoy.mxt.screen.information.MxtSavedPlayerView;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.MissingReason;
import com.iafenvoy.saturn.data.information.model.SavedPlayerViewContext;
import com.iafenvoy.saturn.data.information.model.ValueOrigin;
import com.iafenvoy.saturn.data.information.model.ViewValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

public enum MxtInformationGetter implements EntityValueGetter<Map<String, MxtPlayerView.Row>> {
    INSTANCE;

    public static final MapCodec<MxtInformationGetter> CODEC = MapCodec.unit(INSTANCE);
    private static final Codec<Map<String, MxtPlayerView.Row>> VALUE_CODEC = Codec.unboundedMap(Codec.STRING, MxtPlayerView.Row.CODEC);

    @Override
    public MapCodec<MxtInformationGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<Map<String, MxtPlayerView.Row>> valueCodec() {
        return VALUE_CODEC;
    }

    @Override
    public ViewValue<Map<String, MxtPlayerView.Row>> capture(Entity entity) {
        return entity instanceof Player player ? ViewValue.known(MxtPlayerView.capture(player), ValueOrigin.CAPTURED)
                : ViewValue.missing(MissingReason.UNSUPPORTED);
    }

    @Override
    public ViewValue<Map<String, MxtPlayerView.Row>> readSaved(SavedPlayerViewContext context) {
        return ViewValue.known(MxtSavedPlayerView.read(context), ValueOrigin.SAVED);
    }
}
