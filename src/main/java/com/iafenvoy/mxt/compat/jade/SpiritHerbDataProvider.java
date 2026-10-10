package com.iafenvoy.mxt.compat.jade;

import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbGrowthService;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbService;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Sends which herb grows at the looked-at position and how old it is. Registered on {@code Block.class} with a
 * guard inside, because Jade registers against a block class and offers no interface or predicate overload, and a
 * herb block is a data pack's choice rather than a type this mod can name.
 */
public enum SpiritHerbDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    public static final Identifier ID = Identifier.fromNamespaceAndPath("mxt", "spirit_herb");
    static final String HERB = "mxt_herb";
    static final String AGE = "mxt_herb_age";
    static final String MATURE_AGE = "mxt_herb_mature_age";

    @Override
    public void appendServerData(@NonNull CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) return;
        BlockState state = accessor.getBlockState();
        Holder<SpiritHerb> herb = SpiritHerbService.findBlock(level.registryAccess(), state).orElse(null);
        if (herb == null) return;
        SpiritHerb.Growth growth = herb.value().growth().orElse(null);
        if (growth == null) return;
        // The age read settles this plant, so merely looking at a wild one plants its clock. That is deliberate:
        // the alternative is rolling wild_age on every look and letting two looks disagree.
        double age = SpiritHerbGrowthService.ageAt(level, accessor.getPosition(), state).orElse(0.0D);
        data.putString(HERB, HolderHelper.id(herb).toString());
        data.putDouble(AGE, age);
        data.putDouble(MATURE_AGE, growth.matureAge());
    }

    @Override
    public @NonNull Identifier getUid() {
        return ID;
    }
}
