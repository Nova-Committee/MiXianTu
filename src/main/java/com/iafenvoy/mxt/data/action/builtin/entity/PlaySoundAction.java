package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import java.util.Locale;
import java.util.Optional;

public record PlaySoundAction(SoundEvent sound, Optional<SoundSource> category, float volume,
                              float pitch) implements EntityAction {
    private static final Codec<SoundSource> SOUND_SOURCE_CODEC = Codec.STRING.xmap(
            value -> SoundSource.valueOf(value.toUpperCase(Locale.ROOT)),
            value -> value.name().toLowerCase(Locale.ROOT)
    );
    public static final MapCodec<PlaySoundAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BuiltInRegistries.SOUND_EVENT.byNameCodec().fieldOf("sound").forGetter(PlaySoundAction::sound),
            SOUND_SOURCE_CODEC.optionalFieldOf("category").forGetter(PlaySoundAction::category),
            Codec.FLOAT.optionalFieldOf("volume", 1.0F).forGetter(PlaySoundAction::volume),
            Codec.FLOAT.optionalFieldOf("pitch", 1.0F).forGetter(PlaySoundAction::pitch)
    ).apply(i, PlaySoundAction::new));

    @Override
    public void execute(Entity entity, FormulaContext context) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), this.sound, this.category.orElse(entity.getSoundSource()), this.volume, this.pitch);
    }

    @Override
    public MapCodec<PlaySoundAction> codec() {
        return CODEC;
    }
}
