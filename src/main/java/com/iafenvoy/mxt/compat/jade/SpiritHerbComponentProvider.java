package com.iafenvoy.mxt.compat.jade;

import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.TooltipText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Shows which herb grows here and how old it is, from what {@link SpiritHerbDataProvider} sent. The name is resolved
 * against the client's copy of the datapack registry rather than sent as text, so a pack-written name and a
 * generated key both read the way they do on the item.
 */
public enum SpiritHerbComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(@NonNull ITooltip tooltip, BlockAccessor accessor, @NonNull IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(SpiritHerbDataProvider.HERB)) return;
        double age = data.getDoubleOr(SpiritHerbDataProvider.AGE, 0.0D);
        double matureAge = data.getDoubleOr(SpiritHerbDataProvider.MATURE_AGE, 0.0D);
        tooltip.add(herbName(accessor, data.getStringOr(SpiritHerbDataProvider.HERB, ""))
                .copy().withStyle(ChatFormatting.GREEN), SpiritHerbDataProvider.ID);
        tooltip.add(Component.translatable("jade.mxt.spirit_herb.age", TooltipText.number(age),
                TooltipText.number(matureAge)), SpiritHerbDataProvider.ID);
        tooltip.add(Component.translatable(age >= matureAge
                ? "jade.mxt.spirit_herb.mature" : "jade.mxt.spirit_herb.immature"), SpiritHerbDataProvider.ID);
    }

    // The definition's own name when the client has the entry, the id-keyed name otherwise: a herb whose definition
    // the pack dropped still has to read as something.
    private static Component herbName(BlockAccessor accessor, String idText) {
        Identifier id = Identifier.tryParse(idText);
        if (id == null) return Component.literal(idText);
        Holder<SpiritHerb> holder = MxtDatapackRegistries
                .holder(accessor.getLevel().registryAccess(), MxtResourceKeys.SPIRIT_HERB, id).orElse(null);
        return holder == null ? DefinitionText.name(id, "spirit_herb") : DefinitionText.name(holder);
    }

    @Override
    public @NonNull Identifier getUid() {
        return SpiritHerbDataProvider.ID;
    }
}
