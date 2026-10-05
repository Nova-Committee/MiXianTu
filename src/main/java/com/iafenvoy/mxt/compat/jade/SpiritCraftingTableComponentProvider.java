package com.iafenvoy.mxt.compat.jade;

import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.TooltipText;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Shows the active recipe and per-element aura progress in a spirit crafting table, from what
 * {@link SpiritCraftingTableDataProvider} sent.
 */
public enum SpiritCraftingTableComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(@NonNull ITooltip tooltip, BlockAccessor accessor, @NonNull IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(SpiritCraftingTableDataProvider.REQUIRED)) return;
        CompoundTag required = data.getCompoundOrEmpty(SpiritCraftingTableDataProvider.REQUIRED);
        CompoundTag stored = data.getCompoundOrEmpty(SpiritCraftingTableDataProvider.STORED);
        Set<String> elements = new LinkedHashSet<>(required.keySet());
        elements.addAll(stored.keySet());
        for (String idText : elements) {
            Identifier id = Identifier.tryParse(idText);
            Component element = id == null ? Component.literal(idText) : DefinitionText.name(id, "resource");
            int current = stored.getIntOr(idText, 0);
            int needed = required.getIntOr(idText, 0);
            tooltip.add(Component.translatable("jade.mxt.spirit_crafting.aura", element,
                    TooltipText.number(current), TooltipText.number(needed)), SpiritCraftingTableDataProvider.ID);
        }
    }

    @Override
    public @NonNull Identifier getUid() {
        return SpiritCraftingTableDataProvider.ID;
    }
}
