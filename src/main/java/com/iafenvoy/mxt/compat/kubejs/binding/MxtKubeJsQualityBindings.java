package com.iafenvoy.mxt.compat.kubejs.binding;

import com.google.gson.JsonElement;
import com.iafenvoy.mxt.compat.kubejs.MxtJsWarnings;
import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtKubeJsDataCodec;
import com.iafenvoy.mxt.data.quality.QualityRequirement;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalInt;

/**
 * Runtime quality operations exposed as {@code MxtQuality}. A quality lives on an item stack, so every call names
 * the stack it acts on and the entity is only what the registry lookup runs from.
 */
public final class MxtKubeJsQualityBindings {
    @Info("The quality tier id that stack resolves to right now, or null when it has none.")
    public String get(Entity entity, ItemStack stack) {
        return text(MxtKubeJsApi.itemQuality(entity, stack));
    }

    @Info("The ladder that stack's tier belongs to, or null when no single ladder holds it.")
    public String chain(Entity entity, ItemStack stack) {
        return text(MxtKubeJsApi.itemQualityChain(entity, stack));
    }

    @Info("The tier one step up from the stack's own, or null at the top of its ladder.")
    public String next(Entity entity, ItemStack stack) {
        return text(MxtKubeJsApi.nextItemQuality(entity, stack));
    }

    @Info("Writes a tier onto the stack as an override; false for an unknown id or on the client.")
    public boolean set(Entity entity, ItemStack stack, String quality) {
        return MxtKubeJsApi.setItemQuality(entity, stack, id(quality));
    }

    @Info("Takes the override off the stack so it falls back to its definition's default tier.")
    public boolean clear(Entity entity, ItemStack stack) {
        return MxtKubeJsApi.clearItemQuality(entity, stack);
    }

    @Info("Moves the stack one step up its ladder, paying that step's costs; the result carries why it refused.")
    public Object upgrade(LivingEntity entity, ItemStack stack) {
        return MxtKubeJsApi.upgradeItemQuality(entity, stack);
    }

    @Info("How far apart two tiers stand on the ladder they share: positive when the first is higher, 0 for the same tier, null when no single ladder holds both.")
    public Integer compare(Entity entity, String left, String right) {
        OptionalInt distance = MxtKubeJsApi.compareQualities(entity, id(left), id(right));
        return distance.isPresent() ? distance.getAsInt() : null;
    }

    @Info("Whether the stack satisfies a complete MXT tier requirement: the same shape mxt:quality writes, a quality list and/or min_quality. The floor is a ladder comparison, so it answers false across ladders.")
    public boolean satisfies(Entity entity, ItemStack stack, JsonElement requirement) {
        if (entity.level().isClientSide()) {
            MxtJsWarnings.warnOnce("quality.client", "MxtQuality.satisfies was called from a client script; it answers false there");
            return false;
        }
        QualityRequirement decoded = MxtKubeJsDataCodec.decodeCached(QualityRequirement.CODEC.codec(), requirement,
                entity.level().registryAccess());
        return MxtKubeJsApi.satisfiesQualityRequirement(entity, stack, decoded);
    }

    @Info("Whether the stack's tier stands at or above the given tier on one shared ladder; false across ladders and for a tier no ladder holds.")
    public boolean atLeast(Entity entity, ItemStack stack, String floor) {
        return MxtKubeJsApi.qualityAtLeast(entity, stack, id(floor));
    }

    private static String text(Identifier id) {
        return id == null ? null : id.toString();
    }

    private static Identifier id(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid MXT identifier: " + raw);
        return id;
    }
}
