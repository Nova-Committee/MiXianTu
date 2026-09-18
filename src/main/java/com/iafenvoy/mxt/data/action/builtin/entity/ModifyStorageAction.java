package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.data.storage.DataStorageDeclaration;
import com.iafenvoy.mxt.data.storage.DataStorageHolder;
import com.iafenvoy.mxt.data.storage.RuntimeStorage;
import com.iafenvoy.mxt.registry.MxtDataStorageHosts;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Writes one storage value of one host. A host is named by its family — the data-pack registry it lives in — and
 * its id, and the value is the kind instance itself, decoded by the same {@code type} dispatch codec that reads
 * it back out of saved data, so this action needs to know nothing about any kind. The value is written into the
 * holder the family keeps on the entity, which is the attachment that owns that state.
 *
 * <p>Only a kind the host declares can be written: state nothing reads is worse than a refusal that says so. The
 * cursors a runtime keeps for itself are refused as well, because they belong to the runtime that reads them.</p>
 */
public record ModifyStorageAction(Identifier family, Identifier id, DataStorage value) implements EntityAction {
    public static final MapCodec<ModifyStorageAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("family").forGetter(ModifyStorageAction::family),
            Identifier.CODEC.fieldOf("id").forGetter(ModifyStorageAction::id),
            DataStorage.CODEC.fieldOf("value").forGetter(ModifyStorageAction::value)
    ).apply(i, ModifyStorageAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        if (entity.level().isClientSide()) return;
        if (this.value instanceof RuntimeStorage) {
            MiXianTu.LOGGER.warn("{} is a runtime-owned kind; nothing was written", this.value.getClass().getSimpleName());
            return;
        }
        DataStorageDeclaration definition = MxtDataStorageHosts.definition(this.family, this.id).orElse(null);
        if (definition == null) {
            MiXianTu.LOGGER.warn("{} is not a host family that keeps storage; nothing was written", this.family);
            return;
        }
        if (!definition.declares(this.value)) {
            MiXianTu.LOGGER.warn("{} declares no {}; nothing was written", this.id, this.value.getClass().getSimpleName());
            return;
        }
        DataStorageHolder storage = MxtDataStorageHosts.holder(entity, this.family).orElse(null);
        if (storage == null) return;
        storage.set(this.id, this.value, entity.level().getGameTime());
    }

    @Override
    public @NonNull MapCodec<ModifyStorageAction> codec() {
        return CODEC;
    }
}
