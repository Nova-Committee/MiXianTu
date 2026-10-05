package com.iafenvoy.mxt.compat.jade;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.item.block.entity.SpiritCraftingTableBlockEntity;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * The server half of the spirit crafting table display: the recipe's per-element demand and what the table holds.
 * Jade refuses one object playing both roles, so the payload lives here and the display reads it.
 */
public enum SpiritCraftingTableDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;
    // The display reads all three; the shared uid is what pairs this payload with that display.
    static final Identifier ID = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "spirit_crafting_table");
    static final String REQUIRED = "required";
    static final String STORED = "stored";

    @Override
    public void appendServerData(@NonNull CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof SpiritCraftingTableBlockEntity table)
                || table.requiredAura().isEmpty()) return;
        CompoundTag required = new CompoundTag();
        table.requiredAura().forEach((element, amount) -> required.putInt(HolderHelper.id(element).toString(), amount));
        CompoundTag stored = new CompoundTag();
        table.auras().forEach((element, amount) -> stored.putInt(HolderHelper.id(element).toString(), amount));
        data.put(REQUIRED, required);
        data.put(STORED, stored);
    }

    @Override
    public @NonNull Identifier getUid() {
        return ID;
    }
}
