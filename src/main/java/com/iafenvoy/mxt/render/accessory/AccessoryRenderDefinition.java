package com.iafenvoy.mxt.render.accessory;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * Client-side, resource-pack controlled transforms for an item rendered in
 * physical Curios back and belt slots.
 */
public record AccessoryRenderDefinition(Preset preset, Transform back, Transform belt) {
    public static final MapCodec<AccessoryRenderDefinition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Preset.CODEC.optionalFieldOf("preset", Preset.DEFAULT).forGetter(AccessoryRenderDefinition::preset),
            Transform.CODEC.codec().optionalFieldOf("back", Transform.DEFAULT).forGetter(AccessoryRenderDefinition::back),
            Transform.CODEC.codec().optionalFieldOf("belt", Transform.DEFAULT).forGetter(AccessoryRenderDefinition::belt)
    ).apply(i, AccessoryRenderDefinition::new));
    public static final AccessoryRenderDefinition DEFAULT = create(Preset.DEFAULT);
    public static final AccessoryRenderDefinition WEAPON = create(Preset.WEAPON);

    private static AccessoryRenderDefinition create(Preset preset) {
        return new AccessoryRenderDefinition(preset, Transform.DEFAULT, Transform.DEFAULT);
    }

    public void apply(PoseStack poseStack, boolean back) {
        this.preset.apply(poseStack, back);
        (back ? this.back : this.belt).apply(poseStack);
    }

    private static void apply(PoseStack poseStack, Vector3fc translation, Vector3fc rotation, Vector3fc scale) {
        poseStack.translate(translation.x() / 16.0F, translation.y() / 16.0F, translation.z() / 16.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(rotation.x()));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation.y()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotation.z()));
        poseStack.scale(scale.x(), scale.y(), scale.z());
    }

    public enum Preset implements StringRepresentable {
        DEFAULT(Transform.DEFAULT, Transform.DEFAULT),
        WEAPON(new Transform(new Vector3f(0, 10, 0), new Vector3f(180, 0, 0), new Vector3f(1, 1, 1)), Transform.DEFAULT),
        BIG_WEAPON(new Transform(new Vector3f(0, 4.8F, 0), new Vector3f(), new Vector3f(1, 1, 1)), Transform.DEFAULT);
        public static final Codec<Preset> CODEC = StringRepresentable.fromValues(Preset::values);
        private final Transform back, belt;

        Preset(Transform back, Transform belt) {
            this.back = back;
            this.belt = belt;
        }

        private void apply(PoseStack poseStack, boolean back) {
            (back ? this.back : this.belt).apply(poseStack);
        }

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public record Transform(Vector3fc translation, Vector3fc rotation, Vector3fc scale) {
        public static final Transform DEFAULT = new Transform(new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1));
        public static final MapCodec<Transform> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ExtraCodecs.VECTOR3F.optionalFieldOf("translation", new Vector3f()).forGetter(Transform::translation),
                ExtraCodecs.VECTOR3F.optionalFieldOf("rotation", new Vector3f()).forGetter(Transform::rotation),
                ExtraCodecs.VECTOR3F.optionalFieldOf("scale", new Vector3f(1, 1, 1)).forGetter(Transform::scale)
        ).apply(i, Transform::new));

        private void apply(PoseStack poseStack) {
            AccessoryRenderDefinition.apply(poseStack, this.translation, this.rotation, this.scale);
        }
    }
}
