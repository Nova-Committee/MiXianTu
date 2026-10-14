package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.ability.Abilities;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Writes one storage value of one ability, named by the ability's own id. Only a kind the ability declares can be
 * written. Ability state lives in the ability attachment, so a write is what creates that attachment.
 */
public record ModifyAbilityStorageAction(Identifier id, DataStorage value) implements EntityAction {
    public static final MapCodec<ModifyAbilityStorageAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(ModifyAbilityStorageAction::id),
            DataStorage.CODEC.fieldOf("value").forGetter(ModifyAbilityStorageAction::value)
    ).apply(i, ModifyAbilityStorageAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        if (entity.level().isClientSide()) return;
        Holder<Ability> ability = Abilities.resolve(entity.level().registryAccess(), this.id).orElse(null);
        if (ability == null) {
            MiXianTu.LOGGER.warn("{} is not an ability; nothing was written", this.id);
            return;
        }
        Ability definition = ability.value();
        if (!definition.declares(this.value)) {
            MiXianTu.LOGGER.warn("{} declares no {}; nothing was written", this.id, this.value.getClass().getSimpleName());
            return;
        }
        entity.getData(MxtAttachments.ABILITY_HOLDER).storage().set(ability, this.value, entity.level().getGameTime());
    }

    @Override
    public @NonNull MapCodec<ModifyAbilityStorageAction> codec() {
        return CODEC;
    }
}
