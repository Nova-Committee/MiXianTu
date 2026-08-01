package com.iafenvoy.mxt.loot;

import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContext.EntityTarget;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Assigns a generated artifact to a selected entity without trusting a client-provided UUID.
 */
public final class SetArtifactOwnerLootFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetArtifactOwnerLootFunction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            LootItemCondition.DIRECT_CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(function -> function.predicates),
            EntityTarget.CODEC.optionalFieldOf("entity", EntityTarget.THIS).forGetter(function -> function.target)
    ).apply(instance, SetArtifactOwnerLootFunction::new));
    private final EntityTarget target;

    private SetArtifactOwnerLootFunction(List<LootItemCondition> conditions, EntityTarget target) {
        super(conditions);
        this.target = target;
    }

    @Override
    public @NonNull MapCodec<SetArtifactOwnerLootFunction> codec() {
        return CODEC;
    }

    @Override
    public @NonNull ItemStack run(@NonNull ItemStack stack, @NonNull LootContext context) {
        Entity entity = this.target.get(context);
        if (entity != null) ArtifactService.refine(stack, entity.getUUID());
        return stack;
    }
}
