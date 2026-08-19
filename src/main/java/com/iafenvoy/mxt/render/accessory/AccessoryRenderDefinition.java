package com.iafenvoy.mxt.render.accessory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * Client-side, resource-pack controlled transform for an item rendered on a Curios slot.
 */
public record AccessoryRenderDefinition(Preset preset, ItemDisplayContext displayContext,
                                        Vector3fc translation, Vector3fc rotation, Vector3fc scale) {
    public static final Codec<AccessoryRenderDefinition> CODEC = codec(ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
    public static final Codec<AccessoryRenderDefinition> BELT_CODEC = codec(ItemDisplayContext.GROUND);
    public static final AccessoryRenderDefinition BACK_DEFAULT = new AccessoryRenderDefinition(Preset.AUTO, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1));
    public static final AccessoryRenderDefinition BELT_DEFAULT = new AccessoryRenderDefinition(Preset.AUTO, ItemDisplayContext.GROUND, new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1));

    private static Codec<AccessoryRenderDefinition> codec(ItemDisplayContext defaultContext) {
        return RecordCodecBuilder.create(i -> i.group(
                Preset.CODEC.optionalFieldOf("preset", Preset.AUTO).forGetter(AccessoryRenderDefinition::preset),
                ItemDisplayContext.CODEC.optionalFieldOf("display_context", defaultContext).forGetter(AccessoryRenderDefinition::displayContext),
                ExtraCodecs.VECTOR3F.optionalFieldOf("translation", new Vector3f()).forGetter(AccessoryRenderDefinition::translation),
                ExtraCodecs.VECTOR3F.optionalFieldOf("rotation", new Vector3f()).forGetter(AccessoryRenderDefinition::rotation),
                ExtraCodecs.VECTOR3F.optionalFieldOf("scale", new Vector3f(1, 1, 1)).forGetter(AccessoryRenderDefinition::scale)
        ).apply(i, AccessoryRenderDefinition::new));
    }

    public void apply(PoseStack poseStack, ItemStack stack, boolean left, boolean back) {
        this.preset.apply(poseStack, stack, left, back);
        apply(poseStack, this.translation, this.rotation, this.scale);
    }

    private static void apply(PoseStack poseStack, Vector3fc translation, Vector3fc rotation, Vector3fc scale) {
        poseStack.translate(translation.x() / 16.0F, translation.y() / 16.0F, translation.z() / 16.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(rotation.x()));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation.y()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotation.z()));
        poseStack.scale(scale.x(), scale.y(), scale.z());
    }

    public enum Preset implements StringRepresentable {
        AUTO(new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1)),
        DEFAULT(new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1)),
        BIG_WEAPON(new Vector3f(0, 4.8F, 0), new Vector3f(), new Vector3f(1, 1, 1)),
        WEAPON(new Vector3f(0, -6.4F, -6.4F), new Vector3f(180, 0, 0), new Vector3f(1, 1, 1));

        public static final Codec<Preset> CODEC = StringRepresentable.fromValues(Preset::values);
        private final Vector3fc translation;
        private final Vector3fc rotation;
        private final Vector3fc scale;

        Preset(Vector3fc translation, Vector3fc rotation, Vector3fc scale) {
            this.translation = translation;
            this.rotation = rotation;
            this.scale = scale;
        }

        private void apply(PoseStack poseStack, ItemStack stack, boolean left, boolean back) {
            if (!back) return;
            Preset selected = this == AUTO ? (stack.has(DataComponents.WEAPON) ? WEAPON : DEFAULT) : this;
            AccessoryRenderDefinition.apply(poseStack, selected.translation, selected.rotation, selected.scale);
        }

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
