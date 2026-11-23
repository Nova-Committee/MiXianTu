package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.StormCloudOptions;
import com.iafenvoy.mxt.entity.StormCloudEntity;
import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NonNull;

public record SpawnStormCloudAction(StormCloudOptions.Appearance appearance, float radius, int lifetime,
                                   double offsetY, boolean follow) implements EntityAction {
    public static final MapCodec<SpawnStormCloudAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            StormCloudOptions.Appearance.CODEC.forGetter(SpawnStormCloudAction::appearance),
            StormCloudOptions.RADIUS_CODEC.optionalFieldOf("radius", StormCloudOptions.DEFAULT.radius()).forGetter(SpawnStormCloudAction::radius),
            StormCloudOptions.LIFETIME_CODEC.fieldOf("lifetime").forGetter(SpawnStormCloudAction::lifetime),
            MiscCodecs.FINITE.optionalFieldOf("offset_y", 16.0D).forGetter(SpawnStormCloudAction::offsetY),
            Codec.BOOL.optionalFieldOf("follow", true).forGetter(SpawnStormCloudAction::follow)
    ).apply(i, SpawnStormCloudAction::new));

    @Override
    public void execute(@NonNull EntityActionContext context) {
        if (!(context.entity().level() instanceof ServerLevel level)) return;
        StormCloudEntity cloud = StormCloudEntity.create(level, context.position().add(0.0D, this.offsetY, 0.0D),
                this.appearance.options(this.radius, this.lifetime), this.follow ? context.entity() : null);
        if (cloud != null) level.addFreshEntity(cloud);
    }

    @Override
    public @NonNull MapCodec<SpawnStormCloudAction> codec() {
        return CODEC;
    }
}
