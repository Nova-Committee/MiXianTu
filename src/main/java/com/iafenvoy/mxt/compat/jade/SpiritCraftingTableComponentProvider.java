package com.iafenvoy.mxt.compat.jade;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.item.block.entity.SpiritCraftingTableBlockEntity;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.TooltipText;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Shows the active recipe and per-element aura progress in a spirit crafting table.
 */
public enum SpiritCraftingTableComponentProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;
    private static final Identifier ID = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "spirit_crafting_table");

    @Override
    public void appendServerData(@NonNull CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof SpiritCraftingTableBlockEntity table)
                || table.requiredAura().isEmpty()) return;
        CompoundTag required = new CompoundTag();
        table.requiredAura().forEach((element, amount) -> required.putInt(HolderHelper.id(element).toString(), amount));
        CompoundTag stored = new CompoundTag();
        table.auras().forEach((element, amount) -> stored.putInt(HolderHelper.id(element).toString(), amount));
        data.put("required", required);
        data.put("stored", stored);
    }

    @Override
    public void appendTooltip(@NonNull ITooltip tooltip, BlockAccessor accessor, @NonNull IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains("required")) return;
        CompoundTag required = data.getCompoundOrEmpty("required");
        CompoundTag stored = data.getCompoundOrEmpty("stored");
        Set<String> elements = new LinkedHashSet<>(required.keySet());
        elements.addAll(stored.keySet());
        for (String idText : elements) {
            Identifier id = Identifier.tryParse(idText);
            Component element = id == null ? Component.literal(idText) : DefinitionText.name(id, "resource");
            int current = stored.getIntOr(idText, 0);
            int needed = required.getIntOr(idText, 0);
            tooltip.add(Component.translatable("jade.mxt.spirit_crafting.aura", element,
                    TooltipText.number(current), TooltipText.number(needed)), ID);
        }
    }

    @Override
    public @NonNull Identifier getUid() {
        return ID;
    }
}
