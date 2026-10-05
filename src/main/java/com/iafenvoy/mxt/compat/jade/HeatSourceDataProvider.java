package com.iafenvoy.mxt.compat.jade;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyHeatService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * The server half of the heat display: it reads the answer where the table and a block's own answer are both
 * available. Jade refuses one object playing both roles, so the payload lives here and the display reads it.
 */
public enum HeatSourceDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;
    // The display reads all three; the shared uid is what pairs this payload with that display.
    static final Identifier ID = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "heat_source");
    static final String TEMPERATURE = "mxt_heat_temperature";
    static final String HEATING = "mxt_heat_heating";

    @Override
    public void appendServerData(@NonNull CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) return;
        BlockState state = accessor.getBlockState();
        if (!AlchemyHeatService.isHeatSource(state)) return;
        data.putDouble(TEMPERATURE, AlchemyHeatService.maxTemperature(level, accessor.getPosition()));
        data.putDouble(HEATING, AlchemyHeatService.heatingPerTick(level, accessor.getPosition()));
    }

    @Override
    public @NonNull Identifier getUid() {
        return ID;
    }
}
