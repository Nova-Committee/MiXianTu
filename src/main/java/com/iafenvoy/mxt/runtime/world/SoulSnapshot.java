package com.iafenvoy.mxt.runtime.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.attachment.AttachmentHolder;

import java.util.List;
import java.util.Optional;

public record SoulSnapshot(Identifier entityType, CompoundTag data, Optional<ResolvableProfile> profile,
                           float width, float height, float scale, Optional<PlayerVisual> playerVisual) {
    public static final Codec<SoulSnapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("entity_type").forGetter(SoulSnapshot::entityType),
            CompoundTag.CODEC.fieldOf("data").forGetter(SoulSnapshot::data),
            ResolvableProfile.CODEC.optionalFieldOf("profile").forGetter(SoulSnapshot::profile),
            Codec.floatRange(0.01F, 1024.0F).fieldOf("width").forGetter(SoulSnapshot::width),
            Codec.floatRange(0.01F, 1024.0F).fieldOf("height").forGetter(SoulSnapshot::height),
            Codec.floatRange(0.01F, 1024.0F).fieldOf("scale").forGetter(SoulSnapshot::scale),
            PlayerVisual.CODEC.optionalFieldOf("player_visual").forGetter(SoulSnapshot::playerVisual)
    ).apply(i, SoulSnapshot::new));

    public SoulSnapshot {
        data = data.copy();
    }

    @Override
    public CompoundTag data() {
        return this.data.copy();
    }

    public static SoulSnapshot capture(LivingEntity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        entity.saveWithoutId(output);
        return new SoulSnapshot(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), output.buildResult(),
                entity instanceof Player player ? Optional.of(player.getProfile()) : Optional.empty(),
                dimension(entity.getDimensions(Pose.STANDING).width()),
                dimension(entity.getDimensions(Pose.STANDING).height()), dimension(entity.getScale()),
                entity instanceof Player player ? Optional.of(PlayerVisual.capture(player)) : Optional.empty());
    }

    // This temporary source never enters the world; loading it must not restore live relationships or attachments.
    public Optional<LivingEntity> appearanceSource(ServerLevel level) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(this.entityType).orElse(null);
        if (type == null) return Optional.empty();
        Entity entity;
        if (type == EntityType.PLAYER) {
            if (this.profile.isEmpty()) return Optional.empty();
            entity = new Player(level, this.profile.get().partialProfile()) {
                {
                    // Player save data omits these client options, so restore their separately captured values.
                    SoulSnapshot.this.playerVisual.ifPresent(visual -> {
                        this.setMainArm(visual.mainArm());
                        this.getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) visual.modelParts());
                    });
                }

                @Override
                public GameType gameMode() {
                    return GameType.SURVIVAL;
                }
            };
        } else entity = type.create(level, EntitySpawnReason.LOAD);
        if (!(entity instanceof LivingEntity living)) return Optional.empty();
        CompoundTag visual = this.data();
        for (String key : List.of("Team", "Passengers", "RootVehicle", "leash", "NeoForgeData", AttachmentHolder.ATTACHMENTS_NBT_KEY))
            visual.remove(key);
        living.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), visual));
        return Optional.of(living);
    }

    public record PlayerVisual(HumanoidArm mainArm, int modelParts) {
        public static final Codec<PlayerVisual> CODEC = RecordCodecBuilder.create(i -> i.group(
                HumanoidArm.CODEC.fieldOf("main_arm").forGetter(PlayerVisual::mainArm),
                Codec.intRange(0, 127).fieldOf("model_parts").forGetter(PlayerVisual::modelParts)
        ).apply(i, PlayerVisual::new));

        private static PlayerVisual capture(Player player) {
            int parts = 0;
            for (PlayerModelPart part : PlayerModelPart.values())
                if (player.isModelPartShown(part)) parts |= part.getMask();
            return new PlayerVisual(player.getMainArm(), parts);
        }
    }

    private static float dimension(float value) {
        return Float.isFinite(value) ? Math.clamp(value, 0.01F, 1024.0F) : 1.0F;
    }
}
