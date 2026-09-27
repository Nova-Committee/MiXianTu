package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.NonNull;

/**
 * A consume action that finishes a second item use on the same body. The settlement guard must ignore that call.
 */
public final class PillProbeActions {
    public static final DeferredRegister<MapCodec<? extends EntityAction>> REGISTRY =
            DeferredRegister.create(MxtRegistries.ENTITY_ACTION_TYPE, MxtTestMod.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<ReenterPillAction>> REENTER =
            REGISTRY.register("reenter_pill", () -> ReenterPillAction.CODEC);
    public static int calls;

    private PillProbeActions() {
    }

    public record ReenterPillAction() implements EntityAction {
        public static final MapCodec<ReenterPillAction> CODEC = MapCodec.unit(new ReenterPillAction());

        @Override
        public void execute(@NonNull EntityActionContext ctx) {
            calls++;
            if (ctx.entity() instanceof LivingEntity living)
                ItemBindingService.onUseFinish(living, new ItemStack(Items.HONEY_BOTTLE));
        }

        @Override
        public @NonNull MapCodec<ReenterPillAction> codec() {
            return CODEC;
        }
    }
}
