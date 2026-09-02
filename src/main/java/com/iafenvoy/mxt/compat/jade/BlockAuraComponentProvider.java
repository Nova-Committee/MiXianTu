package com.iafenvoy.mxt.compat.jade;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.aura.BlockAura;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.TooltipText;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shows the aggregate contribution of every matching {@code block_aura} entry.
 */
public enum BlockAuraComponentProvider implements IBlockComponentProvider {
    INSTANCE;
    private static final Identifier ID = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "block_aura");

    @Override
    public void appendTooltip(@NonNull ITooltip tooltip, BlockAccessor accessor, @NonNull IPluginConfig config) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(accessor.getBlock());
        Totals totals = new Totals();
        MxtDatapackRegistries.holders(accessor.getLevel().registryAccess(), MxtResourceKeys.BLOCK_AURA)
                .map(Reference::value)
                .filter(definition -> RegistryCodecs.matches(definition.blocks(), BuiltInRegistries.BLOCK, Registries.BLOCK, blockId))
                .forEach(totals::add);
        totals.appendTo(tooltip);
    }

    @Override
    public @NonNull Identifier getUid() {
        return ID;
    }

    private static final class Totals {
        private final Map<Holder<Resource>, double[]> resources = new LinkedHashMap<>();

        private void add(BlockAura definition) {
            definition.aura().forEach((resource, value) -> {
                double[] totals = this.resources.computeIfAbsent(resource, ignored -> new double[2]);
                totals[0] += value.amount();
                totals[1] += value.regenPerTick();
            });
        }

        private void appendTo(ITooltip tooltip) {
            this.resources.entrySet().stream()
                    .filter(entry -> entry.getValue()[0] != 0.0D || entry.getValue()[1] != 0.0D)
                    .forEach(entry -> tooltip.add(Component.translatable("jade.mxt.block_aura.element",
                            DefinitionText.name(entry.getKey(), "resource"),
                            TooltipText.signed(entry.getValue()[0])), ID));
        }
    }
}
