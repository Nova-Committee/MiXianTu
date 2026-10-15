package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.data.storage.DataStorageHolder;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * The read side of the addressing the ability storage action writes with: one ability, named by its own holder in
 * the condition. Only a kind the ability declares may be read; anything else means "nothing to read".
 */
record AbilityStorageReading(Holder<Ability> ability, Optional<DataStorageHolder> holder, Ability definition) {
    // Read-only, so an entity that never had ability state is not given an empty attachment to carry around.
    // Missing state is not a failure: every kind reads as "nothing written yet" instead.
    static AbilityStorageReading of(Entity entity, Holder<Ability> ability) {
        return new AbilityStorageReading(ability,
                entity.getExistingData(MxtAttachments.ABILITY_HOLDER).map(AbilityAttachment::storage),
                ability.value());
    }

    <T extends DataStorage> Optional<T> declared(Class<T> kind) {
        return this.definition.storages().stream().filter(kind::isInstance).map(kind::cast).findFirst();
    }

    // Empty when the ability declares this kind but has never written it, which callers read as "not set".
    <T extends DataStorage> Optional<T> stored(Class<T> kind) {
        return this.holder.flatMap(storage -> storage.get(this.ability, kind));
    }
}
